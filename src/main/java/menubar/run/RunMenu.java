package menubar.run;

import lombok.extern.slf4j.Slf4j;

import javax.swing.*;

@Slf4j
public class RunMenu extends JMenu {

    public RunMenu() {
        super("Run");

        add(new RunMenuItem());
        add(new PauseMenuItem());
    }
}
