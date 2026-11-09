package json;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import ui.menu.NodePanel;
import json.Json;

import java.lang.reflect.Type;

public class NodePanelAdapter implements JsonSerializer<NodePanel> { // TODO, impl deserializer.

    @Override
    public JsonElement serialize(NodePanel src, Type typeOfSrc, JsonSerializationContext context) {
        return JsonParser.parseString(Json.toJson(src.getNode()));
    }
}
