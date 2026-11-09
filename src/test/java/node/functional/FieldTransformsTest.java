package node.functional;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FieldTransformsTest {

    private static Object eval(String expression, Map<String, Object> payload) {
        return FieldTransforms.parseExpression(expression).evaluate(payload);
    }

    @Test
    void copiesConstantsAndFields() {
        Map<String, Object> payload = Map.of("title", "  [롯데온] 백팩 (73,470원) ", "n", 5);
        assertEquals("clien", eval("\"clien\"", payload));
        assertEquals("clien", eval("const(\"clien\")", payload));
        assertEquals(5, eval("n", payload));
        assertEquals("[롯데온] 백팩 (73,470원)", eval("trim(title)", payload));
        assertEquals("abc", eval("lower(\"ABC\")", payload));
        assertNull(eval("missing", payload));
        assertNull(eval("trim(missing)", payload), "null은 null로");
    }

    @Test
    void extractsWithRegexAndReplaces() {
        Map<String, Object> payload = Map.of("title", "[롯데온] 쌤소나이트 백팩 (73,470원)", "link", "https://www.clien.net/service/board/jirum/19289962?od=T31");
        assertEquals("롯데온", eval("regex(title, \"^\\\\[(.+?)\\\\]\")", payload));
        assertEquals("19289962", eval("regex(link, \"/jirum/(\\\\d+)\")", payload));
        assertEquals("(73,470원)", eval("regex(title, \"\\\\(.*?\\\\)\")", payload), "그룹이 없으면 전체 일치");
        assertNull(eval("regex(title, \"없는패턴\")", payload));
        assertEquals("쌤소나이트 백팩", eval("trim(replace(replace(title, \"^\\\\[.+?\\\\]\", \"\"), \"\\\\(.*?\\\\)$\", \"\"))", payload), "중첩 호출");
        assertEquals("https://www.clien.net/service/board/jirum/19289962", eval("replace(link, \"\\\\?.*$\", \"\")", payload));
    }

    @Test
    void parsesNumbersFromMessyText() {
        assertEquals(73470L, FieldTransforms.number("(73,470원)"));
        assertEquals(27300L, FieldTransforms.number("27.3 k"));
        assertEquals(3L, FieldTransforms.number("(3)"));
        assertEquals(12000L, FieldTransforms.number("1.2만"));
        assertEquals(4.5, FieldTransforms.number("4.5"));
        assertEquals(352L, FieldTransforms.number("[0|352|0|0]"), "첫 번째 숫자");
        assertNull(FieldTransforms.number("가격 미정"));
        assertNull(FieldTransforms.number(null));
    }

    @Test
    void formatsTemplatesAndNormalizesDatetimes() {
        Map<String, Object> payload = Map.of("source", "clien", "pid", "19289962");
        assertEquals("clien:19289962", eval("format(\"{source}:{pid}\")", payload));
        assertEquals("clien:", eval("format(\"{source}:{missing}\")", payload), "없는 필드는 빈칸");

        assertEquals("2026-11-09T21:17:40+09:00", eval("datetime(t, \"rfc1123\")", Map.of("t", "Mon, 09 Nov 2026 21:17:40 +0900")));
        assertEquals("2026-11-09T21:40:54+09:00", eval("datetime(t, \"rfc1123\")", Map.of("t", "Mon, 09 Nov 2026 12:40:54 GMT")), "GMT를 서울 시각으로");
        assertEquals("2026-11-09T20:50:06+09:00", eval("datetime(t, \"yyyy-MM-dd HH:mm:ss\")", Map.of("t", "2026-11-09 20:50:06")));
        String today = LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
        assertEquals(today + "T18:26:00+09:00", eval("datetime(t, \"HH:mm\")", Map.of("t", "18:26")), "시각만 있으면 오늘 날짜");
        assertNull(eval("datetime(t, \"rfc1123\")", new LinkedHashMap<>()), "값이 없으면 null");
        assertTrue(((String) eval("now()", Map.of())).endsWith("+09:00"));
        assertThrows(FieldTransforms.TransformException.class, () -> eval("datetime(t, \"rfc1123\")", Map.of("t", "어제쯤")));
    }

    @Test
    void appliesRulesInOrderSoLaterRulesSeeEarlierResults() {
        List<FieldTransforms.Rule> rules = FieldTransforms.parseRules("""
                source = "clien"
                pid = regex(link, "/jirum/(\\\\d+)")
                id = format("{source}:{pid}")
                price = number(regex(title, "\\\\(([^)]*원)"))
                """);
        Map<String, Object> result = FieldTransforms.apply(rules, Map.of("link", "https://www.clien.net/service/board/jirum/19289962", "title", "[롯데온] 백팩 (73,470원)"));
        assertEquals("clien:19289962", result.get("id"));
        assertEquals(73470L, result.get("price"));
        assertEquals("[롯데온] 백팩 (73,470원)", result.get("title"), "기존 필드는 유지");
    }

    @Test
    void reportsSyntaxErrorsAtParseTime() {
        assertThrows(IllegalArgumentException.class, () -> FieldTransforms.parseExpression("regex(title"));
        assertThrows(IllegalArgumentException.class, () -> FieldTransforms.parseExpression("unknown(title)"));
        assertThrows(IllegalArgumentException.class, () -> FieldTransforms.parseExpression("regex(title, \"[\")"), "정규식 오류는 미리 잡음");
        assertThrows(IllegalArgumentException.class, () -> FieldTransforms.parseExpression("regex(title, pattern)"), "패턴은 문자열 상수");
        assertThrows(IllegalArgumentException.class, () -> FieldTransforms.parseExpression("number(a, b)"));
        assertThrows(IllegalArgumentException.class, () -> FieldTransforms.parseExpression("\"unterminated"));
        assertThrows(IllegalArgumentException.class, () -> FieldTransforms.parseExpression("title extra"));
    }
}
