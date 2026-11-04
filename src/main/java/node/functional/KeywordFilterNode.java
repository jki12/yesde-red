package node.functional;

import annotation.OptionField;
import com.google.gson.annotations.Expose;
import lombok.Getter;
import lombok.Setter;
import node.BaseNode;
import node.InputOutputNode;
import node.Inputable;
import node.Message;
import node.Outputable;
import node.Type;
import ui.Codicons;

import javax.swing.Icon;

@Getter
@Setter
public class KeywordFilterNode extends InputOutputNode implements Inputable, Outputable {
    public static final String DISPLAY_NAME = "키워드 필터";
    public static final String DESCRIPTION = "키워드가 포함된 메시지만 전달합니다.";
    public static final Icon ICON = Codicons.icon("search", 18);

    @Expose
    @OptionField
    private volatile String keyword = "error"; // 임시로 설정

    public KeywordFilterNode() {
        super(Type.KEYWORD_FILTER);

        setIntervalMilli(0);
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
    protected void process() throws InterruptedException {
        Message message = getIn().poll();
        String text = message.getMessage();
        String currentKeyword = keyword;

        if (currentKeyword == null || currentKeyword.isBlank()
                || text == null || !text.contains(currentKeyword)) {
            return;
        }

        for (BaseNode node : getOut()) {
            ((InputOutputNode) node).getIn().push(message);
        }
    }
}
