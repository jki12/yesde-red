package node.functional;

import com.google.gson.Gson;
import lombok.Getter;
import lombok.Setter;
import node.BaseNode;
import node.Inputable;
import node.Message;
import node.NodeType;
import node.option.OptionField;
import node.option.OptionInputType;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 받은 메시지를 CSV 또는 JSON Lines 파일에 한 줄씩 기록합니다.
 * 기록할 필드를 비우면 첫 메시지의 모든 필드를 열로 씁니다. 파일을 열 수 없는 것은 노드 실패(워크플로 종료)입니다.
 */
@Getter
@Setter
public class FileWriteNode extends BaseNode implements Inputable {
    public enum Format { CSV, JSONL }

    private static final Gson GSON = new Gson();

    @OptionField(label = "파일 경로")
    private String path;
    @OptionField(type = OptionInputType.RADIO_BUTTON, label = "형식")
    private Format format = Format.CSV;
    @OptionField(type = OptionInputType.TEXT_AREA, label = "기록할 필드 (줄마다 이름, 비우면 첫 메시지의 모든 필드)")
    private String fields;
    @OptionField(type = OptionInputType.CHECK_BOX, label = "기존 파일에 이어 쓰기")
    private boolean append = true;

    private BufferedWriter writer;
    private List<String> columns;
    private boolean headerPending;

    public FileWriteNode(String id) {
        super(NodeType.FILE_WRITE, id);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (path == null || path.isBlank()) throw new IllegalStateException("파일 경로가 비어 있습니다.");
        try {
            Path file = Path.of(path);
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            boolean resume = append && Files.exists(file) && Files.size(file) > 0;
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
            headerPending = format == Format.CSV && !resume; // 이어 쓰는 파일에는 머리글을 다시 쓰지 않습니다.
            columns = FieldMappings.names(fields);
        } catch (IOException e) {
            throw new IllegalStateException("파일을 열 수 없습니다: " + path + " (" + e.getMessage() + ")", e);
        }
    }

    @Override
    protected void process() throws Exception {
        Message message = getInbox().take();
        if (columns == null) columns = new ArrayList<>(message.getPayload().keySet());

        if (headerPending) {
            writer.write(csvLine(columns));
            headerPending = false;
        }
        Map<String, Object> row = selected(message);
        writer.write(format == Format.CSV ? csvLine(new ArrayList<>(row.values())) : GSON.toJson(row) + System.lineSeparator());
        writer.flush();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (writer == null) return;
        try {
            writer.close();
        } catch (IOException e) {
            warn("파일을 닫는 중 오류: " + e.getMessage());
        } finally {
            writer = null;
        }
    }

    private Map<String, Object> selected(Message message) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (String column : columns) row.put(column, message.get(column));
        return row;
    }

    // RFC 4180: 쉼표·따옴표·줄바꿈이 있으면 따옴표로 감싸고 따옴표는 두 번 씁니다.
    private static String csvLine(List<?> values) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) line.append(',');
            Object value = values.get(i);
            String text = value == null ? "" : value.toString();
            if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
                text = "\"" + text.replace("\"", "\"\"") + "\"";
            }
            line.append(text);
        }
        return line.append(System.lineSeparator()).toString();
    }
}
