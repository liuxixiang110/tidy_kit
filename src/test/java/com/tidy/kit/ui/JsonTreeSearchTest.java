package com.tidy.kit.ui;

import com.tidy.kit.model.JsonNodeModel;
import com.tidy.kit.model.NodeType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JsonTreeSearch 的匹配与路径语义(设计:JSON 格式化树查找与高亮 3.2)。
 * 直接手搭 JsonNodeModel,不经解析器,免得测试结论被解析行为牵住。
 */
class JsonTreeSearchTest {

    private static JsonNodeModel object(String key, JsonNodeModel... children) {
        return container(NodeType.OBJECT, key, children);
    }

    private static JsonNodeModel array(String key, JsonNodeModel... children) {
        return container(NodeType.ARRAY, key, children);
    }

    private static JsonNodeModel container(NodeType type, String key, JsonNodeModel... children) {
        JsonNodeModel node = new JsonNodeModel(type, key, null);
        for (JsonNodeModel child : children) {
            node.getChildren().add(child);
        }
        return node;
    }

    private static JsonNodeModel string(String key, String value) {
        return new JsonNodeModel(NodeType.STRING, key, value);
    }

    private static JsonNodeModel number(String key, String literal) {
        return new JsonNodeModel(NodeType.NUMBER, key, literal);
    }

    private static List<String> stepsOf(List<JsonTreeSearch.Hit> hits) {
        return hits.stream().map(hit -> String.join("/", hit.steps())).toList();
    }

    @Test
    void findsMatchedKey() {
        JsonNodeModel root = object(null, string("userName", "bob"));
        assertEquals(List.of("kuserName"), stepsOf(JsonTreeSearch.hits(root, "Name")));
    }

    @Test
    void findsMatchedValue() {
        JsonNodeModel root = object(null, string("user", "bob"));
        assertEquals(List.of("kuser"), stepsOf(JsonTreeSearch.hits(root, "bo")));
    }

    /** 不拆词:跨"词边界"的连续子串算命中,反过来打乱顺序不算 */
    @Test
    void matchesSubstringAcrossWordBoundaryButNotReordered() {
        JsonNodeModel root = object(null, string("note", "user name is bob"));
        assertEquals(1, JsonTreeSearch.hits(root, "name is").size());
        assertTrue(JsonTreeSearch.hits(root, "name user").isEmpty());
    }

    @Test
    void isCaseSensitive() {
        JsonNodeModel root = object(null, string("key", "Abc"));
        assertEquals(1, JsonTreeSearch.hits(root, "Abc").size());
        assertTrue(JsonTreeSearch.hits(root, "abc").isEmpty());
    }

    /** 数字节点按字面量文本比,不走数值 */
    @Test
    void matchesNumberLiteralText() {
        JsonNodeModel root = object(null, number("count", "12345"));
        assertEquals(List.of("kcount"), stepsOf(JsonTreeSearch.hits(root, "234")));
    }

    /** 折叠摘要 {n keys} / [n] 是渲染出来的统计,不该被当成数据命中 */
    @Test
    void doesNotMatchCollapsedSummary() {
        JsonNodeModel root = object("items", string("a", "1"), string("b", "2"));
        assertTrue(JsonTreeSearch.hits(root, "{2 keys}").isEmpty());
        assertTrue(JsonTreeSearch.hits(root, "[2]").isEmpty());
    }

    /** 数组元素没有 key,靠下标定位 */
    @Test
    void arrayElementsAreAddressedByIndex() {
        JsonNodeModel root = array(null, string(null, "aa"), string(null, "bb"));
        assertEquals(List.of("i1"), stepsOf(JsonTreeSearch.hits(root, "bb")));
    }

    @Test
    void buildsNestedStepPath() {
        JsonNodeModel root = object(null,
                array("users",
                        object(null, string("id", "1")),
                        object(null, string("id", "2")),
                        object(null, string("name", "bob"))));
        assertEquals(List.of("kusers/i2/kname"), stepsOf(JsonTreeSearch.hits(root, "name")));
    }

    /** 深度优先且父在子前:"第一个命中"就是文档序最靠前的那个 */
    @Test
    void visitsParentBeforeDescendants() {
        JsonNodeModel root = object("aa", object("aa2", string("aa3", "x")));
        List<JsonTreeSearch.Hit> hits = JsonTreeSearch.hits(root, "aa");
        // steps 是从根走到该节点的完整路径,每段都是那一层节点的 key/下标(根没有步骤,所以为空)
        // 与 JsonTree 记忆展开状态时用的 prefix 同一套语义
        assertEquals(List.of("", "kaa2", "kaa2/kaa3"), stepsOf(hits));
        // 根自身命中时路径为空,展开的就是根
        assertTrue(hits.get(0).steps().isEmpty());
    }

    /** 同一个节点 key 与 value 都命中只算一处(高亮是渲染器逐段判断的事) */
    @Test
    void keyAndValueMatchOnSameNodeCountsOnce() {
        JsonNodeModel root = object(null, string("ab", "ab"));
        assertEquals(1, JsonTreeSearch.hits(root, "ab").size());
    }

    @Test
    void emptyOrNullQueryFindsNothing() {
        JsonNodeModel root = object(null, string("key", "value"));
        assertTrue(JsonTreeSearch.hits(root, null).isEmpty());
        assertTrue(JsonTreeSearch.hits(root, "").isEmpty());
    }

    @Test
    void nullRootFindsNothing() {
        assertTrue(JsonTreeSearch.hits(null, "x").isEmpty());
    }

    @Test
    void noMatchReturnsEmpty() {
        JsonNodeModel root = object(null, string("key", "value"));
        assertTrue(JsonTreeSearch.hits(root, "zzz").isEmpty());
    }

    /** 步骤编码规则与 JsonTree 记忆展开状态用的那套是同一个方法 */
    @Test
    void stepOfEncodesKeyOrIndex() {
        assertEquals("kname", JsonTreeSearch.stepOf(string("name", "x"), 0));
        assertEquals("i3", JsonTreeSearch.stepOf(string(null, "x"), 3));
    }
}
