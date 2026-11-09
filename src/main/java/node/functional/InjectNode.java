package node.functional;

import ui.theme.Codicons;
import javax.swing.Icon;
import node.option.OptionField;
import node.option.OptionInputType;
import lombok.Getter;
import lombok.Setter;
import node.*;

@Setter
@Getter
public class InjectNode extends PollingNode implements Outputable {
    public static final String DISPLAY_NAME = "메시지 주입";
    public static final String DESCRIPTION = "설정한 메시지 또는 현재 시각을 연결된 노드에 전달합니다.";
    public static final Icon ICON = Codicons.icon("arrow-right", 18);

    @OptionField(type = OptionInputType.TEXT_AREA)
    private String message;

    public InjectNode() {
        super(NodeType.INJECT);
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
        Message msg = new Message((message != null) ? message : String.valueOf(System.currentTimeMillis()));

        send(msg);
    }
}
