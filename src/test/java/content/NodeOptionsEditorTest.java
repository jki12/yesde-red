package content;

import annotation.OptionField;
import annotation.OptionInputType;
import node.Outputable;
import node.PollingNode;
import node.Type;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.Edt;
import support.Swing;
import support.TestNodes;
import ui.UiTheme;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NodeOptionsEditorTest {
    enum Mode { FAST, SLOW }

    /** 지원하는 모든 입력 종류와 편집 불가·static 필드를 가진 스텁입니다. */
    static final class Stub extends PollingNode implements Outputable {
        @OptionField(type = OptionInputType.TEXT_AREA) String message = "hello";
        @OptionField(type = OptionInputType.SENSITIVE_TEXT) String secret;
        @OptionField(type = OptionInputType.CHECK_BOX) boolean flag = true;
        @OptionField(type = OptionInputType.RADIO_BUTTON) Mode mode = Mode.SLOW;
        @OptionField(editable = false) int fixed = 7;
        @OptionField static int ignoredStatic = 1;

        Stub() { super(Type.INJECT); }
        @Override public Icon getIcon() { return null; }
        @Override public String getDisplayName() { return "stub"; }
        @Override public String getDescription() { return ""; }
        @Override protected void process() { }
    }

    private boolean boardEditable = true;
    private Stub node;
    private NodeOptionsEditor editor;
    private JComponent panel;

    @BeforeAll
    static void installTheme() throws Exception {
        Edt.run(UiTheme::install);
    }

    @BeforeEach
    void setUp() throws Exception {
        Edt.run(() -> {
            node = new Stub();
            editor = new NodeOptionsEditor(node, () -> boardEditable);
            panel = editor.getComponent();
        });
    }

    @Test
    void buildsOneRowPerOptionFieldInDeclarationOrder() throws Exception {
        Edt.run(() -> {
            assertTrue(editor.hasOptions());
            List<String> names = new ArrayList<>();
            collectOptionNames(panel, names);
            assertEquals(List.of("option.message", "option.secret", "option.flag", "option.mode", "option.fixed", "option.intervalMilli"), names);

            assertInstanceOf(JScrollPane.class, text("message").getParent().getParent(), "여러 줄 입력은 스크롤 패널로 감쌈");
            assertEquals(130, text("message").getParent().getParent().getPreferredSize().height);
            assertEquals(32, text("intervalMilli").getPreferredSize().height);
            assertEquals(60, find("option.mode").getPreferredSize().height, "라디오는 상수 수 × 30");
        });
    }

    @Test
    void loadsCurrentValuesAndEditableFlags() throws Exception {
        Edt.run(() -> {
            assertEquals("hello", text("message").getText());
            assertEquals("", text("secret").getText());
            assertTrue(((JCheckBox) find("option.flag")).isSelected());
            assertTrue(radio("SLOW").isSelected());
            assertFalse(radio("FAST").isSelected());
            assertEquals("7", text("fixed").getText());
            assertEquals("1000", text("intervalMilli").getText());

            assertTrue(text("message").isEditable());
            assertFalse(text("fixed").isEditable(), "editable=false 필드는 읽기 전용");
            assertTrue(apply().isEnabled());
        });
    }

    @Test
    void applyWritesConvertedValuesAndLeavesNonEditableFieldsAlone() throws Exception {
        Edt.run(() -> {
            text("message").setText("");
            text("secret").setText("tok");
            ((JCheckBox) find("option.flag")).setSelected(false);
            radio("FAST").setSelected(true);
            text("intervalMilli").setText(" 250 ");
            text("fixed").setText("99");
            apply().doClick();

            assertEquals("적용했습니다.", feedback().getText());
            assertNull(node.message, "빈 문자열은 null로 저장");
            assertEquals("tok", node.secret);
            assertFalse(node.flag);
            assertEquals(Mode.FAST, node.mode);
            assertEquals(250, node.getIntervalMilli());
            assertEquals(7, node.fixed, "편집 불가 필드는 그대로");
        });
    }

    @Test
    void conversionFailureAppliesNothing() throws Exception {
        Edt.run(() -> {
            text("intervalMilli").setText("abc");
            text("message").setText("partial");
            apply().doClick();

            assertEquals("입력값과 필드 타입을 확인하세요.", feedback().getText());
            assertEquals("hello", node.message, "일부 필드만 바뀌면 안 됨");
        });
    }

    @Test
    void valueBelowMinimumIsRejectedWithSpecificMessage() throws Exception {
        Edt.run(() -> {
            text("intervalMilli").setText("-5");
            text("message").setText("should-not-apply");
            apply().doClick();

            assertEquals("intervalMilli은(는) 0 이상이어야 합니다.", feedback().getText());
            assertEquals(1000, node.getIntervalMilli());
            assertEquals("hello", node.message);

            text("intervalMilli").setText("0");
            apply().doClick();
            assertEquals(0, node.getIntervalMilli(), "0은 허용");
        });
    }

    @Test
    void locksWhileBoardIsNotEditableAndKeepsDraftOtherwise() throws Exception {
        Edt.run(() -> {
            boardEditable = false;
            editor.refreshState();
            assertFalse(text("message").isEditable());
            assertFalse(((JCheckBox) find("option.flag")).isEnabled());
            assertFalse(radio("FAST").isEnabled());
            assertFalse(apply().isEnabled());
            assertEquals("실행 중이거나 종료 중에는 수정할 수 없습니다.", feedback().getText());

            boardEditable = true;
            editor.refreshState();
            assertTrue(text("message").isEditable());
            assertEquals("실행 전에 설정을 적용하세요.", feedback().getText());

            text("message").setText("draft");
            editor.refreshState();
            assertEquals("draft", text("message").getText(), "편집 가능한 동안의 갱신은 작성 중인 입력을 유지");
        });
    }

    @Test
    void nodeWithoutOptionFieldsHasNoEditor() throws Exception {
        Edt.run(() -> assertFalse(new NodeOptionsEditor(new TestNodes.Sink("d1"), () -> true).hasOptions()));
    }

    private Component find(String name) {
        Component found = Swing.find(panel, name);
        assertNotNull(found, name + " 컴포넌트가 없음");
        return found;
    }

    private JTextComponent text(String field) { return (JTextComponent) find("option." + field); }
    private JButton apply() { return (JButton) find("applyNodeOptions"); }
    private JLabel feedback() { return (JLabel) find("nodeOptionsFeedback"); }

    private JRadioButton radio(String label) {
        for (Component child : ((JPanel) find("option.mode")).getComponents()) {
            if (label.equals(((JRadioButton) child).getText())) return (JRadioButton) child;
        }
        throw new AssertionError(label);
    }

    private static void collectOptionNames(Container container, List<String> out) {
        for (Component child : container.getComponents()) {
            if (child.getName() != null && child.getName().startsWith("option.")) out.add(child.getName());
            if (child instanceof Container nested) collectOptionNames(nested, out);
        }
    }
}
