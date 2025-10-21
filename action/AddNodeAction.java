
package action;
import logic.ConfigXmlLoader;
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

public class AddNodeAction implements ActionListener {
    private final JFrame frame;
    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private final LogicNode[] logicRoot;
    private final int[] nodeIdCounter;
    private final ConfigXmlLoader[] config;
    private final LogicGraphPanel graphPanel;
    private final Map<Integer, String> errorNodeMap;
    private final JLabel status;

    public AddNodeAction(JFrame frame, JTree tree, DefaultMutableTreeNode root, LogicNode[] logicRoot, int[] nodeIdCounter, ConfigXmlLoader[] config, LogicGraphPanel graphPanel, JLabel status, Map<Integer, String> errorNodeMap) {
        this.frame = frame;
        this.tree = tree;
        this.root = root;
        this.logicRoot = logicRoot;
        this.nodeIdCounter = nodeIdCounter;
        this.config = config;
        this.graphPanel = graphPanel;
        this.status = status;
        this.errorNodeMap = errorNodeMap;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        TreePath path = tree.getSelectionPath();
        if (path==null || logicRoot[0]==null) return;
        DefaultMutableTreeNode sel = (DefaultMutableTreeNode)path.getLastPathComponent();
        LogicNode ln = TreeHelper.findNode(logicRoot[0], sel, root);
        if (ln==null) return;
        LogicNode.NodeType[] types = Arrays.stream(LogicNode.NodeType.values()).filter(t -> t != LogicNode.NodeType.UNKNOWN).toArray(LogicNode.NodeType[]::new);
        LogicNode.NodeType type = (LogicNode.NodeType)JOptionPane.showInputDialog(frame, "选择节点类型:", "类型", JOptionPane.PLAIN_MESSAGE, null, types, types[0]);
        if (type==null) return;
        Map<String,String> params = new LinkedHashMap<>();
        java.util.List<java.util.Map<String,String>> paramList = new java.util.ArrayList<>();
        Map<String,String> filter = new LinkedHashMap<>();
        java.util.List<java.util.Map<String,String>> filterParamList = new java.util.ArrayList<>();
        switch(type) {
            case FORALL: case EXISTS: {
                String v = JOptionPane.showInputDialog(frame,"变量名(var):");
                if (v==null) return;
                if (config[0]==null || config[0].patterns.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "config.xml未加载或无patterns，无法选择集合名", "错误", JOptionPane.ERROR_MESSAGE); return;
                }
                ConfigXmlLoader.PatternInfo[] patternArr = config[0].patterns.toArray(new ConfigXmlLoader.PatternInfo[0]);
                ConfigXmlLoader.PatternInfo selPattern = (ConfigXmlLoader.PatternInfo)JOptionPane.showInputDialog(frame, "选择集合名(patterns):", "集合名", JOptionPane.PLAIN_MESSAGE, null, patternArr, patternArr[0]);
                if (selPattern==null) return;
                params.put("var", v);
                params.put("in", selPattern.name);
                if (config[0].ffuncs.size()>0) {
                    java.util.List<Object> ffuncList = new java.util.ArrayList<>();
                    ffuncList.add("（无）");
                    ffuncList.addAll(config[0].ffuncs);
                    Object[] ffuncArr = ffuncList.toArray();
                    Object selObj = JOptionPane.showInputDialog(frame, "选择过滤器函数(可选):", "过滤器", JOptionPane.PLAIN_MESSAGE, null, ffuncArr, ffuncArr[0]);
                    if (selObj instanceof ConfigXmlLoader.FuncInfo) {
                        ConfigXmlLoader.FuncInfo selFfunc = (ConfigXmlLoader.FuncInfo)selObj;
                        filter.put("name", selFfunc.name);
                        for (ConfigXmlLoader.ParamInfo pinfo : selFfunc.params) {
                            Map<String,String> fp = new LinkedHashMap<>();
                            String val = JOptionPane.showInputDialog(frame, "填写过滤器参数 " + pinfo.name + " (" + pinfo.description + "):");
                            if (val==null) return;
                            fp.put("pos", pinfo.name);
                            fp.put("var", val);
                            filterParamList.add(fp);
                        }
                    }
                }
                break;
            }
            case BFUNC: {
                if (config[0]==null || config[0].bfuncs.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "config.xml未加载或无bfuncs，无法选择布尔函数", "错误", JOptionPane.ERROR_MESSAGE); return;
                }
                ConfigXmlLoader.FuncInfo[] bfuncArr = config[0].bfuncs.toArray(new ConfigXmlLoader.FuncInfo[0]);
                ConfigXmlLoader.FuncInfo selBfunc = (ConfigXmlLoader.FuncInfo)JOptionPane.showInputDialog(frame, "选择布尔函数:", "布尔函数", JOptionPane.PLAIN_MESSAGE, null, bfuncArr, bfuncArr[0]);
                if (selBfunc==null) return;
                params.put("name", selBfunc.name);
                for (ConfigXmlLoader.ParamInfo pinfo : selBfunc.params) {
                    Map<String,String> p = new LinkedHashMap<>();
                    String val = JOptionPane.showInputDialog(frame, "填写参数 " + pinfo.name + " (" + pinfo.description + "):");
                    if (val==null) return;
                    p.put("pos", pinfo.name);
                    p.put("var", val);
                    paramList.add(p);
                }
                break;
            }
            default:
        }
        LogicNode newNode = new LogicNode(type, nodeIdCounter[0]++);
        newNode.params.putAll(params);
        newNode.paramList.addAll(paramList);
        newNode.filter.putAll(filter);
        newNode.filterParamList.addAll(filterParamList);
        // 保存快照以支持撤销（包括 UI 状态）
        logic.UndoManager.saveSnapshot(logicRoot[0], tree, root);
        ln.children.add(newNode);
        logic.SwingTreeUtil.saveExpandState(logicRoot[0], root, tree);
        logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
        ((DefaultTreeModel)tree.getModel()).reload();
        logic.SwingTreeUtil.restoreExpandState(logicRoot[0], root, tree);
        graphPanel.setLogicRoot(logicRoot[0]);
        // 实时校验
        // 需传入全局errorNodeMap
        LogicValidator.validateAllNodes(logicRoot[0]);
        LogicUiUtil.updateErrorStatusBar(logicRoot[0], status, errorNodeMap);
    }
}
