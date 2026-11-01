package node.functional;

import content.DebugPanel;
import ui.frame.YesdeRedFrame;
import node.InputOutputNode;
import node.Inputable;
import node.Message;
import node.Type;

public class DebugNode extends InputOutputNode implements Inputable {
    public static final String DISPLAY_NAME = "디버그";
    public static final String DESCRIPTION = "수신한 메시지를 하단 콘솔에 표시합니다.";

    private final DebugPanel instance = YesdeRedFrame.getInstance().getDebugPanel();

    public DebugNode() {
        super(Type.DEBUG, DISPLAY_NAME, DESCRIPTION);
    }

    @Override
    protected void process() throws Exception {
        Message message = getIn().poll();

        instance.add("DEBUG " + getId().substring(0, Math.min(6, getId().length())), message.toString());

        instance.revalidate();
        instance.repaint();
    }
}
