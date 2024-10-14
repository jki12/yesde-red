package menu.popup;

import lombok.extern.slf4j.Slf4j;
import menu.FlowNode;
import node.Type;
import org.apache.tika.utils.StringUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;

@Slf4j
public class AutoCompletePanel extends JScrollPane {
    private static final int TEXT_FIELD_COLUMNS = 25;

    private final String[] typeNames = typeToStringArr();
    private final JList<String> suggestions;

    public AutoCompletePanel() {
        suggestions = new JList<>(new DefaultListModel<>());

        suggestions.setFixedCellWidth(TEXT_FIELD_COLUMNS * 10);
        suggestions.setSelectionBackground(Color.lightGray);

        setViewportView(suggestions);
        setBorder(BorderFactory.createEmptyBorder());

        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                suggestions.setSelectedIndex(0);
                suggestions.requestFocus();
            }
        });

        suggestions.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                JPopupMenu popupMenu = (JPopupMenu) getParent();
                JPanel invoker = (JPanel) popupMenu.getInvoker();

                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    createAndAddNode(suggestions.getSelectedValue(), popupMenu, invoker);
                    popupMenu.setVisible(false);
                }
            }
        });

        suggestions.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    JPopupMenu popupMenu = (JPopupMenu) getParent();
                    JPanel invoker = (JPanel) popupMenu.getInvoker();

                    createAndAddNode(suggestions.getSelectedValue(), popupMenu, invoker);
                    popupMenu.setVisible(false);
                }
            }
        });
    }

    public void reset() {
        suggestions.ensureIndexIsVisible(0);
        updateSuggestions("");
    }

    public void updateSuggestions(String text) {
        DefaultListModel<String> currentListModel = ((DefaultListModel<String>) suggestions.getModel());
        currentListModel.clear();

        if (StringUtils.isBlank(text)) {
            for (String typeName : typeNames) {
                currentListModel.addElement(typeName);
            }

            return;
        }

        for (String typeName : typeNames) {
            if (typeName.contains(text)) { // TODO, regex match vs string contains 속도 측정해보기 + index of로 정렬해서 가장 가까이 있는 값부터.
                currentListModel.addElement(typeName);
            }
        }
    }

    private String[] typeToStringArr() {
        return Arrays.stream(Type.values())
                .map(Enum::toString)
                .toArray(String[]::new);
    }

    /**
     * @param popupMenu popup 메뉴가 열린 x, y 좌표를 얻기 위해 같이 구해줌
     */
    private void createAndAddNode (String type, JPopupMenu popupMenu, JPanel invoker) {
        FlowNode node = new FlowNode(type);

        // popupMenu.getLocationOnScreen() - invoker 좌표를 빼면 마우스 우클릭 된 좌표값을 구할 수 있음. -> x, y로 관리하지 말고 point로 변경.
        Point pos = SwingUtilities.convertPoint(popupMenu, popupMenu.getLocation(), invoker);

        pos.setLocation(pos.x, pos.y);
        node.setBounds(pos.x, pos.y, FlowNode.DEFAULT_WIDTH, FlowNode.DEFAULT_HEIGHT);

        invoker.add(node, 0);
        invoker.repaint();
    }
}
