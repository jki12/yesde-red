package menubar;

import content.BoardPanel;
import content.TabPanel;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.event.ActionEvent;

@Slf4j
public class RunMenu extends JMenu {

    public RunMenu() {
        super("Run");

        add(run());
        add(stop());
    }

    private Action stop() {
        return new AbstractAction("stop") {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("stop");
            }
        };
    }

    private Action run() {
        return new AbstractAction("run") {
            @Override
            public void actionPerformed(ActionEvent e) {
                TabPanel contentPane = (TabPanel) getRootPane().getContentPane().getComponent(0);
                BoardPanel board = (BoardPanel) contentPane.getComponentAt(contentPane.getSelectedIndex());

                board.run();
                System.out.println("run");

            }
        };
    }


}
