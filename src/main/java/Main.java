import ui.YesdeRedFrame;
import ui.theme.UiTheme;
import javax.swing.SwingUtilities;

public class Main {

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeLater(() -> {
            UiTheme.install();
            YesdeRedFrame.getInstance().setVisible(true);
        });
    }
}
