package node.functional;

import com.sun.net.httpserver.HttpExchange;
import lombok.Getter;
import lombok.Setter;
import node.NodeType;
import node.option.OptionField;
import node.option.OptionInputType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 워크플로가 실행되는 동안 127.0.0.1에 HTTP 서버를 띄워 폴더의 파일을 응답으로 내줍니다.
 * 실제 사이트 대신 고정된 입력으로 파이프라인을 돌려 보거나 데모할 때 씁니다. 외부에서는 접근할 수 없습니다.
 */
@Getter
@Setter
public class MockServerNode extends LocalHttpServerNode {
    @OptionField(label = "응답 파일 폴더 (경로 그대로 서빙, 예: /items.json → 폴더/items.json)")
    private String directory = "mock";
    @OptionField(min = 0, label = "응답 지연(ms)")
    private long delayMillis = 0;
    @OptionField(type = OptionInputType.TEXT_AREA, label = "경로 규칙 (줄마다 경로 = 상태 [본문], 폴더보다 우선)")
    private String routes;
    @OptionField(min = 0, label = "N번째 요청마다 500으로 실패 (0이면 없음)")
    private int failEvery = 0;

    private Path root;
    private Map<String, Route> routeTable = Map.of();
    private final AtomicLong requestCount = new AtomicLong();

    private record Route(int status, String body) { }

    public MockServerNode(String id) {
        super(NodeType.MOCK_SERVER, id, 8099);
    }

    @Override
    protected void onStart() {
        root = Path.of(directory == null ? "" : directory).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) throw new IllegalStateException("응답 파일 폴더가 없습니다: " + root);
        routeTable = parseRoutes(routes);
        requestCount.set(0);
        super.onStart();
    }

    // 응답 순서: 장애 주입 → 경로 규칙 → 폴더의 파일 → 404. 돌려주는 값은 보낸 상태 코드입니다.
    @Override
    protected int serve(HttpExchange exchange) throws IOException {
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
}
