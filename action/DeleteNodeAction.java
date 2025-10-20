
package action;
import logic.LogicGraphPanel;
import logic.LogicNode;
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

public class DeleteNodeAction implements ActionListener {
    private final JFrame frame;
    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private final LogicNode[] logicRoot;
    private final LogicGraphPanel graphPanel;
    private final Map<Integer, String> errorNodeMap;
    private final JLabel status;

    public DeleteNodeAction(JFrame frame, JTree tree, DefaultMutableTreeNode root, LogicNode[] logicRoot, LogicGraphPanel graphPanel, JLabel status, Map<Integer, String> errorNodeMap) {
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
        if (path==null || logicRoot[0]==null) return;
        DefaultMutableTreeNode sel = (DefaultMutableTreeNode)path.getLastPathComponent();
        if (sel==root) {
            JOptionPane.showMessageDialog(frame, "根节点不能删除。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        LogicNode parent = TreeHelper.findParent(logicRoot[0], sel, root);
        LogicNode ln = TreeHelper.findNode(logicRoot[0], sel, root);
        if (ln==null) {
            JOptionPane.showMessageDialog(frame, "未找到节点对应的数据，无法删除。", "错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(frame, "确定要删除该节点及其所有子节点吗？", "确认删除", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;
        logic.SwingTreeUtil.saveExpandState(logicRoot[0], root, tree);
        if (parent!=null)
            parent.children.remove(ln);
        else
            logicRoot[0].children.remove(ln);
        logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
        ((DefaultTreeModel)tree.getModel()).reload();
        logic.SwingTreeUtil.restoreExpandState(logicRoot[0], root, tree);
        graphPanel.setLogicRoot(logicRoot[0]);
        // 实时校验
    LogicValidator.validateAllNodes(logicRoot[0]);
    LogicUiUtil.updateErrorStatusBar(logicRoot[0], status, errorNodeMap);
    }
}
