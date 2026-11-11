package com.tidy.kit.sql;

/**
 * SQL 参数格式化失败,携带可选的行列号(1 起)。
 * 与 FormatterException 同风格:逻辑层只给定位信息,呈现方式交给 UI(设计文档第 9 节)。
 * 本功能的错误多为语义性(数量不匹配、参数列表畸形),通常只用消息构造器。
 */
public class SqlException extends Exception {

    private final int line;
    private final int column;

    /** 无具体位置信息的错误(如占位符与参数数量不匹配) */
    public SqlException(String message) {
        this(message, -1, -1);
    }

    public SqlException(String message, int line, int column) {
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