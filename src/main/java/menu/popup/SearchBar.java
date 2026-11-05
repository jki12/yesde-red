package menu.popup;

import ui.Codicons;

import com.formdev.flatlaf.FlatClientProperties;
import ui.UiTheme;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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
        textField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) {
                update();
            }

            public void removeUpdate(DocumentEvent e) {
                update();
            }

            public void changedUpdate(DocumentEvent e) { }

            private void update() {
                results.updateSuggestions(textField.getText());
            }
        });

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
