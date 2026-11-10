package node.functional;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.Getter;
import lombok.Setter;
import node.BaseNode;
import node.Message;
import node.NodeType;
import node.Outputable;
import node.option.OptionField;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 워크플로가 실행되는 동안 127.0.0.1에 HTTP 서버를 띄우는 노드의 공통 부분입니다. 외부에서는 접근할 수 없습니다.
 * 하위 클래스는 {@link #serve}에서 요청에 응답하고 보낸 상태 코드를 돌려줍니다. 요청마다 method·path·query·status 메시지를 내보냅니다.
 */
@Getter
@Setter
public abstract class LocalHttpServerNode extends BaseNode implements Outputable {
    private static final int EVENT_QUEUE_CAPACITY = 1_000;

    @OptionField(min = 0, label = "포트 (0이면 비어 있는 포트 자동)")
    private int port;

    private HttpServer server;
    private ExecutorService executor;
    // 들어온 요청을 메시지로 내보내기 위한 큐입니다. 받는 노드가 없어도 메모리가 늘지 않도록 상한을 둡니다.
    private final BlockingQueue<Message> events = new ArrayBlockingQueue<>(EVENT_QUEUE_CAPACITY);

    protected LocalHttpServerNode(NodeType type, String id, int defaultPort) {
        super(type, id);
        this.port = defaultPort;
    }

    /** 서버가 실제로 연 포트입니다. 옵션이 0이면 자동으로 고른 포트를 돌려줍니다. 서버가 없으면 -1입니다. */
    public int getBoundPort() {
        return server == null ? -1 : server.getAddress().getPort();
    }

    /** 서버를 엽니다. 하위 클래스는 준비(폴더 확인, DB 연결 등)를 마친 뒤 이 메서드를 부릅니다. */
    @Override
    protected void onStart() {
        super.onStart();
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        } catch (BindException e) {
            throw new IllegalStateException("포트 " + port + "을(를) 열 수 없습니다. 이미 사용 중인지 확인하세요.", e);
        } catch (IOException e) {
            throw new IllegalStateException(getDisplayName() + "를 시작할 수 없습니다: " + e.getMessage(), e);
        }
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    @Override
    protected void process() throws Exception {
        send(events.take()); // 요청이 올 때까지 기다립니다. stop()의 인터럽트로 깨어나 종료됩니다.
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (server != null) server.stop(0);
        if (executor != null) executor.shutdownNow();
        server = null;
        executor = null;
    }

    /** 요청 하나에 응답하고 보낸 상태 코드를 돌려줍니다. 서버 실행기의 스레드에서 동시에 여러 번 호출될 수 있습니다. */
    protected abstract int serve(HttpExchange exchange) throws IOException;

    private void handle(HttpExchange exchange) throws IOException {
        int status;
        try (exchange) {
            status = serve(exchange);
        }
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("method", exchange.getRequestMethod());
        event.put("path", exchange.getRequestURI().getPath());
        event.put("query", exchange.getRequestURI().getQuery());
        event.put("status", status);
        if (!events.offer(new Message(event))) warn("요청 이벤트 큐가 가득 차 이벤트를 버립니다. 출력 포트에 받는 노드를 연결하세요.");
    }

    protected static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        respond(exchange, status, contentType + "; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    protected static void respond(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
        if (status == 204 || status == 304) { // 본문이 없어야 하는 상태입니다. 본문을 실으면 HttpServer가 500으로 바꿉니다.
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
