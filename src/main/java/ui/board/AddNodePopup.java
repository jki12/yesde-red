package ui.board;

import ui.theme.Codicons;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import ui.theme.UiTheme;
import java.awt.Dimension;

public class AddNodePopup extends JPopupMenu {

    public AddNodePopup() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(UiTheme.getBackgroundColor());
        setPreferredSize(new Dimension(320, 332));

        JLabel title = UiTheme.label("노드 추가", false);

        title.setIcon(Codicons.icon("add", 16));
        title.setIconTextGap(6);
        title.setOpaque(true);
        title.setBackground(UiTheme.getHeaderBackgroundColor());
        title.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        title.setBorder(BorderFactory.createEmptyBorder(9, 12, 5, 12));
        title.setAlignmentX(LEFT_ALIGNMENT);
        add(title);

        NodeTypeListPanel typeList = new NodeTypeListPanel();
        SearchBar searchbar = new SearchBar(typeList);

        add(searchbar);
        add(typeList);
        JLabel hint = UiTheme.label("↑↓ 선택   Enter 추가   Esc 닫기", true);
        hint.setFont(UiTheme.getSmallFont());
        hint.setBorder(BorderFactory.createEmptyBorder(7, 12, 8, 12));
        hint.setAlignmentX(LEFT_ALIGNMENT);
        add(hint);

        addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) { // 팝업 실행시
                searchbar.clear();
                typeList.reset();

                SwingUtilities.invokeLater(searchbar::focusSearch);
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
            }

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {
            }
        });
    }
}
