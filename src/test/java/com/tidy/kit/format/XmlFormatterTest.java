package com.tidy.kit.format;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XML 格式化器单测(脱离 IDE):缩进、声明保留、已有缩进重排、命名空间、错误定位。
 */
class XmlFormatterTest {

    private final XmlFormatter formatter = new XmlFormatter();

    private List<String> lines(String source) throws FormatterException {
        return List.of(formatter.format(source).split("\n"));
    }

    @Test
    void indentsNestedElements() throws FormatterException {
        assertLinesMatch(List.of("<a>", "  <b>", "    <c/>", "  </b>", "</a>"),
                lines("<a><b><c/></b></a>"));
    }

    @Test
    void keepsTextOnlyElementOnOneLine() throws FormatterException {
        assertLinesMatch(List.of("<root>", "  <name>json-tree-formatter</name>", "</root>"),
                lines("<root><name>json-tree-formatter</name></root>"));
    }

    @Test
    void keepsOriginalDeclarationVerbatimOnFirstLine() throws FormatterException {
        // 不能让 Transformer 改写声明里的 encoding
        List<String> result = lines("<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\"?><a><b/></a>");
        assertEquals("<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\"?>", result.get(0));
        assertEquals("<a>", result.get(1));
    }

    @Test
    void doesNotInventDeclarationWhenAbsent() throws FormatterException {
        assertFalse(formatter.format("<a><b/></a>").startsWith("<?xml"));
    }

    @Test
    void reindentsAlreadyIndentedInput() throws FormatterException {
        // 粘一份 4 空格缩进的 XML 也要按 2 空格重排:必须先清掉纯缩进的空白文本节点
        String source = "<a>\n    <b>\n        <c x=\"1\"/>\n    </b>\n</a>\n";
        assertLinesMatch(List.of("<a>", "  <b>", "    <c x=\"1\"/>", "  </b>", "</a>"), lines(source));
    }

    @Test
    void keepsAttributesAndNamespacePrefixes() throws FormatterException {
        String result = formatter.format("<ns:a xmlns:ns=\"urn:x\"><ns:b v=\"1\" w=\"2\"/></ns:a>");
        assertTrue(result.contains("ns:a"), result);
        assertTrue(result.contains("xmlns:ns=\"urn:x\""), result);
        assertTrue(result.contains("v=\"1\""), result);
        assertTrue(result.contains("w=\"2\""), result);
    }

    @Test
    void keepsCommentsAndProcessingInstruction() throws FormatterException {
        String result = formatter.format("<a><!-- 说明 --><?pi data?><b/></a>");
        assertTrue(result.contains("<!--"), result);
        assertTrue(result.contains("<?pi"), result);
    }

    @Test
    void keepsDoctypeWithoutFetchingIt() throws FormatterException {
        // 外部 DTD 不加载:这个系统标识指向一个不存在的地址,联网就会卡住或报错
        String source = "<!DOCTYPE pom SYSTEM \"http://127.0.0.1:1/none.dtd\"><project><x>1</x></project>";
        List<String> result = lines(source);
        assertEquals("<!DOCTYPE pom SYSTEM \"http://127.0.0.1:1/none.dtd\">", result.get(0));
        assertEquals("<project>", result.get(1));
    }

    @Test
    void cdataContentSurvivesRoundTrip() throws FormatterException {
        String result = formatter.format("<a><![CDATA[x < y]]></a>");
        // JDK 可能把 CDATA 退化成转义文本,内容不能丢
        assertTrue(result.contains("x < y") || result.contains("x &lt; y"), result);
    }

    @Test
    void reportsErrorPositionForMalformedXml() {
        FormatterException ex = assertThrows(FormatterException.class, () -> formatter.format("<a><b></a>"));
        assertTrue(ex.getLine() > 0, "应带上出错行号: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("行 "), ex.getMessage());
    }

    @Test
    void rejectsBlankInput() {
        assertThrows(FormatterException.class, () -> formatter.format(""));
        assertThrows(FormatterException.class, () -> formatter.format("   \n"));
        assertThrows(FormatterException.class, () -> formatter.format(null));
    }

    @Test
    void formatsHtmlShapedXmlTagMismatchFails() {
        // 与 HTML 面板不同:XML 严格,未闭合标签必须报错而不是自动补齐
        assertThrows(FormatterException.class, () -> formatter.format("<div><p>text"));
    }
}
