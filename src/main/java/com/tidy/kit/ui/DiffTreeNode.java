package com.tidy.kit.ui;

import com.tidy.kit.compare.DiffNode;
import com.tidy.kit.compare.DiffStatus;

import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * DiffNode 的 TreeNode 视图。差异树规模通常较小,直接物化子节点。
 */
class DiffTreeNode implements TreeNode {

    private final DiffNode diff;
    private DiffTreeNode parent;
    private final List<DiffTreeNode> children = new ArrayList<>();

    /**
     * @param diffOnly 为 true 时剔除完全相等的子树。增/删子树的后代本身也是 ADDED/REMOVED,
     *                 因此整块保留;容器只要含子孙差异就会被提升为 CHANGED,不会被误剔。
     */
    DiffTreeNode(DiffNode diff, boolean diffOnly) {
        this.diff = diff;
        for (DiffNode child : diff.getChildren()) {
            if (diffOnly && !containsDiff(child)) {
                continue;
            }
            DiffTreeNode node = new DiffTreeNode(child, diffOnly);
            node.parent = this;
            children.add(node);
        }
    }

    private static boolean containsDiff(DiffNode node) {
        return node.getStatus() != DiffStatus.EQUAL || node.isHasNestedDiff();
    }

    DiffNode getDiff() {
        return diff;
    }

    @Override
    public TreeNode getChildAt(int childIndex) {
        return children.get(childIndex);
    }

    @Override
    public int getChildCount() {
        return children.size();
    }

    @Override
    public TreeNode getParent() {
        return parent;
    }

    @Override
    public int getIndex(TreeNode node) {
        return children.indexOf(node);
    }

    @Override
    public boolean getAllowsChildren() {
        return !children.isEmpty();
    }

    @Override
    public boolean isLeaf() {
        return children.isEmpty();
    }

    @Override
    public Enumeration<? extends TreeNode> children() {
        return Collections.enumeration(children);
    }
}
