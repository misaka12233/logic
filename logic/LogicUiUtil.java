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

    // 为所有节点分配 ruleId 与 localId：
    // - root (RULES) 的 ruleId/localId 都为 0
    // - 对于每个直接子节点为 RULE 的节点，按出现顺序从 1 开始计 ruleId
    // - 对应某个 rule 的所有子孙节点其 ruleId 为该 rule 的编号，localId 在同一 rule 中按遇到的先后顺序从 1 开始分配（RULE 本身 localId 为 0）
    public static void assignRuleAndLocalIds(LogicNode root) {
        if (root == null) return;
        root.ruleId = 0;
        root.localId = 0;
        int ruleCounter = 0;
        for (LogicNode child : root.children) {
            if (child.type == LogicNode.NodeType.RULE) {
                ruleCounter++;
                // assign for rule node and its subtree
                child.ruleId = ruleCounter;
                child.localId = 0;
                // assign local ids for descendants
                java.util.concurrent.atomic.AtomicInteger localCounter = new java.util.concurrent.atomic.AtomicInteger(1);
                assignLocalIdsRec(child, ruleCounter, localCounter);
            } else {
                // nodes directly under rules but not RULE (unusual) get ruleId 0
                child.ruleId = 0;
                child.localId = 0;
                assignLocalIdsRec(child, 0, new java.util.concurrent.atomic.AtomicInteger(1));
            }
        }
    }

    // 根据 ruleId 与 localId 查找节点（遍历整个树）
    public static LogicNode findNodeByRuleAndLocal(LogicNode node, int ruleId, int localId) {
        if (node == null) return null;
        if (node.ruleId == ruleId && node.localId == localId) return node;
        for (LogicNode c : node.children) {
            LogicNode r = findNodeByRuleAndLocal(c, ruleId, localId);
            if (r != null) return r;
        }
        return null;
    }

    private static void assignLocalIdsRec(LogicNode node, int ruleId, java.util.concurrent.atomic.AtomicInteger counter) {
        for (LogicNode c : node.children) {
            if (c.type == LogicNode.NodeType.RULE) {
                // nested RULE inside a rule: keep same ruleId for children, rule node itself has localId 0
                c.ruleId = ruleId;
                c.localId = 0;
                assignLocalIdsRec(c, ruleId, counter);
            } else if (c.type == LogicNode.NodeType.UNKNOWN) {
                // UNKNOWN 节点也显示 ruleId，但不参与 localId 编号（不消耗计数器）
                c.ruleId = ruleId;
                c.localId = 0;
                // 仍然为其子节点递归分配（子节点仍会按规则编号规则分配 localId）
                assignLocalIdsRec(c, ruleId, counter);
            } else {
                c.ruleId = ruleId;
                if (ruleId > 0) {
                    c.localId = counter.getAndIncrement();
                } else {
                    c.localId = 0;
                }
                assignLocalIdsRec(c, ruleId, counter);
            }
        }
    }
}
