package node.functional;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConditionsTest {
    private static final Map<String, Object> DEAL = new HashMap<>();
    static {
        DEAL.put("title", "[가상몰] 무선 키보드 K1 (39,900원/무료)");
        DEAL.put("price", 39900L);
        DEAL.put("hits", "2100");   // 문자열로 들어온 숫자
        DEAL.put("source", "clien");
        DEAL.put("shop", null);
    }

    private static boolean test(String condition) {
        return Conditions.parse(condition).test(DEAL);
    }

    @Test
    void comparesNumbersAndStrings() {
        assertTrue(test("price < 40000"));
        assertFalse(test("price < 20000"));
        assertTrue(test("price >= 39900 and price <= 39900"));
        assertTrue(test("hits > 2000"), "문자열 숫자도 숫자로 비교");
        assertTrue(test("source = \"CLIEN\""), "문자열 비교는 대소문자 무시");
        assertTrue(test("source != \"ruliweb\""));
        assertTrue(test("price = 39900.0"));
        assertFalse(test("title < 10"), "숫자가 아닌 쪽이 있으면 크기 비교는 거짓");
    }

    @Test
    void handlesNullAndMissingFields() {
        assertTrue(test("shop = null"));
        assertTrue(test("missing = null"), "없는 필드는 null");
        assertFalse(test("shop != null"));
        assertFalse(test("shop contains \"a\""));
        assertFalse(test("missing < 1"));
        assertTrue(test("not shop != null"));
    }

    @Test
    void containsAndMatches() {
        assertTrue(test("title contains \"키보드\""));
        assertTrue(test("title contains \"k1\""), "contains는 대소문자 무시");
        assertFalse(test("title contains \"마우스\""));
        assertTrue(test("title matches \"^\\\\[.*\\\\]\""));
        assertTrue(test("title matches \"\\\\d{2},\\\\d{3}원\""));
    }

    @Test
    void combinesWithAndOrNotAndParentheses() {
        assertTrue(test("price < 20000 or title contains \"키보드\""));
        assertFalse(test("price < 20000 and title contains \"키보드\""));
        assertTrue(test("(source = \"clien\" or source = \"ruliweb\") and not price > 50000"));
        assertTrue(test("not (price > 50000 and source = \"clien\")"));
        assertTrue(test("source = \"ruliweb\" or source = \"ppomppu\" or price < 40000"), "or는 왼쪽부터 묶임");
        assertFalse(test("source = \"ruliweb\" or source = \"ppomppu\" and price < 40000"), "and가 or보다 먼저 묶임");
    }

    @Test
    void rejectsBadSyntaxAtParseTime() {
        for (String bad : new String[] {"", "price <", "price < 1 2", "price ? 1", "(price < 1", "title matches \"[\"", "title matches price", "price < \"a\" ;"}) {
            assertThrows(IllegalArgumentException.class, () -> Conditions.parse(bad), bad);
        }
    }
}
