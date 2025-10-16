package action;

import logic.*;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import javax.swing.tree.*;

public class EditNodeAction implements ActionListener {
    private final JFrame frame;
    private final JTree tree;
    private final DefaultMutableTreeNode root;
    private final LogicNode[] logicRoot;
    private final ConfigXmlLoader[] config;
    private final LogicGraphPanel graphPanel;
    private final JLabel status;
    private final Map<Integer, String> errorNodeMap;

    public EditNodeAction(JFrame frame, JTree tree, DefaultMutableTreeNode root, LogicNode[] logicRoot, ConfigXmlLoader[] config, LogicGraphPanel graphPanel, JLabel status, Map<Integer, String> errorNodeMap) {
        this.frame = frame;
        this.tree = tree;
        this.root = root;
        this.logicRoot = logicRoot;
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
        if (ln==null) {
            JOptionPane.showMessageDialog(frame, "未找到节点对应的数据。", "错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        // 1. 类型选择
        LogicNode.NodeType[] types = Arrays.stream(LogicNode.NodeType.values()).filter(t -> t != LogicNode.NodeType.UNKNOWN).toArray(LogicNode.NodeType[]::new);
        LogicNode.NodeType type = (LogicNode.NodeType)JOptionPane.showInputDialog(frame, "选择要修改为的类型:", "类型", JOptionPane.PLAIN_MESSAGE, null, types, ln.type);
        if (type==null) return;
        // 2. 按新类型填写参数，全部用下拉选择
        Map<String,String> params = new LinkedHashMap<>();
        java.util.List<java.util.Map<String,String>> paramList = new java.util.ArrayList<>();
        Map<String,String> filter = new LinkedHashMap<>();
        java.util.List<java.util.Map<String,String>> filterParamList = new java.util.ArrayList<>();
        switch(type) {
            case FORALL: case EXISTS: {
                String v = JOptionPane.showInputDialog(frame,"变量名(var):", ln.params.getOrDefault("var","") );
                if (v==null) return;
                if (config[0]==null || config[0].patterns.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "config.xml未加载或无patterns，无法选择集合名", "错误", JOptionPane.ERROR_MESSAGE); return;
                }
                ConfigXmlLoader.PatternInfo[] patternArr = config[0].patterns.toArray(new ConfigXmlLoader.PatternInfo[0]);
                ConfigXmlLoader.PatternInfo selPattern = (ConfigXmlLoader.PatternInfo)JOptionPane.showInputDialog(frame, "选择集合名(patterns):", "集合名", JOptionPane.PLAIN_MESSAGE, null, patternArr, Arrays.stream(patternArr).filter(p->p.name.equals(ln.params.getOrDefault("in",""))).findFirst().orElse(patternArr[0]));
                if (selPattern==null) return;
                params.put("var", v);
                params.put("in", selPattern.name);
                if (config[0].ffuncs.size()>0) {
                    java.util.List<Object> ffuncList = new java.util.ArrayList<>();
                    ffuncList.add("（无）");
                    ffuncList.addAll(config[0].ffuncs);
                    Object[] ffuncArr = ffuncList.toArray();
                    Object defaultSel = "（无）";
                    if (ln.filter.containsKey("name")) {
                        for (Object o : ffuncArr) {
                            if (o instanceof ConfigXmlLoader.FuncInfo && ((ConfigXmlLoader.FuncInfo)o).name.equals(ln.filter.get("name"))) {
                                defaultSel = o; break;
                            }
                        }
                    }
                    Object selObj = JOptionPane.showInputDialog(frame, "选择过滤器函数(可选):", "过滤器", JOptionPane.PLAIN_MESSAGE, null, ffuncArr, defaultSel);
                    if (selObj instanceof ConfigXmlLoader.FuncInfo) {
                        ConfigXmlLoader.FuncInfo selFfunc = (ConfigXmlLoader.FuncInfo)selObj;
                        filter.put("name", selFfunc.name);
                        for (int i=0;i<selFfunc.params.size();i++) {
                            ConfigXmlLoader.ParamInfo pinfo = selFfunc.params.get(i);
                            String oldVal = (i<ln.filterParamList.size()) ? ln.filterParamList.get(i).getOrDefault("var","") : "";
                            String val = JOptionPane.showInputDialog(frame, "填写过滤器参数 " + pinfo.name + " (" + pinfo.description + "):", oldVal);
                            if (val==null) return;
                            Map<String,String> fp = new LinkedHashMap<>();
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
                ConfigXmlLoader.FuncInfo selBfunc = (ConfigXmlLoader.FuncInfo)JOptionPane.showInputDialog(frame, "选择布尔函数:", "布尔函数", JOptionPane.PLAIN_MESSAGE, null, bfuncArr, ln.params.containsKey("name") ? Arrays.stream(bfuncArr).filter(f->f.name.equals(ln.params.get("name"))).findFirst().orElse(bfuncArr[0]) : null);
                if (selBfunc==null) return;
                params.put("name", selBfunc.name);
                for (int i=0;i<selBfunc.params.size();i++) {
                    ConfigXmlLoader.ParamInfo pinfo = selBfunc.params.get(i);
                    String oldVal = (i<ln.paramList.size()) ? ln.paramList.get(i).getOrDefault("var","") : "";
                    String val = JOptionPane.showInputDialog(frame, "填写参数 " + pinfo.name + " (" + pinfo.description + "):", oldVal);
                    if (val==null) return;
                    Map<String,String> p = new LinkedHashMap<>();
                    p.put("pos", pinfo.name);
                    p.put("var", val);
                    paramList.add(p);
                }
                break;
            }
            default:
        }
        ln.type = type;
        ln.params.clear(); ln.params.putAll(params);
        ln.paramList.clear(); ln.paramList.addAll(paramList);
        ln.filter.clear(); ln.filter.putAll(filter);
        ln.filterParamList.clear(); ln.filterParamList.addAll(filterParamList);
        logic.SwingTreeUtil.saveExpandState(logicRoot[0], root, tree);
        logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
        ((DefaultTreeModel)tree.getModel()).reload();
        logic.SwingTreeUtil.restoreExpandState(logicRoot[0], root, tree);
        graphPanel.setLogicRoot(logicRoot[0]);
        logic.LogicUiUtil.validateAllNodes(logicRoot[0], errorNodeMap);
        logic.LogicUiUtil.updateErrorStatusBar(logicRoot[0], status, errorNodeMap);
    }
}
