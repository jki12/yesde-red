package node.functional;

import node.Message;
import org.junit.jupiter.api.Test;
import support.TestNodes;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FilterNodeTest {

    private static List<String> passed(String condition, Message... messages) throws Exception {
        FilterNode node = new FilterNode("f1");
        node.setCondition(condition);
        TestNodes.Sink sink = new TestNodes.Sink("s1");
        node.connect(sink);
        node.start(); sink.start();
        assertNull(node.getFailure(), String.valueOf(node.getFailure()));
        for (Message message : messages) node.receive(message);
        long deadline = System.currentTimeMillis() + 3_000;
        while (!node.getInbox().isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10);
        Thread.sleep(100);
        node.stop(); sink.stop();
        node.getThread().join(2_000); sink.getThread().join(2_000);
        return sink.received.stream().map(m -> m.getString("title")).toList();
    }

    @Test
    void passesOnlyMatchingMessages() throws Exception {
        List<String> titles = passed("price < 20000 and title contains \"키보드\"",
                new Message(Map.of("title", "무선 키보드", "price", 15000L)),
                new Message(Map.of("title", "기계식 키보드", "price", 89000L)),
                new Message(Map.of("title", "마우스", "price", 9000L)),
                new Message(Map.of("title", "값 없는 키보드")));
        assertEquals(List.of("무선 키보드"), titles);
    }

    @Test
    void emptyConditionPassesEverything() throws Exception {
        assertEquals(List.of("a", "b"), passed("  ", new Message(Map.of("title", "a")), new Message(Map.of("title", "b"))));
    }

    @Test
    void badConditionFailsAtStart() throws Exception {
        FilterNode node = new FilterNode("f2");
        node.setCondition("price <");
        node.start();
        node.getThread().join(2_000);
        assertNotNull(node.getFailure());
        assertTrue(node.getFailure().getMessage().startsWith("조건식 오류"), node.getFailure().getMessage());
    }
}
