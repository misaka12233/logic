
package logic;
import javax.swing.*;
import javax.swing.tree.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import java.awt.*;
import action.*;

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

    // 递归展开子树（辅助方法，避免在 main 中使用泛型数组/递归 lambda）
    public static void expandSubtree(JTree tree, DefaultMutableTreeNode node) {
        TreePath path = new TreePath(node.getPath());
        tree.expandPath(path);
        for (int i = 0; i < node.getChildCount(); i++) {
            javax.swing.tree.TreeNode tn = node.getChildAt(i);
            if (tn instanceof DefaultMutableTreeNode) expandSubtree(tree, (DefaultMutableTreeNode) tn);
        }
    }

    // 递归收起子树（先收起子孙，再收起自身）
    public static void collapseSubtree(JTree tree, DefaultMutableTreeNode node) {
        for (int i = 0; i < node.getChildCount(); i++) {
            javax.swing.tree.TreeNode tn = node.getChildAt(i);
            if (tn instanceof DefaultMutableTreeNode) collapseSubtree(tree, (DefaultMutableTreeNode) tn);
        }
        TreePath path = new TreePath(node.getPath());
        tree.collapsePath(path);
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
        // 我们自己处理关闭事件以便提示未保存状态并删除临时文件
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
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
                    // 颜色渲染：编号蓝色，关键字紫色，其他黑色
                    if (s.startsWith("[")) {
                        int idx = s.indexOf("]");
                        if (idx > 0) {
                            String idStr = s.substring(0, idx+1);
                            String content = s.substring(idx+1).trim();
                            // 简单 HTML 转义
                            java.util.function.Function<String,String> esc = (str) -> str.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
                            // 将 content 按空格分词，对关键字上色
                            String[] parts = content.split(" ");
                            StringBuilder contentHtml = new StringBuilder();
                            for (int i=0;i<parts.length;i++) {
                                String p = parts[i];
                                String clean = p.replaceAll("[^A-Za-z]", "");
                                String lower = clean.toLowerCase();
                                boolean isKeyword = "forall".equals(lower) || "exists".equals(lower) || "and".equals(lower) || "or".equals(lower) || "with".equals(lower) || "in".equals(lower) || "formula".equals(lower) || "implies".equals(lower) || "not".equals(lower);
                                if (isKeyword) contentHtml.append("<span style='color:purple;'>").append(esc.apply(p)).append("</span>");
                                else contentHtml.append("<span style='color:black;'>").append(esc.apply(p)).append("</span>");
                                if (i<parts.length-1) contentHtml.append(" ");
                            }
                            c.setText("<html><span style='color:#3C78FF;'>"+esc.apply(idStr)+"</span> "+contentHtml.toString()+"</html>");
                        }
                    }
                            boolean markError = false;
                            if (nodeId != null && logic.LogicValidator.errorNodeMap.containsKey(nodeId)) {
                                markError = true;
                            } else {
                                // 如果当前节点折叠且其子孙包含错误，则标红当前节点以提示用户
                                if (!tree.isExpanded(new TreePath(node.getPath()))) {
                                    if (SwingTreeUtil.swingSubtreeHasError(node)) markError = true;
                                }
                            }
                            if (markError) c.putClientProperty("errorLine", true);
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
        // 编辑菜单，包含撤销
        JMenu editMenu = new JMenu("编辑");
        JMenuItem undoItem = new JMenuItem("撤销");
        undoItem.setEnabled(logic.UndoManager.isUndoAvailable());
        undoItem.addActionListener(new action.UndoAction(tree, root, logicRoot, nodeIdCounter, graphPanel, status));
        // 订阅 UndoManager 变化以更新菜单项状态
        logic.UndoManager.addListener(() -> {
            // Swing 事件线程更新 UI
            javax.swing.SwingUtilities.invokeLater(() -> {
                undoItem.setEnabled(logic.UndoManager.isUndoAvailable());
            });
        });
        editMenu.add(undoItem);
        bar.add(editMenu);
        frame.setJMenuBar(bar);

        // 编辑菜单添加所有节点操作
        JMenuItem addItem = new JMenuItem("添加");
        JMenuItem editItem = new JMenuItem("修改");
        JMenuItem delItem = new JMenuItem("删除");
        JMenuItem moveItem = new JMenuItem("移动");
        JMenuItem swapSubtreeItem = new JMenuItem("交换（全子树）");
        JMenuItem swapNodeItem = new JMenuItem("交换（单节点）");
        JMenuItem copyNodeItem = new JMenuItem("复制-粘贴（全子树）");
        JMenuItem renameVarItem = new JMenuItem("变量重命名");
        editMenu.addSeparator();
        editMenu.add(addItem); editMenu.add(editItem); editMenu.add(delItem); editMenu.add(moveItem);
        editMenu.add(swapSubtreeItem); editMenu.add(swapNodeItem); editMenu.add(copyNodeItem); editMenu.add(renameVarItem);

        addItem.addActionListener(new AddNodeAction(frame, tree, root, logicRoot, nodeIdCounter, config, graphPanel, status, logic.LogicValidator.errorNodeMap));
        editItem.addActionListener(new EditNodeAction(frame, tree, root, logicRoot, config, graphPanel, status, logic.LogicValidator.errorNodeMap));
        delItem.addActionListener(new DeleteNodeAction(frame, tree, root, logicRoot, graphPanel, status, logic.LogicValidator.errorNodeMap));
        moveItem.addActionListener(new MoveNodeAction(frame, tree, root, logicRoot, graphPanel, status, logic.LogicValidator.errorNodeMap));
        swapSubtreeItem.addActionListener(new SwapSubtreeAction(frame, tree, root, logicRoot, graphPanel, status, logic.LogicValidator.errorNodeMap));
        swapNodeItem.addActionListener(new SwapNodeAction(frame, tree, root, logicRoot, graphPanel, status, logic.LogicValidator.errorNodeMap));
        copyNodeItem.addActionListener(new CopyNodeAction(frame, tree, root, logicRoot, nodeIdCounter, graphPanel, status, logic.LogicValidator.errorNodeMap));
        renameVarItem.addActionListener(new action.RenameVarAction(frame, tree, root, logicRoot, graphPanel, status, logic.LogicValidator.errorNodeMap));

        // 新增视图菜单，包含展开/收起操作
        JMenu viewMenu = new JMenu("视图");
        JMenuItem expandItem = new JMenuItem("展开");
        JMenuItem collapseItem = new JMenuItem("收起");
        viewMenu.add(expandItem); viewMenu.add(collapseItem);
        bar.add(viewMenu);
        expandItem.addActionListener(e -> {
            TreePath path = tree.getSelectionPath();
            if (path == null) return;
            DefaultMutableTreeNode sel = (DefaultMutableTreeNode) path.getLastPathComponent();
            ConstraintVisualizer.expandSubtree(tree, sel);
        });
        collapseItem.addActionListener(e -> {
            TreePath path = tree.getSelectionPath();
            if (path == null) return;
            DefaultMutableTreeNode sel = (DefaultMutableTreeNode) path.getLastPathComponent();
            ConstraintVisualizer.collapseSubtree(tree, sel);
        });

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
        open.addActionListener(new OpenXmlAction(frame, tree, root, logicRoot, nodeIdCounter, graphPanel, status));
        save.addActionListener(new SaveXmlAction(frame, logicRoot, status));

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

        // 预加载空的 XML 内容
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();
            Element rootElement = doc.createElement("formula");
            doc.appendChild(rootElement);
            
            LogicNode temp = logic.LogicXmlUtil.parseXml(rootElement, nodeIdCounter);
            logicRoot[0] = temp;
            root.setUserObject(logicRoot[0].toString());
            root.removeAllChildren();
            logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
            ((DefaultTreeModel)tree.getModel()).reload();
            graphPanel.setLogicRoot(logicRoot[0]);
            status.setText("已预加载空约束公式");
        } catch (Exception ex) {
            status.setText("预加载XML失败: "+ex.getMessage());
        }

        // 退出时提示并删除临时文件
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                // 若有未保存更改，提示用户
                if (!logic.UndoManager.isSaved()) {
                    int r = JOptionPane.showConfirmDialog(frame, "当前有未保存的更改，确认退出并丢弃未保存更改吗?", "未保存确认", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (r != JOptionPane.YES_OPTION) {
                        // 取消退出
                        return;
                    }
                }
                // 用户确认退出或已保存：删除临时文件并退出
                logic.UndoManager.clearTemporaryFiles();
                frame.dispose();
                System.exit(0);
            }
        });
        frame.setVisible(true);
    }
}
