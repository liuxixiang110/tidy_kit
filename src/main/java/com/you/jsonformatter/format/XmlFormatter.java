package com.you.jsonformatter.format;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * XML 美化:走 JDK 自带 DOM + identity Transformer,命名空间、实体、CDATA 交给平台处理。
 *
 * 安全:开 FEATURE_SECURE_PROCESSING 并关掉外部 DTD/实体加载,格式化一段陌生 XML
 * 不会去联网,也不会被实体展开拖死。DOCTYPE 本身原样保留(pom.xml 这类输入带 DOCTYPE,
 * 直接禁 DOCTYPE 就格式化不了了)。
 *
 * 已知取舍:mixed content(段落里夹标签)的空白有语义,不做特判也不重排。
 */
public class XmlFormatter implements TextFormatter {

    /** 缩进宽度,与 JSON 面板的 toPrettyString 保持一致 */
    private static final String INDENT_AMOUNT = "2";

    /** JDK 解析器特性名,不支持时忽略即可,不影响主流程 */
    private static final String LOAD_EXTERNAL_DTD = "http://apache.org/xml/features/nonvalidating/load-external-dtd";
    private static final String EXTERNAL_GENERAL_ENTITIES = "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PARAMETER_ENTITIES = "http://xml.org/sax/features/external-parameter-entities";

    @Override
    public String format(String source) throws FormatterException {
        if (source == null || source.isBlank()) {
            throw new FormatterException("输入为空,没有可格式化的 XML");
        }
        Document document = parse(source);
        dropIndentationWhitespace(document);
        // Transformer 在 Windows 上用 \r\n,统一成 \n 与 HTML 格式化器保持一致
        String body = transform(document).replace("\r\n", "\n").strip();
        String prolog = extractProlog(source);
        return prolog.isEmpty() ? body : prolog + body;
    }

    private Document parse(String source) throws FormatterException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        // 特性不受支持时不能放弃解析,JDK 自带的 SECURE_PROCESSING 已足以挡住实体展开攻击
        trySetFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
        trySetFeature(factory, LOAD_EXTERNAL_DTD, false);
        trySetFeature(factory, EXTERNAL_GENERAL_ENTITIES, false);
        trySetFeature(factory, EXTERNAL_PARAMETER_ENTITIES, false);
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            // 默认 ErrorHandler 会把错误打到 stderr(污染 IDE 日志),这里改成收集首个异常并中断
            builder.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                    // 警告不影响排版结果,忽略
                }

                @Override
                public void error(SAXParseException e) throws SAXException {
                    throw e;
                }

                @Override
                public void fatalError(SAXParseException e) throws SAXException {
                    throw e;
                }
            });
            return builder.parse(new InputSource(new StringReader(source)));
        } catch (SAXParseException e) {
            throw new FormatterException("XML 格式错误:" + e.getMessage(), e.getLineNumber(), e.getColumnNumber());
        } catch (SAXException | IOException e) {
            throw new FormatterException("XML 格式错误:" + e.getMessage());
        } catch (ParserConfigurationException e) {
            throw new FormatterException("无法创建 XML 解析器:" + e.getMessage());
        }
    }

    private static void trySetFeature(DocumentBuilderFactory factory, String name, boolean value) {
        try {
            factory.setFeature(name, value);
        } catch (ParserConfigurationException | IllegalArgumentException ex) {
            // 当前解析器实现不支持该特性,忽略
        }
    }

    /**
     * 清掉"纯缩进"的空白文本节点。Transformer 只要看到元素之间已有空白就不再缩进,
     * 粘一份 4 空格缩进的 XML 会原样吐出,换不了缩进宽度。
     * 含实义文本的元素(mixed content)里的空白有语义,整份保留不动。
     */
    private static void dropIndentationWhitespace(Node parent) {
        NodeList children = parent.getChildNodes();
        boolean mixed = false;
        List<Node> removable = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            switch (child.getNodeType()) {
                case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                    String value = child.getNodeValue();
                    if (value != null && !value.isBlank()) {
                        mixed = true;
                    } else if (child.getNodeType() == Node.TEXT_NODE) {
                        removable.add(child);
                    }
                }
                case Node.ELEMENT_NODE -> dropIndentationWhitespace(child);
                default -> {
                    // 注释、PI 等一律保留
                }
            }
        }
        if (mixed) {
            return;
        }
        for (Node node : removable) {
            parent.removeChild(node);
        }
    }

    private String transform(Document document) throws FormatterException {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", INDENT_AMOUNT);
            // 声明与 DOCTYPE 统一由 extractProlog 按原文补回,这里一律不输出
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (TransformerException | IllegalArgumentException e) {
            throw new FormatterException("XML 输出失败:" + e.getMessage());
        }
    }

    /**
     * 把原文开头的 {@code <?xml ...?>} 与 {@code <!DOCTYPE ...>} 原样拼回第一行。
     * Transformer 会按自己的理解改写声明里的 encoding,又干脆不输出 DOCTYPE,
     * 而 pom.xml 这类文件的 DOCTYPE 不能丢。
     */
    private static String extractProlog(String source) {
        String text = source.stripLeading();
        StringBuilder prolog = new StringBuilder();
        while (true) {
            if (text.startsWith("<?xml")) {
                int end = text.indexOf("?>");
                if (end < 0) {
                    return prolog.toString();
                }
                prolog.append(text, 0, end + 2).append('\n');
                text = text.substring(end + 2).stripLeading();
            } else if (text.startsWith("<!DOCTYPE")) {
                int end = doctypeEnd(text);
                if (end < 0) {
                    return prolog.toString();
                }
                prolog.append(text, 0, end + 1).append('\n');
                text = text.substring(end + 1).stripLeading();
            } else {
                return prolog.toString();
            }
        }
    }

    /** DOCTYPE 可能带内部子集 {@code [ ... ]},里面的 '>' 不是结束 */
    private static int doctypeEnd(String text) {
        int close = text.indexOf('>');
        int open = text.indexOf('[');
        if (close < 0 || open < 0 || open > close) {
            return close;
        }
        int subset = text.indexOf(']', open);
        return subset < 0 ? close : text.indexOf('>', subset);
    }
}
