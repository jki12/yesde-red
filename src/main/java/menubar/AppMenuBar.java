package menubar;

import menubar.file.FileMenu;
import menubar.run.RunMenu;

import javax.swing.*;

public class AppMenuBar extends JMenuBar {

    public AppMenuBar() {
        add(new FileMenu());
        add(new RunMenu());
    }
}
