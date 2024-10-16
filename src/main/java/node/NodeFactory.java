package node;

import lombok.extern.slf4j.Slf4j;
import node.functional.DebugNode;
import node.functional.InjectNode;
import node.functional.MarketIndexFetchNode;
import node.functional.TcpServerNode;

@Slf4j
public class NodeFactory {

    public static BaseNode createNode(String t) {
        Type type = Type.valueOf(t.toUpperCase());  // type은 항상 올바른 값이 들어온다고 가정한다.

        BaseNode node;

        if (type == Type.DEBUG) node = new DebugNode();
        else if (type == Type.INJECT) node = new InjectNode();
        else if (type == Type.TCP_SERVER) node = new TcpServerNode();
        else if (type == Type.MARKET_INDEX_FETCH) node = new MarketIndexFetchNode();
        else {
            log.error("node type : {}", type);
            throw new IllegalStateException("지원하지 않는 노드 입니다.");
        }

        log.info("{}가 생성 되었습니다", node.getClass().getSimpleName());

        return node;
    }
}
