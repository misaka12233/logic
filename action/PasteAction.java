package action;

import logic.*;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class PasteAction implements ActionListener {
    private final JFrame frame;
    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private final LogicNode[] logicRoot;
    private final int[] nodeIdCounter;
    private final LogicGraphPanel graphPanel;
    private final JLabel status;
    private final java.util.Map<Integer,String> errorNodeMap;

    public PasteAction(JFrame frame, JTree tree, DefaultMutableTreeNode root, LogicNode[] logicRoot, int[] nodeIdCounter, LogicGraphPanel graphPanel, JLabel status, java.util.Map<Integer,String> errorNodeMap) {
        this.frame = frame; this.tree = tree; this.root = root; this.logicRoot = logicRoot; this.nodeIdCounter = nodeIdCounter; this.graphPanel = graphPanel; this.status = status; this.errorNodeMap = errorNodeMap;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (logic.CopyBuffer.template == null) {
            JOptionPane.showMessageDialog(frame, "缓冲区为空，请先复制一个节点或子树。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        // 粘贴为当前选中节点的子节点
        TreePath selPath = tree.getSelectionPath();
        if (selPath == null) {
            JOptionPane.showMessageDialog(frame, "请先选中要粘贴到的父节点。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        DefaultMutableTreeNode sel = (DefaultMutableTreeNode) selPath.getLastPathComponent();
        LogicNode parent = TreeHelper.findNode(logicRoot[0], sel, root);
        if (parent == null) {
            JOptionPane.showMessageDialog(frame, "未找到选中节点对应的数据。", "错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // 实例化缓冲区模板（分配新的 nodeId）并添加到目标
        LogicNode inst = NodeCopyUtil.instantiateFromTemplate(logic.CopyBuffer.template, nodeIdCounter);
        // 保存快照以支持撤销
        logic.UndoManager.saveSnapshot(logicRoot[0], tree, root);
        // 插入为第一个子节点
        parent.children.add(0, inst);
        java.util.List<Integer> expandedIds = logic.SwingTreeUtil.collectExpandedIds(tree, root);
        Integer selectedId = logic.SwingTreeUtil.findSelectedNodeId(tree);
        logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
        ((javax.swing.tree.DefaultTreeModel)tree.getModel()).reload();
        logic.SwingTreeUtil.applyUiState(tree, root, expandedIds, selectedId);
        graphPanel.setLogicRoot(logicRoot[0]);
        logic.LogicValidator.validateAllNodes(logicRoot[0]);
        logic.LogicUiUtil.updateErrorStatusBar(logicRoot[0], status, errorNodeMap);
        // 提示用户粘贴结果
        try {
            JOptionPane.showMessageDialog(frame, "已粘贴到: " + parent.toString(), "粘贴", JOptionPane.INFORMATION_MESSAGE);
        } catch (Throwable t) {
            // 忽略对话框异常以免影响主要流程
        }
    }
}
