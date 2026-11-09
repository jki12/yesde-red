package content;
import ui.board.BoardPanel;

import ui.theme.Codicons;
import com.formdev.flatlaf.extras.FlatSVGIcon;

import ui.theme.Listeners;
import ui.theme.UiTheme;
import ui.board.NodePanel;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NodeListPanel extends JPanel {
    private final DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    private final DefaultTreeModel model = new DefaultTreeModel(root);
    private final JTree tree = new JTree(model);
    private final JTextField search = new JTextField();

    private BoardPanel board;
    private List<NodePanel> allNodes = new ArrayList<>();

    // 프로그램이 트리 선택을 맞추는 중에는 트리 이벤트를 보드 선택으로 되돌려 보내지 않는 재진입 방지 플래그입니다.
    private boolean syncingSelection;

    public NodeListPanel() {
        setLayout(new BorderLayout());
        setBorder(UiTheme.separatorBorder(0, 0, 0, 1)); // 오른쪽 경계로 캔버스와 목록을 구분합니다.

        add(createHeader(), BorderLayout.NORTH);
        configureTree();

        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);
        setPreferredSize(new Dimension(228, 400));
        setMinimumSize(new Dimension(180, 200));

        registerListeners();
    }

    private JPanel createHeader() {
        JPanel heading = new JPanel(new BorderLayout());
        heading.add(UiTheme.header("노드 목록", Codicons.icon("list-tree")), BorderLayout.NORTH);
        JPanel searchBox = new JPanel(new BorderLayout(0, 5));
        searchBox.setBackground(UiTheme.getPanelBackgroundColor());
        searchBox.setBorder(UiTheme.padding(12));
        searchBox.add(UiTheme.label("이름 또는 타입 검색", true), BorderLayout.NORTH);
        search.setName("nodeSearch");
        search.putClientProperty(com.formdev.flatlaf.FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                Codicons.icon("search", 16));
        searchBox.add(search, BorderLayout.CENTER);
        heading.add(searchBox, BorderLayout.SOUTH);
        return heading;
    }

    private void configureTree() {
        tree.setName("nodeListTree");
        tree.setRowHeight(32); // 16px 아이콘에 맞춘 행 높이
        tree.setBorder(BorderFactory.createEmptyBorder(5, 4, 5, 0));
        tree.setShowsRootHandles(false);
        tree.setCellRenderer(new Renderer());
        tree.getSelectionModel().setSelectionMode(javax.swing.tree.TreeSelectionModel.SINGLE_TREE_SELECTION);
    }

    private void registerListeners() {
        tree.addTreeSelectionListener(e -> {
            // 루트를 고르거나 선택이 없으면 보드 선택도 비웁니다.
            if (syncingSelection || board == null) return;
            Object selected = tree.getLastSelectedPathComponent();
            Object value = selected instanceof DefaultMutableTreeNode
                    ? ((DefaultMutableTreeNode) selected).getUserObject() : null;
            board.setSelectedNode(value instanceof NodePanel ? (NodePanel) value : null);
        });
        // 검색은 트리에 보일 행만 다시 만듭니다. 캔버스의 카드나 연결 모델은 제거하지 않습니다.
        Listeners.onTextChange(search, this::rebuildTree);
    }

    public void showBoard(BoardPanel board) {
        // 보드가 바뀌거나 실제 카드 목록이 달라진 경우만 트리를 다시 만듭니다.
        // 카드 개수가 같고 선택만 바뀌면 선택 강조만 맞춰 불필요한 모델 재구성을 줄입니다.
        List<NodePanel> nodes = board == null ? List.of() : board.getNodePanels();
        if (this.board != board || !allNodes.equals(nodes)) {
            this.board = board;
            rebuildTree();
        } else syncSelection();
    }

    private void rebuildTree() {
        // reload()/선택 복원도 TreeSelection 이벤트를 발생시킬 수 있으므로 재구성 전체를 syncingSelection으로 감쌉니다.
        syncingSelection = true;
        try {
            allNodes = board == null ? List.of() : board.getNodePanels();
            // allNodes는 필터 적용 전 전체 목록입니다. 검색 결과만 보관하면 다음 선택 갱신 때 매번 변경으로 오인할 수 있습니다.
            root.removeAllChildren();
            String query = search.getText().strip().toLowerCase(Locale.ROOT);
            for (NodePanel view : allNodes) {
                String text = view.getNode().getDisplayName() + " " + view.getNode().getType()
                        + " " + view.getNode().getId();
                if (text.toLowerCase(Locale.ROOT).contains(query)) root.add(new DefaultMutableTreeNode(view));
            }
            // 제목에 노드 수를 표시하고, 검색 중에는 "표시 수/전체 수"로 보여줍니다.
            root.setUserObject((board == null ? "워크플로" : board.getName())
                    + " (" + root.getChildCount() + (query.isEmpty() ? "" : "/" + allNodes.size()) + ")");
            model.reload();
            tree.expandRow(0);
            selectCurrentNode();
        } finally { syncingSelection = false; } // 예외가 발생해도 이후 사용자 선택을 계속 무시하는 상태로 남기지 않습니다.
    }

    private void syncSelection() {
        syncingSelection = true; // 목록 선택 → 보드 선택 → 목록 갱신의 순환 이벤트를 막습니다.
        try { selectCurrentNode(); }
        finally { syncingSelection = false; }
    }

    private void selectCurrentNode() {
        // 표시 문자열이 아니라 동일한 카드 인스턴스로 비교합니다. 같은 타입 노드가 여러 개 있어도 선택 대상을 구분합니다.
        NodePanel selected = board == null ? null : board.getSelectedNode();
        for (int i = 0; i < root.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) root.getChildAt(i);
            if (child.getUserObject() == selected) {
                TreePath path = new TreePath(child.getPath());
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
                return;
            }
        }
        // 검색으로 숨겨진 노드는 캔버스 선택을 유지하고 목록에서만 강조를 지웁니다.
        tree.clearSelection();
    }

    // 선택·비활성 색상은 기본 렌더러에 맡겨 글자와 배경 조합이 테마와 맞게 합니다.
    private static final class Renderer extends DefaultTreeCellRenderer {
        @Override public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                boolean expanded, boolean leaf, int row, boolean focus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focus);
            setFont(UIManager.getFont("Label.font"));
            Object item = ((DefaultMutableTreeNode) value).getUserObject();
            if (item instanceof NodePanel) {
                NodePanel node = (NodePanel) item;
                String id = node.getNode().getId();
                setText(node.getNode().getDisplayName() + "  " + id);
                // 노드 아이콘을 목록용 16px로 줄여 씁니다.
                Icon icon = node.getNode().getIcon();
                setIcon(icon instanceof FlatSVGIcon svg ? svg.derive(16, 16) : icon);
                setIconTextGap(8);
            } else {
                setIcon(null);
                setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD));
            }
            return this;
        }
    }
}
