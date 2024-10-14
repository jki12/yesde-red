package node.functional;

import lombok.extern.slf4j.Slf4j;
import node.BaseNode;
import node.Type;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;

/**
 * wireshark의 로그를 통해 얻은 패킷을 통해 해당 서버에 부하를 걸어보는 프로그램 만들어볼 예정.
 */
@Slf4j
public class TcpServerNode extends BaseNode {
    private final Set<Socket> sockets = new HashSet<>();
    private ServerSocket serverSocket;

    public TcpServerNode() {
        super(Type.TCP_SERVER);
    }

    @Override
    protected void preprocess() {
        try {
            serverSocket = new ServerSocket(3000);
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    @Override
    protected void process() throws Exception {
        Socket client = serverSocket.accept();

        sockets.add(client);
        Thread t = new Thread(new Runnable() { // TODO, try-catch-resource.
            BufferedReader br = new BufferedReader(new InputStreamReader(client.getInputStream()));

            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        System.out.println(br.readLine());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }


            }
        });

        t.start();
    }
}
