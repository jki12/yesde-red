package content;

import lombok.Getter;
import ui.Codicons;

import action.RunAction;
import ui.UiTheme;
import ui.menu.NodePanel;
import workflow.RunState;
import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;

public class ContentPanel extends JPanel {
    private static final int DIVIDER_SIZE = 5;
    private static final int MIN_BOARD_WIDTH = 360;
    private static final int MIN_HIERARCHY_WIDTH = 180;
    private static final int MIN_CONSOLE_HEIGHT = 145;
    // 콘솔 위 영역에 남겨 둘 높이입니다. 기존 265px은 이 높이 260px과 구분선 5px의 합입니다.
    private static final int MIN_EDITOR_AREA_HEIGHT = 260;

    private static final int INITIAL_HIERARCHY_MIN_WIDTH = 190;
    private static final int INITIAL_HIERARCHY_MAX_WIDTH = 228;
    private static final int INITIAL_NODE_INFO_WIDTH = 300;
    private static final int INITIAL_CONSOLE_MIN_HEIGHT = 180;
    private static final double INITIAL_CONSOLE_HEIGHT_RATIO = 0.27;
    // 선택된 보드에서 구독하는 이벤트. 어느 것이든 오면 목록·속성·상태 표시를 다시 그립니다.
    private static final String[] OBSERVED_BOARD_PROPERTIES = {BoardPanel.PROP_SELECTED_NODE,
            BoardPanel.PROP_NODES_CHANGED, BoardPanel.PROP_CONNECTIONS_CHANGED, BoardPanel.PROP_BOARD_STATE};

    @Getter
    private final TabPanel tabPanel = new TabPanel();
    @Getter
    private final DebugPanel debugPanel = new DebugPanel();
    private final NodeInfoPanel nodeInfo = new NodeInfoPanel();

    private final NodeHierarchyPanel hierarchy = new NodeHierarchyPanel(); // 확인 필요.
    private final StatusBar statusBar = new StatusBar();
    private final JSplitPane editorSplit;
    private final JSplitPane hierarchySplit;
    private final JSplitPane consoleSplit;
    // 매 배치마다 경계를 초기화하면 사용자가 드래그한 위치가 사라지므로 최초 배치와 실제 크기 변경을 구분합니다.
    private boolean dividersInitialized;
    private int lastLayoutWidth;
    private int lastLayoutHeight;

    private final PropertyChangeListener boardListener = e -> refreshCurrentBoardView();
    private BoardPanel observedBoard;

    /*
     * ContentPanel 구성
     *
     * ContentPanel (JPanel, BorderLayout)
     * ├─ NORTH: toolbar (JPanel)
     * │   ├─ CENTER: leftActions
     * │   │   ├─ addNode (JButton): 노드 추가
     * │   │   └─ run (JButton): 실행
     * │   └─ EAST: rightActions
     * │       └─ newFlow (JButton): 새 워크플로
     * │
     * ├─ CENTER: consoleSplit (JSplitPane, 상하 분할)
     * │   ├─ 위: hierarchySplit (JSplitPane, 좌우 분할)
     * │   │   ├─ 왼쪽: hierarchy (NodeHierarchyPanel): 노드 목록
     * │   │   └─ 오른쪽: editorSplit (JSplitPane, 좌우 분할)
     * │   │       ├─ 왼쪽: tabPanel (TabPanel): 워크플로 탭
     * │   │       │   └─ 각 탭: BoardPanel
     * │   │       │       └─ NodePanel: 노드 카드
     * │   │       └─ 오른쪽: nodeInfo (NodeInfoPanel): 선택한 노드의 속성
     * │   └─ 아래: debugPanel (DebugPanel): 실행 로그
     * │
     * └─ SOUTH: statusBar (StatusBar): 실행 상태 / 보드 이름, 노드 수, 연결 수
     *
     */
    public ContentPanel() {
        setLayout(new BorderLayout());

        add(createToolbar(), BorderLayout.NORTH);


        // [원본 대비] 중앙/속성을 먼저 가로로 묶고, 왼쪽 목록을 붙인 뒤, 전체 아래에 콘솔을 세로로 붙입니다.
        // 기존 TabPanel 자체를 유지하므로 실행 액션은 계속 BoardPanel을 선택 탭으로 얻습니다.
        // 경계 크기 조절만 지원하며 패널을 떼어 이동하는 임의 도킹 기능은 아닙니다.
        tabPanel.setMinimumSize(new Dimension(MIN_BOARD_WIDTH, 220));
        editorSplit = split(JSplitPane.HORIZONTAL_SPLIT, tabPanel, nodeInfo, 1.0);
        hierarchySplit = split(JSplitPane.HORIZONTAL_SPLIT, hierarchy, editorSplit, 0.0);
        debugPanel.setMinimumSize(new Dimension(600, MIN_CONSOLE_HEIGHT));
        consoleSplit = split(JSplitPane.VERTICAL_SPLIT, hierarchySplit, debugPanel, 0.76);
        add(consoleSplit, BorderLayout.CENTER);


        add(statusBar, BorderLayout.SOUTH);


        // 선택된 보드 하나만 구독합니다. 탭이 바뀌면 이전 보드의 구독을 풀고 새 보드에 붙입니다.
        tabPanel.addChangeListener(e -> observeSelectedBoard());
        observeSelectedBoard();
    }

    private void observeSelectedBoard() {
        BoardPanel board = getSelectedBoard();
        if (board != observedBoard) {
            if (observedBoard != null) {
                for (String property : OBSERVED_BOARD_PROPERTIES) observedBoard.removePropertyChangeListener(property, boardListener);
            }
            if (board != null) {
                for (String property : OBSERVED_BOARD_PROPERTIES) board.addPropertyChangeListener(property, boardListener);
            }
            observedBoard = board;
        }
        refreshCurrentBoardView();
    }

    private JPanel createToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(16, 0)); // 실행, 새 워크플로 생성.
        toolbar.setBackground(UiTheme.getToolbarBackgroundColor());
        toolbar.setBorder(UiTheme.headerBorder(8, 12));

        JPanel leftActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftActions.setOpaque(false);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightActions.setOpaque(false);

        JButton addNode = UiTheme.button("노드 추가");
        addNode.setIcon(Codicons.icon("add"));
        addNode.addActionListener(e -> {
            BoardPanel board = getSelectedBoard();
            if (board != null) board.getPopupMenu().show(board, 32, 36);
        });
        JButton run = UiTheme.button("실행");
        run.setIcon(Codicons.icon("play"));
        run.addActionListener(new RunAction());

        JButton newFlow = UiTheme.button("새 워크플로");
        newFlow.setIcon(Codicons.icon("new-file"));
        newFlow.addActionListener(e -> tabPanel.addBoardTab());

        leftActions.add(addNode);
        leftActions.add(run);
        rightActions.add(newFlow);

        toolbar.add(leftActions, BorderLayout.CENTER);
        toolbar.add(rightActions, BorderLayout.EAST);

        return toolbar;
    }

    private static JSplitPane split(int orientation, Component first, Component second, double weight) {
        // weight는 창 크기 변화분의 배분 비율입니다. 처음 보이는 패널의 폭/높이는 doLayout()에서 따로 정합니다.
        // continuousLayout은 경계를 놓을 때뿐 아니라 드래그하는 동안에도 패널 크기를 보여주는 표시 옵션입니다.
        JSplitPane split = new JSplitPane(orientation, first, second);
        split.setBorder(null);
        split.setDividerSize(DIVIDER_SIZE);
        split.setContinuousLayout(true);
        split.setResizeWeight(weight);
        return split;
    }

    @Override public void doLayout() {
        // 크기 변경 전 패널 폭을 보관합니다. 사용자 드래그 결과를 다음 창 크기에도 유지합니다.
        // 목록의 초기 폭은 전체 폭의 1/6을 기준으로 최소·최대 범위 안에서 정합니다.
        int hierarchyWidth = dividersInitialized ? hierarchy.getWidth()
                : Math.max(INITIAL_HIERARCHY_MIN_WIDTH, Math.min(INITIAL_HIERARCHY_MAX_WIDTH, getWidth() / 6));
        int nodeInfoWidth = dividersInitialized ? nodeInfo.getWidth() : INITIAL_NODE_INFO_WIDTH;
        int consoleHeight = dividersInitialized ? debugPanel.getHeight() : 0;
        super.doLayout();
        if (getWidth() > 0 && getHeight() > 0
                && (!dividersInitialized || lastLayoutWidth != getWidth() || lastLayoutHeight != getHeight())) {
            // 처음에는 콘솔을 약 27%로 잡고, 이후 창 크기 변경에서는 기존 하단 높이와 좌우 폭을 먼저 보존합니다.
            // 최소 영역을 뺀 남는 공간을 캔버스가 사용하게 해 축소 시 속성이 0px로 접히는 문제를 피합니다.
            // 중첩 분할창을 실제 새 크기로 먼저 배치한 다음 경계를 정해야 이중 크기 배분이 생기지 않습니다.
            consoleSplit.doLayout();
            if (!dividersInitialized) {
                consoleHeight = Math.max(INITIAL_CONSOLE_MIN_HEIGHT,
                        (int) (consoleSplit.getHeight() * INITIAL_CONSOLE_HEIGHT_RATIO));
            }
            consoleHeight = Math.max(MIN_CONSOLE_HEIGHT,
                    Math.min(consoleHeight, consoleSplit.getHeight() - MIN_EDITOR_AREA_HEIGHT - DIVIDER_SIZE));
            consoleSplit.setDividerLocation(consoleSplit.getHeight() - consoleHeight - DIVIDER_SIZE);
            consoleSplit.doLayout();
            hierarchySplit.doLayout();
            hierarchyWidth = Math.max(MIN_HIERARCHY_WIDTH,
                    Math.min(hierarchyWidth, getWidth() - MIN_BOARD_WIDTH - nodeInfo.getMinimumSize().width - 2 * DIVIDER_SIZE));
            hierarchySplit.setDividerLocation(hierarchyWidth);
            hierarchySplit.doLayout();
            editorSplit.doLayout();
            nodeInfoWidth = Math.max(nodeInfo.getMinimumSize().width,
                    Math.min(nodeInfoWidth, editorSplit.getWidth() - MIN_BOARD_WIDTH - DIVIDER_SIZE));
            editorSplit.setDividerLocation(editorSplit.getWidth() - nodeInfoWidth - DIVIDER_SIZE);
            editorSplit.doLayout();
            dividersInitialized = true;
            // 같은 크기에서 선택 텍스트만 바뀌어 레이아웃이 다시 호출되어도 사용자의 경계 위치를 재설정하지 않습니다.
            lastLayoutWidth = getWidth();
            lastLayoutHeight = getHeight();
        }
    }

    private void refreshCurrentBoardView() {
        BoardPanel board = getSelectedBoard();
        NodePanel selected = board == null ? null : board.getSelectedNode();

        nodeInfo.showNode(selected == null ? null : selected.getNode(),
                () -> board != null && board.getRunState() == RunState.IDLE);
        hierarchy.showBoard(board);
        statusBar.showBoard(board);
    }

    public BoardPanel getSelectedBoard() {
        return (BoardPanel) tabPanel.getSelectedComponent(); // 항상 탭은 하나 이상 존재.
    }
}
