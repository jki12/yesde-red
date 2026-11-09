package content;

import node.BaseNode;
import ui.Codicons;
import ui.UiTheme;
import javax.swing.*;
import java.awt.*;
import java.util.function.BooleanSupplier;

public class NodeInspectorPanel extends JPanel {
    private final JLabel title = UiTheme.label("노드를 선택하세요", false);
    private final JTextField id = createReadOnlyField("nodeId");
    private final JTextArea description = new JTextArea();
    private final JPanel editorContainer = new JPanel(new BorderLayout());

    private BooleanSupplier boardEditable = () -> false;
    private BaseNode selectedNode;
    private NodeOptionsEditor optionsEditor;

    public NodeInspectorPanel() {
        setLayout(new BorderLayout());
        setBorder(UiTheme.separatorBorder(0, 1, 0, 0));
        add(UiTheme.header("노드 속성", Codicons.icon("settings-gear")), BorderLayout.NORTH);
        JPanel body = new InspectorBody();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(UiTheme.padding(16));

        title.setFont(UiTheme.getFont());

        addField(body, "이름", title);
        addField(body, "ID", id);
        body.add(Box.createVerticalStrut(12));

        JLabel hint = UiTheme.label("노드 정보 · 읽기 전용", true);
        hint.setFont(UiTheme.getFont().deriveFont(11f));
        hint.setAlignmentX(LEFT_ALIGNMENT);
        body.add(hint);
        body.add(Box.createVerticalStrut(10));

        description.setEditable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setRows(3);
        description.setPreferredSize(new Dimension(0, 70));
        description.setFont(UiTheme.getFont().deriveFont(12f));
        description.setForeground(UiTheme.getTextColor());

        description.setOpaque(false);
        description.setAlignmentX(LEFT_ALIGNMENT);
        description.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        body.add(description);

        editorContainer.setOpaque(false);
        editorContainer.setAlignmentX(LEFT_ALIGNMENT);
        editorContainer.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        editorContainer.setVisible(false);

        body.add(editorContainer);
        body.add(Box.createVerticalGlue());
        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(UiTheme.getPanelBackgroundColor());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        setPreferredSize(new Dimension(300, 430));
        setMinimumSize(new Dimension(280, 230));
        showNode(null, () -> false);
    }

    // TODO, nodeId만 받고 있음. 사용 안하면 지워야 함
    private static JTextField createReadOnlyField(String name) {
        JTextField field = new JTextField();
        field.setName(name);
        field.setEditable(false);

        field.setFont(UiTheme.getNodeTypeFont().deriveFont(12f));
        UiTheme.fixHeight(field, 180, 32);
        field.setMinimumSize(new Dimension(0, 32));

        return field;
    }

    private void addField(JPanel body, String label, JComponent field) {
        JPanel row = new JPanel(new BorderLayout(8, 0));

        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        JLabel caption = UiTheme.label(label, false);

        caption.setPreferredSize(new Dimension(60, 32));
        row.add(caption, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        body.add(row);

        body.add(Box.createVerticalStrut(8));
    }

    public void showNode(BaseNode node, BooleanSupplier boardEditable) {
        // 적용 순간에도 현재 보드 상태를 읽도록 값 대신 조회 함수를 전달받습니다.
        this.boardEditable = boardEditable;
        showOptionsEditor(node);

        if (node == null) {
            title.setText("노드를 선택하세요");
            id.setText("");
            description.setText("캔버스나 왼쪽 목록에서 노드를 선택하면 정보를 표시합니다.");
            return;
        }

        title.setText(node.getDisplayName());

        id.setText(node.getId());
        id.setCaretPosition(0);
        id.setToolTipText(node.getId());
        description.setText(node.getDescription());
    }

    private void showOptionsEditor(BaseNode node) {
        if (selectedNode != node) {
            selectedNode = node;
            optionsEditor = createOptionsEditor(node);
            editorContainer.removeAll();

            if (optionsEditor != null) {
                editorContainer.add(optionsEditor.getComponent(), BorderLayout.CENTER);
            }
            editorContainer.setVisible(optionsEditor != null);
            editorContainer.revalidate();
            editorContainer.repaint();
        }

        if (optionsEditor != null) {
            optionsEditor.refreshState();
        }
    }

    private NodeOptionsEditor createOptionsEditor(BaseNode node) {
        if (node == null) return null;

        NodeOptionsEditor editor = new NodeOptionsEditor(node, () -> boardEditable.getAsBoolean());

        return editor.hasOptions() ? editor : null;
    }

    private static final class InspectorBody extends JPanel implements Scrollable {
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return 16; }
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) { return 80; }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
}
