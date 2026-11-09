package node;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import util.RandomIdGenerator;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Getter
@Setter
@Slf4j
public abstract class BaseNode implements Runnable {
    /** 노드가 예외로 멈췄을 때 워커 스레드에서 호출됩니다. */
    public interface FailureListener {
        void nodeFailed(BaseNode node, Throwable error);
    }

    private static final RandomIdGenerator ID_GENERATOR = new RandomIdGenerator();

    private final LocalDateTime createdAt;
    private final String id;
    private final NodeType type;
    @Setter(AccessLevel.NONE)
    private volatile Thread thread;
    private int x; // board panel을 기준으로 한 x, y 값.
    private int y;
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private volatile boolean keepRunning; // stop()이 false로 내리면 루프가 끝납니다. 스레드 생존 여부는 isAlive().
    // 마지막 실행이 예외로 끝났으면 그 예외입니다. start()에서 지웁니다. 종료 요청 뒤의 예외는 실패로 보지 않습니다.
    @Setter(AccessLevel.NONE)
    private volatile Throwable failure;
    @Getter(AccessLevel.NONE)
    private volatile FailureListener failureListener;

    // 입출력 역할에 필요한 큐와 연결 목록만 생성하며, 참조는 생성 후 교체하지 않습니다.
    private final Pipe inbox;
    // 연결 변경은 드물고(EDT에서 사용자 조작) 순회는 메시지마다 워커 스레드에서 일어나므로,
    // 순회 중 추가돼도 예외가 나지 않는 복사-쓰기 집합을 씁니다. 연결 순서도 유지됩니다.
    private final Set<BaseNode> targets;

    public BaseNode(NodeType type) {
        this(type, null);
    }

    /** id가 null이면 새 ID를 발급하고, 아니면 그 ID를 쓰며 생성기에 예약해 이후 새 노드와 겹치지 않게 합니다. */
    protected BaseNode(NodeType type, String id) {
        if (id == null) id = ID_GENERATOR.get();
        else ID_GENERATOR.reserve(id);
        this.createdAt = LocalDateTime.now();
        this.id = id;
        this.type = type;

        this.inbox = this instanceof Inputable ? new Pipe() : null;
        this.targets = this instanceof Outputable ? new CopyOnWriteArraySet<>() : null;
    }

    public Set<BaseNode> getTargets() {
        // 외부에서는 연결 목록을 조회만 허용합니다. 복사본이 아니므로 이후 연결 변경도 반영됩니다.
        // 출력 기능이 없는 노드는 null을 반환합니다.
        return targets == null ? null : Collections.unmodifiableSet(targets);
    }

    public boolean connect(Inputable input) {
        if (!(input instanceof BaseNode other)) return false;
        if (!canConnect(other) || !targets.add(other)) return false; // 이미 연결돼 있으면 add가 false

        log.info("{}노드와 {}노드가 연결되었습니다.", this, other);

        return true;
    }

    public boolean disconnect(Inputable input) {
        if (!(input instanceof BaseNode other) || targets == null) return false;
        return targets.remove(other);
    }

    /** 나가는 연결을 모두 끊습니다. 노드를 보드에서 지울 때 씁니다. 끊은 것이 하나라도 있으면 true입니다. */
    public boolean disconnectAll() {
        if (targets == null || targets.isEmpty()) return false;
        targets.clear();
        return true;
    }

    private boolean canConnect(BaseNode other) {
        // 드래그를 시작한 방향과 관계없이 모델 연결은 출력 → 입력만 허용합니다.
        return this instanceof Outputable
                && other instanceof Inputable
                && this != other;
    }

    // 연결된 각 노드의 receive()를 호출해 메시지를 전달합니다.
    protected void send(Message message) {
        if (!(this instanceof Outputable)) {
            throw new IllegalStateException("출력 기능이 없는 노드입니다.");
        }

        for (BaseNode target : targets) {
            target.receive(message);
        }
    }

    // 받은 메시지는 입력 큐에 넣고, 실제 처리는 노드 작업 스레드에 맡깁니다.
    public void receive(Message message) {
        if (!(this instanceof Inputable)) {
            throw new IllegalStateException("입력 기능이 없는 노드입니다.");
        }

        inbox.put(message);
    }

    public void setLocation(Point p) {
        x = p.x;
        y = p.y;
    }

    protected void onStart() {
        log.info("onStart 훅이 실행되었습니다.");
    }

    protected abstract void process() throws Exception;

    /**
     * process() 한 번이 끝난 뒤 다음 처리까지 기다립니다.
     * 입력 큐를 소비하는 노드는 큐가 빌 때 process()가 블록되므로 기본 구현은 바로 다음 처리로 넘어갑니다.
     * 주기적으로 스스로 동작하는 노드는 {@link PollingNode}가 간격만큼 쉽니다.
     */
    protected void awaitNextCycle() throws InterruptedException { }

    // 표시 정보는 타입이 가집니다. 특별한 표시가 필요한 노드만 재정의합니다.
    public Icon getIcon() { return NodeFactory.getIcon(type); }

    public String getDisplayName() { return type.getDisplayName(); }

    public String getDescription() { return type.getDescription(); }

    protected void onStop() {
        log.info("onStop 훅이 실행되었습니다.");
    }

    @Override
    public void run() {
        Throwable error = null;
        try {
            // start() 직후 stop()이 호출돼도 종료 요청을 다시 덮어쓰지 않습니다.
            if (!keepRunning || Thread.currentThread().isInterrupted()) return;
            onStart();
            while (keepRunning && !Thread.currentThread().isInterrupted()) {
                process();
                if (!keepRunning) break;
                awaitNextCycle();
            }
        } catch (InterruptedException e) {
            // 큐 대기나 sleep 중의 인터럽트는 정상적인 종료 요청입니다.
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("노드 처리 중 오류가 발생했습니다. id={}", id, e);
            if (keepRunning) error = e; // 종료 요청 중에 난 예외는 정상 종료의 일부로 봅니다.
        } finally {
            try {
                onStop();
            } catch (Exception e) {
                log.error("노드 종료 정리 중 오류가 발생했습니다. id={}", id, e);
                if (error == null) error = e;
            } finally {
                keepRunning = false;
            }
            if (error != null) reportFailure(error);
        }
    }

    private void reportFailure(Throwable error) {
        failure = error;
        FailureListener listener = failureListener;
        if (listener != null) listener.nodeFailed(this, error);
    }

    public synchronized void start() {
        if (isAlive()) throw new IllegalStateException("아직 종료되지 않은 노드입니다.");

        // 큐는 보드가 모든 노드의 시작 전에 한꺼번에 비웁니다.
        // 여기에서 다시 비우면 먼저 실행된 송신 노드의 새 메시지가 사라질 수 있습니다.
        Thread next = new Thread(this, "node-" + id);
        thread = next;
        failure = null;
        keepRunning = true;
        try {
            next.start();
        } catch (RuntimeException | Error e) {
            keepRunning = false;
            throw e;
        }
    }

    public synchronized void stop() {
        keepRunning = false;
        if (thread != null) thread.interrupt();
    }

    // 종료 플래그가 내려가도 onStop() 중일 수 있으므로 실제 스레드 생존 여부를 확인합니다.
    public synchronized boolean isAlive() {
        return thread != null && thread.isAlive();
    }

    public synchronized void clearPendingMessages() {
        if (isAlive()) throw new IllegalStateException("실행 중인 노드의 큐는 비울 수 없습니다.");
        if (inbox != null) inbox.clear();
    }
}
