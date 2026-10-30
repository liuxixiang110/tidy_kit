package com.tidy.kit.history;

import java.util.ArrayList;
import java.util.List;

/**
 * 对比历史的纯 Java 容器:一条 = 一次对比现场(A 文本 + B 文本 + 模式)。
 * 最新在前,按 A/B 文本完全相同去重(重复则提到最新并刷新模式)。
 * 不 import 任何 IDE 类型,可脱离平台单测;读写盘由 JsonHistoryStore 负责。
 */
public class DiffHistoryEntries {

    /** 最多保留条数,超出丢弃最旧。成对条目体积约等于两份 JSON,上限比单段历史更紧 */
    public static final int MAX_ENTRIES = 30;

    /** A 与 B 合计超过该字符数的条目不入历史,避免配置目录被大 JSON 撑爆 */
    public static final int MAX_TOTAL_LENGTH = 200_000;

    /** 没有记录模式时的兜底值,与 ParseMode.STRICT 的默认一致 */
    static final String DEFAULT_MODE = "STRICT";

    private final List<DiffHistoryEntry> items = new ArrayList<>();

    /**
     * 记录一次对比现场。
     *
     * @return 是否真正入库(任一侧空白或两侧合计超长会被忽略)
     */
    public boolean record(String textA, String textB, String mode, long now) {
        if (textA == null || textB == null || textA.isBlank() || textB.isBlank()) {
            return false;
        }
        if (textA.length() + textB.length() > MAX_TOTAL_LENGTH) {
            return false;
        }
        for (int i = 0; i < items.size(); i++) {
            DiffHistoryEntry old = items.get(i);
            if (textA.equals(old.textA) && textB.equals(old.textB)) {
                items.remove(i);
                break;
            }
        }
        items.add(0, new DiffHistoryEntry(textA, textB, normalizeMode(mode), now));
        while (items.size() > MAX_ENTRIES) {
            items.remove(items.size() - 1);
        }
        return true;
    }

    public void clear() {
        items.clear();
    }

    /** 最新在前 */
    public List<DiffHistoryEntry> list() {
        return new ArrayList<>(items);
    }

    public int size() {
        return items.size();
    }

    /** 从持久化状态回填,丢弃残缺/超长条目并截断到上限 */
    public void loadFrom(List<DiffHistoryEntry> saved) {
        items.clear();
        if (saved == null) {
            return;
        }
        for (DiffHistoryEntry entry : saved) {
            if (entry == null || entry.textA == null || entry.textB == null
                    || entry.textA.isBlank() || entry.textB.isBlank()
                    || entry.textA.length() + entry.textB.length() > MAX_TOTAL_LENGTH) {
                continue;
            }
            entry.mode = normalizeMode(entry.mode);
            items.add(entry);
            if (items.size() >= MAX_ENTRIES) {
                break;
            }
        }
    }

    private static String normalizeMode(String mode) {
        return mode == null || mode.isBlank() ? DEFAULT_MODE : mode.trim();
    }
}
