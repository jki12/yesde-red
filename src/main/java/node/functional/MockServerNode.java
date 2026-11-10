package node.functional;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.Getter;
import lombok.Setter;
import node.BaseNode;
import node.Message;
import node.NodeType;
import node.Outputable;
import node.option.OptionField;
import node.option.OptionInputType;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 워크플로가 실행되는 동안 127.0.0.1에 HTTP 서버를 띄워 폴더의 파일을 응답으로 내줍니다.
 * 실제 사이트 대신 고정된 입력으로 파이프라인을 돌려 보거나 데모할 때 씁니다. 외부에서는 접근할 수 없습니다.
 */
@Getter
@Setter
public class MockServerNode extends BaseNode implements Outputable {
    private static final int EVENT_QUEUE_CAPACITY = 1_000;

    @OptionField(min = 0, label = "포트 (0이면 비어 있는 포트 자동)")
    private int port = 8099;
    @OptionField(label = "응답 파일 폴더 (경로 그대로 서빙, 예: /items.json → 폴더/items.json)")
    private String directory = "mock-data";
    @OptionField(min = 0, label = "응답 지연(ms)")
    private long delayMillis = 0;
    @OptionField(type = OptionInputType.TEXT_AREA, label = "경로 규칙 (줄마다 경로 = 상태 [본문], 폴더보다 우선)")
    private String routes;
    @OptionField(min = 0, label = "N번째 요청마다 500으로 실패 (0이면 없음)")
    private int failEvery = 0;

    private HttpServer server;
    private ExecutorService executor;
    private Path root;
    private Map<String, Route> routeTable = Map.of();
    private final AtomicLong requestCount = new AtomicLong();

    private record Route(int status, String body) { }
    // 들어온 요청을 메시지로 내보내기 위한 큐입니다. 받는 노드가 없어도 메모리가 늘지 않도록 상한을 둡니다.
    private final BlockingQueue<Message> events = new ArrayBlockingQueue<>(EVENT_QUEUE_CAPACITY);

    public MockServerNode(String id) {
        super(NodeType.MOCK_SERVER, id);
    }

    /** 서버가 실제로 연 포트입니다. 옵션이 0이면 자동으로 고른 포트를 돌려줍니다. 서버가 없으면 -1입니다. */
    public int getBoundPort() {
        return server == null ? -1 : server.getAddress().getPort();
    }

    @Override
    protected void onStart() {
        super.onStart();
        root = Path.of(directory == null ? "" : directory).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) throw new IllegalStateException("응답 파일 폴더가 없습니다: " + root);
        routeTable = parseRoutes(routes);
        requestCount.set(0);
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        } catch (BindException e) {
            throw new IllegalStateException("포트 " + port + "을(를) 열 수 없습니다. 이미 사용 중인지 확인하세요.", e);
        } catch (IOException e) {
            throw new IllegalStateException("Mock 서버를 시작할 수 없습니다: " + e.getMessage(), e);
        }
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    @Override
    protected void process() throws Exception {
        send(events.take()); // 요청이 올 때까지 기다립니다. stop()의 인터럽트로 깨어나 종료됩니다.
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
        server = null;
        executor = null;
    }

    private void handle(HttpExchange exchange) throws IOException {
        int status;
        try (exchange) {
            status = serve(exchange);
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("method", exchange.getRequestMethod());
        event.put("path", exchange.getRequestURI().getPath());
        event.put("query", exchange.getRequestURI().getQuery());
        event.put("status", status);
        if (!events.offer(new Message(event))) warn("요청 이벤트 큐가 가득 차 이벤트를 버립니다. 출력 포트에 받는 노드를 연결하세요.");
    }

    // 응답 순서: 장애 주입 → 경로 규칙 → 폴더의 파일 → 404. 돌려주는 값은 보낸 상태 코드입니다.
    private int serve(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        long count = requestCount.incrementAndGet();
        if (failEvery > 0 && count % failEvery == 0) {
            respond(exchange, 500, "text/plain", "injected failure (request #" + count + ")");
            return 500;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "text/plain", "GET only");
            return 405;
        }
        if (delayMillis > 0) {
            try { Thread.sleep(delayMillis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return 0; }
        }
        Route route = routeTable.get(path);
        if (route != null) {
            respond(exchange, route.status(), "text/plain", route.body());
            return route.status();
        }
        Path file = resolve(path);
        if (file == null || !Files.isRegularFile(file)) {
            respond(exchange, 404, "text/plain", "not found: " + path);
            return 404;
        }
        respond(exchange, 200, contentType(file), Files.readAllBytes(file));
        return 200;
    }

    /** "경로 = 상태 [본문]" 줄들입니다. 본문을 생략하면 상태 코드 문구를 씁니다. */
    static Map<String, Route> parseRoutes(String text) {
        Map<String, Route> table = new LinkedHashMap<>();
        for (Map.Entry<String, String> line : FieldMappings.parse(text).entrySet()) {
            String[] parts = line.getValue().split("\\s+", 2);
            int status;
            try {
                status = Integer.parseInt(parts[0]);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("경로 규칙의 상태 코드가 숫자가 아닙니다: " + line.getKey() + " = " + line.getValue());
            }
            if (status < 100 || status > 599) throw new IllegalArgumentException("경로 규칙의 상태 코드 범위 오류: " + status);
            table.put(line.getKey(), new Route(status, parts.length > 1 ? parts[1] : "HTTP " + status));
        }
        return table;
    }

    // 요청 경로를 폴더 안의 파일로 바꿉니다. 폴더 밖("..")을 가리키면 null입니다.
    private Path resolve(String requestPath) {
        String relative = requestPath.startsWith("/") ? requestPath.substring(1) : requestPath;
        if (relative.isEmpty()) relative = "index.html";
        Path file = root.resolve(relative).normalize();
        return file.startsWith(root) ? file : null;
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String extension = dot < 0 ? "" : name.substring(dot + 1);
        return switch (extension) {
            case "json" -> "application/json; charset=utf-8";
            case "html", "htm" -> "text/html; charset=utf-8";
            case "xml", "rss" -> "application/xml; charset=utf-8";
            case "txt" -> "text/plain; charset=utf-8";
            case "csv" -> "text/csv; charset=utf-8";
            default -> "application/octet-stream";
        };
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        respond(exchange, status, contentType + "; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
