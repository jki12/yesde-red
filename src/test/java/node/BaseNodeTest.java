package node;

import org.junit.jupiter.api.Test;
import support.TestNodes;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class BaseNodeTest {

    @Test
    void consumerDrainsQueuedMessagesWithoutPausing() throws Exception {
        TestNodes.Sink sink = new TestNodes.Sink("d1");
        for (int i = 0; i < 5; i++) sink.receive(new Message("pre" + i));

        long started = System.nanoTime();
        sink.start();
        try {
            waitUntil(() -> sink.seen.get() == 5, 1_000);
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
            assertTrue(elapsedMillis < 500, "소비 노드는 메시지 사이에 쉬지 않아야 함, 걸린 시간 " + elapsedMillis + "ms");
        } finally {
            sink.stop();
        }
        sink.getThread().join(1_000);
        assertFalse(sink.isExecutionActive(), "큐 대기 중 stop()으로 종료돼야 함");
    }

    @Test
    void pollingSourcePacesByIntervalAndConsumerKeepsUp() throws Exception {
        TestNodes.Source source = new TestNodes.Source("s1");
        TestNodes.Sink sink = new TestNodes.Sink("d1");
        source.setIntervalMilli(50);
        source.connect(sink);

        source.start();
        sink.start();
        Thread.sleep(330);
        source.stop();
        sink.stop();
        source.getThread().join(1_000);
        sink.getThread().join(1_000);

        int sent = source.sent.get();
        assertTrue(sent >= 4 && sent <= 9, "50ms 간격으로 330ms 동안 보낸 수: " + sent);
        assertTrue(sink.seen.get() >= sent - 1, "소비 노드가 따라잡아야 함: seen=" + sink.seen.get() + " sent=" + sent);
    }

    @Test
    void connectOnlyLinksOutputToInputOnce() {
        TestNodes.Source source = new TestNodes.Source("s1");
        TestNodes.Sink sink = new TestNodes.Sink("d1");

        assertTrue(source.connect(sink));
        assertFalse(source.connect(sink), "같은 대상은 두 번 연결되지 않음");
        assertEquals(1, source.getOut().size());
        assertNull(sink.getOut(), "입력 전용 노드는 연결 목록이 없음");
        assertThrows(UnsupportedOperationException.class, () -> source.getOut().clear(), "외부에서는 읽기 전용");
    }

    @Test
    void connectingWhileSendingDoesNotThrow() throws Exception {
        TestNodes.Source source = new TestNodes.Source("s1");
        source.connect(new TestNodes.Sink("d0"));

        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean running = new AtomicBoolean(true);
        Thread sender = new Thread(() -> {
            try {
                while (running.get()) source.emit(); // send()를 반복하는 워커 역할
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        sender.start();
        try {
            for (int i = 0; i < 3_000 && failure.get() == null; i++) source.connect(new TestNodes.Sink("d" + (i + 1)));
        } finally {
            running.set(false);
            sender.join(2_000);
        }

        assertNull(failure.get(), "송신 중 연결 추가가 예외를 일으키면 안 됨");
        assertEquals(3_001, source.getOut().size());
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) fail("시간 안에 조건을 만족하지 못함");
            Thread.sleep(5);
        }
    }
}
