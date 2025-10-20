package action;
import logic.LogicGraphPanel;
import logic.LogicNode;
import logic.SwingTreeUtil;
import logic.TreeHelper;
import logic.LogicUiUtil;
import logic.LogicValidator;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;

public class RenameVarAction implements ActionListener {
    private final JFrame frame;
    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private final LogicNode[] logicRoot;
    private final LogicGraphPanel graphPanel;
    private final Map<Integer, String> errorNodeMap;
    private final JLabel status;

    public RenameVarAction(JFrame frame, JTree tree, DefaultMutableTreeNode root, LogicNode[] logicRoot, LogicGraphPanel graphPanel, JLabel status, Map<Integer, String> errorNodeMap) {
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
        TreePath path = tree.getSelectionPath();
        if (path == null || logicRoot[0] == null) return;
        DefaultMutableTreeNode sel = (DefaultMutableTreeNode)path.getLastPathComponent();
        LogicNode ln = TreeHelper.findNode(logicRoot[0], sel, root);
        if (ln == null) return;
        if (ln.type != LogicNode.NodeType.FORALL && ln.type != LogicNode.NodeType.EXISTS) {
            JOptionPane.showMessageDialog(frame, "请选择 forall 或 exists 节点进行重命名", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String oldVar = ln.params.get("var");
        if (oldVar == null || oldVar.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "当前节点未定义变量(var)", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String newVar = JOptionPane.showInputDialog(frame, "将变量 '" + oldVar + "' 重命名为: ");
        if (newVar == null || newVar.isEmpty()) return;
        // 冲突检测：收集祖先作用域中已定义的变量
        java.util.Set<String> ancestorVars = new java.util.HashSet<>();
        DefaultMutableTreeNode parentSwing = (DefaultMutableTreeNode) sel.getParent();
        while (parentSwing != null) {
            LogicNode pnode = TreeHelper.findNode(logicRoot[0], parentSwing, root);
            if (pnode != null && (pnode.type == LogicNode.NodeType.FORALL || pnode.type == LogicNode.NodeType.EXISTS)) {
                String pv = pnode.params.get("var");
                if (pv != null && !pv.isEmpty()) ancestorVars.add(pv);
            }
            parentSwing = (DefaultMutableTreeNode) parentSwing.getParent();
        }
        // 检查子孙中是否也定义了相同名字
        java.util.List<String> descendantDefs = new java.util.ArrayList<>();
        collectDescendantDefs(ln, newVar, descendantDefs);

        if (ancestorVars.contains(newVar) || !descendantDefs.isEmpty()) {
            StringBuilder warn = new StringBuilder();
            warn.append("检测到与新名字 '").append(newVar).append("' 冲突:\n");
            if (ancestorVars.contains(newVar)) warn.append("- 在祖先作用域中已定义\n");
            if (!descendantDefs.isEmpty()) {
                warn.append("- 在子孙节点中定义：\n");
                for (String info : descendantDefs) warn.append("    ").append(info).append("\n");
            }
            warn.append("继续将导致重复定义或覆盖子孙定义，是否继续？");
            int ok = JOptionPane.showConfirmDialog(frame, warn.toString(), "冲突检测", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (ok != JOptionPane.YES_OPTION) return;
        }
        // 在当前节点修改定义
        ln.params.put("var", newVar);
        // 递归子树把所有使用 oldVar 的位置替换为 newVar
        renameVarInSubtree(ln, oldVar, newVar);

        // 重新构建树与图并校验
        SwingTreeUtil.saveExpandState(logicRoot[0], root, tree);
        SwingTreeUtil.buildSwingTree(logicRoot[0], root);
        ((DefaultTreeModel)tree.getModel()).reload();
        SwingTreeUtil.restoreExpandState(logicRoot[0], root, tree);
        graphPanel.setLogicRoot(logicRoot[0]);
        LogicValidator.validateAllNodes(logicRoot[0]);
        LogicUiUtil.updateErrorStatusBar(logicRoot[0], status, errorNodeMap);
    }

    private void renameVarInSubtree(LogicNode node, String oldVar, String newVar) {
        // paramList
        if (node.paramList != null) {
            for (java.util.Map<String,String> p : node.paramList) {
                String v = p.get("var");
                if (v != null && v.equals(oldVar)) p.put("var", newVar);
            }
        }
        // filterParamList
        if (node.filterParamList != null) {
            for (java.util.Map<String,String> p : node.filterParamList) {
                String v = p.get("var");
                if (v != null && v.equals(oldVar)) p.put("var", newVar);
            }
        }
        // children
        for (LogicNode c : node.children) renameVarInSubtree(c, oldVar, newVar);
    }

    private void collectDescendantDefs(LogicNode node, String name, java.util.List<String> out) {
        for (LogicNode c : node.children) {
            if ((c.type == LogicNode.NodeType.FORALL || c.type == LogicNode.NodeType.EXISTS) && name.equals(c.params.get("var"))) {
                out.add(c.toString());
            }
            collectDescendantDefs(c, name, out);
        }
    }
}
