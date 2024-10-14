package node;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import node.functional.DebugNode;
import node.functional.InjectNode;
import node.functional.MarketIndexFetchNode;
import node.functional.TcpServerNode;
import util.HashIdGenerator;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Supplier;

@Getter
@Setter
@Slf4j
public abstract class BaseNode implements Runnable {
    private static final long DEFAULT_INTERVAL_MILLI = 5_000;
    public static final Map<Type, Class<?>> CLASS_MAP = Map.of(Type.DEBUG, DebugNode.class, Type.INJECT, InjectNode.class, Type.TCP_SERVER, TcpServerNode.class, Type.MARKET_INDEX_FETCH, MarketIndexFetchNode.class);

    private static final Supplier<String> ID_GENERATOR = new HashIdGenerator();

    private final LocalDateTime createdAt;
    private final String id;
    private final Type type;
    private final Thread thread;
    private final long intervalMilli = DEFAULT_INTERVAL_MILLI;

    protected BaseNode(Type type) {
        this.createdAt = LocalDateTime.now();
        this.id = ID_GENERATOR.get();
        this.type = type;

        this.thread = new Thread(this);
    }

    protected void preprocess() {
        log.info("pre-process 함수가 정상적으로 실행되었습니다.");
    }

    protected abstract void process() throws Exception;

    protected void postprocess() {
        log.info("post-process 함수가 정상적으로 실행되었습니다.");
    }

    @Override
    public void run() {
        preprocess();

        while (!Thread.currentThread().isInterrupted()) {

            try {
                process();
                Thread.sleep(intervalMilli);
            } catch (InterruptedException e) {
                log.info(e.getMessage());
                Thread.currentThread().interrupt();
            } catch (Exception e1) {
            }
        }

        postprocess();
    }

    public void start() {
        thread.start();
    }

    public void stop() {
    }
}
