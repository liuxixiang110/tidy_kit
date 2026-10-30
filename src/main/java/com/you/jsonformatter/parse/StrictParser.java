package com.you.jsonformatter.parse;

import com.you.jsonformatter.model.JsonNodeModel;

/**
 * 严格模式:仅标准 JSON,委托 CoreJsonParser(lenient=false)。
 */
class StrictParser {

    JsonNodeModel parse(String input) throws JsonParseException {
        if (input == null || input.trim().isEmpty()) {
            throw new JsonParseException("输入为空", "非空 JSON");
        }
        return new CoreJsonParser(input, false).parseValueRoot();
    }
}
