package content;

import lombok.Getter;
import lombok.Setter;
import menu.FlowNode;
import menu.popup.NodePopupMenu;
import node.BaseNode;
import node.InputOutputNode;
import node.Outputable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.CubicCurve2D;
import java.util.Arrays;

@Getter
public class BoardPanel extends JPanel {
    private final JPopupMenu popupMenu;
    @Setter
    private String description; // TODO, tool tip, json 형식으로 export시 사용
    private boolean isRunning;

    public BoardPanel(String name) {
        setName(name);
        setLayout(null); // 노드들을 자유롭게 배치해야 하기 때문에 null로 설정.

        popupMenu = new NodePopupMenu();

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) { // TODO, 왼쪽 클릭 시 node 선택(다중 선택이 지원되어야 함)
                Point cur = e.getPoint();

                if (e.getButton() == MouseEvent.BUTTON3) {
                    if (getComponentAt(cur.x, cur.y) instanceof FlowNode) return; // TODO, 노드 삭제, 복사 .. 창 뜨도록

                    // 노드 선택창 popup.
                    popupMenu.show(getComponentAt(cur), cur.x, cur.y);
                }
            }
        });
    }

    private BaseNode[] getNodes() {
        return Arrays.stream(getComponents())
                .map(c -> ((FlowNode) c).getNode()).toArray(BaseNode[]::new);
    }

    // TODO, 이미 실행 중인 경우 알림 뜨도록 변경,
    public void run() {
        isRunning = true;

        for (BaseNode node : getNodes()) {
            node.start();
        }
    }

    public void stop() {
        isRunning = false;

        for (BaseNode node : getNodes()) {
            node.stop();
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);

        // 노드들 끼리 연결.
        Graphics2D graphics2D = (Graphics2D) g.create();
        graphics2D.setColor(Color.gray); // line color.
        graphics2D.setStroke(new BasicStroke(3));

        for (Component c : getComponents()) {
            FlowNode flowNode = (FlowNode) c;

            if (flowNode.getNode() instanceof Outputable) {

                // 시작 지점 (output port)
                Point p1 = SwingUtilities.convertPoint(flowNode, flowNode.getOutputPort().getLocation(), this);

                for (BaseNode node : ((InputOutputNode) flowNode.getNode()).getOut()) {
                    FlowNode dst = FlowNode.FLOW_NODE_MAP.get(node.getId());

                    Point p2 = SwingUtilities.convertPoint(dst, dst.getInputPort().getLocation(), this);

                    // TODO, 매직 넘버 제거, 비율 조정, clickable 하게 만들어야 함.
                    CubicCurve2D line = new CubicCurve2D.Double(p1.x + 5, p1.y + 5, p1.x + 5 + 40, p1.y + 5 + 40, p2.x - 35, p2.y - 35, p2.x + 5, p2.y + 5);
                    graphics2D.draw(line);
                }
            }
        }

        revalidate();
        repaint();
        graphics2D.dispose();
    }
}
