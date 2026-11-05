package com.tidy.kit.ui;

import com.tidy.kit.model.JsonNodeModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 在 JsonNodeModel 模型树上查找 key / value 里的子串(设计:JSON 格式化树查找与高亮)。
 * 走模型而不是 TreeNode 视图:收起的分支、超过 LAZY_THRESHOLD 尚未物化的大节点也能搜到。
 * 大小写敏感,不拆词 —— 整条 key/value 里出现连续子串即算命中。
 * 纯逻辑,不 import Swing,便于单测。
 */
final class JsonTreeSearch {

    /** 一处命中:从根到该节点的路径片段,以及命中的模型节点 */
    record Hit(List<String> steps, JsonNodeModel node) {
    }

    private JsonTreeSearch() {
    }

    /**
     * 路径片段编码:对象成员用 key,数组元素用下标。
     * 与 JsonTree 记忆展开状态用的是同一套规则,所以两边共用这一个方法。
     */
    static String stepOf(JsonNodeModel model, int index) {
        String key = model.getKey();
        return key != null ? "k" + key : "i" + index;
    }

    /** 深度优先,父节点排在子孙之前 —— "第一个命中"就是文档序最靠前的那个 */
    static List<Hit> hits(JsonNodeModel root, String query) {
        List<Hit> found = new ArrayList<>();
        if (root == null || query == null || query.isEmpty()) {
            return found;
        }
        collect(root, query, new ArrayList<>(), found);
        return found;
    }

    private static void collect(JsonNodeModel node, String query, List<String> path, List<Hit> out) {
        // key 与 value 都命中也只算一处(高亮是渲染器逐段判断的事,与这里的计数无关)
        if (matches(node.getKey(), query) || matches(node.getTextValue(), query)) {
            out.add(new Hit(List.copyOf(path), node));
        }
        List<JsonNodeModel> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            JsonNodeModel child = children.get(i);
            path.add(stepOf(child, i));
            collect(child, query, path, out);
            path.remove(path.size() - 1);
        }
    }

    private static boolean matches(String text, String query) {
        return text != null && text.contains(query);
    }
}
