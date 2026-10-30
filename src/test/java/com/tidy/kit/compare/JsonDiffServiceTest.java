package com.tidy.kit.compare;

import com.tidy.kit.parse.JsonParseService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 对比层纯 JUnit 单测(脱离 IDE)。用 JsonParseService 造左右树再对比。
 */
class JsonDiffServiceTest {

    private final JsonParseService parser = new JsonParseService();
    private final JsonDiffService differ = new JsonDiffService();

    private DiffResult cmp(String left, String right) throws Exception {
        com.tidy.kit.model.JsonNodeModel l = parser.parse(left);
        com.tidy.kit.model.JsonNodeModel r = parser.parse(right);
        return differ.compare(l, r);
    }

    @Test
    void identicalIsEqual() throws Exception {
        DiffResult res = cmp("{\"a\":1,\"b\":[1,2]}", "{\"a\":1,\"b\":[1,2]}");
        assertEquals(DiffStatus.EQUAL, res.getRoot().getStatus());
        assertEquals(0, res.getSummary().getTotal());
    }

    @Test
    void keyOrderIgnored() throws Exception {
        DiffResult res = cmp("{\"a\":1,\"b\":2}", "{\"b\":2,\"a\":1}");
        assertEquals(DiffStatus.EQUAL, res.getRoot().getStatus());
        assertEquals(0, res.getSummary().getTotal());
    }

    @Test
    void leafChanged() throws Exception {
        DiffResult res = cmp("{\"a\":1}", "{\"a\":2}");
        DiffNode a = child(res.getRoot(), "a");
        assertEquals(DiffStatus.CHANGED, a.getStatus());
        assertEquals(1, res.getSummary().getChanged());
        assertEquals(1, res.getSummary().getTotal());
    }

    @Test
    void numberPrecisionTreatedDifferent() throws Exception {
        DiffResult res = cmp("{\"a\":1}", "{\"a\":1.0}");
        assertEquals(DiffStatus.CHANGED, child(res.getRoot(), "a").getStatus());
    }

    @Test
    void summaryCountsAllDiffTreeNodes() throws Exception {
        DiffResult res = cmp("{\"a\":1,\"b\":{\"c\":2}}", "{\"a\":9,\"b\":{\"c\":2}}");
        // 根 + a + b + b.c 共 4 个节点,供 UI 做超大输入阈值提示
        assertEquals(4, res.getSummary().getNodeCount());
    }

    @Test
    void objectAddedAndRemovedKey() throws Exception {
        DiffResult added = cmp("{\"a\":1}", "{\"a\":1,\"b\":2}");
        assertEquals(DiffStatus.ADDED, child(added.getRoot(), "b").getStatus());
        assertEquals(1, added.getSummary().getAdded());

        DiffResult removed = cmp("{\"a\":1,\"b\":2}", "{\"a\":1}");
        assertEquals(DiffStatus.REMOVED, child(removed.getRoot(), "b").getStatus());
        assertEquals(1, removed.getSummary().getRemoved());
    }

    @Test
    void arrayAlignedByIndex() throws Exception {
        DiffResult res = cmp("[1,2]", "[1,2,3]");
        // 根为数组,子按 index;新增的第 3 个元素
        DiffNode last = res.getRoot().getChildren().get(2);
        assertEquals(DiffStatus.ADDED, last.getStatus());
        assertEquals(Integer.valueOf(2), last.getIndex());
        assertEquals(1, res.getSummary().getAdded());
    }

    @Test
    void typeDifference() throws Exception {
        DiffResult res = cmp("{\"a\":1}", "{\"a\":\"x\"}");
        DiffNode a = child(res.getRoot(), "a");
        assertEquals(DiffStatus.TYPE_DIFF, a.getStatus());
        assertEquals(1, res.getSummary().getTypeDiff());
    }

    @Test
    void rootTypeDifference() throws Exception {
        DiffResult res = cmp("[1]", "{\"a\":1}");
        assertEquals(DiffStatus.TYPE_DIFF, res.getRoot().getStatus());
    }

    @Test
    void nestedHasDiffAndCountsOnce() throws Exception {
        DiffResult res = cmp("{\"x\":{\"y\":1}}", "{\"x\":{\"y\":2}}");
        DiffNode root = res.getRoot();
        assertEquals(DiffStatus.CHANGED, root.getStatus()); // 容器聚合态
        assertTrue(root.isHasNestedDiff());
        DiffNode x = child(root, "x");
        assertEquals(DiffStatus.CHANGED, x.getStatus());
        DiffNode y = child(x, "y");
        assertEquals(DiffStatus.CHANGED, y.getStatus());
        // 只有叶子 y 计入 1 处变更,容器不重复计数
        assertEquals(1, res.getSummary().getChanged());
        assertEquals(1, res.getSummary().getTotal());
    }

    @Test
    void equalSubtreeHasNoNestedDiff() throws Exception {
        DiffResult res = cmp("{\"same\":{\"k\":1},\"diff\":1}", "{\"same\":{\"k\":1},\"diff\":2}");
        DiffNode same = child(res.getRoot(), "same");
        assertEquals(DiffStatus.EQUAL, same.getStatus());
        assertFalse(same.isHasNestedDiff());
    }

    @Test
    void removedSubtreeCountedOnceButExpanded() throws Exception {
        DiffResult res = cmp("{\"a\":{\"b\":1,\"c\":2}}", "{}");
        DiffNode a = child(res.getRoot(), "a");
        assertEquals(DiffStatus.REMOVED, a.getStatus());
        // 展示子树仍展开(b、c 两个),但整体只计 1 处删除
        assertEquals(2, a.getChildren().size());
        assertEquals(1, res.getSummary().getRemoved());
    }

    private static DiffNode child(DiffNode node, String key) {
        return node.getChildren().stream()
                .filter(c -> key.equals(c.getKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing key: " + key));
    }
}
