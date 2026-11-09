package node.functional;

import node.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import support.TestNodes;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class HtmlExtractNodeTest {
    private static final String HTML = """
            <html><body>
              <div class="item"><h2><a href="/a/1">첫 글</a></h2><span class="price">1,000원</span></div>
              <div class="item"><h2><a href="https://other.example/b">둘째 글</a></h2></div>
            </body></html>
            """;

    private final HtmlExtractNode node = new HtmlExtractNode("html01");
    private final TestNodes.Sink sink = new TestNodes.Sink("sink01");
    private final List<String> warnings = new CopyOnWriteArrayList<>();

    @AfterEach
    void stop() throws Exception {
        node.stop(); sink.stop();
        if (node.getThread() != null) node.getThread().join(2_000);
        if (sink.getThread() != null) sink.getThread().join(2_000);
    }

    private void run(Message input) throws Exception {
        node.setWarningListener((n, message) -> warnings.add(message));
        node.connect(sink);
        node.start(); sink.start();
        node.receive(input);
    }

    @Test
    void emitsOneMessagePerItemWithTextAndAbsoluteLinks() throws Exception {
        node.setSelector("div.item");
        node.setFields("title = h2 a\nlink = h2 a @href\nprice = .price");
        run(new Message(Map.of("url", "https://site.example/list", "body", HTML, "status", 200)));

        assertTrue(sink.awaitMessages(2, 5_000));
        Message first = sink.received.get(0);
        assertEquals("첫 글", first.get("title"));
        assertEquals("https://site.example/a/1", first.get("link"), "상대 주소는 payload.url 기준 절대 주소로");
        assertEquals("1,000원", first.get("price"));
        assertEquals("https://site.example/list", first.get("url"), "상류 필드 유지");
        assertNull(first.get("body"), "본문은 제거");

        Message second = sink.received.get(1);
        assertEquals("둘째 글", second.get("title"));
        assertEquals("https://other.example/b", second.get("link"));
        assertNull(second.get("price"), "없는 필드는 null");
        assertTrue(warnings.isEmpty());
    }

    @Test
    void emptySelectorTreatsTheWholeDocumentAsOneItem() throws Exception {
        node.setFields("count = div.item\nfirst = .item h2 a");
        run(new Message(Map.of("body", HTML)));

        assertTrue(sink.awaitMessages(1, 5_000));
        assertEquals("첫 글", sink.received.get(0).get("first"));
    }

    @Test
    void missingBodyBadFieldsAndBadSelectorsAreWarnings() throws Exception {
        node.setSelector("div.item");
        node.setFields("broken line");
        run(new Message("no body"));
        node.receive(new Message(Map.of("body", HTML)));
        node.setFields("title = h2 a");
        node.setSelector("div[");
        node.receive(new Message(Map.of("body", HTML)));

        Thread.sleep(400);
        assertEquals(3, warnings.size(), "경고: " + warnings);
        assertTrue(warnings.get(0).contains("body가 없습니다"));
        assertTrue(warnings.get(1).startsWith("필드 옵션 오류"));
        assertTrue(warnings.get(2).startsWith("선택자 오류"));
        assertTrue(sink.received.isEmpty());
        assertNull(node.getFailure());
    }
}
