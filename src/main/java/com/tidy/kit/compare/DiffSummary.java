package com.tidy.kit.compare;

/**
 * 差异计数汇总,供 UI 状态摘要展示。compare 层纯 Java,不依赖 IDE。
 */
public class DiffSummary {

    private int added;
    private int removed;
    private int changed;
    private int typeDiff;
    /** 差异树节点总数,供 UI 做超大输入阈值提示 */
    private int nodeCount;
    /** 是否因超过深度上限被保守截断 */
    private boolean truncated;

    void countNode() {
        nodeCount++;
    }

    public int getNodeCount() {
        return nodeCount;
    }

    void count(DiffStatus status) {
        switch (status) {
            case ADDED:
                added++;
                break;
            case REMOVED:
                removed++;
                break;
            case CHANGED:
                changed++;
                break;
            case TYPE_DIFF:
                typeDiff++;
                break;
            case EQUAL:
            default:
                break;
        }
    }

    public int getAdded() {
        return added;
    }

    public int getRemoved() {
        return removed;
    }

    public int getChanged() {
        return changed;
    }

    public int getTypeDiff() {
        return typeDiff;
    }

    public int getTotal() {
        return added + removed + changed + typeDiff;
    }

    public boolean isTruncated() {
        return truncated;
    }

    void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    @Override
    public String toString() {
        if (getTotal() == 0) {
            return "两段 JSON 等价";
        }
        String s = "共 " + getTotal() + " 处差异(新增 " + added
                + " / 删除 " + removed + " / 变更 " + changed + " / 类型 " + typeDiff + ")";
        if (truncated) {
            s += ";超深度上限,部分按变更处理";
        }
        return s;
    }
}
