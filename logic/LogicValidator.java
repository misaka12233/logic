package logic;

public class LogicValidator {
    // 只校验当前节点本身的错误（不递归子节点）
    public static String validateNodeSelf(logic.LogicNode node, logic.LogicNode.NodeType parentType) {
        // 根节点只能是FORMULA类型
        if (parentType == null && node.type != logic.LogicNode.NodeType.FORMULA) {
            return "根节点只能是FORMULA类型";
        }
        switch (node.type) {
            case FORALL: case EXISTS:
                if (!node.params.containsKey("var") || !node.params.containsKey("in"))
                    return "量词节点缺少 var 或 in 参数";
                if (node.children.size() != 1) return "量词节点必须有且仅有一个子公式";
                // filter/filterParamList可为空或不为空，无需强制校验内容
                break;
            case BFUNC:
                if (!node.params.containsKey("name")) return "bfunc 缺少 name 参数";
                if (!node.children.isEmpty()) return "bfunc不能有子公式";
                break;
            case AND: case OR: case IMPLIES:
                if (node.children.size()!=2) return node.type.name().toLowerCase()+"节点必须有2个子公式";
                break;
            case NOT:
                if (node.children.size()!=1) return "not 节点必须有1个子公式";
                break;
            case FORMULA:
                if (parentType != null) return "FORMULA类型只能作为根节点出现";
                if (node.children.size() != 1) return "FORMULA类型必须有且仅有一个子节点";
                break;
            default:
                return "出现unknown节点";
        }
        if (node.type != logic.LogicNode.NodeType.BFUNC && node.paramList != null && !node.paramList.isEmpty()) {
            return node.type.name().toLowerCase() + "类型不允许有参数列表";
        }
        return null;
    }

    // 递归校验整个树，遇到第一个错误就返回（用于整体校验）
    public static String validate(logic.LogicNode node) {
        return validate(node, null);
    }
    public static String validate(logic.LogicNode node, logic.LogicNode.NodeType parentType) {
        String err = validateNodeSelf(node, parentType);
        if (err != null) return err;
        for (logic.LogicNode child : node.children) {
            String childErr = validate(child, node.type);
            if (childErr != null) return childErr;
        }
        return null;
    }
}
