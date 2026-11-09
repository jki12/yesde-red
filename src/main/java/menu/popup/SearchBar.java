package menu.popup;

import ui.Codicons;

import com.formdev.flatlaf.FlatClientProperties;
import ui.Listeners;
import ui.UiTheme;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class SearchBar extends JPanel {
    private final JTextField textField = new JTextField();

    public SearchBar(SearchResultPanel results) {
        setLayout(new BorderLayout(8, 0));
        setBackground(UiTheme.getBackgroundColor());
        setBorder(BorderFactory.createEmptyBorder(4, 10, 7, 10));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        setAlignmentX(LEFT_ALIGNMENT);

        textField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, Codicons.icon("search"));
        textField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "이름 또는 타입 검색");

        add(textField, BorderLayout.CENTER);
        Listeners.onTextChange(textField, () -> results.updateSuggestions(textField.getText()));

        bind("DOWN", "focusSearchResults", results::focusSearchResults);
        bind("ENTER", "addSelectedNode", results::addSelectedNode);
        bind("meta BACK_SPACE", "clearSearchText", this::clear);

        bind("ESCAPE", "closeSearchPopup", () -> {
            JPopupMenu popup = (JPopupMenu) SwingUtilities.getAncestorOfClass(JPopupMenu.class, SearchBar.this);
            if (popup != null) popup.setVisible(false);
        });
    }

    private void bind(String key, String name, Runnable action) {
        textField.getInputMap().put(KeyStroke.getKeyStroke(key), name);
        textField.getActionMap().put(name, new AbstractAction() {
            public void actionPerformed(ActionEvent e) { action.run(); }
        });
    }

    public void clear() {
        textField.setText("");
    }

    public void focusSearch() {
        textField.requestFocusInWindow();
    }
}
