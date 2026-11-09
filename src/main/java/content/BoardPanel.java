package content;

import lombok.AccessLevel;
import lombok.Getter;
import menu.popup.NodePopupMenu;
import node.BaseNode;
import node.MessageReceiver;
import node.MessageSender;
import node.NodeType;
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
    /** 선택 카드가 바뀜. old/new: NodePanel 또는 null */
    public static final String PROP_SELECTED_NODE = "selectedNode";
    /** 카드가 추가·제거됨. new: 현재 카드 목록 */
    public static final String PROP_NODES_CHANGED = "nodesChanged";
    /** 모델 연결이 추가됨. 값 없음(수신 측이 다시 조회) */
    public static final String PROP_CONNECTIONS_CHANGED = "connectionsChanged";
    /** 실행 상태가 바뀜. old/new: RunState */
    public static final String PROP_BOARD_STATE = "boardState";
    /** 실행 중 노드가 예외로 멈춤. new: NodeFailure. 직후 보드 전체가 종료됩니다. */
    public static final String PROP_NODE_FAILED = "nodeFailed";

    /** 실패한 노드와 예외입니다. 다음 실행을 시작할 때까지 보드가 기억합니다. */
    public record NodeFailure(BaseNode node, Throwable error) {
        public String describe() {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            return node.getDisplayName() + " " + node.getId() + " — " + message;
        }
    }

    private static final int GRID_SIZE = 64;

    private final JPopupMenu popupMenu = new NodePopupMenu();
    // private String description; // TODO, tool tip, json 형식으로 export시 사용
    // 실행 생명주기는 WorkflowRunner가 맡고, 보드는 상태 변경을 PROP_BOARD_STATE 속성 이벤트로 전달합니다.
    @Getter(AccessLevel.NONE)
    private final WorkflowRunner runner;
    private NodeFailure lastFailure;
    private NodePanel selectedNode;

    // 이 보드에 추가된 카드만 ID로 찾습니다. 외부에서 맵을 수정하지 못하도록 getter는 생성하지 않습니다.
    @Getter(AccessLevel.NONE)
    private final Map<String, NodePanel> nodePanelsById = new HashMap<>();

    // 포트 드래그로 연결을 만드는 동안의 상태와 미리보기는 컨트롤러가 맡습니다.
    @Getter(AccessLevel.NONE)
    private final ConnectionDragController connectionDrag = new ConnectionDragController(this);

    public BoardPanel(String name) {
        setName(name);
        runner = new WorkflowRunner(name, new WorkflowRunner.StateListener() {
            @Override public void stateChanged(RunState previous, RunState current) {
                firePropertyChange(PROP_BOARD_STATE, previous, current);
            }
            @Override public void nodeFailed(BaseNode node, Throwable error) {
                lastFailure = new NodeFailure(node, error);
                firePropertyChange(PROP_NODE_FAILED, null, lastFailure);
            }
        });
        setLayout(null); // 노드 자유롭게 배치.
        setBackground(UiTheme.getBackgroundColor());

        addContainerListener(new ContainerAdapter() {
            @Override public void componentAdded(ContainerEvent e) {
                if (e.getChild() instanceof NodePanel panel) {
                    nodePanelsById.put(panel.getNode().getId(), panel);
                }
                fireNodesChanged();
            }

            @Override public void componentRemoved(ContainerEvent e) {
                if (e.getChild() instanceof NodePanel panel) {
                    // ID와 카드가 모두 일치하는 항목만 제거합니다.
                    nodePanelsById.remove(panel.getNode().getId(), panel);
                }
                if (e.getChild() == selectedNode) setSelectedNode(null);

                // 삭제된 노드에 연결 시작 포트나 대상 포트가 포함되어 있으면 연결을 취소
                if (connectionDrag.usesPortOf(e.getChild())) cancelConnection();

                fireNodesChanged();
            }

            private void fireNodesChanged() { firePropertyChange(PROP_NODES_CHANGED, null, getNodePanels()); }
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

    public JButton getConnectionStartPort() {
        return connectionDrag.getStartPort();
    }

    public void beginConnection(JButton port) {
        connectionDrag.begin(port);
    }

    public void updateConnection(Point point) {
        connectionDrag.update(point);
    }

    public void finishConnection(Point point) {
        connectionDrag.finish(point);
    }

    public void cancelConnection() {
        connectionDrag.cancel();
    }

    /** 출력 카드의 노드를 입력 카드의 노드에 연결합니다. 중복 등으로 거부되면 false이며 변경 알림을 보내지 않습니다. */
    boolean connect(NodePanel source, NodePanel target) {
        boolean connected = ((MessageSender) source.getNode()).connect((MessageReceiver) target.getNode());
        if (connected) fireConnectionsChanged();
        return connected;
    }

    /** 타입의 노드를 새로 만들어 보드 좌표 location에 카드를 놓고 선택합니다. 노드 생성 실패 예외는 호출 측에서 처리합니다. */
    public NodePanel addNode(NodeType type, Point location) {
        NodePanel view = new NodePanel(type.name());

        view.setBounds(Math.max(6, location.x), Math.max(6, location.y), NodePanel.DEFAULT_WIDTH, NodePanel.DEFAULT_HEIGHT);
        view.getNode().setLocation(view.getLocation());

        add(view, 0);
        setSelectedNode(view);
        revalidate();
        repaint();
        return view;
    }

    public void setSelectedNode(NodePanel node) {
        NodePanel previous = selectedNode;
        if (previous != null) previous.setSelected(false);

        selectedNode = node;
        if (node != null) node.setSelected(true);
        firePropertyChange(PROP_SELECTED_NODE, previous, node);
        repaint();
    }

    private void fireConnectionsChanged() {
        // 변경 사실만 알리고, 수신 측에서 현재 연결 정보를 다시 조회합니다.
        firePropertyChange(PROP_CONNECTIONS_CHANGED, null, null);
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

    /** 이 보드의 출력 노드들이 가진 연결 수 합계입니다. 처리된 메시지 수가 아닙니다. */
    public long getConnectionCount() {
        return getNodePanels().stream().map(NodePanel::getNode)
                .filter(node -> node.getTargets() != null)
                .mapToLong(node -> node.getTargets().size()).sum();
    }

    public RunState getRunState() {
        return runner.getState();
    }

    public void startWorkflow() {
        lastFailure = null;
        runner.start(List.of(getNodes()));
    }

    public void stopWorkflow() {
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
            if (!(source.getNode() instanceof MessageSender)) continue;

            for (BaseNode target : source.getNode().getTargets()) {
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

    Point center(JButton port) {
        return SwingUtilities.convertPoint(port, port.getWidth() / 2, port.getHeight() / 2, this);
    }

    @Override
    protected void paintChildren(Graphics g) {
        super.paintChildren(g); // 노드 카드
        connectionDrag.paintPreview(g);   // 드래그 중인 연결선은 카드 위에 보여야 합니다.
    }
}
