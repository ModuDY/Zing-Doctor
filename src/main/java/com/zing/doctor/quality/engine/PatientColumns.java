package com.zing.doctor.quality.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 患者明细的「默认列」定义。
 *
 * <p>明细的列有三个来源：
 * <ol>
 *   <li><b>固定列</b>：引擎从结果集直接读出的六列（姓名 / 住院号 / 患者ID / 科室 / 入分子 / 入分母），
 *       任何事实层都有；</li>
 *   <li><b>扩展默认列</b>：床号 / 诊断 / 入科时间 / 出科时间，取自事实层投影的列，
 *       <b>事实层有该列才出现</b>；</li>
 *   <li><b>指标自配的补充列</b>（性别、年龄…，取自事实层投影列）。</li>
 * </ol>
 *
 * <p>扩展默认列为什么是「有才出现」而不是写死：各事实层投影的字段并不相同 ——
 * 入科/出科时间多数事实层都有，床号只有床位事实层有，诊断在患者主表上根本没投影
 * （诊断是 patient_info_diagnosis 里的多行，事实层只用 EXISTS 判定，从不 select 出来）。
 * 若把列名写死进明细 SQL，缺列的事实层会整份报「无效的列名」、明细直接空掉，
 * 而那本来只是「这一列没数据」—— 为了一列把整张明细赔进去不值得。
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

    /** 固定列：保留 key（配置里写它表示「保留这个默认列」）→ 中文表头。 */
    private static final Map<String, String> BASE = new LinkedHashMap<>();

    /** 固定列在明细 SQL / 事实层里的实际列名（小写）。 */
    private static final Set<String> BASE_COLUMNS = new LinkedHashSet<>();

    /**
     * 扩展默认列：key → 中文表头。
     *
     * <p>key 直接用<b>事实层列名</b>而不是驼峰：这几列走的是补充列的取值路径
     * （结果集 → extras → 行），而达梦会把别名折成大写，驼峰 key 在这里取不到值。
     */
    private static final Map<String, String> EXTENDED = new LinkedHashMap<>();

    /** 全部保留 key = 固定列 + 扩展默认列，供配置页下拉与「是否保留 key」判定。 */
    private static final Map<String, String> RESERVED = new LinkedHashMap<>();

    /** 默认显示顺序（未配置时的出列顺序）。 */
    private static final List<String> DEFAULT_ORDER = new ArrayList<>();

    /** 默认列宽度（像素）；没列到的交给前端自适应。 */
    private static final Map<String, Integer> DEFAULT_WIDTH = new LinkedHashMap<>();

    static {
        BASE.put("patientName", "姓名");
        BASE.put("inHospitalNo", "住院号");
        BASE.put("patientId", "患者ID");
        BASE.put("departCode", "科室");
        BASE.put("inNumerator", "入分子");
        BASE.put("inDenominator", "入分母");
        BASE_COLUMNS.addAll(Arrays.asList(
                "patient_id", "in_hospital_no", "patient_name", "depart_code",
                "in_numerator", "in_denominator"));

        EXTENDED.put("bed_code", "床号");
        EXTENDED.put("diagnosis_content", "诊断");
        EXTENDED.put("in_depart_time", "入科时间");
        EXTENDED.put("out_depart_time", "出科时间");

        RESERVED.putAll(BASE);
        RESERVED.putAll(EXTENDED);

        // 顺序即页面显示顺序。患者ID / 科室 不在其中：
        // 它们仍是保留 key（老配置可能还引用着），只是默认不再各占一列。
        DEFAULT_ORDER.addAll(Arrays.asList(
                "patientName", "bed_code", "inHospitalNo", "diagnosis_content",
                "in_depart_time", "out_depart_time", "inNumerator", "inDenominator"));

        DEFAULT_WIDTH.put("patientName", 70);
        DEFAULT_WIDTH.put("bed_code", 60);
        DEFAULT_WIDTH.put("inHospitalNo", 130);
        DEFAULT_WIDTH.put("diagnosis_content", 120);
        DEFAULT_WIDTH.put("in_depart_time", 180);
        DEFAULT_WIDTH.put("out_depart_time", 180);
        DEFAULT_WIDTH.put("inNumerator", 60);
        DEFAULT_WIDTH.put("inDenominator", 60);
    }

    /** 全部保留 key → 表头。 */
    public static Map<String, String> reserved() {
        return Collections.unmodifiableMap(RESERVED);
    }

    /** 固定列 → 表头。 */
    public static Map<String, String> base() {
        return Collections.unmodifiableMap(BASE);
    }

    /** 扩展默认列 → 表头。 */
    public static Map<String, String> extended() {
        return Collections.unmodifiableMap(EXTENDED);
    }

    /** 默认显示顺序；出列与「配置页归一化的默认清单」都以此为准。 */
    public static List<String> defaultOrder() {
        return Collections.unmodifiableList(DEFAULT_ORDER);
    }

    /**
     * 固定列的列名（小写）：引擎据此把结果集里的固定列剔除，其余才算补充字段。
     *
     * <p>只包含固定列，<b>不含扩展默认列</b> —— 扩展列要留在 extras 里带出去，
     * 否则前端按 key 取不到值，页面就是一列空白。
     */
    public static Set<String> baseColumns() {
        return Collections.unmodifiableSet(BASE_COLUMNS);
    }

    /** 是否保留 key（大小写敏感：key 由前端固定传入）。 */
    public static boolean isReservedKey(String key) {
        return key != null && RESERVED.containsKey(key.trim());
    }

    /** 是否是「事实层有才出现」的扩展默认列。 */
    public static boolean isExtendedKey(String key) {
        return key != null && EXTENDED.containsKey(key.trim());
    }

    /**
     * 该列名是否已被默认列占用（大小写无关）。
     *
     * <p>达梦建事实表时未加引号的别名会折成大写，因此比较必须忽略大小写 ——
     * 否则 {@code PATIENT_ID} 会被当成一个合法的补充列放行，照样撞出重复列名。
     *
     * <p>扩展默认列也算占用：否则指标再配一次 {@code in_depart_time} 就会与默认列
     * 重复 select，内层子查询照样撞出两个同名列。
     */
    public static boolean isReservedColumn(String name) {
        if (name == null) {
            return false;
        }
        String n = name.trim().toLowerCase();
        return BASE_COLUMNS.contains(n) || EXTENDED.containsKey(n);
    }

    /** 默认列宽度；没有预设时返回 null（前端自适应）。 */
    public static Integer defaultWidth(String key) {
        return key == null ? null : DEFAULT_WIDTH.get(key.trim());
    }
}
