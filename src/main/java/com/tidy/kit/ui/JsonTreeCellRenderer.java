package com.tidy.kit.ui;

import com.tidy.kit.model.JsonNodeModel;
import com.tidy.kit.model.NodeType;

import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import javax.swing.JTree;

/**
 * 大纲风格单元格渲染器:纯缩进 + 折叠三角(三角由 BasicTreeUI 绘制,节点图标置空)。
 * key/字符串/数字/常量/结构符号分别取 IDE ColorKey 着色;容器折叠后行尾显示 {n keys} / [n]。
 * 查找高亮就地判定:渲染器只持有查询串,拼 HTML 时顺手把命中的片段包上背景 span,
 * 因此不维护"命中集合"这种会随模型重建失效的缓存 —— 重新解析后高亮自动跟着新树走。
 */
class JsonTreeCellRenderer extends DefaultTreeCellRenderer {

    /** 当前查询串;null 表示不高亮 */
    private String query;

    JsonTreeCellRenderer() {
        // 去掉默认文件夹/叶子图标,保留展开三角 -> 简洁大纲风格
        setLeafIcon(null);
        setClosedIcon(null);
        setOpenIcon(null);
        setFont(UiColors.editorFont());
    }

    void setQuery(String query) {
        this.query = query == null || query.isEmpty() ? null : query;
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                  boolean expanded, boolean leaf, int row,
                                                  boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        Font font = UiColors.editorFont();
        setFont(font);
        if (value instanceof JsonTreeNode node) {
            setText(buildHtml(node.getModel(), expanded));
        } else {
            setText(value == null ? "" : value.toString());
        }
        return this;
    }

    private String buildHtml(JsonNodeModel m, boolean expanded) {
        // 文本走 escape,查询串也走同一次 escape:两边状态一致才能比得上,且不会把 span 转坏
        String escapedQuery = query == null ? null : escape(query);
        StringBuilder sb = new StringBuilder("<html>");
        if (m.getKey() != null) {
            sb.append(color(UiColors.key(), mark(escape(m.getKey()), escapedQuery)));
            sb.append(color(UiColors.punctuation(), ":"));
            sb.append(' ');
        }
        switch (m.getType()) {
            case OBJECT:
            case ARRAY:
                // 折叠摘要 {n keys} / [n] 是渲染出来的统计,不参与查找高亮
                if (expanded) {
                    sb.append(color(UiColors.punctuation(),
                            m.getType() == NodeType.OBJECT ? "{ }" : "[ ]"));
                } else {
                    sb.append(color(UiColors.comment(), escape(m.collapsedSummary())));
                }
                break;
            case STRING:
                sb.append(color(UiColors.string(),
                        mark(escape("\"" + m.getTextValue() + "\""), escapedQuery)));
                if (m.getEscapedInner() != null) {
                    // 宽松模式成功解析内层转义 JSON,可再展开为子树(见 JsonTree)
                    sb.append(' ').append(color(UiColors.comment(), "(escaped JSON)"));
                }
                break;
            case NUMBER:
                sb.append(color(UiColors.number(), mark(escape(m.getTextValue()), escapedQuery)));
                break;
            case BOOLEAN:
            case NULL:
                sb.append(color(UiColors.constant(), mark(escape(m.getTextValue()), escapedQuery)));
                break;
            default:
                break;
        }
        sb.append("</html>");
        return sb.toString();
    }

    /** 命中片段包上背景 span;没有查询串时原样返回 */
    private static String mark(String escapedText, String escapedQuery) {
        return JsonHighlight.mark(escapedText, escapedQuery,
                UiColors.hex(UiColors.searchHitBackground()),
                UiColors.hex(UiColors.searchHitForeground()));
    }

    private static String color(Color c, String text) {
        return "<font color='" + UiColors.hex(c) + "'>" + text + "</font>";
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
