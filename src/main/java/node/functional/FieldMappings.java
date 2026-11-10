package node.functional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 옵션 텍스트의 줄들을 읽습니다. 빈 줄과 #으로 시작하는 줄은 건너뜁니다.
 * {@link #parse}는 "이름 = 식" 줄을 이름 → 식 맵으로, {@link #names}는 줄마다 이름 하나를 목록으로 만듭니다.
 */
final class FieldMappings {
    private FieldMappings() { }

    /** 줄마다 이름 하나입니다. 이름이 하나도 없으면 null을 돌려줘 "지정 안 함"과 구분합니다. */
    static List<String> names(String text) {
        List<String> names = lines(text);
        return names.isEmpty() ? null : names;
    }

    static Map<String, String> parse(String text) {
        Map<String, String> mappings = new LinkedHashMap<>();
        for (String line : lines(text)) {
            int separator = line.indexOf('=');
            if (separator <= 0) throw new IllegalArgumentException("\"이름 = 식\" 형식이 아닙니다: " + line);
            String name = line.substring(0, separator).strip();
            String expression = line.substring(separator + 1).strip();
            if (name.isEmpty() || expression.isEmpty()) throw new IllegalArgumentException("이름이나 식이 비어 있습니다: " + line);
            mappings.put(name, expression);
        }
        return mappings;
    }

    private static List<String> lines(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null) return lines;
        for (String raw : text.split("\\R")) {
            String line = raw.strip();
            if (!line.isEmpty() && !line.startsWith("#")) lines.add(line);
        }
        return lines;
    }
}
