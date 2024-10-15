package menubar.run;

import action.PauseAction;

import javax.swing.*;

public class PauseMenuItem extends JMenuItem {

    public PauseMenuItem() {
        super(new PauseAction());

        setAccelerator(KeyStroke.getKeyStroke("meta F2"));
        setMnemonic('p');
    }
}
