package workflow;

import org.junit.jupiter.api.Test;
import support.Edt;
import support.TestNodes;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowRunnerTest {

    @Test
    void runsThroughIdleRunningStoppingAndBackToIdle() throws Exception {
        TestNodes.Source source = new TestNodes.Source("s1");
        TestNodes.Sink sink = new TestNodes.Sink("d1");
        source.setIntervalMilli(10);
        source.connect(sink);

        List<RunState> states = new CopyOnWriteArrayList<>();
        CountDownLatch idle = new CountDownLatch(1);
        WorkflowRunner runner = new WorkflowRunner("test", (previous, current) -> {
            states.add(current);
            if (current == RunState.IDLE) idle.countDown();
        });

        Edt.run(() -> {
            assertEquals(RunState.IDLE, runner.getState());
            runner.start(List.of(source, sink));
            assertEquals(RunState.RUNNING, runner.getState());
            assertThrows(IllegalStateException.class, () -> runner.start(List.of(source, sink)), "실행 중 재시작은 거부");
        });
        Thread.sleep(200);
        Edt.run(() -> {
            runner.stop();
            assertEquals(RunState.STOPPING, runner.getState());
        });

        assertTrue(idle.await(5, TimeUnit.SECONDS), "모든 워커가 끝나면 IDLE로 돌아와야 함");
        assertFalse(source.isExecutionActive());
        assertFalse(sink.isExecutionActive());
        assertTrue(sink.seen.get() > 0, "메시지가 실제로 전달돼야 함");
        assertEquals(List.of(RunState.RUNNING, RunState.STOPPING, RunState.IDLE), states);
    }

    @Test
    void canStartAgainAfterCompletion() throws Exception {
        TestNodes.Source source = new TestNodes.Source("s1");
        CountDownLatch idle = new CountDownLatch(1);
        WorkflowRunner runner = new WorkflowRunner("test", (previous, current) -> { if (current == RunState.IDLE) idle.countDown(); });

        Edt.run(() -> { runner.start(List.of(source)); runner.stop(); });
        assertTrue(idle.await(5, TimeUnit.SECONDS));

        Edt.run(() -> {
            assertDoesNotThrow(() -> runner.start(List.of(source)));
            runner.stop();
        });
    }

    @Test
    void emptyNodeListStaysIdle() throws Exception {
        WorkflowRunner runner = new WorkflowRunner("test", (previous, current) -> fail("상태가 바뀌면 안 됨"));
        Edt.run(() -> {
            runner.start(List.of());
            assertEquals(RunState.IDLE, runner.getState());
        });
    }

    @Test
    void rejectsCallsOffTheEventDispatchThread() {
        WorkflowRunner runner = new WorkflowRunner("test", (previous, current) -> { });
        assertThrows(IllegalStateException.class, () -> runner.start(List.of()));
        assertThrows(IllegalStateException.class, runner::stop);
    }
}
