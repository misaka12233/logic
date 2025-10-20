package logic;
import java.awt.*;
import java.util.*;

/**
 * LogicGraphSvgExporter
 * 导出LogicGraphPanel的树状图为SVG字符串
 */
public class LogicGraphSvgExporter {
    private Map<LogicNode, Rectangle> nodeBounds;
    private Map<LogicNode, Dimension> nodeSizes;
    private int vGap = 40;
    private int hGap = 24;
    private int svgWidth = 1200;
    private int svgHeight = 1200;

    public String exportSvg(LogicNode root) {
        nodeBounds = new HashMap<>();
        nodeSizes = new HashMap<>();
        // 计算尺寸
        calcNodeSizes(root);
        int[] y = {vGap};
        layoutTree(root, 0, y); // 先布局，左上角为(0, vGap)
        // 计算边界
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Rectangle rect : nodeBounds.values()) {
            minX = Math.min(minX, rect.x);
            minY = Math.min(minY, rect.y);
            maxX = Math.max(maxX, rect.x + rect.width);
            maxY = Math.max(maxY, rect.y + rect.height);
        }
        int pad = 40;
        svgWidth = maxX - minX + pad * 2;
        svgHeight = maxY - minY + pad * 2;
        // 平移所有节点到有边距的正区间
        Map<LogicNode, Rectangle> shiftedBounds = new HashMap<>();
        for (Map.Entry<LogicNode, Rectangle> e : nodeBounds.entrySet()) {
            Rectangle r = e.getValue();
            shiftedBounds.put(e.getKey(), new Rectangle(r.x - minX + pad, r.y - minY + pad, r.width, r.height));
        }
        nodeBounds = shiftedBounds;
        // 生成SVG
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(svgWidth).append("\" height=\"").append(svgHeight).append("\">\n");
        // 连线
        drawEdges(sb, root);
        // 节点
        drawNodes(sb, root);
        sb.append("</svg>\n");
        return sb.toString();
    }

    private void calcNodeSizes(LogicNode node) {
        // 用默认字体估算宽高，padding略大
        Font font = new Font("SansSerif", Font.PLAIN, 14);
        Canvas c = new Canvas();
        FontMetrics fm = c.getFontMetrics(font);
        String text = node.toString();
        int width = fm.stringWidth(text) + 36; // padding更大，防止溢出
        int height = fm.getAscent() + fm.getDescent() + 20; // 上下padding
        nodeSizes.put(node, new Dimension(width, height));
        for (LogicNode child : node.children) {
            calcNodeSizes(child);
        }
    }

    private int layoutTree(LogicNode node, int centerX, int[] y) {
        int thisY = y[0];
        int childCount = node.children.size();
        Dimension size = nodeSizes.getOrDefault(node, new Dimension(120,36));
        int nodeWidth = size.width;
        int nodeHeight = size.height;
        int childY = thisY + nodeHeight + vGap;
        int totalChildWidth = 0;
        int[] subtreeWidths = new int[childCount];
        for (int i = 0; i < childCount; i++) {
            int[] dummyY = {childY};
            subtreeWidths[i] = layoutTree(node.children.get(i), 0, dummyY);
            totalChildWidth += subtreeWidths[i];
        }
        if (childCount > 0) totalChildWidth += (childCount - 1) * hGap;
        int subtreeWidth = Math.max(nodeWidth, totalChildWidth);
        int startX = centerX - subtreeWidth / 2;
        int curX = startX;
        for (int i = 0; i < childCount; i++) {
            int childCenter = curX + subtreeWidths[i] / 2;
            int[] childYArr = {childY};
            layoutTree(node.children.get(i), childCenter, childYArr);
            curX += subtreeWidths[i] + hGap;
        }
        nodeBounds.put(node, new Rectangle(centerX - nodeWidth / 2, thisY, nodeWidth, nodeHeight));
        return subtreeWidth;
    }

    private void drawEdges(StringBuilder sb, LogicNode node) {
        Rectangle from = nodeBounds.get(node);
        Dimension fromSize = nodeSizes.getOrDefault(node, new Dimension(120,36));
        int fromW = fromSize.width, fromH = fromSize.height;
        for (LogicNode child : node.children) {
            Rectangle to = nodeBounds.get(child);
            Dimension toSize = nodeSizes.getOrDefault(child, new Dimension(120,36));
            int toW = toSize.width;
            if (from != null && to != null) {
                // 画线
                int x1 = from.x + fromW / 2, y1 = from.y + fromH;
                int x2 = to.x + toW / 2, y2 = to.y;
                sb.append("<line x1=\"").append(x1).append("\" y1=\"").append(y1).append("\" x2=\"").append(x2).append("\" y2=\"").append(y2).append("\" stroke=\"#888\" stroke-width=\"2\" marker-end=\"url(#arrow)\"/>");
            }
            drawEdges(sb, child);
        }
    }

    private void drawNodes(StringBuilder sb, LogicNode node) {
        Rectangle rect = nodeBounds.get(node);
        if (rect == null) return;
        Dimension size = nodeSizes.getOrDefault(node, new Dimension(120,36));
        int nodeWidth = size.width;
        int nodeHeight = size.height;
        int rx = 12, ry = 12;
        // 背景
        sb.append("<rect x=\"").append(rect.x).append("\" y=\"").append(rect.y).append("\" width=\"").append(nodeWidth).append("\" height=\"").append(nodeHeight).append("\" rx=\"").append(rx).append("\" ry=\"").append(ry).append("\" fill=\"white\" stroke=\"black\" stroke-width=\"1.5\"/>");
        // 文本：编号蓝色，关键字紫色，其余黑色，同行显示
        String text = node.toString();
        String nodeIdStr = "[" + node.nodeId + "]";
        int fontSize = 14;
        String content = text;
        if (content.startsWith(nodeIdStr)) content = content.substring(nodeIdStr.length()).trim();
        sb.append("<text x=\"").append(rect.x + nodeWidth/2).append("\" y=\"").append(rect.y + nodeHeight/2).append("\" text-anchor=\"middle\" dominant-baseline=\"middle\" font-size=\"").append(fontSize).append("\" font-family=\"SansSerif\">\n");
        // 编号（蓝色）居中段
        sb.append("  <tspan fill='#3C78FF'>").append(escapeXml(nodeIdStr)).append("</tspan>");
        sb.append("\n  ");
        // 内容逐词输出，关键字用紫色
        String[] parts = content.split(" ");
        for (int i=0;i<parts.length;i++) {
            String p = parts[i];
            String clean = p.replaceAll("[^A-Za-z]", "");
            String lower = clean.toLowerCase();
            boolean isKeyword = "forall".equals(lower) || "exists".equals(lower) || "and".equals(lower) || "or".equals(lower) || "with".equals(lower) || "in".equals(lower) || "formula".equals(lower) || "implies".equals(lower) || "not".equals(lower);
            if (isKeyword) sb.append("<tspan fill='purple'>").append(escapeXml(p)).append("</tspan>");
            else sb.append("<tspan fill='black'>").append(escapeXml(p)).append("</tspan>");
            if (i<parts.length-1) sb.append(" ");
        }
        sb.append("</text>");
        for (LogicNode child : node.children) {
                drawNodes(sb, child);
        }
    }

    private String escapeXml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
