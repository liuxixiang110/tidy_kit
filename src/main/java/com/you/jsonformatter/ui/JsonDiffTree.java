package com.you.jsonformatter.ui;

import com.you.jsonformatter.compare.DiffNode;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.HashMap;
import java.util.Map;

/**
 * 单棵合并差异树:大纲风格(无连线纯缩进 + 折叠三角)。
 * 首次渲染只展开含差异的分支;之后重建(自动对比会反复触发)优先沿用用户已有的展开/收起。
 */
class JsonDiffTree extends JTree {

    /** 路径片段分隔符,用不可打印字符避免 JSON key 本身含常规符号时产生歧义 */
    private static final String STEP_SEP = "\u0001";

    JsonDiffTree() {
        putClientProperty("JTree.lineStyle", "None");
        setShowsRootHandles(true);
        setRootVisible(true);
        setCellRenderer(new JsonDiffCellRenderer());
        applyFontAndRowHeight();
    }

    void applyFontAndRowHeight() {
        Font font = JsonUiColors.editorFont();
        setFont(font);
        FontMetrics fm = getFontMetrics(font);
        setRowHeight(fm.getHeight() + 2);
    }

    /** 渲染差异树:先记住当前展开状态,重建后恢复,避免自动对比把手开的分支收回 */
    void showDiff(DiffNode root, boolean diffOnly) {
        Map<String, Boolean> previous = captureExpansion();
        DiffTreeNode treeRoot = new DiffTreeNode(root, diffOnly);
        setModel(new DefaultTreeModel(treeRoot));
        applyExpansion(treeRoot, new TreePath(treeRoot), "", previous);
        validate();
        repaint();
    }

    /**
     * 记录各容器节点的展开状态,键为从根到该节点的路径。
     * 已折叠节点的子孙不在可视区、读不到状态,因此不记录,重建后走默认策略。
     */
    private Map<String, Boolean> captureExpansion() {
        Map<String, Boolean> state = new HashMap<>();
        if (!(getModel().getRoot() instanceof DiffTreeNode root)) {
            return state;
        }
        recordNode(root, new TreePath(root), "", state);
        return state;
    }

    private void recordNode(DiffTreeNode node, TreePath path, String prefix, Map<String, Boolean> state) {
        if (node.getChildCount() == 0) {
            return;
        }
        boolean expanded = isExpanded(path);
        if (!prefix.isEmpty()) {
            state.put(prefix, expanded);
        }
        if (!expanded) {
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DiffTreeNode child = (DiffTreeNode) node.getChildAt(i);
            recordNode(child, path.pathByAddingChild(child), prefix + stepOf(child), state);
        }
    }

    /**
     * 按记录恢复展开状态。
     * 根节点强制展开;本次新出现的节点(没被记录过)回到默认策略:含差异则展开。
     */
    private void applyExpansion(DiffTreeNode node, TreePath path, String prefix, Map<String, Boolean> previous) {
        if (node.getChildCount() == 0) {
            return;
        }
        boolean expand;
        if (prefix.isEmpty()) {
            expand = true;
        } else {
            Boolean remembered = previous.get(prefix);
            expand = remembered != null ? remembered : node.getDiff().isHasNestedDiff();
        }
        if (expand) {
            expandPath(path);
        } else {
            collapsePath(path);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            DiffTreeNode child = (DiffTreeNode) node.getChildAt(i);
            applyExpansion(child, path.pathByAddingChild(child), prefix + stepOf(child), previous);
        }
    }

    /** 路径片段:对象成员用 key,数组元素用下标 */
    private static String stepOf(DiffTreeNode node) {
        DiffNode d = node.getDiff();
        return d.getKey() != null ? STEP_SEP + "k" + d.getKey() : STEP_SEP + "i" + d.getIndex();
    }

    void expandAllNodes() {
        Object root = getModel().getRoot();
        if (root != null) {
            expandRecursively(new TreePath(root));
        }
        revalidate();
        repaint();
    }

    private void expandRecursively(TreePath path) {
        DiffTreeNode node = (DiffTreeNode) path.getLastPathComponent();
        expandPath(path);
        for (int i = 0; i < node.getChildCount(); i++) {
            DiffTreeNode child = (DiffTreeNode) node.getChildAt(i);
            if (child.getChildCount() > 0) {
                expandRecursively(path.pathByAddingChild(child));
            }
        }
    }

    void collapseAllNodes() {
        for (int i = getRowCount() - 1; i > 0; i--) {
            collapseRow(i);
        }
        revalidate();
        repaint();
    }
}
