package com.tidy.kit.sql;

import java.util.ArrayList;
import java.util.List;

/**
 * SQL 文本扫描的共用原语:识别引号串/标识符/注释区域。
 * 占位符统计(参数剥离启发式与绑定校验共用)与美化器的 token 切分都从这里取口径,
 * 避免两处规则漂移(与 JsonTreeSearch.stepOf 共用编码同一思路)。
 *
 * 容错取向:未闭合的引号/块注释一律视为延伸到输入末尾,不抛错——
 * 扫描器服务于"尽力处理",严格性由绑定层的数量校验兜底。
 */
final class SqlScan {

    /** code=false 的段是引号串/反引号标识符/注释,其中的 ? 不算占位符 */
    record Segment(String text, boolean code) {
    }

    private SqlScan() {
    }

    static List<Segment> segments(String sql) {
        List<Segment> out = new ArrayList<>();
        StringBuilder code = new StringBuilder();
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            int end = literalEnd(sql, i);
            if (end > i) {
                if (code.length() > 0) {
                    out.add(new Segment(code.toString(), true));
                    code.setLength(0);
                }
                out.add(new Segment(sql.substring(i, end), false));
                i = end;
            } else {
                code.append(c);
                i++;
            }
        }
        if (code.length() > 0) {
            out.add(new Segment(code.toString(), true));
        }
        return out;
    }

    static int countPlaceholders(String sql) {
        int count = 0;
        for (Segment seg : segments(sql)) {
            if (!seg.code()) {
                continue;
            }
            for (int i = 0; i < seg.text().length(); i++) {
                if (seg.text().charAt(i) == '?') {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 若 pos 处是引号串/反引号标识符/注释的起点,返回整个区域的结束下标(不含);否则返回 pos。
     * 单引号串处理 '' 双写与反斜杠转义;双引号串处理反斜杠转义;反引号内 '' 不适用,只认闭合反引号。
     */
    static int literalEnd(String sql, int pos) {
        char c = sql.charAt(pos);
        int n = sql.length();
        if (c == '\'' || c == '"' || c == '`') {
            int i = pos + 1;
            while (i < n) {
                char ch = sql.charAt(i);
                if (c != '`' && ch == '\\' && i + 1 < n) {
                    i += 2;
                    continue;
                }
                if (ch == c) {
                    // 单引号串的 '' 双写转义:连着的两个引号不算闭合
                    if (c == '\'' && i + 1 < n && sql.charAt(i + 1) == '\'') {
                        i += 2;
                        continue;
                    }
                    return i + 1;
                }
                i++;
            }
            return n;
        }
        if (c == '-' && pos + 1 < n && sql.charAt(pos + 1) == '-') {
            return lineCommentEnd(sql, pos);
        }
        if (c == '#') {
            return lineCommentEnd(sql, pos);
        }
        if (c == '/' && pos + 1 < n && sql.charAt(pos + 1) == '*') {
            int close = sql.indexOf("*/", pos + 2);
            return close < 0 ? n : close + 2;
        }
        return pos;
    }

    private static int lineCommentEnd(String sql, int pos) {
        int nl = sql.indexOf('\n', pos);
        return nl < 0 ? sql.length() : nl;
    }
}