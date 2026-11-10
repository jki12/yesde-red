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

/** embedded PostgreSQL에 글 몇 개를 넣고, 노드가 띄운 API를 실제 HTTP로 호출합니다. */
class ApiServerNodeTest {
    private static EmbeddedPostgres postgres;
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private final ApiServerNode node = new ApiServerNode("api01");
    private final TestNodes.Sink events = new TestNodes.Sink("sink01");
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private String base;

    @BeforeAll
    static void startDatabase() throws Exception {
        postgres = EmbeddedPostgres.start();
        try (Connection connection = postgres.getPostgresDatabase().getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(Path.of("db/schema.sql")));
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
    void startNode() throws Exception {
        node.setUrl(postgres.getJdbcUrl("postgres", "postgres"));
        node.setUser("postgres");
        node.setPassword("postgres");
        node.setPort(0);
        node.setWarningListener((n, m) -> warnings.add(m));
        node.connect(events);
        node.start();
        events.start();
        assertNull(node.getFailure(), String.valueOf(node.getFailure()));
        base = "http://127.0.0.1:" + node.getBoundPort();
    }

    @AfterEach
    void stopNode() throws Exception {
        node.stop(); events.stop();
        node.getThread().join(3_000); events.getThread().join(3_000);
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
    void listsDealsNewestFirstWithFieldsAsJson() throws Exception {
        HttpResponse<String> response = get("/deals");
        assertEquals(200, response.statusCode());
        assertEquals("application/json; charset=utf-8", response.headers().firstValue("Content-Type").orElse(null));
        assertEquals("*", response.headers().firstValue("Access-Control-Allow-Origin").orElse(null));

        JsonObject body = json(response);
        assertEquals(List.of("ruliweb:2", "clien:1", "ppomppu:3", "clien:4"), ids(body), "최신 글부터, 날짜 없는 글은 마지막");
        assertEquals(4, body.get("total").getAsInt());
        JsonObject first = body.getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals(4900, first.get("price").getAsInt(), "숫자는 숫자로");
        assertTrue(first.get("posted_at").getAsString().startsWith("2026-11-10T"), "시각은 ISO 8601: " + first.get("posted_at"));
        assertTrue(first.get("shop").isJsonNull(), "비어 있는 값은 null");

        assertTrue(events.awaitMessages(1, 2_000));
        assertEquals("/deals", events.received.get(0).getString("path"));
        assertEquals(200, ((Number) events.received.get(0).get("status")).intValue());
    }

    @Test
    void filtersAndPagesTheList() throws Exception {
        assertEquals(List.of("clien:1", "clien:4"), ids(json(get("/deals?source=clien"))));
        assertEquals(List.of("ruliweb:2", "clien:1"), ids(json(get("/deals?since=2026-11-10T00:00:00%2B09:00"))), "since는 posted_at 하한");
        assertEquals(List.of("clien:1", "ppomppu:3"), ids(json(get("/deals?q=%ED%82%A4%EB%B3%B4%EB%93%9C"))), "q는 제목 검색(키보드)");

        JsonObject page = json(get("/deals?limit=2&offset=1"));
        assertEquals(List.of("clien:1", "ppomppu:3"), ids(page));
        assertEquals(4, page.get("total").getAsInt(), "total은 필터 기준 전체 수");
        assertEquals(2, page.get("limit").getAsInt());
        assertEquals(1, page.get("offset").getAsInt());
    }

    @Test
    void returnsOneDealWithItsSnapshotHistory() throws Exception {
        HttpResponse<String> response = get("/deals/clien%3A1"); // 콜론을 인코딩해도, 그대로 보내도 됩니다
        assertEquals(200, response.statusCode());
        JsonObject deal = json(response);
        assertEquals("무선 키보드", deal.get("title").getAsString());
        JsonArray snapshots = deal.getAsJsonArray("snapshots");
        assertEquals(2, snapshots.size());
        assertEquals(39900, snapshots.get(0).getAsJsonObject().get("price").getAsInt(), "수집 시각순");
        assertEquals(35900, snapshots.get(1).getAsJsonObject().get("price").getAsInt());
        assertTrue(snapshots.get(1).getAsJsonObject().get("collected_at").getAsString().contains("T"), "ISO 8601");

        assertEquals(0, json(get("/deals/clien:4")).getAsJsonArray("snapshots").size(), "이력이 없으면 빈 배열");
        assertEquals(404, get("/deals/nope:1").statusCode());
        assertEquals(404, get("/deals/").statusCode());
    }

    @Test
    void badInputAndUnknownPathsAreClientErrors() throws Exception {
        assertEquals(400, get("/deals?limit=0").statusCode());
        assertEquals(400, get("/deals?limit=abc").statusCode());
        assertEquals(400, get("/deals?limit=1000").statusCode(), "maxLimit 초과");
        HttpResponse<String> badSince = get("/deals?since=not-a-date"); // "yesterday"는 PostgreSQL이 실제로 받아들이는 표현
        assertEquals(400, badSince.statusCode());
        assertTrue(json(badSince).get("error").getAsString().contains("since"), badSince.body());
        assertEquals(404, get("/nothing").statusCode());
        assertEquals(405, CLIENT.send(HttpRequest.newBuilder(URI.create(base + "/deals")).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals("ok", json(get("/health")).get("status").getAsString());
        assertEquals(List.of(), warnings, "클라이언트 오류는 경고가 아님");
    }
}
