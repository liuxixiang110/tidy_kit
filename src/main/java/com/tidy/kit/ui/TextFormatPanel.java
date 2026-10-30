package com.tidy.kit.ui;

import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.tidy.kit.format.FormatterException;
import com.tidy.kit.format.TextFormatter;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * HTML / XML 共用的格式化面板:上输入、下结果(只读),点"格式化"重排缩进,可一键复制。
 *
 * 与 JSON 面板的差别是有意为之:不做树渲染、不做历史、也没有严格/宽松模式
 * (HTML 一律宽松容错,XML 一律严格报错,都由各自的 Formatter 决定)。
 */
public class TextFormatPanel extends JPanel {

    /** 展示用名称,同时用于提示文案 */
    private final String kind;
    private final TextFormatter formatter;

    private final JBTextArea inputArea = new JBTextArea();
    private final JBTextArea outputArea = new JBTextArea();
    private final JButton formatButton = new JButton("格式化");
    private final JButton clearButton = new JButton("清空");
    private final JButton copyButton = new JButton("复制结果");
    private final JBLabel statusLabel = new JBLabel(" ");

    public TextFormatPanel(String kind, TextFormatter formatter) {
        super(new BorderLayout());
        this.kind = kind;
        this.formatter = formatter;
        setBorder(JBUI.Borders.empty(6));
        buildUi();
        wireEvents();
        setStatus(hint(), false);
    }

    private String hint() {
        return "粘贴 " + kind + " 后点\"格式化\"";
    }

    private void buildUi() {
        // 两个文本区都必须换行:不换行时单行超长内容会破坏鼠标拖选(横向自动滚动把光标甩到行尾)
        inputArea.setLineWrap(true);
        outputArea.setLineWrap(true);
        inputArea.setFont(JsonUiColors.editorFont());
        outputArea.setFont(JsonUiColors.editorFont());
        outputArea.setEditable(false);
        outputArea.setToolTipText("格式化结果;可全选后自行复制,或用右下角\"复制结果\"");

        JPanel controlRow = new JPanel();
        controlRow.setLayout(new BoxLayout(controlRow, BoxLayout.X_AXIS));
        controlRow.add(formatButton);
        controlRow.add(Box.createHorizontalStrut(8));
        controlRow.add(clearButton);
        controlRow.add(Box.createHorizontalGlue());

        JBSplitter splitter = new JBSplitter(true, 0.4f);
        splitter.setFirstComponent(new JBScrollPane(inputArea));
        splitter.setSecondComponent(new JBScrollPane(outputArea));

        JPanel center = new JPanel(new BorderLayout());
        center.add(controlRow, BorderLayout.NORTH);
        center.add(splitter, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        JPanel rightButtons = new JPanel();
        rightButtons.add(copyButton);
        south.add(statusLabel, BorderLayout.CENTER);
        south.add(rightButtons, BorderLayout.EAST);
        south.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        add(south, BorderLayout.SOUTH);
    }

    private void wireEvents() {
        formatButton.addActionListener(e -> doFormat());
        clearButton.addActionListener(e -> {
            inputArea.setText("");
            outputArea.setText("");
            setStatus(hint(), false);
        });
        copyButton.addActionListener(e -> copyResult());
    }

    private void doFormat() {
        String source = inputArea.getText();
        if (source.isBlank()) {
            outputArea.setText("");
            setStatus(hint(), false);
            return;
        }
        try {
            String pretty = formatter.format(source);
            outputArea.setText(pretty);
            outputArea.setCaretPosition(0);
            setStatus("已格式化,共 " + pretty.lines().count() + " 行", false);
        } catch (FormatterException ex) {
            outputArea.setText("");
            setStatus(kind + " 格式化失败:" + ex.getMessage(), true);
        }
    }

    private void copyResult() {
        String pretty = outputArea.getText();
        if (pretty.isEmpty()) {
            setStatus("还没有可复制的结果,先点\"格式化\"", true);
            return;
        }
        UiFeedback.copyToClipboard(pretty);
        setStatus("已复制格式化结果到剪贴板", false);
    }

    private void setStatus(String message, boolean error) {
        UiFeedback.setStatus(statusLabel, message, error);
    }
}
