package node;

import lombok.extern.slf4j.Slf4j;
import javax.swing.Icon;
import node.functional.DebugNode;
import node.functional.InjectNode;

import java.util.Arrays;

@Slf4j
public class NodeFactory {

    public static String[] getTypeNames() {
        return Arrays.stream(NodeType.values())
                .map(Enum::toString)
                .toArray(String[]::new);
    }

    public static String getDisplayName(NodeType type) {
        return switch (type) {
            case INJECT -> InjectNode.DISPLAY_NAME;
            case DEBUG -> DebugNode.DISPLAY_NAME;
        };
    }

    public static Icon getIcon(NodeType type) {
        return switch (type) {
            case INJECT -> InjectNode.ICON;
            case DEBUG -> DebugNode.ICON;
        };
    }

    public static BaseNode createNode(String t) {
        NodeType type = NodeType.valueOf(t.toUpperCase());  // type은 항상 올바른 값이 들어온다고 가정한다.

        BaseNode node;

        if (type == NodeType.DEBUG) node = new DebugNode();
        else if (type == NodeType.INJECT) node = new InjectNode();
        else {
            log.error("node type : {}", type);
            throw new IllegalStateException("지원하지 않는 노드 입니다.");
        }

        log.info("{}가 생성 되었습니다", node.getClass().getSimpleName());

        return node;
    }
}
