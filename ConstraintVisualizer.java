import javax.swing.*;
import javax.swing.tree.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import java.awt.*;
import javax.xml.transform.TransformerFactory;
import java.util.*;
import logic.LogicNode;
import logic.TreeHelper;

/**
 * 约束描述语言可视化与编辑器
 * - 支持 XML 格式解析/保存
 * - 支持语法校验（参数、子公式数量、变量使用等）
 * - 支持树状结构展示、节点增删改
 * - 支持导出 PNG 图片
 * - 节点数据结构区分类型、参数、子节点
 */
public class ConstraintVisualizer {

    /**
     * 深拷贝LogicNode子树（不复用nodeId，分配新id）
     */
    static LogicNode deepCopyNode(LogicNode node) {
        LogicNode n = new LogicNode(node.type, nodeIdCounter[0]++);
        n.params.putAll(node.params);
        for (java.util.Map<String,String> p : node.paramList) n.paramList.add(new java.util.LinkedHashMap<>(p));
        n.filter.putAll(node.filter);
        for (java.util.Map<String,String> p : node.filterParamList) n.filterParamList.add(new java.util.LinkedHashMap<>(p));
        for (LogicNode c : node.children) n.children.add(deepCopyNode(c));
        return n;
    }
    // 递归查找nodeId对应的LogicNode
    static LogicNode findNodeById(LogicNode node, int id) {
        if (node.nodeId == id) return node;
        for (LogicNode child : node.children) {
            LogicNode res = findNodeById(child, id);
            if (res != null) return res;
        }
        return null;
    }

    // 更新底部状态栏错误摘要
    static void updateErrorStatusBar(LogicNode logicRoot, JLabel status) {
        if (logicRoot == null || errorNodeMap.isEmpty()) {
            status.setText("Ready");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, String> entry : errorNodeMap.entrySet()) {
            LogicNode n = findNodeById(logicRoot, entry.getKey());
            if (n != null) {
                sb.append(n.toString()).append(" ").append(entry.getValue()).append("\n");
            }
        }
        if (sb.length() > 0 && sb.charAt(sb.length()-1) == '\n') sb.setLength(sb.length()-1);
        String html = "<html>" + sb.toString().replace("\n", "<br>") + "</html>";
        status.setText(html);
    }
    // 通过树节点查找对应LogicNode的nodeId（递归）
    static Integer getNodeIdByTreeNode(DefaultMutableTreeNode treeNode, LogicNode logicNode, DefaultMutableTreeNode swingRoot) {
        if (treeNode == swingRoot) return logicNode.nodeId;
        // 在logicNode.children和treeNode.children中同步递归
        int idx = swingRoot.getIndex(treeNode);
        if (idx >= 0 && idx < logicNode.children.size()) {
            return logicNode.children.get(idx).nodeId;
        }
        // 多层递归
        for (int i = 0; i < swingRoot.getChildCount(); i++) {
            Integer id = getNodeIdByTreeNode(treeNode, logicNode.children.get(i), (DefaultMutableTreeNode)swingRoot.getChildAt(i));
            if (id != null) return id;
        }
        return null;
    }

    // 自定义渲染器
    static class ErrorHighlightTreeCellRenderer extends DefaultTreeCellRenderer {
        LogicNode logicRoot;
        DefaultMutableTreeNode swingRoot;
        public ErrorHighlightTreeCellRenderer(LogicNode logicRoot, DefaultMutableTreeNode swingRoot) {
            this.logicRoot = logicRoot;
            this.swingRoot = swingRoot;
        }
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            Component c = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
            return c;
        }
        // 增加字段保存当前渲染节点
        DefaultMutableTreeNode currentNode;
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus, DefaultMutableTreeNode node) {
            this.currentNode = node;
            return getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
        }
        @Override
        public void updateUI() {
            super.updateUI();
        }
    }
    // 错误节点缓存：nodeId -> 错误描述
    static Map<Integer, String> errorNodeMap = new HashMap<>();

    // 递归校验所有节点，收集所有有错误的节点及类型（每个节点只标记自身错误）
    static void validateAllNodes(LogicNode node) {
        errorNodeMap.clear();
        validateAllNodesRec(node, null);
    }
    static void validateAllNodesRec(LogicNode node, logic.LogicNode.NodeType parentType) {
        String err = logic.LogicValidator.validateNodeSelf(node, parentType);
        if (err != null) {
            errorNodeMap.put(node.nodeId, err);
        }
        for (LogicNode child : node.children) {
            validateAllNodesRec(child, node.type);
        }
    }
    static int[] nodeIdCounter = new int[]{1};

    public static void main(String[] args) {
        // 当前数据
        final LogicNode[] logicRoot = new LogicNode[1];
        // 加载config.xml
        final ConfigXmlLoader[] config = new ConfigXmlLoader[1];
        try {
            config[0] = ConfigXmlLoader.loadFromFile("config.xml");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null, "读取config.xml失败："+ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
        }
    JFrame frame = new JFrame("约束描述语言可视化");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900,600);
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("(未加载)");
    JTree tree = new JTree(root);
    tree.setFont(new Font("SansSerif", Font.PLAIN, 18));
    // 右侧有向图可视化面板
    LogicGraphPanel graphPanel = new LogicGraphPanel();
    // 点击JTree空白处取消选中
    tree.addMouseListener(new java.awt.event.MouseAdapter() {
        @Override
        public void mousePressed(java.awt.event.MouseEvent e) {
            int selRow = tree.getRowForLocation(e.getX(), e.getY());
            if (selRow == -1) {
                tree.clearSelection();
            }
        }
    });
        // 设置自定义渲染器，需能访问logicRoot[0]
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                JLabel c = (JLabel)super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                c.putClientProperty("errorLine", false);
                if (value instanceof DefaultMutableTreeNode) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
                    Integer nodeId = null;
                    String s = node.getUserObject().toString();
                    if (s.startsWith("[")) {
                        int idx = s.indexOf("]");
                        if (idx > 1) {
                            try { nodeId = Integer.parseInt(s.substring(1, idx)); } catch(Exception ex){}
                        }
                    }
                    // 颜色渲染：编号紫色，内容黑色
                    if (s.startsWith("[")) {
                        int idx = s.indexOf("]");
                        if (idx > 0) {
                            String idStr = s.substring(0, idx+1);
                            String content = s.substring(idx+1).trim();
                            c.setText("<html><span style='color:purple;'>"+idStr+"</span> <span style='color:black;'>"+content+"</span></html>");
                        }
                    }
                    if (nodeId != null && ConstraintVisualizer.errorNodeMap.containsKey(nodeId)) {
                        c.putClientProperty("errorLine", true);
                    }
                }
                return c;
            }
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Boolean error = (Boolean)this.getClientProperty("errorLine");
                if (error != null && error) {
                    g.setColor(Color.RED);
                    int y = getHeight() - 2;
                    g.fillRect(2, y, getWidth()-4, 2);
                }
            }
        });
        JScrollPane scroll = new JScrollPane(tree);
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(scroll);
        splitPane.setRightComponent(graphPanel);
        splitPane.setResizeWeight(0.5);
        frame.add(splitPane, BorderLayout.CENTER);
        JLabel status = new JLabel("Ready");
        frame.add(status, BorderLayout.SOUTH);

        // 文件操作
        JMenuBar bar = new JMenuBar();
        JMenu fileMenu = new JMenu("文件");
        JMenuItem open = new JMenuItem("打开XML");
        JMenuItem save = new JMenuItem("保存XML");
        JMenuItem export = new JMenuItem("导出PNG");
        fileMenu.add(open); fileMenu.add(save); fileMenu.add(export);
        bar.add(fileMenu);
        frame.setJMenuBar(bar);

        // 节点操作
        JPanel btnPanel = new JPanel();
        JButton addBtn = new JButton("添加节点");
        JButton editBtn = new JButton("修改节点");
        JButton delBtn = new JButton("删除节点");
        JButton moveBtn = new JButton("移动节点");
        JButton swapSubtreeBtn = new JButton("整体交换");
        JButton swapNodeBtn = new JButton("节点交换");
        JButton copyNodeBtn = new JButton("复制节点");
        JButton expandCollapseBtn = new JButton("展开树");
        btnPanel.add(addBtn); btnPanel.add(editBtn); btnPanel.add(delBtn); btnPanel.add(moveBtn); btnPanel.add(swapSubtreeBtn); btnPanel.add(swapNodeBtn); btnPanel.add(copyNodeBtn); btnPanel.add(expandCollapseBtn);
        
        // 复制节点及其子树
        copyNodeBtn.addActionListener(e -> {
            TreePath selectedPath = tree.getSelectionPath();
            if (selectedPath == null || logicRoot[0] == null) {
                JOptionPane.showMessageDialog(frame, "请先选中要复制的节点", "提示", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            DefaultMutableTreeNode sel = (DefaultMutableTreeNode)selectedPath.getLastPathComponent();
            LogicNode toCopy = TreeHelper.findNode(logicRoot[0], sel, root);
            if (toCopy == null) return;
            // 收集所有可作为父节点的节点
            java.util.List<LogicNode> allNodes = new java.util.ArrayList<>();
            java.util.List<DefaultMutableTreeNode> allTreeNodes = new java.util.ArrayList<>();
            java.util.function.BiConsumer<LogicNode, DefaultMutableTreeNode> collect = new java.util.function.BiConsumer<LogicNode, DefaultMutableTreeNode>() {
                public void accept(LogicNode n, DefaultMutableTreeNode t) {
                    allNodes.add(n); allTreeNodes.add(t);
                    for (int i=0;i<n.children.size();i++) accept(n.children.get(i), (DefaultMutableTreeNode)t.getChildAt(i));
                }
            };
            collect.accept(logicRoot[0], root);
            // 使用下拉框选择父节点
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
            // 深拷贝子树
            LogicNode copy = deepCopyNode(toCopy);
            parent.children.add(copy);
            logic.SwingTreeUtil.saveExpandState(logicRoot[0], root, tree);
            logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
            ((DefaultTreeModel)tree.getModel()).reload();
            logic.SwingTreeUtil.restoreExpandState(logicRoot[0], root, tree);
            graphPanel.setLogicRoot(logicRoot[0]);
            validateAllNodes(logicRoot[0]);
            updateErrorStatusBar(logicRoot[0], status);
        });



        // 展开/收起树功能
        final boolean[] treeExpanded = {false};
        // 展开所有节点
        Runnable expandAll = () -> {
            int row = 0;
            while (row < tree.getRowCount()) {
                tree.expandRow(row);
                row++;
            }
        };
        // 收起所有节点，只展开根节点
        Runnable collapseAll = () -> {
            for (int i = tree.getRowCount() - 1; i > 0; i--) {
                tree.collapseRow(i);
            }
        };
        expandCollapseBtn.addActionListener(e -> {
            if (treeExpanded[0]) {
                collapseAll.run();
                expandCollapseBtn.setText("展开树");
            } else {
                expandAll.run();
                expandCollapseBtn.setText("收起树");
            }
            treeExpanded[0] = !treeExpanded[0];
        });
    frame.add(btnPanel, BorderLayout.NORTH);

        // 选中树节点时高亮右侧图节点
        tree.addTreeSelectionListener(e -> {
            TreePath path = tree.getSelectionPath();
            if (path != null && logicRoot[0] != null) {
                DefaultMutableTreeNode sel = (DefaultMutableTreeNode)path.getLastPathComponent();
                LogicNode ln = TreeHelper.findNode(logicRoot[0], sel, root);
                if (ln != null) {
                    graphPanel.setHighlightNodeId(ln.nodeId);
                    // 平移图片中心到该节点
                    Rectangle rect = graphPanel.getNodeBounds(ln.nodeId);
                    if (rect != null) {
                        int cx = rect.x + rect.width/2;
                        int cy = rect.y + rect.height/2;
                        int viewW = graphPanel.getVisibleRect().width;
                        int viewH = graphPanel.getVisibleRect().height;
                        int targetOffsetX = (int)(viewW/2 - cx * graphPanel.getScale());
                        int targetOffsetY = (int)(viewH/2 - cy * graphPanel.getScale());
                        graphPanel.setOffset(targetOffsetX, targetOffsetY);
                    }
                } else {
                    graphPanel.setHighlightNodeId(null);
                }
            } else {
                graphPanel.setHighlightNodeId(null);
            }
        });
        // 打开XML
        open.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
            if (fc.showOpenDialog(frame)==JFileChooser.APPROVE_OPTION) {
                try {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    Document doc = db.parse(fc.getSelectedFile());
                    Element fe = (Element)doc.getDocumentElement();
                    nodeIdCounter = new int[]{1};
                    LogicNode temp = logic.LogicXmlUtil.parseXml(fe, nodeIdCounter);
                    String err = logic.LogicValidator.validate(temp);
                    if (err != null) {
                        JOptionPane.showMessageDialog(frame, "XML语法校验失败：" + err, "错误", JOptionPane.ERROR_MESSAGE);
                        status.setText("XML校验失败");
                        return;
                    }
                    logicRoot[0] = temp;
                    root.setUserObject(logicRoot[0].toString());
                    root.removeAllChildren();
                    logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
                    ((DefaultTreeModel)tree.getModel()).reload();
                    graphPanel.setLogicRoot(logicRoot[0]);
                    validateAllNodes(logicRoot[0]);
                    updateErrorStatusBar(logicRoot[0], status);
                    status.setText("XML加载成功");
                } catch (Exception ex) {
                    status.setText("XML解析失败: "+ex.getMessage());
                }
            }
        });
        // 保存XML
        save.addActionListener(e -> {
            if (logicRoot[0]==null) return;
            String err = logic.LogicValidator.validate(logicRoot[0]);
            if (err != null) {
                JOptionPane.showMessageDialog(frame, "XML语法校验失败：" + err, "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
            if (fc.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION) {
                try {
                    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                    DocumentBuilder db = dbf.newDocumentBuilder();
                    Document doc = db.newDocument();
                    doc.appendChild(logic.LogicXmlUtil.toXml(logicRoot[0], doc));
                    TransformerFactory tf = TransformerFactory.newInstance();
                    javax.xml.transform.Transformer t = tf.newTransformer();
                    t.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
                    t.transform(new javax.xml.transform.dom.DOMSource(doc), new javax.xml.transform.stream.StreamResult(fc.getSelectedFile()));
                    status.setText("XML保存成功");
                } catch (Exception ex) {
                    status.setText("XML保存失败: "+ex.getMessage());
                }
            }
        });
        // 导出PNG
        // 导出SVG
        export.setText("导出SVG");
        export.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
            if (fc.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION) {
                try {
                    logic.LogicNode logic = logicRoot[0];
                    LogicGraphSvgExporter svgExporter = new LogicGraphSvgExporter();
                    String svg = svgExporter.exportSvg(logic);
                    java.nio.file.Files.write(fc.getSelectedFile().toPath(), svg.getBytes("UTF-8"));
                    status.setText("SVG导出成功");
                } catch (Exception ex) {
                    status.setText("SVG导出失败: "+ex.getMessage());
                }
            }
        });

        // 添加节点
        addBtn.addActionListener(e -> {
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
            // 按类型填写参数，全部用下拉选择
            switch(type) {
                case FORALL: case EXISTS: {
                    String v = JOptionPane.showInputDialog(frame,"变量名(var):");
                    if (v==null) return;
                    // 集合名下拉
                    if (config[0]==null || config[0].patterns.isEmpty()) {
                        JOptionPane.showMessageDialog(frame, "config.xml未加载或无patterns，无法选择集合名", "错误", JOptionPane.ERROR_MESSAGE); return;
                    }
                    ConfigXmlLoader.PatternInfo[] patternArr = config[0].patterns.toArray(new ConfigXmlLoader.PatternInfo[0]);
                    ConfigXmlLoader.PatternInfo selPattern = (ConfigXmlLoader.PatternInfo)JOptionPane.showInputDialog(frame, "选择集合名(patterns):", "集合名", JOptionPane.PLAIN_MESSAGE, null, patternArr, patternArr[0]);
                    if (selPattern==null) return;
                    params.put("var", v);
                    params.put("in", selPattern.name);
                    // filter函数名下拉，前置“无”选项
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
                        // 选“无”则filter保持空
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
            LogicNode newNode = new LogicNode(type, nodeIdCounter[0]++); // nodeId 由后续 build/parse 统一分配
            newNode.params.putAll(params);
            newNode.paramList.addAll(paramList);
            newNode.filter.putAll(filter);
            newNode.filterParamList.addAll(filterParamList);
            ln.children.add(newNode);
            logic.SwingTreeUtil.saveExpandState(logicRoot[0], root, tree);
            logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
            ((DefaultTreeModel)tree.getModel()).reload();
            logic.SwingTreeUtil.restoreExpandState(logicRoot[0], root, tree);
            graphPanel.setLogicRoot(logicRoot[0]);
            // 实时校验
            validateAllNodes(logicRoot[0]);
            updateErrorStatusBar(logicRoot[0], status);
        });
        // 修改节点
        editBtn.addActionListener(e -> {
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
                    String v = JOptionPane.showInputDialog(frame,"变量名(var):", ln.params.getOrDefault("var",""));
                    if (v==null) return;
                    if (config[0]==null || config[0].patterns.isEmpty()) {
                        JOptionPane.showMessageDialog(frame, "config.xml未加载或无patterns，无法选择集合名", "错误", JOptionPane.ERROR_MESSAGE); return;
                    }
                    ConfigXmlLoader.PatternInfo[] patternArr = config[0].patterns.toArray(new ConfigXmlLoader.PatternInfo[0]);
                    // 默认选中原集合名
                    ConfigXmlLoader.PatternInfo selPattern = (ConfigXmlLoader.PatternInfo)JOptionPane.showInputDialog(frame, "选择集合名(patterns):", "集合名", JOptionPane.PLAIN_MESSAGE, null, patternArr, Arrays.stream(patternArr).filter(p->p.name.equals(ln.params.getOrDefault("in",""))).findFirst().orElse(patternArr[0]));
                    if (selPattern==null) return;
                    params.put("var", v);
                    params.put("in", selPattern.name);
                    // filter函数名下拉，前置“无”选项
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
                        // 选“无”则filter保持空
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
            // 3. 更新节点类型和参数
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
            // 实时校验
            validateAllNodes(logicRoot[0]);
            updateErrorStatusBar(logicRoot[0], status);
        });
        // 删除节点
        delBtn.addActionListener(e -> {
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
            validateAllNodes(logicRoot[0]);
            updateErrorStatusBar(logicRoot[0], status);
        });
        // 移动节点
        moveBtn.addActionListener(e -> {
            TreePath fromPath = tree.getSelectionPath();
            if (fromPath==null || logicRoot[0]==null) return;
            DefaultMutableTreeNode fromSel = (DefaultMutableTreeNode)fromPath.getLastPathComponent();
            if (fromSel==root) {
                JOptionPane.showMessageDialog(frame, "根节点不能移动。", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            LogicNode fromParent = TreeHelper.findParent(logicRoot[0], fromSel, root);
            LogicNode fromNode = TreeHelper.findNode(logicRoot[0], fromSel, root);
            if (fromNode==null) {
                JOptionPane.showMessageDialog(frame, "未找到选中节点对应的数据。", "错误", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // 构造所有可选目标节点（排除自身及子孙节点）
            java.util.List<DefaultMutableTreeNode> candidates = new java.util.ArrayList<>();
            TreeHelper.collectNodes(root, fromSel, candidates, false);
            if (candidates.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "没有可用于移动的目标节点。", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            DefaultMutableTreeNode[] arr = candidates.toArray(new DefaultMutableTreeNode[0]);
            DefaultMutableTreeNode toSel = (DefaultMutableTreeNode)JOptionPane.showInputDialog(frame, "选择目标父节点:", "移动到...", JOptionPane.PLAIN_MESSAGE, null, arr, arr[0]);
            if (toSel==null) return;
            LogicNode toNode = TreeHelper.findNode(logicRoot[0], toSel, root);
            if (toNode==null) {
                JOptionPane.showMessageDialog(frame, "未找到目标节点对应的数据。", "错误", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // 执行移动
            logic.SwingTreeUtil.saveExpandState(logicRoot[0], root, tree);
            if (fromParent!=null) {
                fromParent.children.remove(fromNode);
            } else {
                logicRoot[0].children.remove(fromNode);
            }
            toNode.children.add(fromNode);
            logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
            ((DefaultTreeModel)tree.getModel()).reload();
            logic.SwingTreeUtil.restoreExpandState(logicRoot[0], root, tree);
            graphPanel.setLogicRoot(logicRoot[0]);
            // 实时校验
            validateAllNodes(logicRoot[0]);
            updateErrorStatusBar(logicRoot[0], status);
        });
        
        // 交换节点
        // 整体交换（含子树）
        swapSubtreeBtn.addActionListener(e -> {
            TreePath fromPath = tree.getSelectionPath();
            if (fromPath==null || logicRoot[0]==null) return;
            DefaultMutableTreeNode fromSel = (DefaultMutableTreeNode)fromPath.getLastPathComponent();
            if (fromSel==root) {
                JOptionPane.showMessageDialog(frame, "根节点不能参与整体交换。", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            LogicNode fromParent = TreeHelper.findParent(logicRoot[0], fromSel, root);
            LogicNode fromNode = TreeHelper.findNode(logicRoot[0], fromSel, root);
            if (fromNode==null) {
                JOptionPane.showMessageDialog(frame, "未找到选中节点对应的数据。", "错误", JOptionPane.WARNING_MESSAGE);
                return;
            }
            // 构造所有可选目标节点（排除自身及子孙节点和自身和祖先）
            java.util.List<DefaultMutableTreeNode> candidates = new java.util.ArrayList<>();
            TreeHelper.collectNodes(root, fromSel, candidates, true);
            candidates.remove(fromSel);
            if (candidates.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "没有可用于整体交换的其他节点。", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            DefaultMutableTreeNode[] arr = candidates.toArray(new DefaultMutableTreeNode[0]);
            DefaultMutableTreeNode toSel = (DefaultMutableTreeNode)JOptionPane.showInputDialog(frame, "选择要整体交换的节点:", "整体交换", JOptionPane.PLAIN_MESSAGE, null, arr, arr[0]);
            if (toSel==null) return;
            LogicNode toParent = TreeHelper.findParent(logicRoot[0], toSel, root);
            LogicNode toNode = TreeHelper.findNode(logicRoot[0], toSel, root);
            if (toNode==null) {
                JOptionPane.showMessageDialog(frame, "未找到目标节点对应的数据。", "错误", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (fromParent==null || toParent==null) {
                JOptionPane.showMessageDialog(frame, "不能交换根节点。", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            int fromIdx = fromParent.children.indexOf(fromNode);
            int toIdx = toParent.children.indexOf(toNode);
            if (fromIdx==-1 || toIdx==-1) {
                JOptionPane.showMessageDialog(frame, "节点索引异常，无法交换。", "错误", JOptionPane.WARNING_MESSAGE);
                return;
            }
            logic.SwingTreeUtil.saveExpandState(logicRoot[0], root, tree);
            fromParent.children.set(fromIdx, toNode);
            toParent.children.set(toIdx, fromNode);
            logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
            ((DefaultTreeModel)tree.getModel()).reload();
            logic.SwingTreeUtil.restoreExpandState(logicRoot[0], root, tree);
            graphPanel.setLogicRoot(logicRoot[0]);
            // 实时校验
            validateAllNodes(logicRoot[0]);
            updateErrorStatusBar(logicRoot[0], status);
        });

        // 节点交换（仅交换节点内容，不动子树）
        swapNodeBtn.addActionListener(e -> {
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
            // 仅禁止根节点，其他节点均可交换
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
            logic.SwingTreeUtil.saveExpandState(logicRoot[0], root, tree);
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
            logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
            ((DefaultTreeModel)tree.getModel()).reload();
            logic.SwingTreeUtil.restoreExpandState(logicRoot[0], root, tree);
            graphPanel.setLogicRoot(logicRoot[0]);
            // 实时校验
            validateAllNodes(logicRoot[0]);
            updateErrorStatusBar(logicRoot[0], status);
        });

        // 预加载空的 XML 内容
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();
            Element rootElement = doc.createElement("formula");
            doc.appendChild(rootElement);
            
            nodeIdCounter = new int[]{1};
            LogicNode temp = logic.LogicXmlUtil.parseXml(rootElement, nodeIdCounter);
            String err = logic.LogicValidator.validate(temp);
            if (err != null) {
                status.setText("预加载XML校验失败：" + err);
            } else {
                logicRoot[0] = temp;
                root.setUserObject(logicRoot[0].toString());
                root.removeAllChildren();
                logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
                ((DefaultTreeModel)tree.getModel()).reload();
                graphPanel.setLogicRoot(logicRoot[0]);
                status.setText("已预加载空约束公式");
            }
        } catch (Exception ex) {
            status.setText("预加载XML失败: "+ex.getMessage());
        }

        frame.setVisible(true);
    }
}
