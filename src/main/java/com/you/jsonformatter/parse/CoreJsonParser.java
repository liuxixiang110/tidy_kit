package com.you.jsonformatter.parse;

import com.you.jsonformatter.model.JsonNodeModel;
import com.you.jsonformatter.model.NodeType;

/**
 * 自写递归下降解析器核心(不引第三方库,便于保留错误定位上下文)。
 * lenient=true 时额外容忍:注释(// 与 /* *\/)、单引号字符串、尾逗号、
 * 值位置的内层转义 JSON(解析成功则挂到 escapedInner)。
 * 不 import 任何 com.intellij 类,可脱离 IDE 单测。
 */
class CoreJsonParser {

    /** 嵌套深度上限,超过报错"嵌套过深"而非 StackOverflow */
    static final int MAX_DEPTH = 1000;

    private final String src;
    private final boolean lenient;
    private int pos;

    CoreJsonParser(String src, boolean lenient) {
        this.src = src;
        this.lenient = lenient;
    }

    /** 解析单个值(要求输入 Consumes 到结尾由调用方校验) */
    JsonNodeModel parseValueRoot() throws JsonParseException {
        skipWhitespace();
        JsonNodeModel root = parseValue(0);
        skipWhitespace();
        if (pos < src.length()) {
            fail("输入在值的之后有多余内容", "JSON 结尾");
        }
        return root;
    }

    private JsonNodeModel parseValue(int depth) throws JsonParseException {
        if (depth > MAX_DEPTH) {
            fail("嵌套过深(超过 " + MAX_DEPTH + " 层)", null);
        }
        skipWhitespace();
        if (pos >= src.length()) {
            fail("输入意外结束", "JSON 值");
        }
        char c = src.charAt(pos);
        JsonNodeModel node;
        switch (c) {
            case '{':
                node = parseObject(depth);
                break;
            case '[':
                node = parseArray(depth);
                break;
            case '"':
            case '\'':
                node = parseString(null);
                break;
            case 't':
                node = parseLiteral("true", NodeType.BOOLEAN, "true", null);
                break;
            case 'f':
                node = parseLiteral("false", NodeType.BOOLEAN, "false", null);
                break;
            case 'n':
                node = parseLiteral("null", NodeType.NULL, "null", null);
                break;
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    JsonNodeModel num = parseNumber(null);
                    num.setDepth(depth);
                    return num;
                }
                fail("无法识别的字符 '" + describe(c) + "'", "JSON 值");
                return null; // unreachable
        }
        node.setDepth(depth);
        return node;
    }

    // ---------- object ----------

    private JsonNodeModel parseObject(int depth) throws JsonParseException {
        int startLine = lineAt(pos);
        JsonNodeModel obj = new JsonNodeModel(NodeType.OBJECT, null, null);
        obj.setStartLine(startLine);
        pos++; // {
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            obj.setEndLine(lineAt(pos - 1));
            return obj;
        }
        while (true) {
            skipWhitespace();
            char c = peek();
            if (c != '"' && c != '\'') {
                fail("对象 key 必须用双引号包裹", "\" 或 }");
            }
            JsonNodeModel key = parseString(null);
            skipWhitespace();
            if (peek() != ':') {
                fail("对象 key 之后缺少冒号", "\":\"");
            }
            pos++; // :
            skipWhitespace();
            JsonNodeModel value = parseValue(depth + 1);
            value.setKeyOf(key.getTextValue());
            obj.getChildren().add(value);
            skipWhitespace();
            char after = peek();
            if (after == ',') {
                pos++;
                skipWhitespace();
                if (peek() == '}' && lenient) {
                    pos++; // 尾逗号
                    obj.setEndLine(lineAt(pos - 1));
                    return obj;
                }
                continue;
            }
            if (after == '}') {
                pos++;
                obj.setEndLine(lineAt(pos - 1));
                return obj;
            }
            fail("对象成员之间缺少逗号或未闭合", "\",\" 或 \"}\"");
        }
    }

    // ---------- array ----------

    private JsonNodeModel parseArray(int depth) throws JsonParseException {
        int startLine = lineAt(pos);
        JsonNodeModel arr = new JsonNodeModel(NodeType.ARRAY, null, null);
        arr.setStartLine(startLine);
        pos++; // [
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            arr.setEndLine(lineAt(pos - 1));
            return arr;
        }
        while (true) {
            JsonNodeModel value = parseValue(depth + 1);
            arr.getChildren().add(value);
            skipWhitespace();
            char after = peek();
            if (after == ',') {
                pos++;
                skipWhitespace();
                if (peek() == ']' && lenient) {
                    pos++; // 尾逗号
                    arr.setEndLine(lineAt(pos - 1));
                    return arr;
                }
                continue;
            }
            if (after == ']') {
                pos++;
                arr.setEndLine(lineAt(pos - 1));
                return arr;
            }
            fail("数组元素之间缺少逗号或未闭合", "\",\" 或 \"]\"");
        }
    }

    // ---------- string ----------

    private JsonNodeModel parseString(String key) throws JsonParseException {
        int startLine = lineAt(pos);
        char quote = src.charAt(pos);
        if (quote == '\'' && !lenient) {
            fail("严格模式不支持单引号字符串", "\"");
        }
        pos++; // opening quote
        StringBuilder decoded = new StringBuilder();
        while (true) {
            if (pos >= src.length()) {
                fail("字符串未闭合", String.valueOf(quote));
            }
            char c = src.charAt(pos);
            if (c == quote) {
                pos++;
                break;
            }
            if (c == '\n') {
                fail("字符串中出现未转义的换行", "\\n 或字符串闭合引号");
            }
            if (c == '\\') {
                pos++;
                char esc = src.charAt(pos);
                switch (esc) {
                    case '"':
                        decoded.append('"');
                        break;
                    case '\\':
                        decoded.append('\\');
                        break;
                    case '/':
                        decoded.append('/');
                        break;
                    case 'b':
                        decoded.append('\b');
                        break;
                    case 'f':
                        decoded.append('\f');
                        break;
                    case 'n':
                        decoded.append('\n');
                        break;
                    case 'r':
                        decoded.append('\r');
                        break;
                    case 't':
                        decoded.append('\t');
                        break;
                    case 'u':
                        decoded.append(parseUnicodeEscape());
                        continue; // parseUnicodeEscape 已前移 pos
                    case '\'':
                        if (quote == '\'') {
                            decoded.append('\'');
                        } else {
                            fail("非法转义序列 \\“" + describe(esc) + "”",
                                    "\\\", \\\\, \\/, \\b, \\f, \\n, \\r, \\t 或 \\uXXXX");
                        }
                        break;
                    default:
                        fail("非法转义序列 \\“" + describe(esc) + "”",
                                "\\\", \\\\, \\/, \\b, \\f, \\n, \\r, \\t 或 \\uXXXX");
                }
                pos++;
            } else {
                decoded.append(c);
                pos++;
            }
        }
        JsonNodeModel node = new JsonNodeModel(NodeType.STRING, key, decoded.toString());
        node.setStartLine(startLine);
        node.setEndLine(lineAt(pos - 1));
        if (lenient) {
            attachEscapedInner(node);
        }
        return node;
    }

    private char parseUnicodeEscape() throws JsonParseException {
        // pos 指向 'u'
        if (pos + 4 >= src.length()) {
            fail("\\u 转义序列不完整", "4 位十六进制数字");
        }
        String hex = src.substring(pos + 1, pos + 5);
        int cp;
        try {
            cp = Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            fail("\\u 转义序列含非法十六进制字符 \"" + hex + "\"", "4 位十六进制数字");
            return 0; // unreachable
        }
        pos += 5; // u + 4 hex
        return (char) cp;
    }

    /** 宽松模式:字符串值形如 JSON 时尝试解析内层,失败不影响外层(设计 6.4) */
    private void attachEscapedInner(JsonNodeModel node) {
        String text = node.getTextValue();
        if (text == null || text.isEmpty()) {
            return;
        }
        char first = text.charAt(0);
        if (first != '{' && first != '[') {
            return;
        }
        try {
            JsonNodeModel inner = new CoreJsonParser(text, true).parseValueRoot();
            if (inner.isContainer()) {
                node.setEscapedInner(inner.toPrettyString());
            }
        } catch (JsonParseException ignored) {
            // 内层解析失败:按原始字符串渲染
        }
    }

    // ---------- number ----------

    private JsonNodeModel parseNumber(String key) throws JsonParseException {
        int startLine = lineAt(pos);
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        if (pos >= src.length()) {
            fail("数字未写完", "0-9");
        }
        if (peek() == '0') {
            pos++;
        } else if (Character.isDigit(peek())) {
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
        } else {
            fail("无法识别的字符 '" + describe(peek()) + "'", "0-9");
        }
        if (pos < src.length() && src.charAt(pos) == '.') {
            pos++;
            if (pos >= src.length() || !Character.isDigit(src.charAt(pos))) {
                fail("小数点后缺少数字", "0-9");
            }
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                pos++;
            }
            if (pos >= src.length() || !Character.isDigit(src.charAt(pos))) {
                fail("指数部分缺少数字", "0-9");
            }
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
        }
        // 数字后不能紧跟字母等标识符字符
        if (pos < src.length()) {
            char next = src.charAt(pos);
            if (Character.isLetter(next) || next == '_' || next == '-' || next == '.') {
                fail("数字后紧跟非法字符 '" + describe(next) + "'", "空白 , } ] 或输入结尾");
            }
        }
        JsonNodeModel node = new JsonNodeModel(NodeType.NUMBER, key, src.substring(start, pos));
        node.setStartLine(startLine);
        node.setEndLine(lineAt(pos - 1));
        return node;
    }

    // ---------- literal ----------

    private JsonNodeModel parseLiteral(String word, NodeType type, String textValue, String key)
            throws JsonParseException {
        int startLine = lineAt(pos);
        if (!src.startsWith(word, pos)) {
            fail("无法识别的字面量", "\"" + word + "\"");
        }
        pos += word.length();
        if (pos < src.length()) {
            char next = src.charAt(pos);
            if (Character.isLetterOrDigit(next) || next == '_') {
                fail("字面量 \"" + word + "\" 后紧跟非法字符 '" + describe(next) + "'",
                        "空白 , : } ] 或输入结尾");
            }
        }
        JsonNodeModel node = new JsonNodeModel(type, key, textValue);
        node.setStartLine(startLine);
        node.setEndLine(lineAt(pos - 1));
        return node;
    }

    // ---------- whitespace / comment ----------

    private void skipWhitespace() throws JsonParseException {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else if (lenient && c == '/' && pos + 1 < src.length()) {
                char next = src.charAt(pos + 1);
                if (next == '/') {
                    while (pos < src.length() && src.charAt(pos) != '\n') {
                        pos++;
                    }
                } else if (next == '*') {
                    int end = src.indexOf("*/", pos + 2);
                    if (end < 0) {
                        fail("块注释未闭合", "*/");
                    }
                    pos = end + 2;
                } else {
                    break;
                }
            } else {
                break;
            }
        }
    }

    // ---------- helpers ----------

    private char peek() {
        return pos < src.length() ? src.charAt(pos) : '\0';
    }

    private static String describe(char c) {
        if (c == '\0') {
            return "EOF";
        }
        if (c == '\n') {
            return "\\n";
        }
        if (c < 0x20) {
            return String.format("\\u%04x", (int) c);
        }
        return String.valueOf(c);
    }

    /** 字符偏移 -> 行号(1 起) */
    private int lineAt(int index) {
        int clamped = Math.max(0, Math.min(index, src.length() - 1));
        int line = 1;
        for (int i = 0; i < clamped; i++) {
            if (src.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private JsonParseException fail(String message, String expected) throws JsonParseException {
        int line = lineAt(pos);
        int col = columnAt(pos);
        int from = Math.max(0, pos - 20);
        int to = Math.min(src.length(), pos + 20);
        String snippet = src.substring(from, to).replace("\n", "\\n");
        throw new JsonParseException(message, line, col, snippet, expected);
    }

    /** 字符偏移 -> 列号(1 起,基于所在行行首) */
    private int columnAt(int index) {
        int clamped = Math.max(0, Math.min(index, src.length()));
        int lineStart = 0;
        for (int i = 0; i < clamped; i++) {
            if (src.charAt(i) == '\n') {
                lineStart = i + 1;
            }
        }
        return clamped - lineStart + 1;
    }
}
