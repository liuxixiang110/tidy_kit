package com.tidy.kit.ui;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.ui.components.JBTextField;
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
import java.util.List;

/**
 * 工具窗口与编辑器 Dialog 复用的主面板(设计 5.3)。
 * 布局:输入区 -> 控制行(模式/展开收起/宽松重试/历史/查找) -> 树区 -> 状态栏 + [复制结果]。
 * 输入停止 300ms 自动解析渲染,不设"格式化"按钮;输入为空时结果区直接留空。
 * 输入区与结果区之间用 JBSplitter 分隔,高度可拖(与 HTML/XML 面板一致)。
 * 结果树上的右键可复制选中节点及其子树的完整 JSON。
 * 查找按子串包含(大小写敏感)在 key/value 里找,命中就展开所在分支并高亮关键字;
 * 查询串没变时重复点`查找`/回车会在命中之间循环跳转,走到最后一处后回到第一处。
 */
public class JsonTreePanel extends JPanel {

    /** 节点总数超过该值提示切纯文本(设计 5.3 性能) */
    private static final int NODE_COUNT_WARN = 100_000;
    /** 自动格式化的防抖时长,与对比面板的自动对比保持一致 */
    private static final int AUTO_FORMAT_DELAY_MS = 300;
    /**
     * 一次查找最多展开多少条命中路径。全展开会把树撑成几万行、重绘与滚动都卡;
     * 高亮不受这个上限影响(渲染器就地判定),循环跳转也不受它影响 —— 它只展开
     * 当前这一处,代价与总命中数无关,所以游标能走到全部命中而不只是前 500 条。
     */
    private static final int MAX_EXPAND_HITS = 500;

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
    private final JBTextField findField = new JBTextField();
    private final JButton findButton = new JButton("查找");
    private final JButton copyButton = new JButton("复制结果");
    private final JBLabel statusLabel = new JBLabel(" ");
    private final JsonTree tree = new JsonTree(msg -> setStatus(msg, false));

    private String lastPretty = "";
    /** 上一次真正用于解析的模式,用于识别下拉选项是否真的变了(与对比面板同一手法) */
    private ParseMode lastFormattedMode = ParseMode.STRICT;
    /** 上一次查找用的查询串;与输入框当前内容比较,用来区分"新查询"和"跳到下一处" */
    private String lastFindQuery;
    /** 上一次查找的命中(文档序),循环跳转就在它上面推进 */
    private List<JsonTreeSearch.Hit> findHits = List.of();
    /** 当前定位在 findHits 里的下标;-1 表示还没查过 */
    private int findCursor = -1;

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
        controlRow.add(Box.createHorizontalStrut(8));
        // 查找框必须同时锁 preferred 与 maximum:BoxLayout 会把组件撑到 maximum,
        // 不锁高度就会把整行拉高(与标签头上"×"按钮被 IDE 新 UI 撑大是同一类坑)
        Dimension findSize = new Dimension(JBUI.scale(160), findField.getPreferredSize().height);
        findField.setPreferredSize(findSize);
        findField.setMaximumSize(findSize);
        findField.setToolTipText("在 key 与 value 里查找子串(区分大小写);回车等价于点查找,查询串没变时反复按会在命中之间循环跳转");
        controlRow.add(findField);
        controlRow.add(Box.createHorizontalStrut(4));
        controlRow.add(findButton);
        controlRow.add(Box.createHorizontalGlue());

        JPanel top = new JPanel(new BorderLayout());
        top.add(inputScroll, BorderLayout.CENTER);
        top.add(controlRow, BorderLayout.SOUTH);

        // 输入区与结果树之间用可拖动分割条,高度不再写死;初始比例与 HTML/XML 面板一致
        JBSplitter splitter = new JBSplitter(true, 0.4f);
        splitter.setFirstComponent(top);
        splitter.setSecondComponent(new JBScrollPane(tree));
        add(splitter, BorderLayout.CENTER);

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
        findButton.addActionListener(e -> doFind());
        // JTextField 的回车本来就发 ActionEvent,不需要另加 KeyBinding
        findField.addActionListener(e -> doFind());
    }

    /**
     * 查找:查询串变了就在模型树上重找并停在第一处(连带展开命中分支),
     * 没变就只把游标推到下一处、单独跳转 —— 重复点`查找`等价于逐个翻阅命中。
     * 高亮不靠命中集合 —— 渲染器拿着 query 就地判定,所以重解析后高亮会自动跟着新树走。
     */
    private void doFind() {
        String query = findField.getText();
        if (query == null || query.isBlank()) {
            resetFind();
            tree.setSearchQuery(null);
            setStatus("请输入要查找的内容", false);
            return;
        }
        // 走到这里说明 query 与上次不同(含首次查找、刚改过输入、上一次没找到):当作新查询
        if (!query.equals(lastFindQuery)) {
            JsonNodeModel root = tree.rootModel();
            if (root == null) {
                // 还没解析出结果(新标签或输入已清空):不报错,也不清掉已有的高亮
                return;
            }
            List<JsonTreeSearch.Hit> hits = JsonTreeSearch.hits(root, query);
            if (hits.isEmpty()) {
                resetFind();
                tree.setSearchQuery(null);
                setStatus("未找到 \"" + query + "\"", false);
                return;
            }
            lastFindQuery = query;
            findHits = hits;
            findCursor = 0;
            // 先让高亮生效再展开,免得展开过程中的重绘用的还是旧 query
            tree.setSearchQuery(query);
            int expandable = Math.min(hits.size(), MAX_EXPAND_HITS);
            tree.expandAndSelect(hits.subList(0, expandable));
            String message = findStatus();
            if (expandable < hits.size()) {
                // 展开受上限影响时要说清楚只展开了前多少条(游标不受这个限制)
                message += "，仅展开前 " + expandable + " 处";
            }
            setStatus(message, false);
            return;
        }
        findCursor = nextCursor(findCursor, findHits.size());
        tree.selectAndReveal(findHits.get(findCursor));
        setStatus(findStatus(), false);
    }

    private void resetFind() {
        lastFindQuery = null;
        findHits = List.of();
        findCursor = -1;
    }

    private String findStatus() {
        return "第 " + (findCursor + 1) + "/" + findHits.size() + " 处，共 " + findHits.size() + " 处";
    }

    /** 同一查询串下的循环跳转:往后走一格,越过最后一条回到第一处 */
    static int nextCursor(int current, int total) {
        return current + 1 >= total ? 0 : current + 1;
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
