package com.you.jsonformatter.ui;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.you.jsonformatter.history.JsonHistoryStore;
import com.you.jsonformatter.model.JsonNodeModel;
import com.you.jsonformatter.parse.JsonParseException;
import com.you.jsonformatter.parse.JsonParseService;
import com.you.jsonformatter.parse.ParseMode;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * 工具窗口与编辑器 Dialog 复用的主面板(设计 5.3)。
 * 布局:输入区 + 模式下拉 + [格式化] -> 树区 -> [复制结果] + 状态栏。
 */
public class JsonTreePanel extends JPanel {

    /** 节点总数超过该值提示切纯文本(设计 5.3 性能) */
    private static final int NODE_COUNT_WARN = 100_000;

    private final JsonParseService parseService = new JsonParseService();

    private final JBTextArea inputArea = new JBTextArea();
    private final ComboBox<ParseMode> modeCombo = new ComboBox<>(ParseMode.values());
    private final JButton formatButton = new JButton("格式化");
    private final JButton expandAllButton = new JButton("全部展开");
    private final JButton collapseAllButton = new JButton("全部收起");
    private final JButton retryLenientButton = new JButton("宽松模式重试");
    private final JButton historyButton = new JButton("历史 ▾");
    private final JButton copyButton = new JButton("复制结果");
    private final JBLabel statusLabel = new JBLabel(" ");
    private final JsonTree tree = new JsonTree();

    private String lastPretty = "";

    public JsonTreePanel() {
        super(new BorderLayout());
        setBorder(JBUI.Borders.empty(6));
        buildUi();
        wireEvents();
    }

    private void buildUi() {
        // 顶部:输入区 + 控制行
        // 换行以保证单行长 JSON 能自由拖选(不换行时拖选会横向自动滚动并把光标甩到行尾)
        inputArea.setLineWrap(true);
        inputArea.setFont(JsonUiColors.editorFont());
        JBScrollPane inputScroll = new JBScrollPane(inputArea);
        inputScroll.setPreferredSize(new Dimension(10, 140));

        JPanel controlRow = new JPanel();
        controlRow.setLayout(new BoxLayout(controlRow, BoxLayout.X_AXIS));
        controlRow.add(new JBLabel("模式: "));
        modeCombo.setSelectedItem(ParseMode.STRICT);
        controlRow.add(modeCombo);
        controlRow.add(Box.createHorizontalStrut(8));
        controlRow.add(formatButton);
        controlRow.add(Box.createHorizontalStrut(8));
        controlRow.add(expandAllButton);
        controlRow.add(Box.createHorizontalStrut(4));
        controlRow.add(collapseAllButton);
        controlRow.add(Box.createHorizontalStrut(8));
        controlRow.add(retryLenientButton);
        retryLenientButton.setVisible(false);
        controlRow.add(Box.createHorizontalStrut(8));
        controlRow.add(historyButton);
        controlRow.add(Box.createHorizontalGlue());

        JPanel north = new JPanel(new BorderLayout());
        north.add(inputScroll, BorderLayout.CENTER);
        north.add(controlRow, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        // 中部:树
        add(new JBScrollPane(tree), BorderLayout.CENTER);

        // 底部:状态栏 + 复制
        JPanel south = new JPanel(new BorderLayout());
        JPanel rightBtns = new JPanel();
        rightBtns.add(copyButton);
        south.add(statusLabel, BorderLayout.CENTER);
        south.add(rightBtns, BorderLayout.EAST);
        south.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        add(south, BorderLayout.SOUTH);
    }

    private void wireEvents() {
        formatButton.addActionListener(e -> doFormat(selectedMode(), false));
        expandAllButton.addActionListener(e -> tree.expandAllNodes());
        collapseAllButton.addActionListener(e -> tree.collapseAllNodes());
        retryLenientButton.addActionListener(e -> {
            modeCombo.setSelectedItem(ParseMode.LENIENT);
            doFormat(ParseMode.LENIENT, false);
        });
        copyButton.addActionListener(e -> {
            if (!lastPretty.isEmpty()) {
                UiFeedback.copyToClipboard(lastPretty);
                setStatus("已复制格式化结果到剪贴板", false);
            }
        });
        historyButton.setToolTipText("选择曾经解析成功过的 JSON 片段,选中后直接重新格式化");
        HistoryPopup.attach(historyButton, text -> {
            setInputText(text);
            doFormat(selectedMode(), false);
        });
    }

    private ParseMode selectedMode() {
        Object sel = modeCombo.getSelectedItem();
        return sel instanceof ParseMode m ? m : ParseMode.STRICT;
    }

    /** 执行解析 + 渲染。lenientRetryHint 为 true 时严格失败不弹重试(内部使用) */
    private void doFormat(ParseMode mode, boolean unused) {
        String text = inputArea.getText();
        try {
            JsonNodeModel root = parseService.parse(text, mode);
            tree.showJson(root);
            lastPretty = root.toPrettyString();
            // 只把解析通过的文本入历史,与对比面板共用同一个池
            JsonHistoryStore.getInstance().record(text);
            retryLenientButton.setVisible(false);
            int nodes = JsonTreeNode.countNodes(root);
            String msg = "解析成功,共 " + nodes + " 个节点";
            if (nodes > NODE_COUNT_WARN) {
                msg += "(节点较多,建议切纯文本查看)";
            }
            setStatus(msg, false);
        } catch (JsonParseException ex) {
            lastPretty = "";
            setStatus("解析失败:" + ex.getMessage(), true);
            // 严格失败不自动降级,给"宽松模式重试"入口(设计 6.2)
            if (mode == ParseMode.STRICT) {
                retryLenientButton.setVisible(true);
            }
        }
    }

    private void setStatus(String message, boolean error) {
        UiFeedback.setStatus(statusLabel, message, error);
    }

    /** 外部(工具窗口/Dialog)预填输入内容,如自动填入剪贴板 */
    public void setInputText(String text) {
        inputArea.setText(text);
        inputArea.setCaretPosition(0);
    }

    /** 外部触发一次格式化(如编辑器 Action 预选文本后打开) */
    public void formatNow() {
        doFormat(selectedMode(), false);
    }

    public String getPrettyResult() {
        return lastPretty;
    }
}
