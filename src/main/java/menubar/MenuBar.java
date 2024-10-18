package menubar;

import menubar.file.FileMenu;
import menubar.run.RunMenu;

import javax.swing.*;

public class MenuBar extends JMenuBar {

    public MenuBar() {
        add(new FileMenu());
        add(new RunMenu());
    }
}
