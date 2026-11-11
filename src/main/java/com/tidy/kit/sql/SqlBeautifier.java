package com.tidy.kit.sql;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * 轻量 SQL 美化:关键字换行 + 2 空格缩进,只改空白、绝不改 token 内容
 * (关键字大小写照搬、字符串/注释原样)。设计文档第 7 节。
 *
 * 这是启发式排版器而非完整 SQL 解析器:方言边角可能排得不完美,但保证 token 无损
 * ——"去掉引号串/注释之外的所有空白"后,输入与输出的字符序列完全一致(单测硬判据)。
 * 永不抛错:未知 token 原样通过。
 */
public final class SqlBeautifier {

    private enum TokenType { WORD, STRING, NUMBER, SYMBOL, LINE_COMMENT, BLOCK_COMMENT }

    /** glued:源码中该 token 前没有空白(用于 ifnull( 与 IN ( 的括号贴附判断) */
    record Token(TokenType type, String text, boolean glued) {
    }

    private enum FrameKind { BASE, SUBQUERY, CASE }

    private static final class Frame {
        final FrameKind kind;
        boolean selectList;
        int inlineDepth;
        /** CASE 帧专用:CASE 关键字所在行的缩进,WHEN/ELSE 比它深一级,END 回到它 */
        int baseIndent;

        Frame(FrameKind kind) {
            this.kind = kind;
        }
    }

    private static final Set<String> CLAUSE_KEYWORDS = Set.of(
            "SELECT", "FROM", "WHERE", "HAVING", "LIMIT", "WITH", "VALUES", "UPDATE", "SET");

    private static final Set<String> JOIN_PREFIXES = Set.of("LEFT", "RIGHT", "INNER", "OUTER", "CROSS", "FULL");

    private static final Set<String> MULTI_OPERATORS = Set.of("->", "<=", ">=", "<>", "!=", "||");

    private SqlBeautifier() {
    }

    public static String beautify(String sql) {
        if (sql == null || sql.isBlank()) {
            return "";
        }
        return new SqlBeautifier().run(tokenize(sql));
    }

    // ---------- 排版 ----------

    private final StringBuilder out = new StringBuilder();
    private final Deque<Frame> frames = new ArrayDeque<>();
    private boolean lineHasContent;
    private boolean forceNewline;
    private boolean inClause;
    private int clauseIndent;
    private int joinIndent = -1;
    private int currentLineIndent;

    private String run(List<Token> tokens) {
        frames.push(new Frame(FrameKind.BASE));
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.type() != TokenType.WORD) {
                if (t.type() == TokenType.SYMBOL) {
                    if (t.text().equals("(")) {
                        appendToken(t);
                        String next = nextMeaningfulUp(tokens, i + 1);
                        if ("SELECT".equals(next) || "WITH".equals(next)) {
                            // 子查询括号:内层缩进 +1,闭括号独占一行回外层
                            frames.push(new Frame(FrameKind.SUBQUERY));
                            newline(indent());
                        } else {
                            frames.peek().inlineDepth++;
                        }
                        continue;
                    }
                    if (t.text().equals(")")) {
                        if (frames.peek().kind == FrameKind.SUBQUERY) {
                            frames.pop();
                            newline(indent());
                        } else if (frames.peek().inlineDepth > 0) {
                            frames.peek().inlineDepth--;
                        }
                        appendToken(t);
                        continue;
                    }
                    if (t.text().equals(",") && frames.peek().selectList && frames.peek().inlineDepth == 0) {
                        // SELECT 列表的顶层逗号:每列一行;函数参数/IN 列表里的逗号不动
                        appendToken(t);
                        newline(indent() + 1);
                        continue;
                    }
                }
                appendToken(t);
                continue;
            }
            String up = t.text().toUpperCase();

            // 多词子句:GROUP BY / ORDER BY / UNION [ALL|DISTINCT] / INSERT INTO / DELETE FROM
            if ((up.equals("GROUP") || up.equals("ORDER")) && wordIs(tokens, i + 1, "BY")) {
                emitClause(t.text() + " " + tokens.get(i + 1).text());
                i++;
                continue;
            }
            if (up.equals("UNION")) {
                StringBuilder kw = new StringBuilder(t.text());
                if (wordIs(tokens, i + 1, "ALL") || wordIs(tokens, i + 1, "DISTINCT")) {
                    kw.append(' ').append(tokens.get(++i).text());
                }
                emitClause(kw.toString());
                continue;
            }
            if ((up.equals("INSERT") && wordIs(tokens, i + 1, "INTO"))
                    || (up.equals("DELETE") && wordIs(tokens, i + 1, "FROM"))) {
                emitClause(t.text() + " " + tokens.get(i + 1).text());
                i++;
                continue;
            }

            // JOIN 家族:LEFT [OUTER] JOIN 等,前缀逐个收集
            if (up.equals("JOIN") || JOIN_PREFIXES.contains(up)) {
                int j = i;
                StringBuilder kw = new StringBuilder(t.text());
                while (true) {
                    String next = wordUpAt(tokens, j + 1);
                    if (next == null || !(JOIN_PREFIXES.contains(next) || next.equals("JOIN"))) {
                        break;
                    }
                    kw.append(' ').append(tokens.get(++j).text());
                    if (next.equals("JOIN")) {
                        break;
                    }
                }
                if (j > i || up.equals("JOIN")) {
                    newline(indent());
                    appendText(kw.toString());
                    joinIndent = indent();
                    inClause = false;
                    i = j;
                    continue;
                }
            }

            if (CLAUSE_KEYWORDS.contains(up)) {
                emitClause(t.text());
                if (up.equals("SELECT")) {
                    frames.peek().selectList = true;
                } else if (up.equals("FROM")) {
                    frames.peek().selectList = false;
                }
                continue;
            }
            if (up.equals("AND") || up.equals("OR")) {
                newline((inClause ? clauseIndent : indent()) + 1);
                appendText(t.text());
                continue;
            }
            if (up.equals("ON")) {
                int level = (joinIndent >= 0 ? joinIndent : indent()) + 1;
                newline(level);
                appendText(t.text());
                clauseIndent = level;
                inClause = true;
                continue;
            }
            if (up.equals("CASE")) {
                appendToken(t);
                Frame caseFrame = new Frame(FrameKind.CASE);
                caseFrame.baseIndent = currentLineIndent;
                frames.push(caseFrame);
                continue;
            }
            if (up.equals("WHEN") || up.equals("ELSE")) {
                Frame top = frames.peek();
                newline(top.kind == FrameKind.CASE ? top.baseIndent + 1 : indent());
                appendText(t.text());
                continue;
            }
            if (up.equals("END") && frames.peek().kind == FrameKind.CASE) {
                Frame caseFrame = frames.pop();
                newline(caseFrame.baseIndent);
                appendText(t.text());
                continue;
            }
            appendToken(t);
        }
        return out.toString().strip();
    }

    private void emitClause(String keyword) {
        newline(indent());
        appendText(keyword);
        clauseIndent = indent();
        inClause = true;
    }

    private int indent() {
        return frames.size() - 1;
    }

    /** 换行并缩进;先削掉行尾多余空格,已在新行开头时只调缩进不叠空行 */
    private void newline(int level) {
        while (out.length() > 0 && out.charAt(out.length() - 1) == ' ') {
            out.deleteCharAt(out.length() - 1);
        }
        if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
            out.append('\n');
        }
        out.append("  ".repeat(Math.max(0, level)));
        lineHasContent = false;
        currentLineIndent = level;
        // 任何换行路径都消费掉行注释留下的强制换行,避免后续 token 再换一次行
        forceNewline = false;
    }

    private void appendText(String text) {
        out.append(text);
        lineHasContent = true;
    }

    private void appendToken(Token t) {
        if (t.type() == TokenType.LINE_COMMENT) {
            if (lineHasContent) {
                newline(indent());
            }
            appendText(t.text());
            forceNewline = true;
            return;
        }
        if (forceNewline) {
            newline(indent());
            forceNewline = false;
        }
        String text = t.text();
        boolean noSpaceBefore = text.equals(")") || text.equals(",") || text.equals(";") || text.equals(".");
        if (lineHasContent && !noSpaceBefore) {
            char last = out.charAt(out.length() - 1);
            boolean attach = last == '(' || last == '.';
            // 函数名后的括号贴附(ifnull(),源码里就没空格);IN ( 这类关键字后的括号保留空格
            if (text.equals("(") && t.glued() && isWordChar(last)) {
                attach = true;
            }
            if (!attach) {
                out.append(' ');
            }
        }
        appendText(text);
    }

    /** 跳过注释取下一个有效 token 的大写形式(子查询括号判定用);不是单词返回 null */
    private static String nextMeaningfulUp(List<Token> tokens, int index) {
        for (int j = index; j < tokens.size(); j++) {
            Token t = tokens.get(j);
            if (t.type() == TokenType.LINE_COMMENT || t.type() == TokenType.BLOCK_COMMENT) {
                continue;
            }
            return t.type() == TokenType.WORD ? t.text().toUpperCase() : null;
        }
        return null;
    }

    private static boolean wordIs(List<Token> tokens, int index, String word) {
        return word.equals(wordUpAt(tokens, index));
    }

    private static String wordUpAt(List<Token> tokens, int index) {
        if (index >= tokens.size() || tokens.get(index).type() != TokenType.WORD) {
            return null;
        }
        return tokens.get(index).text().toUpperCase();
    }

    // ---------- 分词 ----------

    static List<Token> tokenize(String sql) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int n = sql.length();
        boolean pendingSpace = false;
        while (i < n) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                pendingSpace = true;
                i++;
                continue;
            }
            boolean glued = !pendingSpace && !tokens.isEmpty();
            pendingSpace = false;

            int litEnd = SqlScan.literalEnd(sql, i);
            if (litEnd > i) {
                String text = sql.substring(i, litEnd);
                TokenType type = (c == '\'' || c == '"' || c == '`') ? TokenType.STRING
                        : text.startsWith("/*") ? TokenType.BLOCK_COMMENT : TokenType.LINE_COMMENT;
                tokens.add(new Token(type, text, glued));
                i = litEnd;
                continue;
            }
            if (Character.isLetter(c) || c == '_' || c == '$' || c == '.') {
                int j = i;
                while (j < n && isWordChar(sql.charAt(j))) {
                    j++;
                }
                tokens.add(new Token(TokenType.WORD, sql.substring(i, j), glued));
                i = j;
                continue;
            }
            if (Character.isDigit(c)) {
                int j = i;
                while (j < n && Character.isDigit(sql.charAt(j))) {
                    j++;
                }
                if (j < n && sql.charAt(j) == '.') {
                    j++;
                    while (j < n && Character.isDigit(sql.charAt(j))) {
                        j++;
                    }
                }
                if (j < n && (sql.charAt(j) == 'e' || sql.charAt(j) == 'E')) {
                    int k = j + 1;
                    if (k < n && (sql.charAt(k) == '+' || sql.charAt(k) == '-')) {
                        k++;
                    }
                    if (k < n && Character.isDigit(sql.charAt(k))) {
                        j = k;
                        while (j < n && Character.isDigit(sql.charAt(j))) {
                            j++;
                        }
                    }
                }
                tokens.add(new Token(TokenType.NUMBER, sql.substring(i, j), glued));
                i = j;
                continue;
            }
            if (sql.startsWith("->>", i)) {
                tokens.add(new Token(TokenType.SYMBOL, "->>", glued));
                i += 3;
                continue;
            }
            if (i + 2 <= n && MULTI_OPERATORS.contains(sql.substring(i, i + 2))) {
                tokens.add(new Token(TokenType.SYMBOL, sql.substring(i, i + 2), glued));
                i += 2;
                continue;
            }
            tokens.add(new Token(TokenType.SYMBOL, String.valueOf(c), glued));
            i++;
        }
        return tokens;
    }

    /** 点号算词内字符:sc.id 保持一体,不被空格拆开 */
    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '.';
    }
}