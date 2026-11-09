package content;

import lombok.AccessLevel;
import lombok.Getter;
import menu.popup.NodePopupMenu;
import node.BaseNode;
import node.Inputable;
import node.Outputable;
import ui.UiTheme;
import ui.menu.NodePanel;
import workflow.RunState;
import workflow.WorkflowRunner;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.geom.CubicCurve2D;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class BoardPanel extends JPanel {
    private static final int GRID_SIZE = 64;

    private final JPopupMenu popupMenu = new NodePopupMenu();
    // private String description; // TODO, tool tip, json 형식으로 export시 사용
    // 실행 생명주기는 WorkflowRunner가 맡고, 보드는 상태 변경을 "boardState" 속성 이벤트로 전달합니다.
    @Getter(AccessLevel.NONE)
    private final WorkflowRunner runner;
    private NodePanel selectedNode;

    // 이 보드에 추가된 카드만 ID로 찾습니다. 외부에서 맵을 수정하지 못하도록 getter는 생성하지 않습니다.
    @Getter(AccessLevel.NONE)
    private final Map<String, NodePanel> nodePanelsById = new HashMap<>();

    private JButton connectionStartPort;
    private JButton connectionTargetPort;
    private Point connectionMousePoint; // 연결선을 드래그하는 동안 마우스 위치 좌표 저장하고 있음

    public BoardPanel(String name) {
        setName(name);
        runner = new WorkflowRunner(name, (previous, current) -> firePropertyChange("boardState", previous, current));
        setLayout(null); // 노드 자유롭게 배치.
        setBackground(UiTheme.getBackgroundColor());

        addContainerListener(new ContainerAdapter() {
            @Override public void componentAdded(ContainerEvent e) {
                if (e.getChild() instanceof NodePanel panel) {
                    nodePanelsById.put(panel.getNode().getId(), panel);
                }
                nodesChanged();
            }

            @Override public void componentRemoved(ContainerEvent e) {
                if (e.getChild() instanceof NodePanel panel) {
                    // ID와 카드가 모두 일치하는 항목만 제거합니다.
                    nodePanelsById.remove(panel.getNode().getId(), panel);
                }
                if (e.getChild() == selectedNode) setSelectedNode(null);

                // 삭제된 노드에 연결 시작 포트나 대상 포트가 포함되어 있으면 연결을 취소
                if (connectionStartPort != null && (SwingUtilities.isDescendingFrom(connectionStartPort, e.getChild())
                        || connectionTargetPort != null && SwingUtilities.isDescendingFrom(connectionTargetPort, e.getChild()))) {

                    cancelConnection();
                }

                nodesChanged();
            }

            private void nodesChanged() { firePropertyChange("nodesChanged", null, getNodePanels()); }
        });

        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) setSelectedNode(null);
            }

            private void showPopup(MouseEvent e) {
                popupMenu.show(BoardPanel.this, e.getX(), e.getY());
            }
        });

        // TODO, 노드 삭제하는 이벤트 추가
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "cancelConnection");
        getActionMap().put("cancelConnection", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { cancelConnection(); }
        });
    }

    public void beginConnection(JButton port) {
        cancelConnection();
        connectionStartPort = port;
        connectionMousePoint = center(port);
        activePort(port, true);

        repaint();
    }

    public void updateConnection(Point point) {
        if (connectionStartPort == null) return;
        connectionMousePoint = new Point(point);

        JButton next = connectionTargetAt(point);
        if (next != connectionTargetPort) {
            activePort(connectionTargetPort, false);
            connectionTargetPort = next;
            activePort(connectionTargetPort, true);
        }

        repaint();
    }

    // 보드 기준 마우스 위치에서 연결을 확정하고 임시 표시를 정리합니다.
    public void finishConnection(Point point) {
        if (connectionStartPort == null) return;

        try {
            JButton targetPort = connectionTargetAt(point);
            if (targetPort == null) return;

            NodePanel origin = (NodePanel) SwingUtilities.getAncestorOfClass(NodePanel.class, connectionStartPort);
            NodePanel other = (NodePanel) SwingUtilities.getAncestorOfClass(NodePanel.class, targetPort);
            boolean output = connectionStartPort == origin.getOutputPort();

            boolean connected;
            if (output) {
                connected = ((Outputable) origin.getNode()).connect((Inputable) other.getNode());
            }
            else {
                connected = ((Outputable) other.getNode()).connect((Inputable) origin.getNode());
            }

            // 중복 등으로 연결이 거부된 경우에는 변경 알림을 보내지 않습니다.
            if (connected) notifyConnectionsChanged();
        }
        finally {
            cancelConnection();
        }
    }

    private JButton connectionTargetAt(Point point) {
        Component at = getComponentAt(point);
        NodePanel origin = (NodePanel) SwingUtilities.getAncestorOfClass(NodePanel.class, connectionStartPort);

        if (!(at instanceof NodePanel) || at == origin) return null;
        NodePanel target = (NodePanel) at;

        Point local = SwingUtilities.convertPoint(this, point, target);
        Component hit = SwingUtilities.getDeepestComponentAt(target, local.x, local.y);
        JButton port = connectionStartPort == origin.getOutputPort() ? target.getInputPort() : target.getOutputPort();

        return hit == port ? port : null;
    }

    private void activePort(JButton port, boolean active) {
        if (port == null) return;

        port.putClientProperty("connectionActive", active ? Boolean.TRUE : null);
        port.repaint();
    }

    // ui .
    public void cancelConnection() {
        activePort(connectionStartPort, false);
        activePort(connectionTargetPort, false);

        connectionStartPort = null;
        connectionTargetPort = null;
        connectionMousePoint = null;
        repaint();
    }

    public void setSelectedNode(NodePanel node) {
        NodePanel previous = selectedNode;
        if (previous != null) previous.setSelected(false);

        selectedNode = node;
        if (node != null) node.setSelected(true);
        firePropertyChange("selectedNode", previous, node);
        repaint();
    }

    private void notifyConnectionsChanged() {
        // 변경 사실만 알리고, 수신 측에서 현재 연결 정보를 다시 조회합니다.
        firePropertyChange("connectionsChanged", null, null);
        repaint();
    }

    private BaseNode[] getNodes() {
        return Arrays.stream(getComponents()).filter(NodePanel.class::isInstance)
                .map(c -> ((NodePanel) c).getNode()).toArray(BaseNode[]::new);
    }

    public List<NodePanel> getNodePanels() {
        return Arrays.stream(getComponents()).filter(NodePanel.class::isInstance)
                .map(NodePanel.class::cast)
                .sorted(Comparator.comparing(view -> view.getNode().getCreatedAt())).toList();
    }

    public RunState getRunState() {
        return runner.getState();
    }

    public void run() {
        runner.start(List.of(getNodes()));
    }

    public void stop() {
        runner.stop();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D graphics = (Graphics2D) g.create();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        drawGrid(graphics);

        drawConnections(graphics);

        drawEmptyMessage(graphics);

        graphics.dispose();
    }

    private void drawGrid(Graphics2D graphics) {
        graphics.setColor(UiTheme.getGridColor());

        for (int x = 0; x < getWidth(); x += GRID_SIZE) {
            graphics.drawLine(x, 0, x, getHeight());
        }

        for (int y = 0; y < getHeight(); y += GRID_SIZE) {
            graphics.drawLine(0, y, getWidth(), y);
        }
    }

    private void drawConnections(Graphics2D graphics) {
        graphics.setStroke(new BasicStroke(1.6f));
        for (Component c : getComponents()) {
            if (!(c instanceof NodePanel)) continue;

            NodePanel source = (NodePanel) c;

            // 연결 선 그리는 부분.
            if (!(source.getNode() instanceof Outputable)) continue;

            for (BaseNode target : source.getNode().getOut()) {
                NodePanel destination = nodePanelsById.get(target.getId());
                if (destination == null || destination.getParent() != this) continue;

                Point from = center(source.getOutputPort());
                Point to = center(destination.getInputPort());
                graphics.setColor(UiTheme.getConnectionColor());
                double bend = Math.max(42, Math.abs(to.x - from.x) * .45);

                graphics.draw(new CubicCurve2D.Double(from.x, from.y, from.x + bend, from.y,
                        to.x - bend, to.y, to.x - 12, to.y));
                graphics.drawLine(to.x - 12, to.y, to.x - 5, to.y);

                // 화살촉
                graphics.fillPolygon(new int[] {to.x - 12, to.x - 5, to.x - 12},
                        new int[] {to.y - 3, to.y, to.y + 3}, 3);
            }
        }
    }

    private void drawEmptyMessage(Graphics2D graphics) {
        if (getComponentCount() == 0) { // 비어있는 경우
            graphics.setColor(UiTheme.getTextColor());
            graphics.setFont(UiTheme.getFont());
            String text = "노드 추가 또는 우클릭으로 시작하세요";
            graphics.drawString(text, Math.max(16, (getWidth() - graphics.getFontMetrics().stringWidth(text)) / 2),
                    Math.max(70, getHeight() / 2));
        }
    }

    private Point center(JButton port) {
        return SwingUtilities.convertPoint(port, port.getWidth() / 2, port.getHeight() / 2, this);
    }

    // paintComponent 그린 후 paintChildren를 호출해서 그림.
    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g); // 보드 배경, 노드 그림.
        if (connectionStartPort == null || connectionMousePoint == null) return;

        NodePanel origin = (NodePanel) SwingUtilities.getAncestorOfClass(NodePanel.class, connectionStartPort);
        Point fixed = center(connectionStartPort);
        Point moving = connectionTargetPort == null ? connectionMousePoint : center(connectionTargetPort);

        // 곡선의 논리 방향은 출력 → 입력으로 맞춤.
        boolean output = connectionStartPort == origin.getOutputPort();
        Point from = output ? fixed : moving;
        Point to = output ? moving : fixed;
        double bend = Math.max(42, Math.abs(to.x - from.x) * .45);

        Graphics2D copy = (Graphics2D) g.create();
        copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        copy.setColor(UiTheme.getAccentColor());

        copy.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
                connectionTargetPort == null ? new float[] {5, 4} : null, 0));
        copy.draw(new CubicCurve2D.Double(from.x, from.y, from.x + bend, from.y,
                to.x - bend, to.y, to.x, to.y));

        if (connectionTargetPort == null) copy.drawOval(moving.x - 4, moving.y - 4, 8, 8);
        copy.dispose();
    }
}
