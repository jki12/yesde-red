package ui.board;

import lombok.Getter;
import node.*;
import ui.theme.UiTheme;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * 보드 위의 노드 카드입니다. 카드 이동, 선택, 포트에서 시작하는 연결 드래그 입력을 처리합니다.
 */
public class NodePanel extends JPanel {
    public static final int DEFAULT_WIDTH = 236;
    public static final int DEFAULT_HEIGHT = 72;

    @Getter
    private final BaseNode node;
    @Getter
    private final JButton inputPort;
    @Getter
    private final JButton outputPort;

    private Point dragOrigin;
    private boolean selected;

    public NodePanel(String type) {
        this(NodeFactory.createNode(type));
    }

    /*
    * NodePanel 구성
    *
    * NodePanel (JPanel, BorderLayout)
    * ├─ WEST: 포트 컨테이너 (JPanel)
    * │   └─ inputPort (JButton): 입력 포트
    * │
    * ├─ CENTER: body (JPanel, BorderLayout)
    * │   ├─ NORTH: text (JPanel, BorderLayout)
    * │   │   ├─ WEST: icon (JLabel): 노드 아이콘
    * │   │   └─ CENTER: name (JLabel): 노드 이름
    * │   │
    * │   └─ CENTER: type (JLabel): 노드 타입
    * │
    * └─ EAST: 포트 컨테이너 (JPanel)
    *     └─ outputPort (JButton): 출력 포트
    *
    */
    public NodePanel(BaseNode node) {
        this.node = node;

        setLayout(new BorderLayout(6, 0)); // 입력, 출력 포트 사이 간격 설정.
        setOpaque(false);

        inputPort = createPort(node instanceof Inputable);
        outputPort = createPort(node instanceof Outputable);

        add(createPortContainer(inputPort), BorderLayout.WEST);
        add(createPortContainer(outputPort), BorderLayout.EAST);

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        JLabel icon = new JLabel(node.getIcon());
        JPanel text = new JPanel(new BorderLayout(6, 0));
        text.setOpaque(false);
        text.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        text.setPreferredSize(new Dimension(0, 28));

        JLabel name = UiTheme.label(node.getDisplayName() + " " + node.getId(), false);
        name.setFont(UiTheme.getFont());
        JLabel type = UiTheme.label(node.getType().name(), true);

        type.setFont(UiTheme.getNodeTypeFont());
        type.setBorder(BorderFactory.createEmptyBorder(7, 30, 5, 8));

        text.add(icon, BorderLayout.WEST);
        text.add(name, BorderLayout.CENTER);

        body.add(text, BorderLayout.NORTH);
        body.add(type, BorderLayout.CENTER);

        add(body, BorderLayout.CENTER);

        MouseAdapter drag = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) return; // TODO, 삭제, 복사 창 뜰 수 있게 수정.

                BoardPanel board = board();
                if (board != null) {
                    board.setSelectedNode(NodePanel.this);
                    board.setComponentZOrder(NodePanel.this, 0); // 겹친 카드 중 누른 카드를 맨 앞으로
                    board.requestFocusInWindow(); // Delete 키로 선택 카드를 지울 수 있게 보드가 포커스를 가집니다.
                    board.repaint();
                }

                // 클릭 위치를 NodePanel 기준 좌표로 변환해 저장
                dragOrigin = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), NodePanel.this);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragOrigin == null || board() == null) return;

                Point current = SwingUtilities.convertPoint(e.getComponent(), e.getPoint(), NodePanel.this);

                // 왼쪽, 위쪽 이동 범위를 제한.
                // TODO: 오른쪽, 아래쪽 경계도 적용해야 함.
                int x = Math.max(6, getX() + current.x - dragOrigin.x);
                int y = Math.max(6, getY() + current.y - dragOrigin.y);

                setLocation(x, y);
                node.setLocation(new Point(x, y));

                board().repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragOrigin = null;
            }
        };

        // 내부 라벨·패널에서도 동일하게 노드를 선택하고 드래그할 수 있도록 등록.
        for (JComponent component : new JComponent[] {this, body, text, icon, name, type}) {

            component.addMouseListener(drag);
            component.addMouseMotionListener(drag);
        }
    }

    private JPanel createPortContainer(JButton button) {
        JPanel container = new JPanel(new GridBagLayout());

        container.setOpaque(false);
        container.setPreferredSize(new Dimension(18, DEFAULT_HEIGHT));
        container.add(button);

        return container;
    }

    private JButton createPort(boolean visible) {
        JButton port = new JButton();

        port.setVisible(visible);
        port.setPreferredSize(new Dimension(16, 16));
        // 아래에서 원형 포트로 그리고 있어 안보이게 설정.
        port.setBorderPainted(false);
        port.setContentAreaFilled(false);
        port.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));

        port.setIcon(new Icon() {
            public int getIconWidth() { return 12; }
            public int getIconHeight() { return 12; }

            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D copy = (Graphics2D) g.create();
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                boolean active = Boolean.TRUE.equals(port.getClientProperty("connectionActive"));
                copy.setColor(active ? UiTheme.getAccentColor() : UiTheme.getBackgroundColor());
                copy.fillOval(x + 1, y + 1, 9, 9);
                copy.setColor(selected ? UiTheme.getAccentColor()
                        : UiTheme.getBorderColor());

                copy.setStroke(new BasicStroke(1.5f));
                copy.drawOval(x + 1, y + 1, 9, 9);
                copy.dispose();
            }
        });

        registerConnectionDragListeners(port);
        return port;
    }

    private void registerConnectionDragListeners(JButton port) {
        MouseAdapter connectionDrag = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || board() == null) return;

                board().setSelectedNode(NodePanel.this);
                board().beginConnection(port);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                BoardPanel board = board();

                if (board != null && board.getConnectionStartPort() == port) {
                    board.updateConnection(SwingUtilities.convertPoint(port, e.getPoint(), board));
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                BoardPanel board = board();

                if (!SwingUtilities.isLeftMouseButton(e) || board == null || board.getConnectionStartPort() != port) return;

                board.finishConnection(SwingUtilities.convertPoint(port, e.getPoint(), board));
            }
        };
        port.addMouseListener(connectionDrag);
        port.addMouseMotionListener(connectionDrag);
    }

    private BoardPanel board() {
        return getParent() instanceof BoardPanel ? (BoardPanel) getParent() : null;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        repaint();
    }

    // 선택 여부에 따라 배경·제목 띠·테두리를 직접 그립니다. 글자·아이콘·포트는 자식 컴포넌트가 그립니다.
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D copy = (Graphics2D) g.create();

        copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        copy.setColor(selected ? UiTheme.getNodeSelectedBackgroundColor() : UiTheme.getBackgroundColor());
        copy.fillRoundRect(8, 1, getWidth() - 17, getHeight() - 3, 4, 4);

        copy.setColor(selected ? UiTheme.getNodeSelectedHeaderBackgroundColor() : UiTheme.getHeaderBackgroundColor());
        copy.fillRect(9, 2, getWidth() - 19, 29);
        copy.setColor(UiTheme.getBorderColor());
        copy.drawLine(9, 31, getWidth() - 10, 31);

        copy.setColor(selected ? UiTheme.getAccentColor()
                : UiTheme.getBorderColor());
        copy.setStroke(new BasicStroke(selected ? 1.8f : 1f));
        copy.drawRoundRect(8, 1, getWidth() - 17, getHeight() - 3, 4, 4);

        copy.dispose();
    }
}
