package node;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MessageTest {

    @Test
    void textMessageKeepsItsTextUnderTheTextKey() {
        Message message = new Message("hello");
        assertEquals("hello", message.get(Message.TEXT));
        assertEquals("hello", message.toString(), "text 하나뿐이면 문자열 그대로");
    }

    @Test
    void structuredPayloadRendersAsJsonAndIsImmutable() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("url", "https://example.com");
        source.put("status", 200);
        Message message = new Message(source);
        source.put("status", 500); // 원본 맵을 바꿔도 메시지는 그대로

        assertEquals("200", message.getString("status"));
        assertEquals("{\"url\":\"https://example.com\",\"status\":200}", message.toString());
        assertThrows(UnsupportedOperationException.class, () -> message.getPayload().put("x", 1));
        assertNull(message.getString("missing"));
    }

    @Test
    void withCreatesACopyWithTheExtraField() {
        Message original = new Message("a");
        Message extended = original.with("count", 2).withAll(Map.of("flag", true));

        assertEquals("a", original.toString(), "원본은 그대로");
        assertEquals(2, extended.get("count"));
        assertEquals(true, extended.get("flag"));
        assertEquals("a", extended.get(Message.TEXT));
    }
}
