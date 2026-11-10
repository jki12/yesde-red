package node;

import lombok.extern.slf4j.Slf4j;
import ui.theme.Codicons;

import javax.swing.Icon;
import java.util.EnumMap;
import java.util.Map;

/** {@link NodeType}의 메타데이터로 노드와 아이콘을 만듭니다. */
@Slf4j
public class NodeFactory {
    private static final int ICON_SIZE = 18;
    private static final Map<NodeType, Icon> ICONS = new EnumMap<>(NodeType.class);

    /** 타입별 아이콘은 한 번만 만들어 재사용합니다. */
    public static synchronized Icon getIcon(NodeType type) {
        return ICONS.computeIfAbsent(type, t -> Codicons.icon(t.getIconName(), ICON_SIZE));
    }

    public static BaseNode createNode(NodeType type) {
        return createNode(type, null);
    }

    /** id가 null이면 새 ID를 받습니다. 불러오기처럼 ID가 정해진 경우 그 ID로 만듭니다. */
    public static BaseNode createNode(NodeType type, String id) {
        BaseNode node = type.create(id);
        log.info("{}가 생성 되었습니다", node.getClass().getSimpleName());
        return node;
    }
}
