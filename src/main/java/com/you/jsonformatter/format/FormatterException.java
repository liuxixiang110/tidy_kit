package com.you.jsonformatter.format;

/**
 * 格式化失败,携带可选的行列号(1 起)。
 * 与 JsonParseException 同风格:格式化层只给定位信息,呈现方式交给 UI(设计文档第 6 节)。
 */
public class FormatterException extends Exception {

    private final int line;
    private final int column;

    /** 无具体位置信息的错误(如输入为空、解析器创建失败) */
    public FormatterException(String message) {
        this(message, -1, -1);
    }

    public FormatterException(String message, int line, int column) {
        super(buildMessage(message, line, column));
        this.line = line;
        this.column = column;
    }

    private static String buildMessage(String message, int line, int column) {
        if (line <= 0) {
            return message;
        }
        return message + "(行 " + line + ", 列 " + column + ")";
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }
}
