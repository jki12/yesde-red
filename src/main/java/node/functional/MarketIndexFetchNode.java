package node.functional;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import node.InputOutputNode;
import node.Message;
import node.Type;
import util.Utils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Setter
@Getter
@Slf4j
public class MarketIndexFetchNode extends InputOutputNode {
    private static final String USER_AGENT_KEY = "User-Agent";
    private static final String USER_AGENT_VALUE = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/51.0.2704.103 Safari/537.36";
    private static final String FORMATTED_URL = "https://www.google.com/async/finance_wholepage_price_updates?ei=IfUsXc2oJY2J5wKngriIAQ&yv=3&async=mids:%s,currencies:,_fmt:jspb,currencies:,_fmt:jspb";
    private static final Pattern PATTERN = Pattern.compile(",\"[0-9,.]*\""); // ex),"2596.91" 이런 형태의 값으로 결과를 반환합니다. subString을 이용해 값을 파싱해서 사용해야함.

    private final HttpClient client;
    private MarketIndex currentIndex;

    public MarketIndexFetchNode() {
        super(Type.MARKET_INDEX_FETCH);

        client = Utils.createHttpClient();
        currentIndex = MarketIndex.KOSPI; // 기본값은 코스피 지수.
    }

    @Override
    protected void process() throws Exception {
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .GET()
                .headers(USER_AGENT_KEY, USER_AGENT_VALUE)
                .uri(URI.create(String.format(FORMATTED_URL, currentIndex.id)))
                .build();

        HttpResponse<String> res = client
                .send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        String body = new String(res.body().getBytes());
        if (res.statusCode() == 200) {
            log.debug(body);

            Matcher matcher = PATTERN.matcher(body);

            if (matcher.find()) {
                String data = matcher.group();

                Message message = new Message(String.format("%s : %s", currentIndex, data.substring(2, data.length() - 1)));
                System.out.println(message); // TODO, 연결된 값에 전달,
            }
            else {
                log.error("정규표현식으로 값을 찾지 못했습니다. data를 확인해주세요.");
            }
        }
        else {
            log.error("status code : {}, message : {}", res.statusCode(), body);
        }
    }

    public enum MarketIndex { // - 퍼센트 인코딩이 되어 있음..
        S_AND_P_500("%2Fm%2F016yss"), // S&P 500 지수, /m/016yss
        KOSPI("%2Fm%2F04w0nf"); // /m/04w0nf
        // KOSDAQ("%2Fm%2F03k1tk"); // /m/03k1tk, 코스닥은 지원 안함..

        private final String id;

        MarketIndex(String id) {
            this.id = id;
        }
    }
}
