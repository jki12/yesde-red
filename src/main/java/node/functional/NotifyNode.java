package node.functional;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.Setter;
import node.BaseNode;
import node.Inputable;
import node.Message;
import node.NodeType;
import node.option.OptionField;
import node.option.OptionInputType;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 메시지를 받을 때마다 웹훅 URL로 JSON을 POST합니다. 조건 필터 뒤에 두면 원하는 핫딜만 알림이 됩니다.
 * 본문은 형식에 따라 Discord {"content": …}, Slack {"text": …}, 또는 payload 전체이며, 글은 {필드} 자리를 채운 템플릿입니다.
 * 실패한 전송은 경고만 남깁니다. 서비스의 요청 제한을 지키도록 전송 사이 최소 간격을 둡니다.
 */
@Getter
@Setter
public class NotifyNode extends BaseNode implements Inputable {
    public enum Format { DISCORD, SLACK, JSON }

    private static final Gson GSON = new Gson();

    @OptionField(label = "웹훅 URL")
    private String url;
    @OptionField(type = OptionInputType.RADIO_BUTTON, label = "형식 (JSON은 payload 전체)")
    private Format format = Format.DISCORD;
    @OptionField(type = OptionInputType.TEXT_AREA, label = "글 템플릿 ({필드}로 값을 넣음)")
    private String template = "{title}\n{price}원 · {post_url}";
    @OptionField(min = 1, label = "타임아웃(ms)")
    private long timeoutMillis = 10_000;
    @OptionField(min = 0, label = "전송 사이 최소 간격(ms)")
    private long minIntervalMillis = 1_000;

    private HttpClient client;
    private long lastSentAt;

    public NotifyNode(String id) {
        super(NodeType.NOTIFY, id);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (url == null || url.isBlank()) throw new IllegalStateException("웹훅 URL이 비어 있습니다.");
        try {
            URI.create(url).toURL();
        } catch (IllegalArgumentException | java.net.MalformedURLException e) {
            throw new IllegalStateException("웹훅 URL이 올바르지 않습니다: " + url, e);
        }
        client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMillis)).build();
        lastSentAt = 0;
    }

    @Override
    protected void process() throws Exception {
        Message message = getInbox().take();
        String body = body(message);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        long wait = lastSentAt + minIntervalMillis - System.currentTimeMillis();
        if (wait > 0) Thread.sleep(wait);
        lastSentAt = System.currentTimeMillis();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) warn("알림 전송 실패: HTTP " + response.statusCode() + " (" + message + ")");
        } catch (IOException e) {
            warn("알림 전송 실패: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
    }

    String body(Message message) {
        String text = FieldTransforms.format(template == null ? "" : template, message.getPayload());
        return switch (format) {
            case DISCORD -> GSON.toJson(Map.of("content", text));
            case SLACK -> GSON.toJson(Map.of("text", text));
            case JSON -> GSON.toJson(message.getPayload());
        };
    }
}
