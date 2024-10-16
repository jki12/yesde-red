package frame;

import content.ContentPanel;
import menubar.MenuBar;

import javax.swing.*;

public class YesdeRedFrame extends JFrame {
    private static final YesdeRedFrame YESDE_RED_FRAME = new YesdeRedFrame();

    private static final String TITLE = "yesde-red";

    public static YesdeRedFrame getInstance() {
        return YESDE_RED_FRAME;
    }

    public YesdeRedFrame() {
        super(TITLE);

        setSize(1200, 800); // TODO, 화면 비율에 맞게 변경.
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        setJMenuBar(new MenuBar());
        setContentPane(new ContentPanel());
    }
}
