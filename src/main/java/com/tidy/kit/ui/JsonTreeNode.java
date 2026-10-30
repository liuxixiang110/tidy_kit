package com.tidy.kit.ui;

import com.tidy.kit.model.JsonNodeModel;

import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * JsonNodeModel 的 TreeNode 视图。子节点懒构建:
 * 大数据节点(children > {@value #LAZY_THRESHOLD})在展开前不会物化全部子节点。
 */
class JsonTreeNode implements TreeNode {

    /** 超过该子节点数的节点默认折叠,首次展开才物化子节点 */
    static final int LAZY_THRESHOLD = 5000;

    private final JsonNodeModel model;
    private JsonTreeNode parent;
    /** null 表示尚未物化;懒加载标志 */
    private List<JsonTreeNode> children;
    private final int childCount;

    JsonTreeNode(JsonNodeModel model) {
        this.model = model;
        this.childCount = model.getChildren().size();
        // 小节点直接物化,大节点保持 null 延迟到展开
        if (childCount <= LAZY_THRESHOLD) {
            materialize();
        }
    }

    JsonNodeModel getModel() {
        return model;
    }

    boolean isLazy() {
        return children == null;
    }

    private void materialize() {
        if (children != null) {
            return;
        }
        List<JsonTreeNode> built = new ArrayList<>(childCount);
        for (JsonNodeModel child : model.getChildren()) {
            JsonTreeNode node = new JsonTreeNode(child);
            node.parent = this;
            built.add(node);
        }
        children = built;
    }

    @Override
    public TreeNode getChildAt(int childIndex) {
        materialize();
        return children.get(childIndex);
    }

    @Override
    public int getChildCount() {
        return childCount;
    }

    @Override
    public TreeNode getParent() {
        return parent;
    }

    @Override
    public int getIndex(TreeNode node) {
        materialize();
        return children.indexOf(node);
    }

    @Override
    public boolean getAllowsChildren() {
        return model.isContainer();
    }

    @Override
    public boolean isLeaf() {
        return childCount == 0;
    }

    @Override
    public Enumeration<? extends TreeNode> children() {
        materialize();
        return Collections.enumeration(children);
    }

    @Override
    public String toString() {
        return model.toString();
    }

    /** 统计整棵模型树的节点总数(用于 10 万阈值提示) */
    static int countNodes(JsonNodeModel root) {
        int total = 1;
        for (JsonNodeModel child : root.getChildren()) {
            total += countNodes(child);
        }
        return total;
    }
}
