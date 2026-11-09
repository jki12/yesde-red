package node.functional;

import com.sun.net.httpserver.HttpServer;
import node.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.TestNodes;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class HttpRequestNodeTest {
    private HttpServer server;
    private String base;
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private HttpRequestNode node;
    private TestNodes.Sink sink;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/page", exchange -> respond(exchange, 200, "text/html; charset=utf-8", "<html><body><h1>안녕</h1></body></html>"));
        server.createContext("/missing", exchange -> respond(exchange, 404, "text/plain", "not here"));
        server.createContext("/echo-agent", exchange -> respond(exchange, 200, "text/plain",
                exchange.getRequestHeaders().getFirst("User-Agent") + "|" + exchange.getRequestHeaders().getFirst("X-Test")));
        server.createContext("/slow", exchange -> { try { Thread.sleep(800); } catch (InterruptedException ignored) { } respond(exchange, 200, "text/plain", "late"); });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();

        node = new HttpRequestNode("http01");
        node.setMinIntervalMillis(0);
        node.setWarningListener((n, message) -> warnings.add(message));
        sink = new TestNodes.Sink("sink01");
        node.connect(sink);
    }

    @AfterEach
    void stopEverything() throws Exception {
        node.stop(); sink.stop();
        if (node.getThread() != null) node.getThread().join(2_000);
        if (sink.getThread() != null) sink.getThread().join(2_000);
        server.stop(0);
    }

    private boolean awaitWarnings(int count, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (warnings.size() < count) {
            if (System.currentTimeMillis() > deadline) return false;
            Thread.sleep(10);
        }
        return true;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String contentType, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @Test
    void fetchesTheUrlFromThePayloadAndPassesBodyAlong() throws Exception {
        node.start(); sink.start();
        node.receive(new Message(Map.of("url", base + "/page", "keyword", "news")));

        assertTrue(sink.awaitMessages(1, 5_000));
        Message out = sink.received.get(0);
        assertEquals(base + "/page", out.get("url"));
        assertEquals(200, out.get("status"));
        assertEquals("text/html; charset=utf-8", out.get("contentType"));
        assertTrue(out.getString("body").contains("<h1>안녕</h1>"));
        assertEquals("news", out.get("keyword"), "들어온 payload는 그대로 전달");
        assertTrue(warnings.isEmpty());
    }

    @Test
    void fallsBackToTheUrlOptionAndSendsHeaders() throws Exception {
        node.setUrl(base + "/echo-agent");
        node.setUserAgent("test-agent");
        node.setHeaders("X-Test: yes\nmalformed line");
        node.start(); sink.start();
        node.receive(new Message("tick"));

        assertTrue(sink.awaitMessages(1, 5_000));
        assertEquals("test-agent|yes", sink.received.get(0).get("body"));
    }

    @Test
    void non2xxTimeoutsAndBadUrlsBecomeWarningsNotFailures() throws Exception {
        node.setTimeoutMillis(200);
        node.start(); sink.start();
        node.receive(new Message(Map.of("url", base + "/missing")));
        node.receive(new Message(Map.of("url", "not a url at all")));
        node.receive(new Message(Map.of("url", base + "/slow")));
        node.receive(new Message("no url anywhere"));
        node.receive(new Message(Map.of("url", base + "/page"))); // 경고들 뒤에도 계속 동작

        assertTrue(sink.awaitMessages(1, 8_000), "정상 요청은 도착해야 함");
        assertTrue(awaitWarnings(4, 8_000), "경고 4건이 쌓여야 함: " + warnings);
        assertTrue(warnings.get(0).startsWith("HTTP 404"));
        assertTrue(warnings.get(1).startsWith("URL이 올바르지 않습니다"));
        assertTrue(warnings.get(2).startsWith("요청 실패"));
        assertTrue(warnings.get(3).startsWith("요청할 URL이 없습니다"));
        assertNull(node.getFailure(), "노드는 실패 상태가 아님");
        assertTrue(node.isAlive());
    }

    @Test
    void respectsTheMinimumIntervalBetweenRequests() throws Exception {
        node.setMinIntervalMillis(300);
        node.start(); sink.start();
        long started = System.currentTimeMillis();
        node.receive(new Message(Map.of("url", base + "/page")));
        node.receive(new Message(Map.of("url", base + "/page")));
        node.receive(new Message(Map.of("url", base + "/page")));

        assertTrue(sink.awaitMessages(3, 8_000));
        long elapsed = System.currentTimeMillis() - started;
        assertTrue(elapsed >= 600, "세 요청 사이에 최소 간격 2번이 들어가야 함, 걸린 시간 " + elapsed + "ms");
    }
}
