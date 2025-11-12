package action;

import logic.*;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CopySingleAction implements ActionListener {
    private final JFrame frame;
    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private final LogicNode[] logicRoot;
    private final LogicGraphPanel graphPanel;

    public CopySingleAction(JFrame frame, JTree tree, DefaultMutableTreeNode root, LogicNode[] logicRoot, LogicGraphPanel graphPanel) {
        this.frame = frame; this.tree = tree; this.root = root; this.logicRoot = logicRoot; this.graphPanel = graphPanel;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        TreePath selPath = tree.getSelectionPath();
        if (selPath == null || logicRoot[0] == null) { JOptionPane.showMessageDialog(frame, "请先选中要复制的节点", "提示", JOptionPane.INFORMATION_MESSAGE); return; }
        DefaultMutableTreeNode sel = (DefaultMutableTreeNode) selPath.getLastPathComponent();
        LogicNode ln = TreeHelper.findNode(logicRoot[0], sel, root);
        if (ln == null) { JOptionPane.showMessageDialog(frame, "未找到选中节点对应的数据。", "错误", JOptionPane.WARNING_MESSAGE); return; }
        // 将单节点模板写入缓冲区（不包含子节点）
        logic.CopyBuffer.template = NodeCopyUtil.makeTemplateCopy(ln, false);
        logic.CopyBuffer.isSubtree = false;
        java.util.List<Integer> expandedIds = logic.SwingTreeUtil.collectExpandedIds(tree, root);
        Integer selectedId = logic.SwingTreeUtil.findSelectedNodeId(tree);
        logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
        ((javax.swing.tree.DefaultTreeModel)tree.getModel()).reload();
        logic.SwingTreeUtil.applyUiState(tree, root, expandedIds, selectedId);
        graphPanel.setLogicRoot(logicRoot[0]);
        JOptionPane.showMessageDialog(frame, "已复制单节点到缓冲区。", "复制", JOptionPane.INFORMATION_MESSAGE);
    }
}
