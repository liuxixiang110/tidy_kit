package com.tidy.kit.parse;

/**
 * 解析模式开关。默认 STRICT。
 */
public enum ParseMode {
    /** 仅标准 JSON(RFC 8259) */
    STRICT,
    /** 宽松:去转义、脏输入容错(注释/单引号/尾逗号/前后杂质) */
    LENIENT
}
