package logic;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.File;
import java.util.*;

/**
 * 用于解析config.xml，提供patterns、bfuncs、ffuncs等结构化信息
 */
public class ConfigXmlLoader {
    public static class PatternInfo {
        public String name;
        public String description;
        public PatternInfo(String name, String description) {
            this.name = name;
            this.description = description;
        }
        public String toString() { return name + " - " + description; }
    }
    public static class FuncInfo {
        public String name;
        public String description;
        public List<ParamInfo> params = new ArrayList<>();
        public FuncInfo(String name, String description) {
            this.name = name;
            this.description = description;
        }
        public String toString() { return name + " - " + description + " (参数:" + params.size() + ")"; }
    }
    public static class ParamInfo {
        public String name;
        public String description;
        public ParamInfo(String name, String description) {
            this.name = name;
            this.description = description;
        }
        public String toString() { return name + " - " + description; }
    }

    public List<PatternInfo> patterns = new ArrayList<>();
    public List<FuncInfo> bfuncs = new ArrayList<>();
    public List<FuncInfo> ffuncs = new ArrayList<>();

    public static ConfigXmlLoader loadFromFile(String path) throws Exception {
        ConfigXmlLoader loader = new ConfigXmlLoader();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new File(path));
        Element root = doc.getDocumentElement();
        // 基本结构校验：根节点必须为 <config>
        if (root == null || !"config".equals(root.getNodeName())) {
            throw new Exception("config.xml 格式错误：根节点必须是 <config>");
        }
        // 辅助方法：查找直接子元素（不递归）
        java.util.function.BiFunction<Element, String, Element> getDirectChild = (parent, name) -> {
            NodeList nl = parent.getChildNodes();
            for (int i = 0; i < nl.getLength(); i++) {
                Node n = nl.item(i);
                if (n.getNodeType() == Node.ELEMENT_NODE && name.equals(n.getNodeName())) return (Element)n;
            }
            return null;
        };

        // 验证 patterns/bfuncs/ffuncs 三大块均存在且满足结构要求
        Element patternsRootElem = getDirectChild.apply(root, "patterns");
        Element bfuncsRootElem = getDirectChild.apply(root, "bfuncs");
        Element ffuncsRootElem = getDirectChild.apply(root, "ffuncs");
        if (patternsRootElem == null || bfuncsRootElem == null || ffuncsRootElem == null) {
            throw new Exception("config.xml 格式错误：必须包含 patterns, bfuncs, ffuncs 三个直接子元素");
        }

        // 验证 patterns 下的每个 pattern 包含 name 和 description 子元素
        NodeList patternNodesVal = patternsRootElem.getElementsByTagName("pattern");
        for (int i = 0; i < patternNodesVal.getLength(); i++) {
            Element e = (Element)patternNodesVal.item(i);
            Element nameElem = getDirectChild.apply(e, "name");
            Element descElem = getDirectChild.apply(e, "description");
            if (nameElem == null || descElem == null || nameElem.getTextContent().trim().isEmpty() || descElem.getTextContent().trim().isEmpty()) {
                throw new Exception("config.xml 格式错误：patterns 下的每个 pattern 必须包含非空的 name 和 description 子元素");
            }
        }

        // 验证 bfuncs 下的每个 bfunc 包含 name, description, params 且 params 包含 param 子元素，param 必须含 name 和 description 属性
        NodeList bfuncNodesVal = bfuncsRootElem.getElementsByTagName("bfunc");
        for (int i = 0; i < bfuncNodesVal.getLength(); i++) {
            Element e = (Element)bfuncNodesVal.item(i);
            Element nameElem = getDirectChild.apply(e, "name");
            Element descElem = getDirectChild.apply(e, "description");
            Element paramsElem = getDirectChild.apply(e, "params");
            if (nameElem == null || descElem == null || paramsElem == null
                || nameElem.getTextContent().trim().isEmpty() || descElem.getTextContent().trim().isEmpty()) {
                throw new Exception("config.xml 格式错误：每个 bfunc 必须包含非空的 name, description, params 子元素");
            }
            NodeList paramNodesVal = paramsElem.getElementsByTagName("param");
            for (int j = 0; j < paramNodesVal.getLength(); j++) {
                Element pe = (Element)paramNodesVal.item(j);
                String aname = pe.getAttribute("name");
                String adesc = pe.getAttribute("description");
                if (aname == null || aname.trim().isEmpty() || adesc == null || adesc.trim().isEmpty()) {
                    throw new Exception("config.xml 格式错误：bfunc 的 param 必须包含 name 和 description 属性");
                }
            }
        }

        // 验证 ffuncs 下的每个 ffunc 与 bfuncs 结构相同要求
        NodeList ffuncNodesVal = ffuncsRootElem.getElementsByTagName("ffunc");
        for (int i = 0; i < ffuncNodesVal.getLength(); i++) {
            Element e = (Element)ffuncNodesVal.item(i);
            Element nameElem = getDirectChild.apply(e, "name");
            Element descElem = getDirectChild.apply(e, "description");
            Element paramsElem = getDirectChild.apply(e, "params");
            if (nameElem == null || descElem == null || paramsElem == null
                || nameElem.getTextContent().trim().isEmpty() || descElem.getTextContent().trim().isEmpty()) {
                throw new Exception("config.xml 格式错误：每个 ffunc 必须包含非空的 name, description, params 子元素");
            }
            NodeList paramNodesVal2 = paramsElem.getElementsByTagName("param");
            for (int j = 0; j < paramNodesVal2.getLength(); j++) {
                Element pe = (Element)paramNodesVal2.item(j);
                String aname = pe.getAttribute("name");
                String adesc = pe.getAttribute("description");
                if (aname == null || aname.trim().isEmpty() || adesc == null || adesc.trim().isEmpty()) {
                    throw new Exception("config.xml 格式错误：ffunc 的 param 必须包含 name 和 description 属性");
                }
            }
        }
        // patterns
        NodeList patternsList = root.getElementsByTagName("patterns");
        if (patternsList.getLength() > 0) {
            Element patternsElem = (Element)patternsList.item(0);
            NodeList patternNodes = patternsElem.getElementsByTagName("pattern");
            for (int i = 0; i < patternNodes.getLength(); i++) {
                Element e = (Element)patternNodes.item(i);
                loader.patterns.add(new PatternInfo(
                    e.getElementsByTagName("name").item(0).getTextContent(),
                    e.getElementsByTagName("description").item(0).getTextContent()
                ));
            }
        }
        // bfuncs
        NodeList bfuncsList = root.getElementsByTagName("bfuncs");
        if (bfuncsList.getLength() > 0) {
            Element bfuncsElem = (Element)bfuncsList.item(0);
            NodeList bfuncNodes = bfuncsElem.getElementsByTagName("bfunc");
            for (int i = 0; i < bfuncNodes.getLength(); i++) {
                Element e = (Element)bfuncNodes.item(i);
                FuncInfo f = new FuncInfo(
                    e.getElementsByTagName("name").item(0).getTextContent(),
                    e.getElementsByTagName("description").item(0).getTextContent()
                );
                NodeList paramNodes = ((Element)e.getElementsByTagName("params").item(0)).getElementsByTagName("param");
                for (int j = 0; j < paramNodes.getLength(); j++) {
                    Element pe = (Element)paramNodes.item(j);
                    f.params.add(new ParamInfo(
                        pe.getAttribute("name"),
                        pe.getAttribute("description")
                    ));
                }
                loader.bfuncs.add(f);
            }
        }
        // ffuncs
        NodeList ffuncsList = root.getElementsByTagName("ffuncs");
        if (ffuncsList.getLength() > 0) {
            Element ffuncsElem = (Element)ffuncsList.item(0);
            NodeList ffuncNodes = ffuncsElem.getElementsByTagName("ffunc");
            for (int i = 0; i < ffuncNodes.getLength(); i++) {
                Element e = (Element)ffuncNodes.item(i);
                FuncInfo f = new FuncInfo(
                    e.getElementsByTagName("name").item(0).getTextContent(),
                    e.getElementsByTagName("description").item(0).getTextContent()
                );
                NodeList paramNodes = ((Element)e.getElementsByTagName("params").item(0)).getElementsByTagName("param");
                for (int j = 0; j < paramNodes.getLength(); j++) {
                    Element pe = (Element)paramNodes.item(j);
                    f.params.add(new ParamInfo(
                        pe.getAttribute("name"),
                        pe.getAttribute("description")
                    ));
                }
                loader.ffuncs.add(f);
            }
        }
        return loader;
    }
}
