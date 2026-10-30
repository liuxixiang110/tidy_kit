package com.you.jsonformatter.compare;

import com.you.jsonformatter.model.NodeType;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构化差异树节点。叶子存左右文本值,容器存子差异。零 IDE 依赖。
 */
public class DiffNode {

    private final DiffStatus status;
    /** 对象成员名;数组元素与非成员为 null */
    private final String key;
    /** 数组下标;非数组元素为 null */
    private final Integer index;
    private final NodeType leftType;
    private final NodeType rightType;
    /** 叶子左值(数字/布尔/null/字符串的文本);容器为 null */
    private final String leftText;
    /** 叶子右值;容器为 null */
    private final String rightText;
    private final List<DiffNode> children = new ArrayList<>();
    /** 该子树内是否含差异(驱动默认展开) */
    private boolean hasNestedDiff;

    public DiffNode(DiffStatus status, String key, Integer index,
                    NodeType leftType, NodeType rightType,
                    String leftText, String rightText) {
        this.status = status;
        this.key = key;
        this.index = index;
        this.leftType = leftType;
        this.rightType = rightType;
        this.leftText = leftText;
        this.rightText = rightText;
        this.hasNestedDiff = status != DiffStatus.EQUAL;
    }

    public DiffStatus getStatus() {
        return status;
    }

    public String getKey() {
        return key;
    }

    public Integer getIndex() {
        return index;
    }

    public NodeType getLeftType() {
        return leftType;
    }

    public NodeType getRightType() {
        return rightType;
    }

    public String getLeftText() {
        return leftText;
    }

    public String getRightText() {
        return rightText;
    }

    public List<DiffNode> getChildren() {
        return children;
    }

    public boolean isContainer() {
        return !children.isEmpty();
    }

    public boolean isHasNestedDiff() {
        return hasNestedDiff;
    }

    void setHasNestedDiff(boolean hasNestedDiff) {
        this.hasNestedDiff = hasNestedDiff;
    }
}
