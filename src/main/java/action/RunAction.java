package action;

import content.BoardPanel;
import lombok.extern.slf4j.Slf4j;
import util.Utils;
import workflow.RunState;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.function.Supplier;

/** 현재 보드의 실행 상태를 확인하고 새 실행을 요청합니다. */
@Slf4j
public class RunAction extends AbstractAction {
    private final Supplier<BoardPanel> selectedBoard;

    /** 메뉴용. 메뉴는 프레임 생성 중에 만들어지므로 보드는 클릭 시점에 프레임에서 찾습니다. */
    public RunAction() {
        this(() -> Utils.getSelectedTabInfo().getBoard());
    }

    /** 보드를 직접 아는 호출자(툴바 등)용입니다. */
    public RunAction(Supplier<BoardPanel> selectedBoard) {
        super("run");
        this.selectedBoard = selectedBoard;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        BoardPanel board = selectedBoard.get();
        if (board == null) return;
        if (board.getRunState() != RunState.IDLE) {
            String message = board.getRunState() == RunState.STOPPING
                    ? "워크플로가 종료 중입니다. 종료 완료 후 다시 실행하세요."
                    : "이 워크플로는 이미 실행 중입니다.";
            JOptionPane.showMessageDialog(board, message);
            return;
        }
        if (board.getNodePanels().isEmpty()) return;

        try {
            board.startWorkflow();
            log.info("{} 보드가 실행되었습니다.", board.getName());
        } catch (RuntimeException ex) {
            log.error("워크플로를 시작하지 못했습니다. board={}", board.getName(), ex);
            JOptionPane.showMessageDialog(board,
                    "워크플로를 시작하지 못했습니다. 실행 중인 노드가 있다면 종료 후 다시 시도하세요.",
                    "실행 실패", JOptionPane.ERROR_MESSAGE);
        }
    }
}
