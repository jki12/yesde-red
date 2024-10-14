package content;

import lombok.Getter;
import menu.FlowNode;
import menu.popup.NodePopupMenu;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.Flow;

@Getter
public class BoardPanel extends JPanel {
    private final JPopupMenu popupMenu;
    private String description = ""; // TODOm tool tip.
    private boolean isRunning = false;
//    private /\;

    public BoardPanel(String name) {
        setName(name);
        setLayout(null);

        popupMenu = new NodePopupMenu();
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Point cur = e.getPoint();

                if (e.getButton() == MouseEvent.BUTTON1) {

                }

                if (e.getButton() == MouseEvent.BUTTON3) {
                    if (getComponentAt(cur.x, cur.y) instanceof FlowNode) return;

                    popupMenu.show(getComponentAt(cur), cur.x, cur.y);
                }
            }
        });
    }

    public void run() {
        isRunning = true;



        for (Component flowNode : getComponents()) {
            ((FlowNode)flowNode).start();

        }
    }

    public void stop() {
        isRunning = false;
    }
}
