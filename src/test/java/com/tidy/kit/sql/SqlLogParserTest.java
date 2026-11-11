package com.tidy.kit.sql;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL 日志提取单测(脱离 IDE):日志形态/裸 SQL 形态、反引号参数分组、误剥防护。
 * 样例与需求方提供的 GORM 调试日志一致(设计文档附录)。
 */
class SqlLogParserTest {

    @Test
    void extractsSqlAndParamsFromGormLog() throws SqlException {
        String log = """
                2026-11-11 15:08:37.588\t[DEBUG]\tdao/debug_sql.go:53\t[DAO] - [OK /  db.Select /    13.0ms] - [
                SELECT id, name FROM ranking_instance
                WHERE root_id = ? AND ver_no = ? and code = ? ] - `1`, `202610`, `"0701"`""";
        SqlLogParser.ParsedSql parsed = SqlLogParser.parse(log);
        assertEquals("SELECT id, name FROM ranking_instance\nWHERE root_id = ? AND ver_no = ? and code = ?",
                parsed.sql());
        assertEquals(List.of("1", "202610", "\"0701\""), parsed.rawParams());
    }

    @Test
    void sqlBodyMayContainClosingBracketInsideLiteral() throws SqlException {
        // SQL 里的 ](如 JSON 路径)不能干扰切分:从后往前找 "] - "
        String log = "[DAO] - [OK / db.Select / 1.0ms] - [ SELECT detail ->> '$.arr[0]' FROM t WHERE id = ? ] - `5`";
        SqlLogParser.ParsedSql parsed = SqlLogParser.parse(log);
        assertEquals("SELECT detail ->> '$.arr[0]' FROM t WHERE id = ?", parsed.sql());
        assertEquals(List.of("5"), parsed.rawParams());
    }

    @Test
    void logWithoutParamsEndsWithBracket() throws SqlException {
        String log = "[DAO] - [OK / db.Select / 1.0ms] - [ SELECT 1 ]";
        SqlLogParser.ParsedSql parsed = SqlLogParser.parse(log);
        assertEquals("SELECT 1", parsed.sql());
        assertEquals(List.of(), parsed.rawParams());
    }

    @Test
    void extractsAllSevenParamsFromSample2() throws SqlException {
        String log = """
                2026-11-11 15:08:37.616\t[DEBUG]\tdao/debug_sql.go:53\t[DAO] - [OK /  db.Select /    19.7ms] - [
                WITH i AS (SELECT id FROM indicator_latest WHERE level = ? AND deleted_at IS NULL)
                SELECT sc.id FROM ind_value_latest sc
                JOIN i ON sc.ind_id = i.id
                WHERE sc.r_root_id = ? AND sc.r_leaf_id = ?
                AND sc.ind_id IN (?, ?) AND sc.univ_code IN (?)
                AND sc.deleted_at IS NULL ] - `3`, `4`, `1`, `141`, `95`, `97`, `"RC00005"`""";
        SqlLogParser.ParsedSql parsed = SqlLogParser.parse(log);
        assertEquals(List.of("3", "4", "1", "141", "95", "97", "\"RC00005\""), parsed.rawParams());
        assertTrue(parsed.sql().startsWith("WITH i AS"));
        assertTrue(parsed.sql().endsWith("AND sc.deleted_at IS NULL"));
    }

    @Test
    void bareSqlWithOwnLineParams() throws SqlException {
        String input = "SELECT * FROM t WHERE id = ? AND code = ?\n`1`, `\"0701\"`";
        SqlLogParser.ParsedSql parsed = SqlLogParser.parse(input);
        assertEquals("SELECT * FROM t WHERE id = ? AND code = ?", parsed.sql());
        assertEquals(List.of("1", "\"0701\""), parsed.rawParams());
    }

    @Test
    void bareSqlSameLineParamsWhenCountMatches() throws SqlException {
        String input = "SELECT * FROM t WHERE id = ? `1`";
        SqlLogParser.ParsedSql parsed = SqlLogParser.parse(input);
        assertEquals("SELECT * FROM t WHERE id = ?", parsed.sql());
        assertEquals(List.of("1"), parsed.rawParams());
    }

    @Test
    void trailingBacktickIdentifierIsNotStripped() throws SqlException {
        // 结尾的反引号是标识符不是参数:没有占位符,组数也对不上,不能误剥
        String input = "SELECT `a` FROM `t`";
        SqlLogParser.ParsedSql parsed = SqlLogParser.parse(input);
        assertEquals(input, parsed.sql());
        assertEquals(List.of(), parsed.rawParams());
    }

    @Test
    void bareSqlWithoutParamsKeepsWholeInput() throws SqlException {
        String input = "SELECT * FROM t WHERE id = ?";
        SqlLogParser.ParsedSql parsed = SqlLogParser.parse(input);
        assertEquals(input, parsed.sql());
        assertEquals(List.of(), parsed.rawParams());
    }

    @Test
    void malformedParamListThrows() {
        String log = "[DAO] - [OK / db.Select / 1.0ms] - [ SELECT 1 FROM t WHERE a = ? ] - `1` xxx `2`";
        SqlException ex = assertThrows(SqlException.class, () -> SqlLogParser.parse(log));
        assertTrue(ex.getMessage().contains("无法解析参数列表"));
    }

    @Test
    void emptyInputThrows() {
        assertThrows(SqlException.class, () -> SqlLogParser.parse(""));
        assertThrows(SqlException.class, () -> SqlLogParser.parse("   \n "));
        assertThrows(SqlException.class, () -> SqlLogParser.parse(null));
    }
}