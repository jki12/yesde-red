package node;

import node.functional.DebugNode;
import node.functional.DedupeNode;
import node.functional.FileWriteNode;
import node.functional.HtmlExtractNode;
import node.functional.HttpRequestNode;
import node.functional.InjectNode;
import node.functional.JsonExtractNode;

import java.util.function.Function;

/** 노드 종류와 그 표시명·설명·아이콘·생성자입니다. 새 노드는 여기에 한 줄 추가하면 팝업·팩토리·저장에 모두 나타납니다. */
public enum NodeType {
    INJECT("메시지 주입", "설정한 메시지 또는 현재 시각을 연결된 노드에 전달합니다.", "arrow-right", InjectNode::new),
    DEBUG("디버그", "수신한 메시지를 하단 콘솔에 표시합니다.", "bug", DebugNode::new),
    HTTP_REQUEST("HTTP 요청", "메시지를 받을 때마다 URL을 GET으로 호출하고 응답 본문·상태·Content-Type을 payload에 더합니다.", "globe", HttpRequestNode::new),
    HTML_EXTRACT("HTML 추출", "응답 본문에서 CSS 선택자로 항목과 필드를 뽑아 항목마다 메시지를 보냅니다.", "search", HtmlExtractNode::new),
    JSON_EXTRACT("JSON 추출", "응답 본문(JSON)에서 경로로 항목과 필드를 뽑아 항목마다 메시지를 보냅니다.", "list-tree", JsonExtractNode::new),
    FILE_WRITE("파일 저장", "받은 메시지를 CSV 또는 JSON Lines 파일에 한 줄씩 기록합니다.", "new-file", FileWriteNode::new),
    DEDUPE("중복 제거", "기준 필드의 값이 처음 보이는 메시지만 통과시킵니다. 수집마다 반복되는 글을 걸러 새 글만 남깁니다.", "clear-all", DedupeNode::new);

    private final String displayName;
    private final String description;
    private final String iconName;
    private final Function<String, BaseNode> factory;

    /** factory는 ID를 받습니다. null이면 새 ID를 발급합니다. */
    NodeType(String displayName, String description, String iconName, Function<String, BaseNode> factory) {
        this.displayName = displayName;
        this.description = description;
        this.iconName = iconName;
        this.factory = factory;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    /** codicons 아이콘 파일 이름(확장자 없음)입니다. */
    public String getIconName() { return iconName; }

    public BaseNode create(String id) {
        return factory.apply(id);
    }
}
