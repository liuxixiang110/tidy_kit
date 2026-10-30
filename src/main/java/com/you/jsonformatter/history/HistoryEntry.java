package com.you.jsonformatter.history;

/**
 * 一条输入历史。公有字段便于 IntelliJ 的 XStream 序列化,请勿改成 getter/setter
 * (两者混用会让 XML 出现重复字段)。
 */
public class HistoryEntry {

    /** 原始文本,保持录入时的空白与格式以便原样回填 */
    public String text;

    /** 记录时刻(epoch millis),仅用于展示与排序 */
    public long savedAt;

    public HistoryEntry() {
    }

    public HistoryEntry(String text, long savedAt) {
        this.text = text;
        this.savedAt = savedAt;
    }
}
