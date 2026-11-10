package node.functional;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import node.BaseNode;
import node.Inputable;
import node.Message;
import node.NodeType;
import node.Outputable;
import node.option.OptionField;
import node.option.OptionInputType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 메시지를 받을 때마다 URL을 GET으로 호출하고 응답을 payload에 더해 넘깁니다.
 * URL은 들어온 payload의 url 필드를 우선 쓰고, 없으면 옵션의 URL을 씁니다.
 * 연결 실패·타임아웃·429·5xx는 대기 시간을 두 배씩 늘리며 재시도하고, 그래도 안 되면 경고만 남기고 건너뜁니다. 404 같은 4xx는 바로 건너뜁니다.
 */
@Slf4j
@Getter
@Setter
public class HttpRequestNode extends BaseNode implements Inputable, Outputable {
    public static final String URL = "url";
    public static final String STATUS = "status";
    public static final String CONTENT_TYPE = "contentType";
    public static final String BODY = "body";

    @OptionField(label = "URL (payload에 url이 없을 때)")
    private String url;
    @OptionField(type = OptionInputType.TEXT_AREA, label = "요청 헤더 (줄마다 이름: 값)")
    private String headers;
    @OptionField(label = "User-Agent")
    private String userAgent = "yesde-red/1.0";
    @OptionField(min = 1, label = "타임아웃(ms)")
    private long timeoutMillis = 10_000;
    @OptionField(min = 0, label = "요청 사이 최소 간격(ms)")
    private long minIntervalMillis = 1_000;
    @OptionField(min = 0, label = "재시도 횟수 (연결 실패·타임아웃·429·5xx)")
    private int maxRetries = 2;
    @OptionField(min = 0, label = "첫 재시도까지 대기(ms), 이후 두 배씩")
    private long retryDelayMillis = 1_000;

    private static final long MAX_RETRY_DELAY_MILLIS = 60_000;

    private HttpClient client;
    private long lastRequestAt;

    public HttpRequestNode(String id) {
        super(NodeType.HTTP_REQUEST, id);
    }

    @Override
    protected void onStart() {
        super.onStart();
        client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMillis))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        lastRequestAt = 0;
    }

    @Override
    protected void process() throws Exception {
        Message in = getInbox().take();
        String target = in.getString(URL) != null ? in.getString(URL) : url;
        if (target == null || target.isBlank()) {
            warn("요청할 URL이 없습니다. payload의 url 필드나 URL 옵션을 채우세요.");
            return;
        }

        HttpRequest request;
        try {
            request = buildRequest(target);
        } catch (IllegalArgumentException e) {
            warn("URL이 올바르지 않습니다: " + target);
            return;
        }

        waitForInterval();
        for (int attempt = 0; ; attempt++) {
            String problem;
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 == 2) {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put(URL, target);
                    result.put(STATUS, response.statusCode());
                    result.put(CONTENT_TYPE, response.headers().firstValue("Content-Type").orElse(null));
                    result.put(BODY, response.body());
                    send(in.withAll(result));
                    return;
                }
                problem = "HTTP " + response.statusCode() + ": " + target;
                if (!isRetryable(response.statusCode())) {
                    warn(problem);
                    return;
                }
            } catch (IOException e) {
                problem = "요청 실패: " + target + " (" + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()) + ")";
            }
            if (attempt >= maxRetries) {
                warn(problem + (attempt > 0 ? " — " + (attempt + 1) + "회 시도" : ""));
                return;
            }
            long delay = Math.min(retryDelayMillis << attempt, MAX_RETRY_DELAY_MILLIS); // 1초, 2초, 4초 … 최대 1분
            log.info("[{}] {} — {}ms 뒤 재시도 ({}/{})", getId(), problem, delay, attempt + 1, maxRetries);
            Thread.sleep(delay); // stop()의 인터럽트로 깨어나 종료됩니다.
        }
    }

    // 서버 쪽 일시 장애와 요청 제한만 재시도합니다. 404·403 같은 4xx는 다시 보내도 같은 답이 옵니다.
    private static boolean isRetryable(int status) {
        return status == 429 || status >= 500;
    }

    private HttpRequest buildRequest(String target) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(target))
                .timeout(Duration.ofMillis(timeoutMillis))
                .GET();
        if (userAgent != null && !userAgent.isBlank()) builder.header("User-Agent", userAgent);
        if (headers != null) {
            for (String line : headers.split("\\R")) {
                int separator = line.indexOf(':');
                if (separator <= 0) continue;
                builder.header(line.substring(0, separator).strip(), line.substring(separator + 1).strip());
            }
        }
        return builder.build();
    }

    // 같은 서버를 너무 빠르게 두드리지 않도록 직전 요청으로부터 최소 간격을 지킵니다.
    private void waitForInterval() throws InterruptedException {
        long wait = lastRequestAt + minIntervalMillis - System.currentTimeMillis();
        if (wait > 0) Thread.sleep(wait);
        lastRequestAt = System.currentTimeMillis();
    }
}
