package content;

import ui.Codicons;

import ui.UiTheme;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ConsolePanel extends JPanel {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String[] COLUMN_NAMES =  { "시간", "출력 노드", "메시지" };

    private final DefaultTableModel model = new DefaultTableModel(COLUMN_NAMES, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false; // 로그 셀은 읽기 전용으로 유지합니다.
        }
    };

    private final JTable table = new JTable(model);
    private final JScrollPane scrollPane = new JScrollPane(table);
    private final JCheckBox autoScrollBox = new JCheckBox("자동 스크롤", true);
    private final JLabel countLabel = UiTheme.label(getLogCountText(), true);
    private int lastMessageColumnWidth;

    /*
     * ConsolePanel (JPanel, BorderLayout)
     * ├─ NORTH: header (JPanel, GridBagLayout, 한 줄 배치)
     * │  ├─ title (JLabel): 터미널 아이콘과 제목
     * │  ├─ countLabel (JLabel): 로그 개수
     * │  ├─ autoScrollBox (JCheckBox): 자동 스크롤
     * │  └─ clear (JButton): 로그 비우기
     * │
     * └─ CENTER: scrollPane (JScrollPane)
     *    ├─ columnHeader (JViewport)
     *    │  └─ JTableHeader: 시각 / 출력 노드 / 메시지
     *    │
     *    ├─ viewport (JViewport)
     *    │  └─ table (JTable): 로그 목록
     *    │     ├─ 시각 열: 로그 수신 시각
     *    │     ├─ 출력 노드 열: 메시지를 표시한 디버그 노드
     *    │     └─ 메시지 열: 줄바꿈을 지원하는 메시지
     *    │
     *    └─ verticalScrollBar (JScrollBar): 세로 스크롤
     *
     */
    public ConsolePanel() {
        setLayout(new BorderLayout());
        setBorder(UiTheme.separatorBorder(1, 0, 0, 0));

        add(createHeader(), BorderLayout.NORTH);

        configureTable();

        configureCellRenderers();

        table.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) { updateRowHeightsIfWidthChanged(); }
        });

        table.getColumnModel().addColumnModelListener(new javax.swing.event.TableColumnModelListener() {
            public void columnMarginChanged(javax.swing.event.ChangeEvent e) { updateRowHeightsIfWidthChanged(); }
            public void columnAdded(javax.swing.event.TableColumnModelEvent e) { }
            public void columnRemoved(javax.swing.event.TableColumnModelEvent e) { }
            public void columnMoved(javax.swing.event.TableColumnModelEvent e) { }
            public void columnSelectionChanged(javax.swing.event.ListSelectionEvent e) { }
        });

        scrollPane.setBorder(null);

        scrollPane.setColumnHeaderView(table.getTableHeader());
        scrollPane.getVerticalScrollBar().setUnitIncrement(24);

        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createHeader() {
        JPanel header = UiTheme.header("디버깅", Codicons.icon("terminal"));
        // 체크박스·버튼이 들어가므로 기본 머리글보다 2px 높게 잡고 세로 여백을 줄입니다.
        header.setPreferredSize(new Dimension(0, 38));
        header.setBorder(UiTheme.headerBorder(5, 12));

        autoScrollBox.setOpaque(false);
        autoScrollBox.setFont(UiTheme.getFont());
        autoScrollBox.setForeground(UiTheme.getTextColor());

        JButton clear = UiTheme.button("로그 비우기");
        clear.setIcon(Codicons.icon("clear-all", 16));
        clear.addActionListener(e -> clear());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        actions.add(autoScrollBox);
        actions.add(clear);

        header.add(countLabel, BorderLayout.CENTER);
        header.add(actions, BorderLayout.EAST);

        return header;
    }

    private void configureTable() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setRowHeight(32);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setIntercellSpacing(new Dimension(0, 1));

        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(0, 30));
        table.getColumnModel().getColumn(0).setPreferredWidth(108);
        table.getColumnModel().getColumn(0).setMinWidth(90);
        table.getColumnModel().getColumn(0).setMaxWidth(125);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(1).setMinWidth(140);
        table.getColumnModel().getColumn(1).setMaxWidth(260);
        table.getColumnModel().getColumn(2).setPreferredWidth(900);
    }

    private void configureCellRenderers() {
        // 렌더러는 여러 셀에서 재사용되므로 선택 색·글꼴·테두리를 매 호출마다 설정해 이전 셀의 상태가 남지 않게 한다.
        DefaultTableCellRenderer metadata = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable view, Object value, boolean selected,
                    boolean focus, int row, int column) {
                super.getTableCellRendererComponent(view, value, selected, focus, row, column);
                setFont(view.getFont());
                setForeground(selected ? view.getSelectionForeground() : view.getForeground());
                setBackground(selected ? view.getSelectionBackground() : view.getBackground());
                setVerticalAlignment(TOP);
                setBorder(BorderFactory.createEmptyBorder(8, 12, 6, 10));

                return this;
            }
        };

        table.getColumnModel().getColumn(0).setCellRenderer(metadata);
        table.getColumnModel().getColumn(1).setCellRenderer(metadata);
        table.getColumnModel().getColumn(2).setCellRenderer((view, value, selected, focus, row, column) -> {
            JTextArea area = messageArea(String.valueOf(value));
            area.setBackground(selected ? view.getSelectionBackground() : view.getBackground());
            area.setForeground(selected ? view.getSelectionForeground() : view.getForeground());
            return area;
        });
    }

    // 로그 메세지가 길어지면 크기 조절하기 위해.
    private JTextArea messageArea(String text) {
        JTextArea area = new JTextArea(text);

        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);

        area.setFont(UiTheme.getFont());
        area.setForeground(UiTheme.getTextColor());
        area.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));

        return area;
    }

    private void updateRowHeightsIfWidthChanged() {
        int width = table.getColumnModel().getColumn(2).getWidth();
        if (width == lastMessageColumnWidth) return;

        lastMessageColumnWidth = width;
        for (int i = 0; i < model.getRowCount(); i++) {
            updateRowHeight(i);
        }
    }

    private void updateRowHeight(int row) {
        JTextArea measure = messageArea(getLogText(row));

        measure.setSize(Math.max(80, table.getColumnModel().getColumn(2).getWidth()), Short.MAX_VALUE);
        int height = Math.max(32, measure.getPreferredSize().height + 1);

        if (table.getRowHeight(row) != height) table.setRowHeight(row, height);
    }

    private boolean isScrolledToBottom() {
        JScrollBar bar = scrollPane.getVerticalScrollBar();

        // 4px 정도 오차 허용
        return model.getRowCount() == 0 || bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 4;
    }

    private String getLogCountText() {
        return String.format("%3d개", model.getRowCount());
    }

    public void append(String source, String text) {
        LogEntry entry = new LogEntry(TIME.format(LocalTime.now()), source, text);

        Runnable append = () -> {
            boolean wasScrolledToBottom = isScrolledToBottom();
            int row = model.getRowCount();

            model.addRow(new Object[] {entry.time(), entry.source(), entry.text()});
            updateRowHeight(row); // 새 행 하나의 높이만 계산합니다.
            countLabel.setText(getLogCountText());

            if (autoScrollBox.isSelected() && wasScrolledToBottom) SwingUtilities.invokeLater(() -> {
                if (model.getRowCount() > 0) table.scrollRectToVisible(table.getCellRect(model.getRowCount() - 1, 0, true));
            });
        };

        if (SwingUtilities.isEventDispatchThread()) {
            append.run();
        }
        else {
            SwingUtilities.invokeLater(append);
        }
    }

    public void clear() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::clear);

            return;
        }

        model.setRowCount(0);
        countLabel.setText(getLogCountText());
    }

    public String getLogText(int index) {
        return (String) model.getValueAt(index, 2);
    }

    private record LogEntry(String time, String source, String text) {
    }
}
