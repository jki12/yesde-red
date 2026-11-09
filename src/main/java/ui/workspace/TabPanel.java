package ui.workspace;
import ui.board.BoardPanel;

import json.WorkflowDocument;
import lombok.extern.slf4j.Slf4j;
import workflow.RunState;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
public class TabPanel extends JTabbedPane {
    private static final String TAB_TITLE_PREFIX = "워크플로 ";
    private static final Pattern NUMERIC = Pattern.compile("[0-9]+");

    public TabPanel() {
        registerShortcuts();
        addBoardTab();
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
                if (closeBoardTab(getSelectedIndex()) || getTabCount() <= 1) return;
                JOptionPane.showMessageDialog(TabPanel.this,
                        "실행 중이거나 종료 중인 워크플로는 닫을 수 없습니다. 먼저 종료하세요.");
            }
        });
    }

    /**
     * @return 새 탭의 기본 이름을 "워크플로 01" 형식으로 만들어 반환합니다.
     */
    private String nextTabTitle() {
        List<Integer> titles = new ArrayList<>(getTabCount());

        for (int i = 0; i < getTabCount(); ++i) {
            String title = getTitleAt(i);

            if (!title.startsWith(TAB_TITLE_PREFIX)) continue;

            String temp = title.substring(TAB_TITLE_PREFIX.length());
            if (NUMERIC.matcher(temp).matches()) {
                titles.add(Integer.parseInt(temp));
            }
        }

        Collections.sort(titles); // 오름차순으로 훑어야 비어 있는 가장 작은 번호를 찾을 수 있습니다.

        int num = 1;
        for (int n : titles) {
            if (n != num) break;

            num++;
        }

        return TAB_TITLE_PREFIX + String.format("%02d", num);
    }

    public void addBoardTab() {
        addTab(new BoardPanel(nextTabTitle()));
    }

    /**
     * 저장된 워크플로를 새 탭으로 엽니다. 문서 이름이 비어 있거나 이미 열린 탭과 같으면 기본 이름을 씁니다.
     * 문서가 잘못됐으면 탭을 만들지 않고 IllegalArgumentException을 던집니다.
     */
    public BoardPanel addBoardTab(WorkflowDocument document) {
        String name = document.name();
        String title = name == null || name.isBlank() || hasTab(name) ? nextTabTitle() : name;
        BoardPanel board = new BoardPanel(title);
        board.load(document);
        return addTab(board);
    }

    private BoardPanel addTab(BoardPanel board) {
        int idx = getSelectedIndex() + 1; // 시작시에도 + 1을 해주기 때문에 -1을 반환해도 문제 없음.

        JScrollPane scroll = new JScrollPane(board);
        scroll.setBorder(null);
        scroll.setName(board.getName()); // JTabbedPane.add(Component)는 컴포넌트 이름을 탭 제목으로 씁니다.

        add(scroll, idx);
        setSelectedIndex(idx);
        log.info("새 탭이 생성되었습니다.(현재 탭 수 {})", getTabCount());
        return board;
    }

    private boolean hasTab(String title) {
        for (int i = 0; i < getTabCount(); i++) {
            if (title.equals(getTitleAt(i))) return true;
        }
        return false;
    }

    /** index번째 탭의 보드입니다. 탭에는 보드를 감싼 스크롤 패널이 들어 있습니다. */
    public BoardPanel getBoardAt(int index) {
        return (BoardPanel) ((JScrollPane) getComponentAt(index)).getViewport().getView();
    }

    /** 선택된 탭의 보드입니다. 탭이 없으면 null입니다. */
    public BoardPanel getSelectedBoard() {
        int index = getSelectedIndex();
        return index < 0 ? null : getBoardAt(index);
    }

    /** 탭을 닫습니다. 마지막 남은 탭이거나 보드가 실행 중(또는 종료 중)이면 닫지 않고 false를 돌려줍니다. */
    public boolean closeBoardTab(int index) {
        if (getTabCount() <= 1) return false;
        // 닫힌 보드의 워커·감시 스레드가 고아가 되지 않도록 IDLE일 때만 닫습니다.
        if (getBoardAt(index).getRunState() != RunState.IDLE) return false;

        removeTabAt(index);
        log.info("탭이 삭제되었습니다.(현재 탭 수 {})", getTabCount());
        return true;
    }
}
