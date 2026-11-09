package node.functional;

import lombok.Getter;
import lombok.Setter;
import node.BaseNode;
import node.Inputable;
import node.Message;
import node.NodeType;
import node.Outputable;
import node.option.OptionField;

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

    private final Set<String> seen = new LinkedHashSet<>();

    public DedupeNode(String id) {
        super(NodeType.DEDUPE, id);
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
        send(message);
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
