package content;

import node.Type;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import support.Edt;
import ui.UiTheme;

import javax.swing.*;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/** ContentPanel이 선택된 보드의 이벤트만 구독해 상태 표시를 갱신하는지 검사합니다. */
class ContentPanelTest {

    @BeforeAll
    static void installTheme() throws Exception {
        Edt.run(UiTheme::install);
    }

    @Test
    void refreshesOnlyForTheSelectedBoard() throws Exception {
        Edt.run(() -> {
            ContentPanel content = new ContentPanel();
            TabPanel tabs = content.getTabPanel();
            BoardPanel first = (BoardPanel) tabs.getComponentAt(0);
            assertTrue(summary(content).contains("노드 0개"), summary(content));

            first.addNode(Type.INJECT, new Point(50, 50));
            assertTrue(summary(content).contains("노드 1개"), "선택 보드 이벤트는 요약을 갱신: " + summary(content));

            tabs.addBoardTab();
            BoardPanel second = (BoardPanel) tabs.getSelectedComponent();
            assertNotSame(first, second);
            assertTrue(summary(content).contains("노드 0개"), "탭 전환은 새 보드를 표시: " + summary(content));

            first.addNode(Type.INJECT, new Point(50, 150));
            assertTrue(summary(content).contains("노드 0개"), "배경 보드 이벤트는 전경을 갱신하지 않음: " + summary(content));

            second.addNode(Type.INJECT, new Point(50, 50));
            assertTrue(summary(content).contains("노드 1개"), summary(content));

            tabs.setSelectedIndex(0);
            assertTrue(summary(content).contains("노드 2개"), "돌아오면 첫 보드를 다시 읽음: " + summary(content));
            assertEquals(1, first.getPropertyChangeListeners(BoardPanel.PROP_NODES_CHANGED).length, "구독자는 선택 보드에 하나");
            assertEquals(0, second.getPropertyChangeListeners(BoardPanel.PROP_NODES_CHANGED).length);
            assertEquals("대기 중", status(content));
        });
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
