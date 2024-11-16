package node;

import com.google.gson.annotations.Expose;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import util.HashIdGenerator;

import java.time.LocalDateTime;
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
    private long intervalMilli = DEFAULT_INTERVAL_MILLI;
    @Expose
    private int x; // board panel을 기준으로 한 x, y 값.
    @Expose
    private int y;
    private boolean isRunning;

    public BaseNode(Type type) {
        this(type, ID_GENERATOR.get());
    }

    protected BaseNode(Type type, String id) {
        this.createdAt = LocalDateTime.now();
        this.id = id;
        this.type = type;

        this.thread = new Thread(this);
    }

    protected void preprocess() {
        isRunning = true;
        log.info("pre-process 함수가 정상적으로 실행되었습니다.");
    }

    protected abstract void process() throws Exception;

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
            } catch (InterruptedException e) {
                log.info(e.getMessage());
                Thread.currentThread().interrupt();
            } catch (Exception e1) {
                log.info(e1.getMessage());
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
