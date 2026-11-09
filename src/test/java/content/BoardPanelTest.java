package content;

import node.NodeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import support.Edt;
import support.Swing;
import support.TestNodes;
import ui.UiTheme;
import ui.menu.NodePanel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 포트 드래그로 연결을 만드는 흐름과 노드 추가를 실제 마우스 이벤트로 검사합니다. 노드 스레드는 시작하지 않습니다. */
class BoardPanelTest {
    private BoardPanel board;
    private NodePanel source;
    private NodePanel sink;
    private NodePanel second;

    @BeforeAll
    static void installTheme() throws Exception {
        Edt.run(UiTheme::install);
    }

    @BeforeEach
    void setUp() throws Exception {
        Edt.run(() -> {
            board = new BoardPanel("테스트");
            board.setSize(900, 450);
            source = add(new TestNodes.Source("s1"), 75, 95);
            sink = add(new TestNodes.Sink("d1"), 540, 230);
            second = add(new TestNodes.Sink("d2"), 540, 335);
            Swing.layout(board);
        });
    }

    @Test
    void draggingShowsPreviewWithoutConnecting() throws Exception {
        Edt.run(() -> {
            JButton output = source.getOutputPort();
            Swing.press(output);
            Swing.drag(output, board, new Point(430, 165));

            assertTrue(source.getNode().getTargets().isEmpty(), "드래그 중에는 모델이 바뀌면 안 됨");
            assertSame(output, board.getConnectionStartPort());
            int accent = Swing.pixelsNear(Swing.render(board), new Rectangle(330, 70, 100, 170), UiTheme.getAccentColor());
            assertTrue(accent > 20, "미리보기 선이 실제로 그려져야 함, 강조색 픽셀 " + accent);
        });
    }

    @Test
    void droppingOnCardBodyCancels() throws Exception {
        Edt.run(() -> {
            JButton output = source.getOutputPort();
            Point body = new Point(sink.getX() + 130, sink.getY() + 48);
            Swing.press(output);
            Swing.drag(output, board, body);
            Swing.release(output, board, body);

            assertTrue(source.getNode().getTargets().isEmpty());
            assertNull(board.getConnectionStartPort(), "잘못된 놓기는 미리보기를 지움");
        });
    }

    @Test
    void droppingOnInputPortConnectsAndNotifiesOnce() throws Exception {
        Edt.run(() -> {
            AtomicInteger notifications = new AtomicInteger();
            board.addPropertyChangeListener(BoardPanel.PROP_CONNECTIONS_CHANGED, e -> notifications.incrementAndGet());
            JButton output = source.getOutputPort();
            JButton input = sink.getInputPort();
            Point target = Swing.center(input, board);

            Swing.press(output);
            Swing.drag(output, board, target);
            assertEquals(Boolean.TRUE, input.getClientProperty("connectionActive"), "대상 포트 강조");
            assertTrue(source.getNode().getTargets().isEmpty(), "놓기 전에는 연결되지 않음");

            Swing.release(output, board, target);
            assertTrue(source.getNode().getTargets().contains(sink.getNode()));
            assertEquals(1, notifications.get());
            assertNull(input.getClientProperty("connectionActive"), "성공한 놓기는 강조를 지움");

            Swing.press(output);
            Swing.drag(output, board, target);
            Swing.release(output, board, target);
            assertEquals(1, notifications.get(), "중복 연결은 알림을 보내지 않음");
        });
    }

    @Test
    void escapeCancelsEvenBeforeRelease() throws Exception {
        Edt.run(() -> {
            JButton output = source.getOutputPort();
            Point target = Swing.center(second.getInputPort(), board);
            Swing.press(output);
            Swing.drag(output, board, target);
            board.getActionMap().get("cancelConnection").actionPerformed(new ActionEvent(board, 0, "test"));
            Swing.release(output, board, target);

            assertFalse(source.getNode().getTargets().contains(second.getNode()));
        });
    }

    @Test
    void reverseDragFromInputPortStillConnectsOutputToInput() throws Exception {
        Edt.run(() -> {
            JButton input = second.getInputPort();
            Point target = Swing.center(source.getOutputPort(), board);
            Swing.press(input);
            Swing.drag(input, board, target);
            assertEquals(Boolean.TRUE, source.getOutputPort().getClientProperty("connectionActive"));
            Swing.release(input, board, target);

            assertTrue(source.getNode().getTargets().contains(second.getNode()));
        });
    }

    @Test
    void removingTheOriginCardCancelsDrag() throws Exception {
        Edt.run(() -> {
            JButton output = source.getOutputPort();
            Swing.press(output);
            board.remove(second);
            assertSame(output, board.getConnectionStartPort(), "다른 카드 제거는 드래그를 유지");
            board.remove(source);
            assertNull(board.getConnectionStartPort(), "시작 카드 제거는 드래그를 취소");
        });
    }

    @Test
    void addNodeClampsPositionSelectsAndNotifies() throws Exception {
        Edt.run(() -> {
            AtomicInteger changes = new AtomicInteger();
            board.addPropertyChangeListener(BoardPanel.PROP_NODES_CHANGED, e -> changes.incrementAndGet());

            NodePanel added = board.addNode(NodeType.INJECT, new Point(-30, 700));

            assertEquals(6, added.getX(), "왼쪽 경계 안으로 보정");
            assertEquals(700, added.getY());
            assertEquals(6, added.getNode().getX(), "모델 좌표 동기화");
            assertSame(added, board.getSelectedNode());
            assertSame(added, board.getComponent(0), "새 카드가 최상단");
            assertEquals(1, changes.get());
            assertEquals(4, board.getNodePanels().size());
        });
    }

    @Test
    void connectionCountSumsOutgoingLinks() throws Exception {
        Edt.run(() -> {
            assertEquals(0, board.getConnectionCount());
            ((TestNodes.Source) source.getNode()).connect((TestNodes.Sink) sink.getNode());
            ((TestNodes.Source) source.getNode()).connect((TestNodes.Sink) second.getNode());
            assertEquals(2, board.getConnectionCount());
        });
    }

    @Test
    void nodeFailureIsRememberedUntilNextRun() throws Exception {
        java.util.concurrent.CountDownLatch idle = new java.util.concurrent.CountDownLatch(1);
        java.util.List<Object> failedEvents = new java.util.concurrent.CopyOnWriteArrayList<>();
        Edt.run(() -> {
            board.removeAll();
            NodePanel src = add(new TestNodes.Source("s1"), 75, 95);
            NodePanel failing = add(new TestNodes.Failing("f1"), 540, 230);
            ((TestNodes.Source) src.getNode()).setIntervalMillis(10);
            ((TestNodes.Source) src.getNode()).connect((TestNodes.Failing) failing.getNode());
            board.addPropertyChangeListener(BoardPanel.PROP_NODE_FAILED, e -> failedEvents.add(e.getNewValue()));
            board.addPropertyChangeListener(BoardPanel.PROP_BOARD_STATE, e -> { if (e.getNewValue() == workflow.RunState.IDLE) idle.countDown(); });
            assertNull(board.getLastFailure());
            board.startWorkflow();
        });
        assertTrue(idle.await(5, java.util.concurrent.TimeUnit.SECONDS));
        Edt.run(() -> {
            assertEquals(1, failedEvents.size());
            assertNotNull(board.getLastFailure());
            assertEquals("f1", board.getLastFailure().node().getId());
            assertEquals("테스트 실패 f1 — boom", board.getLastFailure().describe());

            board.removeAll();
            add(new TestNodes.Source("s2"), 75, 95);
            board.startWorkflow();
            assertNull(board.getLastFailure(), "다음 실행을 시작하면 지워짐");
            board.stopWorkflow();
        });
    }

    private NodePanel add(node.BaseNode model, int x, int y) {
        NodePanel view = new NodePanel(model);
        view.setBounds(x, y, NodePanel.DEFAULT_WIDTH, NodePanel.DEFAULT_HEIGHT);
        board.add(view);
        return view;
    }
}
