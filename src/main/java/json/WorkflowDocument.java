package json;

import java.util.List;
import java.util.Map;

/**
 * 파일로 저장되는 워크플로의 모양입니다. 노드 클래스 내부가 아니라 이 레코드가 저장 형식을 정의하므로,
 * 노드 구현이 바뀌어도 파일 호환성은 여기서 관리합니다.
 */
public record WorkflowDocument(int version, String name, List<Node> nodes, List<Connection> connections) {
    public static final int CURRENT_VERSION = 1;

    /** 노드 하나. options는 @OptionField 이름 → 값입니다. */
    public record Node(String id, String type, int x, int y, Map<String, Object> options) { }

    /** 출력 노드(from)에서 입력 노드(to)로 가는 연결입니다. */
    public record Connection(String from, String to) { }
}
