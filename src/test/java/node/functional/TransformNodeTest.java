package node.functional;

import node.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import support.TestNodes;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class TransformNodeTest {
    private final TransformNode node = new TransformNode("tf01");
    private final TestNodes.Sink sink = new TestNodes.Sink("sink01");
    private final List<String> warnings = new CopyOnWriteArrayList<>();

    @AfterEach
    void stop() throws Exception {
        node.stop(); sink.stop();
        if (node.getThread() != null) node.getThread().join(2_000);
        if (sink.getThread() != null) sink.getThread().join(2_000);
    }

    private void start() {
        node.setWarningListener((n, message) -> warnings.add(message));
        node.connect(sink);
        node.start(); sink.start();
    }

    @Test
    void buildsACommonSchemaFromACommunityRow() throws Exception {
        node.setRules("""
                source = "clien"
                source_post_id = regex(link, "/jirum/(\\\\d+)")
                id = format("{source}:{source_post_id}")
                post_url = replace(link, "\\\\?.*$", "")
                shop = regex(title, "^\\\\[(.+?)\\\\]")
                price = number(regex(title, "\\\\(([^)]*원)"))
                posted_at = datetime(time, "yyyy-MM-dd HH:mm:ss")
                collected_at = now()
                """);
        node.setKeep("id\nsource\nsource_post_id\npost_url\ntitle\nshop\nprice\nposted_at\ncollected_at");
        start();
        node.receive(new Message(Map.of("url", "https://www.clien.net/service/board/jirum",
                "title", "[롯데온] 쌤소나이트 백팩 (73,470원)", "link", "https://www.clien.net/service/board/jirum/19289962?od=T31",
                "time", "2026-11-09 20:50:06", "hits", "555")));

        assertTrue(sink.awaitMessages(1, 5_000));
        Message out = sink.received.get(0);
        assertEquals(List.of("id", "source", "source_post_id", "post_url", "title", "shop", "price", "posted_at", "collected_at"), List.copyOf(out.getPayload().keySet()), "남길 필드 순서대로만");
        assertEquals("clien:19289962", out.get("id"));
        assertEquals("https://www.clien.net/service/board/jirum/19289962", out.get("post_url"));
        assertEquals("롯데온", out.get("shop"));
        assertEquals(73470L, out.get("price"));
        assertEquals("2026-11-09T20:50:06+09:00", out.get("posted_at"));
        assertTrue(out.getString("collected_at").endsWith("+09:00"));
        assertNull(out.get("hits"), "남길 필드에 없는 것은 제거");
        assertTrue(warnings.isEmpty());
    }

    @Test
    void keepsAllFieldsWhenKeepIsEmptyAndSkipsMessagesThatFailToConvert() throws Exception {
        node.setRules("posted_at = datetime(time, \"rfc1123\")");
        start();
        node.receive(new Message(Map.of("title", "a", "time", "어제쯤")));
        node.receive(new Message(Map.of("title", "b", "time", "Mon, 09 Nov 2026 21:17:40 +0900")));

        assertTrue(sink.awaitMessages(1, 5_000));
        assertEquals(1, warnings.size(), "변환 실패는 경고 한 건");
        assertTrue(warnings.get(0).contains("시각을 읽을 수 없습니다"));
        Message out = sink.received.get(0);
        assertEquals("b", out.get("title"), "원래 필드 유지");
        assertEquals("2026-11-09T21:17:40+09:00", out.get("posted_at"));
    }

    @Test
    void ruleSyntaxErrorsFailTheNodeAtStart() throws Exception {
        node.setRules("price = number(title");
        node.start();
        node.getThread().join(2_000);
        assertNotNull(node.getFailure());
        assertTrue(node.getFailure().getMessage().startsWith("변환 규칙 오류"));
    }
}
