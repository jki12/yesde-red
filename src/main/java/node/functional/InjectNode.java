package node.functional;

import lombok.Getter;
import lombok.Setter;
import node.*;

@Setter
@Getter
public class InjectNode extends InputOutputNode implements Outputable {
    private String message;

    public InjectNode() {
        super(Type.INJECT);
    }

    @Override
    protected void process() throws Exception {
        Message msg = new Message((message != null) ? message : String.valueOf(System.currentTimeMillis()));

        for (BaseNode node : getOut()) {
            ((InputOutputNode) node).getIn().push(msg);
        }
    }
}
