package com.tidy.kit.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 GORM 调试日志(或裸 SQL + 参数行)里提取 SQL 主体与原始参数列表。
 * 纯逻辑、零 IDE 依赖(设计文档第 5 节)。
 *
 * 日志形态:... - [ SQL主体 ] - `参数1`, `参数2`
 * 裸 SQL 形态:末尾反引号列表只在"独占一行"或"组数等于占位符数"时才剥下,
 * 避免把 SELECT `a` FROM `t` 结尾的反引号标识符误当参数。
 */
public final class SqlLogParser {

    /** 提取结果:sql 为 trim 后的主体;rawParams 是反引号内的原始内容(不含反引号) */
    public record ParsedSql(String sql, List<String> rawParams) {
    }

    /** 末尾的反引号参数列表:组间只允许逗号与空白 */
    private static final Pattern TRAILING_PARAMS =
            Pattern.compile("(`[^`]*`(?:\\s*,\\s*`[^`]*`)*)\\s*$");

    private SqlLogParser() {
    }

    public static ParsedSql parse(String input) throws SqlException {
        if (input == null || input.isBlank()) {
            throw new SqlException("输入为空,请粘贴 SQL 日志或 SQL + 参数行");
        }
        String text = input.strip();

        // 日志形态:最后一个 "] - " 切分(SQL 里可能含 ],如 JSON 路径,所以从后往前找)。
        // 时间戳段也有 "] - ",所以还要求尾部以反引号参数组开头才确认是带参日志;
        // 参数区直接交给 extractParams(畸形就报错)。不满足则落到无参数日志/裸 SQL 分支
        int split = text.lastIndexOf("] - ");
        if (split >= 0) {
            String left = text.substring(0, split);
            String tail = text.substring(split + 4).strip();
            int open = left.lastIndexOf("- [");
            if (open >= 0 && tail.startsWith("`")) {
                String sql = left.substring(open + 3).strip();
                if (sql.isEmpty()) {
                    throw new SqlException("日志里找不到 SQL 主体");
                }
                return new ParsedSql(sql, extractParams(tail));
            }
        }

        // 无参数日志:含 "- [" 且以 ] 结尾(GORM 无参数时不带 " - " 尾巴)
        if (text.endsWith("]")) {
            int open = text.lastIndexOf("- [");
            if (open >= 0) {
                String sql = text.substring(open + 3, text.length() - 1).strip();
                if (!sql.isEmpty()) {
                    return new ParsedSql(sql, List.of());
                }
            }
        }

        return parseBare(text);
    }

    /** 裸 SQL:末尾反引号列表满足"独占一行"或"组数等于占位符数"才剥下当参数 */
    private static ParsedSql parseBare(String text) throws SqlException {
        Matcher m = TRAILING_PARAMS.matcher(text);
        if (m.find()) {
            String listText = m.group(1);
            String rest = text.substring(0, m.start()).stripTrailing();
            boolean ownLine = m.start() > 0 && text.charAt(m.start() - 1) == '\n';
            List<String> params = extractParams(listText);
            if (!rest.isEmpty() && (ownLine || params.size() == SqlScan.countPlaceholders(rest))) {
                return new ParsedSql(rest, params);
            }
        }
        return new ParsedSql(text, List.of());
    }

    /** 按反引号分组提取参数;组间出现逗号与空白之外的内容即报错 */
    private static List<String> extractParams(String paramText) throws SqlException {
        List<String> params = new ArrayList<>();
        String rest = paramText.strip();
        if (rest.isEmpty()) {
            return params;
        }
        Pattern group = Pattern.compile("`([^`]*)`");
        Matcher m = group.matcher(rest);
        int pos = 0;
        while (m.find()) {
            String between = rest.substring(pos, m.start());
            if (!between.isBlank() && !between.strip().equals(",")) {
                throw new SqlException("无法解析参数列表:反引号组之间只允许逗号与空白");
            }
            params.add(m.group(1));
            pos = m.end();
        }
        String tail = rest.substring(pos);
        if (!tail.isBlank() || params.isEmpty()) {
            throw new SqlException("无法解析参数列表:期望 `参数1`, `参数2` 形式");
        }
        return params;
    }
}