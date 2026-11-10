package ui.menu;

import ui.action.ExportAction;
import ui.action.ImportAction;
import ui.action.RunAction;
import ui.action.StopAction;

import javax.swing.*;

/** 메뉴바입니다. 각 항목은 액션 하나와 단축키입니다. */
public class AppMenuBar extends JMenuBar {
    public AppMenuBar() {
        JMenu file = new JMenu("File");
        file.add(item(new ImportAction(), "meta I", 'i'));
        file.add(item(new ExportAction(), "meta E", 'e'));
        add(file);

        JMenu run = new JMenu("Run");
        run.add(item(new RunAction(), "meta R", 'r'));
        run.add(item(new StopAction(), "meta F2", 's'));
        add(run);
    }

    private static JMenuItem item(Action action, String accelerator, char mnemonic) {
        JMenuItem item = new JMenuItem(action);
        item.setAccelerator(KeyStroke.getKeyStroke(accelerator));
        item.setMnemonic(mnemonic);
        return item;
    }
}
