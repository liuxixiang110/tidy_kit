package com.you.jsonformatter.parse;

import com.you.jsonformatter.model.JsonNodeModel;
import com.you.jsonformatter.model.NodeType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 解析层单测:六类型递归、数字精度、行列号断言、宽松容错逐项正/负例、深嵌套。
 * 纯 JUnit,不依赖 IDE(设计 7)。
 */
class JsonParseServiceTest {

    private final JsonParseService service = new JsonParseService();

    // ---------- 严格模式:六类型递归 ----------

    @Test
    void parsesAllSixTypesNested() throws Exception {
        String json = "{\"obj\":{\"arr\":[1,true,null,\"s\",3.14]},\"empty\":{},\"list\":[]}";
        JsonNodeModel root = service.parse(json, ParseMode.STRICT);
        assertEquals(NodeType.OBJECT, root.getType());
        assertEquals(3, root.getChildren().size());

        JsonNodeModel obj = child(root, "obj");
        assertEquals(NodeType.OBJECT, obj.getType());
        JsonNodeModel arr = child(obj, "arr");
        assertEquals(NodeType.ARRAY, arr.getType());
        assertEquals(NodeType.NUMBER, arr.getChildren().get(0).getType());
        assertEquals("1", arr.getChildren().get(0).getTextValue());
        assertEquals(NodeType.BOOLEAN, arr.getChildren().get(1).getType());
        assertEquals(NodeType.NULL, arr.getChildren().get(2).getType());
        assertEquals(NodeType.STRING, arr.getChildren().get(3).getType());
        assertEquals("s", arr.getChildren().get(3).getTextValue());
        assertEquals(NodeType.NUMBER, arr.getChildren().get(4).getType());
        assertEquals("3.14", arr.getChildren().get(4).getTextValue());

        assertTrue(child(root, "empty").getChildren().isEmpty());
        assertTrue(child(root, "list").getChildren().isEmpty());
    }

    @Test
    void objectKeyIsNullForArrayElements() throws Exception {
        JsonNodeModel root = service.parse("[10,20]");
        assertNull(root.getChildren().get(0).getKey());
    }

    // ---------- 数字精度 ----------

    @Test
    void preservesNumberPrecisionAsString() throws Exception {
        JsonNodeModel root = service.parse(
                "{\"big\":123456789012345678901234567890,\"exp\":1.5e+300,\"neg\":-0.0001}");
        assertEquals("123456789012345678901234567890", child(root, "big").getTextValue());
        assertEquals("1.5e+300", child(root, "exp").getTextValue());
        assertEquals("-0.0001", child(root, "neg").getTextValue());
    }

    // ---------- 字符串转义 ----------

    @Test
    void decodesStringEscapes() throws Exception {
        JsonNodeModel root = service.parse("{\"v\":\"a\\nb\\tc\\\"d\\\\e\\/f\\u0041\"}");
        assertEquals("a\nb\tc\"d\\e/fA", child(root, "v").getTextValue());
    }

    // ---------- 严格模式错误定位 ----------

    @Test
    void strictRejectsTrailingComma() {
        JsonParseException e = assertThrows(JsonParseException.class,
                () -> service.parse("{\"a\":1,}", ParseMode.STRICT));
        assertTrue(e.getMessage().contains("期望"), e.getMessage());
    }

    @Test
    void strictRejectsSingleQuote() {
        assertThrows(JsonParseException.class,
                () -> service.parse("{'a':1}", ParseMode.STRICT));
    }

    @Test
    void strictRejectsComment() {
        assertThrows(JsonParseException.class,
                () -> service.parse("{ /* c */ \"a\":1}", ParseMode.STRICT));
    }

    @Test
    void errorCarriesLineAndColumn() {
        // 第 3 行的非法值 x
        JsonParseException e = assertThrows(JsonParseException.class,
                () -> service.parse("{\n  \"a\": 1,\n  \"b\": x\n}", ParseMode.STRICT));
        assertEquals(3, e.getLine());
        assertTrue(e.getSnippet().length() > 0);
        assertNotNull(e.getExpected());
    }

    @Test
    void errorOnUnbalancedBrace() {
        assertThrows(JsonParseException.class, () -> service.parse("{\"a\":1"));
    }

    @Test
    void errorOnTruncatedString() {
        assertThrows(JsonParseException.class, () -> service.parse("{\"a\":\"abc}"));
    }

    // ---------- 深嵌套:1000 层不栈溢出 ----------

    @Test
    void deepNestingWithinLimitParses() throws Exception {
        int depth = 500;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append('[');
        }
        sb.append('1');
        for (int i = 0; i < depth; i++) {
            sb.append(']');
        }
        JsonNodeModel root = service.parse(sb.toString(), ParseMode.STRICT);
        assertEquals(NodeType.ARRAY, root.getType());
    }

    @Test
    void overLimitNestingReportsTooDeep() {
        int depth = CoreJsonParser.MAX_DEPTH + 10;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            sb.append('[');
        }
        sb.append('1');
        for (int i = 0; i < depth; i++) {
            sb.append(']');
        }
        JsonParseException e = assertThrows(JsonParseException.class,
                () -> service.parse(sb.toString(), ParseMode.STRICT));
        assertTrue(e.getMessage().contains("嵌套过深"), e.getMessage());
    }

    // ---------- 宽松模式逐项正例 ----------

    @Test
    void lenientToleratesComments() throws Exception {
        String json = "{\n  // line comment\n  \"a\": 1, /* block */ \"b\": 2\n}";
        JsonNodeModel root = service.parse(json, ParseMode.LENIENT);
        assertEquals("1", child(root, "a").getTextValue());
        assertEquals("2", child(root, "b").getTextValue());
    }

    @Test
    void lenientToleratesSingleQuotesAndTrailingComma() throws Exception {
        JsonNodeModel root = service.parse("['a','b',]", ParseMode.LENIENT);
        assertEquals(2, root.getChildren().size());
        assertEquals("a", root.getChildren().get(0).getTextValue());
    }

    @Test
    void lenientToleratesSurroundingJunk() throws Exception {
        JsonNodeModel root = service.parse("garbage before {\"a\":1} trailing junk",
                ParseMode.LENIENT);
        assertEquals(NodeType.OBJECT, root.getType());
        assertEquals("1", child(root, "a").getTextValue());
    }

    @Test
    void lenientExpandsTopLevelEscapedJsonString() throws Exception {
        // 顶层是一个被转义的 JSON 字符串
        String input = "\"{\\\"a\\\":1}\"";
        JsonNodeModel root = service.parse(input, ParseMode.LENIENT);
        assertEquals(NodeType.STRING, root.getType());
        assertNotNull(root.getEscapedInner(), "内层转义 JSON 应被展开");
        assertTrue(root.getEscapedInner().contains("\"a\": 1"), root.getEscapedInner());
    }

    @Test
    void lenientExpandsNestedEscapedJsonField() throws Exception {
        JsonNodeModel root = service.parse("{\"payload\":\"{\\\"k\\\":\\\"v\\\"}\"}",
                ParseMode.LENIENT);
        JsonNodeModel payload = child(root, "payload");
        assertEquals(NodeType.STRING, payload.getType());
        assertNotNull(payload.getEscapedInner());
    }

    // ---------- 宽松模式逐项负例(不容忍) ----------

    @Test
    void lenientRejectsUnbalancedBrackets() {
        assertThrows(JsonParseException.class,
                () -> service.parse("{\"a\":[1,2}", ParseMode.LENIENT));
    }

    @Test
    void lenientRejectsNoJsonBody() {
        assertThrows(JsonParseException.class,
                () -> service.parse("no braces here", ParseMode.LENIENT));
    }

    @Test
    void lenientInnerParseFailureDoesNotBreakOuter() throws Exception {
        // 内层不是合法 JSON,外层应正常解析,payload 保留为普通字符串
        JsonNodeModel root = service.parse("{\"payload\":\"{not json\"}", ParseMode.LENIENT);
        JsonNodeModel payload = child(root, "payload");
        assertEquals(NodeType.STRING, payload.getType());
        assertNull(payload.getEscapedInner());
    }

    @Test
    void emptyInputRejected() {
        assertThrows(JsonParseException.class, () -> service.parse("   ", ParseMode.STRICT));
        assertThrows(JsonParseException.class, () -> service.parse("", ParseMode.LENIENT));
    }

    private static JsonNodeModel child(JsonNodeModel obj, String key) {
        return obj.getChildren().stream()
                .filter(c -> key.equals(c.getKey()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing key: " + key));
    }
}
