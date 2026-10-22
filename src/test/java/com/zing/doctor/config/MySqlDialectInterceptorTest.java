package com.zing.doctor.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 达梦 → MySQL/MariaDB 方言翻译的回归测试。
 *
 * <p>为什么要有它：这套 SQL 是为达梦写的，MySQL 环境下靠运行时翻译兜底，
 * 而<b>翻译漏了只会在生产页面报错</b>（如 {@code Unknown column 'SYSDATE'}、
 * {@code Table 'xxx.ALL_TAB_COLUMNS' doesn't exist}），编译期毫无征兆。
 * 现场已经踩过两次，故把用例固化下来。
 *
 * <p>关键前提：MyBatis 的 BoundSql 里 {@code #{param}} 已变成 {@code ?}，
 * 所以断言一律用 {@code ?} 形式——用 {@code #{}} 写用例会「测试通过、线上照错」。
 */
class MySqlDialectInterceptorTest {

    @Test
    @DisplayName("SYSDATE：- 数字（天）、- (? / 24.0)（小时）、裸 SYSDATE 都要翻译")
    void translateSysdate() {
        assertEquals("AND li.check_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)",
                MySqlDialectInterceptor.translate("AND li.check_time >= SYSDATE - 7"));

        assertEquals("AND pi.in_depart_time <= DATE_SUB(NOW(), INTERVAL ? HOUR)",
                MySqlDialectInterceptor.translate("AND pi.in_depart_time <= SYSDATE - (? / 24.0)"));

        assertEquals("AND t.create_time > NOW()",
                MySqlDialectInterceptor.translate("AND t.create_time > SYSDATE"));
    }

    @Test
    @DisplayName("DATEDIFF(单位, a, b) 达梦写法要变成 TIMESTAMPDIFF")
    void translateDatediffWithUnit() {
        String dm = "CASE WHEN DATEDIFF(HOUR, pi.in_depart_time, pi.out_depart_time) > 48 THEN 1 ELSE 0 END";
        String my = MySqlDialectInterceptor.translate(dm);
        assertTrue(my.startsWith("CASE WHEN TIMESTAMPDIFF(HOUR, pi.in_depart_time, pi.out_depart_time) > 48"),
                "实际： " + my);
        assertFalse(my.contains("DATEDIFF("));
    }

    @Test
    @DisplayName("REGEXP_LIKE(x, 'p') → (x REGEXP 'p')，含 TRIM 等函数参数")
    void translateRegexpLike() {
        assertEquals("CASE WHEN (pi.bed_code REGEXP '^[A-Za-z]') THEN 0 ELSE 1 END",
                MySqlDialectInterceptor.translate("CASE WHEN REGEXP_LIKE(pi.bed_code, '^[A-Za-z]') THEN 0 ELSE 1 END"));

        assertEquals("CASE WHEN (TRIM(pi.age) REGEXP '^[0-9]{1,3}$') THEN CAST(TRIM(pi.age) AS SIGNED) END",
                MySqlDialectInterceptor.translate(
                        "CASE WHEN REGEXP_LIKE(TRIM(pi.age), '^[0-9]{1,3}$') THEN CAST(TRIM(pi.age) AS INT) END"));
    }

    @Test
    @DisplayName("CAST AS INT/INTEGER → AS SIGNED（MariaDB 不认 AS INT）")
    void translateCastInt() {
        assertEquals("CAST(x AS SIGNED)", MySqlDialectInterceptor.translate("CAST(x AS INT)"));
        assertEquals("CAST(x AS SIGNED)", MySqlDialectInterceptor.translate("CAST(x AS INTEGER)"));
        // 不能误伤 AS INTERVAL / AS INTxxx
        assertEquals("CAST(x AS INTERVAL DAY)", MySqlDialectInterceptor.translate("CAST(x AS INTERVAL DAY)"));
    }

    @Test
    @DisplayName("数据字典：ALL_TAB_COLUMNS/OWNER/DATA_LENGTH → information_schema")
    void translateDataDictionary() {
        String dm = "SELECT COUNT(*) FROM ALL_TAB_COLUMNS "
                + "WHERE UPPER(OWNER) = 'ZING_ICU_DB_PROD' AND UPPER(TABLE_NAME) = 'PATIENT_INFO' "
                + "AND UPPER(COLUMN_NAME) = ?";
        String my = MySqlDialectInterceptor.translate(dm);
        assertEquals("SELECT COUNT(*) FROM information_schema.COLUMNS "
                + "WHERE UPPER(TABLE_SCHEMA) = 'ZING_ICU_DB_PROD' AND UPPER(TABLE_NAME) = 'PATIENT_INFO' "
                + "AND UPPER(COLUMN_NAME) = ?", my);

        assertEquals("SELECT CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS WHERE UPPER(TABLE_SCHEMA) = 'X'",
                MySqlDialectInterceptor.translate(
                        "SELECT DATA_LENGTH FROM ALL_TAB_COLUMNS WHERE UPPER(OWNER) = 'X'"));
    }

    @Test
    @DisplayName("ROWNUM 取前 N 条：子查询包装与 WHERE 内两种写法")
    void translateRownum() {
        assertEquals("FROM (SELECT 1 FROM t ORDER BY id DESC) t LIMIT 1",
                MySqlDialectInterceptor.translate("FROM (SELECT 1 FROM t ORDER BY id DESC) WHERE ROWNUM <= 1"));

        assertEquals("SELECT * FROM t WHERE a = 1 LIMIT 10",
                MySqlDialectInterceptor.translate("SELECT * FROM t WHERE a = 1 AND ROWNUM <= 10"));
    }

    @Test
    @DisplayName("不相关 SQL 原样返回（避免翻译器引入新问题）")
    void leaveOthersUntouched() {
        String sql = "SELECT id, name FROM patient_info WHERE del_flag = 0 AND name LIKE ?";
        assertEquals(sql, MySqlDialectInterceptor.translate(sql));
    }

    @Test
    @DisplayName("CREATE TABLE AS SELECT（事实层物化）里的达梦写法必须整句翻译")
    void translateCreateTableAsSelect() {
        // 现场故障：CTAS 走 QualitySqlMapper.execute（@Update）→ Executor.update，
        // 而拦截器原先只挂 query，DATEDIFF(DAY, …) 原样打到 MariaDB 报 1582。
        String dm = "CREATE TABLE \"zing_doctor_db_prod\".\"quality_fact_x\" AS SELECT "
                + "DATEDIFF(DAY, a.in_time, a.out_time) AS stay_days, "
                + "DATEDIFF(MINUTE, a.in_time, b.check_time) AS first_min, "
                + "CASE WHEN REGEXP_LIKE(TRIM(a.age), '^[0-9]{1,3}$') THEN CAST(TRIM(a.age) AS INT) END AS age_num, "
                + "CAST(a.diag AS VARCHAR(2000)) AS diag "
                + "FROM \"zing_icu_db_prod\".\"patient_info\" a";
        String my = MySqlDialectInterceptor.translate(dm);
        assertFalse(my.contains("DATEDIFF("), "仍残留达梦 DATEDIFF: " + my);
        assertTrue(my.contains("TIMESTAMPDIFF(DAY, a.in_time, a.out_time)"), "实际： " + my);
        assertTrue(my.contains("TIMESTAMPDIFF(MINUTE, a.in_time, b.check_time)"), "实际： " + my);
        assertTrue(my.contains("(TRIM(a.age) REGEXP '^[0-9]{1,3}$')"), "实际： " + my);
        assertTrue(my.contains("AS CHAR(2000)"), "实际： " + my);
    }
}
