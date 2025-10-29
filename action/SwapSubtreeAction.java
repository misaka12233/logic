package action;

import logic.*;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public class SwapSubtreeAction implements ActionListener {
    private final JFrame frame;
    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private final LogicNode[] logicRoot;
    private final LogicGraphPanel graphPanel;
    private final JLabel status;
    private final Map<Integer, String> errorNodeMap;

    public SwapSubtreeAction(JFrame frame, JTree tree, DefaultMutableTreeNode root, LogicNode[] logicRoot, LogicGraphPanel graphPanel, JLabel status, Map<Integer, String> errorNodeMap) {
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
        javax.swing.tree.TreePath fromPath = tree.getSelectionPath();
        if (fromPath==null || logicRoot[0]==null) return;
        DefaultMutableTreeNode fromSel = (DefaultMutableTreeNode)fromPath.getLastPathComponent();
        if (fromSel==root) {
            JOptionPane.showMessageDialog(frame, "根节点不能参与整体交换。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        LogicNode fromParent = TreeHelper.findParent(logicRoot[0], fromSel, root);
        LogicNode fromNode = TreeHelper.findNode(logicRoot[0], fromSel, root);
        if (fromNode==null) {
            JOptionPane.showMessageDialog(frame, "未找到选中节点对应的数据。", "错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        java.util.List<DefaultMutableTreeNode> candidates = new java.util.ArrayList<>();
        TreeHelper.collectNodes(root, fromSel, candidates, true);
        candidates.remove(fromSel);
        if (candidates.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "没有可用于整体交换的其他节点。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        DefaultMutableTreeNode[] arr = candidates.toArray(new DefaultMutableTreeNode[0]);
        DefaultMutableTreeNode toSel = (DefaultMutableTreeNode)JOptionPane.showInputDialog(frame, "选择要整体交换的节点:", "整体交换", JOptionPane.PLAIN_MESSAGE, null, arr, arr[0]);
        if (toSel==null) return;
        LogicNode toParent = TreeHelper.findParent(logicRoot[0], toSel, root);
        LogicNode toNode = TreeHelper.findNode(logicRoot[0], toSel, root);
        if (toNode==null) {
            JOptionPane.showMessageDialog(frame, "未找到目标节点对应的数据。", "错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (fromParent==null || toParent==null) {
            JOptionPane.showMessageDialog(frame, "不能交换根节点。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int fromIdx = fromParent.children.indexOf(fromNode);
        int toIdx = toParent.children.indexOf(toNode);
        if (fromIdx==-1 || toIdx==-1) {
            JOptionPane.showMessageDialog(frame, "节点索引异常，无法交换。", "错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
    // 保存快照以支持撤销（包含 UI 状态）
    logic.UndoManager.saveSnapshot(logicRoot[0], tree, root);
    fromParent.children.set(fromIdx, toNode);
    toParent.children.set(toIdx, fromNode);
        java.util.List<Integer> expandedIds = logic.SwingTreeUtil.collectExpandedIds(tree, root);
        Integer selectedId = logic.SwingTreeUtil.findSelectedNodeId(tree);
        logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
        ((javax.swing.tree.DefaultTreeModel)tree.getModel()).reload();
        logic.SwingTreeUtil.applyUiState(tree, root, expandedIds, selectedId);
        graphPanel.setLogicRoot(logicRoot[0]);
        logic.LogicValidator.validateAllNodes(logicRoot[0]);
        logic.LogicUiUtil.updateErrorStatusBar(logicRoot[0], status, errorNodeMap);
    }
}
