package ui.workspace;
import ui.board.BoardPanel;

import node.NodeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import support.Edt;
import ui.theme.UiTheme;
import workflow.RunState;

import javax.swing.*;
import java.awt.Point;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TabPanelTest {

    @BeforeAll
    static void installTheme() throws Exception {
        Edt.run(UiTheme::install);
    }

    @Test
    void newTabsGetNumberedTitlesAndTheLastTabStaysOpen() throws Exception {
        Edt.run(() -> {
            TabPanel tabs = new TabPanel();
            assertEquals(1, tabs.getTabCount());
            assertEquals("워크플로 01", tabs.getTitleAt(0));
            assertFalse(tabs.closeBoardTab(0), "마지막 탭은 닫히지 않음");

            tabs.addBoardTab();
            assertEquals("워크플로 02", tabs.getTitleAt(1));
            assertInstanceOf(JScrollPane.class, tabs.getComponentAt(1), "보드는 스크롤 패널 안에 있음");
            assertEquals("워크플로 02", tabs.getBoardAt(1).getName());
            assertEquals(1, tabs.getSelectedIndex(), "새 탭이 선택됨");
            assertTrue(tabs.closeBoardTab(1));
            assertEquals(1, tabs.getTabCount());
        });
    }

    @Test
    void canvasFillsTheViewportUntilCardsGrowBeyondIt() throws Exception {
        Edt.run(() -> {
            TabPanel tabs = new TabPanel();
            tabs.setSize(600, 400);
            JScrollPane scroll = (JScrollPane) tabs.getComponentAt(0);
            BoardPanel board = tabs.getBoardAt(0);
            support.Swing.layout(tabs);

            assertEquals(scroll.getViewport().getWidth(), board.getWidth(), "카드가 없으면 보드가 뷰포트를 꽉 채움");
            assertFalse(scroll.getHorizontalScrollBar().isVisible());

            board.addNode(node.NodeType.INJECT, new Point(900, 600));
            support.Swing.layout(tabs);

            assertTrue(board.getWidth() >= 900 + ui.board.NodePanel.DEFAULT_WIDTH, "카드를 담을 만큼 캔버스가 커짐");
            assertTrue(scroll.getHorizontalScrollBar().isVisible(), "가로 스크롤바가 생김");
            assertTrue(scroll.getVerticalScrollBar().isVisible(), "세로 스크롤바가 생김");

            board.removeNode(board.getNodePanels().get(0));
            support.Swing.layout(tabs);
            assertFalse(scroll.getHorizontalScrollBar().isVisible(), "카드가 사라지면 스크롤바도 사라짐");
        });
    }

    @Test
    void runningOrStoppingBoardCannotBeClosed() throws Exception {
        CountDownLatch idle = new CountDownLatch(1);
        TabPanel[] tabs = new TabPanel[1];
        Edt.run(() -> {
            tabs[0] = new TabPanel();
            tabs[0].addBoardTab();
            BoardPanel board = tabs[0].getSelectedBoard();
            board.addNode(NodeType.INJECT, new Point(40, 40));
            board.addPropertyChangeListener(BoardPanel.PROP_BOARD_STATE, e -> { if (e.getNewValue() == RunState.IDLE) idle.countDown(); });

            board.startWorkflow();
            assertEquals(RunState.RUNNING, board.getRunState());
            assertFalse(tabs[0].closeBoardTab(1), "실행 중인 보드는 닫히지 않음");

            board.stopWorkflow();
            assertEquals(RunState.STOPPING, board.getRunState());
            assertFalse(tabs[0].closeBoardTab(1), "종료 중인 보드도 닫히지 않음");
        });
        assertTrue(idle.await(3, TimeUnit.SECONDS), "IDLE로 돌아와야 함");
        Edt.run(() -> {
            assertTrue(tabs[0].closeBoardTab(1), "IDLE이면 닫힘");
            assertEquals(1, tabs[0].getTabCount());
        });
        assertTrue(Thread.getAllStackTraces().keySet().stream().noneMatch(t -> t.getName().startsWith("node-") && t.isAlive()),
                "닫힌 뒤 워커 스레드가 남아 있으면 안 됨");
    }
}
