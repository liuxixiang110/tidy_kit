package com.you.jsonformatter.format;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * HTML 美化:自写轻量 tokenizer(零依赖,与手写 JSON 解析器同风格)。
 *
 * 取向是"把粘进来的 HTML 排好版看",宽松优先:
 * - 未闭合的标签自动补一个闭标签,多余的闭标签原样留一行,不静默丢用户内容
 * - 属性顺序、引号风格、标签大小写一律照抄,不做浏览器式规范化(不补 tbody、不提 head)
 * - script / style / textarea 内部原样保留(缩进会改变 textarea 的值,也会破坏脚本)
 * - 行内元素与文本并排进一行,块级元素各自一行;空白折叠成一个空格但保留"此处原有空白",
 *   否则 {@code Hello <b>W</b>} 会粘连成 {@code HelloW}
 */
public class HtmlFormatter implements TextFormatter {

    private static final String INDENT = "  ";

    /** 空元素:没有闭标签,也不该有子节点 */
    private static final Set<String> VOID = Set.of(
            "area", "base", "br", "col", "embed", "hr", "img", "input", "link",
            "meta", "param", "source", "track", "wbr");

    /** 行内元素:与前后文本并排在一行 */
    private static final Set<String> INLINE = Set.of(
            "a", "abbr", "b", "bdi", "bdo", "big", "br", "cite", "code", "data", "del",
            "dfn", "em", "font", "i", "img", "ins", "kbd", "label", "mark", "nobr", "q",
            "s", "samp", "small", "span", "strong", "sub", "sup", "time", "tt", "u",
            "var", "wbr");

    /** 原始文本元素:从开标签到匹配的闭标签之间不做任何解析与重排 */
    private static final Set<String> RAW_TEXT = Set.of("script", "style", "textarea");

    @Override
    public String format(String source) throws FormatterException {
        if (source == null || source.isBlank()) {
            throw new FormatterException("输入为空,没有可格式化的 HTML");
        }
        Node root = new Parser(source).parse();
        StringBuilder out = new StringBuilder(source.length() + 64);
        renderChildren(root, 0, out);
        return out.toString().strip();
    }

    // ---------- 渲染 ----------

    /** 逐个子节点输出;连续的文本与行内元素合并成一行 */
    private static void renderChildren(Node parent, int depth, StringBuilder out) {
        StringBuilder run = null;
        for (Node child : parent.children) {
            if (isInlineChild(child)) {
                if (run == null) {
                    run = new StringBuilder();
                }
                run.append(inlineText(child));
            } else {
                flushRun(out, depth, run);
                run = null;
                render(child, depth, out);
            }
        }
        flushRun(out, depth, run);
    }

    private static void flushRun(StringBuilder out, int depth, StringBuilder run) {
        if (run == null) {
            return;
        }
        String body = run.toString().strip();
        if (!body.isEmpty()) {
            out.append(INDENT.repeat(depth)).append(body).append('\n');
        }
    }

    private static void render(Node node, int depth, StringBuilder out) {
        String pad = INDENT.repeat(depth);
        switch (node.kind) {
            case TEXT -> {
                String body = collapse(node.raw).strip();
                if (!body.isEmpty()) {
                    out.append(pad).append(body).append('\n');
                }
            }
            case RAW -> out.append(pad).append(node.raw.strip()).append('\n');
            case ELEMENT -> {
                if (RAW_TEXT.contains(node.name)) {
                    out.append(pad).append(node.raw).append('\n');
                    // 内容原样吐,不追加缩进:内部可能自带排版,textarea 的值更不能动
                    for (Node child : node.children) {
                        String body = child.raw.strip();
                        if (!body.isEmpty()) {
                            out.append(body).append('\n');
                        }
                    }
                    out.append(pad).append(closeText(node)).append('\n');
                } else if (node.selfContained || node.children.isEmpty()) {
                    out.append(pad).append(node.raw).append(closeText(node)).append('\n');
                } else if (isFlat(node)) {
                    // 行内内容两端的空白无意义,必须刷掉,否则 <p>  Hello 会多一个行首空格
                    out.append(pad).append(node.raw).append(flatten(node).strip()).append(closeText(node)).append('\n');
                } else {
                    out.append(pad).append(node.raw).append('\n');
                    renderChildren(node, depth + 1, out);
                    out.append(pad).append(closeText(node)).append('\n');
                }
            }
        }
    }

    /** 文本节点或"整体能排进一行"的行内元素 */
    private static boolean isInlineChild(Node child) {
        return child.kind == Kind.TEXT
                || (child.kind == Kind.ELEMENT && INLINE.contains(child.name) && isFlat(child));
    }

    /** 子树是否全是行内内容(注释会破坏这一点,需要单独占行) */
    private static boolean isFlat(Node node) {
        for (Node child : node.children) {
            if (child.kind == Kind.TEXT) {
                continue;
            }
            if (child.kind == Kind.RAW) {
                return false;
            }
            if (!INLINE.contains(child.name) || !isFlat(child)) {
                return false;
            }
        }
        return true;
    }

    /** 把一棵行内子树压成一行 */
    private static String flatten(Node node) {
        StringBuilder sb = new StringBuilder();
        for (Node child : node.children) {
            sb.append(child.kind == Kind.TEXT ? collapse(child.raw) : inlineText(child));
        }
        return sb.toString();
    }

    private static String inlineText(Node node) {
        if (node.kind == Kind.TEXT) {
            return collapse(node.raw);
        }
        return node.raw + flatten(node) + closeText(node);
    }

    /** 闭标签:照抄原文;原文缺闭标签就按开标签的名字补一个,让输出仍是合法 HTML */
    private static String closeText(Node node) {
        if (node.selfContained) {
            return "";
        }
        return node.closeRaw != null ? node.closeRaw : "</" + node.name + ">";
    }

    /** 空白折叠成一个空格,并保留首尾"此处原本有空白"的信息 */
    private static String collapse(String raw) {
        String body = raw.strip();
        if (body.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (Character.isWhitespace(raw.charAt(0))) {
            sb.append(' ');
        }
        sb.append(body.replaceAll("\\s+", " "));
        if (Character.isWhitespace(raw.charAt(raw.length() - 1))) {
            sb.append(' ');
        }
        return sb.toString();
    }

    // ---------- 建树 ----------

    private enum Kind { TEXT, RAW, ELEMENT }

    /** 极简标记节点:元素带原样的开/闭标签文本,文本与片段(注释、声明、PI)带原文 */
    private static final class Node {
        final Kind kind;
        /** 元素的小写标签名,其他节点为空 */
        final String name;
        /** 元素:原样开标签;文本/片段:原样内容 */
        final String raw;
        final boolean selfContained;
        /** 元素的闭标签原文,缺闭标签时为 null */
        String closeRaw;
        final List<Node> children = new ArrayList<>();

        private Node(Kind kind, String name, String raw, boolean selfContained) {
            this.kind = kind;
            this.name = name;
            this.raw = raw;
            this.selfContained = selfContained;
        }

        static Node element(String name, String openRaw, boolean selfContained) {
            return new Node(Kind.ELEMENT, name, openRaw, selfContained);
        }

        static Node text(String raw) {
            return new Node(Kind.TEXT, "", raw, false);
        }

        static Node raw(String raw) {
            return new Node(Kind.RAW, "", raw, false);
        }
    }

    /**
     * 单遍扫描建树。栈里是当前未闭合的元素链,遇到闭标签时向上找最近的同名开标签:
     * 中间那些没闭合的元素就留在树上(自动补齐),找不到就把闭标签当片段留一行。
     */
    private static final class Parser {
        private final String src;
        /** 小写副本,用于忽略大小写地找闭标签 */
        private final String lower;
        private int index;
        private final Node root = Node.element("#root", "", false);
        private final List<Node> stack = new ArrayList<>();

        Parser(String src) {
            this.src = src;
            this.lower = src.toLowerCase(Locale.ROOT);
            stack.add(root);
        }

        Node parse() {
            while (index < src.length()) {
                if (src.charAt(index) != '<' || !consumeMarkup()) {
                    consumeText();
                }
            }
            return root;
        }

        private Node current() {
            return stack.get(stack.size() - 1);
        }

        private void consumeText() {
            int start = index;
            int next = src.indexOf('<', start);
            int end = next < 0 ? src.length() : next;
            if (end == start) {
                // 裸的 '<'(比如 a < b),按一个字符的文本推进,否则会原地打转
                end = start + 1;
            }
            current().children.add(Node.text(src.substring(start, end)));
            index = end;
        }

        /** @return 是否消费掉了一段标记(返回 false 表示这个 '<' 只是普通字符) */
        private boolean consumeMarkup() {
            if (src.startsWith("<!--", index)) {
                return addRawSegment(src.indexOf("-->", index + 4), 3);
            }
            if (src.startsWith("<![CDATA[", index)) {
                return addRawSegment(src.indexOf("]]>", index + 9), 3);
            }
            if (src.startsWith("<?", index)) {
                return addRawSegment(src.indexOf("?>", index), 2);
            }
            if (src.startsWith("<!", index)) {
                return addRawSegment(src.indexOf('>', index), 1);
            }
            char after = index + 1 < src.length() ? src.charAt(index + 1) : '\0';
            if (after == '/' || Character.isLetter(after)) {
                consumeTag();
                return true;
            }
            return false;
        }

        /** found 为 -1 表示没有结束符,内容吃到输入末尾 */
        private boolean addRawSegment(int found, int terminatorLength) {
            int end = found < 0 ? src.length() : found + terminatorLength;
            current().children.add(Node.raw(src.substring(index, end)));
            index = end;
            return true;
        }

        private void consumeTag() {
            int end = tagEnd(index);
            String inner = src.substring(index + 1, end);
            index = end + 1;

            if (inner.startsWith("/")) {
                String typed = tagNameOf(inner.substring(1));
                if (typed.isEmpty()) {
                    current().children.add(Node.raw("<" + inner + ">"));
                    return;
                }
                int match = indexOfOpen(typed.toLowerCase(Locale.ROOT));
                if (match < 0) {
                    // 多余的闭标签:留成一行片段,不假装没看见
                    current().children.add(Node.raw("</" + typed + ">"));
                } else {
                    // 中间未闭合的元素留在树上,由渲染阶段补出闭标签
                    stack.subList(match + 1, stack.size()).clear();
                    Node closed = stack.remove(match);
                    closed.closeRaw = "</" + typed + ">";
                }
                return;
            }

            String typed = tagNameOf(inner);
            if (typed.isEmpty()) {
                current().children.add(Node.raw("<" + inner + ">"));
                return;
            }
            String name = typed.toLowerCase(Locale.ROOT);
            boolean selfContained = inner.endsWith("/") || VOID.contains(name);
            Node element = Node.element(name, "<" + inner + ">", selfContained);
            current().children.add(element);
            if (selfContained) {
                return;
            }
            stack.add(element);
            if (RAW_TEXT.contains(name)) {
                consumeRawText(element);
            }
        }

        /** script / style / textarea 内部不再解析标签,整段作为文本收下 */
        private void consumeRawText(Node element) {
            int close = lower.indexOf("</" + element.name, index);
            while (close >= 0) {
                int after = close + element.name.length() + 2;
                boolean realClose = after >= src.length()
                        || src.charAt(after) == '>' || Character.isWhitespace(src.charAt(after));
                if (realClose) {
                    break;
                }
                // 跳过 </scripter> 这类前缀相同但不同名的标签
                close = lower.indexOf("</" + element.name, after);
            }
            String body = close < 0 ? src.substring(index) : src.substring(index, close);
            if (!body.isEmpty()) {
                element.children.add(Node.text(body));
            }
            // 命中闭标签时把 index 停在 '>' 前,交给主循环按普通闭标签处理
            index = close < 0 ? src.length() : close;
        }

        private int indexOfOpen(String name) {
            for (int i = stack.size() - 1; i > 0; i--) {
                if (stack.get(i).name.equals(name)) {
                    return i;
                }
            }
            return -1;
        }

        /** 找标签的结束 '>' ,跳过属性值里的引号,避免 {@code <a title="x>y">} 被截断 */
        private int tagEnd(int start) {
            char quote = 0;
            for (int i = start + 1; i < src.length(); i++) {
                char c = src.charAt(i);
                if (quote != 0) {
                    if (c == quote) {
                        quote = 0;
                    }
                } else if (c == '"' || c == '\'') {
                    quote = c;
                } else if (c == '>') {
                    return i;
                }
            }
            // 标签没闭合就到输入结尾
            return src.length() - 1;
        }

        private static String tagNameOf(String inner) {
            int i = 0;
            while (i < inner.length()) {
                char c = inner.charAt(i);
                if (Character.isWhitespace(c) || c == '/' || c == '>') {
                    break;
                }
                i++;
            }
            return inner.substring(0, i);
        }
    }
}
