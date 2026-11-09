package node.functional;

import ui.Codicons;
import javax.swing.Icon;
import content.ConsolePanel;
import ui.frame.YesdeRedFrame;
import node.BaseNode;
import node.Inputable;
import node.Message;
import node.Type;

public class DebugNode extends BaseNode implements Inputable {
    public static final String DISPLAY_NAME = "디버그";
    public static final String DESCRIPTION = "수신한 메시지를 하단 콘솔에 표시합니다.";
    public static final Icon ICON = Codicons.icon("bug", 18);

    private final ConsolePanel console = YesdeRedFrame.getInstance().getConsole();

    public DebugNode() {
        super(Type.DEBUG);
    }

    @Override
    public Icon getIcon() {
        return ICON;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    protected void process() throws Exception {
        Message message = getIn().take();

        console.append("DEBUG " + getId(), message.toString());

        console.revalidate();
        console.repaint();
    }
}
