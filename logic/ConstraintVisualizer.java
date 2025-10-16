
package logic;
import javax.swing.*;
import javax.swing.tree.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import java.awt.*;
import java.util.*;
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
        open.addActionListener(new OpenXmlAction(frame, tree, root, logicRoot, nodeIdCounter, graphPanel, status, errorNodeMap));
        save.addActionListener(new SaveXmlAction(frame, logicRoot, status));
        addBtn.addActionListener(new AddNodeAction(frame, tree, root, logicRoot, nodeIdCounter, config, graphPanel, status, errorNodeMap));
        editBtn.addActionListener(new EditNodeAction(frame, tree, root, logicRoot, config, graphPanel, status, errorNodeMap));
        delBtn.addActionListener(new DeleteNodeAction(frame, tree, root, logicRoot, graphPanel, status, errorNodeMap));
        moveBtn.addActionListener(new MoveNodeAction(frame, tree, root, logicRoot, graphPanel, status, errorNodeMap));
        swapSubtreeBtn.addActionListener(new SwapSubtreeAction(frame, tree, root, logicRoot, graphPanel, status, errorNodeMap));
        swapNodeBtn.addActionListener(new SwapNodeAction(frame, tree, root, logicRoot, graphPanel, status, errorNodeMap));
        copyNodeBtn.addActionListener(new CopyNodeAction(frame, tree, root, logicRoot, nodeIdCounter, graphPanel, status, errorNodeMap));

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
