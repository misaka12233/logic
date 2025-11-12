
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
        // 在图上方显示当前 Rule 的 ID 节点 content（只读标签）
        JLabel idLabel = new JLabel("ID: null");
        // 点击JTree空白处取消选中；同时支持点击注释角标切换注释显示
        tree.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                int selRow = tree.getRowForLocation(e.getX(), e.getY());
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path == null || selRow == -1) {
                    tree.clearSelection();
                    return;
                }
                // 检查是否点击在注释角标区域（右下角小圆点）
                Rectangle bounds = tree.getPathBounds(path);
                if (bounds != null) {
                    int size = 10; // 与渲染器保持一致的尺寸
                    int bx = bounds.x + bounds.width - size - 4; // 靠右
                    int by = bounds.y + (bounds.height - size) / 2; // 垂直居中
                    java.awt.Point p = e.getPoint();
                    boolean inBadge = (p.x >= bx && p.x <= bx + size && p.y >= by && p.y <= by + size);
                    if (inBadge) {
                        // badge clicked -> 切换该节点注释显示
                        DefaultMutableTreeNode sel = (DefaultMutableTreeNode)path.getLastPathComponent();
                        LogicNode ln = TreeHelper.findNode(logicRoot[0], sel, root);
                        if (ln != null && ln.comments != null && !ln.comments.isEmpty()) {
                            // 仅切换该节点的注释显示并刷新该节点的渲染，避免重建整棵树导致展开状态变化
                            ln.showComments = !ln.showComments;
                            DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
                            model.nodeChanged(sel);
                            // 确保界面刷新（仅重绘树即可）
                            tree.repaint();
                        }
                        return;
                    }
                }
                // 非 badge 区域点击：如点击空白则取消选中
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
                c.putClientProperty("hasComments", false);
                if (value instanceof DefaultMutableTreeNode) {
                    DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
                    Object uo = node.getUserObject();
                    LogicNode ln = null;
                    if (uo instanceof LogicNode) ln = (LogicNode)uo;
                    // 文本与编号前缀由 LogicNode.toString() 提供，但我们需要把编号着色：
                    // - RULES: 无编号
                    // - 其它: 前置 [localId] 用蓝色
                    String full = (uo == null) ? "" : uo.toString();
                    java.util.function.Function<String,String> esc = (str) -> str.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");
                    String prefix = "";
                    String content = full;
                    if (full.startsWith("(")) {
                        int idx = full.indexOf(")");
                        if (idx > 0) {
                            prefix = full.substring(0, idx+1);
                            content = full.substring(idx+1).trim();
                        }
                    } else if (full.startsWith("[")) {
                        int idx = full.indexOf("]");
                        if (idx > 0) {
                            prefix = full.substring(0, idx+1);
                            content = full.substring(idx+1).trim();
                        }
                    }
                    // 将 content 按空格分词，对关键字上色
                    String[] parts = content.split(" ");
                    StringBuilder contentHtml = new StringBuilder();
                    for (int i=0;i<parts.length;i++) {
                        String p = parts[i];
                        String clean = p.replaceAll("[^A-Za-z]", "");
                        String lower = clean.toLowerCase();
                        boolean isKeyword = "forall".equals(lower) || "exists".equals(lower) || "and".equals(lower) || "or".equals(lower) || "with".equals(lower) || "in".equals(lower) || "formula".equals(lower) || "implies".equals(lower) || "not".equals(lower) || "rules".equals(lower) || "rule".equals(lower);
                        if (isKeyword) contentHtml.append("<span style='color:purple;'>").append(esc.apply(p)).append("</span>");
                        else contentHtml.append("<span style='color:black;'>").append(esc.apply(p)).append("</span>");
                        if (i<parts.length-1) contentHtml.append(" ");
                    }
                    StringBuilder html = new StringBuilder();
                    html.append("<html>");
                    if (!prefix.isEmpty()) {
                        String color = "#3C78FF"; //蓝色
                        html.append("<span style='color:").append(color).append(";'>").append(esc.apply(prefix)).append("</span> ");
                    }
                    html.append(contentHtml.toString());
                    if (ln != null && ln.comments != null && !ln.comments.isEmpty()) {
                        c.putClientProperty("hasComments", true);
                        c.putClientProperty("badgeFlipped", ln.showComments);
                        html.append("&nbsp;&nbsp;&nbsp;&nbsp;");
                        if (ln.showComments) {
                            String commentHtml = esc.apply(ln.getCommentsAsHtml()).replace("\n","<br/>");
                            html.append("<div style='font-size:smaller;color:#666;margin-top:6px;'>").append(commentHtml).append("</div>");
                        }
                    } else {
                        html.append("&nbsp;&nbsp;");
                    }
                    html.append("</html>");
                    c.setText(html.toString());
                    Integer nodeId = (ln == null) ? null : ln.nodeId;
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
                Boolean hasComments = (Boolean)this.getClientProperty("hasComments");
                Boolean badgeFlipped = (Boolean)this.getClientProperty("badgeFlipped");
                if (hasComments != null && hasComments) {
                    // draw small triangle at right side (fixed vertical center); when badgeFlipped==true draw left-pointing triangle
                    g.setColor(new Color(255,140,0));
                    int size = 10;
                    int x = getWidth() - size - 4; // 靠右，不覆盖文本
                    int y = (getHeight() - size) / 2; // 垂直居中
                    int[] ys = new int[] { y, y + size, y + size / 2 };
                    int[] xs;
                    if (badgeFlipped != null && badgeFlipped) {
                        // left-pointing triangle within same bbox
                        xs = new int[] { x + size, x + size, x };
                    } else {
                        // right-pointing
                        xs = new int[] { x, x, x + size };
                    }
                    g.fillPolygon(xs, ys, 3);
                }
            }
        });
        JScrollPane scroll = new JScrollPane(tree);
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(scroll);
        // 右侧容器：顶部为 idLabel，中心为 graphPanel
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.add(idLabel, BorderLayout.NORTH);
        rightPanel.add(graphPanel, BorderLayout.CENTER);
        splitPane.setRightComponent(rightPanel);
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
        // 编辑菜单
        JMenu editMenu = new JMenu("编辑");
        bar.add(editMenu);
        // 初始无选中，编辑菜单禁用
        editMenu.setEnabled(false);
        frame.setJMenuBar(bar);

        // 编辑菜单添加所有节点操作
        JMenuItem addItem = new JMenuItem("添加");
        JMenuItem editItem = new JMenuItem("修改");
        JMenuItem delItem = new JMenuItem("删除");
        JMenuItem moveItem = new JMenuItem("移动");
        JMenuItem swapSubtreeItem = new JMenuItem("交换（全子树）");
        JMenuItem swapNodeItem = new JMenuItem("交换（单节点）");
        JMenuItem copySingleItem = new JMenuItem("复制（单节点）");
        JMenuItem copySubtreeItem = new JMenuItem("复制（全子树）");
        JMenuItem pasteItem = new JMenuItem("粘贴");
        JMenuItem renameVarItem = new JMenuItem("变量重命名");
        JMenuItem editCommentsItem = new JMenuItem("编辑注释");
        editMenu.addSeparator();
        editMenu.add(addItem); editMenu.add(editItem); editMenu.add(delItem); editMenu.add(moveItem);
        editMenu.add(swapSubtreeItem); editMenu.add(swapNodeItem); editMenu.add(copySingleItem); editMenu.add(copySubtreeItem); editMenu.add(pasteItem); editMenu.add(renameVarItem);
        editMenu.add(editCommentsItem);

        addItem.addActionListener(new AddNodeAction(frame, tree, root, logicRoot, nodeIdCounter, config, graphPanel, status, logic.LogicValidator.errorNodeMap));
        editItem.addActionListener(new EditNodeAction(frame, tree, root, logicRoot, config, graphPanel, status, logic.LogicValidator.errorNodeMap));
        delItem.addActionListener(new DeleteNodeAction(frame, tree, root, logicRoot, graphPanel, status, logic.LogicValidator.errorNodeMap));
        moveItem.addActionListener(new MoveNodeAction(frame, tree, root, logicRoot, graphPanel, status, logic.LogicValidator.errorNodeMap));
        swapSubtreeItem.addActionListener(new SwapSubtreeAction(frame, tree, root, logicRoot, graphPanel, status, logic.LogicValidator.errorNodeMap));
        swapNodeItem.addActionListener(new SwapNodeAction(frame, tree, root, logicRoot, graphPanel, status, logic.LogicValidator.errorNodeMap));
        copySingleItem.addActionListener(new action.CopySingleAction(frame, tree, root, logicRoot, graphPanel));
        copySubtreeItem.addActionListener(new action.CopySubtreeAction(frame, tree, root, logicRoot, graphPanel));
        pasteItem.addActionListener(new action.PasteAction(frame, tree, root, logicRoot, nodeIdCounter, graphPanel, status, logic.LogicValidator.errorNodeMap));
        renameVarItem.addActionListener(new action.RenameVarAction(frame, tree, root, logicRoot, graphPanel, status, logic.LogicValidator.errorNodeMap));
        editCommentsItem.addActionListener(new action.EditCommentsAction(frame, tree, root, logicRoot, graphPanel, status, logic.LogicValidator.errorNodeMap));

        // 新增视图菜单，包含展开/收起操作
        JMenu viewMenu = new JMenu("视图");
        JMenuItem expandItem = new JMenuItem("全展开");
        JMenuItem collapseItem = new JMenuItem("全收起");
        viewMenu.add(expandItem); viewMenu.add(collapseItem);
        bar.add(viewMenu);
        // 初始无选中，视图菜单禁用
        viewMenu.setEnabled(false);
        // 在视图菜单右侧添加一个撤销按钮，便于快速访问
        java.awt.event.ActionListener undoListener = new action.UndoAction(tree, root, logicRoot, nodeIdCounter, graphPanel, status);
        JButton undoButton = new JButton("撤销");
        undoButton.setToolTipText("撤销 (Ctrl+Z)");
        // 让按钮更紧凑，确保在菜单栏上完整显示
        undoButton.setMargin(new Insets(2,6,2,6));
        undoButton.setEnabled(logic.UndoManager.isUndoAvailable());
        undoButton.addActionListener(undoListener);
        bar.add(undoButton);
        // 更新 UndoManager 的监听器以同时维护按钮的 enabled 状态
        logic.UndoManager.addListener(() -> {
            javax.swing.SwingUtilities.invokeLater(() -> {
                boolean avail = logic.UndoManager.isUndoAvailable();
                undoButton.setEnabled(avail);
            });
        });
        // 注册全局快捷键 Ctrl/Cmd+Z 到 root pane，触发与按钮相同的 UndoAction
        try {
            javax.swing.KeyStroke ks = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_Z, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
            javax.swing.InputMap im = frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
            im.put(ks, "undoShortcut");
            frame.getRootPane().getActionMap().put("undoShortcut", new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    undoListener.actionPerformed(new java.awt.event.ActionEvent(undoButton, java.awt.event.ActionEvent.ACTION_PERFORMED, "shortcut"));
                }
            });
            // 注册 Ctrl+C -> 复制（单节点），Ctrl+V -> 粘贴
            javax.swing.KeyStroke ksC = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_C, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
            javax.swing.KeyStroke ksV = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx());
            // 给菜单项设置 accelerator（显示在菜单里并在窗口有焦点时响应）
            copySingleItem.setAccelerator(ksC);
            pasteItem.setAccelerator(ksV);
            // 额外在 RootPane 的 InputMap/ActionMap 上绑定，确保在任何组件时也可触发
            frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksC, "copyShortcut");
            frame.getRootPane().getActionMap().put("copyShortcut", new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) { copySingleItem.doClick(); }
            });
            frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(ksV, "pasteShortcut");
            frame.getRootPane().getActionMap().put("pasteShortcut", new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) { pasteItem.doClick(); }
            });
            // 同时在树组件上也绑定，便于当树有键盘焦点时响应
            tree.getInputMap(JComponent.WHEN_FOCUSED).put(ksC, "copyShortcutTree");
            tree.getActionMap().put("copyShortcutTree", new javax.swing.AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) { copySingleItem.doClick(); } });
            tree.getInputMap(JComponent.WHEN_FOCUSED).put(ksV, "pasteShortcutTree");
            tree.getActionMap().put("pasteShortcutTree", new javax.swing.AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) { pasteItem.doClick(); } });
        } catch (Throwable t) {
            // ignore binding failures
        }
        expandItem.addActionListener(e -> {
            TreePath path = tree.getSelectionPath();
            if (path == null) return;
            DefaultMutableTreeNode sel = (DefaultMutableTreeNode) path.getLastPathComponent();
            TreeHelper.expandSubtree(tree, sel);
        });
        collapseItem.addActionListener(e -> {
            TreePath path = tree.getSelectionPath();
            if (path == null) return;
            DefaultMutableTreeNode sel = (DefaultMutableTreeNode) path.getLastPathComponent();
            TreeHelper.collapseSubtree(tree, sel);
        });

        // 构建节点右键菜单：包含编辑菜单和视图菜单中的操作
        final JPopupMenu nodePopup = new JPopupMenu();
        java.util.List<JMenuItem> popupItems = java.util.Arrays.asList(addItem, editItem, delItem, moveItem, swapSubtreeItem, swapNodeItem, copySingleItem, copySubtreeItem, pasteItem, renameVarItem, editCommentsItem, expandItem, collapseItem);
        for (JMenuItem src : popupItems) {
            JMenuItem pi = new JMenuItem(src.getText());
            // 通过触发原菜单项的 doClick() 来复用其行为和现有监听器
            pi.addActionListener(ev -> {
                // 确保选中路径在触发时已经设置
                src.doClick();
            });
            nodePopup.add(pi);
        }

        // 右键点击：选中节点并显示右键菜单（兼容不同平台的 popupTrigger）
        tree.addMouseListener(new java.awt.event.MouseAdapter() {
            private void tryShowPopup(java.awt.event.MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                int selRow = tree.getRowForLocation(e.getX(), e.getY());
                if (path != null && selRow != -1) {
                    // 选中被右击的节点
                    tree.setSelectionPath(path);
                    // 在显示弹出菜单前，同步各项的 enabled 状态（与原菜单项保持一致）
                    for (int i=0;i<popupItems.size() && i<nodePopup.getComponentCount();i++) {
                        java.awt.Component comp = nodePopup.getComponent(i);
                        if (comp instanceof JMenuItem) {
                            ((JMenuItem)comp).setEnabled(popupItems.get(i).isEnabled());
                        }
                    }
                    // 在该位置显示弹出菜单
                    nodePopup.show(tree, e.getX(), e.getY());
                }
            }
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                if (javax.swing.SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) tryShowPopup(e);
            }
            @Override
            public void mouseReleased(java.awt.event.MouseEvent e) {
                if (e.isPopupTrigger()) tryShowPopup(e);
            }
        });

        // 选中树节点时高亮右侧图节点，并根据选中节点启用/禁用菜单项
        tree.addTreeSelectionListener(e -> {
            TreePath path = tree.getSelectionPath();
            // 默认：无选中时禁用 编辑/视图 菜单
            boolean hasSelection = (path != null && logicRoot[0] != null);
            editMenu.setEnabled(hasSelection);
            viewMenu.setEnabled(hasSelection);
            if (path != null && logicRoot[0] != null) {
                DefaultMutableTreeNode sel = (DefaultMutableTreeNode)path.getLastPathComponent();
                LogicNode ln = TreeHelper.findNode(logicRoot[0], sel, root);
                if (ln != null) {
                    graphPanel.setHighlightNodeId(ln.nodeId);
                    // 设置缩放倍率为 1.5 并同步重绘（确保 nodeBounds 在新 scale 下已更新），然后居中选中节点
                    graphPanel.setScale(1.5);
                    try {
                        if (graphPanel.getWidth() > 0 && graphPanel.getHeight() > 0) {
                            graphPanel.paintImmediately(0, 0, graphPanel.getWidth(), graphPanel.getHeight());
                        }
                    } catch (Exception ex) {
                        // ignore
                    }
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
                    // 更新 idLabel
                    Integer hid = graphPanel.getHighlightNodeId();
                    if (hid != null) {
                        LogicNode highlighted = logic.LogicUiUtil.findNodeById(logicRoot[0], hid);
                        if (highlighted != null) {
                            LogicNode ruleNode = logic.LogicUiUtil.findRuleForNode(logicRoot[0], highlighted.nodeId);
                            if (ruleNode != null) {
                                LogicNode idNode = null;
                                for (LogicNode c : ruleNode.children) if (c.type == LogicNode.NodeType.ID) { idNode = c; break; }
                                if (idNode != null && idNode.content != null) idLabel.setText("ID: " + idNode.content);
                                else idLabel.setText("ID: null");
                            } else {
                                idLabel.setText("ID: null");
                            }
                        } else {
                            idLabel.setText("ID: null");
                        }
                    } else {
                        idLabel.setText("ID: null");
                    }

                    // 根据选中节点类型控制菜单项可用性
                    // 默认全部启用，然后按规则关闭特定操作
                    editItem.setEnabled(true);
                    delItem.setEnabled(true);
                    moveItem.setEnabled(true);
                    swapSubtreeItem.setEnabled(true);
                    swapNodeItem.setEnabled(true);
                    copySingleItem.setEnabled(true);
                    copySubtreeItem.setEnabled(true);
                    renameVarItem.setEnabled(true);
                    // 若选中类型为 RULES：禁止 编辑/删除/复制/移动/交换/重命名变量
                    if (ln.type == LogicNode.NodeType.RULES) {
                        editItem.setEnabled(false);
                        delItem.setEnabled(false);
                        moveItem.setEnabled(false);
                        swapSubtreeItem.setEnabled(false);
                        swapNodeItem.setEnabled(false);
                        copySingleItem.setEnabled(false);
                        copySubtreeItem.setEnabled(false);
                        renameVarItem.setEnabled(false);
                    }
                    // 若选中类型为 RULE/ID/GROUP_BY/UNKNOWN：禁止移动与交换
                    if (ln.type == LogicNode.NodeType.RULE || ln.type == LogicNode.NodeType.ID || ln.type == LogicNode.NodeType.GROUP_BY || ln.type == LogicNode.NodeType.UNKNOWN) {
                        moveItem.setEnabled(false);
                        swapSubtreeItem.setEnabled(false);
                        swapNodeItem.setEnabled(false);
                    }
                    // 变量重命名仅在 FORALL/EXISTS 可用
                    if (!(ln.type == LogicNode.NodeType.FORALL || ln.type == LogicNode.NodeType.EXISTS)) {
                        renameVarItem.setEnabled(false);
                    }
                } else {
                    graphPanel.setHighlightNodeId(null);
                    idLabel.setText("ID: null");
                    // 没有找到对应逻辑节点：禁用编辑/视图菜单
                    editMenu.setEnabled(false);
                    viewMenu.setEnabled(false);
                }
            } else {
                graphPanel.setHighlightNodeId(null);
                idLabel.setText("ID: null");
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
                    LogicNode logic = logicRoot[0];
                    LogicGraphSvgExporter svgExporter = new LogicGraphSvgExporter();
                    String svg = svgExporter.exportSvg(logic, graphPanel.getHighlightNodeId());
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
            Element rootElement = doc.createElement("rules");
            doc.appendChild(rootElement);
            
            LogicNode temp = logic.LogicXmlUtil.parseXml(rootElement, nodeIdCounter);
            logicRoot[0] = temp;
            root.setUserObject(logicRoot[0].toString());
            root.removeAllChildren();
            logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
            ((DefaultTreeModel)tree.getModel()).reload();
            graphPanel.setLogicRoot(logicRoot[0]);
            // 初始时更新 idLabel：若存在第一个 rule 且其包含 ID 节点则显示该 ID 的 content，否则 null
            LogicNode firstRule = null;
            for (LogicNode c : logicRoot[0].children) { if (c.type == LogicNode.NodeType.RULE) { firstRule = c; break; } }
            if (firstRule != null) {
                LogicNode idNode = null;
                for (LogicNode c : firstRule.children) if (c.type == LogicNode.NodeType.ID) { idNode = c; break; }
                if (idNode != null && idNode.content != null) idLabel.setText("ID: " + idNode.content);
                else idLabel.setText("ID: null");
            } else {
                idLabel.setText("ID: null");
            }
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
