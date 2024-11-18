package json;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import ui.menu.WorkflowNode;
import util.Utils;

import java.lang.reflect.Type;

public class WorkflowNodeAdapter implements JsonSerializer<ui.menu.WorkflowNode> { // TODO, impl deserializer.

    @Override
    public JsonElement serialize(WorkflowNode src, Type typeOfSrc, JsonSerializationContext context) {
        return JsonParser.parseString(Utils.toJson(src.getNode()));
    }
}
