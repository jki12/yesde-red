package node;

import com.google.gson.Gson;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 노드 사이를 오가는 데이터 한 건입니다. payload는 이름 → 값의 순서 있는 맵이며 만든 뒤에는 바뀌지 않습니다.
 * 문자열 하나만 담는 메시지는 {@link #TEXT} 키 하나를 가집니다. 노드는 받은 payload에 자기 필드를 더해 넘깁니다.
 */
public final class Message implements Comparable<Message> {
    public static final String TEXT = "text";
    private static final int DEFAULT_PRIORITY = 0;
    private static final Gson GSON = new Gson();
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private final int priority;
    // 같은 우선순위끼리는 만든 순서대로 처리되도록 하는 순번입니다. 우선순위 큐만으로는 순서가 보장되지 않습니다.
    private final long sequence = SEQUENCE.incrementAndGet();
    private final Map<String, Object> payload;

    public Message(int priority, Map<String, ?> payload) {
        this.priority = priority;
        this.payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    public Message(Map<String, ?> payload) {
        this(DEFAULT_PRIORITY, payload);
    }

    public Message(int priority, String text) {
        this(priority, single(text));
    }

    public Message(String text) {
        this(DEFAULT_PRIORITY, text);
    }

    private static Map<String, Object> single(String text) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(TEXT, text);
        return payload;
    }

    public int getPriority() {
        return priority;
    }

    /** 읽기 전용 payload입니다. */
    public Map<String, Object> getPayload() {
        return payload;
    }

    public Object get(String key) {
        return payload.get(key);
    }

    /** 값을 문자열로 돌려줍니다. 없거나 null이면 null입니다. */
    public String getString(String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    /** key를 value로 바꾼(또는 더한) 새 메시지입니다. 원본은 그대로입니다. */
    public Message with(String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(payload);
        copy.put(key, value);
        return new Message(priority, copy);
    }

    /** values를 모두 더한 새 메시지입니다. 같은 키는 덮어씁니다. */
    public Message withAll(Map<String, ?> values) {
        Map<String, Object> copy = new LinkedHashMap<>(payload);
        copy.putAll(values);
        return new Message(priority, copy);
    }

    @Override
    public int compareTo(Message o) {
        int byPriority = Integer.compare(o.priority, this.priority); // 우선순위가 높은 메시지가 먼저
        return byPriority != 0 ? byPriority : Long.compare(this.sequence, o.sequence); // 같으면 먼저 만든 것부터
    }

    /** text 하나뿐이면 그 문자열, 아니면 payload 전체를 한 줄 JSON으로. 콘솔 표시에 씁니다. */
    @Override
    public String toString() {
        if (payload.size() == 1 && payload.containsKey(TEXT)) return String.valueOf(payload.get(TEXT));
        return GSON.toJson(payload);
    }
}
