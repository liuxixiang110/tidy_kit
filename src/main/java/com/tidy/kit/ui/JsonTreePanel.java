package com.tidy.kit.ui;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.tidy.kit.history.JsonHistoryStore;
import com.tidy.kit.model.JsonNodeModel;
import com.tidy.kit.parse.JsonParseException;
import com.tidy.kit.parse.JsonParseService;
import com.tidy.kit.parse.ParseMode;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultTreeModel;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * 工具窗口与编辑器 Dialog 复用的主面板(设计 5.3)。
 * 布局:输入区 -> 控制行(模式/展开收起/宽松重试/历史) -> 树区 -> 状态栏 + [复制结果]。
 * 输入停止 300ms 自动解析渲染,不设"格式化"按钮;输入为空时结果区直接留空。
 */
public class JsonTreePanel extends JPanel {

    /** 节点总数超过该值提示切纯文本(设计 5.3 性能) */
    private static final int NODE_COUNT_WARN = 100_000;
    /** 自动格式化的防抖时长,与对比面板的自动对比保持一致 */
    private static final int AUTO_FORMAT_DELAY_MS = 300;

    private final JsonParseService parseService = new JsonParseService();

    private final JBTextArea inputArea = new JBTextArea();
    private final ComboBox<ParseMode> modeCombo = new ComboBox<>(ParseMode.values());
    /**
     * 防抖定时器。一次性触发(见 Debounce)――javax.swing.Timer 默认 repeats=true,
     * 不关掉就会每 300ms 反复重解析、把树重新展开,用户收起的动作会被抹掉。
     */
    private final Timer formatTimer = Debounce.oneShot(AUTO_FORMAT_DELAY_MS, this::autoFormat);
    private final JButton expandAllButton = new JButton("全部展开");
    private final JButton collapseAllButton = new JButton("全部收起");
    private final JButton retryLenientButton = new JButton("宽松模式重试");
    private final JButton historyButton = new JButton("历史 ▾");
    private final JButton copyButton = new JButton("复制结果");
    private final JBLabel statusLabel = new JBLabel(" ");
    private final JsonTree tree = new JsonTree();

    private String lastPretty = "";
    /** 上一次真正用于解析的模式,用于识别下拉选项是否真的变了(与对比面板同一手法) */
    private ParseMode lastFormattedMode = ParseMode.STRICT;

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
        inputArea.setFont(UiColors.editorFont());
        JBScrollPane inputScroll = new JBScrollPane(inputArea);
        inputScroll.setPreferredSize(new Dimension(10, 140));

        JPanel controlRow = new JPanel();
        controlRow.setLayout(new BoxLayout(controlRow, BoxLayout.X_AXIS));
        controlRow.add(new JBLabel("模式: "));
        modeCombo.setSelectedItem(ParseMode.STRICT);
        controlRow.add(modeCombo);
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
        // 输入变化就重置定时器;setText 会连着触发删/插两次事件,timer.restart 天然合并成一次解析
        DocumentListener autoFormat = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                formatTimer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                formatTimer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // 仅属性变更,不影响文本
            }
        };
        inputArea.getDocument().addDocumentListener(autoFormat);

        // 切换模式立即重算;下拉弹出/关闭也会发 ActionEvent,只在模式真变了时调度
        modeCombo.addActionListener(e -> {
            if (selectedMode() != lastFormattedMode) {
                formatTimer.restart();
            }
        });
        expandAllButton.addActionListener(e -> tree.expandAllNodes());
        collapseAllButton.addActionListener(e -> tree.collapseAllNodes());
        // 选中宽松后上面的模式监听会调度重算,这里不重复触发
        retryLenientButton.addActionListener(e -> modeCombo.setSelectedItem(ParseMode.LENIENT));
        copyButton.addActionListener(e -> {
            if (!lastPretty.isEmpty()) {
                UiFeedback.copyToClipboard(lastPretty);
                setStatus("已复制格式化结果到剪贴板", false);
            }
        });
        historyButton.setToolTipText("选择曾经解析成功过的 JSON 片段,选中后回填并自动重算");
        HistoryPopup.attach(historyButton, this::setInputText);
    }

    /** 防抖到期后的入口:输入为空就清空结果区,否则按当前模式解析 */
    private void autoFormat() {
        if (inputArea.getText().isBlank()) {
            clearResult();
            return;
        }
        doFormat(selectedMode());
    }

    private ParseMode selectedMode() {
        Object sel = modeCombo.getSelectedItem();
        return sel instanceof ParseMode m ? m : ParseMode.STRICT;
    }

    /** 执行解析 + 渲染 */
    private void doFormat(ParseMode mode) {
        lastFormattedMode = mode;
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

    /** 输入为空(或新标签):结果树清空、不残留上一次的可复制内容,状态栏也不留字 */
    private void clearResult() {
        tree.setModel(new DefaultTreeModel(null));
        lastPretty = "";
        retryLenientButton.setVisible(false);
        lastFormattedMode = selectedMode();
        setStatus("", false);
    }

    private void setStatus(String message, boolean error) {
        UiFeedback.setStatus(statusLabel, message, error);
    }

    /** 外部(工具窗口/Dialog)预填输入内容,如自动填入剪贴板;填入后会自动格式 */
    public void setInputText(String text) {
        inputArea.setText(text);
        inputArea.setCaretPosition(0);
    }

    public String getPrettyResult() {
        return lastPretty;
    }
}
