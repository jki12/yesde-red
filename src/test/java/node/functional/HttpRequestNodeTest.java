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
    private final java.util.concurrent.atomic.AtomicInteger flakyHits = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicInteger downHits = new java.util.concurrent.atomic.AtomicInteger();
    private HttpRequestNode node;
    private TestNodes.Sink sink;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool()); // 기본 실행기는 순차 처리라 /slow가 다른 요청을 막음
        server.createContext("/page", exchange -> respond(exchange, 200, "text/html; charset=utf-8", "<html><body><h1>안녕</h1></body></html>"));
        server.createContext("/missing", exchange -> respond(exchange, 404, "text/plain", "not here"));
        server.createContext("/echo-agent", exchange -> respond(exchange, 200, "text/plain",
                exchange.getRequestHeaders().getFirst("User-Agent") + "|" + exchange.getRequestHeaders().getFirst("X-Test")));
        server.createContext("/flaky", exchange -> { // 두 번은 503, 그다음부터 200
            int n = flakyHits.incrementAndGet();
            if (n <= 2) respond(exchange, 503, "text/plain", "try later"); else respond(exchange, 200, "text/plain", "ok after " + n);
        });
        server.createContext("/down", exchange -> { downHits.incrementAndGet(); respond(exchange, 503, "text/plain", "down"); });
        server.createContext("/slow", exchange -> { try { Thread.sleep(800); } catch (InterruptedException ignored) { } respond(exchange, 200, "text/plain", "late"); });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();

        node = new HttpRequestNode("http01");
        node.setMinIntervalMillis(0);
        node.setMaxRetries(0); // 재시도 검사는 각 테스트가 따로 켭니다.
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
        ((java.util.concurrent.ExecutorService) server.getExecutor()).shutdownNow();
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

    @Test
    void retriesTransientFailuresWithGrowingDelays() throws Exception {
        node.setMaxRetries(3);
        node.setRetryDelayMillis(60); // 60ms, 120ms
        node.start(); sink.start();
        long startedAt = System.currentTimeMillis();
        node.receive(new Message(Map.of("url", base + "/flaky")));

        assertTrue(sink.awaitMessages(1, 5_000));
        assertTrue(System.currentTimeMillis() - startedAt >= 180, "대기 60 + 120ms 뒤에 성공");
        assertEquals(3, flakyHits.get(), "503 두 번 뒤 세 번째에 성공");
        assertEquals("ok after 3", sink.received.get(0).getString(HttpRequestNode.BODY));
        assertEquals(List.of(), warnings, "재시도로 성공하면 경고 없음");
    }

    @Test
    void givesUpAfterMaxRetriesAndDoesNotRetryClientErrors() throws Exception {
        node.setMaxRetries(2);
        node.setRetryDelayMillis(10);
        node.start(); sink.start();
        node.receive(new Message(Map.of("url", base + "/down")));
        node.receive(new Message(Map.of("url", base + "/missing")));

        assertTrue(awaitWarnings(2, 5_000), warnings.toString());
        assertEquals(3, downHits.get(), "처음 + 재시도 2회");
        assertTrue(warnings.get(0).startsWith("HTTP 503") && warnings.get(0).endsWith("3회 시도"), warnings.get(0));
        assertEquals("HTTP 404: " + base + "/missing", warnings.get(1), "404는 재시도 없이 한 번에 경고");
        assertEquals(0, sink.received.size());
    }
}
