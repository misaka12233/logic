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

    // 递归校验所有节点，收集所有有错误的节点及类型（每个节点只标记自身错误）
    public static void validateAllNodes(LogicNode node, Map<Integer, String> errorNodeMap) {
        errorNodeMap.clear();
        validateAllNodesRec(node, null, errorNodeMap);
    }
    private static void validateAllNodesRec(LogicNode node, LogicNode.NodeType parentType, Map<Integer, String> errorNodeMap) {
        String err = LogicValidator.validateNodeSelf(node, parentType);
        if (err != null) {
            errorNodeMap.put(node.nodeId, err);
        }
        for (LogicNode child : node.children) {
            validateAllNodesRec(child, node.type, errorNodeMap);
        }
    }
}
