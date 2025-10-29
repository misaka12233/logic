package logic;

public class LogicValidator {
    // 错误节点缓存：nodeId -> 错误描述（用于收集每个节点的自校验错误）
    public static java.util.Map<Integer, String> errorNodeMap = new java.util.HashMap<>();

    // 递归校验所有节点，收集所有有错误的节点及类型（每个节点只标记自身错误）
    public static void validateAllNodes(logic.LogicNode node) {
        errorNodeMap.clear();
        validateAllNodesRec(node, null, new java.util.LinkedHashMap<>());
    }
    /**
     * 递归校验所有节点，增加变量作用域检查：
     * - FORALL/EXISTS 在 params.var 定义变量，该变量不得与祖先已定义的变量重复；
     * - 在 bfunc/ffunc（以及所有含 paramList 或 filterParamList 的节点）中使用的 var 必须在祖先中已定义。
     *
     * @param node 当前节点
     * @param parentType 父节点类型（用于结构校验）
     * @param definedVars 当前祖先链已定义的变量集合
     */
    public static void validateAllNodesRec(logic.LogicNode node, logic.LogicNode.NodeType parentType, java.util.Map<String, Boolean> definedVars) {
        // 先做原有的节点自身结构校验
        String err = validateNodeSelf(node, parentType);
        if (err != null) {
            errorNodeMap.put(node.nodeId, err);
        }

        // 变量作用域与使用检查
        // 1) 如果是量词节点，检查当前定义的变量是否已在祖先中存在（重复定义为错误）
        boolean pushedVar = false;
        String pushedName = null;
        Boolean pushedPrev = null;
        boolean hadPrev = false;
        if (node.type == logic.LogicNode.NodeType.FORALL || node.type == logic.LogicNode.NodeType.EXISTS) {
            String var = node.params.get("var");
            if (var != null && !var.isEmpty()) {
                hadPrev = definedVars.containsKey(var);
                pushedPrev = definedVars.get(var); // may be null
                if (hadPrev) {
                    // 追加或设置错误信息
                    String prev = errorNodeMap.get(node.nodeId);
                    String msg = "变量重复定义: " + var;
                    if (prev == null) errorNodeMap.put(node.nodeId, msg);
                    else errorNodeMap.put(node.nodeId, prev + "; " + msg);
                }
                // 以当前定义覆盖（标记为未使用），递归后恢复原值或移除
                definedVars.put(var, Boolean.FALSE);
                pushedVar = true;
                pushedName = var;
            } else {
                // 未能取到 var 参数，已在 validateNodeSelf 中报告结构错误
            }
        }

        // 2) 对当前节点中使用的变量（paramList / filterParamList）进行未定义检查
        // 检查 paramList
        if (node.paramList != null) {
            for (java.util.Map<String,String> p : node.paramList) {
                String used = p.get("var");
                if (used != null && !used.isEmpty()) {
                    if (!definedVars.containsKey(used)) {
                        String prev = errorNodeMap.get(node.nodeId);
                        String msg = "使用未定义的变量: " + used;
                        if (prev == null) errorNodeMap.put(node.nodeId, msg);
                        else errorNodeMap.put(node.nodeId, prev + "; " + msg);
                    } else {
                        // 标记该变量在作用域中已被使用
                        definedVars.put(used, Boolean.TRUE);
                    }
                }
            }
        }
        // 检查 filterParamList
        if (node.filterParamList != null) {
            for (java.util.Map<String,String> p : node.filterParamList) {
                String used = p.get("var");
                if (used != null && !used.isEmpty()) {
                    if (!definedVars.containsKey(used)) {
                        String prev = errorNodeMap.get(node.nodeId);
                        String msg = "使用未定义的变量: " + used;
                        if (prev == null) errorNodeMap.put(node.nodeId, msg);
                        else errorNodeMap.put(node.nodeId, prev + "; " + msg);
                    } else {
                        definedVars.put(used, Boolean.TRUE);
                    }
                }
            }
        }

        // 递归子节点，使用就地更新的 definedVars
        for (logic.LogicNode child : node.children) {
            validateAllNodesRec(child, node.type, definedVars);
        }

        // 回溯：如果在本节点插入了变量定义，则检查是否被使用并恢复/移除
        if (pushedVar && pushedName != null) {
            Boolean cur = definedVars.get(pushedName);
            if (cur == null || cur == Boolean.FALSE) {
                // 当前定义未被使用
                String prev = errorNodeMap.get(node.nodeId);
                String msg = "定义的变量未被使用: " + pushedName;
                if (prev == null) errorNodeMap.put(node.nodeId, msg);
                else errorNodeMap.put(node.nodeId, prev + "; " + msg);
            }
            if (hadPrev) {
                // 恢复之前的值
                definedVars.put(pushedName, pushedPrev);
            } else {
                definedVars.remove(pushedName);
            }
        }
    }

    // 只校验当前节点本身的错误（不递归子节点）
    static String validateNodeSelf(logic.LogicNode node, logic.LogicNode.NodeType parentType) {
        // 根节点要求为 RULES 类型（包含若干 rule）
        if (parentType == null && node.type != logic.LogicNode.NodeType.RULES) {
            return "根节点只能是RULES类型";
        }
        switch (node.type) {
            case RULES:
                // rules 必须为根节点
                if (parentType != null) return "rules 节点必须位于根位置";
                // 可包含多个 rule 子节点，至少允许 0 个以便编辑空规则集合
                break;
            case RULE:
                // rule 的父节点必须是 rules
                if (parentType != logic.LogicNode.NodeType.RULES) return "rule 的父节点必须是 rules";
                break;
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
            case IMPLIES:
                if (node.children.size()!=2) return node.type.name().toLowerCase()+"节点必须有2个子公式";
                break;
            case NOT:
                if (node.children.size()!=1) return "not 节点必须有1个子公式";
                break;
            case FORMULA:
                // formula 的父节点必须是 rule
                if (parentType != logic.LogicNode.NodeType.RULE) return "formula 的父节点必须是 rule";
                if (node.children.size() > 1) return "FORMULA类型的子节点不能超过1个";
                break;
            case AND: case OR:
                if (node.children.size() < 1) return node.type.name().toLowerCase()+"节点必须有子公式";
                break;
            default:
                // unknown 节点只能出现在 rule 或 rules 之下
                if (parentType != logic.LogicNode.NodeType.RULE && parentType != logic.LogicNode.NodeType.RULES)
                    return "unknown 节点的父节点必须是 rule 或 rules";
                break;
        }
        if (node.type != logic.LogicNode.NodeType.BFUNC && node.paramList != null && !node.paramList.isEmpty()) {
            return node.type.name().toLowerCase() + "类型不允许有参数列表";
        }
        return null;
    }
}
