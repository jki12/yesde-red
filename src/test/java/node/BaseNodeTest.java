package node;

import org.junit.jupiter.api.Test;
import support.TestNodes;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
        assertFalse(sink.isAlive(), "큐 대기 중 stop()으로 종료돼야 함");
    }

    @Test
    void pollingSourcePacesByIntervalAndConsumerKeepsUp() throws Exception {
        TestNodes.Source source = new TestNodes.Source("s1");
        TestNodes.Sink sink = new TestNodes.Sink("d1");
        source.setIntervalMillis(50);
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
        assertEquals(1, source.getTargets().size());
        assertTrue(sink.getTargets().isEmpty(), "입력 전용 노드는 연결이 없음");
        assertThrows(UnsupportedOperationException.class, () -> source.getTargets().clear(), "외부에서는 읽기 전용");
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
        assertEquals(3_001, source.getTargets().size());
    }

    @Test
    void statsCountThisRunsMessagesAndWarnings() throws Exception {
        TestNodes.Source source = new TestNodes.Source("s1");
        TestNodes.Warner warner = new TestNodes.Warner("w1");
        source.connect(warner);
        for (int i = 0; i < 3; i++) source.emit();

        assertEquals(new BaseNode.Stats(0, 3, 0, source.getStats().lastActivityAt()), source.getStats());
        assertEquals(3, warner.getStats().received(), "받음은 큐에 들어올 때 셈");
        assertTrue(source.getStats().lastActivityAt() > 0);

        warner.start();
        warner.getThread().join(100);
        long deadline = System.currentTimeMillis() + 2_000;
        while (warner.warned.get() < 3 && System.currentTimeMillis() < deadline) Thread.sleep(10);
        assertEquals(3, warner.getStats().warnings());
        assertEquals(0, warner.getStats().received(), "start()가 이번 실행의 수를 0으로 돌림(큐에 남아 있던 3개는 이전 실행분)");
        warner.stop();
        warner.getThread().join(2_000);
    }

    @Test
    void errorsAreRecordedAsFailuresToo() throws Exception {
        BaseNode node = new BaseNode(NodeType.DEBUG) {
            @Override protected void process() { throw new NoClassDefFoundError("missing"); }
        };
        node.start();
        node.getThread().join(2_000);

        assertFalse(node.isAlive());
        assertInstanceOf(NoClassDefFoundError.class, node.getFailure(), "Error도 실패로 기록");
    }

    @Test
    void failureInProcessIsRecordedAndReportedOnce() throws Exception {
        TestNodes.Failing failing = new TestNodes.Failing("f1");
        List<Throwable> reported = new CopyOnWriteArrayList<>();
        failing.setFailureListener((node, error) -> { assertSame(failing, node); reported.add(error); });
        failing.receive(new Message("trigger"));

        failing.start();
        failing.getThread().join(2_000);

        assertFalse(failing.isAlive());
        assertInstanceOf(IllegalStateException.class, failing.getFailure());
        assertEquals(1, reported.size(), "실패는 한 번만 알림");
        assertSame(failing.getFailure(), reported.get(0));

        failing.setFailureListener(null);
        failing.start();
        assertNull(failing.getFailure(), "다시 시작하면 이전 실패 기록이 지워짐");
        failing.stop();
        failing.getThread().join(2_000);
    }

    @Test
    void startReturnsOnlyAfterOnStartHasRun() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean ready = new java.util.concurrent.atomic.AtomicBoolean();
        BaseNode slowStarter = new BaseNode(NodeType.INJECT) {
            @Override protected void onStart() { try { Thread.sleep(200); } catch (InterruptedException ignored) { } ready.set(true); }
            @Override protected void process() throws Exception { Thread.sleep(Long.MAX_VALUE); }
        };
        slowStarter.start();
        assertTrue(ready.get(), "start()는 onStart()가 끝난 뒤 돌아와야 함");
        slowStarter.stop();
        slowStarter.getThread().join(2_000);

        BaseNode failing = new BaseNode(NodeType.INJECT) {
            @Override protected void onStart() { throw new IllegalStateException("준비 실패"); }
            @Override protected void process() { }
        };
        failing.start();
        failing.getThread().join(2_000);
        assertNotNull(failing.getFailure(), "onStart 실패도 노드 실패로 기록되고 start()는 멈추지 않음");
    }

    @Test
    void normalStopIsNotAFailure() throws Exception {
        TestNodes.Sink sink = new TestNodes.Sink("d1");
        List<Throwable> reported = new CopyOnWriteArrayList<>();
        sink.setFailureListener((node, error) -> reported.add(error));

        sink.start();
        Thread.sleep(50);
        sink.stop();
        sink.getThread().join(2_000);

        assertNull(sink.getFailure());
        assertTrue(reported.isEmpty());
    }

    @Test
    void disconnectRemovesOnlyThatLinkAndReportsWhetherItExisted() {
        TestNodes.Source source = new TestNodes.Source("s1");
        TestNodes.Sink first = new TestNodes.Sink("d1");
        TestNodes.Sink second = new TestNodes.Sink("d2");
        source.connect(first);
        source.connect(second);

        assertTrue(source.disconnect(first));
        assertFalse(source.disconnect(first), "이미 끊긴 연결은 false");
        assertEquals(1, source.getTargets().size());
        assertTrue(source.getTargets().contains(second));

        assertTrue(source.disconnectAll());
        assertTrue(source.getTargets().isEmpty());
        assertFalse(source.disconnectAll(), "끊을 것이 없으면 false");
        assertFalse(first.disconnectAll(), "입력 전용 노드는 나가는 연결이 없음");
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) fail("시간 안에 조건을 만족하지 못함");
            Thread.sleep(5);
        }
    }
}
