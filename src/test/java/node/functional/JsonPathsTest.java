package node.functional;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonPathsTest {
    private final JsonElement root = JsonParser.parseString("""
            {"data": {"items": [{"name": "a", "price": {"amount": 10, "unit": "KRW"}, "tags": ["x", "y"]},
                                {"name": "b", "price": {"amount": 2.5}}],
                      "count": 2, "flag": true, "nothing": null}}
            """);

    @Test
    void navigatesNamesIndexesAndStarItems() {
        assertEquals(2, JsonPaths.items(root, "data.items[*]").size());
        assertEquals(1, JsonPaths.items(root, "").size(), "빈 경로는 루트 하나");
        assertEquals(1, JsonPaths.items(root, "data.items[1]").size(), "[*] 없이 배열 원소 하나");
        assertTrue(JsonPaths.items(root, "data.missing[*]").isEmpty(), "없는 경로는 빈 목록");

        JsonElement first = JsonPaths.items(root, "data.items[*]").get(0);
        assertEquals("a", JsonPaths.toJava(JsonPaths.value(first, "name")));
        assertEquals(10L, JsonPaths.toJava(JsonPaths.value(first, "price.amount")), "정수는 Long");
        assertEquals("y", JsonPaths.toJava(JsonPaths.value(first, "tags[1]")));
        assertEquals(List.of("x", "y"), JsonPaths.toJava(JsonPaths.value(first, "tags")));
        assertEquals(Map.of("amount", 10L, "unit", "KRW"), JsonPaths.toJava(JsonPaths.value(first, "price")));
        assertEquals(2.5, JsonPaths.toJava(JsonPaths.value(root, "data.items[1].price.amount")), "소수는 Double");
        assertEquals(true, JsonPaths.toJava(JsonPaths.value(root, "data.flag")));
        assertNull(JsonPaths.value(root, "data.nothing"), "JSON null은 null");
        assertNull(JsonPaths.value(root, "data.items[5].name"), "범위 밖 인덱스는 null");
        assertNull(JsonPaths.value(root, "data.count.deeper"), "원시값 아래로는 내려갈 수 없음");
    }

    @Test
    void rejectsBadSyntax() {
        assertThrows(IllegalArgumentException.class, () -> JsonPaths.value(root, "data..items"));
        assertThrows(IllegalArgumentException.class, () -> JsonPaths.value(root, "data.items[*].name"));
        assertThrows(IllegalArgumentException.class, () -> JsonPaths.items(root, "data.count[*]"));
    }
}
