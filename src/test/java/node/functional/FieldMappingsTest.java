package node.functional;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FieldMappingsTest {

    @Test
    void parsesNameEqualsExpressionLinesSkippingBlanksAndComments() {
        Map<String, String> mappings = FieldMappings.parse("title = h2 a\n\n# 주석\nlink = h2 a @href\n price=.price \n");
        assertEquals(List.of("title", "link", "price"), List.copyOf(mappings.keySet()));
        assertEquals("h2 a @href", mappings.get("link"));
        assertEquals(".price", mappings.get("price"));
        assertTrue(FieldMappings.parse(null).isEmpty());
    }

    @Test
    void rejectsLinesWithoutNameOrExpression() {
        assertThrows(IllegalArgumentException.class, () -> FieldMappings.parse("just text"));
        assertThrows(IllegalArgumentException.class, () -> FieldMappings.parse("= .price"));
        assertThrows(IllegalArgumentException.class, () -> FieldMappings.parse("name ="));
    }
}
