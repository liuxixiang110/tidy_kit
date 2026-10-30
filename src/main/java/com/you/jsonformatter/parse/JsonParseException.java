package com.you.jsonformatter.parse;

/**
 * 解析错误,携带行列号(1 起)、上下文片段(前后 20 字符)与期望内容。
 * 解析层只抛出定位信息,由 UI 决定呈现方式(设计文档第 6 节)。
 */
public class JsonParseException extends Exception {

    private final int line;
    private final int column;
    private final String snippet;
    private final String expected;

    public JsonParseException(String message, int line, int column, String snippet, String expected) {
        super(buildMessage(message, line, column, snippet, expected));
        this.line = line;
        this.column = column;
        this.snippet = snippet;
        this.expected = expected;
    }

    /** 无具体位置信息时的错误(如未找到 JSON 主体) */
    public JsonParseException(String message) {
        this(message, -1, -1, "", null);
    }

    /** 有期望内容但无定位信息的错误 */
    public JsonParseException(String message, String expected) {
        this(message, -1, -1, "", expected);
    }

    private static String buildMessage(String message, int line, int column, String snippet, String expected) {
        StringBuilder sb = new StringBuilder(message);
        if (line > 0) {
            sb.append("(行 ").append(line).append(", 列 ").append(column).append(")");
        }
        if (expected != null && !expected.isEmpty()) {
            sb.append(",期望: ").append(expected);
        }
        if (snippet != null && !snippet.isEmpty()) {
            sb.append(",上下文: ...").append(snippet).append("...");
        }
        return sb.toString();
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getSnippet() {
        return snippet;
    }

    public String getExpected() {
        return expected;
    }
}
