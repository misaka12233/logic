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
            case AND: case OR: case IMPLIES:
                label = type.name().toLowerCase(); break;
            case NOT:
                label = "not"; break;
            case FORMULA:
                label = "formula"; break;
            default:
                label = "unknown"; break;
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

    public enum NodeType { FORALL, EXISTS, AND, OR, IMPLIES, NOT, BFUNC, FORMULA, UNKNOWN }
}
