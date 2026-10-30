package com.tidy.kit.history;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON 输入历史的纯 Java 容器:最新在前,按文本完全相同去重(重复则提到最新)。
 * 不 import 任何 IDE 类型,可脱离平台单测;读写盘由 JsonHistoryStore 负责。
 */
public class HistoryEntries {

    /** 最多保留条数,超出丢弃最旧 */
    public static final int MAX_ENTRIES = 50;

    /** 超过该字符数的条目不入历史,避免配置目录被大 JSON 撑爆 */
    public static final int MAX_TEXT_LENGTH = 200_000;

    private final List<HistoryEntry> items = new ArrayList<>();

    /**
     * 记录一条历史。
     *
     * @return 是否真正入库(空白或超长条目会被忽略)
     */
    public boolean record(String text, long now) {
        if (text == null || text.isBlank() || text.length() > MAX_TEXT_LENGTH) {
            return false;
        }
        for (int i = 0; i < items.size(); i++) {
            if (text.equals(items.get(i).text)) {
                items.remove(i);
                break;
            }
        }
        items.add(0, new HistoryEntry(text, now));
        while (items.size() > MAX_ENTRIES) {
            items.remove(items.size() - 1);
        }
        return true;
    }

    public void clear() {
        items.clear();
    }

    /** 最新在前 */
    public List<HistoryEntry> list() {
        return new ArrayList<>(items);
    }

    public int size() {
        return items.size();
    }

    /** 从持久化状态回填,丢弃空白与超长项并截断到上限(与 record 的过滤规则保持一致) */
    public void loadFrom(List<HistoryEntry> saved) {
        items.clear();
        if (saved == null) {
            return;
        }
        for (HistoryEntry entry : saved) {
            if (entry == null || entry.text == null || entry.text.isBlank()
                    || entry.text.length() > MAX_TEXT_LENGTH) {
                continue;
            }
            items.add(entry);
            if (items.size() >= MAX_ENTRIES) {
                break;
            }
        }
    }
}
