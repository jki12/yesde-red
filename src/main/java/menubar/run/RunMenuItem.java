package menubar.run;

import action.RunAction;

import javax.swing.*;

public class RunMenuItem extends JMenuItem {

    public RunMenuItem() {
        super(new RunAction());

        setAccelerator(KeyStroke.getKeyStroke("meta R"));
        setMnemonic('r');
    }
}
