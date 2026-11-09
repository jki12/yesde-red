package node;

import annotation.OptionField;
import com.google.gson.annotations.Expose;
import common.enums.FieldType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import util.HashIdGenerator;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

@Getter
@Setter
@Slf4j
public abstract class BaseNode implements Runnable {
    private static final long DEFAULT_INTERVAL_MILLI = 1_000;
    private static final Supplier<String> ID_GENERATOR = new HashIdGenerator();

    private final LocalDateTime createdAt;
    @Expose
    private final String id;
    @Expose(deserialize = false)
    private final Type type;
    @Setter(AccessLevel.NONE)
    private volatile Thread thread;
    @Expose
    @OptionField(type = FieldType.TEXT)
    private long intervalMilli = DEFAULT_INTERVAL_MILLI;
    @Expose
    private int x; // board panel을 기준으로 한 x, y 값.
    @Expose
    private int y;
    @Setter(AccessLevel.NONE)
    private volatile boolean isRunning;

    // 입출력 역할에 필요한 큐와 연결 목록만 생성하며, 참조는 생성 후 교체하지 않습니다.
    private final Pipe in;
    private final Set<BaseNode> out;

    public BaseNode(Type type) {
        this(type, ID_GENERATOR.get().substring(0, 6));
    }

    protected BaseNode(Type type, String id) {
        this.createdAt = LocalDateTime.now();
        this.id = id;
        this.type = type;

        this.in = this instanceof Inputable ? new Pipe() : null;
        this.out = this instanceof Outputable ? new HashSet<>() : null;
    }

    public Set<BaseNode> getOut() {
        // 외부에서는 연결 목록을 조회만 허용합니다. 복사본이 아니므로 이후 연결 변경도 반영됩니다.
        // 출력 기능이 없는 노드는 기존처럼 null을 반환합니다.
        return out == null ? null : Collections.unmodifiableSet(out);
    }

    public boolean connect(Inputable input) {
        if (!(input instanceof BaseNode other)) return false;
        if (!canConnect(other) || out.contains(other)) return false;

        out.add(other);
        log.info("{}노드와 {}노드가 연결되었습니다.", this, other);

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

        for (BaseNode target : out) {
            target.receive(message);
        }
    }

    // 받은 메시지는 입력 큐에 넣고, 실제 처리는 노드 작업 스레드에 맡깁니다.
    public void receive(Message message) {
        if (!(this instanceof Inputable)) {
            throw new IllegalStateException("입력 기능이 없는 노드입니다.");
        }

        in.push(message);
    }

    public void setPoint(Point p) {
        x = p.x;
        y = p.y;
    }

    protected void preprocess() {
        log.info("pre-process 함수가 정상적으로 실행되었습니다.");
    }

    protected abstract void process() throws Exception;

    public abstract Icon getIcon();

    public abstract String getDisplayName();

    public abstract String getDescription();

    protected void postprocess() {
        log.info("post-process 함수가 정상적으로 실행되었습니다.");
    }

    @Override
    public void run() {
        try {
            // start() 직후 stop()이 호출돼도 종료 요청을 다시 덮어쓰지 않습니다.
            if (!isRunning || Thread.currentThread().isInterrupted()) return;
            preprocess();
            while (isRunning && !Thread.currentThread().isInterrupted()) {
                process();
                if (!isRunning) break;
                Thread.sleep(intervalMilli);
            }
        } catch (InterruptedException e) {
            // 큐 대기나 sleep 중의 인터럽트는 정상적인 종료 요청입니다.
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("노드 처리 중 오류가 발생했습니다. id={}", id, e);
        } finally {
            try {
                postprocess();
            } catch (Exception e) {
                log.error("노드 종료 정리 중 오류가 발생했습니다. id={}", id, e);
            } finally {
                isRunning = false;
            }
        }
    }

    public synchronized void start() {
        if (isExecutionActive()) throw new IllegalStateException("아직 종료되지 않은 노드입니다.");

        // 큐는 보드가 모든 노드의 시작 전에 한꺼번에 비웁니다.
        // 여기에서 다시 비우면 먼저 실행된 송신 노드의 새 메시지가 사라질 수 있습니다.
        Thread next = new Thread(this, "node-" + id);
        thread = next;
        isRunning = true;
        try {
            next.start();
        } catch (RuntimeException | Error e) {
            isRunning = false;
            throw e;
        }
    }

    public synchronized void stop() {
        isRunning = false;
        if (thread != null) thread.interrupt();
    }

    // 종료 플래그가 내려가도 postprocess() 중일 수 있으므로 실제 스레드 생존 여부를 확인합니다.
    public synchronized boolean isExecutionActive() {
        return thread != null && thread.isAlive();
    }

    public synchronized void clearPendingMessages() {
        if (isExecutionActive()) throw new IllegalStateException("실행 중인 노드의 큐는 비울 수 없습니다.");
        if (in != null) in.clear();
    }
}
