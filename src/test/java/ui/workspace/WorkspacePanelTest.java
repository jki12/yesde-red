package ui.workspace;
import ui.board.BoardPanel;

import node.NodeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import support.Edt;
import ui.theme.UiTheme;

import javax.swing.*;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/** WorkspacePanel이 선택된 보드의 이벤트만 구독해 상태 표시를 갱신하는지 검사합니다. */
class WorkspacePanelTest {

    @BeforeAll
    static void installTheme() throws Exception {
        Edt.run(UiTheme::install);
    }

    @Test
    void refreshesOnlyForTheSelectedBoard() throws Exception {
        Edt.run(() -> {
            WorkspacePanel content = new WorkspacePanel();
            TabPanel tabs = content.getTabPanel();
            BoardPanel first = tabs.getBoardAt(0);
            assertTrue(summary(content).contains("노드 0개"), summary(content));

            first.addNode(NodeType.INJECT, new Point(50, 50));
            assertTrue(summary(content).contains("노드 1개"), "선택 보드 이벤트는 요약을 갱신: " + summary(content));

            tabs.addBoardTab();
            BoardPanel second = tabs.getSelectedBoard();
            assertNotSame(first, second);
            assertTrue(summary(content).contains("노드 0개"), "탭 전환은 새 보드를 표시: " + summary(content));

            first.addNode(NodeType.INJECT, new Point(50, 150));
            assertTrue(summary(content).contains("노드 0개"), "배경 보드 이벤트는 전경을 갱신하지 않음: " + summary(content));

            second.addNode(NodeType.INJECT, new Point(50, 50));
            assertTrue(summary(content).contains("노드 1개"), summary(content));

            tabs.setSelectedIndex(0);
            assertTrue(summary(content).contains("노드 2개"), "돌아오면 첫 보드를 다시 읽음: " + summary(content));
            assertEquals(1, first.getPropertyChangeListeners(BoardPanel.PROP_NODES_CHANGED).length, "탭을 오가도 구독은 보드마다 하나");
            assertEquals(1, second.getPropertyChangeListeners(BoardPanel.PROP_NODES_CHANGED).length);
            assertEquals("대기 중", status(content));
        });
    }

    @Test
    void nodeFailureShowsInStatusBarAndConsole() throws Exception {
        java.util.concurrent.CountDownLatch idle = new java.util.concurrent.CountDownLatch(1);
        WorkspacePanel[] content = new WorkspacePanel[1];
        Edt.run(() -> {
            content[0] = new WorkspacePanel();
            BoardPanel board = content[0].getSelectedBoard();
            support.TestNodes.Source source = new support.TestNodes.Source("s1");
            support.TestNodes.Failing failing = new support.TestNodes.Failing("f1");
            source.setIntervalMillis(10);
            source.connect(failing);
            for (node.BaseNode model : new node.BaseNode[] {source, failing}) {
                ui.board.NodePanel view = new ui.board.NodePanel(model);
                view.setBounds(40, 40, ui.board.NodePanel.DEFAULT_WIDTH, ui.board.NodePanel.DEFAULT_HEIGHT);
                board.add(view);
            }
            board.addPropertyChangeListener(BoardPanel.PROP_BOARD_STATE, e -> { if (e.getNewValue() == workflow.RunState.IDLE) idle.countDown(); });
            assertEquals(0, consoleRows(content[0]));
            board.startWorkflow();
        });
        assertTrue(idle.await(5, java.util.concurrent.TimeUnit.SECONDS));
        Edt.run(() -> { }); // 콘솔 기록은 invokeLater로 들어오므로 EDT 큐를 한 번 비웁니다.
        Edt.run(() -> {
            assertEquals("대기 중   ·   오류로 종료: 테스트 실패 f1 — boom", status(content[0]));
            assertEquals(1, consoleRows(content[0]), "콘솔에 오류 한 줄");
        });
    }

    @Test
    void nodeWarningsGoToTheConsoleWithoutStoppingTheWorkflow() throws Exception {
        java.util.concurrent.CountDownLatch idle = new java.util.concurrent.CountDownLatch(1);
        WorkspacePanel[] content = new WorkspacePanel[1];
        support.TestNodes.Warner warner = new support.TestNodes.Warner("w1");
        Edt.run(() -> {
            content[0] = new WorkspacePanel();
            BoardPanel board = content[0].getSelectedBoard();
            support.TestNodes.Source source = new support.TestNodes.Source("s1");
            source.setIntervalMillis(20);
            source.connect(warner);
            for (node.BaseNode model : new node.BaseNode[] {source, warner}) {
                ui.board.NodePanel view = new ui.board.NodePanel(model);
                view.setBounds(40, 40, ui.board.NodePanel.DEFAULT_WIDTH, ui.board.NodePanel.DEFAULT_HEIGHT);
                board.add(view);
            }
            board.addPropertyChangeListener(BoardPanel.PROP_BOARD_STATE, e -> { if (e.getNewValue() == workflow.RunState.IDLE) idle.countDown(); });
            board.startWorkflow();
        });
        Thread.sleep(250);
        Edt.run(() -> {
            BoardPanel board = content[0].getSelectedBoard();
            assertEquals(workflow.RunState.RUNNING, board.getRunState(), "경고는 실행을 멈추지 않음");
            assertNull(board.getLastFailure());
            assertTrue(consoleRows(content[0]) >= 2, "경고가 콘솔에 쌓임: " + consoleRows(content[0]));
            assertTrue(warner.warned.get() >= 2, "경고 뒤에도 메시지를 계속 처리함");
            board.stopWorkflow();
        });
        assertTrue(idle.await(5, java.util.concurrent.TimeUnit.SECONDS));
    }

    @Test
    void runAndStopButtonsFollowBoardState() throws Exception {
        java.util.concurrent.CountDownLatch idle = new java.util.concurrent.CountDownLatch(1);
        WorkspacePanel[] content = new WorkspacePanel[1];
        Edt.run(() -> {
            content[0] = new WorkspacePanel();
            JButton run = (JButton) support.Swing.find(content[0], "runWorkflow");
            JButton stop = (JButton) support.Swing.find(content[0], "stopWorkflow");
            BoardPanel board = content[0].getSelectedBoard();
            board.addPropertyChangeListener(BoardPanel.PROP_BOARD_STATE, e -> { if (e.getNewValue() == workflow.RunState.IDLE) idle.countDown(); });

            assertFalse(run.isEnabled(), "노드가 없으면 실행 불가");
            assertFalse(stop.isEnabled());

            board.addNode(NodeType.INJECT, new Point(40, 40));
            assertTrue(run.isEnabled(), "노드가 생기면 실행 가능");
            assertFalse(stop.isEnabled());

            run.doClick();
            assertEquals(workflow.RunState.RUNNING, board.getRunState());
            assertFalse(run.isEnabled());
            assertTrue(stop.isEnabled());

            stop.doClick();
            assertEquals(workflow.RunState.STOPPING, board.getRunState());
            assertFalse(run.isEnabled(), "종료 중에는 둘 다 불가");
            assertFalse(stop.isEnabled());
        });
        assertTrue(idle.await(5, java.util.concurrent.TimeUnit.SECONDS));
        Edt.run(() -> {
            assertTrue(((JButton) support.Swing.find(content[0], "runWorkflow")).isEnabled(), "IDLE로 돌아오면 다시 실행 가능");
            assertFalse(((JButton) support.Swing.find(content[0], "stopWorkflow")).isEnabled());
        });
    }

    @Test
    void inspectorShowsTheSelectedNodesCountsWhileRunning() throws Exception {
        java.util.concurrent.CountDownLatch idle = new java.util.concurrent.CountDownLatch(1);
        WorkspacePanel[] content = new WorkspacePanel[1];
        JLabel[] stats = new JLabel[1];
        Edt.run(() -> {
            content[0] = new WorkspacePanel();
            BoardPanel board = content[0].getSelectedBoard();
            ui.board.NodePanel source = new ui.board.NodePanel(new support.TestNodes.Source("s1"));
            ui.board.NodePanel sink = new ui.board.NodePanel(new support.TestNodes.Sink("d1"));
            source.setBounds(40, 40, 200, 60); sink.setBounds(400, 40, 200, 60);
            board.add(source); board.add(sink);
            ((node.Outputable) source.getNode()).connect((node.Inputable) sink.getNode());
            board.setSelectedNode(sink);
            stats[0] = (JLabel) support.Swing.find(content[0], "nodeStats");
            assertEquals("아직 없음", stats[0].getText());
            board.addPropertyChangeListener(BoardPanel.PROP_BOARD_STATE, e -> { if (e.getNewValue() == workflow.RunState.IDLE) idle.countDown(); });
            ((JButton) support.Swing.find(content[0], "runWorkflow")).doClick();
        });
        Thread.sleep(300); // 출력 노드가 시작하자마자 메시지 하나를 보냅니다
        Edt.run(() -> ((JButton) support.Swing.find(content[0], "stopWorkflow")).doClick());
        assertTrue(idle.await(5, java.util.concurrent.TimeUnit.SECONDS));
        Edt.run(() -> {
            assertTrue(stats[0].getText().matches("받음 [1-9]\\d*  ·  보냄 0  ·  경고 0  ·  마지막 \\d{2}:\\d{2}:\\d{2}"), stats[0].getText());
            content[0].getSelectedBoard().setSelectedNode(null);
            assertEquals("", stats[0].getText());
        });
    }

    private static int consoleRows(WorkspacePanel content) {
        JTable table = findTable(content.getConsole());
        assertNotNull(table, "콘솔 표가 없음");
        return table.getRowCount();
    }

    private static JTable findTable(Container container) {
        for (Component child : container.getComponents()) {
            if (child instanceof JTable table) return table;
            if (child instanceof Container nested) {
                JTable found = findTable(nested);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String status(Container content) { return labels(content)[0]; }
    private static String summary(Container content) { return labels(content)[1]; }

    private static String[] labels(Container content) {
        for (Component child : content.getComponents()) {
            if (child instanceof StatusBar bar) {
                return new String[] {((JLabel) bar.getComponent(0)).getText(), ((JLabel) bar.getComponent(1)).getText()};
            }
        }
        throw new AssertionError("StatusBar가 없음");
    }
}
