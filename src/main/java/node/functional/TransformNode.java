package node.functional;

import lombok.Getter;
import lombok.Setter;
import node.BaseNode;
import node.Inputable;
import node.Message;
import node.NodeType;
import node.Outputable;
import node.option.OptionField;
import node.option.OptionInputType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * payload의 필드를 규칙대로 바꾸거나 새 필드를 만듭니다. 출처마다 다른 모양의 데이터를 공통 스키마로 맞추는 데 씁니다.
 * 규칙 문법은 {@link FieldTransforms}를 보세요. 규칙 문법 오류는 시작 시 노드 실패로, 값 변환 실패는 경고로 알립니다.
 */
@Getter
@Setter
public class TransformNode extends BaseNode implements Inputable, Outputable {
    @OptionField(type = OptionInputType.TEXT_AREA, label = "변환 규칙 (줄마다 대상 = 식)")
    private String rules;
    @OptionField(type = OptionInputType.TEXT_AREA, label = "남길 필드 (줄마다 이름, 비우면 전부)")
    private String keep;

    private List<FieldTransforms.Rule> compiledRules = List.of();
    private List<String> keptFields;

    public TransformNode(String id) {
        super(NodeType.TRANSFORM, id);
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            compiledRules = FieldTransforms.parseRules(rules);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("변환 규칙 오류: " + e.getMessage(), e);
        }
        keptFields = parseKeep();
    }

    @Override
    protected void process() throws Exception {
        Message in = getInbox().take();
        Map<String, Object> transformed;
        try {
            transformed = FieldTransforms.apply(compiledRules, in.getPayload());
        } catch (FieldTransforms.TransformException e) {
            warn(e.getMessage() + " — 메시지를 건너뜁니다.");
            return;
        }
        if (keptFields != null) {
            Map<String, Object> pruned = new LinkedHashMap<>();
            for (String field : keptFields) pruned.put(field, transformed.get(field));
            transformed = pruned;
        }
        send(new Message(in.getPriority(), transformed));
    }

    private List<String> parseKeep() {
        if (keep == null || keep.isBlank()) return null;
        List<String> names = new ArrayList<>();
        for (String line : keep.split("\\R")) {
            String name = line.strip();
            if (!name.isEmpty() && !name.startsWith("#")) names.add(name);
        }
        return names.isEmpty() ? null : names;
    }
}
