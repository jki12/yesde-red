package ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;

public final class Codicons {
    static final int DEFAULT_SIZE = 16;

    private Codicons() { }

    public static FlatSVGIcon icon(String name) {
        return icon(name, DEFAULT_SIZE);
    }

    public static FlatSVGIcon icon(String name, int size) {
        FlatSVGIcon icon = new FlatSVGIcon("icons/codicons/" + name + ".svg", size, size);

        icon.setColorFilter(new FlatSVGIcon.ColorFilter((component, color) ->
                component != null && component.getForeground() != null
                        ? component.getForeground() : UiTheme.getTextColor()));

        if (!icon.hasFound()) throw new IllegalStateException("Codicons SVG를 읽지 못했습니다: " + name);

        return icon;
    }
}
