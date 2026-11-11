package com.tidy.kit.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL 美化单测(脱离 IDE):黄金输出、token 无损硬判据、字符串/大小写不动、子查询与 CASE 缩进。
 */
class SqlBeautifierTest {

    @Test
    void clauseKeywordsStartNewLinesAndKeepCase() {
        assertEquals("select a\nfrom t\nwhere x = 1",
                SqlBeautifier.beautify("select a from t where x = 1"));
    }

    @Test
    void selectListCommasBreakLines() {
        assertEquals("SELECT a,\n  b,\n  c\nFROM t",
                SqlBeautifier.beautify("SELECT a, b, c FROM t"));
    }

    @Test
    void andOrIndentedUnderWhere() {
        assertEquals("SELECT 1\nFROM t\nWHERE a = 1\n  AND b = 2\n  OR c = 3",
                SqlBeautifier.beautify("SELECT 1 FROM t WHERE a = 1 AND b = 2 OR c = 3"));
    }

    @Test
    void joinAndOnHierarchy() {
        assertEquals("SELECT *\nFROM a\nLEFT JOIN b\n  ON a.id = b.id\n    AND b.x = 1",
                SqlBeautifier.beautify("SELECT * FROM a LEFT JOIN b ON a.id = b.id AND b.x = 1"));
    }

    @Test
    void subqueryParenIndentsInnerAndClosesOnOwnLine() {
        assertEquals("SELECT *\nFROM (\n  SELECT id\n  FROM t\n  WHERE x = 1\n) s",
                SqlBeautifier.beautify("SELECT * FROM (SELECT id FROM t WHERE x = 1) s"));
    }

    @Test
    void caseWhenElseEnd() {
        assertEquals("SELECT CASE\n  WHEN a = 1 THEN 'x'\n  ELSE 'y'\nEND v\nFROM t",
                SqlBeautifier.beautify("SELECT CASE WHEN a = 1 THEN 'x' ELSE 'y' END v FROM t"));
    }

    @Test
    void caseInsideSelectListIsDeeperThanColumnLine() {
        assertEquals("SELECT i1.id,\n  CASE\n    WHEN a = 1 THEN 'x'\n    ELSE 'y'\n  END target_ver\nFROM t",
                SqlBeautifier.beautify("SELECT i1.id, CASE WHEN a = 1 THEN 'x' ELSE 'y' END target_ver FROM t"));
    }

    @Test
    void inListCommasStayInline() {
        assertEquals("SELECT 1\nFROM t\nWHERE a IN (1, 2, 3)",
                SqlBeautifier.beautify("SELECT 1 FROM t WHERE a IN (1, 2, 3)"));
    }

    @Test
    void functionParenStaysGluedAndArgsInline() {
        assertEquals("SELECT ifnull(json_length(x) > 0, false) AS h\nFROM t",
                SqlBeautifier.beautify("SELECT ifnull(json_length(x) > 0,false) AS h FROM t"));
    }

    @Test
    void stringsAndBacktickIdentifiersUntouched() {
        assertEquals("select 'Hello  World'\nfrom `My Table`",
                SqlBeautifier.beautify("select 'Hello  World' from `My Table`"));
    }

    @Test
    void jsonOperatorPreserved() {
        assertEquals("SELECT a ->> '$.x'\nFROM t",
                SqlBeautifier.beautify("SELECT a ->> '$.x' FROM t"));
    }

    @Test
    void unionAllOnOwnLine() {
        assertEquals("SELECT 1\nUNION ALL\nSELECT 2",
                SqlBeautifier.beautify("SELECT 1 UNION ALL SELECT 2"));
    }

    @Test
    void lineCommentStaysOnOwnLine() {
        // 设计文档第 7 节:行注释独占一行
        assertEquals("SELECT 1\n-- note\nFROM t",
                SqlBeautifier.beautify("SELECT 1 -- note\nFROM t"));
    }

    @Test
    void emptyAndNullBecomeEmpty() {
        assertEquals("", SqlBeautifier.beautify(""));
        assertEquals("", SqlBeautifier.beautify(null));
        assertEquals("", SqlBeautifier.beautify("   "));
    }

    @Test
    void sample1GoldenOutput() {
        String input = "SELECT id, root_id, type_id, ver_no, name, code, should_issue, subj_type, "
                + "calc_subj_type, weight_scheme_id, num_listed, ord_no, remark, heat_ranking, heat_ranking_num "
                + "FROM ranking_instance WHERE root_id = 1 AND ver_no = 202610 AND deleted_at IS NULL "
                + "and code = '0701'";
        String expected = """
                SELECT id,
                  root_id,
                  type_id,
                  ver_no,
                  name,
                  code,
                  should_issue,
                  subj_type,
                  calc_subj_type,
                  weight_scheme_id,
                  num_listed,
                  ord_no,
                  remark,
                  heat_ranking,
                  heat_ranking_num
                FROM ranking_instance
                WHERE root_id = 1
                  AND ver_no = 202610
                  AND deleted_at IS NULL
                  and code = '0701'""";
        assertEquals(expected, SqlBeautifier.beautify(input));
    }

    /**
     * 硬判据:美化只改空白。去掉引号串/注释之外的所有空白后,输入与输出必须逐字符一致。
     * 样例 2 的完整 SQL(含 WITH/UNION ALL/CASE/->>/||/IN)作为最复杂的回归输入。
     */
    @Test
    void beautifyNeverChangesTokens() {
        String sample2 = """
                WITH i AS (
                  SELECT id, detail ->> '$.targetVer' target_ver FROM indicator_latest WHERE level = 3 AND deleted_at IS NULL
                  UNION ALL
                  SELECT i1.id, CASE WHEN i1.detail ->> '$.targetVer' = 'null' || i1.detail ->> '$.targetVer' = '' || i1.detail ->> '$.targetVer' IS NULL THEN i2.detail ->> '$.targetVer' ELSE i1.detail ->> '$.targetVer' END target_ver
                  FROM indicator_latest i1 LEFT JOIN indicator_latest i2 ON i1.pid = i2.id WHERE i1.level = 4 AND i1.deleted_at IS NULL
                )
                SELECT sc.id, ifnull(json_length(sc.var_details) > 0,false) as has_data FROM ind_value_latest sc
                JOIN i ON sc.ind_id = i.id AND sc.target_ver = i.target_ver
                LEFT JOIN ranking_type rt ON sc.r_leaf_id = rt.id
                WHERE sc.r_root_id = 1 AND sc.r_leaf_id = 141 AND sc.ind_id IN (95, 97) AND sc.univ_code IN ('RC00005') AND sc.deleted_at IS NULL""";
        String[] inputs = {
                sample2,
                "select a,b from t where x=? and y like '%?%'",
                "SELECT 1 /* block ? comment */ -- line ? comment\nFROM t",
                "SELECT `a?`, \"b?\" FROM t WHERE c = 'it''s ?'",
                "INSERT INTO t (a, b) VALUES (1, 2), (3, 4)",
                "UPDATE t SET a = 1, b = 2 WHERE id = 3",
        };
        for (String input : inputs) {
            assertEquals(normalize(input), normalize(SqlBeautifier.beautify(input)),
                    "美化改动了 token 内容: " + input);
        }
    }

    @Test
    void sample2StructureChecks() {
        String input = "WITH i AS (SELECT id FROM t WHERE level = 3 UNION ALL SELECT id FROM t2) "
                + "SELECT sc.id FROM sc JOIN i ON sc.id = i.id WHERE sc.ind_id IN (95, 97)";
        String pretty = SqlBeautifier.beautify(input);
        assertTrue(pretty.startsWith("WITH i AS (\n  SELECT id"));
        assertTrue(pretty.contains("\n  UNION ALL\n"));
        assertTrue(pretty.contains("\n) SELECT") || pretty.contains("\n)\nSELECT"));
        assertTrue(pretty.contains("IN (95, 97)"));
    }

    /** 去掉引号串/注释之外的所有空白,得到 token 无损判据的比较基准 */
    private static String normalize(String sql) {
        StringBuilder sb = new StringBuilder();
        for (SqlScan.Segment seg : SqlScan.segments(sql)) {
            if (seg.code()) {
                sb.append(seg.text().replaceAll("\\s+", ""));
            } else {
                sb.append(seg.text());
            }
        }
        return sb.toString();
    }
}