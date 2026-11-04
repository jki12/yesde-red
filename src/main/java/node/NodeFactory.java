package node;

import lombok.extern.slf4j.Slf4j;
import javax.swing.Icon;
import node.functional.DebugNode;
import node.functional.InjectNode;
import node.functional.KeywordFilterNode;
import node.functional.MarketIndexFetchNode;
import node.functional.RssReadNode;
import node.functional.TcpServerNode;
import node.http.HttpServerNode;
import node.http.HttpTestNode;
import node.webhook.WebhookNode;

@Slf4j
public class NodeFactory {

    public static String getDisplayName(Type type) {
        return switch (type) {
            case INJECT -> InjectNode.DISPLAY_NAME;
            case DEBUG -> DebugNode.DISPLAY_NAME;
            case TCP_SERVER -> TcpServerNode.DISPLAY_NAME;
            case MARKET_INDEX_FETCH -> MarketIndexFetchNode.DISPLAY_NAME;
            case HTTP_SERVER -> HttpServerNode.DISPLAY_NAME;
            case HTTP_TESTER -> HttpTestNode.DISPLAY_NAME;
            case KEYWORD_FILTER -> KeywordFilterNode.DISPLAY_NAME;
            case RSS_READ -> RssReadNode.DISPLAY_NAME;
            case WEBHOOK -> WebhookNode.DISPLAY_NAME;
        };
    }

    public static Icon getIcon(Type type) {
        return switch (type) {
            case INJECT -> InjectNode.ICON;
            case DEBUG -> DebugNode.ICON;
            case TCP_SERVER -> TcpServerNode.ICON;
            case MARKET_INDEX_FETCH -> MarketIndexFetchNode.ICON;
            case HTTP_SERVER -> HttpServerNode.ICON;
            case HTTP_TESTER -> HttpTestNode.ICON;
            case KEYWORD_FILTER -> KeywordFilterNode.ICON;
            case RSS_READ -> RssReadNode.ICON;
            case WEBHOOK -> WebhookNode.ICON;
        };
    }

    public static BaseNode createNode(String t) {
        Type type = Type.valueOf(t.toUpperCase());  // type은 항상 올바른 값이 들어온다고 가정한다.

        BaseNode node;

        if (type == Type.DEBUG) node = new DebugNode();
        else if (type == Type.INJECT) node = new InjectNode();
        else if (type == Type.TCP_SERVER) node = new TcpServerNode();
        else if (type == Type.MARKET_INDEX_FETCH) node = new MarketIndexFetchNode();
        else if (type == Type.HTTP_SERVER) node = new HttpServerNode();
        else if (type == Type.KEYWORD_FILTER) node = new KeywordFilterNode();
        else if (type == Type.RSS_READ) node = new RssReadNode();
        else if (type == Type.WEBHOOK) node = new WebhookNode();
        else {
            log.error("node type : {}", type);
            throw new IllegalStateException("지원하지 않는 노드 입니다.");
        }

        log.info("{}가 생성 되었습니다", node.getClass().getSimpleName());

        return node;
    }
}
