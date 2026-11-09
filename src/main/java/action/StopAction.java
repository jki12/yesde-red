package action;

import content.BoardPanel;
import ui.frame.YesdeRedFrame;
import lombok.extern.slf4j.Slf4j;
import workflow.RunState;

import javax.swing.*;
import java.awt.event.ActionEvent;

/** 일시정지 대신 현재 실행의 모든 노드에 종료를 요청합니다. */
@Slf4j
public class StopAction extends AbstractAction {
    private JTabbedPane contentPane;

    public StopAction() {
        super("종료");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // 메뉴 생성 중 프레임 초기화가 순환하지 않도록 클릭 시 조회합니다.
        if (contentPane == null) contentPane = YesdeRedFrame.getInstance().getTabs();
        if (!(contentPane.getSelectedComponent() instanceof BoardPanel board)) return;
        if (board.getRunState() != RunState.RUNNING) return;

        board.stop();
        // 실제 종료 완료는 작업 스레드들이 모두 끝난 뒤 보드가 알립니다.
        log.info("{} 보드에 종료를 요청했습니다.", board.getName());
    }
}
