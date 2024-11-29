package node.http;

import annotation.OptionField;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import common.exception.HttpServerException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import node.*;
import util.Utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class HttpServerNode extends InputOutputNode implements Outputable {
    private static final long DEFAULT_INTERVAL = 10_000;
    private static final int DEFAULT_PORT = 8080;

    private final AtomicInteger counter = new AtomicInteger();
    private final HttpServer server;
    @Setter
    @OptionField
    private int port = DEFAULT_PORT;

    public HttpServerNode() {
        super(Type.HTTP_SERVER);
        setIntervalMilli(DEFAULT_INTERVAL);

        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            log.info(e.getMessage());
            throw new HttpServerException(e.getMessage());
        }
    }

    @Override
    protected void preprocess() {
        super.preprocess();
        String json = Utils.GSON.toJson("message: game over man");
        register("/there_is_no_cow_level", 200, json);

        server.start();
    }

    @Override
    protected void process() throws Exception {
        String msg = String.format("현재까지 처리된 요청 수 : %3d", counter.get());

        for (BaseNode node : getOut()) {
            ((InputOutputNode) node).getIn().push(new Message(msg));
        }
    }

    public void register(String path, int statusCode, byte[] payload) {
        server.createContext(path, (HttpExchange exchange) -> {
            exchange.sendResponseHeaders(statusCode, payload.length);
            exchange.getResponseBody().write(payload);

            counter.incrementAndGet();
        });
    }

    public void register(String path, int statusCode, String payload) {
        register(path, statusCode, payload.getBytes(StandardCharsets.UTF_8));
    }
}
