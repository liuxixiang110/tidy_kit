package com.tidy.kit.ui;

import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import com.tidy.kit.format.HtmlFormatter;
import com.tidy.kit.format.TextFormatter;
import com.tidy.kit.format.XmlFormatter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * 工具窗口内的多标签容器:顶部五个按钮分别新建
 * JSON格式化 / JSON对比 / URL编码/解码 / HTML格式化 / XML格式化 标签,每个标签带 "×" 可关闭。
 * 标签头可按住左右拖动换位置,松手时生效(见 ReorderableTabbedPane)。
 * 按钮行用 FlowLayout,工具窗口很窄时会自然换行。
 */
public class TidyKitTabs extends JPanel {

    private final ReorderableTabbedPane tabbedPane = new ReorderableTabbedPane();
    private int formatCounter = 0;
    private int diffCounter = 0;
    private int urlCounter = 0;
    private int htmlCounter = 0;
    private int xmlCounter = 0;

    public TidyKitTabs() {
        super(new BorderLayout());
        setBorder(JBUI.Borders.empty());

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        toolbar.setBorder(BorderFactory.createEmptyBorder());
        addButton(toolbar, "JSON格式化", "新建一个 JSON 格式化标签", () -> addFormatTab(true));
        addButton(toolbar, "JSON对比", "新建一个 JSON 对比标签", () -> addDiffTab(true));
        addButton(toolbar, "URL编码/解码", "新建一个 URL 百分号编码解码标签", () -> addUrlTab(true));
        addButton(toolbar, "HTML格式化", "新建一个 HTML 格式化标签", () -> addTextTab("HTML", new HtmlFormatter(), true));
        addButton(toolbar, "XML格式化", "新建一个 XML 格式化标签", () -> addTextTab("XML", new XmlFormatter(), true));
        add(toolbar, BorderLayout.NORTH);

        add(tabbedPane, BorderLayout.CENTER);

        // 重排时容器会给被挪走的标签重建标签头(不复用刚被摘下来的组件),这里提供造头入口
        tabbedPane.setHeaderFactory(this::newHeader);

        // 首个标签不预填剪贴板(由工厂处理),这里建一个空面板占位
        addFormatTab(false);
    }

    /** 五个新建标签的按钮长得一样,抽出来免得四处重复 setMargin/setToolTip */
    private static void addButton(JPanel toolbar, String text, String tooltip, Runnable action) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setMargin(JBUI.insets(2, 10));
        button.addActionListener(e -> action.run());
        toolbar.add(button);
    }

    /** 供工厂在首个标签预填剪贴板内容 */
    public JsonTreePanel firstPanel() {
        Component c = tabbedPane.getComponentAt(0);
        return c instanceof JsonTreePanel p ? p : null;
    }

    private void addFormatTab(boolean focus) {
        JsonTreePanel panel = new JsonTreePanel();
        addTab(panel, "JSON格式化 " + (++formatCounter), focus);
    }

    private void addDiffTab(boolean focus) {
        JsonDiffPanel panel = new JsonDiffPanel();
        addTab(panel, "JSON对比 " + (++diffCounter), focus);
    }

    /** 纯文本转换,不涉及解析;标签名取短式,免得"URL编码/解码 n"把标签头撑太宽 */
    private void addUrlTab(boolean focus) {
        addTab(new UrlCodecPanel(), "URL " + (++urlCounter), focus);
    }

    /** HTML 与 XML 共用 TextFormatPanel,只靠格式化器与标题区分;标签名取短式免得头部挤 */
    private void addTextTab(String kind, TextFormatter formatter, boolean focus) {
        int counter = "HTML".equals(kind) ? ++htmlCounter : ++xmlCounter;
        addTab(new TextFormatPanel(kind, formatter), kind + " " + counter, focus);
    }

    private void addTab(JPanel panel, String title, boolean focus) {
        int index = tabbedPane.getTabCount();
        tabbedPane.addTab(title, panel);
        tabbedPane.setTabComponentAt(index, newHeader(title));
        if (focus) {
            tabbedPane.setSelectedIndex(index);
        }
    }

    /** 标签头 = 标题 + 关闭 + 拖拽换序手势;新建标签与重排后重建都走这里,保证两条路径配置一致 */
    private JComponent newHeader(String title) {
        JPanel header = buildTabHeader(title);
        tabbedPane.installDragReorder(header);
        return header;
    }

    /** 自定义标签头:标题 + 关闭按钮;整块都能点选中,仅关闭按钮除外 */
    private JPanel buildTabHeader(String title) {
        // 左右内边距算在标签头自己身上:tab 的尺寸跟着标签头的首选尺寸算,
        // 留给 pane 的内边距越少,能点中标签的区域就越大
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(6), 0));
        header.setOpaque(false);
        header.add(new JBLabel(title));

        JButton close = new JButton("×");
        // 必须显锁尺寸:IDE 新 UI 会给 JButton 一个较大的最小宽度,
        // 不锁定则"×"的点击热区远大于字形本身,点到标签里的空白处就把标签关掉了
        Dimension hitArea = new Dimension(JBUI.scale(16), JBUI.scale(16));
        close.setPreferredSize(hitArea);
        close.setMinimumSize(hitArea);
        close.setMaximumSize(hitArea);
        close.setMargin(JBUI.emptyInsets());
        close.setBorder(BorderFactory.createEmptyBorder());
        close.setContentAreaFilled(false);
        close.setFocusable(false);
        close.setToolTipText("关闭该标签");
        close.addActionListener(e -> closeTab(header));
        header.add(close);
        return header;
    }

    private void closeTab(Component header) {
        int index = -1;
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (tabbedPane.getTabComponentAt(i) == header) {
                index = i;
                break;
            }
        }
        // 任何标签都能关,包括最后一个(标签可拖拽换位后,"位置 0 不可关"这种按位置的限制已没意义);
        // 全关掉只剩空面板也不要紧,顶部五个按钮随时能新建
        if (index >= 0) {
            tabbedPane.removeTabAt(index);
        }
    }
}
