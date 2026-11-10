package node.functional;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.TestNodes;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** embedded PostgreSQL에 글 몇 개를 넣고, 라우트 옵션으로 만든 API를 실제 HTTP로 호출합니다. */
class ApiServerNodeTest {
    private static EmbeddedPostgres postgres;
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    static final String ROUTES = """
            /health = SELECT 'ok' AS status
            /deals = SELECT id, source, title, price, shop, posted_at FROM deals \
              WHERE (:source::text IS NULL OR source = :source) AND (:since::timestamptz IS NULL OR posted_at >= :since) \
              AND (:q::text IS NULL OR title ILIKE '%' || :q || '%') \
              ORDER BY posted_at DESC NULLS LAST, id LIMIT COALESCE(:limit::int, 50) OFFSET COALESCE(:offset::int, 0)
            /deals/{id} = SELECT * FROM deals WHERE id = :id
            /deals/{id}/snapshots = SELECT collected_at, price FROM deal_snapshots WHERE deal_id = :id ORDER BY collected_at
            """;

    private final ApiServerNode node = new ApiServerNode("api01");
    private final TestNodes.Sink events = new TestNodes.Sink("sink01");
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private String base;

    @BeforeAll
    static void startDatabase() throws Exception {
        postgres = EmbeddedPostgres.start();
        try (Connection connection = postgres.getPostgresDatabase().getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(Path.of("samples/hotdeal/schema.sql")));
            statement.execute("""
                INSERT INTO deals (id, source, source_post_id, post_url, title, price, posted_at) VALUES
                  ('clien:1',   'clien',   '1', 'u1', '무선 키보드',   39900, '2026-11-10T09:00:00+09:00'),
                  ('ruliweb:2', 'ruliweb', '2', 'u2', '게임 번들',     4900,  '2026-11-10T10:00:00+09:00'),
                  ('ppomppu:3', 'ppomppu', '3', 'u3', '기계식 키보드', 89000, '2026-11-09T23:00:00+09:00'),
                  ('clien:4',   'clien',   '4', 'u4', '날짜 없는 글',  NULL,  NULL)
                """);
            statement.execute("""
                INSERT INTO deal_snapshots (deal_id, collected_at, price, comments, hits) VALUES
                  ('clien:1', '2026-11-10T09:15:00+09:00', 39900, 2, 100),
                  ('clien:1', '2026-11-10T09:30:00+09:00', 35900, 5, 240)
                """);
        }
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        postgres.close();
    }

    @BeforeEach
    void configure() {
        node.setUrl(postgres.getJdbcUrl("postgres", "postgres"));
        node.setUser("postgres");
        node.setPassword("postgres");
        node.setPort(0);
        node.setRoutes(ROUTES);
        node.setWarningListener((n, m) -> warnings.add(m));
        node.connect(events);
    }

    private void start() throws Exception {
        node.start();
        events.start();
        assertNull(node.getFailure(), String.valueOf(node.getFailure()));
        base = "http://127.0.0.1:" + node.getBoundPort();
    }

    @AfterEach
    void stopNode() throws Exception {
        node.stop(); events.stop();
        if (node.getThread() != null) node.getThread().join(3_000);
        if (events.getThread() != null) events.getThread().join(3_000);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonObject json(HttpResponse<String> response) {
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    private static List<String> ids(JsonObject body) {
        JsonArray items = body.getAsJsonArray("items");
        List<String> ids = new java.util.ArrayList<>();
        items.forEach(item -> ids.add(item.getAsJsonObject().get("id").getAsString()));
        return ids;
    }

    @Test
    void routesReturnRowsAsJson() throws Exception {
        start();
        HttpResponse<String> response = get("/deals");
        assertEquals(200, response.statusCode());
        assertEquals("application/json; charset=utf-8", response.headers().firstValue("Content-Type").orElse(null));
        assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));

        JsonObject body = json(response);
        assertEquals(List.of("ruliweb:2", "clien:1", "ppomppu:3", "clien:4"), ids(body), "SQL의 ORDER BY 그대로");
        assertEquals(4, body.get("count").getAsInt());
        JsonObject first = body.getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals(4900, first.get("price").getAsInt(), "숫자는 숫자로");
        assertTrue(first.get("posted_at").getAsString().startsWith("2026-11-10T"), "시각은 ISO 8601: " + first.get("posted_at"));
        assertTrue(first.get("shop").isJsonNull(), "비어 있는 값은 null로 키를 유지");
        assertEquals("ok", json(get("/health")).getAsJsonArray("items").get(0).getAsJsonObject().get("status").getAsString());

        assertTrue(events.awaitMessages(1, 2_000));
        assertEquals("/deals", events.received.get(0).getString("path"));
        assertEquals(200, ((Number) events.received.get(0).get("status")).intValue());
    }

    @Test
    void queryStringAndPathVariablesFillNamedParameters() throws Exception {
        start();
        assertEquals(List.of("clien:1", "clien:4"), ids(json(get("/deals?source=clien"))));
        assertEquals(List.of("ruliweb:2", "clien:1"), ids(json(get("/deals?since=2026-11-10T00:00:00%2B09:00"))), ":since를 두 번 써도 됨");
        assertEquals(List.of("clien:1", "ppomppu:3"), ids(json(get("/deals?q=%ED%82%A4%EB%B3%B4%EB%93%9C"))), "키보드");
        assertEquals(List.of("clien:1", "ppomppu:3"), ids(json(get("/deals?limit=2&offset=1"))));

        JsonObject deal = json(get("/deals/clien%3A1"));
        assertEquals("무선 키보드", deal.getAsJsonArray("items").get(0).getAsJsonObject().get("title").getAsString(), "경로 변수");
        JsonArray snapshots = json(get("/deals/clien:1/snapshots")).getAsJsonArray("items");
        assertEquals(2, snapshots.size());
        assertEquals(35900, snapshots.get(1).getAsJsonObject().get("price").getAsInt(), "수집 시각순");
        assertEquals(404, get("/deals/nope:1").statusCode(), "경로 변수가 있는 라우트는 결과가 없으면 404");
        assertEquals(0, json(get("/deals?source=nope")).get("count").getAsInt(), "변수 없는 라우트는 빈 목록");
    }

    @Test
    void badValuesUnknownPathsAndMethodsAreClientErrors() throws Exception {
        start();
        HttpResponse<String> badLimit = get("/deals?limit=abc");
        assertEquals(400, badLimit.statusCode(), badLimit.body());
        assertEquals(400, get("/deals?since=not-a-date").statusCode());
        assertEquals(404, get("/nothing").statusCode());
        assertEquals(405, CLIENT.send(HttpRequest.newBuilder(URI.create(base + "/deals")).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(List.of(), warnings, "클라이언트 오류는 경고가 아님");
    }

    @Test
    void badRouteSqlFailsAtStart() throws Exception {
        node.setRoutes("/x = SELEC 1");
        node.start();
        node.getThread().join(5_000);
        assertNotNull(node.getFailure());
        assertTrue(node.getFailure().getMessage().startsWith("DB 연결 또는 라우트 SQL 오류"), node.getFailure().getMessage());

        ApiServerNode.Route route = ApiServerNode.parseRoutes("/a/{x}/b = SELECT :x, :y::int, :x").get(0);
        assertEquals("SELECT ?, ?::int, ?", route.sql());
        assertEquals(List.of("x", "y", "x"), route.parameters());
        assertEquals(List.of("x"), route.variables());
        assertTrue(route.path().matcher("/a/clien:1/b").matches());
        assertThrows(IllegalArgumentException.class, () -> ApiServerNode.parseRoutes("deals = SELECT 1"), "경로는 /로 시작");
    }
}
