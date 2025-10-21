package logic;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;

import java.io.File;

public class UndoManager {
    private static boolean undoAvailable = false;
    private static final String TMP_PATH = System.getProperty("user.dir") + File.separator + "temporary.xml";
    private static final String TMP_META = System.getProperty("user.dir") + File.separator + "temporary_state.json";
    private static boolean saved = true; // whether current model is saved to disk
    private static java.util.List<java.lang.Runnable> listeners = new java.util.ArrayList<>();

    // 保存当前逻辑树为临时文件
    public static void saveSnapshot(LogicNode root) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();
            doc.appendChild(LogicXmlUtil.toXml(root, doc));
            TransformerFactory tf = TransformerFactory.newInstance();
            javax.xml.transform.Transformer t = tf.newTransformer();
            t.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            t.transform(new javax.xml.transform.dom.DOMSource(doc), new javax.xml.transform.stream.StreamResult(new File(TMP_PATH)));
            undoAvailable = true;
            // notify listeners
            for (java.lang.Runnable r : listeners) {
                try { r.run(); } catch(Exception ex) { }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * 带 UI 状态的快照：保存模型 XML（temporary.xml）并把树的展开/选中状态写入 temporary_state.json
     */
    public static void saveSnapshot(LogicNode root, javax.swing.JTree tree, javax.swing.tree.DefaultMutableTreeNode swingRoot) {
        // 标记当前模型为未保存（用户已对模型做出更改）
        saved = false;
        // 先保存模型
        saveSnapshot(root);
        try {
            java.util.List<Integer> expanded = logic.SwingTreeUtil.collectExpandedIds(tree, swingRoot);
            Integer sel = logic.SwingTreeUtil.findSelectedNodeId(tree);
            // 简单 JSON 写入
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"expanded\":[");
            for (int i=0;i<expanded.size();i++) { if (i>0) sb.append(","); sb.append(expanded.get(i)); }
            sb.append("],");
            sb.append("\"selected\":");
            sb.append(sel == null ? "null" : sel.toString());
            sb.append(",");
            sb.append("\"saved\":");
            sb.append(saved ? "true" : "false");
            sb.append("}");
            java.nio.file.Files.write(new java.io.File(TMP_META).toPath(), sb.toString().getBytes("UTF-8"));
            // notify listeners (already done in saveSnapshot(root))
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // 恢复临时文件到逻辑树，返回解析得到的根节点
    public static LogicNode restoreSnapshot() throws Exception {
        File f = new File(TMP_PATH);
        if (!f.exists()) return null;
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        org.w3c.dom.Document doc = db.parse(f);
        Element fe = (Element) doc.getDocumentElement();
        int[] counter = new int[]{1};
        return LogicXmlUtil.parseXml(fe, counter);
    }

    /**
     * 读取 previously saved UI state (temporary_state.json)。返回 pair: expandedIds list and selectedId (may be null).
     */
    public static java.util.Map<String, Object> restoreUiState() {
        java.util.Map<String,Object> out = new java.util.HashMap<>();
        java.io.File f = new java.io.File(TMP_META);
        if (!f.exists()) return out;
        try {
            String s = new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
            // 非严格 JSON 解析：解析 expanded[] 和 selected
            java.util.List<Integer> expanded = new java.util.ArrayList<>();
            Integer selected = null;
            // 找到 "expanded": [ ... ]
            int ei = s.indexOf("\"expanded\"");
            if (ei>=0) {
                int lb = s.indexOf('[', ei);
                int rb = s.indexOf(']', lb);
                if (lb>=0 && rb>lb) {
                    String inner = s.substring(lb+1, rb).trim();
                    if (!inner.isEmpty()) {
                        String[] parts = inner.split(",");
                        for (String p : parts) {
                            try { expanded.add(Integer.parseInt(p.trim())); } catch(Exception ex) {}
                        }
                    }
                }
            }
            int si = s.indexOf("\"selected\"");
            if (si>=0) {
                int colon = s.indexOf(':', si);
                if (colon>0) {
                    int comma = s.indexOf(',', colon);
                    int end = comma>0 ? comma : s.indexOf('}', colon);
                    if (end>colon) {
                        String val = s.substring(colon+1, end).trim();
                        if (!"null".equals(val)) {
                            try { selected = Integer.parseInt(val); } catch(Exception ex) {}
                        }
                    }
                }
            }
            out.put("expanded", expanded);
            out.put("selected", selected);
            // parse saved
            int svi = s.indexOf("\"saved\"");
            Boolean sv = null;
            if (svi>=0) {
                int colon = s.indexOf(':', svi);
                if (colon>0) {
                    int comma = s.indexOf(',', colon);
                    int end = comma>0 ? comma : s.indexOf('}', colon);
                    if (end>colon) {
                        String val = s.substring(colon+1, end).trim();
                        if ("true".equals(val)) sv = Boolean.TRUE;
                        else if ("false".equals(val)) sv = Boolean.FALSE;
                    }
                }
            }
            out.put("saved", sv);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        return out;
    }

    public static boolean isSaved() { return saved; }
    public static void setSaved(boolean v) { saved = v; }

    public static void clearTemporaryFiles() {
        try {
            java.io.File f1 = new java.io.File(TMP_PATH);
            java.io.File f2 = new java.io.File(TMP_META);
            if (f1.exists()) f1.delete();
            if (f2.exists()) f2.delete();
        } catch (Exception ex) { }
        undoAvailable = false;
        saved = true;
        for (java.lang.Runnable r : listeners) {
            try { r.run(); } catch(Exception ex) {}
        }
    }

    public static boolean isUndoAvailable() { return undoAvailable; }
    public static void setUndoAvailable(boolean v) { undoAvailable = v; }

    public static void setUndoAvailableAndNotify(boolean v) {
        undoAvailable = v;
        for (java.lang.Runnable r : listeners) {
            try { r.run(); } catch(Exception ex) { }
        }
    }

    public static void addListener(java.lang.Runnable r) {
        if (r == null) return;
        listeners.add(r);
    }

    public static void removeListener(java.lang.Runnable r) {
        listeners.remove(r);
    }
}
