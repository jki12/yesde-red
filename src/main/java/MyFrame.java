import content.TabPanel;
import menubar.MenuBar;
import sidebar.DebugPanel;

import javax.swing.*;
import java.awt.*;

public class MyFrame extends JFrame {
    private static final String TITLE = "yesde-red";

    public MyFrame() throws HeadlessException {
        super(TITLE);

        setSize(1200, 800); // TODO, 화면 비율에 맞게 변경.

//        setVisible(true);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        setJMenuBar(new MenuBar());
        JPanel p = new JPanel();
        p.setLayout(new BorderLayout());

        p.add(new TabPanel(), BorderLayout.CENTER);
        p.add(DebugPanel.getInstance(), BorderLayout.EAST);

        setContentPane(p);
//        add(new content.TabPanel());
    }
}
