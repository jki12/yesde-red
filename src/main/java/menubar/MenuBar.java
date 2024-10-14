package menubar;

import lombok.Getter;

import javax.swing.*;

@Getter
public class MenuBar extends JMenuBar {

    public MenuBar() {
        add(new FileMenu());
        add(new RunMenu());

    }
}
