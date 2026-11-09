package content;

import ui.theme.UiTheme;
import ui.menu.NodePanel;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.CubicCurve2D;

/**
 * 포트에서 시작한 연결 드래그의 상태(시작 포트, 강조 중인 대상 포트, 마우스 위치)와 미리보기 선 그리기를 담당합니다.
 * 모델 연결 자체는 확정 시점에 {@link BoardPanel#connect(NodePanel, NodePanel)}에 맡깁니다.
 */
final class ConnectionDragController {
    private final BoardPanel board;

    private JButton startPort;
    private JButton targetPort;
    private Point mousePoint; // 보드 좌표. 드래그 중에만 값이 있습니다.

    ConnectionDragController(BoardPanel board) {
        this.board = board;
    }

    JButton getStartPort() {
        return startPort;
    }

    void begin(JButton port) {
        cancel();
        startPort = port;
        mousePoint = board.center(port);
        highlight(port, true);

        board.repaint();
    }

    void update(Point point) {
        if (startPort == null) return;
        mousePoint = new Point(point);

        JButton next = targetAt(point);
        if (next != targetPort) {
            highlight(targetPort, false);
            targetPort = next;
            highlight(targetPort, true);
        }

        board.repaint();
    }

    // 보드 기준 마우스 위치에서 연결을 확정하고 임시 표시를 정리합니다.
    void finish(Point point) {
        if (startPort == null) return;

        try {
            JButton target = targetAt(point);
            if (target == null) return;

            NodePanel origin = cardOf(startPort);
            NodePanel other = cardOf(target);

            // 드래그를 어느 포트에서 시작했든 모델 연결은 출력 노드 → 입력 노드 방향입니다.
            if (startPort == origin.getOutputPort()) board.connect(origin, other);
            else board.connect(other, origin);
        }
        finally {
            cancel();
        }
    }

    /** 제거된 컴포넌트에 시작 포트나 대상 포트가 포함돼 있으면 true입니다. */
    boolean usesPortOf(Component removed) {
        if (startPort == null) return false;
        return SwingUtilities.isDescendingFrom(startPort, removed)
                || targetPort != null && SwingUtilities.isDescendingFrom(targetPort, removed);
    }

    void cancel() {
        highlight(startPort, false);
        highlight(targetPort, false);

        startPort = null;
        targetPort = null;
        mousePoint = null;
        board.repaint();
    }

    // 자식(노드 카드) 위에 그려야 하므로 보드의 paintChildren 뒤에 호출합니다.
    void paintPreview(Graphics g) {
        if (startPort == null || mousePoint == null) return;

        NodePanel origin = cardOf(startPort);
        Point fixed = board.center(startPort);
        Point moving = targetPort == null ? mousePoint : board.center(targetPort);

        // 곡선의 논리 방향은 출력 → 입력으로 맞춤.
        boolean output = startPort == origin.getOutputPort();
        Point from = output ? fixed : moving;
        Point to = output ? moving : fixed;
        double bend = Math.max(42, Math.abs(to.x - from.x) * .45);

        Graphics2D copy = (Graphics2D) g.create();
        copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        copy.setColor(UiTheme.getAccentColor());

        copy.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
                targetPort == null ? new float[] {5, 4} : null, 0));
        copy.draw(new CubicCurve2D.Double(from.x, from.y, from.x + bend, from.y,
                to.x - bend, to.y, to.x, to.y));

        if (targetPort == null) copy.drawOval(moving.x - 4, moving.y - 4, 8, 8);
        copy.dispose();
    }

    // 마우스 아래의 카드에서, 시작 포트와 짝이 되는 포트를 정확히 가리킬 때만 그 포트를 돌려줍니다.
    private JButton targetAt(Point point) {
        Component at = board.getComponentAt(point);
        NodePanel origin = cardOf(startPort);

        if (!(at instanceof NodePanel target) || at == origin) return null;

        Point local = SwingUtilities.convertPoint(board, point, target);
        Component hit = SwingUtilities.getDeepestComponentAt(target, local.x, local.y);
        JButton port = startPort == origin.getOutputPort() ? target.getInputPort() : target.getOutputPort();

        return hit == port ? port : null;
    }

    private static NodePanel cardOf(JButton port) {
        return (NodePanel) SwingUtilities.getAncestorOfClass(NodePanel.class, port);
    }

    private static void highlight(JButton port, boolean active) {
        if (port == null) return;

        port.putClientProperty("connectionActive", active ? Boolean.TRUE : null);
        port.repaint();
    }
}
