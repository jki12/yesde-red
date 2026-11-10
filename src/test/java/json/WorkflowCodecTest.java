package json;

import node.BaseNode;
import node.NodeType;
import node.PollingNode;
import node.functional.InjectNode;
import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowCodecTest {

    @Test
    void roundTripsNodesOptionsAndConnectionsThroughJson() {
        InjectNode inject = new InjectNode("aaa111");
        inject.setMessage("hello");
        inject.setIntervalMillis(250);
        inject.setLocation(new Point(40, 50));
        BaseNode debug = node.NodeFactory.createNode(NodeType.DEBUG, "bbb222");
        debug.setLocation(new Point(400, 60));
        inject.connect((node.Inputable) debug);

        String json = WorkflowCodec.toJson(WorkflowCodec.toDocument("테스트 흐름", List.of(inject, debug)));
        assertTrue(json.contains("\"aaa111\"") && json.contains("\"INJECT\"") && json.contains("\"hello\""));

        WorkflowDocument document = WorkflowCodec.fromJson(json);
        assertEquals("테스트 흐름", document.name());
        assertEquals(WorkflowDocument.CURRENT_VERSION, document.version());
        assertEquals(2, document.nodes().size());
        assertEquals(List.of(new WorkflowDocument.Connection("aaa111", "bbb222")), document.connections());

        List<BaseNode> restored = WorkflowCodec.toNodes(document);
        InjectNode restoredInject = (InjectNode) restored.get(0);
        assertEquals("aaa111", restoredInject.getId());
        assertEquals(new Point(40, 50), new Point(restoredInject.getX(), restoredInject.getY()));
        assertEquals("hello", restoredInject.getMessage());
        assertEquals(250, restoredInject.getIntervalMillis(), "JSON의 Double이 long 옵션으로 돌아옴");
        assertEquals(NodeType.DEBUG, restored.get(1).getType());
        assertTrue(restoredInject.getTargets().contains(restored.get(1)), "연결이 복원됨");
    }

    @Test
    void connectionsToNodesOutsideTheSetAreDropped() {
        InjectNode inject = new InjectNode("aaa111");
        BaseNode outside = node.NodeFactory.createNode(NodeType.DEBUG, "zzz999");
        inject.connect((node.Inputable) outside);

        WorkflowDocument document = WorkflowCodec.toDocument("w", List.of(inject));
        assertTrue(document.connections().isEmpty());
    }

    @Test
    void rejectsBrokenDocumentsWithReadableMessages() {
        assertThrows(IllegalArgumentException.class, () -> WorkflowCodec.fromJson("not json"));
        assertThrows(IllegalArgumentException.class, () -> WorkflowCodec.fromJson(""));
        assertThrows(IllegalArgumentException.class, () -> WorkflowCodec.fromJson("{\"version\": 99, \"name\": \"x\", \"nodes\": []}"));

        WorkflowDocument.Node a = new WorkflowDocument.Node("a", "INJECT", 0, 0, Map.of());
        WorkflowDocument.Node dupe = new WorkflowDocument.Node("a", "DEBUG", 0, 0, Map.of());
        WorkflowDocument.Node unknown = new WorkflowDocument.Node("b", "TELEPORT", 0, 0, Map.of());
        WorkflowDocument.Node d = new WorkflowDocument.Node("d", "DEBUG", 0, 0, Map.of());

        assertEquals("노드 ID가 중복됩니다: a", message(() -> WorkflowCodec.toNodes(doc(List.of(a, dupe), List.of()))));
        assertTrue(message(() -> WorkflowCodec.toNodes(doc(List.of(unknown), List.of()))).startsWith("알 수 없는 노드 타입입니다: TELEPORT"));
        assertTrue(message(() -> WorkflowCodec.toNodes(doc(List.of(a), List.of(new WorkflowDocument.Connection("a", "ghost"))))).startsWith("연결이 없는 노드를 가리킵니다"));
        assertTrue(message(() -> WorkflowCodec.toNodes(doc(List.of(a, d), List.of(new WorkflowDocument.Connection("d", "a"))))).startsWith("연결 방향이 맞지 않습니다"));
        WorkflowDocument.Node badOption = new WorkflowDocument.Node("a", "INJECT", 0, 0, Map.of("intervalMillis", "abc"));
        assertTrue(message(() -> WorkflowCodec.toNodes(doc(List.of(badOption), List.of()))).contains("숫자여야"));
    }

    @Test
    void bundledSampleWorkflowsLoad() throws Exception {
        Map<String, int[]> expected = Map.of("/samples/json-api.json", new int[] {5, 4}, "/samples/html-list.json", new int[] {5, 4},
                "/samples/hotdeal.json", new int[] {15, 14}, "/samples/mock-hotdeal.json", new int[] {17, 15});
        for (Map.Entry<String, int[]> sample : expected.entrySet()) {
            try (var in = getClass().getResourceAsStream(sample.getKey())) {
                assertNotNull(in, sample.getKey() + " 리소스가 없음");
                WorkflowDocument document = WorkflowCodec.fromJson(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                List<BaseNode> nodes = WorkflowCodec.toNodes(document);
                assertEquals(sample.getValue()[0], nodes.size(), sample.getKey());
                assertEquals(sample.getValue()[1], document.connections().size(), sample.getKey());
                assertEquals(sample.getValue()[1], nodes.stream().filter(n -> n.getTargets() != null).mapToLong(n -> n.getTargets().size()).sum(), sample.getKey() + " 연결 복원");
            }
        }
    }

    private static WorkflowDocument doc(List<WorkflowDocument.Node> nodes, List<WorkflowDocument.Connection> connections) {
        return new WorkflowDocument(WorkflowDocument.CURRENT_VERSION, "w", nodes, connections);
    }

    private static String message(Runnable action) {
        return assertThrows(IllegalArgumentException.class, action::run).getMessage();
    }
}
