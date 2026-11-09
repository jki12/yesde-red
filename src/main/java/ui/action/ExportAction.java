package ui.action;

import lombok.extern.slf4j.Slf4j;
import ui.YesdeRedFrame;
import ui.board.BoardPanel;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/** 현재 보드를 JSON 파일로 저장합니다. */
@Slf4j
public class ExportAction extends AbstractAction {
    private final Supplier<BoardPanel> selectedBoard;
    private JFileChooser chooser; // 처음 쓸 때 만듭니다. 메뉴는 프레임 생성 중에 만들어집니다.

    public ExportAction() {
        this(() -> YesdeRedFrame.getInstance().getSelectedBoard());
    }

    public ExportAction(Supplier<BoardPanel> selectedBoard) {
        super("내보내기…");
        this.selectedBoard = selectedBoard;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        BoardPanel board = selectedBoard.get();
        if (board == null) return;

        JFileChooser chooser = chooser();
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), board.getName() + "." + WorkflowFiles.EXTENSION));
        if (chooser.showSaveDialog(board) != JFileChooser.APPROVE_OPTION) return;

        Path path = WorkflowFiles.withExtension(chooser.getSelectedFile().toPath());
        if (Files.exists(path) && JOptionPane.showConfirmDialog(board,
                path.getFileName() + " 파일이 이미 있습니다. 덮어쓸까요?", "내보내기", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            WorkflowFiles.save(board, path);
            log.info("{} 보드를 {}에 저장했습니다.", board.getName(), path);
        } catch (IOException ex) {
            log.error("워크플로를 저장하지 못했습니다. path={}", path, ex);
            JOptionPane.showMessageDialog(board, "파일을 저장하지 못했습니다.\n" + ex.getMessage(), "내보내기 실패", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JFileChooser chooser() {
        if (chooser == null) {
            chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("워크플로 JSON (*.json)", WorkflowFiles.EXTENSION));
        }
        return chooser;
    }
}
