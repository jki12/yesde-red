package node.functional;

import node.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import support.TestNodes;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class JsonExtractNodeTest {
    private static final String JSON = "{\"data\": {\"items\": [{\"name\": \"a\", \"price\": {\"amount\": 10}}, {\"name\": \"b\"}]}}";

    private final JsonExtractNode node = new JsonExtractNode("json01");
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
    void emitsOneMessagePerArrayItemWithConvertedValues() throws Exception {
        node.setItems("data.items[*]");
        node.setFields("name = name\nprice = price.amount");
        run(new Message(Map.of("url", "https://api.example/items", "body", JSON)));

        assertTrue(sink.awaitMessages(2, 5_000));
        assertEquals("a", sink.received.get(0).get("name"));
        assertEquals(10L, sink.received.get(0).get("price"));
        assertEquals("https://api.example/items", sink.received.get(0).get("url"), "상류 필드 유지");
        assertNull(sink.received.get(0).get("body"), "본문은 제거");
        assertNull(sink.received.get(1).get("price"), "없는 필드는 null");
        assertTrue(warnings.isEmpty());
    }

    @Test
    void wholeDocumentIsOneItemWhenItemsPathIsEmpty() throws Exception {
        node.setFields("first = data.items[0].name");
        run(new Message(Map.of("body", JSON)));

        assertTrue(sink.awaitMessages(1, 5_000));
        assertEquals("a", sink.received.get(0).get("first"));
    }

    @Test
    void invalidJsonAndBadPathsAreWarnings() throws Exception {
        node.setItems("data.items[*]");
        node.setFields("name = name");
        run(new Message(Map.of("url", "u", "body", "<html>not json</html>")));
        assertTrue(awaitWarnings(1));
        assertTrue(warnings.get(0).startsWith("JSON을 읽을 수 없습니다"));

        node.setItems("data.count[*]");
        node.receive(new Message(Map.of("body", "{\"data\": {\"count\": 3}}")));
        assertTrue(awaitWarnings(2));
        assertTrue(warnings.get(1).startsWith("경로 오류"));
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
