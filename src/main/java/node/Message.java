package node;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Message implements Comparable<Message> {
    private static final int DEFAULT_PRIORITY = 0;

    private int priority;
    private String text;

    public Message(int priority, String text) {
        this.priority = priority;
        this.text = text;
    }

    public Message(String text) {
        this(DEFAULT_PRIORITY, text);
    }

    @Override
    public int compareTo(Message o) {
        return o.priority - this.priority; // 우선순위가 높은 메시지가 먼저 처리됩니다.
    }

    @Override
    public String toString() {
        return text;
    }
}
