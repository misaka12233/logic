package action;

import logic.*;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

public class MoveNodeAction implements ActionListener {
    private final JFrame frame;
    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private final LogicNode[] logicRoot;
    private final LogicGraphPanel graphPanel;
    private final JLabel status;
    private final Map<Integer, String> errorNodeMap;

    public MoveNodeAction(JFrame frame, JTree tree, DefaultMutableTreeNode root, LogicNode[] logicRoot, LogicGraphPanel graphPanel, JLabel status, Map<Integer, String> errorNodeMap) {
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
        TreePath fromPath = tree.getSelectionPath();
        if (fromPath==null || logicRoot[0]==null) return;
        DefaultMutableTreeNode fromSel = (DefaultMutableTreeNode)fromPath.getLastPathComponent();
        if (fromSel==root) {
            JOptionPane.showMessageDialog(frame, "根节点不能移动。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        LogicNode fromParent = TreeHelper.findParent(logicRoot[0], fromSel, root);
        LogicNode fromNode = TreeHelper.findNode(logicRoot[0], fromSel, root);
        if (fromNode==null) {
            JOptionPane.showMessageDialog(frame, "未找到选中节点对应的数据。", "错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // 新增条件：当前节点 localId 必须不为 0
        if (fromNode.localId == 0) {
            JOptionPane.showMessageDialog(frame, "该节点不可移动", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        java.util.List<DefaultMutableTreeNode> candidates = new java.util.ArrayList<>();
        TreeHelper.collectNodes(root, fromSel, candidates, false);
        // 过滤：目标节点必须 localId != 0 且 ruleId 与当前节点相同
        java.util.Iterator<DefaultMutableTreeNode> it = candidates.iterator();
        while (it.hasNext()) {
            DefaultMutableTreeNode tn = it.next();
            LogicNode ln = TreeHelper.findNode(logicRoot[0], tn, root);
            if (ln == null || ln.localId == 0 || ln.ruleId != fromNode.ruleId) {
                it.remove();
            }
        }
        if (candidates.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "没有可用于移动的目标节点。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        DefaultMutableTreeNode[] arr = candidates.toArray(new DefaultMutableTreeNode[0]);
        DefaultMutableTreeNode toSel = (DefaultMutableTreeNode)JOptionPane.showInputDialog(frame, "选择目标父节点:", "移动到...", JOptionPane.PLAIN_MESSAGE, null, arr, arr[0]);
        if (toSel==null) return;
        LogicNode toNode = TreeHelper.findNode(logicRoot[0], toSel, root);
        if (toNode==null) {
            JOptionPane.showMessageDialog(frame, "未找到目标节点对应的数据。", "错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
    // 保存快照以支持撤销（包含 UI 状态）
    logic.UndoManager.saveSnapshot(logicRoot[0], tree, root);
        if (fromParent!=null) {
            fromParent.children.remove(fromNode);
        } else {
            logicRoot[0].children.remove(fromNode);
        }
        toNode.children.add(fromNode);
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
