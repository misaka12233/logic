package logic;
import java.util.*;

public class LogicNode {
    public int nodeId = 0; // 可视化编号
    public NodeType type;
    public Map<String, String> params = new LinkedHashMap<>();
    public List<Map<String,String>> paramList = new ArrayList<>();
    public List<LogicNode> children = new ArrayList<>();
    public boolean expanded = false; // 展开状态
    public Map<String, String> filter = new LinkedHashMap<>();
    public List<Map<String,String>> filterParamList = new ArrayList<>();

    // 来自 XML 的注释（附加到紧随其后的逻辑节点）
    public List<String> comments = new ArrayList<>();
    // UI 标记：是否展开显示注释
    public boolean showComments = false;
    // 对于未知类型节点，记录原始标签与内容以便可视化与编辑
    public String unknownTag = null;
    public String unknownContent = null;

    public String getCommentsAsHtml() {
        if (comments==null || comments.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i=0;i<comments.size();i++) {
            if (i>0) sb.append("\n");
            sb.append(comments.get(i));
        }
        return sb.toString();
    }

    public LogicNode(NodeType t, int id) { type = t; nodeId = id; }

    public String toString() {
        String label;
        switch (type) {
            case FORALL: case EXISTS:
                label = type.name().toLowerCase() + " " + params.getOrDefault("var","") + " in " + params.getOrDefault("in","");
                if (!filter.isEmpty()) {
                    label += filterStr();
                }
                break;
            case BFUNC:
                label = params.getOrDefault("name", "bfunc") + paramListStr(); break;
            case AND: case OR: case IMPLIES: case NOT: case FORMULA: case RULE: case RULES:
                label = type.name().toLowerCase(); break;
            default:
                // 若解析时记录了原始标签与内容，则可视化显示这些信息
                if (unknownTag != null && !unknownTag.isEmpty()) {
                    String content = unknownContent == null ? "" : unknownContent;
                    // 截断过长内容以免树标签过长
                    String display = content.length() > 60 ? content.substring(0, 57) + "..." : content;
                    label = unknownTag + ": " + display;
                } else {
                    label = "unknown";
                }
                break;
        }
        return "[" + nodeId + "] " + label;
    }
    private String paramListStr() {
        if (paramList.isEmpty()) return "()";
        StringBuilder sb = new StringBuilder("(");
        for (Map<String,String> p : paramList) {
            String pos = p.getOrDefault("pos", "?");
            String var = p.getOrDefault("var", "?");
            sb.append(pos).append(":").append(var).append(", ");
        }
        if (sb.length()>2) sb.setLength(sb.length()-2);
        sb.append(")");
        return sb.toString();
    }
    // 新增filter可视化后缀
    private String filterStr() {
        StringBuilder sb = new StringBuilder(" with ");
        sb.append(filter.getOrDefault("name", "filter"));
        sb.append("(");
        if (!filterParamList.isEmpty()) {
            for (Map<String,String> p : filterParamList) {
                String pos = p.getOrDefault("pos", "?");
                String var = p.getOrDefault("var", "?");
                sb.append(pos).append(":").append(var).append(", ");
            }
            if (sb.length()>1 && sb.charAt(sb.length()-2)==',') sb.setLength(sb.length()-2);
        }
        sb.append(")");
        return sb.toString();
    }

    public enum NodeType { RULES, RULE, FORALL, EXISTS, AND, OR, IMPLIES, NOT, BFUNC, FORMULA, UNKNOWN }
}
