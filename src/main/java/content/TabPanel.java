package content;

import lombok.extern.slf4j.Slf4j;
import util.Utils;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class TabPanel extends JTabbedPane {
    private static final String TAB_TITLE = "무제-";

    public TabPanel() {
        registerShortcuts();
        add();

        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
            }
        });
    }

    private void registerShortcuts() {
        InputMap inputMap = getInputMap(WHEN_IN_FOCUSED_WINDOW);
        inputMap.put(KeyStroke.getKeyStroke("meta T"), "metaT"); // TODO, ctrl 단축키도 추가.
        inputMap.put(KeyStroke.getKeyStroke("meta W"), "metaW");

        ActionMap actionMap = getActionMap();
        actionMap.put("metaT", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                add();
            }
        });

        actionMap.put("metaW", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (getTabCount() > 1) {
                    int index = getSelectedIndex();

                    remove(index);
                }
            }
        });
    }

    /**
     * @return "무제-n"의 형식으로 반환합니다.
     */
    private String generateTitle() {
        List<Integer> titles = new ArrayList<>(getTabCount());

        for (int i = 0; i < getTabCount(); ++i) {
            String title = getTitleAt(i);

            if (!title.startsWith(TAB_TITLE)) continue;

            String temp = title.substring(TAB_TITLE.length());
            if (Utils.isNumeric(temp)) {
                titles.add(Integer.parseInt(temp));
            }
        }

        Collections.sort(titles); // 정렬하지 않으면 발생하는 문제 있었음.. (옵시디언에 정리)

        int num = 1;
        for (int n : titles) {
            if (n != num) break;

            num++;
        }

        return TAB_TITLE + num;
    }

    public void add() {
        int idx = getSelectedIndex() + 1; // 시작시에도 + 1을 해주기 때문에 -1을 반환해도 문제 없음.

        add(new BoardPanel(generateTitle()), idx);
        setSelectedIndex(idx);
        log.info("새 탭이 생성되었습니다.(현재 탭 수 {})", getTabCount());
    }

    public void remove(int index) {
        removeTabAt(index);
        log.info("탭이 삭제되었습니다.(현재 탭 수 {})", getTabCount());
    }
}
