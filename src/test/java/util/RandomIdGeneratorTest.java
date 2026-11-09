package util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RandomIdGeneratorTest {

    @Test
    void producesSixLowercaseHexCharacters() {
        RandomIdGenerator generator = new RandomIdGenerator();
        for (int i = 0; i < 100; i++) {
            assertTrue(generator.get().matches("[0-9a-f]{6}"));
        }
    }

    @Test
    void neverRepeatsWithinOneGenerator() {
        RandomIdGenerator generator = new RandomIdGenerator();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            assertTrue(seen.add(generator.get()), "같은 생성기에서 중복 ID가 나옴");
        }
    }

    @Test
    void reservedIdsAreNeverIssued() {
        RandomIdGenerator generator = new RandomIdGenerator();
        for (int i = 0; i < 0x1000; i++) generator.reserve(String.format("%06x", i)); // 000000~000fff 예약
        for (int i = 0; i < 2_000; i++) assertFalse(generator.get().startsWith("000"), "예약된 ID가 발급됨");
    }

    @Test
    void factoryCreatesNodesWithAGivenId() {
        node.BaseNode created = node.NodeFactory.createNode(node.NodeType.INJECT, "abc123");
        assertEquals("abc123", created.getId());
        assertEquals(node.NodeType.INJECT, created.getType());
        assertNotEquals("abc123", node.NodeFactory.createNode(node.NodeType.INJECT).getId(), "새 노드는 다른 ID");
    }

    @Test
    void nodesCreatedBackToBackGetDistinctIds() {
        // 이전 구현(밀리초 해시)에서는 같은 밀리초에 만든 노드들이 같은 ID를 받았습니다.
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 50; i++) ids.add(new node.functional.InjectNode().getId());
        assertEquals(50, ids.size());
    }
}
