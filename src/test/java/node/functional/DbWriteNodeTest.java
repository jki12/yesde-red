package node.functional;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import node.Message;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** Docker 없이 임시 디렉터리에 띄운 진짜 PostgreSQL에 db/schema.sql을 적용하고 노드를 검사합니다. */
class DbWriteNodeTest {
    private static EmbeddedPostgres postgres;
    private static String url;

    @BeforeAll
    static void startDatabase() throws Exception {
        postgres = EmbeddedPostgres.start();
        url = postgres.getJdbcUrl("postgres", "postgres");
        try (Connection connection = postgres.getPostgresDatabase().getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(Files.readString(Path.of("db/schema.sql")));
        }
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        postgres.close();
    }

    @BeforeEach
    void clearTables() throws Exception {
        try (Connection connection = postgres.getPostgresDatabase().getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE deal_snapshots, deals");
        }
    }

    private static DbWriteNode node(String columns) {
        DbWriteNode node = new DbWriteNode("db1");
        node.setUrl(url);
        node.setUser("postgres");
        node.setPassword("postgres");
        node.setColumns(columns);
        return node;
    }

    /** 노드를 시작해 메시지를 모두 넣고, 받은 메시지 수만큼 처리될 때까지 기다린 뒤 멈춥니다. */
    private static List<String> run(DbWriteNode node, Message... messages) throws Exception {
        List<String> warnings = new CopyOnWriteArrayList<>();
        node.setWarningListener((n, m) -> warnings.add(m));
        node.start();
        for (Message message : messages) node.receive(message);
        long deadline = System.currentTimeMillis() + 5_000;
        while (!node.getInbox().isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10);
        Thread.sleep(100); // 마지막 메시지의 commit까지
        node.stop();
        node.getThread().join(3_000);
        return warnings;
    }

    private static Map<String, Object> deal(String id, String title, long price, String collectedAt) {
        Map<String, Object> deal = new LinkedHashMap<>();
        deal.put("id", id);
        deal.put("source", "clien");
        deal.put("source_post_id", id.substring(id.indexOf(':') + 1));
        deal.put("post_url", "https://example/" + id);
        deal.put("title", title);
        deal.put("price", price);
        deal.put("hits", 2100L);
        deal.put("posted_at", "2026-11-10T09:12:00+09:00");
        deal.put("collected_at", collectedAt);
        return deal;
    }

    private static final String MAPPING = """
            id
            source
            source_post_id
            post_url
            title
            price
            hits
            posted_at
            last_seen_at = collected_at
            """;

    @Test
    void insertsThenUpdatesTheRowWithTheSameKey() throws Exception {
        List<String> warnings = run(node(MAPPING),
                new Message(deal("clien:1", "키보드", 39900, "2026-11-10T10:00:00+09:00")),
                new Message(deal("clien:1", "키보드 (가격 인하)", 35900, "2026-11-10T10:15:00+09:00")),
                new Message(deal("clien:2", "마우스", 12000, "2026-11-10T10:15:00+09:00")));
        assertEquals(List.of(), warnings);

        try (Connection connection = postgres.getPostgresDatabase().getConnection(); Statement statement = connection.createStatement()) {
            ResultSet rows = statement.executeQuery("SELECT id, title, price, hits, posted_at::text, last_seen_at::text, first_seen_at > now() - interval '1 minute' FROM deals ORDER BY id");
            assertTrue(rows.next());
            assertEquals("clien:1", rows.getString(1));
            assertEquals("키보드 (가격 인하)", rows.getString(2), "같은 키는 갱신");
            assertEquals(35900, rows.getLong(3));
            assertEquals(2100, rows.getInt(4));
            assertTrue(rows.getString(5).startsWith("2026-11-10 "), "ISO 문자열이 TIMESTAMPTZ로 들어감: " + rows.getString(5));
            assertTrue(rows.getString(6).contains("10:15:00") || rows.getString(6).contains("01:15:00"), "last_seen_at은 두 번째 수집 시각: " + rows.getString(6));
            assertTrue(rows.getBoolean(7), "first_seen_at은 DB 기본값(now())으로 INSERT 때 정해짐");
            assertTrue(rows.next());
            assertEquals("clien:2", rows.getString(1));
            assertFalse(rows.next(), "행은 두 개뿐");
        }
    }

    @Test
    void snapshotTableGetsOneRowPerCollection() throws Exception {
        DbWriteNode node = node(MAPPING);
        node.setSnapshotTable("deal_snapshots");
        node.setSnapshotColumns("deal_id = id\ncollected_at\nprice\nhits");
        List<String> warnings = run(node,
                new Message(deal("clien:1", "키보드", 39900, "2026-11-10T10:00:00+09:00")),
                new Message(deal("clien:1", "키보드", 35900, "2026-11-10T10:15:00+09:00")));
        assertEquals(List.of(), warnings);

        try (Connection connection = postgres.getPostgresDatabase().getConnection(); Statement statement = connection.createStatement()) {
            ResultSet rows = statement.executeQuery("SELECT (SELECT count(*) FROM deals), string_agg(price::text, ',' ORDER BY collected_at) FROM deal_snapshots");
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1), "deals는 한 행");
            assertEquals("39900,35900", rows.getString(2), "스냅샷은 수집마다 한 행");
        }
    }

    @Test
    void withoutColumnOptionPayloadFieldNamesAreTheColumns() throws Exception {
        Map<String, Object> deal = deal("ruliweb:7", "보온병", 8900, "x");
        deal.remove("collected_at");
        List<String> warnings = run(node(null), new Message(deal));
        assertEquals(List.of(), warnings);

        try (Connection connection = postgres.getPostgresDatabase().getConnection(); Statement statement = connection.createStatement()) {
            ResultSet rows = statement.executeQuery("SELECT count(*), max(title) FROM deals");
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1));
            assertEquals("보온병", rows.getString(2));
        }
    }

    @Test
    void aBadRowIsAWarningAndTheNodeKeepsWriting() throws Exception {
        Map<String, Object> noTitle = deal("clien:9", null, 1, "2026-11-10T10:00:00+09:00"); // title NOT NULL 위반
        List<String> warnings = run(node(MAPPING),
                new Message(noTitle),
                new Message(deal("clien:10", "정상", 1, "2026-11-10T10:00:00+09:00")));

        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("title"), warnings.get(0));
        try (Connection connection = postgres.getPostgresDatabase().getConnection(); Statement statement = connection.createStatement()) {
            ResultSet rows = statement.executeQuery("SELECT string_agg(id, ',') FROM deals");
            assertTrue(rows.next());
            assertEquals("clien:10", rows.getString(1), "잘못된 행만 빠지고 다음 행은 들어감");
        }
    }

    @Test
    void optionAndConnectionProblemsFailAtStart() throws Exception {
        DbWriteNode badKey = node("title\nprice");
        badKey.start();
        badKey.getThread().join(3_000);
        assertNotNull(badKey.getFailure());
        assertTrue(badKey.getFailure().getMessage().contains("키 컬럼"), badKey.getFailure().getMessage());

        DbWriteNode unreachable = node(MAPPING);
        unreachable.setUrl("jdbc:postgresql://127.0.0.1:1/nope");
        unreachable.start();
        unreachable.getThread().join(10_000);
        assertNotNull(unreachable.getFailure());
        assertTrue(unreachable.getFailure().getMessage().startsWith("DB에 연결할 수 없습니다"), unreachable.getFailure().getMessage());

        assertThrows(IllegalArgumentException.class, () -> DbWriteNode.upsertSql("deals; drop table deals", "id", Map.of("id", "id")));
        Map<String, String> ordered = new LinkedHashMap<>(); // Map.of는 순회 순서가 실행마다 달라 열 순서를 못 믿습니다
        ordered.put("id", "id");
        ordered.put("title", "title");
        assertEquals("INSERT INTO deals (id, title) VALUES (?, ?) ON CONFLICT (id) DO UPDATE SET title = EXCLUDED.title",
                DbWriteNode.upsertSql("deals", "id", ordered));
    }
}
