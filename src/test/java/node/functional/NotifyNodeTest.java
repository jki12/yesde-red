package node.functional;

import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import node.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class NotifyNodeTest {
    private HttpServer server;
    private String base;
    private final List<String> posted = new CopyOnWriteArrayList<>();      // "METHOD content-type|body"
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private NotifyNode node;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            posted.add(exchange.getRequestMethod() + " " + exchange.getRequestHeaders().getFirst("Content-Type") + "|"
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.createContext("/broken", exchange -> { exchange.sendResponseHeaders(500, -1); exchange.close(); });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
        node = new NotifyNode("n1");
        node.setMinIntervalMillis(0);
        node.setWarningListener((n, m) -> warnings.add(m));
    }

    @AfterEach
    void stop() throws Exception {
        node.stop();
        if (node.getThread() != null) node.getThread().join(2_000);
        server.stop(0);
    }

    private void send(Message... messages) throws Exception {
        node.start();
        assertNull(node.getFailure(), String.valueOf(node.getFailure()));
        for (Message message : messages) node.receive(message);
        long deadline = System.currentTimeMillis() + 3_000;
        while (!node.getInbox().isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10);
        Thread.sleep(150);
    }

    @Test
    void postsDiscordStyleJsonFromTheTemplate() throws Exception {
        node.setUrl(base + "/hook");
        node.setTemplate("{title}\n{price}원 · {post_url}");
        send(new Message(Map.of("title", "무선 키보드", "price", 15000L, "post_url", "https://x/1")));

        assertEquals(1, posted.size());
        assertTrue(posted.get(0).startsWith("POST application/json"), posted.get(0));
        String body = posted.get(0).substring(posted.get(0).indexOf('|') + 1);
        assertEquals("무선 키보드\n15000원 · https://x/1", JsonParser.parseString(body).getAsJsonObject().get("content").getAsString());
        assertEquals(List.of(), warnings);
    }

    @Test
    void slackAndRawJsonFormats() {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("title", "a");
        payload.put("price", 1L);
        Message message = new Message(payload);
        node.setFormat(NotifyNode.Format.SLACK);
        node.setTemplate("{title} {missing}!");
        assertEquals("{\"text\":\"a !\"}", node.body(message), "없는 필드는 빈 문자열");
        node.setFormat(NotifyNode.Format.JSON);
        assertEquals("{\"title\":\"a\",\"price\":1}", node.body(message), "payload 전체");
        node.setFormat(NotifyNode.Format.DISCORD);
        node.setTemplate(null);
        assertEquals("{\"content\":\"{\\\"title\\\":\\\"a\\\",\\\"price\\\":1}\"}", node.body(message), "템플릿이 없으면 메시지 그대로");
    }

    @Test
    void failedDeliveryIsAWarningAndTheNodeKeepsGoing() throws Exception {
        node.setUrl(base + "/broken");
        send(new Message("x"), new Message("y"));
        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("HTTP 500"), warnings.get(0));
        assertTrue(node.isAlive());
    }

    @Test
    void missingUrlFailsAtStart() throws Exception {
        node.setUrl(" ");
        node.start();
        node.getThread().join(2_000);
        assertNotNull(node.getFailure());
    }
}
