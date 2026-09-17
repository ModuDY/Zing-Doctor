package com.zing.doctor.quality.engine;

import com.zing.doctor.quality.dsl.FactDefinition;
import com.zing.doctor.quality.dsl.MetricDefinition;
import com.zing.doctor.quality.dsl.PatientFieldDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 患者明细「默认列」定义与出列 SQL 的单元测试（<b>不连数据库</b>）。
 *
 * <p><b>为什么必须测</b>：床号 / 诊断 / 入科 / 出科这四个扩展默认列是
 * 「事实层有该列才出现」的。这条设计一旦写错，后果不是显示难看，而是
 * <b>整份明细打不开</b> —— 缺列的事实层会直接报「无效的列名」，
 * 页面上只显示「无患者明细」，看不出是引擎把一个不存在的列拼进了 SQL。
 *
 * <p>另一个隐蔽点：固定列集合（{@link PatientColumns#baseColumns()}）<b>不能</b>包含扩展列。
 * 引擎靠这个集合决定「结果集里哪些列要剔出去、其余才算补充字段」——扩展列一旦被算成固定列，
 * 就会被剔除，前端按 key 取不到值，页面表现为整列空白（同样不报错）。
 */
class PatientColumnsTest {

    private final SqlCompiler compiler = new SqlCompiler();

    // ------------------------------------------------------------------
    // 定义本身
    // ------------------------------------------------------------------

    @Test
    @DisplayName("默认显示顺序：姓名 / 床号 / 住院号 / 诊断 / 入科时间 / 出科时间 / 入分子 / 入分母")
    void defaultOrder() {
        assertEquals(
                Arrays.asList("patientName", "bed_code", "inHospitalNo", "diagnosis_content",
                        "in_depart_time", "out_depart_time", "inNumerator", "inDenominator"),
                PatientColumns.defaultOrder());
    }

    @Test
    @DisplayName("默认列宽度：70 / 60 / 130 / 120 / 180 / 180 / 60 / 60")
    void defaultWidths() {
        assertEquals(70, PatientColumns.defaultWidth("patientName"));
        assertEquals(60, PatientColumns.defaultWidth("bed_code"));
        assertEquals(130, PatientColumns.defaultWidth("inHospitalNo"));
        assertEquals(120, PatientColumns.defaultWidth("diagnosis_content"));
        assertEquals(180, PatientColumns.defaultWidth("in_depart_time"));
        assertEquals(180, PatientColumns.defaultWidth("out_depart_time"));
        assertEquals(60, PatientColumns.defaultWidth("inNumerator"));
        assertEquals(60, PatientColumns.defaultWidth("inDenominator"));
    }

    @Test
    @DisplayName("患者ID / 科室 仍是保留 key，但不在默认显示顺序里")
    void patientIdAndDepartAreReservedButNotDefault() {
        assertTrue(PatientColumns.isReservedKey("patientId"));
        assertTrue(PatientColumns.isReservedKey("departCode"));
        assertFalse(PatientColumns.defaultOrder().contains("patientId"));
        assertFalse(PatientColumns.defaultOrder().contains("departCode"));
    }

    @Test
    @DisplayName("固定列集合不含扩展列 —— 含了它们就进不了 extras，前端整列取不到值")
    void baseColumnsExcludeExtended() {
        Set<String> base = PatientColumns.baseColumns();

        assertTrue(base.contains("patient_id"), "引擎固定列必须在固定列集合里");
        for (String col : PatientColumns.extended().keySet()) {
            assertFalse(base.contains(col), col + " 不该算固定列，否则会被剔出 extras");
        }
    }

    @Test
    @DisplayName("扩展列名算「已被默认列占用」—— 指标再配一次不能重复 select")
    void extendedColumnsCountAsReserved() {
        for (String col : PatientColumns.extended().keySet()) {
            assertTrue(PatientColumns.isReservedColumn(col), col + " 应被判为默认列");
            assertTrue(PatientColumns.isReservedColumn(col.toUpperCase()),
                    "达梦建表时别名会折成大写，比较必须忽略大小写");
            assertTrue(PatientColumns.isReservedKey(col), col + " 应是保留 key");
            assertTrue(PatientColumns.isExtendedKey(col), col + " 应是扩展列");
        }
    }

    // ------------------------------------------------------------------
    // 出列 SQL：事实层有才带出来
    // ------------------------------------------------------------------

    @Test
    @DisplayName("事实层有该列时，明细 SQL 里带出整列（MAX 聚合）")
    void selectsExtendedColumnWhenAvailable() {
        String sql = compile(available("patient_id", "in_depart_time"), fields());

        assertTrue(sql.contains("MAX(in_depart_time) AS in_depart_time"), sql);
    }

    @Test
    @DisplayName("事实层没有该列时整列跳过 —— 而不是拼进 SQL 让整份明细报「无效的列名」")
    void skipsExtendedColumnWhenMissing() {
        String sql = compile(available("patient_id"), fields());

        assertFalse(sql.contains("in_depart_time"), sql);
        assertFalse(sql.contains("bed_code"), sql);
        assertFalse(sql.contains("diagnosis_content"), sql);
    }

    @Test
    @DisplayName("指标把默认列当补充列配了，也不会重复 select（重复列名会让整份明细为空）")
    void configuredReservedColumnIsNotDuplicated() {
        // 前提：事实层确实有这两列，同时指标也把它们写进了 patient_fields ——
        // 只有两处都想要同一列时，才谈得上「会不会 select 两遍」
        String sql = compile(available("patient_id", "in_depart_time", "bed_code"),
                fields("in_depart_time", "bed_code"));

        assertEquals(1, countOf(sql, "AS in_depart_time"), sql);
        assertEquals(1, countOf(sql, "AS bed_code"), sql);
    }

    @Test
    @DisplayName("补充列正常带出，且同一列配两次只保留一个")
    void extraFieldsAreSelectedOnce() {
        String sql = compile(available("patient_id"), fields("gender", "gender", "age"));

        assertEquals(1, countOf(sql, "AS gender"), sql);
        assertEquals(1, countOf(sql, "AS age"), sql);
    }

    // ------------------------------------------------------------------
    // fixtures
    // ------------------------------------------------------------------

    private static Set<String> available(String... cols) {
        return new HashSet<>(Arrays.asList(cols));
    }

    private static List<PatientFieldDefinition> fields(String... keys) {
        List<PatientFieldDefinition> list = new ArrayList<>();
        for (String k : keys) {
            PatientFieldDefinition f = new PatientFieldDefinition();
            f.setKey(k);
            f.setLabel(k);
            list.add(f);
        }
        return list;
    }

    /**
     * 编译明细 SQL。
     *
     * <p>FactDefinition 只给空对象：引擎对未声明的键都有默认值（patient_id 等），
     * 本测试关心的是「扩展列带不带出来」，不关心键名映射。
     */
    private String compile(Set<String> availableColumns, List<PatientFieldDefinition> fields) {
        MetricDefinition m = new MetricDefinition();
        m.setPatientFields(fields);
        return compiler.compilePatients(m, "qc_fact_demo", new FactDefinition(), null, false,
                availableColumns);
    }

    private static int countOf(String text, String sub) {
        int count = 0;
        int i = 0;
        while ((i = text.indexOf(sub, i)) >= 0) {
            count++;
            i += sub.length();
        }
        return count;
    }
}
