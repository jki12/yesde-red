package content;

import javax.swing.*;
import java.awt.*;

public class ContentPanel extends JPanel {

    public ContentPanel() {
        GridBagLayout layout = new GridBagLayout();
        setLayout(layout);

        // tab, debug 화면 비율을 7 : 3으로 조절하는 코드.
        TabPanel tabPanel = new TabPanel();
        GridBagConstraints tabPanelGbc = new GridBagConstraints();

        tabPanelGbc.gridx = 0;
        tabPanelGbc.gridy = 0;
        tabPanelGbc.weightx = 0.7;
        tabPanelGbc.weighty = 1;
        tabPanelGbc.fill = GridBagConstraints.BOTH;

        layout.setConstraints(tabPanel, tabPanelGbc);
        add(tabPanel, tabPanelGbc);

        DebugPanel debugPanel = new DebugPanel();
        GridBagConstraints debugPanelGbc = new GridBagConstraints();

        debugPanelGbc.gridx = 1;
        debugPanelGbc.gridy = 0;
        debugPanelGbc.weightx = 0.3;
        debugPanelGbc.weighty = 1;
        debugPanelGbc.fill = GridBagConstraints.BOTH;

        layout.setConstraints(debugPanel, debugPanelGbc);
        add(debugPanel, debugPanelGbc);
    }
}
