package ui.action;

import json.WorkflowDocument;
import node.NodeType;
import node.functional.InjectNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.Edt;
import ui.board.BoardPanel;
import ui.board.NodePanel;
import ui.theme.UiTheme;
import ui.workspace.TabPanel;

import java.awt.Point;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowFilesTest {

    @BeforeAll
    static void installTheme() throws Exception {
        Edt.run(UiTheme::install);
    }

    @Test
    void savesABoardAndOpensItBackAsANewTab(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("flow");
        Edt.run(() -> {
            BoardPanel board = new BoardPanel("저장 테스트");
            NodePanel inject = board.addNode(NodeType.INJECT, new Point(20, 30));
            NodePanel debug = board.addNode(NodeType.DEBUG, new Point(360, 30));
            ((InjectNode) inject.getNode()).setMessage("저장된 메시지");
            ((node.Outputable) inject.getNode()).connect((node.Inputable) debug.getNode());

            Path saved = WorkflowFiles.withExtension(file);
            assertEquals("flow.json", saved.getFileName().toString(), "확장자가 없으면 .json을 붙임");
            WorkflowFiles.save(board, saved);
            assertTrue(Files.size(saved) > 0);

            WorkflowDocument document = WorkflowFiles.load(saved);
            TabPanel tabs = new TabPanel();
            BoardPanel opened = tabs.addBoardTab(document);

            assertEquals("저장 테스트", opened.getName());
            assertEquals(2, opened.getNodePanels().size());
            assertEquals(1, opened.getConnectionCount());
            assertEquals("저장된 메시지", ((InjectNode) opened.getNodePanels().get(0).getNode()).getMessage());
            assertEquals(new Point(20, 30), opened.getNodePanels().get(0).getLocation());
            assertEquals(inject.getNode().getId(), opened.getNodePanels().get(0).getNode().getId(), "ID도 그대로");
        });
    }

    @Test
    void loadingGarbageFailsWithAReadableMessage(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("broken.json");
        Files.writeString(file, "{ \"nodes\": 42 }");
        assertThrows(IllegalArgumentException.class, () -> WorkflowFiles.load(file));
        assertThrows(java.io.IOException.class, () -> WorkflowFiles.load(dir.resolve("missing.json")));
    }
}
