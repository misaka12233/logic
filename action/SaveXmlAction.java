package action;

import logic.LogicNode;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import javax.xml.transform.TransformerFactory;

public class SaveXmlAction implements ActionListener {
    private final JFrame frame;
    private final LogicNode[] logicRoot;
    private final JLabel status;

    public SaveXmlAction(JFrame frame, LogicNode[] logicRoot, JLabel status) {
        this.frame = frame;
        this.logicRoot = logicRoot;
        this.status = status;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (logicRoot[0]==null) return;
        // 使用递归校验收集节点错误（不阻止保存）
        logic.LogicValidator.validateAllNodes(logicRoot[0]);
        boolean hasErrors = !logic.LogicValidator.errorNodeMap.isEmpty();
        if (hasErrors) {
            // 构造简短错误摘要
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Integer,String> en : logic.LogicValidator.errorNodeMap.entrySet()) {
                sb.append("[").append(en.getKey()).append("] ").append(en.getValue()).append("; ");
            }
            String msg = "校验发现问题，但仍将保存文件。错误摘要：" + sb.toString();
            JOptionPane.showMessageDialog(frame, msg, "警告：校验未通过", JOptionPane.WARNING_MESSAGE);
        }
        JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
        if (fc.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION) {
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document doc = db.newDocument();
                // 若校验未通过，在文档前添加注释说明（便于追踪）
                if (hasErrors) {
                    String shortNote = "VALIDATION FAILED";
                    doc.appendChild(doc.createComment(shortNote));
                }
                // 先写入根节点自身的注释（如果有），每条注释作为独立的 COMMENT 节点
                if (logicRoot[0].comments != null && !logicRoot[0].comments.isEmpty()) {
                    for (String com : logicRoot[0].comments) {
                        if (com == null) continue;
                        String txt = com.trim();
                        if (txt.isEmpty()) continue;
                        doc.appendChild(doc.createComment(txt));
                    }
                }
                doc.appendChild(logic.LogicXmlUtil.toXml(logicRoot[0], doc));
                TransformerFactory tf = TransformerFactory.newInstance();
                javax.xml.transform.Transformer t = tf.newTransformer();
                t.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
                t.transform(new javax.xml.transform.dom.DOMSource(doc), new javax.xml.transform.stream.StreamResult(fc.getSelectedFile()));
                status.setText("XML保存成功");
                // 标记为已保存
                logic.UndoManager.setSaved(true);
            } catch (Exception ex) {
                status.setText("XML保存失败: "+ex.getMessage());
            }
        }
    }
}
