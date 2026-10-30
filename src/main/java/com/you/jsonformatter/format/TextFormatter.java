package com.you.jsonformatter.format;

/**
 * 把一段标记语言文本重新排版缩进。实现必须是无状态的,便于面板直接复用实例。
 * 语义:只做排版,不修语义;无法处理的输入抛 FormatterException 而不是静默改内容。
 */
public interface TextFormatter {

    String format(String source) throws FormatterException;
}
