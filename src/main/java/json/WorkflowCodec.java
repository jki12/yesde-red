package json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import node.BaseNode;
import node.Inputable;
import node.NodeFactory;
import node.NodeType;
import node.Outputable;
import node.option.NodeOptions;

import java.awt.Point;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 노드 모델 ↔ {@link WorkflowDocument} ↔ JSON 문자열 변환입니다. 화면(카드)은 다루지 않습니다. */
public final class WorkflowCodec {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

    private WorkflowCodec() { }

    /** 노드들의 위치·옵션과, 양 끝이 모두 nodes 안에 있는 연결만 문서로 만듭니다. */
    public static WorkflowDocument toDocument(String name, Collection<? extends BaseNode> nodes) {
        List<WorkflowDocument.Node> nodeDocuments = new ArrayList<>();
        List<WorkflowDocument.Connection> connections = new ArrayList<>();
        for (BaseNode node : nodes) {
            nodeDocuments.add(new WorkflowDocument.Node(node.getId(), node.getType().name(), node.getX(), node.getY(),
                    NodeOptions.read(node)));
            for (BaseNode target : node.getTargets()) {
                if (nodes.contains(target)) connections.add(new WorkflowDocument.Connection(node.getId(), target.getId()));
            }
        }
        return new WorkflowDocument(WorkflowDocument.CURRENT_VERSION, name, nodeDocuments, connections);
    }

    /**
     * 문서로부터 노드를 만들고 옵션을 쓰고 연결합니다. 문서가 잘못됐으면(모르는 타입, 중복 ID, 없는 노드로의 연결 등)
     * 사용자에게 보여줄 메시지를 담은 IllegalArgumentException을 던집니다.
     */
    public static List<BaseNode> toNodes(WorkflowDocument document) {
        if (document.nodes() == null) throw new IllegalArgumentException("워크플로에 nodes 항목이 없습니다.");

        Map<String, BaseNode> byId = new LinkedHashMap<>();
        for (WorkflowDocument.Node entry : document.nodes()) {
            if (entry.id() == null || entry.id().isBlank()) throw new IllegalArgumentException("ID가 없는 노드가 있습니다.");
            if (byId.containsKey(entry.id())) throw new IllegalArgumentException("노드 ID가 중복됩니다: " + entry.id());
            BaseNode node = NodeFactory.createNode(parseType(entry), entry.id());
            node.setLocation(new Point(entry.x(), entry.y()));
            if (entry.options() != null) NodeOptions.write(node, entry.options());
            byId.put(entry.id(), node);
        }

        for (WorkflowDocument.Connection connection : document.connections() == null ? List.<WorkflowDocument.Connection>of() : document.connections()) {
            BaseNode from = byId.get(connection.from());
            BaseNode to = byId.get(connection.to());
            if (from == null || to == null) throw new IllegalArgumentException("연결이 없는 노드를 가리킵니다: " + connection.from() + " → " + connection.to());
            if (!(from instanceof Outputable source) || !(to instanceof Inputable target)) {
                throw new IllegalArgumentException("연결 방향이 맞지 않습니다: " + connection.from() + " → " + connection.to());
            }
            source.connect(target);
        }
        return new ArrayList<>(byId.values());
    }

    public static String toJson(WorkflowDocument document) {
        return GSON.toJson(document);
    }

    public static WorkflowDocument fromJson(String json) {
        WorkflowDocument document;
        try {
            document = GSON.fromJson(json, WorkflowDocument.class);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("워크플로 파일 형식이 아닙니다.", e);
        }
        if (document == null) throw new IllegalArgumentException("비어 있는 파일입니다.");
        if (document.version() > WorkflowDocument.CURRENT_VERSION) {
            throw new IllegalArgumentException("이 프로그램보다 새 버전(" + document.version() + ")의 워크플로 파일입니다.");
        }
        return document;
    }

    private static NodeType parseType(WorkflowDocument.Node entry) {
        try {
            return NodeType.valueOf(entry.type());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("알 수 없는 노드 타입입니다: " + entry.type() + " (노드 " + entry.id() + ")");
        }
    }
}
