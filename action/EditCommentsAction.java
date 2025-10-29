package action;

import logic.*;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

/**
 * 编辑所选节点的注释（支持多段，空行分段）。
 * 修改前会保存撤销快照（UndoManager.saveSnapshot），修改后会重建 Swing 树并恢复 UI 状态。
 */
public class EditCommentsAction implements ActionListener {
    private final JFrame frame;
    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private final LogicNode[] logicRoot;
    private final LogicGraphPanel graphPanel;
    private final JLabel status;
    private final Map<Integer,String> errorNodeMap;

    public EditCommentsAction(JFrame frame, JTree tree, DefaultMutableTreeNode root, LogicNode[] logicRoot, LogicGraphPanel graphPanel, JLabel status, Map<Integer,String> errorNodeMap) {
        this.frame = frame;
        this.tree = tree;
        this.root = root;
        this.logicRoot = logicRoot;
        this.graphPanel = graphPanel;
        this.status = status;
        this.errorNodeMap = errorNodeMap;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        TreePath path = tree.getSelectionPath();
        if (path == null || logicRoot[0] == null) {
            JOptionPane.showMessageDialog(frame, "请先选中要编辑注释的节点", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        DefaultMutableTreeNode sel = (DefaultMutableTreeNode) path.getLastPathComponent();
        LogicNode ln = TreeHelper.findNode(logicRoot[0], sel, root);
        if (ln == null) {
            JOptionPane.showMessageDialog(frame, "未找到选中节点对应的数据。", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 组合当前注释为多段文本（段落以空行分隔）
        String initial = "";
        if (ln.comments != null && !ln.comments.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i=0;i<ln.comments.size();i++) {
                if (i>0) sb.append("\n\n");
                sb.append(ln.comments.get(i));
            }
            initial = sb.toString();
        }

        JTextArea area = new JTextArea(initial, 12, 60);
        JScrollPane sp = new JScrollPane(area);
        int ok = JOptionPane.showConfirmDialog(frame, sp, "编辑注释（用空行分段）", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) return;

        String text = area.getText();
        // 按空行分段（保留每段内部换行）
        String[] parts = text.split("\\r?\\n\\r?\\n");
        java.util.List<String> newComments = new ArrayList<>();
        for (String p : parts) {
            String t = p.replaceAll("\\r\\n", "\n").trim();
            if (!t.isEmpty()) newComments.add(t);
        }

        // 保存撤销快照（包含 UI 状态）
        logic.UndoManager.saveSnapshot(logicRoot[0], tree, root);

        // 更新并刷新单节点显示
        ln.comments.clear();
        ln.comments.addAll(newComments);

        java.util.List<Integer> expandedIds = logic.SwingTreeUtil.collectExpandedIds(tree, root);
        Integer selectedId = logic.SwingTreeUtil.findSelectedNodeId(tree);
        logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
        ((DefaultTreeModel)tree.getModel()).reload();
        logic.SwingTreeUtil.applyUiState(tree, root, expandedIds, selectedId);
        graphPanel.setLogicRoot(logicRoot[0]);
        logic.LogicValidator.validateAllNodes(logicRoot[0]);
        logic.LogicUiUtil.updateErrorStatusBar(logicRoot[0], status, errorNodeMap);
    }
}
