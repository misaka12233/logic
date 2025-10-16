package logic;

import javax.swing.*;
import javax.swing.tree.*;

public class SwingTreeUtil {
    // 递归保存展开状态
    public static void saveExpandState(logic.LogicNode ln, DefaultMutableTreeNode tn, JTree tree) {
        if (ln == null || tn == null) return;
        TreePath path = new TreePath(tn.getPath());
        ln.expanded = tree.isExpanded(path);
        for (int i = 0; i < ln.children.size(); i++) {
            if (tn.getChildCount() > i)
                saveExpandState(ln.children.get(i), (DefaultMutableTreeNode) tn.getChildAt(i), tree);
        }
    }
    // 递归恢复展开状态
    public static void restoreExpandState(logic.LogicNode ln, DefaultMutableTreeNode tn, JTree tree) {
        if (ln == null || tn == null) return;
        TreePath path = new TreePath(tn.getPath());
        if (ln.expanded) tree.expandPath(path); else tree.collapsePath(path);
        for (int i = 0; i < ln.children.size(); i++) {
            if (tn.getChildCount() > i)
                restoreExpandState(ln.children.get(i), (DefaultMutableTreeNode) tn.getChildAt(i), tree);
        }
    }
    // 构建Swing树
    public static void buildSwingTree(logic.LogicNode node, DefaultMutableTreeNode swingNode) {
        swingNode.setUserObject(node.toString());
        swingNode.removeAllChildren();
        for (logic.LogicNode child : node.children) {
            DefaultMutableTreeNode c = new DefaultMutableTreeNode(child.toString());
            buildSwingTree(child, c);
            swingNode.add(c);
        }
    }
}
