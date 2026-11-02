package com.tidy.kit.ui;

import com.tidy.kit.model.JsonNodeModel;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 大纲风格 JSON 树:无连线、纯缩进、清晰折叠三角(设计 5.3)。
 * 自动格式化会反复重建模型,因此重建前先记住展开/收起状态,再按它恢复。
 * 右键可将选中节点及其子树作为完整 JSON 复制。
 */
class JsonTree extends JTree {

    /** 路径片段分隔符,用不可打印字符避免 JSON key 本身含常规符号时产生歧义 */
    private static final String STEP_SEP = "\u0001";

    /** 复制结果的反馈文案出口,由面板提供;树不持有状态栏 */
    private final Consumer<String> statusReporter;
    private final JPopupMenu copyMenu = new JPopupMenu();

    JsonTree(Consumer<String> statusReporter) {
        this.statusReporter = statusReporter;
        // 无连线纯缩进
        putClientProperty("JTree.lineStyle", "None");
        setShowsRootHandles(true);
        setRootVisible(true);
        setCellRenderer(new JsonTreeCellRenderer());
        // JTree() 无参构造会自带一份 Swing 示例树(Animal/Plantae 那些),新标签得从空模型开始
        setModel(new DefaultTreeModel(null));
        applyFontAndRowHeight();
        installCopyMenu();
    }

    private void installCopyMenu() {
        JMenuItem copyItem = new JMenuItem("复制");
        copyItem.addActionListener(e -> copySelectedSubtree());
        copyMenu.add(copyItem);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowCopyMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowCopyMenu(e);
            }
        });
    }

    /** press/release 两处都问一次:Windows/Linux 的 popup 标志多在 press,macOS 多在 release */
    private void maybeShowCopyMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        TreePath hit = getPathForLocation(e.getX(), e.getY());
        if (hit == null || !(hit.getLastPathComponent() instanceof JsonTreeNode)) {
            // 空白处或空模型(新标签、输入已清空):不弹菜单,免得让人以为复制到了什么
            return;
        }
        // 先把选中位移到右键那一行;不移的话复制到的是上一次点选的行
        setSelectionPath(hit);
        copyMenu.show(this, e.getX(), e.getY());
    }

    /**
     * 复制选中节点及其下所有子节点。
     * 走 JsonNodeModel 原始模型而不是 TreeNode 视图,因此处于收起/懒构建状态的大节点也能拿到完整子树。
     */
    private void copySelectedSubtree() {
        TreePath selected = getSelectionPath();
        if (selected == null || !(selected.getLastPathComponent() instanceof JsonTreeNode node)) {
            return;
        }
        String json = node.getModel().toPrettyValueString();
        UiFeedback.copyToClipboard(json);
        statusReporter.accept("已复制选中节点的内容,共 " + json.lines().count() + " 行");
    }

    /** 字体变化时(Dark/Light 切换、方案调整)重算行高 */
    void applyFontAndRowHeight() {
        Font font = UiColors.editorFont();
        setFont(font);
        FontMetrics fm = getFontMetrics(font);
        setRowHeight(fm.getHeight() + 2);
    }

    /**
     * 用解析结果重建树。先 capture 当前展开状态再换模型:
     * 边打字边自动重算时,用户手动收起的分支不该被重新弹开(与对比树同一套做法)。
     * 本次新出现、没被记录过的节点回到默认策略:只展开根及其直接子节点。
     */
    void showJson(JsonNodeModel root) {
        Map<String, Boolean> previous = captureExpansion();
        JsonTreeNode treeRoot = new JsonTreeNode(root);
        setModel(new DefaultTreeModel(treeRoot));
        applyExpansion(treeRoot, new TreePath(treeRoot), "", 0, previous);
        validate();
        repaint();
    }

    /**
     * 记录各容器节点的展开状态,键为从根到该节点的路径。
     * 已折叠节点的子孙不在可视区、读不到状态,因此不记录,重建后走默认策略。
     */
    private Map<String, Boolean> captureExpansion() {
        Map<String, Boolean> state = new HashMap<>();
        if (!(getModel().getRoot() instanceof JsonTreeNode root)) {
            return state;
        }
        recordNode(root, new TreePath(root), "", state);
        return state;
    }

    private void recordNode(JsonTreeNode node, TreePath path, String prefix, Map<String, Boolean> state) {
        if (node.getChildCount() == 0) {
            return;
        }
        boolean expanded = isExpanded(path);
        // 根也记录:否则用户"全部收起"后一次重算就会把根重新弹开
        state.put(prefix, expanded);
        if (!expanded) {
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            JsonTreeNode child = (JsonTreeNode) node.getChildAt(i);
            recordNode(child, path.pathByAddingChild(child), prefix + stepOf(child, i), state);
        }
    }

    private void applyExpansion(JsonTreeNode node, TreePath path, String prefix, int depth, Map<String, Boolean> previous) {
        if (node.getChildCount() == 0) {
            return;
        }
        boolean expand;
        Boolean remembered = previous.get(prefix);
        if (remembered != null) {
            expand = remembered;
        } else {
            // 没记录过(首次渲染或内容变了新出现的节点):保持旧默认行为,
            // 根展开 + 根的直接子节点(非懒节点)展开,更深层收起
            expand = prefix.isEmpty() || (depth == 1 && !node.isLazy());
        }
        if (!expand) {
            collapsePath(path);
            return;
        }
        expandPath(path);
        for (int i = 0; i < node.getChildCount(); i++) {
            JsonTreeNode child = (JsonTreeNode) node.getChildAt(i);
            applyExpansion(child, path.pathByAddingChild(child), prefix + stepOf(child, i), depth + 1, previous);
        }
    }

    /** 路径片段:对象成员用 key,数组元素用下标 */
    private static String stepOf(JsonTreeNode node, int index) {
        String key = node.getModel().getKey();
        return key != null ? STEP_SEP + "k" + key : STEP_SEP + "i" + index;
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

    /**
     * 收起全部:连根一起折起来,只剩根那一行。
     * 不逐行 collapseRow(i>0)——那样只会退到"根 + 直接子节点",
     * 而默认渲染本来就是这个样子,点下去看不出任何变化。
     */
    void collapseAllNodes() {
        Object root = getModel().getRoot();
        if (root != null) {
            collapsePath(new TreePath(root));
        }
        revalidate();
        repaint();
    }
}
