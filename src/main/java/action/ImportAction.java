package action;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;

public class ImportAction extends AbstractAction {
    private final JFileChooser fileChooser;

    public ImportAction() {
        super("import");

        fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("JSON 파일 (*.json)", "json"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        fileChooser.setCurrentDirectory(fileChooser.getCurrentDirectory()); // null이 들어오면 유저 디폴트 디렉토리로 열림.

        fileChooser.showOpenDialog(null); // TODO, test.
    }
}
