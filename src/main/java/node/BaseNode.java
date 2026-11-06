package node;

import annotation.OptionField;
import com.google.gson.annotations.Expose;
import common.enums.FieldType;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import util.HashIdGenerator;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
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
    private final Thread thread;
    @Expose
    @OptionField(type = FieldType.TEXT)
    private long intervalMilli = DEFAULT_INTERVAL_MILLI;
    @Expose
    private int x; // board panel을 기준으로 한 x, y 값.
    @Expose
    private int y;
    private boolean isRunning;

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

        this.thread = new Thread(this);
        this.in = this instanceof Inputable ? new Pipe() : null;
        this.out = this instanceof Outputable ? new HashSet<>() : null;
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
        isRunning = true;
        log.info("pre-process 함수가 정상적으로 실행되었습니다.");
    }

    protected abstract void process() throws Exception;

    public abstract Icon getIcon();

    public abstract String getDisplayName();

    public abstract String getDescription();

    protected void postprocess() {
        isRunning = false;
        log.info("post-process 함수가 정상적으로 실행되었습니다.");
    }

    @Override
    public void run() {
        preprocess();

        while (isRunning && !Thread.currentThread().isInterrupted()) {

            try {
                process();
                Thread.sleep(intervalMilli);
            } catch (Exception e) {
                log.error(e.getMessage());
                Thread.currentThread().interrupt();
            }
        }

        postprocess();
    }

    public void start() {
        thread.start();
    }

    public void stop() {
        isRunning = false;
    }
}
