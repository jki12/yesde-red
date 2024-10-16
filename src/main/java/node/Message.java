package node;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Message implements Comparable<Message> {
    private static final int DEFAULT_ORDER = 0;

    private int order;
    private String message;

    public Message(int order, String message) {
        this.order = order;
        this.message = message;
    }

    public Message(String message) {
        this(DEFAULT_ORDER, message);
    }

    @Override
    public int compareTo(Message o) {
        return o.order - this.order;
    }

    @Override
    public String toString() {
        return message;
    }
}
