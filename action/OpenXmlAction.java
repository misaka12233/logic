
package action;
import logic.LogicGraphPanel;
import logic.LogicNode;
import logic.LogicUiUtil;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class OpenXmlAction implements ActionListener {
    private final JFrame frame;
    private final DefaultMutableTreeNode root;
    private final LogicNode[] logicRoot;
    private final int[] nodeIdCounter;
    private final LogicGraphPanel graphPanel;
    private final JTree tree;
    private final JLabel status;

    public OpenXmlAction(JFrame frame, JTree tree, DefaultMutableTreeNode root, LogicNode[] logicRoot, int[] nodeIdCounter, LogicGraphPanel graphPanel, JLabel status) {
        this.frame = frame;
        this.tree = tree;
        this.root = root;
        this.logicRoot = logicRoot;
        this.nodeIdCounter = nodeIdCounter;
        this.graphPanel = graphPanel;
        this.status = status;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JFileChooser fc = new JFileChooser(System.getProperty("user.dir"));
        if (fc.showOpenDialog(frame)==JFileChooser.APPROVE_OPTION) {
            try {
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document doc = db.parse(fc.getSelectedFile());
                Element fe = (Element)doc.getDocumentElement();
                nodeIdCounter[0] = 1;
                LogicNode temp = logic.LogicXmlUtil.parseXml(fe, nodeIdCounter);
                // 不在打开时做阻断校验，仅加载文件，然后使用全量校验收集并展示错误信息
                logicRoot[0] = temp;
                root.setUserObject(logicRoot[0].toString());
                root.removeAllChildren();
                logic.SwingTreeUtil.buildSwingTree(logicRoot[0], root);
                ((DefaultTreeModel)tree.getModel()).reload();
                graphPanel.setLogicRoot(logicRoot[0]);
                // 使用 LogicValidator 的全量校验收集每个节点的错误
                logic.LogicValidator.validateAllNodes(logicRoot[0]);
                // 把校验结果显示到状态栏（以及 UI 的错误高亮）
                LogicUiUtil.updateErrorStatusBar(logicRoot[0], status, logic.LogicValidator.errorNodeMap);
                if (logic.LogicValidator.errorNodeMap.isEmpty()) status.setText("XML加载成功");
            } catch (Exception ex) {
                status.setText("XML解析失败: "+ex.getMessage());
            }
        }
    }
}
