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
        java.util.List<DefaultMutableTreeNode> candidates = new java.util.ArrayList<>();
        TreeHelper.collectNodes(root, fromSel, candidates, false);
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
    logic.SwingTreeUtil.saveExpandState(logicRoot[0], root, tree);
    // 保存快照以支持撤销（包含 UI 状态）
    logic.UndoManager.saveSnapshot(logicRoot[0], tree, root);
        if (fromParent!=null) {
            fromParent.children.remove(fromNode);
        } else {
            logicRoot[0].children.remove(fromNode);
        }
        toNode.children.add(fromNode);
        logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
        ((javax.swing.tree.DefaultTreeModel)tree.getModel()).reload();
        logic.SwingTreeUtil.restoreExpandState(logicRoot[0], root, tree);
        graphPanel.setLogicRoot(logicRoot[0]);
        logic.LogicValidator.validateAllNodes(logicRoot[0]);
        logic.LogicUiUtil.updateErrorStatusBar(logicRoot[0], status, errorNodeMap);
    }
}
