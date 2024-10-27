package menubar.file;

import lombok.extern.slf4j.Slf4j;

import javax.swing.*;

@Slf4j
public class FileMenu extends JMenu {

    public FileMenu() {
        super("File");

        add(new ImportMenuItem());
        add(new ExportMenuItem());
    }
}
