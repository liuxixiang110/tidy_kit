package com.you.jsonformatter.history;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 历史容器纯逻辑单测(脱离 IDE):去重、上限、超长跳过、回填清洗。
 */
class HistoryEntriesTest {

    @Test
    void recordsNewestFirst() {
        HistoryEntries entries = new HistoryEntries();
        assertTrue(entries.record("{\"a\":1}", 1L));
        assertTrue(entries.record("{\"b\":2}", 2L));

        List<HistoryEntry> list = entries.list();
        assertEquals(2, list.size());
        assertEquals("{\"b\":2}", list.get(0).text);
        assertEquals(2L, list.get(0).savedAt);
        assertEquals("{\"a\":1}", list.get(1).text);
    }

    @Test
    void duplicateTextMovesToTopWithoutGrowing() {
        HistoryEntries entries = new HistoryEntries();
        entries.record("{\"a\":1}", 1L);
        entries.record("{\"b\":2}", 2L);
        entries.record("{\"a\":1}", 3L);

        List<HistoryEntry> list = entries.list();
        assertEquals(2, list.size());
        assertEquals("{\"a\":1}", list.get(0).text);
        assertEquals(3L, list.get(0).savedAt);
    }

    @Test
    void keepsWhitespaceVariantAsSeparateEntry() {
        HistoryEntries entries = new HistoryEntries();
        entries.record("{\"a\":1}", 1L);
        entries.record("{ \"a\": 1 }", 2L);

        // 按文本完全相同去重,格式差异视为两条(回填时才能还原用户原本粘贴的样子)
        assertEquals(2, entries.size());
    }

    @Test
    void skipsBlankAndNull() {
        HistoryEntries entries = new HistoryEntries();
        assertFalse(entries.record(null, 1L));
        assertFalse(entries.record("", 1L));
        assertFalse(entries.record("   \n\t ", 1L));
        assertEquals(0, entries.size());
    }

    @Test
    void skipsOversizedEntry() {
        HistoryEntries entries = new HistoryEntries();
        String tooLong = "a".repeat(HistoryEntries.MAX_TEXT_LENGTH + 1);
        assertFalse(entries.record(tooLong, 1L));
        assertEquals(0, entries.size());

        String atLimit = "a".repeat(HistoryEntries.MAX_TEXT_LENGTH);
        assertTrue(entries.record(atLimit, 2L));
        assertEquals(1, entries.size());
    }

    @Test
    void capsAtMaxEntriesDroppingOldest() {
        HistoryEntries entries = new HistoryEntries();
        int total = HistoryEntries.MAX_ENTRIES + 10;
        for (int i = 0; i < total; i++) {
            assertTrue(entries.record("{\"i\":" + i + "}", i));
        }

        List<HistoryEntry> list = entries.list();
        assertEquals(HistoryEntries.MAX_ENTRIES, list.size());
        assertEquals("{\"i\":" + (total - 1) + "}", list.get(0).text);
        // 最旧的 10 条被淘汰,i=10 是当前最末
        assertEquals("{\"i\":10}", list.get(list.size() - 1).text);
    }

    @Test
    void loadFromDropsBlankAndTruncatesToCap() {
        HistoryEntries entries = new HistoryEntries();
        List<HistoryEntry> saved = new ArrayList<>();
        for (int i = 0; i < HistoryEntries.MAX_ENTRIES + 5; i++) {
            saved.add(new HistoryEntry("{\"i\":" + i + "}", i));
        }
        saved.add(new HistoryEntry("  ", 999L));
        saved.add(null);

        entries.loadFrom(saved);

        List<HistoryEntry> list = entries.list();
        assertEquals(HistoryEntries.MAX_ENTRIES, list.size());
        assertEquals("{\"i\":0}", list.get(0).text);
    }

    @Test
    void loadFromDropsOversizedEntry() {
        HistoryEntries entries = new HistoryEntries();
        List<HistoryEntry> saved = new ArrayList<>();
        // 旧文件里可能残留超长条目,回填时要按与 record 相同的规则刷掉
        saved.add(new HistoryEntry("a".repeat(HistoryEntries.MAX_TEXT_LENGTH + 1), 1L));
        saved.add(new HistoryEntry("{\"a\":1}", 2L));

        entries.loadFrom(saved);

        assertEquals(1, entries.size());
        assertEquals("{\"a\":1}", entries.list().get(0).text);
    }

    @Test
    void loadFromNullClears() {
        HistoryEntries entries = new HistoryEntries();
        entries.record("{\"a\":1}", 1L);
        entries.loadFrom(null);
        assertEquals(0, entries.size());
    }

    @Test
    void listIsDefensiveCopy() {
        HistoryEntries entries = new HistoryEntries();
        entries.record("{\"a\":1}", 1L);
        entries.list().clear();
        assertEquals(1, entries.size());
    }

    @Test
    void clearEmptiesAll() {
        HistoryEntries entries = new HistoryEntries();
        entries.record("{\"a\":1}", 1L);
        entries.record("{\"b\":2}", 2L);
        entries.clear();
        assertEquals(0, entries.size());
    }
}
