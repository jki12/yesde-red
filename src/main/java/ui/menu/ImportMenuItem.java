package ui.menu;

import ui.action.ImportAction;

import javax.swing.*;

public class ImportMenuItem extends JMenuItem {

    public ImportMenuItem() {
        super(new ImportAction());

        setAccelerator(KeyStroke.getKeyStroke("meta I"));
        setMnemonic('i');
    }
}
