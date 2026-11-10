package node.functional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MockServerNodeTest {
    private final MockServerNode node = new MockServerNode("mock01");
    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void stop() throws Exception {
        node.stop();
        if (node.getThread() != null) node.getThread().join(2_000);
    }

    private void start(Path dir) throws Exception {
        node.setDirectory(dir.toString());
        node.setPort(0);
        node.start();
        long deadline = System.currentTimeMillis() + 5_000;
        while (node.getBoundPort() < 0 && node.getFailure() == null && System.currentTimeMillis() < deadline) Thread.sleep(10);
        assertNull(node.getFailure(), "서버가 떠야 함");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + node.getBoundPort() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void servesFilesFromTheDirectoryWithContentTypes(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("items.json"), "{\"items\":[1,2]}");
        Files.writeString(dir.resolve("page.html"), "<h1>안녕</h1>");
        Files.createDirectories(dir.resolve("feeds"));
        Files.writeString(dir.resolve("feeds/hot.rss"), "<rss/>");
        Files.writeString(dir.resolve("index.html"), "home");
        start(dir);

        HttpResponse<String> json = get("/items.json");
        assertEquals(200, json.statusCode());
        assertEquals("application/json; charset=utf-8", json.headers().firstValue("Content-Type").orElse(""));
        assertEquals("{\"items\":[1,2]}", json.body());
        assertEquals("<h1>안녕</h1>", get("/page.html").body());
        assertEquals("application/xml; charset=utf-8", get("/feeds/hot.rss").headers().firstValue("Content-Type").orElse(""));
        assertEquals("home", get("/").body(), "루트는 index.html");
        assertEquals(404, get("/missing.json").statusCode());
    }

    @Test
    void refusesPathsOutsideTheDirectoryAndNonGetMethods(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("ok.txt"), "ok");
        Path secret = dir.getParent().resolve("secret-" + dir.getFileName() + ".txt");
        Files.writeString(secret, "secret");
        start(dir);
        try {
            assertEquals(404, get("/../" + secret.getFileName()).statusCode(), "폴더 밖은 404");
            assertEquals(404, get("/sub/../../" + secret.getFileName()).statusCode());
            HttpResponse<String> post = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + node.getBoundPort() + "/ok.txt"))
                    .POST(HttpRequest.BodyPublishers.ofString("x")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(405, post.statusCode(), "파일은 GET만");
            node.stop(); node.getThread().join(2_000);
            node.setRoutes("/webhook = 204");
            start(dir);
            HttpResponse<String> hook = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + node.getBoundPort() + "/webhook"))
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(204, hook.statusCode(), "경로 규칙은 POST에도 답함");
        } finally {
            Files.deleteIfExists(secret);
        }
    }

    @Test
    void routeRulesOverrideFilesAndFailuresCanBeInjected(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("items.json"), "{}");
        Files.writeString(dir.resolve("a.txt"), "a");
        node.setRoutes("/items.json = 503 maintenance\n/ping = 200 pong\n/gone = 410");
        node.setFailEvery(2);
        start(dir);

        HttpResponse<String> overridden = get("/items.json");
        assertEquals(503, overridden.statusCode(), "규칙이 파일보다 우선");
        assertEquals("maintenance", overridden.body());
        assertEquals("pong", get("/ping").body());
        assertEquals("HTTP 410", get("/gone").body(), "본문을 생략하면 상태 문구");
        assertEquals("pong", get("/ping").body(), "규칙은 장애 주입을 받지 않음");

        assertEquals(200, get("/a.txt").statusCode(), "파일 요청 1번째");
        HttpResponse<String> injected = get("/a.txt"); // 파일 요청 2번째
        assertEquals(500, injected.statusCode(), "파일 요청 N번째는 500");
        assertTrue(injected.body().startsWith("injected failure"));
        assertEquals(200, get("/a.txt").statusCode(), "그다음은 다시 정상");
    }

    @Test
    void emitsOneMessagePerRequest(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.txt"), "a");
        support.TestNodes.Sink sink = new support.TestNodes.Sink("sink01");
        node.connect(sink);
        sink.start();
        try {
            start(dir);
            get("/a.txt?page=2");
            get("/nothing");

            assertTrue(sink.awaitMessages(2, 5_000));
            node.Message first = sink.received.get(0);
            assertEquals("GET", first.get("method"));
            assertEquals("/a.txt", first.get("path"));
            assertEquals("page=2", first.get("query"));
            assertEquals(200, first.get("status"));
            assertEquals(404, sink.received.get(1).get("status"));
        } finally {
            sink.stop();
            if (sink.getThread() != null) sink.getThread().join(2_000);
        }
    }

    @Test
    void badRouteRulesFailAtStart(@TempDir Path dir) throws Exception {
        node.setDirectory(dir.toString());
        node.setPort(0);
        node.setRoutes("/x = abc");
        node.start();
        node.getThread().join(2_000);
        assertNotNull(node.getFailure());
        assertTrue(node.getFailure().getMessage().contains("상태 코드"));
    }

    @Test
    void delaysResponsesWhenConfigured(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("slow.txt"), "late");
        node.setDelayMillis(300);
        start(dir);

        long started = System.currentTimeMillis();
        assertEquals("late", get("/slow.txt").body());
        assertTrue(System.currentTimeMillis() - started >= 300, "지연 옵션만큼 늦게 응답");
    }

    @Test
    void missingDirectoryOrBusyPortFailsTheNodeAtStart(@TempDir Path dir) throws Exception {
        node.setDirectory(dir.resolve("nope").toString());
        node.setPort(0);
        node.start();
        node.getThread().join(2_000);
        assertNotNull(node.getFailure());
        assertTrue(node.getFailure().getMessage().contains("폴더가 없습니다"));

        try (ServerSocket taken = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            MockServerNode busy = new MockServerNode("mock02");
            busy.setDirectory(dir.toString());
            busy.setPort(taken.getLocalPort());
            busy.start();
            busy.getThread().join(2_000);
            assertNotNull(busy.getFailure());
            assertTrue(busy.getFailure().getMessage().contains("이미 사용 중"), busy.getFailure().getMessage());
        }
    }

    @Test
    void serverIsClosedWhenTheNodeStops(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("a.txt"), "a");
        start(dir);
        int port = node.getBoundPort();
        assertEquals(200, get("/a.txt").statusCode());

        node.stop();
        node.getThread().join(2_000);
        assertEquals(-1, node.getBoundPort());
        assertThrows(java.io.IOException.class, () -> client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/a.txt")).GET().build(),
                HttpResponse.BodyHandlers.ofString()), "종료 후에는 연결되지 않음");
    }
}
