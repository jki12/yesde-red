package ui.theme;

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

    public static Font getNodeTypeFont() {
        return UIManager.getFont("Custom.Node.typeFont");
    }

    public static Font getSmallFont() {
        return UIManager.getFont("small.font");
    }

    /** 왼쪽에 제목을 둔 패널 머리글입니다. 필요하면 호출 측이 CENTER·EAST에 컴포넌트를 더 붙입니다. */
    public static JPanel header(String title, FlatSVGIcon icon) {
        JPanel header = new JPanel(new BorderLayout(10, 0));

        header.setBackground(getHeaderBackgroundColor());
        header.setPreferredSize(new Dimension(0, 36));
        header.setBorder(headerBorder(6, 12));

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

    /** 지정한 변에만 1px 구분선을 그리는 테두리입니다. 인접한 패널끼리 경계를 나눌 때 사용합니다. */
    public static Border separatorBorder(int top, int left, int bottom, int right) {
        return BorderFactory.createMatteBorder(top, left, bottom, right, getBorderColor());
    }

    /** 아래쪽 구분선과 안쪽 여백을 합친 테두리입니다. 툴바와 패널 머리글에 사용합니다. */
    public static Border headerBorder(int vertical, int horizontal) {
        return BorderFactory.createCompoundBorder(separatorBorder(0, 0, 1, 0),
                BorderFactory.createEmptyBorder(vertical, horizontal, vertical, horizontal));
    }

    /** 위쪽 구분선과 안쪽 여백을 합친 테두리입니다. 상태 표시줄 같은 하단 영역에 사용합니다. */
    public static Border footerBorder(int vertical, int horizontal) {
        return BorderFactory.createCompoundBorder(separatorBorder(1, 0, 0, 0),
                BorderFactory.createEmptyBorder(vertical, horizontal, vertical, horizontal));
    }

    /** 세로로 쌓이는 패널(BoxLayout Y축)에서 높이를 고정하고 가로로는 늘어나게 합니다. */
    public static void fixHeight(JComponent component, int preferredWidth, int height) {
        component.setPreferredSize(new Dimension(preferredWidth, height));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        component.setAlignmentX(JComponent.LEFT_ALIGNMENT);
    }
}
