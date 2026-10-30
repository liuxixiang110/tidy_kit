package com.tidy.kit.history;

/**
 * 一条对比历史:一次成功对比的完整现场(左右原文 + 当时的解析模式)。
 * 公有字段便于 IntelliJ 的 XStream 序列化,请勿改成 getter/setter
 * (两者混用会让 XML 出现重复字段)。
 */
public class DiffHistoryEntry {

    /** 输入 A 的原始文本,保持录入时的空白与格式以便原样回填 */
    public String textA;

    /** 输入 B 的原始文本 */
    public String textB;

    /** 解析模式名(ParseMode 的枚举名)。存字符串而非枚举,避免历史包依赖解析包 */
    public String mode;

    /** 记录时刻(epoch millis),仅用于展示与排序 */
    public long savedAt;

    public DiffHistoryEntry() {
    }

    public DiffHistoryEntry(String textA, String textB, String mode, long savedAt) {
        this.textA = textA;
        this.textB = textB;
        this.mode = mode;
        this.savedAt = savedAt;
    }
}
