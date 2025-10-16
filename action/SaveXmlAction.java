package action;

import logic.LogicNode;
import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
        String err = logic.LogicValidator.validate(logicRoot[0]);
        if (err != null) {
            JOptionPane.showMessageDialog(frame, "XML语法校验失败：" + err, "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }
        JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
        if (fc.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION) {
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document doc = db.newDocument();
                doc.appendChild(logic.LogicXmlUtil.toXml(logicRoot[0], doc));
                TransformerFactory tf = TransformerFactory.newInstance();
                javax.xml.transform.Transformer t = tf.newTransformer();
                t.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
                t.transform(new javax.xml.transform.dom.DOMSource(doc), new javax.xml.transform.stream.StreamResult(fc.getSelectedFile()));
                status.setText("XML保存成功");
            } catch (Exception ex) {
                status.setText("XML保存失败: "+ex.getMessage());
            }
        }
    }
}
