package com.tidy.kit.ui;

import com.tidy.kit.history.DiffHistoryEntry;
import com.tidy.kit.history.HistoryEntry;
import com.tidy.kit.history.JsonHistoryStore;

import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.util.List;
import java.util.function.Consumer;

/**
 * "历史"菜单:每次点开都重新构建,因此总能读到最新历史。
 * 两种菜单项:
 * - 单段历史:列出曾经解析成功过的 JSON 片段,点击回填到一个输入框
 * - 对比历史:列出曾经对比成功过的现场,点击同时回填 A 与 B
 */
final class HistoryPopup {

    /** 菜单标题里显示的摘要长度 */
    private static final int SUMMARY_MAX = 60;

    /** 成对条目的标题里每侧只显示这么多字符,否则菜单会被撑得过宽 */
    private static final int PAIR_SIDE_MAX = 24;

    /** tooltip 里每侧最多展示的行数与字符数。曾经直接塞 4000 字符,
     * 悬浮窗高度超过屏幕(日志: popup preferred size is bigger than screen),也没法读 */
    private static final int TOOLTIP_LINES = 24;
    private static final int TOOLTIP_CHARS = 1200;

    private HistoryPopup() {
    }

    /** 给按钮装上单段历史菜单,选中条目后交给 sink(通常是某个输入框的 setText) */
    static void attach(JButton button, Consumer<String> sink) {
        button.addActionListener(e -> build(sink).show(button, 0, button.getHeight()));
    }

    /** 给按钮装上对比历史菜单,选中条目后整对回填 */
    static void attachPairs(JButton button, Consumer<DiffHistoryEntry> sink) {
        button.addActionListener(e -> buildPairs(sink).show(button, 0, button.getHeight()));
    }

    private static JPopupMenu build(Consumer<String> sink) {
        JPopupMenu menu = new JPopupMenu();
        List<HistoryEntry> items = JsonHistoryStore.getInstance().list();
        if (items.isEmpty()) {
            JMenuItem empty = new JMenuItem("(暂无历史)");
            empty.setEnabled(false);
            menu.add(empty);
        }
        for (HistoryEntry entry : items) {
            JMenuItem item = new JMenuItem(summarize(entry.text));
            item.setToolTipText(tip(entry.text));
            item.addActionListener(e -> sink.accept(entry.text));
            menu.add(item);
        }
        menu.addSeparator();
        JMenuItem clear = new JMenuItem("清空历史");
        clear.addActionListener(e -> JsonHistoryStore.getInstance().clear());
        menu.add(clear);
        return menu;
    }

    /** 折叠空白后取前若干字符,并附字符数 */
    static String summarize(String text) {
        return flat(text, SUMMARY_MAX) + "  (" + sizeLabel(text.length()) + ")";
    }

    private static JPopupMenu buildPairs(Consumer<DiffHistoryEntry> sink) {
        JPopupMenu menu = new JPopupMenu();
        List<DiffHistoryEntry> items = JsonHistoryStore.getInstance().listPairs();
        if (items.isEmpty()) {
            JMenuItem empty = new JMenuItem("(暂无对比历史)");
            empty.setEnabled(false);
            menu.add(empty);
        }
        for (DiffHistoryEntry entry : items) {
            JMenuItem item = new JMenuItem(summarizePair(entry));
            item.setToolTipText(pairTip(entry));
            item.addActionListener(e -> sink.accept(entry));
            menu.add(item);
        }
        menu.addSeparator();
        JMenuItem clear = new JMenuItem("清空对比历史");
        clear.addActionListener(e -> JsonHistoryStore.getInstance().clearPairs());
        menu.add(clear);
        return menu;
    }

    /** 成对条目标题写成 "A 摘要 ⇄ B 摘要" 并附两侧合计字符数 */
    private static String summarizePair(DiffHistoryEntry entry) {
        return flat(entry.textA, PAIR_SIDE_MAX) + " ⇄ " + flat(entry.textB, PAIR_SIDE_MAX)
                + "  (" + sizeLabel(entry.textA.length() + entry.textB.length()) + ")";
    }

    private static String pairTip(DiffHistoryEntry entry) {
        return "<html><body>"
                + side("A  模式:" + modeLabel(entry.mode), entry.textA)
                + side("B", entry.textB)
                + "</body></html>";
    }

    /** 每侧一个 div,排版各按自己的内容定 */
    private static String side(String label, String text) {
        String body = clipForTip(text);
        return "<div style='" + tipStyle(body) + "'><b>" + label + "</b><br>" + escape(body) + "</div>";
    }

    private static String modeLabel(String mode) {
        return "LENIENT".equals(mode) ? "宽松" : "严格";
    }

    /** 折叠空白后的单行摘要 */
    private static String flat(String text, int max) {
        String collapsed = text.replaceAll("\\s+", " ").trim();
        return collapsed.length() <= max ? collapsed : collapsed.substring(0, max) + "...";
    }

    /** 先按行截断再按字符截断,保证悬浮窗不会高过屏幕 */
    private static String clipForTip(String text) {
        String[] lines = text.split("\n", -1);
        int limit = Math.min(lines.length, TOOLTIP_LINES);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        if (lines.length > limit) {
            sb.append("\n...");
        }
        String body = sb.toString();
        return body.length() <= TOOLTIP_CHARS ? body : body.substring(0, TOOLTIP_CHARS) + "...";
    }

    /** 压缩成一行的 JSON 不能用 pre,否则 tooltip 会变成一条几千像素宽的横条 */
    private static String tipStyle(String body) {
        return body.indexOf('\n') >= 0 ? "white-space:pre;" : "white-space:normal;";
    }

    private static String sizeLabel(int chars) {
        if (chars < 1024) {
            return chars + " 字符";
        }
        return String.format("%.1fK 字符", chars / 1024.0);
    }

    private static String tip(String text) {
        String body = clipForTip(text);
        return "<html><body style='" + tipStyle(body) + "'>" + escape(body) + "</body></html>";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
