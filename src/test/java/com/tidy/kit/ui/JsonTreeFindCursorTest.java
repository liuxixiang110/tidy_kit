package com.tidy.kit.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 重复点"查找"时的游标推进(设计:JSON 格式化树查找与高亮 3.5)。
 * 只验取模回绕这一个纯函数;"跳到哪一行、滚不滚得动"是 Swing 的事,只能在沙盒里点。
 */
class JsonTreeFindCursorTest {

    @Test
    void advancesOneStepAtATime() {
        assertEquals(1, JsonTreePanel.nextCursor(0, 3));
        assertEquals(2, JsonTreePanel.nextCursor(1, 3));
    }

    /** 走到最后一处后再点回到第一处,这是这个功能的全部意义 */
    @Test
    void wrapsBackToFirstAfterLastHit() {
        assertEquals(0, JsonTreePanel.nextCursor(2, 3));
    }

    /** 只有一处命中:反复点都留在原地,不该跳到越界下标 */
    @Test
    void singleHitAlwaysStaysAtZero() {
        assertEquals(0, JsonTreePanel.nextCursor(0, 1));
    }

    /** 连点 7 次(新查询先落在第 0 处,所以是 6 次推进),要正好绕一圈回第 2 处 */
    @Test
    void keepsCyclingOverMultipleRounds() {
        int cursor = 0;
        for (int click = 0; click < 6; click++) {
            cursor = JsonTreePanel.nextCursor(cursor, 4);
        }
        assertEquals(2, cursor);
    }

    /** 推进结果必须始终落在 [0, total) 内,否则 findHits.get(游标) 会越界 */
    @Test
    void neverLeavesValidRange() {
        for (int total = 1; total <= 20; total++) {
            int cursor = 0;
            for (int click = 0; click < total * 3; click++) {
                cursor = JsonTreePanel.nextCursor(cursor, total);
                assertTrue(cursor >= 0 && cursor < total, "total=" + total + " cursor=" + cursor);
            }
        }
    }

    /** 还没查过(游标 -1)时推进应落在第一处,不能停在 -1 上越界 */
    @Test
    void unsearchedCursorLandsOnFirstHit() {
        assertEquals(0, JsonTreePanel.nextCursor(-1, 5));
    }
}
