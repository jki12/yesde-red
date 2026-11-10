package workflow;

import lombok.extern.slf4j.Slf4j;
import node.BaseNode;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Objects;

/**
 * 보드 하나에 속한 노드들의 실행 생명주기를 관리합니다.
 * start()/stop()은 EDT에서만 호출하며, 상태 변경 알림도 EDT에서 전달합니다.
 */
@Slf4j
public final class WorkflowRunner {
    /** 상태 변경과 노드 실패를 EDT에서 전달받습니다. */
    public interface StateListener {
        void stateChanged(RunState previous, RunState current);

        /** 실행 중 노드가 예외로 멈췄을 때 호출됩니다. 직후 워크플로 전체가 종료됩니다. */
        default void nodeFailed(BaseNode node, Throwable error) { }

        /** 노드가 메시지 하나를 건너뛰며 남긴 경고입니다. 실행은 계속됩니다. */
        default void nodeWarned(BaseNode node, String message) { }
    }

    private final String name;
    private final StateListener listener;
    private RunState state = RunState.IDLE;
    private List<BaseNode> activeNodes = List.of();

    public WorkflowRunner(String name, StateListener listener) {
        this.name = name;
        this.listener = listener;
    }

    public RunState getState() {
        return state;
    }

    public void start(List<BaseNode> nodes) {
        requireEventDispatchThread();
        if (state != RunState.IDLE) throw new IllegalStateException("실행 중이거나 종료 중인 워크플로입니다.");

        if (nodes.isEmpty()) return;
        for (BaseNode node : nodes) {
            if (node.isAlive()) throw new IllegalStateException("아직 종료되지 않은 노드가 있습니다.");
        }
        // 모든 큐를 먼저 비운 뒤 시작해야 송신·수신 노드의 시작 순서에 따라 메시지가 유실되지 않습니다.
        for (BaseNode node : nodes) node.clearPendingMessages();
        activeNodes = List.copyOf(nodes);
        setState(RunState.RUNNING);

        try {
            for (BaseNode node : activeNodes) {
                node.setFailureListener(this::onNodeFailed);
                node.setWarningListener(this::onNodeWarned);
                node.start();
            }
        } catch (RuntimeException | Error e) {
            // 일부만 시작됐어도 전체 실행 대상을 정리합니다. 오류 안내는 호출 측에서 담당합니다.
            stop();
            throw e;
        } finally {
            // 시작 도중 실패한 노드도 포함해, 기다릴 스레드를 이 시점의 참조로 고정합니다.
            watchCompletion(activeNodes, activeNodes.stream().map(BaseNode::getThread).filter(Objects::nonNull).toList());
        }
    }

    public void stop() {
        requireEventDispatchThread();
        if (state != RunState.RUNNING) return;

        setState(RunState.STOPPING);
        // 화면에서 카드가 추가·제거돼도 이번에 실행한 노드들을 끝까지 종료 대상으로 유지합니다.
        for (BaseNode node : activeNodes) node.stop();
    }

    private void watchCompletion(List<BaseNode> nodes, List<Thread> workers) {
        Thread watcher = new Thread(() -> {
            try {
                for (Thread worker : workers) worker.join(); // 이 스레드는 아무도 interrupt하지 않습니다.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return; // 살아 있는 노드를 종료 완료로 표시하지 않습니다.
            }
            // 송신 노드까지 모두 끝난 뒤 남은 메시지를 버립니다. EDT는 join()을 기다리지 않습니다.
            for (BaseNode node : nodes) node.clearPendingMessages();
            SwingUtilities.invokeLater(() -> {
                for (BaseNode node : nodes) {
                    node.setFailureListener(null);
                    node.setWarningListener(null);
                }
                activeNodes = List.of();
                setState(RunState.IDLE);
                log.info("{} 보드의 실행이 종료되었습니다.", name);
            });
        }, "workflow-completion");
        watcher.setDaemon(true);
        watcher.start();
    }

    // 워커 스레드에서 호출됩니다. 노드 하나가 죽으면 상류 노드의 메시지가 큐에 쌓이기만 하므로 전체를 종료합니다.
    private void onNodeFailed(BaseNode node, Throwable error) {
        SwingUtilities.invokeLater(() -> {
            if (state != RunState.RUNNING) return; // 종료 중에 난 실패는 사용자가 이미 멈춘 흐름의 일부입니다.
            listener.nodeFailed(node, error);
            stop();
        });
    }

    private void onNodeWarned(BaseNode node, String message) {
        SwingUtilities.invokeLater(() -> {
            if (state == RunState.RUNNING) listener.nodeWarned(node, message);
        });
    }

    private void setState(RunState next) {
        RunState previous = state;
        state = next;
        listener.stateChanged(previous, next);
    }

    private static void requireEventDispatchThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("워크플로 실행 상태는 EDT에서 변경해야 합니다.");
        }
    }
}
