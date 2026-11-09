package content;
import ui.inspector.NodeInspectorPanel;
import ui.board.BoardPanel;

import lombok.Getter;
import ui.theme.Codicons;

import action.RunAction;
import action.StopAction;
import ui.theme.UiTheme;
import ui.board.NodePanel;
import workflow.RunState;
import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

public class WorkspacePanel extends JPanel {
    private static final int DIVIDER_SIZE = 5;
    private static final int MIN_BOARD_WIDTH = 360;
    private static final int MIN_CONSOLE_HEIGHT = 145;
    private static final int INITIAL_CONSOLE_HEIGHT = 220;
    // 선택된 보드에서 구독하는 이벤트. 어느 것이든 오면 목록·속성·상태 표시를 다시 그립니다.
    private static final String[] OBSERVED_BOARD_PROPERTIES = {BoardPanel.PROP_SELECTED_NODE,
            BoardPanel.PROP_NODES_CHANGED, BoardPanel.PROP_CONNECTIONS_CHANGED, BoardPanel.PROP_BOARD_STATE,
            BoardPanel.PROP_NODE_FAILED};

    @Getter
    private final TabPanel tabPanel = new TabPanel();
    @Getter
    private final ConsolePanel console = new ConsolePanel();
    private final NodeInspectorPanel inspector = new NodeInspectorPanel();

    private final NodeListPanel nodeList = new NodeListPanel();
    private final StatusBar statusBar = new StatusBar();
    // 실행은 IDLE이고 노드가 있을 때, 종료는 RUNNING일 때만 누를 수 있습니다. refreshCurrentBoardView()가 맞춥니다.
    private final JButton runButton = UiTheme.button("실행");
    private final JButton stopButton = UiTheme.button("종료");

    private final PropertyChangeListener boardListener = e -> refreshCurrentBoardView();
    private BoardPanel observedBoard;
    // 콘솔은 모든 보드가 공유하므로, 배경 탭의 보드가 실패해도 기록되도록 보드마다 한 번씩 붙입니다.
    private final Set<BoardPanel> consoleBoundBoards = Collections.newSetFromMap(new WeakHashMap<>());

    /*
     * WorkspacePanel 구성
     *
     * WorkspacePanel (JPanel, BorderLayout)
     * ├─ NORTH: toolbar (JPanel)
     * │   ├─ CENTER: leftActions
     * │   │   ├─ addNode (JButton): 노드 추가
     * │   │   ├─ runButton (JButton): 실행
     * │   │   └─ stopButton (JButton): 종료
     * │   └─ EAST: rightActions
     * │       └─ newFlow (JButton): 새 워크플로
     * │
     * ├─ CENTER: consoleSplit (JSplitPane, 상하 분할)
     * │   ├─ 위: listSplit (JSplitPane, 좌우 분할)
     * │   │   ├─ 왼쪽: nodeList (NodeListPanel): 노드 목록
     * │   │   └─ 오른쪽: editorSplit (JSplitPane, 좌우 분할)
     * │   │       ├─ 왼쪽: tabPanel (TabPanel): 워크플로 탭
     * │   │       │   └─ 각 탭: BoardPanel
     * │   │       │       └─ NodePanel: 노드 카드
     * │   │       └─ 오른쪽: inspector (NodeInspectorPanel): 선택한 노드의 속성
     * │   └─ 아래: console (ConsolePanel): 실행 로그
     * │
     * └─ SOUTH: statusBar (StatusBar): 실행 상태 / 보드 이름, 노드 수, 연결 수
     *
     */
    public WorkspacePanel() {
        setLayout(new BorderLayout());

        add(createToolbar(), BorderLayout.NORTH);


        // 중앙/속성을 먼저 가로로 묶고, 왼쪽 목록을 붙인 뒤, 전체 아래에 콘솔을 세로로 붙입니다.
        // 초기 크기는 목록·속성·콘솔의 선호 크기에서 오고, 창 크기 변화는 캔버스가 모두 흡수합니다(split() 참고).
        tabPanel.setMinimumSize(new Dimension(MIN_BOARD_WIDTH, 220));
        console.setMinimumSize(new Dimension(600, MIN_CONSOLE_HEIGHT));
        console.setPreferredSize(new Dimension(600, INITIAL_CONSOLE_HEIGHT));
        JSplitPane editorSplit = split(JSplitPane.HORIZONTAL_SPLIT, tabPanel, inspector, 1.0);
        JSplitPane listSplit = split(JSplitPane.HORIZONTAL_SPLIT, nodeList, editorSplit, 0.0);
        JSplitPane consoleSplit = split(JSplitPane.VERTICAL_SPLIT, listSplit, console, 1.0);
        add(consoleSplit, BorderLayout.CENTER);


        add(statusBar, BorderLayout.SOUTH);


        // 선택된 보드 하나만 구독합니다. 탭이 바뀌면 이전 보드의 구독을 풀고 새 보드에 붙입니다.
        tabPanel.addChangeListener(e -> observeSelectedBoard());
        observeSelectedBoard();
    }

    private void observeSelectedBoard() {
        BoardPanel board = getSelectedBoard();
        if (board != null && consoleBoundBoards.add(board)) {
            board.addPropertyChangeListener(BoardPanel.PROP_NODE_FAILED, e -> {
                BoardPanel.NodeFailure failure = (BoardPanel.NodeFailure) e.getNewValue();
                console.append("오류 " + board.getName(), failure.describe() + " (워크플로를 종료합니다)");
            });
        }
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
        runButton.setName("runWorkflow");
        runButton.setIcon(Codicons.icon("play"));
        runButton.addActionListener(new RunAction(this::getSelectedBoard));
        stopButton.setName("stopWorkflow");
        stopButton.setIcon(Codicons.icon("debug-stop"));
        stopButton.addActionListener(new StopAction(this::getSelectedBoard));

        JButton newFlow = UiTheme.button("새 워크플로");
        newFlow.setIcon(Codicons.icon("new-file"));
        newFlow.addActionListener(e -> tabPanel.addBoardTab());

        leftActions.add(addNode);
        leftActions.add(runButton);
        leftActions.add(stopButton);
        rightActions.add(newFlow);

        toolbar.add(leftActions, BorderLayout.CENTER);
        toolbar.add(rightActions, BorderLayout.EAST);

        return toolbar;
    }

    /**
     * weight는 크기 변화분 중 first가 가져가는 비율입니다. 1.0이면 second가, 0.0이면 first가 크기를 유지합니다.
     * 경계의 초기 위치도 같은 규칙으로 크기를 유지하는 쪽의 선호 크기에 맞춰지고, 사용자가 끌어 놓은 위치는
     * 이후 크기 변화에서 그대로 보존됩니다. 어느 쪽도 최소 크기 아래로는 줄지 않습니다(JSplitPane 기본 동작).
     */
    private static JSplitPane split(int orientation, Component first, Component second, double weight) {
        // continuousLayout은 경계를 놓을 때뿐 아니라 드래그하는 동안에도 패널 크기를 보여주는 표시 옵션입니다.
        JSplitPane split = new JSplitPane(orientation, first, second);
        split.setBorder(null);
        split.setDividerSize(DIVIDER_SIZE);
        split.setContinuousLayout(true);
        split.setResizeWeight(weight);
        return split;
    }

    private void refreshCurrentBoardView() {
        BoardPanel board = getSelectedBoard();
        NodePanel selected = board == null ? null : board.getSelectedNode();

        RunState state = board == null ? RunState.IDLE : board.getRunState();

        inspector.showNode(selected == null ? null : selected.getNode(),
                () -> board != null && board.getRunState() == RunState.IDLE);
        nodeList.showBoard(board);
        statusBar.showBoard(board);
        runButton.setEnabled(state == RunState.IDLE && board != null && !board.getNodePanels().isEmpty());
        stopButton.setEnabled(state == RunState.RUNNING);
    }

    public BoardPanel getSelectedBoard() {
        return (BoardPanel) tabPanel.getSelectedComponent(); // 항상 탭은 하나 이상 존재.
    }
}
