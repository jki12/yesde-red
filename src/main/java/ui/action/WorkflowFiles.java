package ui.action;

import json.WorkflowCodec;
import json.WorkflowDocument;
import ui.board.BoardPanel;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** 워크플로 문서를 UTF-8 JSON 파일로 읽고 씁니다. 대화상자는 액션이 맡습니다. */
public final class WorkflowFiles {
    public static final String EXTENSION = "json";
    private static JFileChooser chooser; // 처음 쓸 때 만듭니다. 불러오기·내보내기가 같은 디렉터리를 기억합니다.

    private WorkflowFiles() { }

    public static JFileChooser chooser() {
        if (chooser == null) {
            chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("워크플로 JSON (*.json)", EXTENSION));
        }
        return chooser;
    }

    public static void save(BoardPanel board, Path path) throws IOException {
        Files.writeString(path, WorkflowCodec.toJson(board.toDocument()), StandardCharsets.UTF_8);
    }

    /** 파일을 읽어 문서로 만듭니다. 형식이 틀리면 IllegalArgumentException을 던집니다. */
    public static WorkflowDocument load(Path path) throws IOException {
        return WorkflowCodec.fromJson(Files.readString(path, StandardCharsets.UTF_8));
    }

    /** 확장자가 없으면 .json을 붙입니다. */
    public static Path withExtension(Path path) {
        String name = path.getFileName().toString();
        return name.toLowerCase().endsWith("." + EXTENSION) ? path : path.resolveSibling(name + "." + EXTENSION);
    }
}
