package menu.popup;

import ui.board.BoardPanel;
import lombok.extern.slf4j.Slf4j;
import node.NodeType;
import node.NodeFactory;
import ui.theme.UiTheme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

@Slf4j
public class NodeTypeListPanel extends JScrollPane {
    private final String[] typeNames = NodeFactory.getTypeNames();
    private final JList<String> suggestions;
    private final JLabel empty = UiTheme.label("검색 결과가 없습니다", true);

    public NodeTypeListPanel() {
        suggestions = new JList<>(new DefaultListModel<>());

        suggestions.setFixedCellHeight(44);
        suggestions.setVisibleRowCount(5);
        suggestions.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        suggestions.setCellRenderer((list, value, index, selected, focus) -> {
            NodeType type = NodeType.valueOf(value);

            JPanel row = new JPanel(new BorderLayout(8, 0));
            row.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
            row.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));

            JLabel icon = new JLabel(NodeFactory.getIcon(type));
            icon.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            row.add(icon, BorderLayout.WEST);
            JPanel text = new JPanel(new GridLayout(2, 1));
            text.setOpaque(false);

            JLabel title = UiTheme.label(NodeFactory.getDisplayName(type), false);
            title.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            text.add(title);

            // 노드 타입.
            JLabel code = UiTheme.label(value, true);
            code.setFont(UiTheme.getNodeTypeFont());
            if (selected) code.setForeground(list.getSelectionForeground());
            text.add(code);
            row.add(text, BorderLayout.CENTER);

            row.setSize(Math.max(100, list.getWidth()), 44);
            row.doLayout();
            text.doLayout();
            return row;
        });

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

    public void reset() {
        updateSuggestions("");

        suggestions.ensureIndexIsVisible(0);
    }

    public void updateSuggestions(String text) {
        DefaultListModel<String> currentListModel = ((DefaultListModel<String>) suggestions.getModel());
        currentListModel.clear();

        if (text == null) {
            text = "";
        }

        String query = text.strip().toLowerCase();
        for (String typeName : typeNames) {
            if ((typeName + " " + NodeFactory.getDisplayName(NodeType.valueOf(typeName))).toLowerCase().contains(query)) {
                currentListModel.addElement(typeName);
            }
        }

        Component view = currentListModel.isEmpty() ? empty : suggestions;
        if (getViewport().getView() != view) setViewportView(view);
        if (!currentListModel.isEmpty()) suggestions.setSelectedIndex(0);
    }

    public void focusSearchResults() {
        if (suggestions.getModel().getSize() == 0) return;

        if (suggestions.getSelectedIndex() < 0) suggestions.setSelectedIndex(0);
        suggestions.requestFocusInWindow();
    }

    public void addSelectedNode() {
        if (suggestions.getSelectedValue() == null) return;

        JPopupMenu popup = (JPopupMenu) getParent();
        if (popup.getInvoker() instanceof BoardPanel board) {
            Point location = popup.getLocationOnScreen();
            SwingUtilities.convertPointFromScreen(location, board);

            try {
                board.addNode(NodeType.valueOf(suggestions.getSelectedValue()), location);
            }
            catch (RuntimeException error) { // 포트 사용 중이거나 이상한 값이 들어왔을 경우
                JOptionPane.showMessageDialog(board, error.getMessage(), "노드를 만들 수 없습니다",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
        popup.setVisible(false);
    }
}
