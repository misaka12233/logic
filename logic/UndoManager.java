package logic;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;

import java.io.File;

/**
 * UndoManager: 支持磁盘持久化的多步撤销（环形缓冲）。
 * 默认保留最近 20 步快照（可根据需要调整 MAX_UNDO）。
 */
public class UndoManager {
    // 最大撤销步数（合理折中，既能回退多步，又不会占用过多磁盘）
    private static final int MAX_UNDO = 20;

    private static boolean undoAvailable = false;
    private static final String UNDO_DIR = System.getProperty("user.dir") + File.separator + ".undo";
    // 保留旧的单文件路径作为兼容回退
    private static boolean saved = true; // whether current model is saved to disk
    private static java.util.List<java.lang.Runnable> listeners = new java.util.ArrayList<>();

    // 环形缓冲元数据
    private static int nextIndex = 0; // 下一个写入槽位
    private static int size = 0; // 当前可撤销快照数
    private static int lastRestoredIndex = -1; // 上次 restore 使用的槽位

    // 保存当前逻辑树为临时文件（写入环形缓冲）
    public static void saveSnapshot(LogicNode root) {
        try {
            writeSnapshotAndGetIndex(root);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * 带 UI 状态的快照：保存模型 XML 到环形槽，并把树的展开/选中状态写入对应的 meta 文件
     */
    public static void saveSnapshot(LogicNode root, javax.swing.JTree tree, javax.swing.tree.DefaultMutableTreeNode swingRoot) {
        int idx = -1;
        try { idx = writeSnapshotAndGetIndex(root); } catch(Exception ex) { ex.printStackTrace(); }
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
            if (idx >= 0) java.nio.file.Files.write(new java.io.File(slotMetaPath(idx)).toPath(), sb.toString().getBytes("UTF-8"));
            // 标记当前模型为未保存（用户已对模型做出更改）
            saved = false;
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // helper: ensure undo directory exists
    private static void ensureUndoDir() {
        try {
            java.io.File d = new java.io.File(UNDO_DIR);
            if (!d.exists()) d.mkdirs();
        } catch (Exception ex) { }
    }

    private static String slotXmlPath(int idx) {
        return UNDO_DIR + File.separator + "temporary_" + idx + ".xml";
    }

    private static String slotMetaPath(int idx) {
        return UNDO_DIR + File.separator + "temporary_state_" + idx + ".json";
    }

    // 写入 XML 到当前槽位并返回槽位索引
    private static int writeSnapshotAndGetIndex(LogicNode root) throws Exception {
        ensureUndoDir();
        int idx = nextIndex;
        // 构造 Document
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.newDocument();
        if (root.comments != null && !root.comments.isEmpty()) {
            StringBuilder cs = new StringBuilder();
            for (int i=0;i<root.comments.size();i++) {
                if (i>0) cs.append("\n");
                cs.append(root.comments.get(i));
            }
            doc.appendChild(doc.createComment(cs.toString()));
        }
        doc.appendChild(LogicXmlUtil.toXml(root, doc));
        TransformerFactory tf = TransformerFactory.newInstance();
        javax.xml.transform.Transformer t = tf.newTransformer();
        t.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
        t.transform(new javax.xml.transform.dom.DOMSource(doc), new javax.xml.transform.stream.StreamResult(new java.io.File(slotXmlPath(idx))));

        // 更新环形缓冲索引/计数
        nextIndex = (nextIndex + 1) % MAX_UNDO;
        if (size < MAX_UNDO) size++;
        undoAvailable = size > 0;
        // notify listeners
        for (java.lang.Runnable r : listeners) {
            try { r.run(); } catch(Exception ex) { }
        }
        return idx;
    }

    // 恢复临时文件到逻辑树，返回解析得到的根节点
    public static LogicNode restoreSnapshot() throws Exception {
        if (size <= 0) return null;
        // 最新的快照位于 nextIndex - 1
        int idx = (nextIndex - 1 + MAX_UNDO) % MAX_UNDO;
        java.io.File f = new java.io.File(slotXmlPath(idx));
        if (!f.exists()) return null;
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        org.w3c.dom.Document doc = db.parse(f);
        Element fe = (Element) doc.getDocumentElement();
        int[] counter = new int[]{1};
        // 更新索引/计数：将 nextIndex 回退到该槽位（覆盖行为留给下一次 save）
        nextIndex = idx;
        size = Math.max(0, size - 1);
        undoAvailable = size > 0;
        lastRestoredIndex = idx;
        // notify listeners
        for (java.lang.Runnable r : listeners) {
            try { r.run(); } catch(Exception ex) { }
        }
        return LogicXmlUtil.parseXml(fe, counter);
    }

    /**
     * 读取 previously saved UI state。优先读取最后一次 restore 使用的 meta 槽文件，否则回退到旧的 TMP_META。
     */
    public static java.util.Map<String, Object> restoreUiState() {
        java.util.Map<String,Object> out = new java.util.HashMap<>();
        // 优先读取最后一次 restore 使用的 meta 文件
        java.io.File f = null;
        if (lastRestoredIndex >= 0) f = new java.io.File(slotMetaPath(lastRestoredIndex));
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
            // 删除 .undo 目录内所有文件
            java.io.File d = new java.io.File(UNDO_DIR);
            if (d.exists() && d.isDirectory()) {
                for (java.io.File f : d.listFiles()) {
                    try { f.delete(); } catch(Exception ex) {}
                }
            }
        } catch (Exception ex) { }
        undoAvailable = false;
        saved = true;
        nextIndex = 0;
        size = 0;
        lastRestoredIndex = -1;
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
