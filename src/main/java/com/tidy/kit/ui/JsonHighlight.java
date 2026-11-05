package com.tidy.kit.ui;

/**
 * 把 HTML 文本里所有匹配片段包成带背景的 span(设计:JSON 格式化树查找与高亮 3.3)。
 * 纯字符串处理,便于单测;渲染相关的颜色由调用方以 #rrggbb 传入。
 */
final class JsonHighlight {

    private JsonHighlight() {
    }

    /**
     * 两个入参必须处于同一状态:都还没转义,或都已按同一规则做过 HTML 转义。
     * 渲染器走的是"先转义再包",所以这里不能碰转义,只负责包 span。
     *
     * @param escapedText  待渲染的文本片段
     * @param escapedQuery 查询串(与文本同状态);null 或空则原样返回
     * @return 含匹配片段时返回包好 span 的文本,否则原样返回
     */
    static String mark(String escapedText, String escapedQuery, String background, String foreground) {
        if (escapedText == null || escapedText.isEmpty()
                || escapedQuery == null || escapedQuery.isEmpty()) {
            return escapedText;
        }
        int at = escapedText.indexOf(escapedQuery);
        if (at < 0) {
            return escapedText;
        }
        StringBuilder sb = new StringBuilder(escapedText.length() + 64);
        int copied = 0;
        while (at >= 0) {
            sb.append(escapedText, copied, at);
            sb.append("<span style=\"background-color:").append(background)
                    .append(";color:").append(foreground).append("\">")
                    .append(escapedText, at, at + escapedQuery.length())
                    .append("</span>");
            copied = at + escapedQuery.length();
            // 从命中片段之后继续找,重复出现的都包上,也不会包出嵌套 span
            at = escapedText.indexOf(escapedQuery, copied);
        }
        sb.append(escapedText.substring(copied));
        return sb.toString();
    }
}
