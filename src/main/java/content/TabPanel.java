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
    private static final String TAB_TITLE = "워크플로 ";

    public TabPanel() {
        registerShortcuts();
        addBoardTab();

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
                addBoardTab();
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
     * @return 새 탭의 기본 이름을 "워크플로 01" 형식으로 만들어 반환합니다.
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

        return TAB_TITLE + String.format("%02d", num);
    }

    public void addBoardTab() {
        int idx = getSelectedIndex() + 1; // 시작시에도 + 1을 해주기 때문에 -1을 반환해도 문제 없음.

        BoardPanel board = new BoardPanel(generateTitle());

        // 보드의 선택·노드 목록·연결·실행 상태 변경 알림을 상위 패널로 전달합니다.
        for (String property : new String[] {BoardPanel.PROP_SELECTED_NODE, BoardPanel.PROP_BOARD_STATE,
                BoardPanel.PROP_NODES_CHANGED, BoardPanel.PROP_CONNECTIONS_CHANGED}) {
            board.addPropertyChangeListener(property,
                    e -> firePropertyChange(property, e.getOldValue(), e.getNewValue()));
        }

        add(board, idx);
        setSelectedIndex(idx);
        log.info("새 탭이 생성되었습니다.(현재 탭 수 {})", getTabCount());
    }

    public void remove(int index) {
        removeTabAt(index);
        log.info("탭이 삭제되었습니다.(현재 탭 수 {})", getTabCount());
    }
}
