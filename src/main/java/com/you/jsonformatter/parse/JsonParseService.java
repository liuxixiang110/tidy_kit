package com.you.jsonformatter.parse;

import com.you.jsonformatter.model.JsonNodeModel;

/**
 * 解析层唯一对外入口。仅依赖 model,不 import com.intellij。
 * UI 层通过 parse(String, ParseMode) 获取 JsonNodeModel,失败拿到带定位的 JsonParseException。
 */
public final class JsonParseService {

    private final StrictParser strictParser = new StrictParser();
    private final LenientParser lenientParser = new LenientParser();

    public JsonParseService() {
    }

    /**
     * @param input 原始文本
     * @param mode  解析模式
     * @return JSON 树根节点
     * @throws JsonParseException 带行列号、上下文片段与期望内容
     */
    public JsonNodeModel parse(String input, ParseMode mode) throws JsonParseException {
        ParseMode m = mode == null ? ParseMode.STRICT : mode;
        return m == ParseMode.LENIENT ? lenientParser.parse(input) : strictParser.parse(input);
    }

    /** 便捷方法:默认严格模式 */
    public JsonNodeModel parse(String input) throws JsonParseException {
        return parse(input, ParseMode.STRICT);
    }
}
