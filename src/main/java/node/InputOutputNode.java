package node;

import lombok.Getter;

import java.util.HashSet;
import java.util.Set;

@Getter
public abstract class InputOutputNode extends BaseNode {
    private final Pipe in;
    private final Set<BaseNode> out;

    protected InputOutputNode(Type type) {
        super(type);

        in = (this instanceof Input) ? new Pipe() : null;
        out = (this instanceof Output) ? new HashSet<>() : null;
    }

    protected void connect(BaseNode node) {
        if (this instanceof Input && node instanceof Output || this instanceof Output && node instanceof Input) {
            out.add(node);
        }
    }
}
