package com.tidy.kit.model;

/**
 * JSON 值类型,对应 RFC 8259 的六类值。
 */
public enum NodeType {
    OBJECT,
    ARRAY,
    STRING,
    NUMBER,
    BOOLEAN,
    NULL
}
