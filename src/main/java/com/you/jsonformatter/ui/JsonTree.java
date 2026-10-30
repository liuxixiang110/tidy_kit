package com.you.jsonformatter.ui;

import com.you.jsonformatter.model.JsonNodeModel;

import javax.swing.JTree;
import javax.swing.tree.TreePath;
import java.awt.Font;
import java.awt.FontMetrics;

/**
 * 大纲风格 JSON 树:无连线、纯缩进、清晰折叠三角(设计 5.3)。
 */
class JsonTree extends JTree {

    JsonTree() {
        // 无连线纯缩进
        putClientProperty("JTree.lineStyle", "None");
        setShowsRootHandles(true);
        setRootVisible(true);
        setCellRenderer(new JsonTreeCellRenderer());
        applyFontAndRowHeight();
    }

    /** 字体变化时(Dark/Light 切换、方案调整)重算行高 */
    void applyFontAndRowHeight() {
        Font font = JsonUiColors.editorFont();
        setFont(font);
        FontMetrics fm = getFontMetrics(font);
        setRowHeight(fm.getHeight() + 2);
    }

    /**
     * 用解析结果重建树。默认展开根及其直接子节点;
     * 大节点(children > 阈值)保持折叠以实现懒展开。
     */
    void showJson(JsonNodeModel root) {
        JsonTreeNode treeRoot = new JsonTreeNode(root);
        setModel(new javax.swing.tree.DefaultTreeModel(treeRoot));

        // 展开根 + 其直接子节点(小节点)
        expandPath(new TreePath(treeRoot));
        for (int i = 0; i < treeRoot.getChildCount(); i++) {
            JsonTreeNode child = (JsonTreeNode) treeRoot.getChildAt(i);
            if (!child.isLazy() && child.getChildCount() > 0) {
                expandPath(new TreePath(new Object[]{treeRoot, child}));
            }
        }
        validate();
        repaint();
    }

    /** 展开全部节点(递归物化懒子节点,超大树会较慢) */
    void expandAllNodes() {
        Object root = getModel().getRoot();
        if (root == null) {
            return;
        }
        expandRecursively(new TreePath(root));
        revalidate();
        repaint();
    }

    private void expandRecursively(TreePath path) {
        JsonTreeNode node = (JsonTreeNode) path.getLastPathComponent();
        expandPath(path);
        for (int i = 0; i < node.getChildCount(); i++) {
            JsonTreeNode child = (JsonTreeNode) node.getChildAt(i);
            if (child.getChildCount() > 0) {
                expandRecursively(path.pathByAddingChild(child));
            }
        }
    }

    /** 收起全部节点(保留根可见) */
    void collapseAllNodes() {
        for (int i = getRowCount() - 1; i > 0; i--) {
            collapseRow(i);
        }
        revalidate();
        repaint();
    }
}
