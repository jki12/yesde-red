package ui.board;

import node.NodeType;
import node.NodeFactory;
import ui.theme.UiTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Locale;

public class NodeTypeListPanel extends JScrollPane {
    private final JList<NodeType> suggestions = new JList<>(new DefaultListModel<>());
    private final JLabel empty = UiTheme.label("검색 결과가 없습니다", true);

    public NodeTypeListPanel() {
        suggestions.setFixedCellHeight(44);
        suggestions.setVisibleRowCount(5);
        suggestions.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        suggestions.setCellRenderer(new Renderer());

        empty.setHorizontalAlignment(SwingConstants.CENTER);
        getViewport().setBackground(UiTheme.getBackgroundColor());
        setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);

        setViewportView(suggestions);
        setBorder(BorderFactory.createEmptyBorder());
        setAlignmentX(LEFT_ALIGNMENT);

        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                focusSearchResults();
            }
        });

        suggestions.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    addSelectedNode();
                }
                else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    (getParent()).setVisible(false);
                }
            }
        });

        suggestions.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int index = suggestions.locationToIndex(e.getPoint());

                    if (index >= 0 && suggestions.getCellBounds(index, index).contains(e.getPoint())) {
                        addSelectedNode();
                    }
                }
            }
        });
    }

    /** 표시명 아래에 타입 이름을 작게 보여주는 두 줄 셀입니다. */
    private static final class Renderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            NodeType type = (NodeType) value;
            setText("<html>" + type.getDisplayName() + "<br><font size=-2>" + type.name() + "</font></html>");
            setIcon(NodeFactory.getIcon(type));
            setIconTextGap(8);
            setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
            return this;
        }
    }

    public void reset() {
        updateSuggestions("");

        suggestions.ensureIndexIsVisible(0);
    }

    public void updateSuggestions(String text) {
        DefaultListModel<NodeType> model = (DefaultListModel<NodeType>) suggestions.getModel();
        model.clear();

        String query = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
        for (NodeType type : NodeType.values()) {
            if ((type.name() + " " + type.getDisplayName()).toLowerCase(Locale.ROOT).contains(query)) model.addElement(type);
        }

        Component view = model.isEmpty() ? empty : suggestions;
        if (getViewport().getView() != view) setViewportView(view);
        if (!model.isEmpty()) suggestions.setSelectedIndex(0);
    }

    public void focusSearchResults() {
        if (suggestions.getModel().getSize() == 0) return;

        if (suggestions.getSelectedIndex() < 0) suggestions.setSelectedIndex(0);
        suggestions.requestFocusInWindow();
    }

    public void addSelectedNode() {
        NodeType type = suggestions.getSelectedValue();
        if (type == null) return;

        JPopupMenu popup = (JPopupMenu) getParent();
        if (popup.getInvoker() instanceof BoardPanel board) {
            Point location = popup.getLocationOnScreen();
            SwingUtilities.convertPointFromScreen(location, board);

            try {
                board.addNode(type, location);
            }
            catch (RuntimeException error) { // 포트 사용 중이거나 이상한 값이 들어왔을 경우
                JOptionPane.showMessageDialog(board, error.getMessage(), "노드를 만들 수 없습니다",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
        popup.setVisible(false);
    }
}
