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
