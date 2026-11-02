package com.tidy.kit.parse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 锁住一条容易踩的耦合:下拉框展示中文靠 toString(),但写进历史文件的必须是枚举名。
 * 有人把持久化改成 toString()/getLabel() 时,老历史数据会读不回来,这几条断言会先红。
 */
class ParseModeTest {

    @Test
    void displaysChineseLabel() {
        assertEquals("严格", ParseMode.STRICT.toString());
        assertEquals("宽松", ParseMode.LENIENT.toString());
        assertEquals("严格", ParseMode.STRICT.getLabel());
    }

    @Test
    void enumNameStaysEnglishForPersistence() {
        assertEquals("STRICT", ParseMode.STRICT.name());
        assertEquals("LENIENT", ParseMode.LENIENT.name());
        // 存 name() 读 valueOf() 的闭环不能被 toString() 影响
        assertEquals(ParseMode.LENIENT, ParseMode.valueOf(ParseMode.LENIENT.name()));
    }

    @Test
    void labelOfToleratesStoredGarbage() {
        assertEquals("宽松", ParseMode.labelOf("LENIENT"));
        assertEquals("宽松", ParseMode.labelOf(" lenient "));
        assertEquals("宽松", ParseMode.labelOf("lenient"));
        assertEquals("严格", ParseMode.labelOf(null));
        assertEquals("严格", ParseMode.labelOf(""));
        assertEquals("严格", ParseMode.labelOf("UNKNOWN"));
    }
}
