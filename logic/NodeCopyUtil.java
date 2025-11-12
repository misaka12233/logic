package logic;

public class NodeCopyUtil {
    // 生成一个模板拷贝：当 includeChildren=false 时只复制单节点（不包含 children）
    public static LogicNode makeTemplateCopy(LogicNode src, boolean includeChildren) {
        if (src == null) return null;
        LogicNode n = new LogicNode(src.type, 0);
        // 拷贝 ID 特有字段
        n.content = src.content;
        n.params.putAll(src.params);
        for (java.util.Map<String,String> p : src.paramList) n.paramList.add(new java.util.LinkedHashMap<>(p));
        n.filter.putAll(src.filter);
        for (java.util.Map<String,String> p : src.filterParamList) n.filterParamList.add(new java.util.LinkedHashMap<>(p));
        if (src.comments != null) n.comments.addAll(src.comments);
        n.showComments = src.showComments;
        n.unknownTag = src.unknownTag;
        if (includeChildren) {
            for (LogicNode c : src.children) {
                n.children.add(makeTemplateCopy(c, true));
            }
        }
        return n;
    }

    // 从模板实例化、并分配新的 nodeId（使用外部传入的计数器）
    public static LogicNode instantiateFromTemplate(LogicNode template, int[] nodeIdCounter) {
        if (template == null) return null;
        LogicNode n = new LogicNode(template.type, nodeIdCounter[0]++);
        // 恢复 ID 特有字段
        n.content = template.content;
        n.params.putAll(template.params);
        for (java.util.Map<String,String> p : template.paramList) n.paramList.add(new java.util.LinkedHashMap<>(p));
        n.filter.putAll(template.filter);
        for (java.util.Map<String,String> p : template.filterParamList) n.filterParamList.add(new java.util.LinkedHashMap<>(p));
        if (template.comments != null) n.comments.addAll(template.comments);
        n.showComments = template.showComments;
        n.unknownTag = template.unknownTag;
        for (LogicNode c : template.children) n.children.add(instantiateFromTemplate(c, nodeIdCounter));
        return n;
    }
}
