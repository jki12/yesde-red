package node.functional;

import node.Message;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import support.TestNodes;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class DedupeNodeTest {
    private final DedupeNode node = new DedupeNode("dedupe1");
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
    void passesOnlyTheFirstMessageForEachKeyValue() throws Exception {
        node.setKey("link");
        start();
        node.receive(new Message(Map.of("link", "/a", "title", "첫 글")));
        node.receive(new Message(Map.of("link", "/b", "title", "둘째 글")));
        node.receive(new Message(Map.of("link", "/a", "title", "첫 글 (다시)")));
        node.receive(new Message(Map.of("link", "/c", "title", "셋째 글")));

        assertTrue(sink.awaitMessages(3, 5_000));
        Thread.sleep(100);
        assertEquals(3, sink.received.size(), "같은 link는 한 번만");
        assertEquals(List.of("첫 글", "둘째 글", "셋째 글"), sink.received.stream().map(m -> m.getString("title")).toList());
        assertEquals(3, node.getSeenCount());
        assertTrue(warnings.isEmpty());
    }

    @Test
    void messagesWithoutTheKeyPassThroughWithAWarning() throws Exception {
        node.setKey("link");
        start();
        node.receive(new Message("no link here"));
        node.receive(new Message("no link here"));

        assertTrue(sink.awaitMessages(2, 5_000));
        assertEquals(2, warnings.size());
        assertTrue(warnings.get(0).contains("link"));
    }

    @Test
    void forgetsTheOldestValuesBeyondMaxEntries() {
        node.setMaxEntries(2);
        assertTrue(node.remember("a"));
        assertTrue(node.remember("b"));
        assertTrue(node.remember("c"), "c를 기억하면서 a는 잊음");
        assertEquals(2, node.getSeenCount());
        assertTrue(node.remember("a"), "잊은 a는 다시 새 값으로 봄");
        assertFalse(node.remember("c"));
    }
}
