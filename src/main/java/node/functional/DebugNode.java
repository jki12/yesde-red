package node.functional;

import content.DebugPanel;
import ui.frame.YesdeRedFrame;
import node.InputOutputNode;
import node.Inputable;
import node.Message;
import node.Type;

public class DebugNode extends InputOutputNode implements Inputable {
    private final DebugPanel instance = YesdeRedFrame.getInstance().getDebugPanel();

    public DebugNode() {
        super(Type.DEBUG, "디버그", "수신한 메시지를 하단 콘솔에 표시합니다.");
    }

    @Override
    protected void process() throws Exception {
        Message message = getIn().poll();

        instance.add("DEBUG " + getId().substring(0, Math.min(6, getId().length())), message.toString());

        instance.revalidate();
        instance.repaint();
    }
}
