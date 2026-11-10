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

/**
 * 조건식에 맞는 메시지만 통과시키고 나머지는 버립니다. 문법은 {@link Conditions}를 보세요.
 * 조건식 오류는 시작 시 노드 실패입니다. 조건이 비어 있으면 모두 통과시킵니다.
 */
@Getter
@Setter
public class FilterNode extends BaseNode implements Inputable, Outputable {
    @OptionField(type = OptionInputType.TEXT_AREA, label = "조건식 (예: price < 20000 and title contains \"키보드\")")
    private String condition;

    private Conditions.Condition compiled;

    public FilterNode(String id) {
        super(NodeType.FILTER, id);
    }

    @Override
    protected void onStart() {
        super.onStart();
        try {
            compiled = condition == null || condition.isBlank() ? payload -> true : Conditions.parse(condition);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("조건식 오류: " + e.getMessage(), e);
        }
    }

    @Override
    protected void process() throws Exception {
        Message message = getInbox().take();
        if (compiled.test(message.getPayload())) send(message);
    }
}
