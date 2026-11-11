package com.tidy.kit.sql;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 占位符回填单测(脱离 IDE):类型渲染表、单引号转义、字面量/注释里的 ? 不替换、数量校验。
 */
class SqlParameterBinderTest {

    @Test
    void replacesPlaceholdersInOrder() throws SqlException {
        assertEquals("a = 1 AND b = 'x'",
                SqlParameterBinder.bind("a = ? AND b = ?", List.of("1", "\"x\"")));
    }

    @Test
    void rendersNumbersAsIs() {
        assertEquals("1", SqlParameterBinder.render("1"));
        assertEquals("202610", SqlParameterBinder.render("202610"));
        assertEquals("-3.5", SqlParameterBinder.render("-3.5"));
        assertEquals(".5", SqlParameterBinder.render(".5"));
        assertEquals("1e5", SqlParameterBinder.render("1e5"));
        assertEquals("+2", SqlParameterBinder.render("+2"));
    }

    @Test
    void doubleQuotedParamBecomesSingleQuotedString() {
        assertEquals("'0701'", SqlParameterBinder.render("\"0701\""));
        assertEquals("'RC00005'", SqlParameterBinder.render("\"RC00005\""));
    }

    @Test
    void singleQuoteInsideStringIsDoubled() throws SqlException {
        assertEquals("name = 'O''Brien'",
                SqlParameterBinder.bind("name = ?", List.of("\"O'Brien\"")));
        // 未加双引号的兜底字符串同样要转义
        assertEquals("name = 'O''Brien'",
                SqlParameterBinder.bind("name = ?", List.of("O'Brien")));
    }

    @Test
    void unquotedNonNumericFallsBackToString() {
        assertEquals("'2026-11-11 15:08:37'", SqlParameterBinder.render("2026-11-11 15:08:37"));
        assertEquals("'abc'", SqlParameterBinder.render("abc"));
    }

    @Test
    void nullTrueFalseStayAsIs() {
        assertEquals("null", SqlParameterBinder.render("null"));
        assertEquals("TRUE", SqlParameterBinder.render("TRUE"));
        assertEquals("false", SqlParameterBinder.render("false"));
    }

    @Test
    void gormNilBecomesNull() {
        assertEquals("null", SqlParameterBinder.render("<nil>"));
    }

    @Test
    void emptyParamBecomesEmptyString() {
        assertEquals("''", SqlParameterBinder.render(""));
    }

    @Test
    void goStyleEscapesAreUnescaped() {
        // 参数原文:a\"b\\c\nd → 还原 a"b\c换行d → SQL 单引号串
        assertEquals("'a\"b\\c\nd'", SqlParameterBinder.render("\"a\\\"b\\\\c\\nd\""));
    }

    @Test
    void questionMarkInsideStringLiteralIsNotPlaceholder() throws SqlException {
        assertEquals("SELECT '$.x?y', 1",
                SqlParameterBinder.bind("SELECT '$.x?y', ?", List.of("1")));
        assertEquals("SELECT \"a?b\", 1",
                SqlParameterBinder.bind("SELECT \"a?b\", ?", List.of("1")));
        assertEquals("SELECT `col?`, 1",
                SqlParameterBinder.bind("SELECT `col?`, ?", List.of("1")));
    }

    @Test
    void questionMarkInsideCommentsIsNotPlaceholder() throws SqlException {
        assertEquals("SELECT 1 -- ?\nWHERE a = 2",
                SqlParameterBinder.bind("SELECT 1 -- ?\nWHERE a = ?", List.of("2")));
        assertEquals("SELECT /* ? */ 1 WHERE a = 2",
                SqlParameterBinder.bind("SELECT /* ? */ 1 WHERE a = ?", List.of("2")));
        assertEquals("SELECT 1 # ?\nWHERE a = 2",
                SqlParameterBinder.bind("SELECT 1 # ?\nWHERE a = ?", List.of("2")));
    }

    @Test
    void tooFewParamsThrowsWithBothCounts() {
        SqlException ex = assertThrows(SqlException.class,
                () -> SqlParameterBinder.bind("a = ? AND b = ?", List.of("1")));
        assertTrue(ex.getMessage().contains("2 个占位符"));
        assertTrue(ex.getMessage().contains("1 个"));
    }

    @Test
    void tooManyParamsThrows() {
        assertThrows(SqlException.class,
                () -> SqlParameterBinder.bind("a = ?", List.of("1", "2")));
    }

    @Test
    void noPlaceholdersNoParamsIsLegal() throws SqlException {
        assertEquals("SELECT 1", SqlParameterBinder.bind("SELECT 1", List.of()));
    }

    @Test
    void sample2EndToEndBinding() throws SqlException {
        String log = """
                2026-11-11 15:08:37.616\t[DEBUG]\tdao/debug_sql.go:53\t[DAO] - [OK /  db.Select /    19.7ms] - [
                WITH i AS (SELECT id FROM indicator_latest WHERE level = ? AND deleted_at IS NULL)
                SELECT sc.id FROM ind_value_latest sc
                JOIN i ON sc.ind_id = i.id AND i.level = ?
                WHERE sc.r_root_id = ? AND sc.r_leaf_id = ?
                AND sc.ind_id IN (?, ?) AND sc.univ_code IN (?)
                AND sc.deleted_at IS NULL ] - `3`, `4`, `1`, `141`, `95`, `97`, `"RC00005"`""";
        SqlLogParser.ParsedSql parsed = SqlLogParser.parse(log);
        String bound = SqlParameterBinder.bind(parsed.sql(), parsed.rawParams());
        assertTrue(bound.contains("level = 3"));
        assertTrue(bound.contains("sc.r_root_id = 1"));
        assertTrue(bound.contains("sc.r_leaf_id = 141"));
        assertTrue(bound.contains("IN (95, 97)"));
        assertTrue(bound.contains("IN ('RC00005')"));
        assertTrue(!bound.contains("?"));
    }
}