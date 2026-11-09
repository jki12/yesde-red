package node.functional;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import lombok.Getter;
import lombok.Setter;
import node.BaseNode;
import node.Inputable;
import node.Message;
import node.NodeType;
import node.Outputable;
import node.option.OptionField;
import node.option.OptionInputType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * payload의 body(JSON)에서 경로로 값을 뽑습니다. 항목 경로가 배열을 가리키면([*]) 원소마다 메시지 하나를 내보냅니다.
 * 필드는 "이름 = 경로" 줄로 정의합니다. 예) name = title / price = price.amount / first = tags[0]
 */
@Getter
@Setter
public class JsonExtractNode extends BaseNode implements Inputable, Outputable {
    @OptionField(label = "항목 경로 (예: data.items[*], 비우면 전체가 한 항목)")
    private String items;
    @OptionField(type = OptionInputType.TEXT_AREA, label = "필드 (줄마다 이름 = 경로)")
    private String fields;

    public JsonExtractNode(String id) {
        super(NodeType.JSON_EXTRACT, id);
    }

    @Override
    protected void process() throws Exception {
        Message in = getInbox().take();
        String body = in.getString(HttpRequestNode.BODY);
        if (body == null) {
            warn("payload에 body가 없습니다. HTTP 요청 노드 뒤에 연결하세요.");
            return;
        }

        Map<String, String> mappings;
        JsonElement root;
        try {
            mappings = FieldMappings.parse(fields);
            root = JsonParser.parseString(body);
        } catch (IllegalArgumentException e) {
            warn("필드 옵션 오류: " + e.getMessage());
            return;
        } catch (JsonSyntaxException e) {
            warn("JSON을 읽을 수 없습니다: " + in.getString(HttpRequestNode.URL));
            return;
        }

        try {
            for (JsonElement item : JsonPaths.items(root, items)) {
                Map<String, Object> extracted = new LinkedHashMap<>(in.getPayload());
                extracted.remove(HttpRequestNode.BODY);
                for (Map.Entry<String, String> mapping : mappings.entrySet()) {
                    extracted.put(mapping.getKey(), JsonPaths.toJava(JsonPaths.value(item, mapping.getValue())));
                }
                send(new Message(in.getPriority(), extracted));
            }
        } catch (IllegalArgumentException e) {
            warn("경로 오류: " + e.getMessage());
        }
    }
}
