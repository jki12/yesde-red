package action;

import content.BoardPanel;
import frame.YesdeRedFrame;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.event.ActionEvent;

@Slf4j
public class RunAction extends AbstractAction {
    private JTabbedPane contentPane;

    public RunAction() {
        super("run");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // 생성시 값을 넣어주면 순환 참조 문제가 발생해, 사용할 때 대입하도록 수정 (지연 로딩 비슷하게)
        if (contentPane == null) {
            contentPane = YesdeRedFrame.getInstance().getContentPane();
        }

        // TODO, 이미 실행 중인 경우 알림 뜨도록 변경, board 상태도 같이 수정..
        int selectedIndex = contentPane.getSelectedIndex();

        ((BoardPanel) contentPane.getComponentAt(selectedIndex)).run();
        log.info("{}번 보드가 실행되었습니다", selectedIndex);
    }
}
