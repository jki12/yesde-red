package ui;

import content.WorkspacePanel;
import content.TabPanel;
import ui.board.BoardPanel;
import ui.console.ConsolePanel;
import menubar.AppMenuBar;

import javax.swing.*;
import java.awt.*;

public class YesdeRedFrame extends JFrame {
    private static final YesdeRedFrame YESDE_RED_FRAME = new YesdeRedFrame();

    private static final String TITLE = "yesde-red";
    private final WorkspacePanel workspace;

    public static YesdeRedFrame getInstance() {
        return YESDE_RED_FRAME;
    }

    public YesdeRedFrame() {
        super(TITLE);

        setSize(1440, 920);
        setMinimumSize(new Dimension(1100, 720));
        setLocationByPlatform(true);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        setJMenuBar(new AppMenuBar());
        workspace = new WorkspacePanel();
        setContentPane(workspace);
    }

    public TabPanel getTabPanel() {
        return workspace.getTabPanel();
    }

    public ConsolePanel getConsole() {
        return workspace.getConsole();
    }

    /** 현재 선택된 탭의 보드입니다. 탭은 항상 하나 이상 있습니다. */
    public BoardPanel getSelectedBoard() {
        return workspace.getSelectedBoard();
    }
}
