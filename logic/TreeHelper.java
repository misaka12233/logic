package logic;

import javax.swing.tree.*;
import java.util.*;

public class TreeHelper {
    // 收集所有可选目标节点（排除自身、子孙节点，可选排除祖先节点）
    public static void collectNodes(DefaultMutableTreeNode cur, DefaultMutableTreeNode exclude, List<DefaultMutableTreeNode> out, boolean excludeAncestor) {
        if (cur==exclude) return;
        // 排除子孙节点
        boolean isDescendant = false;
        TreeNode[] excludePath = exclude.getPath();
        TreeNode[] curPath = cur.getPath();
        if (curPath.length>=excludePath.length) {
            isDescendant = true;
            for (int i=0;i<excludePath.length;i++) {
                if (curPath[i]!=excludePath[i]) { isDescendant=false; break; }
            }
        } else isDescendant=false;
        // 可选排除祖先节点
        boolean isAncestor = false;
        if (excludeAncestor && excludePath.length>=curPath.length) {
            isAncestor = true;
            for (int i=0;i<curPath.length;i++) {
                if (curPath[i]!=excludePath[i]) { isAncestor=false; break; }
            }
        } else isAncestor=false;
        if (!isDescendant && !isAncestor) out.add(cur);
        for (int i=0;i<cur.getChildCount();i++) {
            collectNodes((DefaultMutableTreeNode)cur.getChildAt(i), exclude, out, excludeAncestor);
        }
    }
    // 查找节点
    public static LogicNode findNode(LogicNode logic, DefaultMutableTreeNode swing, DefaultMutableTreeNode swingRoot) {
        // 优先直接使用 Swing 节点绑定的 LogicNode（buildSwingTree 时将 LogicNode 存入 userObject）
        if (swing == null) return null;
        if (swing == swingRoot) return logic;
        Object uo = swing.getUserObject();
        if (uo instanceof LogicNode) return (LogicNode)uo;
        // 兼容老逻辑：回退到基于路径标签的查找（若 userObject 非 LogicNode）
        TreeNode[] path = swing.getPath();
        LogicNode cur = logic;
        for (int i=1;i<path.length;i++) {
            String label = path[i].toString();
            Optional<LogicNode> next = cur.children.stream().filter(n->n.toString().equals(label)).findFirst();
            if (next.isEmpty()) return null;
            cur = next.get();
        }
        return cur;
    }
    // 查找父节点
    public static LogicNode findParent(LogicNode logic, DefaultMutableTreeNode swing, DefaultMutableTreeNode swingRoot) {
        if (swing == null) return null;
        DefaultMutableTreeNode parentSwing = (DefaultMutableTreeNode)swing.getParent();
        if (parentSwing == null || parentSwing == swingRoot) return logic;
        Object uo = parentSwing.getUserObject();
        if (uo instanceof LogicNode) return (LogicNode)uo;
        // 兼容回退：基于路径标签查找父节点
        TreeNode[] path = swing.getPath();
        if (path.length<=2) return null;
        LogicNode cur = logic;
        for (int i=1;i<path.length-1;i++) {
            String label = path[i].toString();
            Optional<LogicNode> next = cur.children.stream().filter(n->n.toString().equals(label)).findFirst();
            if (next.isEmpty()) return null;
            cur = next.get();
        }
        return cur;
    }
}
