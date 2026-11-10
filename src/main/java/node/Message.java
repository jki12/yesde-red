package node;

import com.google.gson.Gson;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 노드 사이를 오가는 데이터 한 건입니다. payload는 이름 → 값의 순서 있는 맵이며 만든 뒤에는 바뀌지 않습니다.
 * 문자열 하나만 담는 메시지는 {@link #TEXT} 키 하나를 가집니다. 노드는 받은 payload에 자기 필드를 더해 넘깁니다.
 */
public final class Message {
    public static final String TEXT = "text";
    private static final Gson GSON = new Gson();

    private final Map<String, Object> payload;

    public Message(Map<String, ?> payload) {
        this.payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    public Message(String text) {
        this(Collections.singletonMap(TEXT, text));
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
        return new Message(copy);
    }

    /** values를 모두 더한 새 메시지입니다. 같은 키는 덮어씁니다. */
    public Message withAll(Map<String, ?> values) {
        Map<String, Object> copy = new LinkedHashMap<>(payload);
        copy.putAll(values);
        return new Message(copy);
    }

    /** text 하나뿐이면 그 문자열, 아니면 payload 전체를 한 줄 JSON으로. 콘솔 표시에 씁니다. */
    @Override
    public String toString() {
        if (payload.size() == 1 && payload.containsKey(TEXT)) return String.valueOf(payload.get(TEXT));
        return GSON.toJson(payload);
    }
}
