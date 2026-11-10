package node.functional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import support.TestNodes;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class ScheduleNodeTest {
    private final ScheduleNode node = new ScheduleNode("sch1");
    private final TestNodes.Sink sink = new TestNodes.Sink("sink1");

    @AfterEach
    void stop() throws Exception {
        node.stop(); sink.stop();
        if (node.getThread() != null) node.getThread().join(2_000);
        if (sink.getThread() != null) sink.getThread().join(2_000);
    }

    @Test
    void runsOnceAtStartThenWaitsForTheNextMatchingMinute() throws Exception {
        node.setCron("* * * * *");
        node.setMessage("tick");
        node.connect(sink);
        node.start(); sink.start();

        assertTrue(sink.awaitMessages(1, 2_000), "시작할 때 한 번 바로");
        assertEquals("tick", sink.received.get(0).toString());
        ZonedDateTime next = node.getNextRunAt();
        assertEquals(0, next.getSecond());
        assertTrue(next.isAfter(ZonedDateTime.now()) && !next.isAfter(ZonedDateTime.now().plusMinutes(1)), "다음은 다음 분 정각: " + next);
        assertTrue(node.isAlive(), "다음 시각까지 기다리는 중");
    }

    @Test
    void withoutRunOnStartNothingIsSentBeforeTheScheduledTime() throws Exception {
        node.setCron("0 0 1 1 *"); // 1월 1일 0시
        node.setRunOnStart(false);
        node.connect(sink);
        node.start(); sink.start();

        Thread.sleep(300);
        assertEquals(0, sink.received.size());
        assertEquals(ZonedDateTime.now().getYear() + 1, node.getNextRunAt().getYear());
        assertNull(node.getFailure());
    }

    @Test
    void emptyMessageSendsTheScheduledTime() throws Exception {
        node.setCron("* * * * *");
        node.connect(sink);
        node.start(); sink.start();
        assertTrue(sink.awaitMessages(1, 2_000));
        assertTrue(sink.received.get(0).toString().startsWith(ZonedDateTime.now().truncatedTo(ChronoUnit.HOURS).toLocalDate().toString() + "T"));
    }

    @Test
    void badCronFailsAtStart() throws Exception {
        node.setCron("every day");
        node.start();
        node.getThread().join(2_000);
        assertNotNull(node.getFailure());
        assertTrue(node.getFailure().getMessage().startsWith("cron 식 오류"), node.getFailure().getMessage());
    }
}
