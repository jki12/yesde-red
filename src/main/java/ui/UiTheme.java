package ui;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

public final class UiTheme {
    private UiTheme() { }

    public static void install() {
        // FlatLightLaf.properties 파일에 팔레트 추가 (https://coolors.co/palette/ffffff-f5f5f5-ebebeb-e0e0e0-d6d6d6)
        FlatLaf.registerCustomDefaultsSource("ui");

        if (!FlatLightLaf.setup()) throw new IllegalStateException("FlatLaf 라이트 테마를 적용하지 못했습니다.");
    }

    public static JLabel label(String text, boolean muted) {
        JLabel label = new JLabel(text);

        if (muted) label.setFont(UIManager.getFont("small.font"));
        return label;
    }

    public static JButton button(String text) {
        return new JButton(text);
    }

    // 실제 색상 값은 FlatLightLaf.properties에서 관리
    public static Color getBackgroundColor() {
        return UIManager.getColor("TextField.background");
    }

    public static Color getPanelBackgroundColor() {
        return UIManager.getColor("Panel.background");
    }

    public static Color getToolbarBackgroundColor() {
        return UIManager.getColor("ToolBar.background");
    }

    public static Color getTextColor() {
        return UIManager.getColor("Label.foreground");
    }

    public static Color getGridColor() {
        return UIManager.getColor("Table.gridColor");
    }

    public static Color getConnectionColor() {
        return UIManager.getColor("Custom.Connection.lineColor");
    }

    public static Color getAccentColor() {
        return UIManager.getColor("Component.accentColor");
    }

    public static Color getNodeSelectedBackgroundColor() {
        return UIManager.getColor("Custom.Node.selectedBackground");
    }

    public static Color getNodeSelectedHeaderBackgroundColor() {
        return UIManager.getColor("Custom.Node.selectedHeaderBackground");
    }

    public static Color getHeaderBackgroundColor() {
        return UIManager.getColor("Custom.Header.background");
    }

    public static Color getBorderColor() {
        return UIManager.getColor("Component.borderColor");
    }

    public static Font getFont() {
        return UIManager.getFont("Label.font");
    }

    public static JPanel header(String title, FlatSVGIcon icon) {
        JPanel header = new JPanel(new BorderLayout());

        header.setBackground(getHeaderBackgroundColor());
        header.setPreferredSize(new Dimension(0, 36));
        // padding 설정
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, getBorderColor()),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)));

        JLabel label = label(title, false);

        label.setFont(getFont().deriveFont(Font.BOLD));
        label.setIcon(icon);
        label.setIconTextGap(8);
        header.add(label, BorderLayout.WEST);

        return header;
    }

    public static Border padding(int amount) {
        return BorderFactory.createEmptyBorder(amount, amount, amount, amount);
    }
}
