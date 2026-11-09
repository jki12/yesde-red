package node;

import org.junit.jupiter.api.Test;
import support.TestNodes;

import static org.junit.jupiter.api.Assertions.*;

class PollingNodeTest {

    @Test
    void defaultIntervalIsOneSecond() {
        assertEquals(1_000, new TestNodes.Source("s1").getIntervalMillis());
    }

    @Test
    void rejectsNegativeInterval() {
        TestNodes.Source source = new TestNodes.Source("s1");
        assertThrows(IllegalArgumentException.class, () -> source.setIntervalMillis(-1));
        assertDoesNotThrow(() -> source.setIntervalMillis(0), "0은 허용");
        assertEquals(0, source.getIntervalMillis());
    }
}
