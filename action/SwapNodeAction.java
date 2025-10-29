package action;

import logic.*;
import javax.swing.*;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import javax.swing.tree.*;

public class SwapNodeAction implements ActionListener {
    private final JFrame frame;
    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private final LogicNode[] logicRoot;
    private final LogicGraphPanel graphPanel;
    private final JLabel status;
    private final Map<Integer, String> errorNodeMap;

    public SwapNodeAction(JFrame frame, JTree tree, DefaultMutableTreeNode root, LogicNode[] logicRoot, LogicGraphPanel graphPanel, JLabel status, Map<Integer, String> errorNodeMap) {
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
        TreePath fromPath = tree.getSelectionPath();
        if (fromPath==null || logicRoot[0]==null) return;
        DefaultMutableTreeNode fromSel = (DefaultMutableTreeNode)fromPath.getLastPathComponent();
        if (fromSel==root) {
            JOptionPane.showMessageDialog(frame, "根节点不能参与节点交换。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        LogicNode fromNode = TreeHelper.findNode(logicRoot[0], fromSel, root);
        if (fromNode==null) {
            JOptionPane.showMessageDialog(frame, "未找到选中节点对应的数据。", "错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        java.util.List<DefaultMutableTreeNode> candidates = new java.util.ArrayList<>();
        Enumeration<TreeNode> en = root.depthFirstEnumeration();
        while (en.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)en.nextElement();
            if (node != fromSel && node != root) candidates.add(node);
        }
        if (candidates.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "没有可用于交换的其他节点。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        DefaultMutableTreeNode[] arr = candidates.toArray(new DefaultMutableTreeNode[0]);
        DefaultMutableTreeNode toSel = (DefaultMutableTreeNode)JOptionPane.showInputDialog(frame, "选择要节点交换的节点:", "节点交换", JOptionPane.PLAIN_MESSAGE, null, arr, arr[0]);
        if (toSel==null) return;
        LogicNode toNode = TreeHelper.findNode(logicRoot[0], toSel, root);
        if (toNode==null) {
            JOptionPane.showMessageDialog(frame, "未找到目标节点对应的数据。", "错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // 保存快照以支持撤销（包含 UI 状态）
        logic.UndoManager.saveSnapshot(logicRoot[0], tree, root);
        // 交换 type
        LogicNode.NodeType tmpType = fromNode.type;
        fromNode.type = toNode.type;
        toNode.type = tmpType;
        // 交换 params
        java.util.Map<String,String> tmpParams = new java.util.LinkedHashMap<>(fromNode.params);
        fromNode.params.clear(); fromNode.params.putAll(toNode.params);
        toNode.params.clear(); toNode.params.putAll(tmpParams);
        // 交换 paramList
        java.util.List<java.util.Map<String,String>> tmpParamList = new java.util.ArrayList<>(fromNode.paramList);
        fromNode.paramList.clear(); fromNode.paramList.addAll(toNode.paramList);
        toNode.paramList.clear(); toNode.paramList.addAll(tmpParamList);
        // 交换 filter
        java.util.Map<String,String> tmpFilter = new java.util.LinkedHashMap<>(fromNode.filter);
        fromNode.filter.clear(); fromNode.filter.putAll(toNode.filter);
        toNode.filter.clear(); toNode.filter.putAll(tmpFilter);
        // 交换 filterParamList
        java.util.List<java.util.Map<String,String>> tmpFilterParamList = new java.util.ArrayList<>(fromNode.filterParamList);
        fromNode.filterParamList.clear(); fromNode.filterParamList.addAll(toNode.filterParamList);
        toNode.filterParamList.clear(); toNode.filterParamList.addAll(tmpFilterParamList);
        // 交换 comments
        java.util.List<String> tmpComments = new java.util.ArrayList<>(fromNode.comments);
        fromNode.comments.clear(); fromNode.comments.addAll(toNode.comments);
        toNode.comments.clear(); toNode.comments.addAll(tmpComments);
        // 交换 unknownTag/unknownContent（若存在）
        String tmpUnknownTag = fromNode.unknownTag;
        String tmpUnknownContent = fromNode.unknownContent;
        fromNode.unknownTag = toNode.unknownTag;
        fromNode.unknownContent = toNode.unknownContent;
        toNode.unknownTag = tmpUnknownTag;
        toNode.unknownContent = tmpUnknownContent;
        // 交换 showComments 标志
        boolean tmpShow = fromNode.showComments;
        fromNode.showComments = toNode.showComments;
        toNode.showComments = tmpShow;
    java.util.List<Integer> expandedIds = logic.SwingTreeUtil.collectExpandedIds(tree, root);
    Integer selectedId = logic.SwingTreeUtil.findSelectedNodeId(tree);
    logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
    ((javax.swing.tree.DefaultTreeModel)tree.getModel()).reload();
    logic.SwingTreeUtil.applyUiState(tree, root, expandedIds, selectedId);
        graphPanel.setLogicRoot(logicRoot[0]);
        logic.LogicValidator.validateAllNodes(logicRoot[0]);
        logic.LogicUiUtil.updateErrorStatusBar(logicRoot[0], status, errorNodeMap);
    }
}
