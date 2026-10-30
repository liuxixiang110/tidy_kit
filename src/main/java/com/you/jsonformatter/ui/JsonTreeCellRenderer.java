package com.you.jsonformatter.ui;

import com.you.jsonformatter.model.JsonNodeModel;
import com.you.jsonformatter.model.NodeType;

import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import javax.swing.JTree;

/**
 * 大纲风格单元格渲染器:纯缩进 + 折叠三角(三角由 BasicTreeUI 绘制,节点图标置空)。
 * key/字符串/数字/常量/结构符号分别取 IDE ColorKey 着色;容器折叠后行尾显示 {n keys} / [n]。
 */
class JsonTreeCellRenderer extends DefaultTreeCellRenderer {

    JsonTreeCellRenderer() {
        // 去掉默认文件夹/叶子图标,保留展开三角 -> 简洁大纲风格
        setLeafIcon(null);
        setClosedIcon(null);
        setOpenIcon(null);
        setFont(JsonUiColors.editorFont());
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                  boolean expanded, boolean leaf, int row,
                                                  boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        Font font = JsonUiColors.editorFont();
        setFont(font);
        if (value instanceof JsonTreeNode node) {
            setText(buildHtml(node.getModel(), expanded));
        } else {
            setText(value == null ? "" : value.toString());
        }
        return this;
    }

    private String buildHtml(JsonNodeModel m, boolean expanded) {
        StringBuilder sb = new StringBuilder("<html>");
        if (m.getKey() != null) {
            sb.append(color(JsonUiColors.key(), escape(m.getKey())));
            sb.append(color(JsonUiColors.punctuation(), ":"));
            sb.append(' ');
        }
        switch (m.getType()) {
            case OBJECT:
            case ARRAY:
                if (expanded) {
                    sb.append(color(JsonUiColors.punctuation(),
                            m.getType() == NodeType.OBJECT ? "{ }" : "[ ]"));
                } else {
                    sb.append(color(JsonUiColors.comment(), escape(m.collapsedSummary())));
                }
                break;
            case STRING:
                sb.append(color(JsonUiColors.string(), escape("\"" + m.getTextValue() + "\"")));
                if (m.getEscapedInner() != null) {
                    // 宽松模式成功解析内层转义 JSON,可再展开为子树(见 JsonTree)
                    sb.append(' ').append(color(JsonUiColors.comment(), "(escaped JSON)"));
                }
                break;
            case NUMBER:
                sb.append(color(JsonUiColors.number(), escape(m.getTextValue())));
                break;
            case BOOLEAN:
            case NULL:
                sb.append(color(JsonUiColors.constant(), escape(m.getTextValue())));
                break;
            default:
                break;
        }
        sb.append("</html>");
        return sb.toString();
    }

    private static String color(Color c, String text) {
        return "<font color='#" + String.format("%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue())
                + "'>" + text + "</font>";
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
