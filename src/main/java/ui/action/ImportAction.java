package ui.action;

import json.WorkflowDocument;
import lombok.extern.slf4j.Slf4j;
import ui.YesdeRedFrame;
import ui.board.BoardPanel;
import ui.workspace.TabPanel;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;

/** JSON 파일의 워크플로를 새 탭으로 엽니다. */
@Slf4j
public class ImportAction extends AbstractAction {
    private final Supplier<TabPanel> tabs;

    public ImportAction() {
        this(() -> YesdeRedFrame.getInstance().getTabPanel());
    }

    public ImportAction(Supplier<TabPanel> tabs) {
        super("불러오기…");
        this.tabs = tabs;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        TabPanel tabPanel = tabs.get();
        if (tabPanel == null) return;

        JFileChooser chooser = WorkflowFiles.chooser();
        if (chooser.showOpenDialog(tabPanel) != JFileChooser.APPROVE_OPTION) return;
        Path path = chooser.getSelectedFile().toPath();

        try {
            WorkflowDocument document = WorkflowFiles.load(path);
            BoardPanel board = tabPanel.addBoardTab(document);
            log.info("{}에서 {} 보드를 불러왔습니다.", path, board.getName());
        } catch (IOException ex) {
            log.error("워크플로 파일을 읽지 못했습니다. path={}", path, ex);
            JOptionPane.showMessageDialog(tabPanel, "파일을 읽지 못했습니다.\n" + ex.getMessage(), "불러오기 실패", JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException ex) {
            log.warn("워크플로 파일 내용이 잘못됐습니다. path={} reason={}", path, ex.getMessage());
            JOptionPane.showMessageDialog(tabPanel, ex.getMessage(), "불러오기 실패", JOptionPane.ERROR_MESSAGE);
        }
    }
}
