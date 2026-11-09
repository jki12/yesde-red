package ui.action;

import lombok.extern.slf4j.Slf4j;
import ui.board.BoardPanel;
import json.WorkflowCodec;
import ui.board.NodePanel;
import ui.YesdeRedFrame;

import javax.swing.*;
import java.awt.event.ActionEvent;

@Slf4j
public class ExportAction extends AbstractAction {

    public ExportAction() {
        super("export");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        BoardPanel board = YesdeRedFrame.getInstance().getSelectedBoard();

        // TODO, 파일 선택 대화상자와 저장은 다음 단계에서 붙입니다.
        log.info(WorkflowCodec.toJson(WorkflowCodec.toDocument(board.getName(),
                board.getNodePanels().stream().map(NodePanel::getNode).toList())));
        log.info("{} 보드가 추출되었습니다.", board.getName());
    }
}
