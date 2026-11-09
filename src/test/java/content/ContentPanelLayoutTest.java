package content;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import support.Edt;
import support.Swing;
import ui.UiTheme;

import javax.swing.*;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 세 분할창의 초기 크기와 창 크기 변경 시 동작을 검사합니다.
 * 의도: 처음에는 목록·속성·콘솔이 적당한 크기를 갖고, 이후 창 크기가 바뀌면 캔버스만 늘고 줄며,
 * 사용자가 끌어 놓은 경계는 유지되고, 어떤 패널도 최소 크기 아래로 줄지 않습니다.
 */
class ContentPanelLayoutTest {
    private static final int DIVIDER = 5;

    @BeforeAll
    static void installTheme() throws Exception {
        Edt.run(UiTheme::install);
    }

    @Test
    void initialDividersGiveSidePanelsTheirIntendedSizes() throws Exception {
        Edt.run(() -> {
            ContentPanel content = layoutAt(1440, 820);

            assertEquals(228, nodeList(content).getWidth(), "목록 초기 폭");
            assertEquals(300, nodeInfo(content).getWidth(), "속성 초기 폭");
            int console = content.getDebugPanel().getHeight();
            assertTrue(console >= 180 && console <= 240, "콘솔 초기 높이는 180~240px 사이: " + console);
            assertEquals(1440 - 228 - 300 - 2 * DIVIDER, content.getTabPanel().getWidth(), "나머지는 캔버스");
        });
    }

    @Test
    void resizingChangesOnlyTheCanvas() throws Exception {
        Edt.run(() -> {
            ContentPanel content = layoutAt(1440, 820);
            int hierarchyWidth = nodeList(content).getWidth();
            int nodeInfoWidth = nodeInfo(content).getWidth();
            int consoleHeight = content.getDebugPanel().getHeight();
            int canvasWidth = content.getTabPanel().getWidth();
            int editorHeight = content.getTabPanel().getHeight();

            relayoutAt(content, 1240, 720);

            assertEquals(hierarchyWidth, nodeList(content).getWidth());
            assertEquals(nodeInfoWidth, nodeInfo(content).getWidth());
            assertEquals(consoleHeight, content.getDebugPanel().getHeight());
            assertEquals(canvasWidth - 200, content.getTabPanel().getWidth());
            assertEquals(editorHeight - 100, content.getTabPanel().getHeight());
        });
    }

    @Test
    void draggedDividersSurviveRelayoutAndResize() throws Exception {
        Edt.run(() -> {
            ContentPanel content = layoutAt(1440, 820);
            JSplitPane consoleSplit = (JSplitPane) content.getDebugPanel().getParent();
            JSplitPane editorSplit = (JSplitPane) nodeInfo(content).getParent();

            consoleSplit.setDividerLocation(consoleSplit.getHeight() - 300 - DIVIDER); // 콘솔을 300px로 끌어 놓음
            editorSplit.setDividerLocation(editorSplit.getWidth() - 400 - DIVIDER);    // 속성을 400px로
            layoutAndPaint(content);
            assertEquals(300, content.getDebugPanel().getHeight());
            assertEquals(400, nodeInfo(content).getWidth());

            layoutAndPaint(content); // 같은 크기에서 다시 배치
            assertEquals(300, content.getDebugPanel().getHeight());
            assertEquals(400, nodeInfo(content).getWidth());

            relayoutAt(content, 1300, 900);
            assertEquals(300, content.getDebugPanel().getHeight(), "창을 키워도 콘솔 높이 유지");
            assertEquals(400, nodeInfo(content).getWidth(), "창을 키워도 속성 폭 유지");
        });
    }

    @Test
    void shrinkingNeverPushesPanelsBelowMinimumSizes() throws Exception {
        Edt.run(() -> {
            ContentPanel content = layoutAt(1440, 820);
            JSplitPane editorSplit = (JSplitPane) nodeInfo(content).getParent();
            editorSplit.setDividerLocation(editorSplit.getWidth() - 600 - DIVIDER); // 속성을 넓게 끌어 놓은 상태
            layoutAndPaint(content);
            assertEquals(600, nodeInfo(content).getWidth());

            relayoutAt(content, 1100, 720); // 프레임 최소 크기

            assertTrue(content.getTabPanel().getWidth() >= 360, "캔버스 최소 폭: " + content.getTabPanel().getWidth());
            assertTrue(nodeInfo(content).getWidth() >= 280, "속성 최소 폭: " + nodeInfo(content).getWidth());
            assertTrue(nodeList(content).getWidth() >= 180, "목록 최소 폭: " + nodeList(content).getWidth());
            assertTrue(content.getDebugPanel().getHeight() >= 145, "콘솔 최소 높이: " + content.getDebugPanel().getHeight());
            assertEquals(1100, nodeList(content).getWidth() + content.getTabPanel().getWidth() + nodeInfo(content).getWidth() + 2 * DIVIDER,
                    "세 영역과 구분선이 전체 폭을 정확히 채움");
        });
    }

    private static ContentPanel layoutAt(int width, int height) {
        ContentPanel content = new ContentPanel();
        relayoutAt(content, width, height);
        return content;
    }

    private static void relayoutAt(ContentPanel content, int width, int height) {
        content.setSize(width, height);
        layoutAndPaint(content);
    }

    // JSplitPane은 한 번 그려진 뒤부터 resizeWeight로 크기 변화를 배분합니다(그 전에는 선호 크기로 다시 계산).
    // 실제 앱은 사용자가 창 크기를 바꾸기 전에 반드시 그려지므로, 테스트도 배치마다 한 번 그려 같은 조건을 만듭니다.
    private static void layoutAndPaint(ContentPanel content) {
        Swing.layout(content);
        Swing.render(content);
    }

    private static NodeListPanel nodeList(Container root) { return find(root, NodeListPanel.class); }
    private static NodeInfoPanel nodeInfo(Container root) { return find(root, NodeInfoPanel.class); }

    private static <T> T find(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container nested) {
                T found = find(nested, type);
                if (found != null) return found;
            }
        }
        return null;
    }
}
