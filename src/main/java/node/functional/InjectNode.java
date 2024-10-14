package node.functional;

import lombok.Getter;
import lombok.Setter;
import node.*;

@Setter
@Getter
public class InjectNode extends InputOutputNode implements Output {
    private String message = "test";

    public InjectNode() {
        super(Type.INJECT);
    }

    @Override
    protected void process() throws Exception {
        for (BaseNode node : getOut()) {
            ((InputOutputNode) node).getIn().push(new Message(message));

        }
    }
}
