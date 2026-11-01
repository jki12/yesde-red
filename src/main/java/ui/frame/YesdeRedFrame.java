package ui.frame;

import content.ContentPanel;
import content.TabPanel;
import content.DebugPanel;
import menubar.MenuBar;

import javax.swing.*;
import java.awt.*;

public class YesdeRedFrame extends JFrame {
    private static final YesdeRedFrame YESDE_RED_FRAME = new YesdeRedFrame();

    private static final String TITLE = "yesde-red";
    private final ContentPanel contentPanel;

    public static YesdeRedFrame getInstance() {
        return YESDE_RED_FRAME;
    }

    public YesdeRedFrame() {
        super(TITLE);

        setSize(1440, 920);
        setMinimumSize(new Dimension(1100, 720));
        setLocationByPlatform(true);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        setJMenuBar(new MenuBar());
        contentPanel = new ContentPanel();
        setContentPane(contentPanel);
    }

    public TabPanel getTabs() {
        return contentPanel.getTabPanel();
    }

    public DebugPanel getDebugPanel() {
        return contentPanel.getDebugPanel();
    }
}
