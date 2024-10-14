package menu.popup;

import node.Type;
import org.apache.tika.utils.StringUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SearchBar extends JPanel {
    private static final String SEARCH_ICON_PATH = "src/main/resources/icon/search-16x16.png";
    private static final int TEXT_FIELD_COLUMNS = 22;

    private final JTextField textField;
    private final AutoCompletePanel autoCompletePanel;

    public SearchBar(AutoCompletePanel autoCompletePanel) {
        this.autoCompletePanel = autoCompletePanel;

        ImageIcon searchIcon = new ImageIcon(SEARCH_ICON_PATH);
        JLabel label = new JLabel(searchIcon);

        textField = new JTextField(TEXT_FIELD_COLUMNS - 3); // TODO, placeholder 추가 ("add a node...")
        textField.setBorder(BorderFactory.createEmptyBorder());
        textField.addKeyListener(new KeyAdapter() {
            private boolean metaPressed;

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_META) {
                    metaPressed = true;
                }

                if (metaPressed && e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    clear();
                }

                if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    autoCompletePanel.requestFocus();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_META) {
                    metaPressed = false;
                }

                String text = textField.getText().trim().toUpperCase();
                SwingUtilities.invokeLater(() -> autoCompletePanel.updateSuggestions(text));
            }
        });

        add(label);
        add(textField);

        textField.setBorder(BorderFactory.createEmptyBorder());

        setBackground(Color.WHITE);
        setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.lightGray));
    }

    public void clear() {
        textField.setText("");
    }
}
