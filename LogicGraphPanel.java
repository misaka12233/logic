import javax.swing.*;
import java.awt.*;
import java.util.*;
import logic.LogicNode;

/**
 * 逻辑树有向图可视化面板
 * - 自动布局树结构为有向图
 * - 支持高亮选中节点
 */
public class LogicGraphPanel extends JPanel {
    // 获取指定nodeId的节点矩形（最新一次paint后有效）
    public Rectangle getNodeBounds(int nodeId) {
        for (Map.Entry<LogicNode, Rectangle> e : nodeBounds.entrySet()) {
            if (e.getKey().nodeId == nodeId) return e.getValue();
        }
        return null;
    }
    private LogicNode root;
    private Integer highlightNodeId = null;
    private Map<LogicNode, Rectangle> nodeBounds = new HashMap<>();
    private Map<LogicNode, Dimension> nodeSizes = new HashMap<>();
    private int vGap = 40;
    private int hGap = 24;
    // 缩放与平移
    private double scale = 1.0;
    private int offsetX = 0, offsetY = 0;
    private int dragStartX = 0, dragStartY = 0;
    private boolean dragging = false;

    public void setScale(double scale) {
        this.scale = Math.max(0.1, Math.min(5.0, scale));
        repaint();
    }
    public double getScale() { return scale; }
    public void setOffset(int x, int y) {
        this.offsetX = x; this.offsetY = y; repaint();
    }
    public int getOffsetX() { return offsetX; }
    public int getOffsetY() { return offsetY; }

    public LogicGraphPanel() {
        setPreferredSize(new Dimension(1200, 1200));
        setBackground(Color.WHITE);
        // 鼠标缩放
        addMouseWheelListener(e -> {
            double oldScale = scale;
            if (e.getPreciseWheelRotation() < 0) setScale(scale * 1.1);
            else setScale(scale / 1.1);
            // 缩放时以鼠标为中心调整偏移
            int mx = e.getX(), my = e.getY();
            offsetX = (int)((offsetX - mx) * (scale/oldScale) + mx);
            offsetY = (int)((offsetY - my) * (scale/oldScale) + my);
            repaint();
        });
        // 鼠标拖动画布
        addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) {
                dragging = true;
                dragStartX = e.getX() - offsetX;
                dragStartY = e.getY() - offsetY;
            }
            public void mouseReleased(java.awt.event.MouseEvent e) {
                dragging = false;
            }
        });
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseDragged(java.awt.event.MouseEvent e) {
                if (dragging) {
                    offsetX = e.getX() - dragStartX;
                    offsetY = e.getY() - dragStartY;
                    repaint();
                }
            }
        });
    }

    public void setLogicRoot(LogicNode root) {
        this.root = root;
        repaint();
    }

    public void setHighlightNodeId(Integer nodeId) {
        this.highlightNodeId = nodeId;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D)g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(offsetX, offsetY);
        g2.scale(scale, scale);
        nodeBounds.clear();
        nodeSizes.clear();
        if (root == null) return;
        // 先计算所有节点的自适应尺寸
        calcNodeSizes(g2, root);
        // 计算布局
        int panelWidth = (int)(getWidth() / scale);
        int[] y = {vGap};
        layoutTree(root, panelWidth / 2, y, g2);
        // 绘制连线
        drawEdges(g2, root);
        // 绘制节点
        drawNodes(g2, root);
    }

    // 递归计算每个节点的自适应尺寸
    private void calcNodeSizes(Graphics g, LogicNode node) {
        String text = node.toString();
        FontMetrics fm = g.getFontMetrics();
        int width = fm.stringWidth(text) + 24;
        int height = fm.getHeight() + 16;
        nodeSizes.put(node, new Dimension(width, height));
        for (LogicNode child : node.children) {
            calcNodeSizes(g, child);
        }
    }

    // 递归布局，返回本子树的宽度
    private int layoutTree(LogicNode node, int centerX, int[] y, Graphics g) {
        int thisY = y[0];
        int childCount = node.children.size();
        Dimension size = nodeSizes.getOrDefault(node, new Dimension(120,36));
        int nodeWidth = size.width;
        int nodeHeight = size.height;
        int childY = thisY + nodeHeight + vGap;
        int totalChildWidth = 0;
        int[] subtreeWidths = new int[childCount];
        // 递归计算每个子树宽度
        for (int i = 0; i < childCount; i++) {
            int[] dummyY = {childY};
            subtreeWidths[i] = layoutTree(node.children.get(i), 0, dummyY, g); // 先算宽度，不布局
            totalChildWidth += subtreeWidths[i];
        }
        if (childCount > 0) totalChildWidth += (childCount - 1) * hGap;
        int subtreeWidth = Math.max(nodeWidth, totalChildWidth);
        int startX = centerX - subtreeWidth / 2;
        // 再布局子节点
        int curX = startX;
        for (int i = 0; i < childCount; i++) {
            int childCenter = curX + subtreeWidths[i] / 2;
            int[] childYArr = {childY};
            layoutTree(node.children.get(i), childCenter, childYArr, g);
            curX += subtreeWidths[i] + hGap;
        }
        // 记录本节点位置
        nodeBounds.put(node, new Rectangle(centerX - nodeWidth / 2, thisY, nodeWidth, nodeHeight));
        return subtreeWidth;
    }

    private void drawEdges(Graphics g, LogicNode node) {
        Rectangle from = nodeBounds.get(node);
        Dimension fromSize = nodeSizes.getOrDefault(node, new Dimension(120,36));
        int fromW = fromSize.width, fromH = fromSize.height;
        for (LogicNode child : node.children) {
            Rectangle to = nodeBounds.get(child);
            Dimension toSize = nodeSizes.getOrDefault(child, new Dimension(120,36));
            int toW = toSize.width;
            if (from != null && to != null) {
                drawArrow(g, from.x + fromW / 2, from.y + fromH, to.x + toW / 2, to.y);
            }
            drawEdges(g, child);
        }
    }

    private void drawNodes(Graphics g, LogicNode node) {
        Rectangle rect = nodeBounds.get(node);
        if (rect == null) return;
        Dimension size = nodeSizes.getOrDefault(node, new Dimension(120,36));
        int nodeWidth = size.width;
        int nodeHeight = size.height;
        Graphics2D g2 = (Graphics2D) g;
        if (highlightNodeId != null && node.nodeId == highlightNodeId) {
            g2.setColor(new Color(60, 120, 255)); // 蓝色
            g2.fillRoundRect(rect.x - 4, rect.y - 4, rect.width + 8, rect.height + 8, 16, 16);
        }
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(rect.x, rect.y, nodeWidth, nodeHeight, 16, 16);
        g2.setColor(Color.BLACK);
        g2.drawRoundRect(rect.x, rect.y, nodeWidth, nodeHeight, 16, 16);
        // 节点内容：编号紫色，内容黑色
        String text = node.toString();
        String nodeIdStr = "[" + node.nodeId + "]";
        FontMetrics fm = g2.getFontMetrics();
        int idWidth = fm.stringWidth(nodeIdStr);
        int contentWidth = fm.stringWidth(text.startsWith(nodeIdStr) ? text.substring(nodeIdStr.length()).trim() : text);
        int totalWidth = idWidth + 4 + contentWidth;
        int tx = rect.x + (nodeWidth - totalWidth) / 2;
        int ty = rect.y + (nodeHeight + fm.getAscent() - fm.getDescent()) / 2;
        // 编号
        g2.setColor(new Color(128,0,128));
        g2.drawString(nodeIdStr, tx, ty);
        // 内容
        g2.setColor(Color.BLACK);
        g2.drawString(text.startsWith(nodeIdStr) ? text.substring(nodeIdStr.length()).trim() : text, tx + idWidth + 4, ty);
        for (LogicNode child : node.children) {
            drawNodes(g, child);
        }
    }

    // 绘制箭头
    private void drawArrow(Graphics g, int x1, int y1, int x2, int y2) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(Color.GRAY);
        g2.setStroke(new BasicStroke(2));
        g2.drawLine(x1, y1, x2, y2);
        // 箭头
        double angle = Math.atan2(y2 - y1, x2 - x1);
        int len = 10;
        int aw = 6;
        int ax = x2 - (int) (len * Math.cos(angle));
        int ay = y2 - (int) (len * Math.sin(angle));
        int xA = ax + (int) (aw * Math.sin(angle));
        int yA = ay - (int) (aw * Math.cos(angle));
        int xB = ax - (int) (aw * Math.sin(angle));
        int yB = ay + (int) (aw * Math.cos(angle));
        int[] xs = {x2, xA, xB};
        int[] ys = {y2, yA, yB};
        g2.fillPolygon(xs, ys, 3);
    }
}
