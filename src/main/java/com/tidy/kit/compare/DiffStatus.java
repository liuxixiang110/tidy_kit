package com.tidy.kit.compare;

/**
 * 结构化对比的单个节点差异状态。
 */
public enum DiffStatus {
    /** 两侧一致 */
    EQUAL,
    /** 仅右侧存在(新增) */
    ADDED,
    /** 仅左侧存在(删除) */
    REMOVED,
    /** 两侧都有但值不同 */
    CHANGED,
    /** 两侧类型不同 */
    TYPE_DIFF
}
