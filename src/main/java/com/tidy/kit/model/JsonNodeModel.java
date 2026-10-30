package com.tidy.kit.model;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON 树节点模型。零依赖(不 import com.intellij),供 parse/ui 层共享。
 * 数字统一以字符串保存(textValue),避免精度丢失。
 */
public class JsonNodeModel {

    private static final String INDENT = "  ";

    private final NodeType type;
    /** 对象成员的 key;数组元素与根节点为 null。解析对象时在父节点回填 */
    private String key;
    /** 叶子值的字符串形式(STRING 为解码后的原始字符串内容,NUMBER 为字面量文本) */
    private final String textValue;
    private final List<JsonNodeModel> children = new ArrayList<>();
    /** 内层转义 JSON 的原始串(仅宽松模式成功解析内层时填充),无则为 null */
    private String escapedInner;

    private int depth;
    private int startLine;
    private int endLine;

    public JsonNodeModel(NodeType type, String key, String textValue) {
        this.type = type;
        this.key = key;
        this.textValue = textValue;
    }

    public NodeType getType() {
        return type;
    }

    public String getKey() {
        return key;
    }

    /** 由父对象节点回填成员 key */
    public void setKeyOf(String key) {
        this.key = key;
    }

    public String getTextValue() {
        return textValue;
    }

    public List<JsonNodeModel> getChildren() {
        return children;
    }

    public String getEscapedInner() {
        return escapedInner;
    }

    public void setEscapedInner(String escapedInner) {
        this.escapedInner = escapedInner;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public int getStartLine() {
        return startLine;
    }

    public void setStartLine(int startLine) {
        this.startLine = startLine;
    }

    public int getEndLine() {
        return endLine;
    }

    public void setEndLine(int endLine) {
        this.endLine = endLine;
    }

    public boolean isContainer() {
        return type == NodeType.OBJECT || type == NodeType.ARRAY;
    }

    /** 折叠时的行尾摘要:OBJECT -> {n keys},ARRAY -> [n] */
    public String collapsedSummary() {
        switch (type) {
            case OBJECT:
                return "{" + children.size() + " keys}";
            case ARRAY:
                return "[" + children.size() + "]";
            default:
                return "";
        }
    }

    /** 以本节点为根序列化为美化 JSON(缩进 2 空格);内层转义 JSON 会展开 */
    public String toPrettyString() {
        StringBuilder sb = new StringBuilder();
        appendPretty(sb, 0);
        return sb.toString();
    }

    private void appendPretty(StringBuilder sb, int level) {
        String pad = INDENT.repeat(level);
        String keyPart = key == null ? "" : escapeString(key) + ": ";
        switch (type) {
            case OBJECT:
            case ARRAY: {
                if (children.isEmpty()) {
                    sb.append(pad).append(keyPart).append(type == NodeType.OBJECT ? "{}" : "[]");
                    return;
                }
                char open = type == NodeType.OBJECT ? '{' : '[';
                char close = type == NodeType.OBJECT ? '}' : ']';
                sb.append(pad).append(keyPart).append(open).append('\n');
                for (int i = 0; i < children.size(); i++) {
                    children.get(i).appendPretty(sb, level + 1);
                    sb.append(i < children.size() - 1 ? ",\n" : "\n");
                }
                sb.append(pad).append(close);
                return;
            }
            case STRING: {
                sb.append(pad).append(keyPart).append(escapeString(textValue));
                if (escapedInner != null) {
                    // 内层转义 JSON:换行展开,值以带缩进的字符串块呈现
                    String inline = replaceIndent(escapedInner, level);
                    sb.append(" (escaped JSON):\n").append(inline);
                }
                return;
            }
            case NUMBER:
            case BOOLEAN:
                sb.append(pad).append(keyPart).append(textValue);
                return;
            case NULL:
                sb.append(pad).append(keyPart).append("null");
                return;
            default:
                throw new IllegalStateException("unknown type: " + type);
        }
    }

    /** 把已格式化的内层 JSON 整体再缩进一级,便于阅读 */
    private static String replaceIndent(String prettyInner, int level) {
        String extra = INDENT.repeat(level + 1);
        StringBuilder sb = new StringBuilder();
        for (String line : prettyInner.split("\n", -1)) {
            sb.append(extra).append(line).append('\n');
        }
        // 去掉末尾多余换行,交回调用方的拼接逻辑
        sb.setLength(sb.length() - 1);
        return sb.toString();
    }

    /** 按 JSON 字符串规则转义(双引号/反斜杠/控制符) */
    static String escapeString(String raw) {
        StringBuilder sb = new StringBuilder(raw.length() + 8);
        sb.append('"');
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
