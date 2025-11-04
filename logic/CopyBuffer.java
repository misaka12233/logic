package logic;

public class CopyBuffer {
    // 存放模板节点（不含最终 nodeId），由复制操作写入，粘贴操作从中实例化新节点
    public static LogicNode template = null;
    public static boolean isSubtree = false;

    public static void clear() { template = null; isSubtree = false; }
}
