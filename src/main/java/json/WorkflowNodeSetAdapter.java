package json;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import node.BaseNode;

import java.lang.reflect.Type;
import java.util.Set;

public class WorkflowNodeSetAdapter implements JsonSerializer<Set<BaseNode>> { // TODO, impl deserializer.

    @Override
    public JsonElement serialize(Set<BaseNode> src, Type typeOfSrc, JsonSerializationContext context) {

        JsonArray arr = new JsonArray(src.size());
        src.forEach(n -> arr.add(n.getId()));

        return arr;
    }
}
