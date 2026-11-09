package json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import node.BaseNode;
import ui.board.NodePanel;

import java.util.Set;

/** 워크플로 직렬화에 쓰는 Gson 설정입니다. @Expose가 붙은 필드만 내보냅니다. */
public final class Json {
    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .excludeFieldsWithoutExposeAnnotation()
            .registerTypeAdapter(NodePanel.class, new NodePanelAdapter())
            .registerTypeAdapter(new TypeToken<Set<BaseNode>>() {}.getType(), new WorkflowNodeSetAdapter())
            .create();

    private Json() { }

    public static String toJson(Object object) {
        return GSON.toJson(object);
    }
}
