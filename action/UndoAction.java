package action;

import logic.LogicNode;
import logic.LogicUiUtil;
import logic.LogicValidator;
import logic.SwingTreeUtil;
import logic.UndoManager;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;

public class UndoAction implements java.awt.event.ActionListener {
    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private final LogicNode[] logicRoot;
    private final int[] nodeIdCounter;
    private final logic.LogicGraphPanel graphPanel;
    private final JLabel status;

    public UndoAction(JTree tree, DefaultMutableTreeNode root, LogicNode[] logicRoot, int[] nodeIdCounter, logic.LogicGraphPanel graphPanel, JLabel status) {
        this.tree = tree;
        this.root = root;
        this.logicRoot = logicRoot;
        this.nodeIdCounter = nodeIdCounter;
        this.graphPanel = graphPanel;
        this.status = status;
    }

    @Override
    public void actionPerformed(java.awt.event.ActionEvent e) {
        try {
            LogicNode restored = UndoManager.restoreSnapshot();
            if (restored == null) {
                status.setText("无可撤销操作");
                return;
            }
            logicRoot[0] = restored;
            // reset nodeIdCounter to a safe value (max id + 1)
            int maxId = findMaxId(restored);
            nodeIdCounter[0] = maxId + 1;
            root.setUserObject(logicRoot[0].toString());
            root.removeAllChildren();
            SwingTreeUtil.buildSwingTree(logicRoot[0], root);
            ((DefaultTreeModel)tree.getModel()).reload();
            graphPanel.setLogicRoot(logicRoot[0]);
            LogicValidator.validateAllNodes(logicRoot[0]);
            LogicUiUtil.updateErrorStatusBar(logicRoot[0], status, logic.LogicValidator.errorNodeMap);
            // 恢复 UI 状态（展开/选中）
            java.util.Map<String,Object> ui = UndoManager.restoreUiState();
            java.util.List<Integer> expanded = new java.util.ArrayList<>();
            Object exo = ui.get("expanded");
            if (exo instanceof java.util.List<?>) {
                for (Object o : (java.util.List<?>)exo) {
                    if (o instanceof Number) expanded.add(((Number)o).intValue());
                }
            }
            Integer selVal = null;
            Object so = ui.get("selected");
            if (so instanceof Number) selVal = ((Number)so).intValue();
            final Integer selFinal = selVal;
            javax.swing.SwingUtilities.invokeLater(() -> {
                logic.SwingTreeUtil.applyUiState(tree, root, expanded, selFinal);
            });
            status.setText("已撤销到上一个快照");
            UndoManager.setUndoAvailableAndNotify(false);
        } catch (Exception ex) {
            status.setText("撤销失败: " + ex.getMessage());
        }
    }

    private int findMaxId(LogicNode node) {
        int m = node.nodeId;
        for (LogicNode c : node.children) m = Math.max(m, findMaxId(c));
        return m;
    }
}
