package ui.menu;


import javax.swing.*;

public class AppMenuBar extends JMenuBar {

    public AppMenuBar() {
        add(new FileMenu());
        add(new RunMenu());
    }
}
