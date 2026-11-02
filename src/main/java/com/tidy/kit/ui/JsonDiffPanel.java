package com.tidy.kit.ui;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.tidy.kit.compare.DiffNode;
import com.tidy.kit.compare.DiffResult;
import com.tidy.kit.compare.JsonDiffService;
import com.tidy.kit.history.DiffHistoryEntry;
import com.tidy.kit.history.JsonHistoryStore;
import com.tidy.kit.model.JsonNodeModel;
import com.tidy.kit.parse.JsonParseException;
import com.tidy.kit.parse.JsonParseService;
import com.tidy.kit.parse.ParseMode;

import javax.swing.BoundedRangeModel;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JScrollBar;
import javax.swing.JToggleButton;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.border.Border;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridLayout;

/**
 * JSON 对比面板:左右两个输入区 + 共用模式,输入变动后自动防抖对比 -> 单棵合并差异树 + 状态摘要。
 * 严格模式解析失败时给"宽松模式重试"。
 */
public class JsonDiffPanel extends JPanel {

    /** 输入变更后的防抖延时,避免逐字符解析 */
    private static final int COMPARE_DELAY_MS = 300;

    /** 差异树节点总数超过该值时提示(与 JsonTreePanel 的阈值保持一致) */
    private static final int DIFF_NODE_WARN = 100_000;
    /** 过滤开关的固定文案与选中态强调色(浅/深主题各一档) */
    private static final String DIFF_ONLY_LABEL = "仅展示差异";
    private static final Color ACCENT = new JBColor(new Color(0x1A6CC1), new Color(0x5BA3EF));

    private final JsonParseService parseService = new JsonParseService();
    private final JsonDiffService diffService = new JsonDiffService();

    private final JBTextArea inputA = new JBTextArea();
    private final JBTextArea inputB = new JBTextArea();
    private final JBScrollPane scrollPaneA = new JBScrollPane(inputA);
    private final JBScrollPane scrollPaneB = new JBScrollPane(inputB);
    private final ComboBox<ParseMode> modeCombo = new ComboBox<>(ParseMode.values());
    private final JToggleButton diffOnlyButton = new JToggleButton(DIFF_ONLY_LABEL);
    private final JButton retryLenientButton = new JButton("宽松模式重试");
    private final JButton pairHistoryButton = new JButton("对比历史 ▾");
    private final JButton expandAllButton = new JButton("全部展开");
    private final JButton collapseAllButton = new JButton("全部收起");
    private final JBLabel statusLabel = new JBLabel(" ");
    private final JsonDiffTree diffTree = new JsonDiffTree();
    /** 最近一次对比结果,供切换"仅展示差异"时重渲染而无需重新解析 */
    private DiffNode lastDiffRoot;
    /** 防抖定时器:输入或模式变更一段时间后自动对比;必须是一次性触发,否则会每 300ms 空转 */
    private final Timer compareTimer = Debounce.oneShot(COMPARE_DELAY_MS, this::autoCompare);
    /** 过滤按钮的默认外观,取消选中时还原 */
    private Color diffOnlyDefaultForeground;
    private Font diffOnlyDefaultFont;
    private Border diffOnlyDefaultBorder;
    /** 上一次真正用于对比的模式,用于识别下拉选项是否真的变了 */
    private ParseMode lastComparedMode = ParseMode.STRICT;
    /** 滚动同步防回环标志:设置对侧滚动条会再触发一次 AdjustmentEvent */
    private boolean scrollSyncing;

    public JsonDiffPanel() {
        super(new BorderLayout());
        setBorder(JBUI.Borders.empty(6));
        buildUi();
        wireEvents();
        installScrollSync();
        // 新建标签:不预置任何对比结果,状态栏说清它在等什么(否则两侧没填时看着像整个面板是坏的)
        clearResult("输入 A 和输入 B 都有内容后会自动对比", false);
    }

    private void buildUi() {
        inputA.setFont(UiColors.editorFont());
        inputB.setFont(UiColors.editorFont());
        // 必须换行:单行长 JSON 在不换行的 JTextArea 里拖选会触发横向自动滚动,
        // 光标被甩到行尾,导致选中区间膨胀成"光标到末尾全选"
        inputA.setLineWrap(true);
        inputB.setLineWrap(true);

        JPanel inputs = new JPanel(new GridLayout(1, 2, 6, 0));
        inputs.add(labeled("输入 A", scrollPaneA));
        inputs.add(labeled("输入 B", scrollPaneB));
        inputs.setPreferredSize(new Dimension(10, 160));

        JPanel controlRow = new JPanel();
        controlRow.setLayout(new BoxLayout(controlRow, BoxLayout.X_AXIS));
        controlRow.add(new JBLabel("模式: "));
        modeCombo.setSelectedItem(ParseMode.STRICT);
        controlRow.add(modeCombo);
        controlRow.add(Box.createHorizontalStrut(8));
        controlRow.add(diffOnlyButton);
        diffOnlyButton.setEnabled(false);
        captureDiffOnlyDefaults();
        controlRow.add(Box.createHorizontalStrut(8));
        controlRow.add(retryLenientButton);
        retryLenientButton.setVisible(false);
        controlRow.add(Box.createHorizontalStrut(8));
        controlRow.add(pairHistoryButton);
        controlRow.add(Box.createHorizontalGlue());
        controlRow.add(expandAllButton);
        controlRow.add(Box.createHorizontalStrut(4));
        controlRow.add(collapseAllButton);

        JPanel north = new JPanel(new BorderLayout());
        north.add(inputs, BorderLayout.CENTER);
        north.add(controlRow, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        add(new JBScrollPane(diffTree), BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.add(statusLabel, BorderLayout.CENTER);
        south.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        add(south, BorderLayout.SOUTH);
    }

    /** 框标题 + 输入区;历史入口不拆到每个框上,统一由控制行的"对比历史"整对回填 */
    private JPanel labeled(String title, JBScrollPane scrollPane) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JBLabel(title), BorderLayout.NORTH);
        p.add(scrollPane, BorderLayout.CENTER);
        return p;
    }

    /** 输入 A / 输入 B 的垂直滚动条双向同步(输入框已换行,无横向滚动条) */
    private void installScrollSync() {
        bindSync(scrollPaneA.getVerticalScrollBar(), scrollPaneB.getVerticalScrollBar());
        bindSync(scrollPaneB.getVerticalScrollBar(), scrollPaneA.getVerticalScrollBar());
    }

    private void bindSync(JScrollBar source, JScrollBar target) {
        if (source == null || target == null) {
            return;
        }
        source.addAdjustmentListener(e -> {
            if (scrollSyncing) {
                return;
            }
            scrollSyncing = true;
            try {
                // 两边内容长度可能不同,按滚动比例同步而非绝对值
                double ratio = scrollRatio(source);
                int range = visibleRange(target);
                if (range > 0) {
                    target.setValue(target.getMinimum() + (int) Math.round(ratio * range));
                }
            } finally {
                scrollSyncing = false;
            }
        });
    }

    private static double scrollRatio(JScrollBar bar) {
        int range = visibleRange(bar);
        if (range <= 0) {
            return 0;
        }
        return (double) (bar.getValue() - bar.getMinimum()) / range;
    }

    /** 可滚动范围 = model 的 max - min - 可见量(JScrollBar 本身不暴露 extent) */
    private static int visibleRange(JScrollBar bar) {
        BoundedRangeModel model = bar.getModel();
        return model.getMaximum() - model.getMinimum() - model.getExtent();
    }

    private void wireEvents() {
        // 输入即对比:两侧文本任一变更都重新调度,靠定时器防抖
        DocumentListener liveCompare = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                scheduleCompare();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                scheduleCompare();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // 仅属性变更,不影响文本
            }
        };
        inputA.getDocument().addDocumentListener(liveCompare);
        inputB.getDocument().addDocumentListener(liveCompare);

        // 切换模式立即重算;仅当真变化才调度,避开下拉弹出/关闭产生的空 ActionEvent
        modeCombo.addActionListener(e -> {
            if (selectedMode() != lastComparedMode) {
                scheduleCompare();
            }
        });
        diffOnlyButton.addActionListener(e -> {
            updateDiffOnlyHighlight();
            refreshTree();
        });
        retryLenientButton.addActionListener(e -> modeCombo.setSelectedItem(ParseMode.LENIENT));
        expandAllButton.addActionListener(e -> diffTree.expandAllNodes());
        collapseAllButton.addActionListener(e -> diffTree.collapseAllNodes());
        pairHistoryButton.setToolTipText("选择之前对比过的一组输入,选中后同时回填 A、B 并恢复当时的模式");
        HistoryPopup.attachPairs(pairHistoryButton, this::restorePair);
        // 初始为未选中,设默认 tooltip
        updateDiffOnlyHighlight();
    }

    /** 记住按钮默认外观,并按加粗后的宽度锁定尺寸,避免高亮切换时按钮跳动 */
    private void captureDiffOnlyDefaults() {
        diffOnlyDefaultForeground = diffOnlyButton.getForeground();
        diffOnlyDefaultFont = diffOnlyButton.getFont();
        diffOnlyDefaultBorder = diffOnlyButton.getBorder();

        Dimension base = diffOnlyButton.getPreferredSize();
        FontMetrics regular = diffOnlyButton.getFontMetrics(diffOnlyDefaultFont);
        FontMetrics bold = diffOnlyButton.getFontMetrics(diffOnlyDefaultFont.deriveFont(Font.BOLD));
        int chrome = base.width - regular.stringWidth(DIFF_ONLY_LABEL);
        int width = Math.max(base.width, bold.stringWidth(DIFF_ONLY_LABEL) + chrome);
        diffOnlyButton.setPreferredSize(new Dimension(width, base.height));
    }

    /** 选中时用强调色 + 加粗 + 描边高亮,文案保持不变 */
    private void updateDiffOnlyHighlight() {
        if (diffOnlyButton.isSelected()) {
            diffOnlyButton.setForeground(ACCENT);
            diffOnlyButton.setFont(diffOnlyDefaultFont.deriveFont(Font.BOLD));
            diffOnlyButton.setBorder(BorderFactory.createLineBorder(ACCENT, 1));
            diffOnlyButton.setToolTipText("当前只显示差异,点击取消高亮并恢复展示完全相等的分支");
        } else {
            diffOnlyButton.setForeground(diffOnlyDefaultForeground);
            diffOnlyButton.setFont(diffOnlyDefaultFont);
            diffOnlyButton.setBorder(diffOnlyDefaultBorder);
            diffOnlyButton.setToolTipText("隐藏完全相等的分支,只保留差异节点");
        }
    }

    private void scheduleCompare() {
        compareTimer.restart();
    }

    /** 防抖到期后的入口:两边都有内容才真正解析,避免输入初始阶段刷一屏报错 */
    private void autoCompare() {
        if (inputA.getText().isBlank() || inputB.getText().isBlank()) {
            retryLenientButton.setVisible(false);
            clearResult("输入 A 和输入 B 都有内容后会自动对比", false);
            return;
        }
        doCompare(selectedMode());
    }

    private ParseMode selectedMode() {
        Object sel = modeCombo.getSelectedItem();
        return sel instanceof ParseMode m ? m : ParseMode.STRICT;
    }

    /**
     * 回填一次对比现场:先恢复模式再填两个输入框。
     * setText 会触发文档监听,回填后自动重新对比,无需手动刷一次。
     */
    private void restorePair(DiffHistoryEntry entry) {
        ParseMode mode = modeByName(entry.mode);
        if (mode != null) {
            modeCombo.setSelectedItem(mode);
        }
        inputA.setText(entry.textA);
        inputB.setText(entry.textB);
    }

    /** 历史里的模式存的是字符串,旧版本或重命名后的值解析不出来就保持当前模式 */
    private ParseMode modeByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return ParseMode.valueOf(name.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void doCompare(ParseMode mode) {
        lastComparedMode = mode;
        // 新一轮解析先复位重试按钮,失败时再按需显示
        retryLenientButton.setVisible(false);
        JsonNodeModel left;
        JsonNodeModel right;
        try {
            left = parseService.parse(inputA.getText(), mode);
        } catch (JsonParseException ex) {
            onParseFailure("A", ex, mode);
            return;
        }
        try {
            right = parseService.parse(inputB.getText(), mode);
        } catch (JsonParseException ex) {
            onParseFailure("B", ex, mode);
            return;
        }
        DiffResult result = diffService.compare(left, right);
        // 只记录解析通过的现场,保证历史里每项都是可用的完整 JSON
        JsonHistoryStore store = JsonHistoryStore.getInstance();
        store.recordPair(inputA.getText(), inputB.getText(), mode.name());
        // 单段文本继续进共用池,格式化面板也能取用对比过的 JSON
        store.record(inputA.getText());
        store.record(inputB.getText());
        lastDiffRoot = result.getRoot();
        diffOnlyButton.setEnabled(true);
        refreshTree();
        String message = result.getSummary().toString();
        if (result.getSummary().getNodeCount() > DIFF_NODE_WARN) {
            message += "(差异树节点较多,展开/收起可能偏慢)";
        }
        setStatus(message, false);
    }

    /** 按当前"仅展示差异"开关重新渲染上一次对比结果 */
    private void refreshTree() {
        if (lastDiffRoot == null) {
            return;
        }
        diffTree.showDiff(lastDiffRoot, diffOnlyButton.isSelected());
    }

    private void onParseFailure(String side, JsonParseException ex, ParseMode mode) {
        clearResult("输入 " + side + " 解析失败:" + ex.getMessage(), true);
        if (mode == ParseMode.STRICT) {
            retryLenientButton.setVisible(true);
        }
    }

    /** 清掉差异树与相关按钮状态,并给出提示 */
    private void clearResult(String message, boolean error) {
        diffTree.setModel(new DefaultTreeModel(null));
        lastDiffRoot = null;
        diffOnlyButton.setEnabled(false);
        setStatus(message, error);
    }

    private void setStatus(String message, boolean error) {
        UiFeedback.setStatus(statusLabel, message, error);
    }
}
