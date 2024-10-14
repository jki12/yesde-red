package node.functional;

import node.*;
import sidebar.DebugPanel;

import javax.swing.*;
import java.awt.*;

public class DebugNode extends InputOutputNode implements Input {
    private DebugPanel instance = DebugPanel.getInstance();

    public DebugNode() {
        super(Type.DEBUG);
    }

    @Override
    protected void process() throws Exception {
        getIn().push(new Message("test"));
        Message message = getIn().poll();

//        var test = new JLabel(message.toString());
//        var box = Box.createVerticalBox();
//        box.add(test);
//        test.setAlignmentX(Component.CENTER_ALIGNMENT);

        instance.add(message.toString());


        instance.updateUI();
        System.out.println("message = " + message);
    }
}
