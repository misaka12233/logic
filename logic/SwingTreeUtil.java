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
    // 检查 Swing 树节点的子孙中是否存在错误节点（依据 LogicValidator.errorNodeMap）
    static boolean swingSubtreeHasError(DefaultMutableTreeNode swingNode) {
        for (int i = 0; i < swingNode.getChildCount(); i++) {
            javax.swing.tree.TreeNode tn = swingNode.getChildAt(i);
            if (!(tn instanceof DefaultMutableTreeNode)) continue;
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) tn;
            Object uo = child.getUserObject();
            if (uo != null) {
                String s = uo.toString();
                if (s.startsWith("[")) {
                    int idx = s.indexOf("]");
                    if (idx > 1) {
                        try {
                            int cid = Integer.parseInt(s.substring(1, idx));
                            if (logic.LogicValidator.errorNodeMap.containsKey(cid)) return true;
                        } catch (Exception ex) {
                        }
                    }
                }
            }
            if (swingSubtreeHasError(child)) return true;
        }
        return false;
    }
}
