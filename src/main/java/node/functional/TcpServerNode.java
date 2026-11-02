package node.functional;

import ui.Codicons;
import javax.swing.Icon;
import lombok.extern.slf4j.Slf4j;
import node.BaseNode;
import node.InputOutputNode;
import node.Message;
import node.Outputable;
import node.Type;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * wireshark의 로그를 통해 얻은 패킷을 통해 해당 서버에 부하를 걸어보는 프로그램 만들어볼 예정.
 */
@Slf4j
public class TcpServerNode extends InputOutputNode implements Outputable {
    public static final String DISPLAY_NAME = "TCP 수신";
    public static final String DESCRIPTION = "TCP 연결에서 한 줄씩 읽은 문자열을 연결된 노드에 전달합니다.";
    public static final Icon ICON = Codicons.icon("server", 18);

    private final Set<Socket> sockets = new HashSet<>();
    private ServerSocket serverSocket;

    public TcpServerNode() {
        super(Type.TCP_SERVER);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    protected void preprocess() {
        try {
            serverSocket = new ServerSocket(3000);
            super.preprocess();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    @Override
    protected void process() throws Exception {
        Socket client = serverSocket.accept();

        sockets.add(client);
        Thread t = new Thread(new Runnable() {
            final BufferedReader br = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));

            @Override
            public void run() {
                try (client; br) {
                    String line;

                    while (!Thread.currentThread().isInterrupted() && (line = br.readLine()) != null) {
                        for (BaseNode node : getOut()) {
                            ((InputOutputNode) node).getIn().push(new Message(line));
                        }
                    }
                } catch (IOException e) {
                    log.error(e.getMessage());
                }
            }
        });

        t.start();
    }
}
