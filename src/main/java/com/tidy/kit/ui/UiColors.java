package com.tidy.kit.ui;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.ui.JBColor;

import java.awt.Color;
import java.awt.Font;

/**
 * 统一从 IDE 当前色彩/字体方案取值,自动适配 Dark/Light(设计 5.3)。
 * 取不到对应 ColorKey 时回退到 JBColor,保证不抛异常。
 */
final class UiColors {

    private UiColors() {
    }

    /** key:用局部变量(标识符)色 */
    static Color key() {
        return foreground(DefaultLanguageHighlighterColors.LOCAL_VARIABLE, JBColor.GRAY);
    }

    /** 字符串:字面量色 */
    static Color string() {
        return foreground(DefaultLanguageHighlighterColors.STRING, new JBColor(new java.awt.Color(0x6A8759), new java.awt.Color(0x6A8759)));
    }

    /** 数字:类型色 */
    static Color number() {
        return foreground(DefaultLanguageHighlighterColors.NUMBER, new JBColor(new java.awt.Color(0x6897BB), new java.awt.Color(0x6897BB)));
    }

    /** 布尔/null:常量色 */
    static Color constant() {
        return foreground(DefaultLanguageHighlighterColors.CONSTANT, new java.awt.Color(0xCC7832));
    }

    /** 结构符号(花括号/方括号/冒号) */
    static Color punctuation() {
        return foreground(DefaultLanguageHighlighterColors.BRACKETS, JBColor.GRAY);
    }

    /** 折叠计数等辅助文字:注释色 */
    static Color comment() {
        return foreground(DefaultLanguageHighlighterColors.LINE_COMMENT, JBColor.GRAY);
    }

    /**
     * 查找命中的背景色。不随 Dark/Light 分支:浅黄底配近黑字,压在深色行背景上
     * 比用 JBColor 自适应更稳,也少一处主题判断。
     */
    static Color searchHitBackground() {
        return new Color(0xFFD666);
    }

    /** 查找命中的前景色:压在黄底上的近黑 */
    static Color searchHitForeground() {
        return new Color(0x1E1E1E);
    }

    /** HTML 片段里只能写 #rrggbb,统一在这里转,免得多处各自 format */
    static String hex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    /** 用于渲染中文等 CJK 字符的探测码点(“中”) */
    private static final int CJK_PROBE = 0x4E2D;

    /**
     * 编辑器等宽字体;若当前编辑器字体无法显示中文(如 JetBrains Mono 缺 CJK 字形),
     * 回退到能显示 CJK 的字体,避免输入框出现“豆腐块”。
     */
    static Font editorFont() {
        com.intellij.openapi.editor.colors.EditorColorsScheme scheme =
                EditorColorsManager.getInstance().getGlobalScheme();
        int size = scheme.getEditorFontSize();
        String name = scheme.getEditorFontName();
        Font base = new Font(name, Font.PLAIN, size);
        if (base.canDisplay(CJK_PROBE)) {
            return base;
        }
        // 依次尝试能聚合 CJK 回退的逻辑字体
        for (String candidate : new String[]{Font.MONOSPACED, "SansSerif", "Dialog"}) {
            Font f = new Font(candidate, Font.PLAIN, size);
            if (f.canDisplay(CJK_PROBE)) {
                return f;
            }
        }
        return base;
    }

    private static Color foreground(TextAttributesKey key, Color fallback) {
        var attrs = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(key);
        if (attrs == null) {
            return fallback;
        }
        Color fg = attrs.getForegroundColor();
        return fg != null ? fg : fallback;
    }
}
