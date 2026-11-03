package node;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Getter
public abstract class InputOutputNode extends BaseNode {
    private Pipe in;
    private Set<BaseNode> out;

    protected InputOutputNode(Type type) {
        super(type);

        if (this instanceof Inputable) {
            in = new Pipe();
        }

        if (this instanceof Outputable) {
            out = new HashSet<>();
        }
    }

    public boolean tryConnect(BaseNode other) {
        if (!canConnect(other) || out.contains(other)) return false;

        out.add(other);
        log.info("{}노드와 {}노드가 연결되었습니다.", this, other);

        return true;
    }

    private boolean canConnect(BaseNode other) {
        if (this.equals(other)) return false;

        return  (this instanceof Inputable && other instanceof Outputable)
                || (this instanceof Outputable && other instanceof Inputable);
    }
}
