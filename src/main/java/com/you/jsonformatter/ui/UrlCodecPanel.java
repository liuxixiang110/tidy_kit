package com.you.jsonformatter.ui;

import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.you.jsonformatter.codec.UrlCodec;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * URL 编码/解码面板:上输入、下结果(只读),两个按钮分别做百分号编码与解码。
 * 编解码规则见 {@link UrlCodec}(UTF-8、空格转 %20、解码不动加号)。
 * 与格式化面板同样的取舍:不做历史、不做树,结果可一键复制。
 */
public class UrlCodecPanel extends JPanel {

    /** 空输入时的引导文案,不当错误报 */
    private static final String HINT =
            "粘贴文本后点\"编码\"或\"解码\";编码按 UTF-8 把中文等非 ASCII 转成 %XX,空格转 %20";

    private final JBTextArea inputArea = new JBTextArea();
    private final JBTextArea outputArea = new JBTextArea();
    private final JButton encodeButton = new JButton("编码");
    private final JButton decodeButton = new JButton("解码");
    private final JButton clearButton = new JButton("清空");
    private final JButton copyButton = new JButton("复制结果");
    private final JBLabel statusLabel = new JBLabel(" ");

    public UrlCodecPanel() {
        super(new BorderLayout());
        setBorder(JBUI.Borders.empty(2));
        buildUi();
        wireEvents();
        setStatus(HINT, false);
    }

    /** 只装一段待处理文本,不预填示例、不解析 */
    public void openWithText(String text) {
        inputArea.setText(text);
        inputArea.setCaretPosition(0);
    }

    private void buildUi() {
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        outputArea.setEditable(false);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        outputArea.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JBSplitter splitter = new JBSplitter(false, 0.5f);
        splitter.setFirstComponent(labeled("输入", new JBScrollPane(inputArea)));
        splitter.setSecondComponent(labeled("结果", new JBScrollPane(outputArea)));
        add(splitter, BorderLayout.CENTER);

        JPanel controlBar = new JPanel();
        controlBar.setLayout(new BoxLayout(controlBar, BoxLayout.X_AXIS));
        controlBar.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
        controlBar.add(encodeButton);
        controlBar.add(decodeButton);
        controlBar.add(Box.createHorizontalStrut(12));
        controlBar.add(clearButton);
        controlBar.add(copyButton);
        controlBar.add(Box.createHorizontalStrut(12));
        statusLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, statusLabel.getPreferredSize().height));
        controlBar.add(statusLabel);
        add(controlBar, BorderLayout.SOUTH);
    }

    private JPanel labeled(String title, JBScrollPane scrollPane) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void wireEvents() {
        encodeButton.addActionListener(e -> run(true));
        decodeButton.addActionListener(e -> run(false));
        clearButton.addActionListener(e -> clear());
        copyButton.addActionListener(e -> copyResult());
    }

    private void run(boolean encode) {
        String source = inputArea.getText();
        if (source.isEmpty()) {
            outputArea.setText("");
            setStatus(HINT, false);
            return;
        }
        String result = encode ? UrlCodec.encode(source) : UrlCodec.decode(source);
        outputArea.setText(result);
        outputArea.setCaretPosition(0);
        // 结果和输入一样通常意味着里面根本没有可转义/可解码的内容,值得点出来
        String note = result.equals(source) ? "(与输入相同,没找到需要转换的内容)" : "";
        setStatus((encode ? "已编码" : "已解码") + ",输入 " + source.length()
                + " 字符 → 输出 " + result.length() + " 字符" + note, false);
    }

    private void clear() {
        inputArea.setText("");
        outputArea.setText("");
        setStatus(HINT, false);
    }

    private void copyResult() {
        String text = outputArea.getText();
        if (text.isEmpty()) {
            setStatus("结果为空,没有可复制的内容", true);
            return;
        }
        UiFeedback.copyToClipboard(text);
        setStatus("结果已复制到剪贴板", false);
    }

    private void setStatus(String message, boolean error) {
        UiFeedback.setStatus(statusLabel, message, error);
    }
}
