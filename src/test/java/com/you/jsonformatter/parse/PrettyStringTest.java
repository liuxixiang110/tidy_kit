package com.you.jsonformatter.parse;

import com.you.jsonformatter.model.JsonNodeModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * toPrettyString 黄金文件式对比(设计 7)。
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
}
