package node.functional;

import node.option.OptionField;
import node.option.OptionInputType;
import lombok.Getter;
import lombok.Setter;
import node.*;

@Setter
@Getter
public class InjectNode extends PollingNode implements Outputable {
    @OptionField(type = OptionInputType.TEXT_AREA)
    private String message;

    public InjectNode() {
        super(NodeType.INJECT);
    }

    /** 저장된 워크플로를 불러올 때처럼 ID가 정해져 있는 경우에 씁니다. */
    public InjectNode(String id) {
        super(NodeType.INJECT, id);
    }

    @Override
    protected void process() throws Exception {
        Message msg = new Message((message != null) ? message : String.valueOf(System.currentTimeMillis()));

        send(msg);
    }
}
