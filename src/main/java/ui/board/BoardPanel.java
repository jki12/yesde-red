package ui.board;

import json.WorkflowCodec;
import json.WorkflowDocument;
import lombok.AccessLevel;
import lombok.Getter;
import node.BaseNode;
import node.Inputable;
import node.Outputable;
import node.NodeType;
import ui.theme.UiTheme;
import workflow.RunState;
import workflow.WorkflowRunner;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class BoardPanel extends JPanel implements Scrollable {
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

    /** 화면에 그려지는 연결 하나입니다. 출력 카드의 노드가 입력 카드의 노드로 메시지를 보냅니다. */
    public record Connection(NodePanel source, NodePanel target) { }

    /** 실패한 노드와 예외입니다. 다음 실행을 시작할 때까지 보드가 기억합니다. */
    public record NodeFailure(BaseNode node, Throwable error) {
        public String describe() {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            return node.getDisplayName() + " " + node.getId() + " — " + message;
        }
    }

    private static final int GRID_SIZE = 64;
    // 카드 오른쪽·아래로 남겨 두는 여유 공간입니다. 끌어서 캔버스를 계속 넓힐 수 있습니다.
    private static final int CANVAS_MARGIN = 240;

    private final JPopupMenu popupMenu = new AddNodePopup();
    // private String description; // TODO, tool tip, json 형식으로 export시 사용
    // 실행 생명주기는 WorkflowRunner가 맡고, 보드는 상태 변경을 PROP_BOARD_STATE 속성 이벤트로 전달합니다.
    @Getter(AccessLevel.NONE)
    private final WorkflowRunner runner;
    private NodeFailure lastFailure;
    // 카드 선택과 연결 선택은 동시에 존재하지 않습니다. 한쪽을 고르면 다른 쪽은 해제됩니다.
    private NodePanel selectedNode;
    private Connection selectedConnection;

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
                requestFocusInWindow(); // 키 입력(Delete, Esc)을 받기 위해 보드가 포커스를 가집니다.
                if (e.isPopupTrigger()) showPopup(e);
            }
            @Override public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showPopup(e);
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return;
                Connection hit = connectionAt(e.getPoint());
                if (hit != null) setSelectedConnection(hit);
                else {
                    setSelectedNode(null);
                    setSelectedConnection(null);
                }
            }

            private void showPopup(MouseEvent e) {
                Connection hit = connectionAt(e.getPoint());
                if (hit != null) {
                    setSelectedConnection(hit);
                    createConnectionMenu(hit).show(BoardPanel.this, e.getX(), e.getY());
                } else {
                    popupMenu.show(BoardPanel.this, e.getX(), e.getY());
                }
            }
        });

        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "cancelConnection");
        getActionMap().put("cancelConnection", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { cancelConnection(); }
        });

        // 삭제 키는 보드가 포커스를 가질 때만 동작해, 인스펙터 입력란에서 글자를 지울 때 카드가 함께 지워지지 않습니다.
        setFocusable(true);
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DELETE"), "deleteSelection");
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("BACK_SPACE"), "deleteSelection");
        getActionMap().put("deleteSelection", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { deleteSelection(); }
        });
    }

    /** 같은 ID의 카드가 이미 있으면 추가를 거부합니다. ID로 연결선을 찾기 때문에 중복이 있으면 선이 엉킵니다. */
    @Override
    protected void addImpl(Component component, Object constraints, int index) {
        if (component instanceof NodePanel panel) {
            NodePanel existing = nodePanelsById.get(panel.getNode().getId());
            if (existing != null && existing != panel && existing.getParent() == this) {
                throw new IllegalArgumentException("이미 같은 ID의 노드가 보드에 있습니다: " + panel.getNode().getId());
            }
        }
        super.addImpl(component, constraints, index);
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
        boolean connected = ((Outputable) source.getNode()).connect((Inputable) target.getNode());
        if (connected) fireConnectionsChanged();
        return connected;
    }

    /** 타입의 노드를 새로 만들어 보드 좌표 location에 카드를 놓고 선택합니다. 노드 생성 실패 예외는 호출 측에서 처리합니다. */
    public NodePanel addNode(NodeType type, Point location) {
        NodePanel view = new NodePanel(type);

        view.setBounds(Math.max(0, location.x), Math.max(0, location.y), NodePanel.DEFAULT_WIDTH, NodePanel.DEFAULT_HEIGHT);
        view.getNode().setLocation(view.getLocation());

        add(view, 0);
        setSelectedNode(view);
        revalidate();
        scrollRectToVisible(view.getBounds());
        repaint();
        return view;
    }

    /**
     * 카드를 보드에서 지우고 들어오고 나가는 연결을 모두 끊습니다.
     * 실행 중이거나 종료 중이면(노드 스레드가 살아 있을 수 있음) 지우지 않고 false를 돌려줍니다.
     */
    public boolean removeNode(NodePanel view) {
        if (getRunState() != RunState.IDLE || view.getParent() != this) return false;

        BaseNode removed = view.getNode();
        boolean connectionsChanged = removed.disconnectAll();
        if (removed instanceof Inputable target) {
            for (BaseNode node : getNodes()) {
                if (node.disconnect(target)) connectionsChanged = true;
            }
        }

        remove(view); // componentRemoved가 선택 해제·드래그 취소·nodesChanged 알림을 처리합니다.
        if (connectionsChanged) fireConnectionsChanged();
        revalidate(); // 카드 범위가 줄었을 수 있으므로 캔버스 크기를 다시 계산합니다.
        repaint();
        return true;
    }

    /** 연결을 끊습니다. 실행 중이거나 종료 중이면, 또는 그런 연결이 없으면 false입니다. */
    public boolean removeConnection(Connection connection) {
        if (getRunState() != RunState.IDLE) return false;
        if (!connection.source().getNode().disconnect((Inputable) connection.target().getNode())) return false;

        fireConnectionsChanged();
        return true;
    }

    /** 연결선 우클릭 메뉴입니다. 실행 중이거나 종료 중이면 삭제가 비활성화됩니다. */
    JPopupMenu createConnectionMenu(Connection connection) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem delete = new JMenuItem("연결 삭제");
        delete.setName("deleteConnection");
        delete.setEnabled(getRunState() == RunState.IDLE);
        delete.addActionListener(e -> removeConnection(connection));
        menu.add(delete);
        return menu;
    }

    /** 현재 보드의 노드·위치·옵션·연결을 저장 문서로 만듭니다. */
    public WorkflowDocument toDocument() {
        return WorkflowCodec.toDocument(getName(), List.of(getNodes()));
    }

    /** 문서의 노드들을 카드로 올립니다. 비어 있고 IDLE인 보드에만 가능하며, 문서가 잘못됐으면 아무것도 올리지 않습니다. */
    public void load(WorkflowDocument document) {
        if (getRunState() != RunState.IDLE) throw new IllegalStateException("실행 중인 보드에는 불러올 수 없습니다.");
        if (getComponentCount() > 0) throw new IllegalStateException("비어 있는 보드에만 불러올 수 있습니다.");

        for (BaseNode node : WorkflowCodec.toNodes(document)) { // 여기서 실패하면 카드는 하나도 추가되지 않습니다.
            NodePanel view = new NodePanel(node);
            view.setBounds(Math.max(0, node.getX()), Math.max(0, node.getY()), NodePanel.DEFAULT_WIDTH, NodePanel.DEFAULT_HEIGHT);
            node.setLocation(view.getLocation());
            add(view);
        }
        fireConnectionsChanged();
        revalidate();
        repaint();
    }

    /** 선택된 연결 또는 카드를 지웁니다. 선택이 없거나 실행 중이면 아무 일도 하지 않고 false입니다. */
    public boolean deleteSelection() {
        if (selectedConnection != null) return removeConnection(selectedConnection);
        return selectedNode != null && removeNode(selectedNode);
    }

    public void setSelectedNode(NodePanel node) {
        NodePanel previous = selectedNode;
        if (previous != null) previous.setSelected(false);

        selectedNode = node;
        if (node != null) {
            node.setSelected(true);
            selectedConnection = null;
        }
        firePropertyChange(PROP_SELECTED_NODE, previous, node);
        repaint();
    }

    public void setSelectedConnection(Connection connection) {
        if (connection != null && selectedNode != null) setSelectedNode(null);
        selectedConnection = connection;
        repaint();
    }

    /** point(보드 좌표) 아래의 연결선입니다. 선 중심에서 5px 안쪽이면 맞은 것으로 봅니다. 없으면 null입니다. */
    public Connection connectionAt(Point point) {
        BasicStroke hitArea = new BasicStroke(10f);
        for (Connection connection : connections()) {
            if (hitArea.createStrokedShape(connectionPath(connection)).contains(point)) return connection;
        }
        return null;
    }

    /** 화면에 그려지는 연결 목록입니다. 대상 카드가 이 보드에 없는 모델 연결은 제외합니다. */
    List<Connection> connections() {
        List<Connection> result = new ArrayList<>();
        for (Component c : getComponents()) {
            if (!(c instanceof NodePanel source) || !(source.getNode() instanceof Outputable)) continue;
            for (BaseNode target : source.getNode().getTargets()) {
                NodePanel destination = nodePanelsById.get(target.getId());
                if (destination != null && destination.getParent() == this) result.add(new Connection(source, destination));
            }
        }
        return result;
    }

    private void fireConnectionsChanged() {
        // 선택했던 연결이 끊겼으면 선택도 해제합니다.
        if (selectedConnection != null && !connections().contains(selectedConnection)) selectedConnection = null;
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

    /** 카드가 차지하는 범위에 여유를 더한 크기입니다. 스크롤 패널 안에서 캔버스가 카드를 따라 자랍니다. */
    @Override
    public Dimension getPreferredSize() {
        int width = 0;
        int height = 0;
        for (Component card : getComponents()) {
            width = Math.max(width, card.getX() + card.getWidth());
            height = Math.max(height, card.getY() + card.getHeight());
        }
        return new Dimension(width + CANVAS_MARGIN, height + CANVAS_MARGIN);
    }

    // Scrollable: 뷰포트보다 작을 때는 뷰포트를 꽉 채우고, 커지면 스크롤바가 생깁니다.
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) { return GRID_SIZE; }
    @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL ? visible.height : visible.width;
    }
    @Override public boolean getScrollableTracksViewportWidth() {
        return getParent() instanceof JViewport viewport && viewport.getWidth() > getPreferredSize().width;
    }
    @Override public boolean getScrollableTracksViewportHeight() {
        return getParent() instanceof JViewport viewport && viewport.getHeight() > getPreferredSize().height;
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
        for (Connection connection : connections()) {
            boolean selected = connection.equals(selectedConnection);
            graphics.setColor(selected ? UiTheme.getAccentColor() : UiTheme.getConnectionColor());
            graphics.setStroke(new BasicStroke(selected ? 2.4f : 1.6f));
            graphics.draw(connectionPath(connection));

            // 화살촉
            Point to = center(connection.target().getInputPort());
            graphics.fillPolygon(new int[] {to.x - 12, to.x - 5, to.x - 12},
                    new int[] {to.y - 3, to.y, to.y + 3}, 3);
        }
    }

    // 출력 포트에서 입력 포트 앞까지의 곡선과 화살촉 직전의 짧은 직선입니다. 그리기와 클릭 판정이 같은 모양을 씁니다.
    private Path2D connectionPath(Connection connection) {
        Point from = center(connection.source().getOutputPort());
        Point to = center(connection.target().getInputPort());
        double bend = Math.max(42, Math.abs(to.x - from.x) * .45);

        Path2D path = new Path2D.Double();
        path.moveTo(from.x, from.y);
        path.curveTo(from.x + bend, from.y, to.x - bend, to.y, to.x - 12, to.y);
        path.lineTo(to.x - 5, to.y);
        return path;
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
