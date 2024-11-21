package ui.menu;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import node.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class WorkflowNode extends JPanel {
    public static final int DEFAULT_WIDTH = 120;
    public static final int DEFAULT_HEIGHT = 60;
    public static final int MAX_NAME_LEN = 5;
    public static final Map<String, WorkflowNode> FLOW_NODE_MAP = new HashMap<>();

    private static final int OUT_PORT_INDEX = 1;
    private static final int IN_PORT_INDEX = 0;
    private static final Dimension PORT_SIZE = new Dimension(10, 10);

    private Point cur;
    @Getter
    private final BaseNode node;
    @Getter
    private final JButton inputPort;
    @Getter
    private final JButton outputPort;

    public WorkflowNode(String type) {
        GridBagLayout layout = new GridBagLayout();
        setLayout(layout);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder());

        node = NodeFactory.createNode(type);
        FLOW_NODE_MAP.put(node.getId(), this);

        JPanel panel = new JPanel(); // icon, background color를 관리하기 위한 컨테이너.
        panel.setBackground(Color.lightGray);
        panel.setBorder(BorderFactory.createLineBorder(Color.black, 1, true));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.ipadx = 0;
        gbc.insets = new Insets(0, 0, 0, 0); // 모든 방향의 간격을 0으로

        // input port 설정.
        inputPort = getPort(node instanceof Inputable);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.1;

        layout.setConstraints(inputPort, gbc);
        add(inputPort, gbc);

        // output port 설정.
        outputPort = getPort(node instanceof Outputable);
        gbc.gridx = 2;
        gbc.gridy = 0;
        gbc.weightx = 0.1;

        layout.setConstraints(outputPort, gbc);
        add(outputPort, gbc);

        // node 설정.
        JLabel label = new JLabel(); // TODO, icon 추가.
        label.setText(type.substring(0, Math.min(type.length(), MAX_NAME_LEN)));
        panel.add(label);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 0.8;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;

        layout.setConstraints(panel, gbc);
        add(panel, gbc);

        addMouseListener(new MouseAdapter() {

            @Override
            public void mouseEntered(MouseEvent e) {
                setCursor(new Cursor(Cursor.HAND_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
            }

            @Override
            public void mousePressed(MouseEvent e) {
                cur = e.getPoint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 || e.getClickCount() == 2) {
                    // TODO, popup menu (설정 창)
                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                int x = getLocation().x + (e.getX() - cur.x);
                int y = getLocation().y + (e.getY() - cur.y);

                setLocation(x, y);
                node.setX(x);
                node.setY(y);
            }
        });
    }

    /**
     * input, output port ui를 설정해 반환해줌.
     */
    private JButton getPort(boolean enable) {
        JButton port = new JButton();
        port.setVisible(enable);

        port.setPreferredSize(PORT_SIZE);
        port.setMaximumSize(PORT_SIZE);

        port.addMouseListener(new MouseAdapter() {
            private boolean isOutPort;

            @Override
            public void mousePressed(MouseEvent e) {
                super.mousePressed(e);
                cur = e.getPoint();

                isOutPort = (getComponent(OUT_PORT_INDEX) == e.getComponent());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                Point releasedPoint = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), getParent());

                if (!(getParent().getComponentAt(releasedPoint) instanceof WorkflowNode)) return;

                WorkflowNode other = (WorkflowNode) getParent().getComponentAt(releasedPoint);
                Component t = other.getComponentAt(SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), other));

                // e.getComponent() == other이 아닌 이전 컴포넌트 값을 가짐.
                if (isOutPort && other.node instanceof Inputable && t != other.getComponent(OUT_PORT_INDEX)) { // out - out으로 연결 x.
                    ((Outputable) node).connect((Inputable) other.node);
                }
                else if (!isOutPort && other.node instanceof Outputable && t != other.getComponent(IN_PORT_INDEX)) { // in - in으로 연결 x.
                    ((Outputable) other.node).connect((Inputable) node);
                }
            }
        });

        return port;
    }
}
