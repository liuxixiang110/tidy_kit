package com.tidy.kit.parse;

import com.tidy.kit.model.JsonNodeModel;

/**
 * 宽松模式流水线(设计 5.2):
 * ① 扫描第一个平衡 {/[ 提取主体(容忍前后杂质)
 * ② 顶层为带引号转义字符串时,交由 CoreJsonParser 递归解析内层(见 attachEscapedInner)
 * ③ 单引号 / 尾逗号 / 注释 容错由 CoreJsonParser(lenient=true) 承担
 * 不容忍:括号不平衡、字符串截断、无 JSON 主体。
 */
class LenientParser {

    JsonNodeModel parse(String input) throws JsonParseException {
        if (input == null || input.trim().isEmpty()) {
            throw new JsonParseException("输入为空", "非空 JSON");
        }
        String trimmed = input.trim();
        char lead = trimmed.charAt(0);

        // 顶层带引号(转义字符串 或 普通字符串标量):整体解析,内层转义 JSON 由解析器自动展开
        if (lead == '"' || lead == '\'') {
            return new CoreJsonParser(trimmed, true).parseValueRoot();
        }

        // ① 提取第一个平衡的 {/[ 主体
        int bodyStart = indexOfFirstBracket(trimmed);
        if (bodyStart < 0) {
            throw new JsonParseException("未找到 JSON 主体(缺少 { 或 [)", "JSON 对象或数组");
        }
        int bodyEnd = findMatchingBracket(trimmed, bodyStart);
        if (bodyEnd < 0) {
            throw new JsonParseException("括号不平衡,主体未闭合", "} 或 ]");
        }
        String body = trimmed.substring(bodyStart, bodyEnd + 1);
        // 主体在原输入中的行偏移,便于错误定位仍指向原始文本
        String prefix = trimmed.substring(0, bodyStart);
        int lineOffset = countLines(prefix);
        JsonNodeModel root = new CoreJsonParser(body, true).parseValueRoot();
        if (lineOffset > 0) {
            shiftLines(root, lineOffset);
        }
        return root;
    }

    private int indexOfFirstBracket(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{' || c == '[') {
                return i;
            }
        }
        return -1;
    }

    /** 扫描到与 start 处括号匹配的闭合括号下标;考虑字符串与转义;不平衡返回 -1 */
    private int findMatchingBracket(String s, int start) throws JsonParseException {
        int depth = 0;
        char inString = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString != 0) {
                if (c == '\\') {
                    i++; // 跳过转义字符
                } else if (c == inString) {
                    inString = 0;
                }
                continue;
            }
            switch (c) {
                case '"':
                case '\'':
                    inString = c;
                    break;
                case '{':
                case '[':
                    depth++;
                    break;
                case '}':
                case ']':
                    depth--;
                    if (depth == 0) {
                        return i;
                    } else if (depth < 0) {
                        throw new JsonParseException("括号不平衡,出现多余闭合括号 '" + c + "'",
                                "匹配的开头括号");
                    }
                    break;
                default:
                    break;
            }
        }
        return -1;
    }

    private int countLines(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    private void shiftLines(JsonNodeModel node, int offset) {
        node.setStartLine(node.getStartLine() + offset);
        node.setEndLine(node.getEndLine() + offset);
        for (JsonNodeModel child : node.getChildren()) {
            shiftLines(child, offset);
        }
    }
}
