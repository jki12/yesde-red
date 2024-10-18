package menubar.file;

import action.ExportAction;

import javax.swing.*;

public class ExportMenuItem extends JMenuItem {

    public ExportMenuItem() {
        super(new ExportAction());

        setAccelerator(KeyStroke.getKeyStroke("meta E"));
        setMnemonic('e');
    }
}
