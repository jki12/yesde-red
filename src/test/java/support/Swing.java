package support;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/** 창 없이 컴포넌트를 배치·렌더링하고 마우스 이벤트를 직접 전달하는 도우미입니다. */
public final class Swing {
    private Swing() { }

    /** 컨테이너와 모든 하위 컴포넌트를 현재 크기로 배치합니다. */
    public static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) layout(nested);
        }
    }

    public static BufferedImage render(JComponent component) {
        BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        component.printAll(graphics);
        graphics.dispose();
        return image;
    }

    /** 영역 안에서 기준 색과 가까운(채널별 차이 25 미만) 픽셀 수를 셉니다. */
    public static int pixelsNear(BufferedImage image, Rectangle region, Color reference) {
        int count = 0;
        for (int y = region.y; y < region.y + region.height; y++) {
            for (int x = region.x; x < region.x + region.width; x++) {
                Color color = new Color(image.getRGB(x, y));
                if (Math.abs(color.getRed() - reference.getRed()) < 25
                        && Math.abs(color.getGreen() - reference.getGreen()) < 25
                        && Math.abs(color.getBlue() - reference.getBlue()) < 25) count++;
            }
        }
        return count;
    }

    public static Component find(Container root, String name) {
        for (Component child : root.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container nested) {
                Component found = find(nested, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    public static Point center(Component component, Component destination) {
        return SwingUtilities.convertPoint(component, component.getWidth() / 2, component.getHeight() / 2, destination);
    }

    public static void press(Component target) {
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
                MouseEvent.BUTTON1_DOWN_MASK, target.getWidth() / 2, target.getHeight() / 2, 1, false, MouseEvent.BUTTON1));
    }

    /** destination 좌표계의 point로 드래그 이벤트를 target에 보냅니다. */
    public static void drag(Component target, Component destination, Point point) {
        Point local = SwingUtilities.convertPoint(destination, point, target);
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(),
                MouseEvent.BUTTON1_DOWN_MASK, local.x, local.y, 0, false, MouseEvent.NOBUTTON));
    }

    public static void release(Component target, Component destination, Point point) {
        Point local = SwingUtilities.convertPoint(destination, point, target);
        target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(),
                0, local.x, local.y, 1, false, MouseEvent.BUTTON1));
    }
}
