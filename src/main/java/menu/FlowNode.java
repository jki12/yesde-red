package menu;

import lombok.extern.slf4j.Slf4j;
import node.BaseNode;
import node.Type;
import util.Utils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionAdapter;
import java.lang.reflect.Constructor;

@Slf4j
public class FlowNode extends JPanel {
    public static final int DEFAULT_WIDTH = 80;
    public static final int DEFAULT_HEIGHT = 40;

    private final BaseNode node;
    private Point cur;

    public FlowNode(String type) {
        type = type.toUpperCase();

        Type t = Type.valueOf(type); // type은 항상 올바른 값이 들어온다고 가정한다.

        try {
            Constructor<?> constructor = BaseNode.CLASS_MAP.get(t).getConstructor();
            constructor.setAccessible(true);

            node = (BaseNode) constructor.newInstance();
            log.info("{} flow 노드가 생성되었습니다.", node.getClass().getSimpleName());
            setBackground(Color.lightGray);

        } catch (Exception e) {
            log.error(e.getMessage());
            throw new IllegalStateException(e);
        }

        JLabel label = new JLabel(new ImageIcon("src/main/resources/icon/search-16x16.png")); // TODO, test.
        label.setText(type);

        add(label);

        setBorder(BorderFactory.createLineBorder(Color.black, 2));

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
                    // popup menu (설정 창) - reflection으로 + 어토테이션으로 boolean이면 체크 박스, string이면 text.
                    // dialog.
                    return;
                }
                else if (e.getButton() == MouseEvent.BUTTON3) {
                    // popup으로 삭제, 이름 변경,

                }
            }
        });

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                int x = getLocation().x + (e.getX() - cur.x);
                int y = getLocation().y + (e.getY() - cur.y);

                setLocation(x, y);
            }
        });
    }

    public void start() {
        node.start();
    }
}
