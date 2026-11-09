package action;

import content.BoardPanel;
import lombok.extern.slf4j.Slf4j;
import util.Utils;
import workflow.RunState;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.function.Supplier;

/** 현재 실행의 모든 노드에 종료를 요청합니다. 실제 종료 완료는 작업 스레드들이 모두 끝난 뒤 보드가 알립니다. */
@Slf4j
public class StopAction extends AbstractAction {
    private final Supplier<BoardPanel> selectedBoard;

    /** 메뉴용. 메뉴는 프레임 생성 중에 만들어지므로 보드는 클릭 시점에 프레임에서 찾습니다. */
    public StopAction() {
        this(() -> Utils.getSelectedTabInfo().getBoard());
    }

    /** 보드를 직접 아는 호출자(툴바 등)용입니다. */
    public StopAction(Supplier<BoardPanel> selectedBoard) {
        super("종료");
        this.selectedBoard = selectedBoard;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        BoardPanel board = selectedBoard.get();
        if (board == null || board.getRunState() != RunState.RUNNING) return;

        board.stopWorkflow();
        log.info("{} 보드에 종료를 요청했습니다.", board.getName());
    }
}
