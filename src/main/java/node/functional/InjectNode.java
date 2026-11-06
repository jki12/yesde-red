package node.functional;

import ui.Codicons;
import javax.swing.Icon;
import annotation.OptionField;
import common.enums.FieldType;
import lombok.Getter;
import lombok.Setter;
import node.*;

@Setter
@Getter
public class InjectNode extends BaseNode implements Outputable {
    public static final String DISPLAY_NAME = "메시지 주입";
    public static final String DESCRIPTION = "설정한 메시지 또는 현재 시각을 연결된 노드에 전달합니다.";
    public static final Icon ICON = Codicons.icon("arrow-right", 18);

    @OptionField(type = FieldType.TEXT_AREA)
    private String message;

    public InjectNode() {
        super(Type.INJECT);
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
