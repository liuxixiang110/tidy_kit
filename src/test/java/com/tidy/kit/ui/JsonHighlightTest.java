package com.tidy.kit.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * JsonHighlight 包 span 的规则(设计:JSON 格式化树查找与高亮 3.3)。
 * 只验字符串产物;能不能画出黄底是 Swing 渲染器的事,已用离屏探针单独确认过。
 */
class JsonHighlightTest {

    private static final String BG = "#ffd666";
    private static final String FG = "#1e1e1e";

    private static String mark(String text, String query) {
        return JsonHighlight.mark(text, query, BG, FG);
    }

    private static String span(String inner) {
        return "<span style=\"background-color:" + BG + ";color:" + FG + "\">" + inner + "</span>";
    }

    @Test
    void wrapsMatchedSegment() {
        assertEquals("h" + span("ell") + "o", mark("hello", "ell"));
    }

    /** 重复出现的都要包上,不能只包第一处 */
    @Test
    void wrapsEveryOccurrence() {
        assertEquals("x " + span("by") + " z " + span("by") + " w", mark("x by z by w", "by"));
    }

    /** 从上一处之后继续找,所以不会包出嵌套 span */
    @Test
    void doesNotWrapOverlappingMatches() {
        assertEquals(span("aa") + "a", mark("aaa", "aa"));
    }

    @Test
    void wholeTextMatchedWrapsOnce() {
        assertEquals(span("key"), mark("key", "key"));
    }

    @Test
    void noMatchReturnsTextUnchanged() {
        assertEquals("hello", mark("hello", "zz"));
    }

    @Test
    void nullOrEmptyQueryReturnsInputUnchanged() {
        assertEquals("hello", mark("hello", null));
        assertEquals("hello", mark("hello", ""));
        assertNull(mark(null, "x"));
        assertEquals("", mark("", "x"));
    }

    /** 已转义的文本被包上 span 之后仍是合法 HTML:实体不会被二次转义 */
    @Test
    void keepsEscapedEntitiesIntact() {
        assertEquals(span("&lt;a&gt;"), mark("&lt;a&gt;", "&lt;a&gt;"));
        assertEquals("b" + span("&amp;") + "c", mark("b&amp;c", "&amp;"));
    }
}
