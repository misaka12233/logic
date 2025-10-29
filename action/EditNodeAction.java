package action;

import logic.*;
import javax.swing.*;

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
        // 特殊交互：对于 FORALL/EXISTS 提供部分修改选项；对于 BFUNC 提供修改单个参数的选项；否则进入完全修改流程
        if (ln.type == LogicNode.NodeType.FORALL || ln.type == LogicNode.NodeType.EXISTS) {
            java.util.List<String> optsList = new java.util.ArrayList<>();
            optsList.add("修改变量(var)");
            optsList.add("修改集合(pattern)");
            // 如果当前节点有 filter，则为每个 filterParam 提供单独修改选项
            boolean hasFilter = ln.filter != null && ln.filter.containsKey("name") && ln.filterParamList != null && !ln.filterParamList.isEmpty();
            if (hasFilter) {
                for (int i=0;i<ln.filterParamList.size();i++) {
                    java.util.Map<String,String> fp = ln.filterParamList.get(i);
                    String pos = fp.getOrDefault("pos", "p"+i);
                    String varVal = fp.getOrDefault("var", "");
                    optsList.add("修改过滤器参数 " + pos + " (当前: " + varVal + ")");
                }
            }
            optsList.add("修改过滤器(ffunc)");
            optsList.add("完全修改");
            String[] opts = optsList.toArray(new String[0]);
            String choice = (String)JOptionPane.showInputDialog(frame, "选择要修改的部分:", "修改", JOptionPane.PLAIN_MESSAGE, null, opts, opts[opts.length-1]);
            if (choice == null) return;
            if (choice.equals("修改变量(var)")) {
                String v = JOptionPane.showInputDialog(frame, "变量名(var):", ln.params.getOrDefault("var",""));
                if (v == null) return;
                // 保存快照并应用修改
                logic.UndoManager.saveSnapshot(logicRoot[0], tree, root);
                ln.params.put("var", v);
                java.util.List<Integer> expandedIds = logic.SwingTreeUtil.collectExpandedIds(tree, root);
                Integer selectedId = logic.SwingTreeUtil.findSelectedNodeId(tree);
                logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
                ((DefaultTreeModel)tree.getModel()).reload();
                logic.SwingTreeUtil.applyUiState(tree, root, expandedIds, selectedId);
                graphPanel.setLogicRoot(logicRoot[0]);
                LogicValidator.validateAllNodes(logicRoot[0]);
                LogicUiUtil.updateErrorStatusBar(logicRoot[0], status, errorNodeMap);
                return;
            } else if (choice.startsWith("修改过滤器参数 ")) {
                // 用户选择修改某个 filter 参数
                // 找到对应参数索引（按 pos 匹配）
                String rest = choice.substring("修改过滤器参数 ".length());
                // rest like "<pos> (当前: ...)", 取第一个空格前的 token 作为 pos
                String posToken = rest.split(" ")[0];
                int foundIdx = -1;
                for (int i=0;i<ln.filterParamList.size();i++) {
                    if (posToken.equals(ln.filterParamList.get(i).getOrDefault("pos", ""))) { foundIdx = i; break; }
                }
                if (foundIdx == -1) {
                    // fallback：按索引解析（极不可能）
                    try { foundIdx = Integer.parseInt(posToken); } catch(Exception ex) { foundIdx = -1; }
                }
                if (foundIdx >= 0 && foundIdx < ln.filterParamList.size()) {
                    java.util.Map<String,String> target = ln.filterParamList.get(foundIdx);
                    String oldVal = target.getOrDefault("var", "");
                    String nv = JOptionPane.showInputDialog(frame, "新过滤器参数值 (var):", oldVal);
                    if (nv == null) return;
                    logic.UndoManager.saveSnapshot(logicRoot[0], tree, root);
                    target.put("var", nv);
                    java.util.List<Integer> expandedIds = logic.SwingTreeUtil.collectExpandedIds(tree, root);
                    Integer selectedId = logic.SwingTreeUtil.findSelectedNodeId(tree);
                    logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
                    ((DefaultTreeModel)tree.getModel()).reload();
                    logic.SwingTreeUtil.applyUiState(tree, root, expandedIds, selectedId);
                    graphPanel.setLogicRoot(logicRoot[0]);
                    LogicValidator.validateAllNodes(logicRoot[0]);
                    LogicUiUtil.updateErrorStatusBar(logicRoot[0], status, errorNodeMap);
                }
                return;
            } else if (choice.equals("修改集合(pattern)")) {
                if (config[0]==null || config[0].patterns.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "config.xml未加载或无patterns，无法选择集合名", "错误", JOptionPane.ERROR_MESSAGE); return;
                }
                ConfigXmlLoader.PatternInfo[] patternArr = config[0].patterns.toArray(new ConfigXmlLoader.PatternInfo[0]);
                ConfigXmlLoader.PatternInfo selPattern = (ConfigXmlLoader.PatternInfo)JOptionPane.showInputDialog(frame, "选择集合名(patterns):", "集合名", JOptionPane.PLAIN_MESSAGE, null, patternArr, Arrays.stream(patternArr).filter(p->p.name.equals(ln.params.getOrDefault("in",""))).findFirst().orElse(patternArr[0]));
                if (selPattern==null) return;
                logic.UndoManager.saveSnapshot(logicRoot[0], tree, root);
                ln.params.put("in", selPattern.name);
                java.util.List<Integer> expandedIds = logic.SwingTreeUtil.collectExpandedIds(tree, root);
                Integer selectedId = logic.SwingTreeUtil.findSelectedNodeId(tree);
                logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
                ((DefaultTreeModel)tree.getModel()).reload();
                logic.SwingTreeUtil.applyUiState(tree, root, expandedIds, selectedId);
                graphPanel.setLogicRoot(logicRoot[0]);
                LogicValidator.validateAllNodes(logicRoot[0]);
                LogicUiUtil.updateErrorStatusBar(logicRoot[0], status, errorNodeMap);
                return;
            } else if (choice.equals("修改过滤器(ffunc)")) {
                if (config[0]==null || config[0].ffuncs.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "config.xml未加载或无ffuncs，无法选择过滤器", "错误", JOptionPane.ERROR_MESSAGE); return;
                }
                java.util.List<Object> ffuncList = new java.util.ArrayList<>();
                ffuncList.add("（无）");
                ffuncList.addAll(config[0].ffuncs);
                Object[] ffuncArr = ffuncList.toArray();
                Object defaultSel = "（无）";
                if (ln.filter.containsKey("name")) {
                    for (Object o : ffuncArr) {
                        if (o instanceof ConfigXmlLoader.FuncInfo && ((ConfigXmlLoader.FuncInfo)o).name.equals(ln.filter.get("name"))) { defaultSel = o; break; }
                    }
                }
                Object selObj = JOptionPane.showInputDialog(frame, "选择过滤器函数(可选):", "过滤器", JOptionPane.PLAIN_MESSAGE, null, ffuncArr, defaultSel);
                if (selObj == null) return;
                Map<String,String> filter = new LinkedHashMap<>();
                java.util.List<java.util.Map<String,String>> filterParamList = new java.util.ArrayList<>();
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
                logic.UndoManager.saveSnapshot(logicRoot[0], tree, root);
                ln.filter.clear(); ln.filter.putAll(filter);
                ln.filterParamList.clear(); ln.filterParamList.addAll(filterParamList);
                java.util.List<Integer> expandedIds = logic.SwingTreeUtil.collectExpandedIds(tree, root);
                Integer selectedId = logic.SwingTreeUtil.findSelectedNodeId(tree);
                logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
                ((DefaultTreeModel)tree.getModel()).reload();
                logic.SwingTreeUtil.applyUiState(tree, root, expandedIds, selectedId);
                graphPanel.setLogicRoot(logicRoot[0]);
                LogicValidator.validateAllNodes(logicRoot[0]);
                LogicUiUtil.updateErrorStatusBar(logicRoot[0], status, errorNodeMap);
                return;
            }
            // 如果用户选择 "完全修改" 则继续到后续的完整修改流程
        } else if (ln.type == LogicNode.NodeType.BFUNC) {
            // 提供修改单个参数或完全修改的选项
            java.util.List<String> opts = new java.util.ArrayList<>();
            for (int i=0;i<ln.paramList.size();i++) {
                java.util.Map<String,String> p = ln.paramList.get(i);
                String pos = p.getOrDefault("pos", "?");
                String var = p.getOrDefault("var", "");
                opts.add("修改参数 " + pos + " (当前: " + var + ")");
            }
            opts.add("完全修改");
            String[] optsArr = opts.toArray(new String[0]);
            String bfuncChoice = (String)JOptionPane.showInputDialog(frame, "选择要修改的参数:", "修改 BFUNC", JOptionPane.PLAIN_MESSAGE, null, optsArr, optsArr[optsArr.length-1]);
            if (bfuncChoice == null) return;
            if (!bfuncChoice.equals("完全修改")) {
                int idx = opts.indexOf(bfuncChoice);
                if (idx >= 0 && idx < ln.paramList.size()) {
                    java.util.Map<String,String> target = ln.paramList.get(idx);
                    String oldVal = target.getOrDefault("var", "");
                    String nv = JOptionPane.showInputDialog(frame, "新参数值 (var):", oldVal);
                    if (nv == null) return;
                    logic.UndoManager.saveSnapshot(logicRoot[0], tree, root);
                    target.put("var", nv);
                    java.util.List<Integer> expandedIds = logic.SwingTreeUtil.collectExpandedIds(tree, root);
                    Integer selectedId = logic.SwingTreeUtil.findSelectedNodeId(tree);
                    logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
                    ((DefaultTreeModel)tree.getModel()).reload();
                    logic.SwingTreeUtil.applyUiState(tree, root, expandedIds, selectedId);
                    graphPanel.setLogicRoot(logicRoot[0]);
                    LogicValidator.validateAllNodes(logicRoot[0]);
                    LogicUiUtil.updateErrorStatusBar(logicRoot[0], status, errorNodeMap);
                }
                return;
            }
            // 若选择完全修改则继续到完整修改流程
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
        // 保存快照以支持撤销（包含 UI 展开/选中状态）
        logic.UndoManager.saveSnapshot(logicRoot[0], tree, root);
        // apply changes
        ln.type = type;
        ln.params.clear(); ln.params.putAll(params);
        ln.paramList.clear(); ln.paramList.addAll(paramList);
        ln.filter.clear(); ln.filter.putAll(filter);
        ln.filterParamList.clear(); ln.filterParamList.addAll(filterParamList);
    java.util.List<Integer> expandedIds = logic.SwingTreeUtil.collectExpandedIds(tree, root);
    Integer selectedId = logic.SwingTreeUtil.findSelectedNodeId(tree);
    logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
    ((DefaultTreeModel)tree.getModel()).reload();
    logic.SwingTreeUtil.applyUiState(tree, root, expandedIds, selectedId);
        graphPanel.setLogicRoot(logicRoot[0]);
        logic.LogicValidator.validateAllNodes(logicRoot[0]);
        logic.LogicUiUtil.updateErrorStatusBar(logicRoot[0], status, errorNodeMap);
    }
}
