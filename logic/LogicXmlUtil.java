package logic;

import org.w3c.dom.*;
import java.util.*;

public class LogicXmlUtil {
    public static LogicNode parseXml(Element e, int[] nodeIdCounter) {
        String tag = e.getTagName();
        LogicNode.NodeType type;
        switch(tag) {
            case "rules": type = LogicNode.NodeType.RULES; break;
            case "rule": type = LogicNode.NodeType.RULE; break;
            case "id": type = LogicNode.NodeType.ID; break;
            case "group-by": type = LogicNode.NodeType.GROUP_BY; break;
            case "forall": type = LogicNode.NodeType.FORALL; break;
            case "exists": type = LogicNode.NodeType.EXISTS; break;
            case "and": type = LogicNode.NodeType.AND; break;
            case "or": type = LogicNode.NodeType.OR; break;
            case "implies": type = LogicNode.NodeType.IMPLIES; break;
            case "not": type = LogicNode.NodeType.NOT; break;
            case "bfunc": type = LogicNode.NodeType.BFUNC; break;
            case "formula": type = LogicNode.NodeType.FORMULA; break;
            default: type = LogicNode.NodeType.UNKNOWN; break;
        }
        LogicNode node = new LogicNode(type, nodeIdCounter[0]++);
        // 如果节点类型未知，记录原始标签与内容供可视化展示
        if (type == LogicNode.NodeType.UNKNOWN) {
            node.unknownTag = tag;
            String txt = e.getTextContent();
            node.content = txt == null ? "" : txt.trim();
        }
        // 如果是 ID 或 GROUP_BY 类型，将元素的文本内容作为 content 字段保存
        if (type == LogicNode.NodeType.ID || type == LogicNode.NodeType.GROUP_BY) {
            String txt = e.getTextContent();
            node.content = txt == null ? "" : txt.trim();
            // 这些节点通常没有结构化子节点，我们可以直接返回（但仍允许属性）
        }
        NamedNodeMap attrs = e.getAttributes();
        for (int i=0;i<attrs.getLength();i++) {
            Attr a = (Attr)attrs.item(i);
            node.params.put(a.getName(), a.getValue());
        }
        NodeList children = e.getChildNodes();
        // collect pending comments to attach to next logic child
        java.util.List<String> pendingComments = new java.util.ArrayList<>();
        for (int i=0;i<children.getLength();i++) {
            org.w3c.dom.Node childNode = children.item(i);
            if (childNode.getNodeType() == org.w3c.dom.Node.COMMENT_NODE) {
                String txt = childNode.getNodeValue();
                if (txt != null) txt = txt.trim();
                if (txt != null && !txt.isEmpty()) pendingComments.add(txt);
                continue;
            }
            if (childNode instanceof Element) {
                Element ce = (Element)childNode;
                if (ce.getTagName().equals("param")) {
                    Map<String,String> param = new LinkedHashMap<>();
                    NamedNodeMap paramAttrs = ce.getAttributes();
                    for (int j=0;j<paramAttrs.getLength();j++) {
                        Attr pa = (Attr)paramAttrs.item(j);
                        param.put(pa.getName(), pa.getValue());
                    }
                    node.paramList.add(param);
                } else if (ce.getTagName().equals("filter") && (type==LogicNode.NodeType.FORALL || type==LogicNode.NodeType.EXISTS)) {
                    // 解析filter子标签
                    NamedNodeMap filterAttrs = ce.getAttributes();
                    for (int j=0;j<filterAttrs.getLength();j++) {
                        Attr fa = (Attr)filterAttrs.item(j);
                        node.filter.put(fa.getName(), fa.getValue());
                    }
                    NodeList filterChildren = ce.getChildNodes();
                    for (int k=0;k<filterChildren.getLength();k++) {
                        org.w3c.dom.Node fchild = filterChildren.item(k);
                        if (fchild instanceof Element && ((Element)fchild).getTagName().equals("param")) {
                            Map<String,String> fparam = new LinkedHashMap<>();
                            NamedNodeMap fparamAttrs = ((Element)fchild).getAttributes();
                            for (int m=0;m<fparamAttrs.getLength();m++) {
                                Attr fpa = (Attr)fparamAttrs.item(m);
                                fparam.put(fpa.getName(), fpa.getValue());
                            }
                            node.filterParamList.add(fparam);
                        }
                    }
                } else {
                    // child is a logic sub-node
                        LogicNode childLogic = parseXml(ce, nodeIdCounter);
                        // attach any pending comments (concatenate as separate entries)
                        if (!pendingComments.isEmpty()) {
                            childLogic.comments.addAll(pendingComments);
                            pendingComments.clear();
                        }
                        node.children.add(childLogic);
                }
            }
        }
        // if there are leftover comments that didn't precede any child logic node, attach them to current node
        if (!pendingComments.isEmpty()) {
            node.comments.addAll(pendingComments);
            pendingComments.clear();
        }
        return node;
    }
    // 树转XML
    public static Element toXml(LogicNode node, Document doc) {
        String tag;
        switch(node.type) {
            case RULES: tag = "rules"; break;
            case RULE: tag = "rule"; break;
            case ID: tag = "id"; break;
            case GROUP_BY: tag = "group-by"; break;
            case FORALL: tag = "forall"; break;
            case EXISTS: tag = "exists"; break;
            case AND: tag = "and"; break;
            case OR: tag = "or"; break;
            case IMPLIES: tag = "implies"; break;
            case NOT: tag = "not"; break;
            case BFUNC: tag = "bfunc"; break;
            case FORMULA: tag = "formula"; break;
            default:
                // for UNKNOWN nodes, if we recorded the original tag during parse, reuse it
                if (node.type == LogicNode.NodeType.UNKNOWN && node.unknownTag != null && !node.unknownTag.isEmpty()) {
                    tag = node.unknownTag;
                } else {
                    tag = "unknown";
                }
                break;
        }
        Element e = doc.createElement(tag);
        for (var entry : node.params.entrySet()) {
            e.setAttribute(entry.getKey(), entry.getValue());
        }
        // 生成param子标签
        for (Map<String,String> param : node.paramList) {
            Element pe = doc.createElement("param");
            for (var entry : param.entrySet()) {
                pe.setAttribute(entry.getKey(), entry.getValue());
            }
            e.appendChild(pe);
        }
        // forall/exists节点生成filter子标签
        if ((node.type==LogicNode.NodeType.FORALL || node.type==LogicNode.NodeType.EXISTS) && !node.filter.isEmpty()) {
            Element filterE = doc.createElement("filter");
            for (var entry : node.filter.entrySet()) {
                filterE.setAttribute(entry.getKey(), entry.getValue());
            }
            for (Map<String,String> param : node.filterParamList) {
                Element pe = doc.createElement("param");
                for (var entry : param.entrySet()) {
                    pe.setAttribute(entry.getKey(), entry.getValue());
                }
                filterE.appendChild(pe);
            }
            e.appendChild(filterE);
        }
        if (node.type == LogicNode.NodeType.ID || node.type == LogicNode.NodeType.GROUP_BY) {
            if (node.content != null) e.appendChild(doc.createTextNode(node.content));
            return e;
        }
        // If this is an UNKNOWN node and we have preserved textual content (and no structured children/params),
        // write it back as text content so the original payload is preserved.
        if (node.type == LogicNode.NodeType.UNKNOWN) {
            String uc = node.content;
            boolean hasStructuredChildren = !node.children.isEmpty() || !node.paramList.isEmpty() || !node.filter.isEmpty();
            if (uc != null && !uc.isEmpty() && !hasStructuredChildren) {
                e.appendChild(doc.createTextNode(uc));
            }
        }
        for (LogicNode child : node.children) {
            // if child has comments, add each comment as a separate COMMENT node before the element
            if (child.comments != null && !child.comments.isEmpty()) {
                for (String com : child.comments) {
                    if (com == null) continue;
                    String txt = com.trim();
                    if (txt.isEmpty()) continue;
                    e.appendChild(doc.createComment(txt));
                }
            }
            e.appendChild(toXml(child, doc));
        }
        return e;
    }
}
