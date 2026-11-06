package content;

import lombok.Getter;
import menu.popup.NodePopupMenu;
import node.BaseNode;
import node.InputOutputNode;
import node.Outputable;
import ui.UiTheme;
import ui.menu.NodePanel;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.geom.CubicCurve2D;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

@Getter
public class BoardPanel extends JPanel {
    private static final int GRID_SIZE = 64;

    private final JPopupMenu popupMenu = new NodePopupMenu();
    // private String description; // TODO, tool tip, json 형식으로 export시 사용
    private boolean isRunning;
    private NodePanel selectedNode;

    private JButton connectionStartPort;
    private JButton connectionTargetPort;
    private Point connectionMousePoint; // 연결선을 드래그하는 동안 마우스 위치 좌표 저장하고 있음

    public BoardPanel(String name) {
        setName(name);
        setLayout(null); // 노드 자유롭게 배치.
        setBackground(UiTheme.getBackgroundColor());

        addContainerListener(new ContainerAdapter() {
            @Override public void componentAdded(ContainerEvent e) {
                nodesChanged();
            }

            @Override public void componentRemoved(ContainerEvent e) {
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

    public void nodeViewChanged() {
        firePropertyChange("selectedNode", null, selectedNode);
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

    public void run() {
        isRunning = true;
        for (BaseNode node : getNodes()) node.start();
        firePropertyChange("boardRunning", false, true);
    }

    // 동작 확인 필요.
    public void stop() {
        isRunning = false;
        for (BaseNode node : getNodes()) node.stop();
        firePropertyChange("boardRunning", true, false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D graphics = (Graphics2D) g.create();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(UiTheme.getGridColor());

        // 격자 배경화면 그림.
        for (int x = 0; x < getWidth(); x += GRID_SIZE) {
            graphics.drawLine(x, 0, x, getHeight());
        }

        for (int y = 0; y < getHeight(); y += GRID_SIZE) {
            graphics.drawLine(0, y, getWidth(), y);
        }

        graphics.setStroke(new BasicStroke(1.6f));
        for (Component c : getComponents()) {
            if (!(c instanceof NodePanel)) continue;

            NodePanel source = (NodePanel) c;

            // 연결 선 그리는 부분.
            if (!(source.getNode() instanceof Outputable)) continue;

            for (BaseNode target : ((InputOutputNode) source.getNode()).getOut()) {
                NodePanel destination = NodePanel.NODE_MAP.get(target.getId());
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

        if (getComponentCount() == 0) { // 비어있는 경우
            graphics.setColor(UiTheme.getTextColor());
            graphics.setFont(UiTheme.getFont());
            String text = "노드 추가 또는 우클릭으로 시작하세요";
            graphics.drawString(text, Math.max(16, (getWidth() - graphics.getFontMetrics().stringWidth(text)) / 2),
                    Math.max(70, getHeight() / 2));
        }

        graphics.dispose();
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
