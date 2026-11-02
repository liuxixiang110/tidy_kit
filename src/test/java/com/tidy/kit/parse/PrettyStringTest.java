package com.tidy.kit.parse;

import com.tidy.kit.model.JsonNodeModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * toPrettyString 黄金文件式对比(设计 7)。
 * 后半部分是右键复制专用的 toPrettyValueString:它跟展示版只差在"粘出去还能再解析"。
 */
class PrettyStringTest {

    private final JsonParseService service = new JsonParseService();

    @Test
    void prettyPrintsObjectAndArray() throws Exception {
        JsonNodeModel root = service.parse("{\"name\":\"json\",\"tags\":[\"a\",\"b\"],\"n\":42,\"ok\":true,\"z\":null}");
        String expected = String.join("\n",
                "{",
                "  \"name\": \"json\",",
                "  \"tags\": [",
                "    \"a\",",
                "    \"b\"",
                "  ],",
                "  \"n\": 42,",
                "  \"ok\": true,",
                "  \"z\": null",
                "}");
        assertEquals(expected, root.toPrettyString());
    }

    @Test
    void prettyPrintsEmptyContainers() throws Exception {
        JsonNodeModel root = service.parse("{\"o\":{},\"a\":[]}");
        String expected = String.join("\n",
                "{",
                "  \"o\": {},",
                "  \"a\": []",
                "}");
        assertEquals(expected, root.toPrettyString());
    }

    @Test
    void prettyReescapesSpecialChars() throws Exception {
        JsonNodeModel root = service.parse("{\"s\":\"line\\nbreak\\\"quote\\\\back\"}");
        String expected = String.join("\n",
                "{",
                "  \"s\": \"line\\nbreak\\\"quote\\\\back\"",
                "}");
        assertEquals(expected, root.toPrettyString());
    }

    @Test
    void roundTripStableForPrettyOutput() throws Exception {
        JsonNodeModel root = service.parse("{\"a\":[1,2,{\"b\":\"c\"}],\"d\":true}");
        String pretty = root.toPrettyString();
        // 美化结果应能被严格模式重新解析(转义 JSON 展开块除外)
        JsonNodeModel reparsed = service.parse(pretty, ParseMode.STRICT);
        assertEquals(pretty, reparsed.toPrettyString());
    }

    @Test
    void valueCopyDropsOwnKeyButKeepsChildren() throws Exception {
        JsonNodeModel root = service.parse("{\"user\":{\"name\":\"json\",\"n\":1},\"flag\":true}");
        JsonNodeModel user = root.getChildren().get(0);
        String expected = String.join("\n",
                "{",
                "  \"name\": \"json\",",
                "  \"n\": 1",
                "}");
        // 不带自身的 key,否则粘出去是个片段而不是完整 JSON;子成员照常带 key
        assertEquals(expected, user.toPrettyValueString());
        // 叶子与布尔值:复制出值本身
        assertEquals("true", root.getChildren().get(1).toPrettyValueString());
    }

    @Test
    void valueCopyOfArrayElementIsTheElement() throws Exception {
        JsonNodeModel tags = service.parse("{\"tags\":[{\"a\":1},\"b\"]}").getChildren().get(0);
        String expected = String.join("\n",
                "{",
                "  \"a\": 1",
                "}");
        assertEquals(expected, tags.getChildren().get(0).toPrettyValueString());
        assertEquals("\"b\"", tags.getChildren().get(1).toPrettyValueString());
    }

    @Test
    void valueCopyOmitsEscapedJsonAnnotation() throws Exception {
        JsonNodeModel payload = service.parse("{\"payload\":\"{\\\"a\\\":1}\"}", ParseMode.LENIENT)
                .getChildren().get(0);
        // 前提:宽松模式确实把内层 JSON 展开了
        assertNotNull(payload.getEscapedInner());
        assertTrue(payload.toPrettyString().contains("escaped JSON"));
        // 复制路径不拼那行注释,内层串按字符串原值输出
        assertFalse(payload.toPrettyValueString().contains("escaped JSON"));
        assertEquals("\"{\\\"a\\\":1}\"", payload.toPrettyValueString());
    }

    @Test
    void valueCopyReparsesUnderStrictMode() throws Exception {
        JsonNodeModel root = service.parse(
                "{\"user\":{\"tags\":[\"x\",\"y\"],\"z\":null},\"payload\":\"{\\\"a\\\":1}\",\"o\":{},\"arr\":[]}",
                ParseMode.LENIENT);
        for (JsonNodeModel child : root.getChildren()) {
            String copied = child.toPrettyValueString();
            JsonNodeModel reparsed = service.parse(copied, ParseMode.STRICT);
            assertEquals(copied, reparsed.toPrettyString());
        }
    }
}
