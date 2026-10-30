package com.tidy.kit.ui;

import com.tidy.kit.compare.DiffNode;
import com.tidy.kit.compare.DiffStatus;
import com.tidy.kit.model.NodeType;
import com.intellij.ui.JBColor;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Color;
import java.awt.Component;

/**
 * 差异树渲染器:每行 `key: 左值 → 右值`,按状态着色并加 +/-/~/! 前缀标记。
 * 大纲风格(节点图标置空,保留折叠三角),复用编辑器字体。
 */
class JsonDiffCellRenderer extends DefaultTreeCellRenderer {

    private static final Color GREEN = new JBColor(new Color(0x2E7D32), new Color(0x6A9955));
    private static final Color RED = new JBColor(new Color(0xCC3333), new Color(0xF05050));
    private static final Color ORANGE = new JBColor(new Color(0xB8860B), new Color(0xCE9A2E));
    private static final Color GRAY = JBColor.GRAY;

    JsonDiffCellRenderer() {
        setLeafIcon(null);
        setClosedIcon(null);
        setOpenIcon(null);
        setFont(UiColors.editorFont());
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                  boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        setFont(UiColors.editorFont());
        if (value instanceof DiffTreeNode node) {
            setText(buildHtml(node.getDiff()));
        } else {
            setText(value == null ? "" : value.toString());
        }
        return this;
    }

    private String buildHtml(DiffNode d) {
        StringBuilder sb = new StringBuilder("<html>");
        // 前缀标记
        sb.append(color(statusColor(d.getStatus()), escape(marker(d.getStatus())) + " "));
        // key 或下标
        if (d.getKey() != null) {
            sb.append(color(UiColors.key(), escape(d.getKey()))).append(": ");
        } else if (d.getIndex() != null) {
            sb.append(color(UiColors.punctuation(), "[" + d.getIndex() + "]")).append(" ");
        }

        if (d.isContainer()) {
            String summary = (d.getLeftType() == NodeType.ARRAY || d.getRightType() == NodeType.ARRAY)
                    ? "[" + d.getChildren().size() + "]"
                    : "{" + d.getChildren().size() + "}";
            sb.append(color(statusColor(d.getStatus()), escape(summary)));
            sb.append("</html>");
            return sb.toString();
        }

        switch (d.getStatus()) {
            case ADDED:
                sb.append(color(GREEN, escape(value(d.getRightType(), d.getRightText()))));
                break;
            case REMOVED:
                sb.append(color(RED, escape(value(d.getLeftType(), d.getLeftText()))));
                break;
            case TYPE_DIFF:
                sb.append(color(ORANGE, escape(value(d.getLeftType(), d.getLeftText()))))
                        .append(color(ORANGE, " → "))
                        .append(color(ORANGE, escape(value(d.getRightType(), d.getRightText()))));
                break;
            case CHANGED:
                sb.append(color(ORANGE, escape(value(d.getLeftType(), d.getLeftText()))))
                        .append(color(GRAY, " → "))
                        .append(color(ORANGE, escape(value(d.getRightType(), d.getRightText()))));
                break;
            case EQUAL:
            default:
                sb.append(color(GRAY, escape(value(d.getLeftType(), d.getLeftText()))));
                break;
        }
        sb.append("</html>");
        return sb.toString();
    }

    private static String value(NodeType type, String text) {
        if (text == null) {
            return type == null ? "" : type.name().toLowerCase();
        }
        return type == NodeType.STRING ? "\"" + text + "\"" : text;
    }

    private static String marker(DiffStatus s) {
        switch (s) {
            case ADDED:
                return "+";
            case REMOVED:
                return "-";
            case CHANGED:
                return "~";
            case TYPE_DIFF:
                return "!";
            case EQUAL:
            default:
                return "=";
        }
    }

    private static Color statusColor(DiffStatus s) {
        switch (s) {
            case ADDED:
                return GREEN;
            case REMOVED:
                return RED;
            case CHANGED:
            case TYPE_DIFF:
                return ORANGE;
            case EQUAL:
            default:
                return GRAY;
        }
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
