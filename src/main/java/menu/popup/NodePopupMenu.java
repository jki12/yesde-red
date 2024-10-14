package menu.popup;

import lombok.Getter;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

@Getter
public class NodePopupMenu extends JPopupMenu {

    public NodePopupMenu() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        AutoCompletePanel autoCompletePanel = new AutoCompletePanel();
        SearchBar searchbar = new SearchBar(autoCompletePanel);

        add(searchbar);
        add(autoCompletePanel);

        addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                searchbar.clear();
                autoCompletePanel.reset();
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
