package com.tidy.kit.compare;

import com.tidy.kit.model.JsonNodeModel;
import com.tidy.kit.model.NodeType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 结构化对比服务:两棵 JsonNodeModel -> 合并差异树 DiffNode。
 * 纯 Java,不 import com.intellij,可脱离 IDE 单测。
 *
 * 语义:对象 key 忽略顺序(按 key 匹配),数组按下标对齐;
 * 数字按字符串精确比较(1 与 1.0 视为不同)。
 * 计数:叶子 CHANGED、结构 ADDED/REMOVED(整棵子树只计顶层一次)、TYPE_DIFF 各计一次;
 * 容器因子孙差异聚合出的 CHANGED 不重复计数。
 */
public class JsonDiffService {

    /** 递归深度上限,超限保守按 CHANGED 处理并置 truncated */
    static final int MAX_DEPTH = 1000;

    public DiffResult compare(JsonNodeModel left, JsonNodeModel right) {
        DiffSummary summary = new DiffSummary();
        DiffNode root = diff(null, null, left, right, 0, summary, true);
        return new DiffResult(root, summary);
    }

    /**
     * @param key       对象成员名(数组/根为 null)
     * @param index     数组下标(对象/根为 null)
     * @param countHere 顶层 ADDED/REMOVED 是否计入汇总(子树内为 false 避免重复计数)
     */
    private DiffNode diff(String key, Integer index, JsonNodeModel left, JsonNodeModel right,
                          int depth, DiffSummary summary, boolean countHere) {
        summary.countNode();
        if (depth > MAX_DEPTH) {
            summary.setTruncated(true);
            summary.count(DiffStatus.CHANGED);
            return new DiffNode(DiffStatus.CHANGED, key, index,
                    type(left), type(right), text(left), text(right));
        }

        // 一侧缺失:整棵子树记 ADDED / REMOVED
        if (left == null || right == null) {
            DiffStatus status = left == null ? DiffStatus.ADDED : DiffStatus.REMOVED;
            if (countHere) {
                summary.count(status);
            }
            JsonNodeModel present = left != null ? left : right;
            DiffNode node = new DiffNode(status, key, index, type(left), type(right),
                    text(left), text(right));
            // 展示用:继续展开存在侧的子结构,但不再重复计数
            if (present != null && isContainer(present)) {
                buildChildrenFromSingleSide(node, present, status, depth, summary);
            }
            node.setHasNestedDiff(true);
            return node;
        }

        // 两侧都在
        if (left.getType() != right.getType()) {
            summary.count(DiffStatus.TYPE_DIFF);
            DiffNode node = new DiffNode(DiffStatus.TYPE_DIFF, key, index,
                    left.getType(), right.getType(), text(left), text(right));
            node.setHasNestedDiff(true);
            return node;
        }

        if (isContainer(left)) {
            return diffContainer(key, index, left, right, depth, summary);
        }

        // 同类型叶子
        boolean equal = safeEquals(left.getTextValue(), right.getTextValue());
        DiffStatus status = equal ? DiffStatus.EQUAL : DiffStatus.CHANGED;
        if (!equal) {
            summary.count(DiffStatus.CHANGED);
        }
        return new DiffNode(status, key, index, left.getType(), right.getType(),
                left.getTextValue(), right.getTextValue());
    }

    private DiffNode diffContainer(String key, Integer index, JsonNodeModel left, JsonNodeModel right,
                                   int depth, DiffSummary summary) {
        DiffNode node = new DiffNode(DiffStatus.EQUAL, key, index,
                left.getType(), right.getType(), null, null);
        boolean anyDiff = false;

        if (left.getType() == NodeType.OBJECT) {
            Map<String, JsonNodeModel> leftMap = byKey(left);
            Map<String, JsonNodeModel> rightMap = byKey(right);
            for (Map.Entry<String, JsonNodeModel> e : leftMap.entrySet()) {
                JsonNodeModel rv = rightMap.get(e.getKey());
                DiffNode child = diff(e.getKey(), null, e.getValue(), rv, depth + 1, summary, true);
                node.getChildren().add(child);
                anyDiff |= child.getStatus() != DiffStatus.EQUAL || child.isHasNestedDiff();
            }
            for (Map.Entry<String, JsonNodeModel> e : rightMap.entrySet()) {
                if (!leftMap.containsKey(e.getKey())) {
                    DiffNode child = diff(e.getKey(), null, null, e.getValue(), depth + 1, summary, true);
                    node.getChildren().add(child);
                    anyDiff = true;
                }
            }
        } else { // ARRAY:按下标对齐
            int n = Math.max(left.getChildren().size(), right.getChildren().size());
            for (int i = 0; i < n; i++) {
                JsonNodeModel lv = i < left.getChildren().size() ? left.getChildren().get(i) : null;
                JsonNodeModel rv = i < right.getChildren().size() ? right.getChildren().get(i) : null;
                DiffNode child = diff(null, i, lv, rv, depth + 1, summary, true);
                node.getChildren().add(child);
                anyDiff |= child.getStatus() != DiffStatus.EQUAL || child.isHasNestedDiff();
            }
        }

        // 容器聚合态:有差异则 CHANGED,但不额外计数(子孙已计)
        if (anyDiff) {
            node = promoteToChanged(node, key, index);
        }
        return node;
    }

    /** 容器含差异时替换其状态为 CHANGED(保留已构建的 children),不重复计数 */
    private DiffNode promoteToChanged(DiffNode built, String key, Integer index) {
        DiffNode changed = new DiffNode(DiffStatus.CHANGED, key, index,
                built.getLeftType(), built.getRightType(), null, null);
        changed.getChildren().addAll(built.getChildren());
        changed.setHasNestedDiff(true);
        return changed;
    }

    /** 单侧子树展开(用于 ADDED/REMOVED 的展示),子节点不再计数 */
    private void buildChildrenFromSingleSide(DiffNode parent, JsonNodeModel present,
                                             DiffStatus status, int depth, DiffSummary summary) {
        if (present.getType() == NodeType.OBJECT) {
            for (JsonNodeModel child : present.getChildren()) {
                DiffNode node = singleSideChild(child.getKey(), null, child, status, depth, summary);
                parent.getChildren().add(node);
            }
        } else {
            for (int i = 0; i < present.getChildren().size(); i++) {
                DiffNode node = singleSideChild(null, i, present.getChildren().get(i), status, depth, summary);
                parent.getChildren().add(node);
            }
        }
    }

    private DiffNode singleSideChild(String key, Integer index, JsonNodeModel present,
                                     DiffStatus status, int depth, DiffSummary summary) {
        // countHere=false:整棵增/删子树只在顶层计一次
        JsonNodeModel left = status == DiffStatus.REMOVED ? present : null;
        JsonNodeModel right = status == DiffStatus.ADDED ? present : null;
        return diff(key, index, left, right, depth + 1, summary, false);
    }

    private Map<String, JsonNodeModel> byKey(JsonNodeModel obj) {
        Map<String, JsonNodeModel> map = new LinkedHashMap<>();
        for (JsonNodeModel child : obj.getChildren()) {
            map.put(child.getKey(), child);
        }
        return map;
    }

    private static boolean isContainer(JsonNodeModel m) {
        return m != null && (m.getType() == NodeType.OBJECT || m.getType() == NodeType.ARRAY);
    }

    private static NodeType type(JsonNodeModel m) {
        return m == null ? null : m.getType();
    }

    /** 叶子文本;容器返回 null */
    private static String text(JsonNodeModel m) {
        if (m == null) {
            return null;
        }
        return isContainer(m) ? null : m.getTextValue();
    }

    private static boolean safeEquals(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
