package util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import json.NodePanelAdapter;
import json.WorkflowNodeSetAdapter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import node.BaseNode;
import content.BoardPanel;
import ui.frame.YesdeRedFrame;
import ui.menu.NodePanel;

import javax.swing.*;
import java.util.*;
import java.util.regex.Pattern;

@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Utils {
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("^[0-9]*$");
    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .excludeFieldsWithoutExposeAnnotation()
            .registerTypeAdapter(NodePanel.class, new NodePanelAdapter())
            .registerTypeAdapter(new TypeToken<Set<BaseNode>>() {}.getType(), new WorkflowNodeSetAdapter())
            .create();

    public static boolean isNumeric(String s) {
        return NUMERIC_PATTERN.matcher(s).matches();
    }

    public static TabInfo getSelectedTabInfo() {
        JTabbedPane contentPane = YesdeRedFrame.getInstance().getTabs();
        int selectedIndex = contentPane.getSelectedIndex();

        return new TabInfo(selectedIndex, ((BoardPanel) contentPane.getComponentAt(selectedIndex)));
    }

    public static String toJson(Object object) {
        return GSON.toJson(object);
    }
}
