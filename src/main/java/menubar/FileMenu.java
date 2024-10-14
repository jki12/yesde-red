package menubar;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;
import java.io.IOException;

@Slf4j
public class FileMenu extends JMenu {
    private final JFileChooser jFileChooser;

    public FileMenu() {
        super("File");

        add(importFile());
        add(exportFile());

        jFileChooser = new JFileChooser();
        jFileChooser.setFileFilter(new FileNameExtensionFilter("SON 파일 (*.json)", "json"));
    }

    private Action importFile() { // TODO, rename.
        return new AbstractAction("import") {
            @Override
            public void actionPerformed(ActionEvent e) {
                jFileChooser.setCurrentDirectory(jFileChooser.getCurrentDirectory()); // 마지막으로 연 파일위치 기억.
                int res = jFileChooser.showOpenDialog(null);

                if (res == JFileChooser.APPROVE_OPTION) {
                    Tika tika = new Tika();

                    try {
                        String type = tika.detect(jFileChooser.getSelectedFile());

                        log.info("{} file type : {}", jFileChooser.getSelectedFile(), type);
                        if (type.endsWith("json")) {
                            // TODO, 파일 읽고 그림창에 그려주는 로직.
                        }
                    } catch (IOException ex) {
                        throw new RuntimeException(ex); // TODO, 경고창 보여주기.
                    }
                }
            }
        };
    }

    private Action exportFile() { // TODO, rename.
        return new AbstractAction("export") {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println(":hello");
            }
        };
    }
}
