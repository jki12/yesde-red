package content;

import ui.theme.UiTheme;
import workflow.RunState;

import javax.swing.*;
import java.awt.*;

/** 하단 상태 표시줄. 왼쪽에 실행 상태, 오른쪽에 보드 이름·노드 수·연결 수를 보여줍니다. */
final class StatusBar extends JPanel {
    private final JLabel status = UiTheme.label("", true);
    private final JLabel summary = UiTheme.label("", true);

    StatusBar() {
        super(new BorderLayout(12, 0));
        setBackground(UiTheme.getPanelBackgroundColor());
        setBorder(UiTheme.footerBorder(6, 14));

        status.setFont(UiTheme.getFont().deriveFont(11f));
        summary.setFont(UiTheme.getFont().deriveFont(11f));

        add(status, BorderLayout.WEST);
        add(summary, BorderLayout.EAST);
        showBoard(null);
    }

    void showBoard(BoardPanel board) {
        RunState state = board == null ? RunState.IDLE : board.getRunState();
        String text = switch (state) {
            case IDLE -> "대기 중";
            case RUNNING -> "실행 중";
            case STOPPING -> "종료 중";
        };
        BoardPanel.NodeFailure failure = board == null ? null : board.getLastFailure();
        if (failure != null) text += "   ·   오류로 종료: " + failure.describe();
        status.setText(text);

        int nodes = board == null ? 0 : board.getNodePanels().size();
        long connections = board == null ? 0 : board.getConnectionCount();
        summary.setText((board == null ? "" : board.getName() + "   |   ")
                + "노드 " + nodes + "개   ·   연결 " + connections + "개");
    }
}
