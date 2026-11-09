package ui.menu;

import action.StopAction;

import javax.swing.*;

public class StopMenuItem extends JMenuItem {

    public StopMenuItem() {
        super(new StopAction());

        setAccelerator(KeyStroke.getKeyStroke("meta F2"));
        setMnemonic('s');
    }
}
