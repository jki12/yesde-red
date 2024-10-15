package action;

import frame.YesdeRedFrame;
import lombok.extern.slf4j.Slf4j;

import javax.swing.*;
import java.awt.event.ActionEvent;

@Slf4j
public class PauseAction extends AbstractAction {
    private JTabbedPane contentPane;

    public PauseAction() {
        super("pause");
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // Runner와 동일하게 생성시 값을 넣어주면 순환 참조 문제가 발생해 실행 시 대입.
        if (contentPane == null) {
            contentPane = YesdeRedFrame.getInstance().getContentPane();
        }

        int selectedIndex = contentPane.getSelectedIndex();

//        ((BoardPanel) contentPane.getComponentAt(selectedIndex)).stop();
        log.info("{}번 보드가 일시정지 되었습니다.", selectedIndex);
    }
}
