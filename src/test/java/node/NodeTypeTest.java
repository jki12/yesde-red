package node;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NodeTypeTest {

    @Test
    void everyTypeHasMetadataAndCanBeCreated() {
        for (NodeType type : NodeType.values()) {
            assertFalse(type.getDisplayName().isBlank(), type + " 표시명");
            assertFalse(type.getDescription().isBlank(), type + " 설명");
            assertNotNull(NodeFactory.getIcon(type), type + " 아이콘"); // 아이콘 파일이 없으면 Codicons가 예외를 던집니다.

            BaseNode fresh = NodeFactory.createNode(type);
            BaseNode fixed = NodeFactory.createNode(type, "id" + type.ordinal());
            assertEquals(type, fresh.getType());
            assertEquals("id" + type.ordinal(), fixed.getId());
            assertEquals(type.getDisplayName(), fresh.getDisplayName(), "표시 정보는 타입에서 옴");
            assertSame(NodeFactory.getIcon(type), fresh.getIcon(), "아이콘은 타입별로 하나만 만듦");
        }
    }
}
