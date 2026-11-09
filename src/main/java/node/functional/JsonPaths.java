package node.functional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 아주 작은 JSON 경로 문법입니다. 점으로 구분한 이름과 [숫자] 인덱스를 지원하고, 항목 경로의 끝에만 [*]를 쓸 수 있습니다.
 * 예) data.items[*], results[0].name, [*], price.amount
 */
final class JsonPaths {
    private static final Pattern SEGMENT = Pattern.compile("([^\\[\\]]*)((?:\\[(?:\\d+|\\*)\\])*)");
    private static final Pattern INDEX = Pattern.compile("\\[(\\d+|\\*)\\]");

    private JsonPaths() { }

    /** 경로가 비면 루트 하나, [*]로 끝나면 그 배열의 원소들, 아니면 경로가 가리키는 값 하나입니다. 없으면 빈 목록입니다. */
    static List<JsonElement> items(JsonElement root, String path) {
        if (path == null || path.isBlank()) return List.of(root);
        String trimmed = path.strip();
        boolean each = trimmed.endsWith("[*]");
        JsonElement target = value(root, each ? trimmed.substring(0, trimmed.length() - 3) : trimmed);
        if (target == null) return List.of();
        if (!each) return List.of(target);
        if (!target.isJsonArray()) throw new IllegalArgumentException("[*] 앞은 배열이어야 합니다: " + path);
        List<JsonElement> elements = new ArrayList<>();
        target.getAsJsonArray().forEach(elements::add);
        return elements;
    }

    /** 경로가 가리키는 값입니다. 중간에 없거나 null이면 null입니다. 문법이 틀리면 IllegalArgumentException입니다. */
    static JsonElement value(JsonElement node, String path) {
        JsonElement current = node;
        if (path == null || path.isBlank()) return current;
        for (String segment : path.strip().split("\\.", -1)) {
            Matcher matcher = SEGMENT.matcher(segment);
            if (!matcher.matches()) throw new IllegalArgumentException("경로 문법이 틀렸습니다: " + path);
            String name = matcher.group(1);
            if (!name.isEmpty()) {
                if (current == null || !current.isJsonObject()) return null;
                current = current.getAsJsonObject().get(name);
            } else if (segment.isEmpty()) {
                throw new IllegalArgumentException("경로 문법이 틀렸습니다: " + path);
            }
            Matcher index = INDEX.matcher(matcher.group(2));
            while (index.find()) {
                if ("*".equals(index.group(1))) throw new IllegalArgumentException("[*]는 항목 경로의 끝에만 쓸 수 있습니다: " + path);
                int i = Integer.parseInt(index.group(1));
                if (current == null || !current.isJsonArray() || i >= current.getAsJsonArray().size()) return null;
                current = current.getAsJsonArray().get(i);
            }
            if (current == null || current.isJsonNull()) return null;
        }
        return current;
    }

    /** JSON 값을 payload에 넣을 자바 값으로 바꿉니다. 정수는 Long, 소수는 Double, 객체는 Map, 배열은 List입니다. */
    static Object toJava(JsonElement element) {
        if (element == null || element.isJsonNull()) return null;
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) return primitive.getAsBoolean();
            if (primitive.isNumber()) {
                BigDecimal number = primitive.getAsBigDecimal();
                return number.stripTrailingZeros().scale() <= 0 && number.abs().compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) <= 0
                        ? (Object) number.longValueExact() : (Object) number.doubleValue();
            }
            return primitive.getAsString();
        }
        if (element.isJsonArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonElement item : (JsonArray) element) list.add(toJava(item));
            return list;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : ((JsonObject) element).entrySet()) map.put(entry.getKey(), toJava(entry.getValue()));
        return map;
    }
}
