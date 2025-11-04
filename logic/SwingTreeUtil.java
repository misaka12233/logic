package logic;

import javax.swing.*;
import javax.swing.tree.*;

public class SwingTreeUtil {

    /**
     * 收集当前树上所有展开节点的 LogicNode id 列表（按深度优先遍历）。
     */
    public static java.util.List<Integer> collectExpandedIds(JTree tree, DefaultMutableTreeNode swingRoot) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        if (tree == null || swingRoot == null) return out;
        collectExpandedIdsImpl(tree, swingRoot, out);
        return out;
    }
    private static void collectExpandedIdsImpl(JTree tree, DefaultMutableTreeNode node, java.util.List<Integer> out) {
        TreePath path = new TreePath(node.getPath());
        if (tree.isExpanded(path)) {
            Object uo = node.getUserObject();
            if (uo instanceof logic.LogicNode) {
                out.add(((logic.LogicNode)uo).nodeId);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            javax.swing.tree.TreeNode tn = node.getChildAt(i);
            if (!(tn instanceof DefaultMutableTreeNode)) continue;
            collectExpandedIdsImpl(tree, (DefaultMutableTreeNode)tn, out);
        }
    }

    /**
     * 找到当前选中节点的 LogicNode id（若无选中则返回 null）。
     */
    public static Integer findSelectedNodeId(JTree tree) {
        if (tree == null) return null;
        TreePath sel = tree.getSelectionPath();
        if (sel == null) return null;
        Object last = sel.getLastPathComponent();
        if (!(last instanceof DefaultMutableTreeNode)) return null;
        Object uo = ((DefaultMutableTreeNode)last).getUserObject();
        if (uo instanceof logic.LogicNode) return ((logic.LogicNode)uo).nodeId;
        return null;
    }

    /**
     * 根据 expandedIds 列表展开对应节点，并设置选中节点（若存在）。
     * best-effort：若某 id 对应的节点不存在则忽略。
     */
    public static void applyUiState(JTree tree, DefaultMutableTreeNode swingRoot, java.util.List<Integer> expandedIds, Integer selectedId) {
        if (tree == null || swingRoot == null) return;
        java.util.Set<Integer> set = new java.util.HashSet<>();
        if (expandedIds != null) set.addAll(expandedIds);
        // 深度遍历展开匹配 id 的节点
        applyUiStateImpl(tree, swingRoot, set);
        // 选择
        if (selectedId != null) {
            DefaultMutableTreeNode found = findSwingNodeById(swingRoot, selectedId);
            if (found != null) {
                TreePath p = new TreePath(found.getPath());
                tree.setSelectionPath(p);
                // 确保选中节点可见
                tree.scrollPathToVisible(p);
            }
        }
    }
    private static void applyUiStateImpl(JTree tree, DefaultMutableTreeNode node, java.util.Set<Integer> set) {
        Object uo = node.getUserObject();
        if (uo instanceof logic.LogicNode) {
            int id = ((logic.LogicNode)uo).nodeId;
            if (set.contains(id)) tree.expandPath(new TreePath(node.getPath()));
        }
        for (int i=0;i<node.getChildCount();i++) {
            javax.swing.tree.TreeNode tn = node.getChildAt(i);
            if (tn instanceof DefaultMutableTreeNode) applyUiStateImpl(tree, (DefaultMutableTreeNode)tn, set);
        }
    }

    private static DefaultMutableTreeNode findSwingNodeById(DefaultMutableTreeNode root, int id) {
        Object uo = root.getUserObject();
        if (uo instanceof logic.LogicNode) {
            if (((logic.LogicNode)uo).nodeId == id) return root;
        }
        for (int i=0;i<root.getChildCount();i++) {
            javax.swing.tree.TreeNode tn = root.getChildAt(i);
            if (!(tn instanceof DefaultMutableTreeNode)) continue;
            DefaultMutableTreeNode found = findSwingNodeById((DefaultMutableTreeNode)tn, id);
            if (found != null) return found;
        }
        return null;
    }

    // 构建Swing树
    public static void buildSwingTree(logic.LogicNode node, DefaultMutableTreeNode swingNode) {
        // 在构建 Swing 树前为节点分配 rule/local 编号（仅在根 rules 节点）
        if (node.type == logic.LogicNode.NodeType.RULES) logic.LogicUiUtil.assignRuleAndLocalIds(node);
        swingNode.setUserObject(node);
        swingNode.removeAllChildren();
        for (logic.LogicNode child : node.children) {
            DefaultMutableTreeNode c = new DefaultMutableTreeNode(child);
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
            if (uo instanceof logic.LogicNode) {
                int cid = ((logic.LogicNode)uo).nodeId;
                if (logic.LogicValidator.errorNodeMap.containsKey(cid)) return true;
            }
            if (swingSubtreeHasError(child)) return true;
        }
        return false;
    }
}
