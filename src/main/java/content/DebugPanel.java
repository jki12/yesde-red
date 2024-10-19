package content;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;

public class DebugPanel extends JPanel { // TODO, 멀티 쓰레드 환경에서도 안전한지 확인, resize 기능 추가
    private static final int MESSAGE_HEIGHT = 80;

    private final JPanel debugPanel;

    public DebugPanel() {
        setLayout(new BorderLayout());

        // debug panel 설정.
        debugPanel = new JPanel();
        debugPanel.setLayout(new BoxLayout(debugPanel, BoxLayout.Y_AXIS));

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setViewportView(debugPanel);

        add(scrollPane);
        scrollPane.getVerticalScrollBar().addAdjustmentListener(new AdjustmentListener() {
            private boolean shouldMove = true; // 스크롤바가 처음 활성화 된 시점 auto scroll 되도록 하기 위한 변수

            @Override
            public void adjustmentValueChanged(AdjustmentEvent e) {
                int messageCount = debugPanel.getComponentCount();

                // getValue, scrollbar가 활성화 되어있는지 확인하지 않고 messageCount를 이용해 비교하는 이유는 값이 처음 값이 들어갈 때 일시적으로 활성화 되어있다고 뜨거나 15라는 값이 들어가는 것을 확인해서.
                // 스크롤이 맨 아래에 있는 경우(값이 add 된 후 함수가 실행되기 때문에 message 높이 만큼 더해준 후 비교한다.)
                if (messageCount > 1 && (shouldMove || e.getAdjustable().getMaximum() - e.getAdjustable().getVisibleAmount() == e.getValue() + MESSAGE_HEIGHT)) {
                    e.getAdjustable().setValue(e.getAdjustable().getMaximum());
                    shouldMove = false;
                }
            }
        });
    }

    public void add(String text) {
        JLabel label = new JLabel(text);

        label.setPreferredSize(new Dimension(Integer.MAX_VALUE, MESSAGE_HEIGHT));
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, MESSAGE_HEIGHT));

        label.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.lightGray));

        debugPanel.add(label);
    }
}
