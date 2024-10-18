package content;

import javax.swing.*;
import java.awt.*;

public class DebugPanel extends JPanel { // TODO, 멀티 쓰레드 환경에서도 안전한지 확인, resize 기능 추가
    private final JPanel debugPanel;

    public DebugPanel() {
        setLayout(new BorderLayout());

        // debug panel 설정.
        debugPanel = new JPanel();
        debugPanel.setLayout(new BoxLayout(debugPanel, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setViewportView(debugPanel);

        add(scrollPane);
        // TODO, 계속 새로운 값이 입력되는 경우 위에 데이터 확인이 불가능한 이슈 발생.
        scrollPane.getVerticalScrollBar().addAdjustmentListener((e) ->
                        e.getAdjustable().setValue(e.getAdjustable().getMaximum()));
    }

    public void add(String text) {
        JLabel label = new JLabel(text);

        label.setPreferredSize(new Dimension(Integer.MAX_VALUE, 80));
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        label.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.lightGray));

        debugPanel.add(label);
    }
}
