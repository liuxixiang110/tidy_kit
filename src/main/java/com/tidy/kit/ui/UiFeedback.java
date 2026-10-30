package com.tidy.kit.ui;

import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;

import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/**
 * 面板共用的反馈动作:状态栏着色文案 + 复制到系统剪贴板。
 * 每个面板各自手写一遍 HTML 转义和红灰配色太散,统一收在这里。
 */
final class UiFeedback {

    /** 告警色,浅/深主题各一档 */
    private static final Color ERROR = new JBColor(new Color(0xCC3333), new Color(0xF05050));

    private UiFeedback() {
    }

    /** white-space:pre 让消息里的制表符和连续空格照原样显示 */
    static void setStatus(JBLabel label, String message, boolean error) {
        label.setText("<html><body style='white-space:pre; padding-left:4px;'>" + escape(message) + "</body></html>");
        label.setForeground(error ? ERROR : JBColor.gray);
    }

    static void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
