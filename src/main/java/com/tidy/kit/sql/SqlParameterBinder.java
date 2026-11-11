package com.tidy.kit.sql;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 把 SQL 里的 ? 占位符逐个替换为参数(设计文档第 6 节)。
 * 引号串/反引号标识符/注释里的 ? 不算占位符;数量不匹配直接报错,不出半成品。
 * 纯逻辑、零 IDE 依赖。
 */
public final class SqlParameterBinder {

    /** 纯数字:整数/小数/科学计数,可带符号 */
    private static final Pattern NUMBER =
            Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");

    private SqlParameterBinder() {
    }

    public static String bind(String sql, List<String> rawParams) throws SqlException {
        List<SqlScan.Segment> segments = SqlScan.segments(sql);
        int placeholders = 0;
        for (SqlScan.Segment seg : segments) {
            if (seg.code()) {
                for (int i = 0; i < seg.text().length(); i++) {
                    if (seg.text().charAt(i) == '?') {
                        placeholders++;
                    }
                }
            }
        }
        if (placeholders != rawParams.size()) {
            throw new SqlException("SQL 有 " + placeholders + " 个占位符,但参数有 " + rawParams.size() + " 个");
        }

        StringBuilder out = new StringBuilder();
        int paramIndex = 0;
        for (SqlScan.Segment seg : segments) {
            if (!seg.code()) {
                out.append(seg.text());
                continue;
            }
            String text = seg.text();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '?') {
                    out.append(render(rawParams.get(paramIndex++)));
                } else {
                    out.append(c);
                }
            }
        }
        return out.toString();
    }

    /**
     * 类型渲染(按序判定):双引号包裹 → 字符串;纯数字/布尔/null 原样;
     * <nil> 是 GORM 的 nil 输出 → null;其余兜底按字符串加单引号。
     */
    static String render(String raw) {
        if (raw.length() >= 2 && raw.charAt(0) == '"' && raw.charAt(raw.length() - 1) == '"') {
            return quote(unescapeGo(raw.substring(1, raw.length() - 1)));
        }
        if (NUMBER.matcher(raw).matches()) {
            return raw;
        }
        if (raw.equalsIgnoreCase("null") || raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false")) {
            return raw;
        }
        if (raw.equals("<nil>")) {
            return "null";
        }
        return quote(raw);
    }

    /** SQL 单引号字符串:内部单引号双写 */
    private static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    /** GORM 用 Go 风格打印字符串参数,还原常见转义后再进 SQL */
    private static String unescapeGo(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                switch (next) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    default -> out.append('\\').append(next);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}