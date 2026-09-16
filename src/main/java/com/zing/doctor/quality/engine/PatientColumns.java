package com.zing.doctor.quality.engine;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 患者明细的「默认列」定义。
 *
 * <p>明细的列有两个来源：
 * <ol>
 *   <li>引擎固定产出的默认列（姓名 / 住院号 / 患者ID / 科室 / 入分子 / 入分母）；</li>
 *   <li>指标自配的补充列（性别、年龄…，取自事实层投影列）。</li>
 * </ol>
 *
 * <p>两边都会往明细 SQL 的 select 里放列，于是存在一个隐蔽的冲突：配置页的字段下拉
 * 里恰好能选到 {@code patient_id}、{@code depart_code} 这些名字，用户一旦选上，
 * 内层子查询就出现两个同名列，外层 {@code SELECT *} 直接报「列名不明确」——
 * 整份明细返回空，而页面只显示「无患者明细」，完全看不出是配置写错了。
 *
 * <p>因此把口径集中在这里：编译 SQL 时据此跳过重复列、保存校验时据此提示、
 * 出列定义时据此渲染，三处引用的必须是同一份常量。
 */
public final class PatientColumns {

    private PatientColumns() {
    }

    /**
     * 保留 key（配置里写它表示「保留这个默认列」）→ 中文表头。
     *
     * <p>顺序即默认显示顺序；{@code LinkedHashMap} 保证遍历顺序稳定，
     * 也让「没配过的指标」和「配过但没动默认列的指标」渲染出完全相同的表头。
     */
    private static final Map<String, String> RESERVED = new LinkedHashMap<>();

    /** 默认列在明细 SQL / 事实层里的实际列名（小写），补充列与它们重名时必须让路。 */
    private static final Set<String> RESERVED_COLUMNS = new LinkedHashSet<>();

    static {
        RESERVED.put("patientName", "姓名");
        RESERVED.put("inHospitalNo", "住院号");
        RESERVED.put("patientId", "患者ID");
        RESERVED.put("departCode", "科室");
        RESERVED.put("inNumerator", "入分子");
        RESERVED.put("inDenominator", "入分母");
        RESERVED_COLUMNS.addAll(Arrays.asList(
                "patient_id", "in_hospital_no", "patient_name", "depart_code",
                "in_numerator", "in_denominator"));
    }

    /** 保留 key → 表头，保持默认顺序。 */
    public static Map<String, String> reserved() {
        return Collections.unmodifiableMap(RESERVED);
    }

    /** 是否保留 key（大小写敏感：key 由前端固定传入）。 */
    public static boolean isReservedKey(String key) {
        return key != null && RESERVED.containsKey(key.trim());
    }

    /**
     * 该列名是否已被默认列占用（大小写无关）。
     *
     * <p>达梦建事实表时未加引号的别名会折成大写，因此比较必须忽略大小写 ——
     * 否则 {@code PATIENT_ID} 会被当成一个合法的补充列放行，照样撞出重复列名。
     */
    public static boolean isReservedColumn(String name) {
        return name != null && RESERVED_COLUMNS.contains(name.trim().toLowerCase());
    }

    /** 默认列名集合（小写），供引擎从结果集里剔除默认列时使用。 */
    public static Set<String> reservedColumns() {
        return Collections.unmodifiableSet(RESERVED_COLUMNS);
    }
}
