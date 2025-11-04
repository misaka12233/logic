package action;

import logic.*;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CopySubtreeAction implements ActionListener {
    private final JFrame frame;
    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private final LogicNode[] logicRoot;

    public CopySubtreeAction(JFrame frame, JTree tree, DefaultMutableTreeNode root, LogicNode[] logicRoot) {
        this.frame = frame; this.tree = tree; this.root = root; this.logicRoot = logicRoot;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        TreePath selPath = tree.getSelectionPath();
        if (selPath == null || logicRoot[0] == null) { JOptionPane.showMessageDialog(frame, "请先选中要复制的节点", "提示", JOptionPane.INFORMATION_MESSAGE); return; }
        DefaultMutableTreeNode sel = (DefaultMutableTreeNode) selPath.getLastPathComponent();
        LogicNode ln = TreeHelper.findNode(logicRoot[0], sel, root);
        if (ln == null) { JOptionPane.showMessageDialog(frame, "未找到选中节点对应的数据。", "错误", JOptionPane.WARNING_MESSAGE); return; }
        // 将子树模板写入缓冲区（包含子节点）
        logic.CopyBuffer.template = NodeCopyUtil.makeTemplateCopy(ln, true);
        logic.CopyBuffer.isSubtree = true;
        JOptionPane.showMessageDialog(frame, "已复制子树到缓冲区。", "复制", JOptionPane.INFORMATION_MESSAGE);
    }
}
