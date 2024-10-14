package sidebar;

import javax.swing.*;
import java.awt.*;

public class DebugPanel extends JPanel { // TODO, node-red의 디버그 창을 만들자, 멀티 쓰레드 환경에서도 안전한지 확인.
    private static DebugPanel instance = new DebugPanel();

    private JPanel panel = new JPanel();

    public static DebugPanel getInstance() {
        return instance;
    }


    private  DebugPanel() {
        add(a());
    }

    private JScrollPane a() {
        JScrollPane scrollPane = new JScrollPane();
        JPanel debugPanel = panel; // jlist 형태로 console 입력 값이 추가되는 형태 - jlist로는 값이 안찍히는 이유는 text 외 추가 정보, UI 변경이 있을 수 있기 때문.
        debugPanel.setLayout(new BoxLayout(debugPanel, BoxLayout.Y_AXIS));

        scrollPane.setViewportView(debugPanel);

        var test = new JLabel("hello");
        var box = Box.createVerticalBox();
        box.add(test);

        debugPanel.setPreferredSize(new Dimension(300, 300));

        debugPanel.add(box);

        return scrollPane;
    }

    public void add(String text) {
        panel.add(new JLabel(text));


    }
}
