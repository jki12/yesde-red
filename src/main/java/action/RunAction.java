package action;

import content.BoardPanel;
import ui.frame.YesdeRedFrame;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.event.ActionEvent;

/** 현재 보드의 실행 상태를 확인하고 새 실행을 요청합니다. */
@Slf4j
public class RunAction extends AbstractAction {
    private JTabbedPane contentPane;

    public RunAction() {
        super("run");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // 프레임 생성 중에 메뉴가 이 액션을 만들기 때문에, 생성자에서 프레임을 다시 조회하면 초기화 순환이 생길 수 있습니다.
        // 원본의 지연 조회 시점은 유지하고 getContentPane() 대신 명시적인 getTabs()로 현재 탭 패널을 얻습니다.
        if (contentPane == null) {
            contentPane = YesdeRedFrame.getInstance().getTabs(); // 변경된 화면 배치의 탭에 접근합니다.
        }

        if (!(contentPane.getSelectedComponent() instanceof BoardPanel board)) return;
        if (board.getRunState() != BoardPanel.RunState.IDLE) {
            String message = board.getRunState() == BoardPanel.RunState.STOPPING
                    ? "워크플로가 종료 중입니다. 종료 완료 후 다시 실행하세요."
                    : "이 워크플로는 이미 실행 중입니다.";
            JOptionPane.showMessageDialog(contentPane, message);
            return;
        }
        if (board.getNodePanels().isEmpty()) return;

        try {
            board.run();
            log.info("{} 보드가 실행되었습니다.", board.getName());
        } catch (RuntimeException ex) {
            log.error("워크플로를 시작하지 못했습니다. board={}", board.getName(), ex);
            JOptionPane.showMessageDialog(contentPane,
                    "워크플로를 시작하지 못했습니다. 실행 중인 노드가 있다면 종료 후 다시 시도하세요.",
                    "실행 실패", JOptionPane.ERROR_MESSAGE);
        }
    }
}
