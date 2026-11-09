package content;

import node.NodeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import support.Edt;
import ui.UiTheme;
import workflow.RunState;

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
            assertEquals(1, tabs.getSelectedIndex(), "새 탭이 선택됨");
            assertTrue(tabs.closeBoardTab(1));
            assertEquals(1, tabs.getTabCount());
        });
    }

    @Test
    void runningOrStoppingBoardCannotBeClosed() throws Exception {
        CountDownLatch idle = new CountDownLatch(1);
        TabPanel[] tabs = new TabPanel[1];
        Edt.run(() -> {
            tabs[0] = new TabPanel();
            tabs[0].addBoardTab();
            BoardPanel board = (BoardPanel) tabs[0].getSelectedComponent();
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
