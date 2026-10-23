package com.zing.doctor.module.labtrend.service.impl;

import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.module.config.dataitem.entity.DataItemConfig;
import com.zing.doctor.module.config.dataitem.service.DataItemConfigService;
import com.zing.doctor.module.labtrend.dto.LabTrendResponse;
import com.zing.doctor.module.labtrend.service.LabTrendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检验趋势实现（肝功能 / 凝血 / 肾功能与电解质）。
 *
 * <p>项目口径来源：
 * <ul>
 *   <li>肝功能、凝血：docs/25 生产库核查（近 90 天实测覆盖）：ALT 丙氨酸氨基转移酶 /
 *       AST <b>天冬氨酸氨基转氨酶</b> / 总胆红素 / 直接胆红素 / 间接胆红素 / 白蛋白 /
 *       碱性磷酸酶 / γ-谷氨酰转肽酶；血小板计数 / PT / INR / APTT / 纤维蛋白原 /
 *       D-二聚体（仅 ICU 床边项目、低覆盖）；</li>
 *   <li>肾功能与电解质：达梦生产库（172.88.1.184:34567，schema {@code zing_icu_db_prod}）
 *       近 90 天实测核查（2026-10-14，见 docs/25 §9）—— 血生化八项
 *       {@code *肌酐 *尿素 *钾 *钠 *氯 *钙 *镁 *磷}（lis_item_code 100210 / 100200 / 100140~100190）
 *       各覆盖 3.6~4.3 万患者；血气侧另有第二套命名 {@code 钾离子 / 钠离子 / 氯离子浓度 / 离子钙}
 *       （各约 3,382 患者）与 {@code 钙离子浓度(PH7.4)}（321 患者），一并纳入。
 *       电解质组含镁、磷：与血生化 K/Na/Cl/Ca/Mg/P 是同一面板（编码连号 100140~100190）。</li>
 * </ul>
 *
 * <p><b>按名称匹配而非只按 code</b>：核查证实 ALT 有第二套编码（约 5,200 患者），
 * 只按 code 100080 查会漏掉新系统数据；项目名普遍带 {@code *} 前缀（异常标记），用包含匹配。
 *
 * <p><b>组合项与同名异物逐项排除</b>：单字段含两个值的组合项（如"35/28"）拆分规则未经临床确认；
 * 名称包含匹配又是一把宽口子 —— 生产库核查（docs/25 §9）实测到这些误伤，
 * 全部按项目名显式挡掉，否则趋势图里会混进完全不同项目的数值，而且看起来完全正常：
 * <ul>
 *   <li>肌酐 ← {@code 尿素/肌酐}（比值）、{@code 内生肌酐清除率}、{@code 尿肌酐}、{@code 24小时腹透液肌酐}；</li>
 *   <li>尿素 ← {@code 尿素/肌酐}、{@code 尿素呼气试验}、{@code 24小时腹透液尿素}；</li>
 *   <li>钠 ← {@code N端-B型钠尿肽前体(NT-ProBNP)}、{@code 血清药物浓度测定(苯妥英钠)}；</li>
 *   <li>氯 ← {@code 氯氮平}、{@code 氯胺酮}（药物浓度，非血氯）、{@code 氯化物测定}（无单位，数值跨血清/脑脊液区间，标本不可判定）；</li>
 *   <li>钙 ← {@code 降钙素原} / {@code 降钙素}（PCT 与降钙素）、{@code 肌钙蛋白}（含「肌钙」）、
 *       {@code 钙卫蛋白}、{@code N端骨钙素}、{@code 草酸钙/磷酸钙/碳酸钙结晶}；</li>
 *   <li>镁 / 磷 ← {@code 磷酸铵镁结晶}、{@code 抗心磷脂抗体}、{@code 血清骨型碱性磷酸酶}、
 *       {@code 6-磷酸脱氢酶}、{@code 磷酸化tau-181蛋白} 等含「磷酸」「磷脂」的项目；</li>
 *   <li>八项共同的 {@code 微量元素-*}（ICP-MS 独立面板，参考区间与生化法不同）与各类尿液标本。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LabTrendServiceImpl implements LabTrendService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final IcuPatientMapper icuPatientMapper;
    private final IcuPatientService icuPatientService;
    /** 项目名关键字与排除词已抽到配置（domain=LAB），下面四个静态表只是兜底 */
    private final DataItemConfigService dataItemConfigService;

    /**
     * 项目定义：{@code {项目名关键词, 展示名, 排除词(可选, 用 | 分隔)}}。
     *
     * <p>第三列只在名称包含匹配会误伤时才写：见类注释里的同名异物清单。
     */
    private static final LinkedHashMap<String, String[][]> LIVER = new LinkedHashMap<>();
    private static final String[][] COAG = {
            {"血小板计数", "PLT 血小板计数"},
            {"凝血酶原时间", "PT 凝血酶原时间"},
            {"凝血酶原国际比值", "INR"},
            {"活化部分凝血活酶", "APTT"},
            {"纤维蛋白原", "Fib 纤维蛋白原"},
            {"D-二聚体", "D-二聚体"},
    };

    /** 肾功能与电解质：组名 → 项目定义。 */
    private static final LinkedHashMap<String, String[][]> RENAL = new LinkedHashMap<>();

    /** 感染指标（院感分析）：白细胞 / 降钙素原 / C反应蛋白 / 白介素6。 */
    private static final String[][] INFECTION = {
            // 排除：各类尿液/体液标本白细胞（血常规才是感染判读口径）
            {"白细胞", "WBC 白细胞", "尿|脊液|胸水|腹水|关节液|精"},
            // 降钙素原无第二套命名，直接按名匹配（肾功能面板的「钙」已排除它，此处正相反）
            {"降钙素原", "PCT 降钙素原"},
            {"C反应蛋白", "CRP C反应蛋白", "超敏"},
            {"白介素6", "IL-6 白介素6"},
    };

    static {
        LIVER.put("肝细胞损伤", new String[][]{
                {"丙氨酸氨基转移酶", "ALT 丙氨酸氨基转移酶"},
                {"天冬氨酸氨基转氨酶", "AST 天冬氨酸氨基转氨酶"},
        });
        LIVER.put("胆红素", new String[][]{
                {"总胆红素", "TBIL 总胆红素"},
                {"直接胆红素", "DBIL 直接胆红素"},
                {"间接胆红素", "IBIL 间接胆红素"},
        });
        LIVER.put("合成功能", new String[][]{
                {"白蛋白", "ALB 白蛋白"},
        });
        LIVER.put("胆道 / 酶学", new String[][]{
                {"碱性磷酸酶", "ALP 碱性磷酸酶"},
                {"谷氨酰转肽酶", "GGT γ-谷氨酰转肽酶"},
        });

        RENAL.put("肾小球滤过功能", new String[][]{
                // 排除：尿素/肌酐比值（单字段双值）、清除率类（计算值不是检验结果）、
                // 尿肌酐与腹透液肌酐（标本不同）
                {"肌酐", "Cr 肌酐", "尿素/肌酐|尿素／肌酐|清除率|腹透液|透析液|尿"},
                // 排除：幽门螺杆菌尿素呼气试验、24小时腹透液尿素（标本不同）。
                // 注意这里不能像钾/钠那样直接排除「尿」字：血尿素的项目名本身就可能写作「尿素」，
                // 一刀切会把要的项目也挡掉。
                {"尿素", "BUN 尿素氮", "尿素/肌酐|尿素／肌酐|呼气|腹透液|透析液|尿尿素|小时尿"},
        });
        RENAL.put("电解质", new String[][]{
                // 钾 / 钠 / 氯 / 钙 的第二套命名（血气侧：钾离子 / 钠离子 / 氯离子浓度 / 离子钙）
                // 都含同一个关键词，天然一并纳入，无需额外配置
                {"钾", "K⁺ 钾", "腹透液|透析液|微量元素|尿"},
                // 排除：脑钠肽 / 钠尿肽（BNP）、含「钠」的药物浓度（苯妥英钠）
                {"钠", "Na⁺ 钠", "腹透液|透析液|微量元素|钠肽|BNP|前体|苯妥英钠|药物浓度|尿"},
                // 排除：含「氯」的药物（氯氮平 / 氯胺酮）、氯化物测定（标本不可判定）
                {"氯", "Cl⁻ 氯", "腹透液|透析液|微量元素|氯化物|氯氮平|氯胺酮|尿"},
                // 排除：降钙素原 PCT / 降钙素、肌钙蛋白、钙卫蛋白、骨钙素、钙盐结晶
                {"钙", "Ca²⁺ 钙", "肌钙蛋白|降钙素|钙卫蛋白|骨钙素|微量元素|结晶|尿"},
                {"镁", "Mg²⁺ 镁", "微量元素|结晶|磷酸铵镁|尿"},
                // 「磷酸」一刀切：血磷的项目名就是「*磷」，含「磷酸」的都是别的项目
                {"磷", "P 磷", "微量元素|磷酸|结晶|磷脂|尿"},
        });
    }

    @Override
    public LabTrendResponse trend(String patientId, String type, int hours) {
        int range = Math.max(1, Math.min(hours, 720));
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusHours(range);
        LabTrendResponse resp = new LabTrendResponse();
        resp.setPatientId(patientId);
        resp.setRangeStart(start.format(ISO));
        resp.setRangeEnd(end.format(ISO));

        WorkbenchPatient p = icuPatientService.getWorkbenchPatientAny(patientId);
        if (p != null) {
            resp.setPatientName(p.getName());
            resp.setInHospitalNo(p.getInHospitalNo());
        }
        String inHospitalNo = p == null ? null : p.getInHospitalNo();

        LinkedHashMap<String, String[][]> groups = groupsOf(type);

        // DB 端按目标项目关键词过滤取数，不再先拉全项目再 Java 筛
        List<String> keywords = new ArrayList<>();
        for (String[][] items : groups.values()) {
            for (String[] it : items) {
                keywords.add(it[0]);
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        boolean queryFailed = false;
        if (inHospitalNo == null || inHospitalNo.isEmpty()) {
            queryFailed = true;
            log.warn("检验趋势：未取到住院号 patientId={}", patientId);
        } else {
            try {
                rows = icuPatientMapper.selectLabsByKeywordsAndRange(
                        inHospitalNo, start.format(ISO), end.format(ISO), keywords);
            } catch (Exception e) {
                queryFailed = true;
                log.warn("检验明细查询失败 patientId={} inHospitalNo={}", patientId, inHospitalNo, e);
            }
        }

        for (Map.Entry<String, String[][]> e : groups.entrySet()) {
            LabTrendResponse.Group g = new LabTrendResponse.Group();
            g.setName(e.getKey());
            // 同显示名的多个关键字合并成一条曲线（一个项目多套命名时不能画成两条同名线）
            Map<String, LabTrendResponse.Series> byName = new LinkedHashMap<>();
            for (String[] item : e.getValue()) {
                String display = item[1] == null || item[1].isEmpty() ? item[0] : item[1];
                LabTrendResponse.Series s = byName.get(display);
                if (s == null) {
                    s = new LabTrendResponse.Series();
                    s.setName(display);
                    byName.put(display, s);
                    g.getSeries().add(s);
                }
                if (!queryFailed) {
                    appendPoints(s, item, rows, resp);
                }
            }
            for (LabTrendResponse.Series s : g.getSeries()) {
                finalizeSeries(s, queryFailed);
            }
            resp.getGroups().add(g);
        }
        resp.setRecordCount(resp.getRecords().size());
        return resp;
    }

    /**
     * 面板类型 → 分组定义：优先读配置表（/page/data-item-config 的「检验项目」Tab），
     * 该面板没有任何配置时回退内置定义 —— 保证未建表的老现场行为完全不变。
     */
    private LinkedHashMap<String, String[][]> groupsOf(String type) {
        LinkedHashMap<String, String[][]> fromConfig = groupsFromConfig(type);
        return fromConfig != null ? fromConfig : builtinGroups(type);
    }

    /** 内置定义（配置表为空时的兜底，与 sql/66_config_data_item.sql 的 LAB 初始化数据同源）。 */
    private LinkedHashMap<String, String[][]> builtinGroups(String type) {
        if ("coag".equals(type)) {
            return singleGroup("凝血功能", COAG);
        }
        if ("renal".equals(type)) {
            return RENAL;
        }
        if ("infection".equals(type)) {
            return singleGroup("感染指标", INFECTION);
        }
        return LIVER;
    }

    /**
     * 从配置组装分组。
     *
     * <p>group_code 选面板（LIVER / COAG / RENAL / INFECTION），group_name 是面板内的分组标题
     * （肝功能下面就有「肝细胞损伤 / 胆红素 / 合成功能 / 胆道 酶学」四个），sort_no 决定项目顺序。
     *
     * <p>一行一个关键字；同一项目配了多行（本院 LIS 里 ALT 同时叫「丙氨酸氨基转移酶」和
     * 「谷丙转氨酶」）会按显示名合并成一条曲线，而不是在图上出现两条同名线。
     */
    private LinkedHashMap<String, String[][]> groupsFromConfig(String type) {
        List<DataItemConfig> rows = dataItemConfigService.enabled(DataItemConfigService.DOMAIN_LAB);
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        String groupCode = groupCodeOf(type);
        LinkedHashMap<String, List<String[]>> tmp = new LinkedHashMap<>();
        for (DataItemConfig row : rows) {
            if (row == null || !groupCode.equalsIgnoreCase(trim(row.getGroupCode()))) {
                continue;
            }
            String value = trim(row.getMatchValue());
            if (value.isEmpty()) {
                continue;
            }
            String display = trim(row.getItemName());
            if (display.isEmpty()) {
                display = value;
            }
            String groupName = trim(row.getGroupName());
            if (groupName.isEmpty()) {
                groupName = groupCode;
            }
            String excludes = trim(row.getExcludeValue());
            String matchType = trim(row.getMatchType());
            for (String one : DataItemConfigService.split(value)) {
                tmp.computeIfAbsent(groupName, k -> new ArrayList<>())
                        .add(new String[]{one, display, excludes, matchType});
            }
        }
        if (tmp.isEmpty()) {
            return null;
        }
        LinkedHashMap<String, String[][]> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String[]>> e : tmp.entrySet()) {
            out.put(e.getKey(), e.getValue().toArray(new String[0][]));
        }
        return out;
    }

    private static String groupCodeOf(String type) {
        if ("coag".equals(type)) {
            return "COAG";
        }
        if ("renal".equals(type)) {
            return "RENAL";
        }
        if ("infection".equals(type)) {
            return "INFECTION";
        }
        return "LIVER";
    }

    private LinkedHashMap<String, String[][]> singleGroup(String name, String[][] items) {
        LinkedHashMap<String, String[][]> m = new LinkedHashMap<>();
        m.put(name, items);
        return m;
    }

    /**
     * 该项目名是否属于「看着像、其实不是」的排除项。
     *
     * <p>两类：组合项（单字段双值，拆分规则未确认）与同名异物（PCT 含「钙」、BNP 含「钠」、
     * 尿素/肌酐含「肌酐」、尿液标本含「钾」等）。判错的后果是趋势图里混入别的项目数值，
     * 曲线本身看不出异常，所以这里宁可少匹配也不宽收。
     */
    private static boolean isExcluded(String itemName, String[] item) {
        if (itemName.contains("谷草/谷丙") || itemName.contains("谷草／谷丙")) {
            return true;
        }
        if (item.length <= 2 || item[2] == null || item[2].isEmpty()) {
            return false;
        }
        for (String bad : item[2].split("\\|")) {
            if (!bad.isEmpty() && itemName.contains(bad)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 项目名是否命中该定义。
     *
     * <p>NAME_EXACT 要求全等；CODE / NAME_LIKE（默认）按包含匹配 —— LIS 项目名普遍带
     * {@code *} 异常标记前缀，全等匹配会一条都取不到。
     */
    private static boolean matches(String itemName, String[] item) {
        String value = item[0];
        if (value == null || value.isEmpty()) {
            return false;
        }
        String mode = item.length > 3 && item[3] != null && !item[3].isEmpty()
                ? item[3] : DataItemConfigService.MATCH_NAME_LIKE;
        if (DataItemConfigService.MATCH_NAME_EXACT.equals(mode)) {
            return itemName.equals(value);
        }
        return itemName.contains(value);
    }

    private void appendPoints(LabTrendResponse.Series s, String[] item, List<Map<String, Object>> rows,
                              LabTrendResponse resp) {
        for (Map<String, Object> r : rows) {
            String itemName = str(r.get("item_name"));
            if (itemName == null || !matches(itemName, item) || isExcluded(itemName, item)) {
                continue;
            }
            Double v = parseDouble(r.get("item_value"));
            if (v == null) {
                continue;
            }
            LabTrendResponse.Point pt = new LabTrendResponse.Point();
            pt.setTime(toIso(r.get("check_time")));
            pt.setValue(v);
            pt.setRefLow(str(r.get("low_value")));
            pt.setRefHigh(str(r.get("high_value")));
            pt.setRefText(str(r.get("ref_text")));
            s.getPoints().add(pt);

            LabTrendResponse.Row row = new LabTrendResponse.Row();
            row.setTime(toIso(r.get("check_time")));
            row.setItemName(itemName);
            row.setValue(str(r.get("item_value")));
            row.setRefLow(str(r.get("low_value")));
            row.setRefHigh(str(r.get("high_value")));
            row.setRefText(str(r.get("ref_text")));
            row.setAlarmFlag(str(r.get("alarm_flag")));
            if (!resp.getRecords().contains(row)) {
                resp.getRecords().add(row);
            }
        }
    }

    /** 曲线状态统一在全部关键字匹配完之后落定（多关键字合并成一条曲线时只能判一次）。 */
    private static void finalizeSeries(LabTrendResponse.Series s, boolean queryFailed) {
        if (queryFailed) {
            s.setDataStatus("UNKNOWN");
            s.setNote("查询失败");
        } else if (!s.getPoints().isEmpty()) {
            s.setDataStatus("AVAILABLE");
        } else {
            s.setDataStatus("NO_RECORD");
            s.setNote("窗口内无记录");
        }
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static Double parseDouble(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(o).trim()).doubleValue();
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o).trim();
    }

    private static String toIso(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) o).toLocalDateTime().format(ISO);
        }
        return String.valueOf(o);
    }
}
