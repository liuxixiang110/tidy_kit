package com.tidy.kit.ui;

import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.tidy.kit.sql.SqlBeautifier;
import com.tidy.kit.sql.SqlException;
import com.tidy.kit.sql.SqlLogParser;
import com.tidy.kit.sql.SqlParameterBinder;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * SQL 参数格式化面板:上输入、下结果(只读)。
 * "参数格式化"把 GORM 日志(或裸 SQL + 参数行)里的 ? 逐个回填为参数;
 * "美化SQL"对结果做关键字换行缩进(只改空白,不改内容)。
 * 与 HTML/XML/URL 面板同样的取舍:按钮触发、不做历史、不做防抖自动重算。
 */
public class SqlParamPanel extends JPanel {

    /** 空输入时的引导文案,不当错误报 */
    private static final String HINT = "粘贴 SQL 日志(或 SQL + 参数行)后点\"参数格式化\"";

    private final JBTextArea inputArea = new JBTextArea();
    private final JBTextArea outputArea = new JBTextArea();
    private final JButton bindButton = new JButton("参数格式化");
    private final JButton beautifyButton = new JButton("美化SQL");
    private final JButton clearButton = new JButton("清空");
    private final JButton copyButton = new JButton("复制结果");
    private final JBLabel statusLabel = new JBLabel(" ");

    public SqlParamPanel() {
        super(new BorderLayout());
        setBorder(JBUI.Borders.empty(6));
        buildUi();
        wireEvents();
        setStatus(HINT, false);
    }

    private void buildUi() {
        // 两个文本区都必须换行:不换行时单行超长内容会破坏鼠标拖选(横向自动滚动把光标甩到行尾)
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        inputArea.setFont(UiColors.editorFont());
        outputArea.setFont(UiColors.editorFont());
        outputArea.setEditable(false);
        outputArea.setToolTipText("替换参数后的 SQL;可用\"美化SQL\"排版,或用右下角\"复制结果\"");

        JPanel controlRow = new JPanel();
        controlRow.setLayout(new BoxLayout(controlRow, BoxLayout.X_AXIS));
        controlRow.add(bindButton);
        controlRow.add(Box.createHorizontalStrut(8));
        controlRow.add(beautifyButton);
        controlRow.add(Box.createHorizontalStrut(8));
        controlRow.add(clearButton);
        controlRow.add(Box.createHorizontalGlue());

        JBSplitter splitter = new JBSplitter(true, 0.4f);
        splitter.setFirstComponent(labeled("输入", new JBScrollPane(inputArea)));
        splitter.setSecondComponent(labeled("结果", new JBScrollPane(outputArea)));

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

    private JPanel labeled(String title, JBScrollPane scrollPane) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void wireEvents() {
        bindButton.addActionListener(e -> doBind());
        beautifyButton.addActionListener(e -> doBeautify());
        clearButton.addActionListener(e -> {
            inputArea.setText("");
            outputArea.setText("");
            setStatus(HINT, false);
        });
        copyButton.addActionListener(e -> copyResult());
    }

    private void doBind() {
        String source = inputArea.getText();
        if (source.isBlank()) {
            outputArea.setText("");
            setStatus(HINT, false);
            return;
        }
        try {
            SqlLogParser.ParsedSql parsed = SqlLogParser.parse(source);
            String bound = SqlParameterBinder.bind(parsed.sql(), parsed.rawParams());
            outputArea.setText(bound);
            outputArea.setCaretPosition(0);
            setStatus("已替换 " + parsed.rawParams().size() + " 个参数", false);
        } catch (SqlException ex) {
            outputArea.setText("");
            setStatus("SQL 参数格式化失败:" + ex.getMessage(), true);
        }
    }

    /** 美化作用于结果区当前内容;想回到原样版本,重点一次"参数格式化"即可 */
    private void doBeautify() {
        String result = outputArea.getText();
        if (result.isEmpty()) {
            setStatus("先点\"参数格式化\"得到结果,再美化", true);
            return;
        }
        String pretty = SqlBeautifier.beautify(result);
        outputArea.setText(pretty);
        outputArea.setCaretPosition(0);
        setStatus("已美化,共 " + pretty.lines().count() + " 行", false);
    }

    private void copyResult() {
        String result = outputArea.getText();
        if (result.isEmpty()) {
            setStatus("还没有可复制的结果,先点\"参数格式化\"", true);
            return;
        }
        UiFeedback.copyToClipboard(result);
        setStatus("已复制结果到剪贴板", false);
    }

    private void setStatus(String message, boolean error) {
        UiFeedback.setStatus(statusLabel, message, error);
    }
}