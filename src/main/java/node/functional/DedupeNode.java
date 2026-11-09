package node.functional;

import lombok.Getter;
import lombok.Setter;
import node.BaseNode;
import node.Inputable;
import node.Message;
import node.NodeType;
import node.Outputable;
import node.option.OptionField;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 기준 필드의 값이 처음 보이는 메시지만 통과시킵니다. 같은 글이 수집마다 반복되는 것을 걸러 새 글만 남깁니다.
 * 기준 필드가 없는 메시지는 경고를 남기고 그대로 통과시킵니다.
 */
@Getter
@Setter
public class DedupeNode extends BaseNode implements Inputable, Outputable {
    @OptionField(label = "기준 필드 (예: link)")
    private String key = "link";
    @OptionField(min = 1, label = "기억할 최대 개수 (넘으면 오래된 것부터 잊음)")
    private int maxEntries = 100_000;
    @OptionField(label = "기억 파일 (비우면 실행 중에만 기억, 채우면 다시 실행해도 유지)")
    private String storePath;

    private final Set<String> seen = new LinkedHashSet<>();
    private BufferedWriter store;

    public DedupeNode(String id) {
        super(NodeType.DEDUPE, id);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (storePath == null || storePath.isBlank()) return;
        try {
            Path file = Path.of(storePath);
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            if (Files.exists(file)) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) remember(line);
                }
            }
            store = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("기억 파일을 열 수 없습니다: " + storePath + " (" + e.getMessage() + ")", e);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (store == null) return;
        try {
            store.close();
        } catch (IOException e) {
            warn("기억 파일을 닫는 중 오류: " + e.getMessage());
        } finally {
            store = null;
        }
    }

    @Override
    protected void process() throws Exception {
        Message message = getInbox().take();
        String value = key == null || key.isBlank() ? null : message.getString(key);
        if (value == null) {
            warn("기준 필드 \"" + key + "\"가 없는 메시지라 그대로 통과시킵니다.");
            send(message);
            return;
        }
        if (!remember(value)) return;
        persist(value);
        send(message);
    }

    // 새 값만 한 줄씩 덧붙입니다. 쓰기에 실패해도 수집은 계속하고 경고만 남깁니다.
    private void persist(String value) {
        if (store == null) return;
        try {
            store.write(value);
            store.newLine();
            store.flush();
        } catch (IOException e) {
            warn("기억 파일에 쓰지 못했습니다: " + e.getMessage());
        }
    }

    /** 처음 보는 값이면 기억하고 true, 이미 본 값이면 false입니다. */
    protected boolean remember(String value) {
        if (!seen.add(value)) return false;
        if (seen.size() > maxEntries) {
            Iterator<String> oldest = seen.iterator();
            oldest.next();
            oldest.remove();
        }
        return true;
    }

    /** 지금까지 기억한 값의 수입니다. */
    public int getSeenCount() {
        return seen.size();
    }
}
