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
    void nodesCreatedBackToBackGetDistinctIds() {
        // 이전 구현(밀리초 해시)에서는 같은 밀리초에 만든 노드들이 같은 ID를 받았습니다.
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 50; i++) ids.add(new node.functional.InjectNode().getId());
        assertEquals(50, ids.size());
    }
}
