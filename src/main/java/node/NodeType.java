package node;

import node.functional.ApiServerNode;
import node.functional.DbWriteNode;
import node.functional.DebugNode;
import node.functional.DedupeNode;
import node.functional.FileWriteNode;
import node.functional.FilterNode;
import node.functional.HtmlExtractNode;
import node.functional.HttpRequestNode;
import node.functional.InjectNode;
import node.functional.JsonExtractNode;
import node.functional.MockServerNode;
import node.functional.TransformNode;

import java.util.function.Function;

/** 노드 종류와 그 표시명·설명·아이콘·생성자입니다. 새 노드는 여기에 한 줄 추가하면 팝업·팩토리·저장에 모두 나타납니다. */
public enum NodeType {
    INJECT("메시지 주입", "설정한 메시지 또는 현재 시각을 연결된 노드에 전달합니다.", "arrow-right", InjectNode::new),
    DEBUG("디버그", "수신한 메시지를 하단 콘솔에 표시합니다.", "bug", DebugNode::new),
    HTTP_REQUEST("HTTP 요청", "메시지를 받을 때마다 URL을 GET으로 호출하고 응답 본문·상태·Content-Type을 payload에 더합니다.", "globe", HttpRequestNode::new),
    HTML_EXTRACT("HTML 추출", "응답 본문(HTML 또는 XML/RSS)에서 CSS 선택자로 항목과 필드를 뽑아 항목마다 메시지를 보냅니다.", "search", HtmlExtractNode::new),
    JSON_EXTRACT("JSON 추출", "응답 본문(JSON)에서 경로로 항목과 필드를 뽑아 항목마다 메시지를 보냅니다.", "list-tree", JsonExtractNode::new),
    FILE_WRITE("파일 저장", "받은 메시지를 CSV 또는 JSON Lines 파일에 한 줄씩 기록합니다.", "new-file", FileWriteNode::new),
    FILTER("조건 필터", "조건식에 맞는 메시지만 통과시킵니다. 예: price < 20000 and title contains \"키보드\"", "filter", FilterNode::new),
    DEDUPE("중복 제거", "기준 필드의 값이 처음 보이는 메시지만 통과시킵니다. 수집마다 반복되는 글을 걸러 새 글만 남깁니다.", "clear-all", DedupeNode::new),
    TRANSFORM("필드 변환", "규칙(대상 = 식)으로 필드를 뽑고 바꾸고 만듭니다. 출처마다 다른 데이터를 공통 스키마로 맞춥니다.", "graph-line", TransformNode::new),
    DB_WRITE("DB 저장", "받은 메시지를 PostgreSQL 테이블에 upsert합니다. 키가 같은 행이 있으면 갱신하고 없으면 넣습니다.", "database", DbWriteNode::new),
    API_SERVER("API 서버", "실행 중 127.0.0.1에 REST API를 띄워 DB의 핫딜(deals)을 JSON으로 내줍니다. GET /deals?source=&since=&q=&limit=&offset=", "radio-tower", ApiServerNode::new),
    MOCK_SERVER("Mock 서버", "실행 중 127.0.0.1에 HTTP 서버를 띄워 폴더의 파일을 응답으로 내줍니다. 실제 사이트 대신 고정 입력으로 파이프라인을 시험합니다.", "server", MockServerNode::new);

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
