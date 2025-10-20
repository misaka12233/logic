package logic;

import javax.swing.*;
import java.util.Map;

public class LogicUiUtil {
    // 递归查找nodeId对应的LogicNode
    public static LogicNode findNodeById(LogicNode node, int id) {
        if (node.nodeId == id) return node;
        for (LogicNode child : node.children) {
            LogicNode res = findNodeById(child, id);
            if (res != null) return res;
        }
        return null;
    }

    // 更新底部状态栏错误摘要
    public static void updateErrorStatusBar(LogicNode logicRoot, JLabel status, Map<Integer, String> errorNodeMap) {
        if (logicRoot == null || errorNodeMap.isEmpty()) {
            status.setText("Ready");
            return;
        }
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        int maxShow = 3;
        for (Map.Entry<Integer, String> entry : errorNodeMap.entrySet()) {
            if (shown >= maxShow) break;
            LogicNode n = findNodeById(logicRoot, entry.getKey());
            if (n != null) {
                sb.append(n.toString()).append(" ").append(entry.getValue()).append("\n");
                shown++;
            }
        }
        int total = errorNodeMap.size();
        if (total > maxShow) {
            sb.append("... 等 ").append(total - maxShow).append(" 条更多错误");
        }
        if (sb.length() > 0 && sb.charAt(sb.length()-1) == '\n') sb.setLength(sb.length()-1);
        String html = "<html>" + sb.toString().replace("\n", "<br>") + "</html>";
        status.setText(html);
    }
}
