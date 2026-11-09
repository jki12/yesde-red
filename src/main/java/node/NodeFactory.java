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

    public static BaseNode createNode(NodeType type) {
        return createNode(type, null);
    }

    /** id가 null이면 새 ID를 받습니다. 불러오기처럼 ID가 정해진 경우 그 ID로 만듭니다. */
    public static BaseNode createNode(NodeType type, String id) {
        BaseNode node = switch (type) {
            case DEBUG -> id == null ? new DebugNode() : new DebugNode(id);
            case INJECT -> id == null ? new InjectNode() : new InjectNode(id);
        };
        log.info("{}가 생성 되었습니다", node.getClass().getSimpleName());
        return node;
    }
}
