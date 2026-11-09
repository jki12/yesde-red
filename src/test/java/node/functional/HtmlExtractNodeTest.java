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

    private static final String RSS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel><title>핫딜</title>
              <item><title>[스팀] 게임 (4,480원/무료)</title><link>https://bbs.example/read/108874</link>
                    <category>게임S/W</category><pubDate>Mon, 09 Nov 2026 21:17:40 +0900</pubDate></item>
              <item><title>[자사몰] 이불 (54,900원)</title><link>http://www.example/view.php?id=hot&amp;no=747388</link></item>
            </channel></rss>
            """;

    @Test
    void xmlModeKeepsRssLinkTextThatTheHtmlParserDrops() throws Exception {
        node.setXml(true);
        node.setSelector("item");
        node.setFields("title = title\nlink = link\ncategory = category\ntime = pubDate");
        run(new Message(Map.of("body", RSS)));

        assertTrue(sink.awaitMessages(2, 5_000));
        Message first = sink.received.get(0);
        assertEquals("[스팀] 게임 (4,480원/무료)", first.get("title"));
        assertEquals("https://bbs.example/read/108874", first.get("link"), "XML 파서는 <link> 본문을 보존");
        assertEquals("게임S/W", first.get("category"));
        assertEquals("Mon, 09 Nov 2026 21:17:40 +0900", first.get("time"), "대소문자 섞인 태그도 선택됨");
        assertEquals("http://www.example/view.php?id=hot&no=747388", sink.received.get(1).get("link"), "엔티티가 풀림");
        assertNull(sink.received.get(1).get("category"));
    }

    @Test
    void htmlModeLosesRssLinkText() throws Exception {
        node.setSelector("item");
        node.setFields("link = link");
        run(new Message(Map.of("body", RSS)));

        assertTrue(sink.awaitMessages(1, 5_000));
        assertEquals("", sink.received.get(0).get("link"), "HTML 파서는 <link>를 빈 태그로 취급 — RSS에는 XML 옵션을 켜야 함");
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
        assertTrue(awaitWarnings(1), "body 없음 경고");
        assertTrue(warnings.get(0).contains("body가 없습니다"));

        node.receive(new Message(Map.of("body", HTML)));
        assertTrue(awaitWarnings(2), "필드 옵션 경고");
        assertTrue(warnings.get(1).startsWith("필드 옵션 오류"));

        node.setFields("title = h2 a"); // 옵션 변경은 이전 메시지 처리가 끝난 뒤에
        node.setSelector("div[");
        node.receive(new Message(Map.of("body", HTML)));
        assertTrue(awaitWarnings(3), "선택자 경고");
        assertTrue(warnings.get(2).startsWith("선택자 오류"));

        assertTrue(sink.received.isEmpty());
        assertNull(node.getFailure());
    }

    private boolean awaitWarnings(int count) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (warnings.size() < count) {
            if (System.currentTimeMillis() > deadline) return false;
            Thread.sleep(10);
        }
        return true;
    }
}
