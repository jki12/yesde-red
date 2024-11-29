package node.http;

import annotation.OptionField;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.HttpRequestPacket;
import node.InputOutputNode;
import node.Type;
import util.Utils;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Setter
public class HttpTestNode extends InputOutputNode {
    // allowRestrictedHeaders property 적용하지 않을 경우 금지된 헤더.
    private static final Set<String> RESTRICTED_HEADER = Set.of("connection", "content-length", "expect", "host", "upgrade");

    private final Set<HttpClient> clients = new HashSet<>();
    private List<HttpRequestPacket> packets;
    @OptionField
    private String jsonFilePath = "src/main/resources/packet/simple_request2.json"; // test용 파일
    @OptionField
    private int clientCount;

    public HttpTestNode() {
        super(Type.HTTP_TESTER);
    }

    @Override
    protected void preprocess() {
        packets = Utils.parsePacketCapture(new File(jsonFilePath));
        for (int i = 0; i < clientCount; ++i) clients.add(HttpClient.newHttpClient());

        super.preprocess();
    }

    /**
     * @return [key1, value1, key2, value2 ..] 형태로 반환합니다.
     */
    private String[] toArray(Map<String, String> headers) {
        String[] arr = new String[headers.size() * 2];
        int index = 0;

        for (String key : headers.keySet()) {
            arr[index++] = key;
            arr[index++] = headers.get(key);
        }

        return arr;
    }

    @Override
    protected void process() throws Exception {
        for (HttpRequestPacket data : packets) {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .version(java.net.http.HttpClient.Version.HTTP_1_1)
                    .uri(URI.create(data.getFullUri()))
                    .method(data.getMethod(), HttpRequest.BodyPublishers.noBody()) // TODO, post, put 구현.
                    .headers(toArray(data.getHeaders()));

            HttpRequest httpRequest = builder.build();
            log.info("Request info : {}", httpRequest);

            for (HttpClient client : clients) {
                HttpResponse<String> res = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                log.info("status code : {} body : {}", res.statusCode(), res.body());
            }
        }
    }
}
