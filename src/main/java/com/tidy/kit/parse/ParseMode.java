package com.tidy.kit.parse;

/**
 * 解析模式开关。默认 STRICT。
 * toString() 返回中文,给下拉框直接展示;持久化与 valueOf 始终用 name()(STRICT/LENIENT)。
 */
public enum ParseMode {
    /** 仅标准 JSON(RFC 8259) */
    STRICT("严格"),
    /** 宽松:去转义、脏输入容错(注释/单引号/尾逗号/前后杂质) */
    LENIENT("宽松");

    private final String label;

    ParseMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }

    /** 把历史里存的枚举名转成展示用中文;认不了(旧数据、空值)一概当严格 */
    public static String labelOf(String storedName) {
        for (ParseMode mode : values()) {
            if (mode.name().equalsIgnoreCase(storedName == null ? "" : storedName.trim())) {
                return mode.label;
            }
        }
        return STRICT.label;
    }
}
