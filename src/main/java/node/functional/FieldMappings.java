package node.functional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 옵션 텍스트의 "이름 = 식" 줄들을 이름 → 식 맵으로 읽습니다. 빈 줄과 #으로 시작하는 줄은 건너뜁니다.
 * 추출 노드들이 필드 목록 옵션에 같은 문법을 씁니다.
 */
final class FieldMappings {
    private FieldMappings() { }

    static Map<String, String> parse(String text) {
        Map<String, String> mappings = new LinkedHashMap<>();
        if (text == null) return mappings;
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int separator = line.indexOf('=');
            if (separator <= 0) throw new IllegalArgumentException("\"이름 = 식\" 형식이 아닙니다: " + line);
            String name = line.substring(0, separator).strip();
            String expression = line.substring(separator + 1).strip();
            if (name.isEmpty() || expression.isEmpty()) throw new IllegalArgumentException("이름이나 식이 비어 있습니다: " + line);
            mappings.put(name, expression);
        }
        return mappings;
    }
}
