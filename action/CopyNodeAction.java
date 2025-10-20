
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
import java.util.*;

public class CopyNodeAction implements ActionListener {
    private final JFrame frame;
    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private final LogicNode[] logicRoot;
    private final LogicGraphPanel graphPanel;
    private final Map<Integer, String> errorNodeMap;
    private final JLabel status;
    private final int[] nodeIdCounter;

    public CopyNodeAction(JFrame frame, JTree tree, DefaultMutableTreeNode root, LogicNode[] logicRoot, int[] nodeIdCounter, LogicGraphPanel graphPanel, JLabel status, Map<Integer, String> errorNodeMap) {
        this.frame = frame;
        this.tree = tree;
        this.root = root;
        this.logicRoot = logicRoot;
        this.nodeIdCounter = nodeIdCounter;
        this.graphPanel = graphPanel;
        this.status = status;
        this.errorNodeMap = errorNodeMap;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        TreePath selectedPath = tree.getSelectionPath();
        if (selectedPath == null || logicRoot[0] == null) {
            JOptionPane.showMessageDialog(frame, "请先选中要复制的节点", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        DefaultMutableTreeNode sel = (DefaultMutableTreeNode)selectedPath.getLastPathComponent();
        LogicNode toCopy = TreeHelper.findNode(logicRoot[0], sel, root);
        if (toCopy == null) return;
        java.util.List<LogicNode> allNodes = new java.util.ArrayList<>();
        java.util.function.BiConsumer<LogicNode, DefaultMutableTreeNode> collect = new java.util.function.BiConsumer<LogicNode, DefaultMutableTreeNode>() {
            public void accept(LogicNode n, DefaultMutableTreeNode t) {
                allNodes.add(n);
                for (int i=0;i<n.children.size();i++) accept(n.children.get(i), (DefaultMutableTreeNode)t.getChildAt(i));
            }
        };
        collect.accept(logicRoot[0], root);
        LogicNode parent = (LogicNode)JOptionPane.showInputDialog(
            frame,
            "选择粘贴目标父节点:",
            "选择父节点",
            JOptionPane.PLAIN_MESSAGE,
            null,
            allNodes.toArray(),
            allNodes.get(0)
        );
        if (parent == null) return;
        LogicNode copy = deepCopyNode(toCopy);
        parent.children.add(copy);
        logic.SwingTreeUtil.saveExpandState(logicRoot[0], root, tree);
        logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
        ((DefaultTreeModel)tree.getModel()).reload();
        logic.SwingTreeUtil.restoreExpandState(logicRoot[0], root, tree);
        graphPanel.setLogicRoot(logicRoot[0]);
        LogicValidator.validateAllNodes(logicRoot[0]);
        LogicUiUtil.updateErrorStatusBar(logicRoot[0], status, errorNodeMap);
    }

    private LogicNode deepCopyNode(LogicNode node) {
        LogicNode n = new LogicNode(node.type, nodeIdCounter[0]++);
        n.params.putAll(node.params);
        for (java.util.Map<String,String> p : node.paramList) n.paramList.add(new java.util.LinkedHashMap<>(p));
        n.filter.putAll(node.filter);
        for (java.util.Map<String,String> p : node.filterParamList) n.filterParamList.add(new java.util.LinkedHashMap<>(p));
        for (LogicNode c : node.children) n.children.add(deepCopyNode(c));
        return n;
    }
}
