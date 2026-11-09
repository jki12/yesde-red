package node.option;

import node.BaseNode;
import node.NodeType;
import node.Outputable;
import org.junit.jupiter.api.Test;

import javax.swing.Icon;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NodeOptionsTest {
    enum Mode { FAST, SLOW }

    static final class Stub extends BaseNode implements Outputable {
        @OptionField String message = "hello";
        @OptionField boolean flag = true;
        @OptionField(type = OptionInputType.RADIO_BUTTON) Mode mode = Mode.SLOW;
        @OptionField long interval = 1000;
        @OptionField(editable = false) int fixed = 7;
        @OptionField static int ignoredStatic = 1;
        String notAnOption = "x";

        Stub() { super(NodeType.INJECT); }
        @Override public Icon getIcon() { return null; }
        @Override public String getDisplayName() { return "stub"; }
        @Override public String getDescription() { return ""; }
        @Override protected void process() { }
    }

    @Test
    void readsAllOptionFieldsInDeclarationOrder() {
        Map<String, Object> values = NodeOptions.read(new Stub());
        assertEquals(List.of("message", "flag", "mode", "interval", "fixed"), List.copyOf(values.keySet()));
        assertEquals("hello", values.get("message"));
        assertEquals(Mode.SLOW, values.get("mode"));
        assertEquals(1000L, values.get("interval"));
    }

    @Test
    void writesJsonStyleValuesConvertingToFieldTypes() {
        Stub node = new Stub();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("message", "bye");
        values.put("flag", false);
        values.put("mode", "FAST");      // enum은 이름 문자열로
        values.put("interval", 250.0);   // JSON 숫자는 Double로 들어옴
        values.put("fixed", 9.0);
        values.put("unknown", "ignored"); // 노드에 없는 옵션은 무시

        NodeOptions.write(node, values);

        assertEquals("bye", node.message);
        assertFalse(node.flag);
        assertEquals(Mode.FAST, node.mode);
        assertEquals(250L, node.interval);
        assertEquals(9, node.fixed);
    }

    @Test
    void rejectsValuesThatDoNotFitTheField() {
        Stub node = new Stub();
        assertThrows(IllegalArgumentException.class, () -> NodeOptions.write(node, Map.of("interval", "abc")));
        assertThrows(IllegalArgumentException.class, () -> NodeOptions.write(node, Map.of("mode", "MEDIUM")));
        assertThrows(IllegalArgumentException.class, () -> NodeOptions.write(node, Map.of("flag", "yes")));
        Map<String, Object> nullPrimitive = new LinkedHashMap<>();
        nullPrimitive.put("interval", null);
        assertThrows(IllegalArgumentException.class, () -> NodeOptions.write(node, nullPrimitive));
        assertEquals(1000L, node.interval, "실패해도 값은 바뀌지 않음");
    }
}
