package com.you.jsonformatter.compare;

/**
 * 对比结果:差异树根节点 + 汇总计数。
 */
public class DiffResult {

    private final DiffNode root;
    private final DiffSummary summary;

    public DiffResult(DiffNode root, DiffSummary summary) {
        this.root = root;
        this.summary = summary;
    }

    public DiffNode getRoot() {
        return root;
    }

    public DiffSummary getSummary() {
        return summary;
    }
}
