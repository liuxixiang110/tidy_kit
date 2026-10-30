package com.tidy.kit.format;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HTML 格式化器单测(脱离 IDE):缩进、行内合并、原样保留、宽松容错。
 */
class HtmlFormatterTest {

    private final HtmlFormatter formatter = new HtmlFormatter();

    private List<String> lines(String source) throws FormatterException {
        return List.of(formatter.format(source).split("\n"));
    }

    @Test
    void indentsNestedBlocks() throws FormatterException {
        assertLinesMatch(List.of(
                        "<div>",
                        "  <section>",
                        "    <p>hi</p>",
                        "  </section>",
                        "</div>"),
                lines("<div><section><p>hi</p></section></div>"));
    }

    @Test
    void keepsInlineRunOnOneLine() throws FormatterException {
        assertEquals("<p>Hello <b>World</b>!</p>", formatter.format("<p>Hello <b>World</b>!</p>"));
    }

    @Test
    void voidElementStaysInsideLine() throws FormatterException {
        assertEquals("<p>a<br>b</p>", formatter.format("<p>a<br>b</p>"));
    }

    @Test
    void preservesAttributeOrderCaseAndQuotes() throws FormatterException {
        String result = formatter.format("<DIV Class=\"a\" data-x='1'>y</DIV>");
        assertTrue(result.startsWith("<DIV Class=\"a\" data-x='1'>"), result);
        assertTrue(result.endsWith("</DIV>"), result);
    }

    @Test
    void angleBracketInAttributeValueDoesNotBreakTag() throws FormatterException {
        assertEquals("<p title=\"a>b\">x</p>", formatter.format("<p title=\"a>b\">x</p>"));
    }

    @Test
    void collapsesWhitespaceButKeepsInlineSeparators() throws FormatterException {
        // 折叠缩进与换行,但 "Hello " 后面的空格必须留着,否则会和 <b> 粘连
        String result = formatter.format("<div>\n    <p>  Hello   <b>World</b>  </p>\n</div>");
        assertLinesMatch(List.of("<div>", "  <p>Hello <b>World</b></p>", "</div>"), List.of(result.split("\n")));
    }

    @Test
    void scriptContentIsNeverRetokenized() throws FormatterException {
        String result = formatter.format("<div><script>var a = 1;\nif (a<2) { x(); }</script></div>");
        assertLinesMatch(List.of(
                        "<div>",
                        "  <script>",
                        "var a = 1;",
                        "if (a<2) { x(); }",
                        "  </script>",
                        "</div>"),
                List.of(result.split("\n")));
    }

    @Test
    void styleBodyKeepsItsOwnFormatting() throws FormatterException {
        // style 内部原样输出,不额外加缩进
        String result = formatter.format("<head><style>.a {\n  color: red;\n}</style></head>");
        assertLinesMatch(List.of(
                        "<head>",
                        "  <style>",
                        ".a {",
                        "  color: red;",
                        "}",
                        "  </style>",
                        "</head>"),
                List.of(result.split("\n")));
    }

    @Test
    void commentGetsItsOwnLine() throws FormatterException {
        assertLinesMatch(List.of(
                        "<div>",
                        "  <!-- c -->",
                        "  <p>x</p>",
                        "</div>"),
                lines("<div><!-- c --><p>x</p></div>"));
    }

    @Test
    void doctypeStaysOnFirstLine() throws FormatterException {
        List<String> result = lines("<!DOCTYPE html><html><body>x</body></html>");
        assertEquals("<!DOCTYPE html>", result.get(0));
        assertEquals("<html>", result.get(1));
        assertEquals("  <body>x</body>", result.get(2));
    }

    @Test
    void closesUnclosedTagsInsteadOfEmittingBrokenOutput() throws FormatterException {
        assertLinesMatch(List.of("<div>", "  <p>text</p>", "</div>"), lines("<div><p>text"));
    }

    @Test
    void unmatchedCloseTagIsKeptNotSilentlyDropped() throws FormatterException {
        List<String> result = lines("<div>x</div></span>");
        assertEquals("<div>x</div>", result.get(0));
        assertEquals("</span>", result.get(1));
    }

    @Test
    void interveningUnclosedElementIsClosedBeforeMatchedParent() throws FormatterException {
        // <b> 没闭合就遇到 </div>:<b> 应当被补在 <div> 内部,而不是吃掉 </div>
        assertLinesMatch(List.of("<div>", "  <p>a<b>c</b></p>", "</div>"),
                lines("<div><p>a<b>c</div>"));
    }

    @Test
    void plainTextWithoutTagsPassesThrough() throws FormatterException {
        assertEquals("hello world", formatter.format("hello   world"));
    }

    @Test
    void rejectsBlankInput() {
        assertThrows(FormatterException.class, () -> formatter.format(""));
        assertThrows(FormatterException.class, () -> formatter.format("  \n\t "));
        assertThrows(FormatterException.class, () -> formatter.format(null));
    }
}
