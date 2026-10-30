package com.you.jsonformatter.history;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对比历史容器纯逻辑单测(脱离 IDE):整对去重、模式刷新、上限、两侧合计超长、回填清洗。
 */
class DiffHistoryEntriesTest {

    @Test
    void recordsNewestFirst() {
        DiffHistoryEntries entries = new DiffHistoryEntries();
        assertTrue(entries.record("{\"a\":1}", "{\"a\":2}", "STRICT", 1L));
        assertTrue(entries.record("{\"b\":1}", "{\"b\":2}", "LENIENT", 2L));

        List<DiffHistoryEntry> list = entries.list();
        assertEquals(2, list.size());
        assertEquals("{\"b\":1}", list.get(0).textA);
        assertEquals("{\"b\":2}", list.get(0).textB);
        assertEquals("LENIENT", list.get(0).mode);
        assertEquals("{\"a\":1}", list.get(1).textA);
    }

    @Test
    void samePairMovesToTopAndRefreshesMode() {
        DiffHistoryEntries entries = new DiffHistoryEntries();
        entries.record("{\"a\":1}", "{\"a\":2}", "STRICT", 1L);
        entries.record("{\"b\":1}", "{\"b\":2}", "STRICT", 2L);
        // 同一对输入换个模式再比一次:不应新增条目,但要提到最新并带上新模式
        entries.record("{\"a\":1}", "{\"a\":2}", "LENIENT", 3L);

        List<DiffHistoryEntry> list = entries.list();
        assertEquals(2, list.size());
        assertEquals("{\"a\":1}", list.get(0).textA);
        assertEquals("LENIENT", list.get(0).mode);
        assertEquals(3L, list.get(0).savedAt);
    }

    @Test
    void swappingSidesCountsAsAnotherPair() {
        DiffHistoryEntries entries = new DiffHistoryEntries();
        entries.record("{\"a\":1}", "{\"a\":2}", "STRICT", 1L);
        entries.record("{\"a\":2}", "{\"a\":1}", "STRICT", 2L);

        // A、B 是有方向的,互换视为另一次现场
        assertEquals(2, entries.size());
    }

    @Test
    void skipsBlankSideAndNull() {
        DiffHistoryEntries entries = new DiffHistoryEntries();
        assertFalse(entries.record(null, "{\"a\":1}", "STRICT", 1L));
        assertFalse(entries.record("{\"a\":1}", null, "STRICT", 1L));
        assertFalse(entries.record("", "{\"a\":1}", "STRICT", 1L));
        assertFalse(entries.record("{\"a\":1}", "   \n\t ", "STRICT", 1L));
        assertEquals(0, entries.size());
    }

    @Test
    void skipsWhenCombinedLengthExceedsCap() {
        DiffHistoryEntries entries = new DiffHistoryEntries();
        int half = DiffHistoryEntries.MAX_TOTAL_LENGTH / 2;
        // 单侧都不超,但两侧合计刚好触顶,应当收
        assertTrue(entries.record("a".repeat(half), "b".repeat(half), "STRICT", 1L));
        assertEquals(1, entries.size());

        entries.clear();
        assertFalse(entries.record("a".repeat(half), "b".repeat(half + 1), "STRICT", 1L));
        assertEquals(0, entries.size());
    }

    @Test
    void blankModeFallsBackToStrict() {
        DiffHistoryEntries entries = new DiffHistoryEntries();
        entries.record("{\"a\":1}", "{\"a\":2}", null, 1L);
        assertEquals(DiffHistoryEntries.DEFAULT_MODE, entries.list().get(0).mode);
    }

    @Test
    void capsAtMaxEntriesDroppingOldest() {
        DiffHistoryEntries entries = new DiffHistoryEntries();
        int total = DiffHistoryEntries.MAX_ENTRIES + 5;
        for (int i = 0; i < total; i++) {
            assertTrue(entries.record("{\"i\":" + i + "}", "{\"i\":" + i + "x}", "STRICT", i));
        }

        List<DiffHistoryEntry> list = entries.list();
        assertEquals(DiffHistoryEntries.MAX_ENTRIES, list.size());
        assertEquals("{\"i\":" + (total - 1) + "}", list.get(0).textA);
        assertEquals("{\"i\":5}", list.get(list.size() - 1).textA);
    }

    @Test
    void loadFromDropsIncompleteEntries() {
        DiffHistoryEntries entries = new DiffHistoryEntries();
        List<DiffHistoryEntry> saved = new ArrayList<>();
        saved.add(new DiffHistoryEntry("{\"x\":1}", "  ", "STRICT", 1L));
        saved.add(new DiffHistoryEntry("{\"y\":1}", null, "STRICT", 2L));
        saved.add(new DiffHistoryEntry("a".repeat(DiffHistoryEntries.MAX_TOTAL_LENGTH), "b", "STRICT", 3L));
        saved.add(null);
        saved.add(new DiffHistoryEntry("{\"z\":1}", "{\"z\":2}", "", 4L));

        entries.loadFrom(saved);

        // 只有最后那条完整且两侧不超长的能进来,空白模式被补成默认值
        assertEquals(1, entries.size());
        assertEquals("{\"z\":1}", entries.list().get(0).textA);
        assertEquals(DiffHistoryEntries.DEFAULT_MODE, entries.list().get(0).mode);
    }

    @Test
    void loadFromTruncatesToCap() {
        DiffHistoryEntries entries = new DiffHistoryEntries();
        List<DiffHistoryEntry> saved = new ArrayList<>();
        // 残缺项放在前面,否则会在达到上限时被 break 跳过,测不到过滤分支
        saved.add(new DiffHistoryEntry("{\"x\":1}", "  ", "STRICT", 9L));
        saved.add(null);
        for (int i = 0; i < DiffHistoryEntries.MAX_ENTRIES + 5; i++) {
            saved.add(new DiffHistoryEntry("{\"i\":" + i + "}", "{\"i\":" + i + "x}", "LENIENT", i));
        }

        entries.loadFrom(saved);

        List<DiffHistoryEntry> list = entries.list();
        assertEquals(DiffHistoryEntries.MAX_ENTRIES, list.size());
        assertEquals("{\"i\":0}", list.get(0).textA);
        assertEquals("LENIENT", list.get(0).mode);
    }

    @Test
    void loadFromNullClears() {
        DiffHistoryEntries entries = new DiffHistoryEntries();
        entries.record("{\"a\":1}", "{\"a\":2}", "STRICT", 1L);
        entries.loadFrom(null);
        assertEquals(0, entries.size());
    }

    @Test
    void listIsDefensiveCopy() {
        DiffHistoryEntries entries = new DiffHistoryEntries();
        entries.record("{\"a\":1}", "{\"a\":2}", "STRICT", 1L);
        entries.list().clear();
        assertEquals(1, entries.size());
    }
}
