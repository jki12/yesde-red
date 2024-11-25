package node.functional;

import ui.content.DebugPanel;
import ui.frame.YesdeRedFrame;
import node.InputOutputNode;
import node.Inputable;
import node.Message;
import node.Type;

public class DebugNode extends InputOutputNode implements Inputable {
    private final DebugPanel instance = (DebugPanel) YesdeRedFrame.getInstance().getContentPane().getComponent(1);

    public DebugNode() {
        super(Type.DEBUG);
    }

    @Override
    protected void process() throws Exception {
        Message message = getIn().poll();

        instance.add(message.toString());

        instance.revalidate();
        instance.repaint();
    }
}
