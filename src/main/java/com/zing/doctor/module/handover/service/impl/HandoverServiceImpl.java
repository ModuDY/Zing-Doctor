package com.zing.doctor.module.handover.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zing.doctor.common.BizException;
import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.module.handover.dto.HandoverOverview;
import com.zing.doctor.module.handover.dto.HandoverPatientCard;
import com.zing.doctor.module.handover.dto.HandoverPatientDetail;
import com.zing.doctor.module.handover.dto.ShiftRange;
import com.zing.doctor.module.handover.entity.HandoverNote;
import com.zing.doctor.module.handover.mapper.HandoverNoteMapper;
import com.zing.doctor.module.handover.service.HandoverService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 医生交班览表 Service 实现。
 *
 * <p>取数区间口径（用户锁定）：默认取"最近一个已完整封板的全天班次"。
 * ICU-4U 全天班次为 [D日07:01, D+1日07:00)，从今天起逐日回溯，取第一个结束时刻 ≤ now 的区间：
 * <ul>
 *   <li>now=09-07 16:46 → [09-06 07:01, 09-07 07:00)</li>
 *   <li>now=09-08 03:00 → [09-06 07:01, 09-07 07:00)（当天07:00未到，再往前一班）</li>
 *   <li>now=09-08 08:00 → [09-07 07:01, 09-08 07:00)</li>
 * </ul>
 * begin/end 时分与日偏移全部来自业务库 config_shift，未配到时回退默认 07:01 / 次日07:00。
 *
 * <p>口径区分：班内统计（出入量/检验异常/新入出科/病情变化）用封板区间 [start,end)；
 * 最新生命体征/当前器官支持用 [start, now]，保证反映"当前状态"。
 *
 * <p>性能：全部按科室一次批量查出后在 Java 端按 patient_id 分组，禁止逐患者 N+1。
 */
@Slf4j
@Service
public class HandoverServiceImpl implements HandoverService {

    @Autowired
    private IcuPatientMapper icuPatientMapper;

    @Autowired
    private HandoverNoteMapper handoverNoteMapper;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Pattern NUM_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");

    /** 默认全天班次开始/结束时分（config_shift 缺失时兜底） */
    private static final LocalTime DEFAULT_BEGIN = LocalTime.of(7, 1);
    private static final LocalTime DEFAULT_END = LocalTime.of(7, 0);
    /** 默认科室 ICU-4U（前端总会传，后端兜底） */
    private static final String DEFAULT_DEPART = "20070131";
    /** 发热阈值 ℃ */
    private static final double FEVER_THRESHOLD = 38.5d;

    /** 内部解析结果：同时持有 LocalDateTime（查询用）与对外 DTO */
    private static class ResolvedShift {
        LocalDateTime start;
        LocalDateTime end;
        LocalDateTime now;
        String shiftName;
        String source;
        ShiftRange dto;
    }

    // ==================================================================
    // 班次区间解析
    // ==================================================================

    @Override
    public HandoverOverview getWardOverview(String departCode, String shiftDate) {
        String dc = (departCode == null || departCode.trim().isEmpty()) ? DEFAULT_DEPART : departCode.trim();
        ResolvedShift rs;
        // 用户指定了交班日期则自动计算该日期的全天班次区间（交班日期=结束日期，实际查询=前一天07:01 ~ 当天07:00）
        if (shiftDate != null && !shiftDate.trim().isEmpty()) {
            String sd = shiftDate.trim().length() >= 10 ? shiftDate.trim().substring(0, 10) : shiftDate.trim();
            rs = new ResolvedShift();
            // 交班日期是第二天，实际数据区间 = 前一天07:01 ~ 交班日期07:00
            rs.end = LocalDateTime.parse(sd + " 07:00:00", DT_FMT);
            rs.start = rs.end.minusDays(1).withHour(7).withMinute(1).withSecond(0).withNano(0);
            rs.now = LocalDateTime.now();
            rs.shiftName = sd + " 全天班";
            rs.source = "user";
            ShiftRange dto = new ShiftRange();
            dto.setStartTime(fmt(rs.start));
            dto.setEndTime(fmt(rs.end));
            dto.setNowTime(fmt(rs.now));
            dto.setShiftName(sd + " 全天班");
            dto.setSource("user");
            rs.dto = dto;
        } else {
            rs = resolveShift(dc);
        }
        String s = fmt(rs.start);
        String e = fmt(rs.end);
        String nowStr = fmt(rs.now);

        // 批量取数（每类一条 SQL，按科室一次查全，Java 分组）
        List<Map<String, Object>> patients = icuPatientMapper.selectHandoverWardPatients(dc);
        List<Map<String, Object>> observes = icuPatientMapper.selectHandoverObserve(dc, s, nowStr);
        // 出入量（参考VW_PATIENT_IO：出量用config_io_item、尿量固定ii_nl、其它入量用io_in、导尿管判断）
        List<Map<String, Object>> outputs = icuPatientMapper.selectHandoverOutput(dc, s, e);
        List<Map<String, Object>> otherIntakes = icuPatientMapper.selectHandoverOtherIntake(dc, s, e);
        List<Map<String, Object>> urines = icuPatientMapper.selectHandoverUrine(dc, s, e);
        List<String> catheters = icuPatientMapper.selectHandoverCatheter(dc, s, e);
        java.util.Set<String> catheterSet = new java.util.HashSet<>(catheters == null ? new ArrayList<>() : catheters);
        // 药品入量（含07:00边界处理，班次起始日期）
        String adviceShiftDate = s.length() >= 10 ? s.substring(0, 10) : s;
        List<Map<String, Object>> adviceIntakes = icuPatientMapper.selectHandoverAdviceIntake(dc, adviceShiftDate, s, e);
        List<Map<String, Object>> labs = icuPatientMapper.selectHandoverLabs(dc, s, e);
        List<Map<String, Object>> vasos = icuPatientMapper.selectHandoverVasopressor(dc);
        List<String> crrtPatients = icuPatientMapper.selectHandoverCrrtPatients(dc);
        java.util.Set<String> crrtSet = new java.util.HashSet<>(crrtPatients == null ? new ArrayList<>() : crrtPatients);
        Map<String, Object> discharge = icuPatientMapper.selectHandoverDischargeCount(s, e, dc);

        // 本班手工交班记录（按住院号映射）
        List<HandoverNote> notes = handoverNoteMapper.selectList(new LambdaQueryWrapper<HandoverNote>()
                .eq(HandoverNote::getDepartCode, dc)
                .eq(HandoverNote::getShiftBeginTime, rs.start)
                .eq(HandoverNote::getStatus, 1));
        Map<String, HandoverNote> noteMap = new LinkedHashMap<>();
        for (HandoverNote n : notes) {
            noteMap.putIfAbsent(n.getInHospitalNo(), n);
        }

        // 患者卡片骨架
        Map<String, HandoverPatientCard> cardMap = new LinkedHashMap<>();
        if (patients != null) {
            for (Map<String, Object> p : patients) {
                String pid = str(p.get("patient_id"));
                if (pid.isEmpty()) {
                    continue;
                }
                cardMap.put(pid, buildBaseCard(p, rs));
            }
        }

        // 最新生命体征（SQL 已按 item_time DESC，每类先到即最新；有创血压优先于无创）
        if (observes != null) {
            for (Map<String, Object> o : observes) {
                HandoverPatientCard card = cardMap.get(str(o.get("patient_id")));
                if (card == null) {
                    continue;
                }
                applyVital(card, str(o.get("item_code")), str(o.get("item_name")), o.get("item_value"), o.get("item_time"));
            }
        }

        // 出量（config_io_item.io_type='o' 的所有记录累加）
        if (outputs != null) {
            for (Map<String, Object> r : outputs) {
                HandoverPatientCard card = cardMap.get(str(r.get("patient_id")));
                if (card == null) continue;
                Double v = parseDoubleOrNull(str(r.get("item_value")));
                if (v != null) {
                    card.setOutputTotal(nz(card.getOutputTotal()) + v);
                }
            }
        }

        // 其它入量（module_code='io_in' 的非药品入量累加）
        if (otherIntakes != null) {
            for (Map<String, Object> r : otherIntakes) {
                HandoverPatientCard card = cardMap.get(str(r.get("patient_id")));
                if (card == null) continue;
                Double v = parseDoubleOrNull(str(r.get("item_value")));
                if (v != null) {
                    card.setIntakeTotal(nz(card.getIntakeTotal()) + v);
                }
            }
        }

        // 尿量（固定 item_code='ii_nl'，有导尿管则显示为 "xxx/C"）
        if (urines != null) {
            for (Map<String, Object> r : urines) {
                HandoverPatientCard card = cardMap.get(str(r.get("patient_id")));
                if (card == null) continue;
                Double v = parseDoubleOrNull(str(r.get("item_value")));
                if (v != null) {
                    card.setUrineTotal(nz(card.getUrineTotal()) + v);
                    if (catheterSet.contains(str(r.get("patient_id")))) {
                        card.setUrineCatheter(true);
                    }
                }
            }
        }

        // 药品入量（is_to_io=1，含07:00边界处理，累加入量）
        if (adviceIntakes != null) {
            for (Map<String, Object> ai : adviceIntakes) {
                HandoverPatientCard card = cardMap.get(str(ai.get("patient_id")));
                if (card == null) continue;
                Double v = parseDoubleOrNull(str(ai.get("item_value")));
                if (v != null) {
                    card.setIntakeTotal(nz(card.getIntakeTotal()) + v);
                }
            }
        }

        // 检验异常
        if (labs != null) {
            for (Map<String, Object> l : labs) {
                HandoverPatientCard card = cardMap.get(str(l.get("patient_id")));
                if (card == null || !isAbnormalLab(l)) {
                    continue;
                }
                card.setAbnormalLabCount(card.getAbnormalLabCount() + 1);
                if (card.getAbnormalLabNames().size() < 3) {
                    card.getAbnormalLabNames().add(
                            str(l.get("item_name")) + " " + str(l.get("result")) + str(l.get("unit")));
                }
            }
        }

        // 在用升压药（按患者去重药名）
        if (vasos != null) {
            for (Map<String, Object> v : vasos) {
                HandoverPatientCard card = cardMap.get(str(v.get("patient_id")));
                if (card == null) {
                    continue;
                }
                String name = str(v.get("name"));
                if (!name.isEmpty() && !card.getVasopressors().contains(name)) {
                    card.getVasopressors().add(name);
                }
            }
        }
        // CRRT标记（用 patient_crrt_record 表，和详情保持一致，覆盖 buildBaseCard 里的 crrt_device_code 判断）
        for (HandoverPatientCard card : cardMap.values()) {
            if (crrtSet.contains(card.getPatientId())) {
                card.setCrrt(true);
            }
        }

        // 手工交班病情变化 + 出入量平衡兜底 + 汇总
        HandoverOverview.Summary summary = new HandoverOverview.Summary();
        List<HandoverPatientCard> cardList = new ArrayList<>(cardMap.values());
        for (HandoverPatientCard c : cardList) {
            HandoverNote note = noteMap.get(c.getInHospitalNo());
            if (note != null) {
                c.setNoteId(note.getId());
                c.setConditionChange(note.getConditionChange());
                c.setNoteCreateBy(note.getCreateBy());
                c.setNoteCreateTime(note.getCreateTime() == null ? null : fmt(note.getCreateTime()));
            }
            // 平衡缺失时用 入-出 兜底
            if (c.getBalanceTotal() == null && c.getIntakeTotal() != null && c.getOutputTotal() != null) {
                c.setBalanceTotal(round1(c.getIntakeTotal() - c.getOutputTotal()));
            }
            if (c.getMaxTemp() != null && c.getMaxTemp() >= FEVER_THRESHOLD) {
                c.setFever(true);
            }
            summary.setTotalPatients(summary.getTotalPatients() + 1);
            if (c.isVentilator()) summary.setVentilatorCount(summary.getVentilatorCount() + 1);
            if (c.isCrrt()) summary.setCrrtCount(summary.getCrrtCount() + 1);
            if (c.isEcmo()) summary.setEcmoCount(summary.getEcmoCount() + 1);
            if (!c.getVasopressors().isEmpty()) summary.setVasopressorCount(summary.getVasopressorCount() + 1);
            if (c.isFever()) summary.setFeverCount(summary.getFeverCount() + 1);
            if (c.getAbnormalLabCount() > 0) summary.setAbnormalLabCount(summary.getAbnormalLabCount() + 1);
            if (c.isSepsisShock()) summary.setSepsisShockCount(summary.getSepsisShockCount() + 1);
            if (c.isIsolation()) summary.setIsolationCount(summary.getIsolationCount() + 1);
            if (c.isNewIn()) summary.setNewInCount(summary.getNewInCount() + 1);
            if (c.getNoteId() != null) summary.setNoteFilledCount(summary.getNoteFilledCount() + 1);
        }
        summary.setDischargeCount(toInt(discharge == null ? null : discharge.get("cnt")));

        HandoverOverview overview = new HandoverOverview();
        overview.setShiftRange(rs.dto);
        overview.setSummary(summary);
        overview.setPatients(cardList);
        return overview;
    }

    @Override
    public HandoverPatientDetail getPatientDetail(String inHospitalNo, String shiftDate) {
        if (isBlank(inHospitalNo)) {
            throw new BizException("缺少住院号");
        }
        Map<String, Object> patient = icuPatientMapper.selectPatientByInHospitalNo(inHospitalNo);
        if (patient == null || patient.isEmpty()) {
            throw new BizException("未找到该住院号对应的在科患者: " + inHospitalNo);
        }
        String pid = str(patient.get("patient_id"));
        String dc = str(patient.get("depart_code"));
        // 用用户选择的交班日期计算班次区间，和总览保持一致
        ResolvedShift rs;
        if (shiftDate != null && !shiftDate.trim().isEmpty()) {
            String sd = shiftDate.trim().length() >= 10 ? shiftDate.trim().substring(0, 10) : shiftDate.trim();
            rs = new ResolvedShift();
            rs.end = LocalDateTime.parse(sd + " 07:00:00", DT_FMT);
            rs.start = rs.end.minusDays(1).withHour(7).withMinute(1).withSecond(0).withNano(0);
            rs.now = LocalDateTime.now();
            rs.shiftName = sd + " 全天班";
            rs.source = "user";
            ShiftRange dto = new ShiftRange();
            dto.setStartTime(fmt(rs.start));
            dto.setEndTime(fmt(rs.end));
            dto.setNowTime(fmt(rs.now));
            dto.setShiftName(sd + " 全天班");
            dto.setSource("user");
            rs.dto = dto;
        } else {
            rs = resolveShift(dc.isEmpty() ? DEFAULT_DEPART : dc);
        }
        String s = fmt(rs.start);
        String e = fmt(rs.end);
        String nowStr = fmt(rs.now);

        HandoverPatientDetail detail = new HandoverPatientDetail();
        detail.setShiftRange(rs.dto);
        detail.setPatient(patient);
        detail.setVitals(safe(icuPatientMapper.selectObserveRecords(pid, s, nowStr)));
        // 出入量明细 = 项目出入量 + 医嘱执行入量
        List<Map<String, Object>> ioList = new ArrayList<>(safe(icuPatientMapper.selectIoRecords(pid, s, e)));
        ioList.addAll(safe(icuPatientMapper.selectPatientAdviceIntake(pid, s, e)));
        ioList.sort((a, b) -> str(b.get("item_time")).compareTo(str(a.get("item_time"))));
        detail.setIoRecords(ioList);

        // 出入量汇总（和总览卡片逻辑一致）
        String adviceShiftDate = s.length() >= 10 ? s.substring(0, 10) : s;
        List<Map<String, Object>> outputs = safe(icuPatientMapper.selectOutputByPatient(pid, s, e));
        List<Map<String, Object>> otherIntakes = safe(icuPatientMapper.selectOtherIntakeByPatient(pid, s, e));
        List<Map<String, Object>> urines = safe(icuPatientMapper.selectUrineByPatient(pid, s, e));
        Map<String, Object> catheterMap = icuPatientMapper.selectCatheterByPatient(pid, s, e);
        List<Map<String, Object>> adviceIntakes = safe(icuPatientMapper.selectAdviceIntakeByPatient(pid, adviceShiftDate, s, e));
        List<Map<String, Object>> outputItemSummary = safe(icuPatientMapper.selectOutputItemSummaryByPatient(pid, s, e));

        double outputTotal = 0, otherIntakeTotal = 0, adviceIntakeTotal = 0, urineTotal = 0;
        for (Map<String, Object> r : outputs) {
            Double v = parseDoubleOrNull(str(r.get("item_value")));
            if (v != null) outputTotal += v;
        }
        for (Map<String, Object> r : otherIntakes) {
            Double v = parseDoubleOrNull(str(r.get("item_value")));
            if (v != null) otherIntakeTotal += v;
        }
        for (Map<String, Object> r : urines) {
            Double v = parseDoubleOrNull(str(r.get("item_value")));
            if (v != null) urineTotal += v;
        }
        for (Map<String, Object> r : adviceIntakes) {
            Double v = parseDoubleOrNull(str(r.get("item_value")));
            if (v != null) adviceIntakeTotal += v;
        }
        double intakeTotal = otherIntakeTotal + adviceIntakeTotal;
        double balanceTotal = intakeTotal - outputTotal;
        boolean hasCatheter = catheterMap != null && parseDoubleOrNull(str(catheterMap.get("cnt"))) != null
                && parseDoubleOrNull(str(catheterMap.get("cnt"))) > 0;

        HandoverPatientDetail.IoSummary ioSummary = new HandoverPatientDetail.IoSummary();
        ioSummary.setIntakeTotal(round1(intakeTotal));
        ioSummary.setOutputTotal(round1(outputTotal));
        ioSummary.setUrineTotal(round1(urineTotal));
        ioSummary.setUrineCatheter(hasCatheter);
        ioSummary.setBalanceTotal(round1(balanceTotal));
        detail.setIoSummary(ioSummary);
        detail.setOutputItemSummary(outputItemSummary);
        // 升压药：查当前执行中（status='1'），和总览卡片逻辑保持一致；按药名去重
        List<Map<String, Object>> vasoAll = safe(icuPatientMapper.selectCurrentVasopressorAdvice(inHospitalNo));
        List<Map<String, Object>> vasoFiltered = new ArrayList<>();
        java.util.Set<String> vasoNames = new java.util.HashSet<>();
        for (Map<String, Object> v : vasoAll) {
            String name = str(v.get("name"));
            if (!name.isEmpty() && !vasoNames.contains(name)) {
                vasoNames.add(name);
                vasoFiltered.add(v);
            }
        }
        detail.setVasopressors(vasoFiltered);
        // 抗菌药：按班次时间范围过滤 + Java层白名单过滤（排除非抗菌药）
        List<Map<String, Object>> abxAll = safe(icuPatientMapper.selectAntibioticAdvice(inHospitalNo, s, e));
        List<Map<String, Object>> abxFiltered = new ArrayList<>();
        for (Map<String, Object> abx : abxAll) {
            if (isBroadSpectrumAntibiotic(str(abx.get("name")))) {
                abxFiltered.add(abx);
            }
        }
        detail.setAntibiotics(abxFiltered);
        detail.setCrrt(icuPatientMapper.selectCrrtRecord(pid));
        detail.setDiagnoses(safe(icuPatientMapper.selectDiagnosis(inHospitalNo)));
        // 微生物：Java层按班次时间范围过滤
        List<Map<String, Object>> microAll = safe(icuPatientMapper.selectMicrobiology(inHospitalNo));
        List<Map<String, Object>> microFiltered = new ArrayList<>();
        for (Map<String, Object> m : microAll) {
            String ct = fmtObj(m.get("check_time"));
            if (ct.compareTo(s) >= 0 && ct.compareTo(e) < 0) {
                microFiltered.add(m);
            }
        }
        detail.setMicrobiology(microFiltered);

        // 检验：近 7 天结果里只保留封板班内的，并逐条标注 abnormal
        List<Map<String, Object>> labInShift = new ArrayList<>();
        List<Map<String, Object>> recent = icuPatientMapper.selectRecentLabs(inHospitalNo);
        if (recent != null) {
            for (Map<String, Object> l : recent) {
                String ct = fmtObj(l.get("check_time"));
                if (ct.compareTo(s) >= 0 && ct.compareTo(e) < 0) {
                    // selectRecentLabs 高值别名为 height_value，统一归一为 high_value 后再判异常
                    if (l.get("high_value") == null && l.get("height_value") != null) {
                        l.put("high_value", l.get("height_value"));
                    }
                    boolean ab = isAbnormalLab(l);
                    l.put("abnormal", ab);
                    labInShift.add(l);
                }
            }
        }
        detail.setLabs(labInShift);

        // 本班手工交班
        HandoverNote note = handoverNoteMapper.selectOne(new LambdaQueryWrapper<HandoverNote>()
                .eq(HandoverNote::getInHospitalNo, inHospitalNo)
                .eq(HandoverNote::getShiftBeginTime, rs.start)
                .eq(HandoverNote::getStatus, 1)
                .orderByDesc(HandoverNote::getId)
                .last("LIMIT 1"));
        if (note != null) {
            Map<String, Object> noteMap = new LinkedHashMap<>();
            noteMap.put("id", note.getId());
            noteMap.put("conditionChange", note.getConditionChange());
            noteMap.put("createBy", note.getCreateBy());
            noteMap.put("createTime", note.getCreateTime() == null ? null : fmt(note.getCreateTime()));
            noteMap.put("updateBy", note.getUpdateBy());
            noteMap.put("updateTime", note.getUpdateTime() == null ? null : fmt(note.getUpdateTime()));
            detail.setNote(noteMap);
        }
        return detail;
    }

    /** 抗菌药白名单判断（61个核心抗菌药通用名，和脓毒症集束化治疗保持一致） */
    private boolean isBroadSpectrumAntibiotic(String name) {
        if (name == null || name.isEmpty()) return false;
        String[] keywords = {
            "美罗培南", "亚胺培南", "比阿培南", "厄他培南", "多利培南",
            "哌拉西林他唑巴坦", "哌拉西林钠他唑巴坦", "头孢哌酮舒巴坦", "头孢哌酮钠舒巴坦钠",
            "头孢他啶阿维巴坦", "头孢他啶阿维巴坦钠", "头孢洛扎他唑巴坦",
            "万古霉素", "去甲万古霉素", "替考拉宁", "利奈唑胺", "替加环素",
            "多粘菌素", "黏菌素", "多粘菌素B", "多粘菌素E",
            "头孢他啶", "头孢吡肟", "头孢匹罗", "头孢噻利",
            "头孢曲松", "头孢噻肟", "头孢唑肟", "头孢地嗪",
            "头孢呋辛", "头孢克洛", "头孢丙烯", "头孢地尼", "头孢克肟",
            "头孢氨苄", "头孢拉定", "头孢唑林", "头孢硫脒",
            "阿莫西林", "氨苄西林", "哌拉西林", "替卡西林", "美洛西林", "阿洛西林",
            "左氧氟沙星", "莫西沙星", "环丙沙星", "依诺沙星", "培氟沙星", "洛美沙星",
            "阿奇霉素", "红霉素", "克拉霉素", "罗红霉素", "地红霉素",
            "克林霉素", "林可霉素",
            "甲硝唑", "替硝唑", "奥硝唑",
            "磺胺甲恶唑", "复方磺胺", "甲氧苄啶",
            "呋喃妥因", "呋喃唑酮",
            "磷霉素", "夫西地酸", "达托霉素", "特拉万星",
            "氯霉素", "异烟肼", "利福平", "乙胺丁醇", "吡嗪酰胺",
            "氟康唑", "伊曲康唑", "伏立康唑", "泊沙康唑", "艾沙康唑",
            "卡泊芬净", "米卡芬净", "阿尼芬净",
            "两性霉素B", "制霉菌素", "特比萘芬",
            "阿昔洛韦", "更昔洛韦", "伐昔洛韦", "泛昔洛韦",
            "奥司他韦", "扎那米韦", "帕拉米韦",
            "利巴韦林", "阿糖腺苷", "膦甲酸钠"
        };
        for (String kw : keywords) {
            if (name.contains(kw)) return true;
        }
        return false;
    }

    @Override
    public HandoverNote saveNote(HandoverNote note) {
        if (note == null || isBlank(note.getInHospitalNo())) {
            throw new BizException("缺少住院号");
        }
        if (isBlank(note.getConditionChange())) {
            throw new BizException("病情变化内容不能为空");
        }
        if (isBlank(note.getCreateBy())) {
            throw new BizException("请填写交班医生");
        }
        if (note.getShiftBeginTime() == null) {
            throw new BizException("缺少班次时间，请刷新页面后重试");
        }
        LocalDateTime now = LocalDateTime.now();
        HandoverNote exist = handoverNoteMapper.selectOne(new LambdaQueryWrapper<HandoverNote>()
                .eq(HandoverNote::getInHospitalNo, note.getInHospitalNo())
                .eq(HandoverNote::getShiftBeginTime, note.getShiftBeginTime())
                .eq(HandoverNote::getStatus, 1)
                .orderByDesc(HandoverNote::getId)
                .last("LIMIT 1"));
        if (exist != null) {
            // 同一患者同一班次只保留一条，更新文本但保留首次创建人/创建时间，保证可追溯
            exist.setConditionChange(note.getConditionChange());
            exist.setUpdateBy(note.getCreateBy());
            exist.setUpdateTime(now);
            handoverNoteMapper.updateById(exist);
            return exist;
        }
        // 补齐患者/科室/班次结束时间
        if (isBlank(note.getPatientName()) || isBlank(note.getDepartCode()) || note.getShiftEndTime() == null) {
            Map<String, Object> p = icuPatientMapper.selectPatientByInHospitalNo(note.getInHospitalNo());
            if (p != null) {
                if (isBlank(note.getPatientId())) {
                    note.setPatientId(str(p.get("patient_id")));
                }
                if (isBlank(note.getPatientName())) {
                    note.setPatientName(str(p.get("name")));
                }
                if (isBlank(note.getDepartCode())) {
                    note.setDepartCode(str(p.get("depart_code")));
                }
            }
            if (note.getShiftEndTime() == null) {
                ResolvedShift rs = resolveShift(isBlank(note.getDepartCode()) ? DEFAULT_DEPART : note.getDepartCode());
                note.setShiftEndTime(rs.end);
            }
        }
        note.setId(null);
        note.setStatus(1);
        note.setCreateTime(now);
        note.setUpdateTime(now);
        handoverNoteMapper.insert(note);
        return note;
    }

    @Override
    public boolean deleteNote(Long id) {
        if (id == null) {
            return false;
        }
        HandoverNote exist = handoverNoteMapper.selectById(id);
        if (exist == null) {
            return false;
        }
        HandoverNote del = new HandoverNote();
        del.setId(id);
        del.setStatus(0);
        del.setUpdateTime(LocalDateTime.now());
        handoverNoteMapper.updateById(del);
        return true;
    }

    @Override
    public List<Map<String, Object>> listDepartments() {
        return icuPatientMapper.selectAllDepartments();
    }

    @Override
    public List<Map<String, Object>> listDischargedPatients(String startTime, String endTime, String departCode) {
        String dc = (departCode == null || departCode.trim().isEmpty()) ? "" : departCode.trim();
        return safe(icuPatientMapper.selectDischargedPatients(startTime, endTime, dc));
    }

    @Override
    public byte[] exportDischargedCsv(String startTime, String endTime, String departCode) {
        List<Map<String, Object>> list = listDischargedPatients(startTime, endTime, departCode);
        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM，Excel打开中文不乱码
        sb.append("\uFEFF");
        // 表头
        sb.append("患者姓名,床号,住院号,入科诊断,入科时间,出科时间,出院时间,主管医生\n");
        // 数据行
        for (Map<String, Object> row : list) {
            sb.append(csvCell(str(row.get("patient_name")))).append(",");
            sb.append(csvCell(str(row.get("bed_code")))).append(",");
            sb.append(csvCell(str(row.get("in_hospital_no")))).append(",");
            sb.append(csvCell(str(row.get("diagnosis")))).append(",");
            sb.append(csvCell(str(row.get("in_depart_time")))).append(",");
            sb.append(csvCell(str(row.get("out_depart_time")))).append(",");
            sb.append(csvCell(str(row.get("out_hospital_time")))).append(",");
            sb.append(csvCell(str(row.get("charge_doctor")))).append("\n");
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /** CSV单元格转义：包含逗号/引号/换行时用双引号包裹，内部双引号转义为两个双引号 */
    private String csvCell(String val) {
        if (val == null) return "";
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    // ==================================================================
    // 私有：区间解析 / 卡片组装 / 分类规则
    // ==================================================================

    /** 解析"最近一个已完整封板的全天班次" */
    private ResolvedShift resolveShift(String departCode) {
        LocalDateTime now = LocalDateTime.now();
        LocalTime begin = DEFAULT_BEGIN;
        LocalTime end = DEFAULT_END;
        int beginFlag = 1;   // 1 当天 / 0 次日 / 2 昨日
        int endFlag = 0;
        String shiftName = "全天";
        String source = "default";

        List<Map<String, Object>> cfgs = icuPatientMapper.selectShiftConfig(departCode == null ? "" : departCode);
        if (cfgs != null && !cfgs.isEmpty()) {
            Map<String, Object> c = cfgs.get(0);
            LocalTime b = parseTime(c.get("shift_begin_time"));
            LocalTime e = parseTime(c.get("shift_end_time"));
            if (b != null) {
                begin = b;
            }
            if (e != null) {
                end = e;
            }
            Integer bf = toInt(c.get("is_begin_now"));
            Integer ef = toInt(c.get("is_end_now"));
            if (bf != null) {
                beginFlag = bf;
            }
            if (ef != null) {
                endFlag = ef;
            }
            String name = str(c.get("shift_name"));
            if (!name.isEmpty()) {
                shiftName = name;
            }
            source = "config";
        }

        LocalDateTime pickedStart = null;
        LocalDateTime pickedEnd = null;
        LocalDate anchor = now.toLocalDate();
        for (int back = 0; back <= 5; back++) {
            LocalDate d = anchor.minusDays(back);
            LocalDateTime cs = combine(d, begin, beginFlag);
            LocalDateTime ce = combine(d, end, endFlag);
            if (!ce.isAfter(now)) {
                pickedStart = cs;
                pickedEnd = ce;
                break;
            }
        }
        if (pickedStart == null) {
            LocalDate d = now.toLocalDate().minusDays(1);
            pickedStart = combine(d, begin, beginFlag);
            pickedEnd = combine(d, end, endFlag);
        }

        ResolvedShift rs = new ResolvedShift();
        rs.start = pickedStart;
        rs.end = pickedEnd;
        rs.now = now;
        rs.shiftName = shiftName;
        rs.source = source;
        ShiftRange dto = new ShiftRange();
        dto.setStartTime(fmt(pickedStart));
        dto.setEndTime(fmt(pickedEnd));
        dto.setNowTime(fmt(now));
        dto.setShiftName(shiftName);
        dto.setSource(source);
        rs.dto = dto;
        return rs;
    }

    /** 按 isXxxNow 日偏移把日期+时分组合成 LocalDateTime */
    private LocalDateTime combine(LocalDate date, LocalTime time, int dayFlag) {
        LocalDate base = date;
        if (dayFlag == 0) {
            base = date.plusDays(1);
        } else if (dayFlag == 2) {
            base = date.minusDays(1);
        }
        return base.atTime(time);
    }

    /** 用 patient_info 行组装卡片基础信息与静态标记 */
    private HandoverPatientCard buildBaseCard(Map<String, Object> p, ResolvedShift rs) {
        HandoverPatientCard c = new HandoverPatientCard();
        c.setPatientId(str(p.get("patient_id")));
        c.setInHospitalNo(str(p.get("in_hospital_no")));
        c.setName(str(p.get("name")));
        c.setGender(str(p.get("gender")));
        c.setAge(str(p.get("age")));
        c.setAgeUnit(str(p.get("age_unit")));
        c.setBedCode(str(p.get("bed_code")));
        c.setInDepartTime(fmtObj(p.get("in_depart_time")));
        c.setOutDepartTime(fmtObj(p.get("out_depart_time")));
        c.setDiagnosisContent(str(p.get("diagnosis_content")));
        c.setAllergyContent(str(p.get("allergy_content")));
        c.setChargeDoctorName(str(p.get("charge_doctor_name")));
        c.setResidentDoctorName(str(p.get("resident_doctor_name")));
        c.setWeight(fmtValue(p.get("weight")));
        c.setWardName(str(p.get("ward_name")));
        c.setDepartCode(str(p.get("depart_code")));

        c.setVentilator(!isBlank(str(p.get("ventilator_code"))));
        c.setCrrt(!isBlank(str(p.get("crrt_device_code"))));
        c.setEcmo(!isBlank(str(p.get("ecmo_device_code"))));
        String iso = str(p.get("isolation_status"));
        c.setIsolation(!iso.isEmpty() && !"0".equals(iso));
        c.setIsolationValue(str(p.get("isolation_value")));
        c.setSepsisShock(toInt(p.get("is_sepsis_shock")) != null && toInt(p.get("is_sepsis_shock")) == 1);
        c.setArds(toInt(p.get("is_ards")) != null && toInt(p.get("is_ards")) == 1);

        // 本班新入科：入科时间落在封板区间
        String inT = c.getInDepartTime();
        c.setNewIn(!inT.isEmpty() && inT.compareTo(fmt(rs.start)) >= 0 && inT.compareTo(fmt(rs.end)) < 0);
        return c;
    }

    /** 单条监护记录归一到对应生命体征槽位（SQL 已倒序，先到即最新；有创血压覆盖无创） */
    private void applyVital(HandoverPatientCard c, String itemCode, String itemName, Object valueObj, Object timeObj) {
        if (itemName == null) {
            return;
        }
        String n = itemName;
        String val = fmtValue(valueObj);
        String t = fmtObj(timeObj);
        String upper = n.toUpperCase();

        // item_code 优先匹配（更准确），item_name 作为兜底
        boolean isHr = "oi_hr".equalsIgnoreCase(itemCode) || n.contains("心率") || n.contains("脉搏");
        boolean isRr = "oi_hxpl".equalsIgnoreCase(itemCode) || n.contains("呼吸频率") || n.contains("呼吸") ;
        boolean isSpo2 = "oi_spo2".equalsIgnoreCase(itemCode) || ((n.contains("血氧饱和") || upper.contains("SPO2")) && !n.contains("中心静脉"));

        if (n.contains("体温")) {
            if (isBlank(c.getTemp())) {
                c.setTemp(val);
                c.setVitalTime(t);
            }
            Double d = parseDoubleOrNull(val);
            if (d != null) {
                c.setMaxTemp(c.getMaxTemp() == null ? d : Math.max(c.getMaxTemp(), d));
            }
        } else if (isHr) {
            if (isBlank(c.getHr())) {
                c.setHr(val);
                if (isBlank(c.getVitalTime())) {
                    c.setVitalTime(t);
                }
            }
        } else if (isRr) {
            if (isBlank(c.getRr())) {
                c.setRr(val);
            }
        } else if (isSpo2) {
            if (isBlank(c.getSpo2())) {
                c.setSpo2(val);
            }
        } else if (n.contains("有创收缩压")) {
            c.setSbp(val);   // 有创优先，直接覆盖
        } else if (n.contains("收缩压") && isBlank(c.getSbp())) {
            c.setSbp(val);
        } else if (n.contains("有创舒张压")) {
            c.setDbp(val);
        } else if (n.contains("舒张压") && isBlank(c.getDbp())) {
            c.setDbp(val);
        }
    }

    /** 单条出入量记录分类累加（按 module_code 区分入量/出量，尿量从出量中单列，平衡单列） */
    private void applyIo(HandoverPatientCard c, String moduleCode, String itemName, Object valueObj) {
        if (itemName == null) {
            return;
        }
        Double v = parseDoubleOrNull(str(valueObj));
        if (v == null) {
            return;
        }
        String n = itemName;
        String mc = moduleCode == null ? "" : moduleCode;
        // 按 module_code 区分（io_in=入量，io_out=出量），兼容旧数据无 module_code 时按 item_name 兜底
        if ("io_in".equals(mc)) {
            c.setIntakeTotal(nz(c.getIntakeTotal()) + v);
        } else if ("io_out".equals(mc)) {
            c.setOutputTotal(nz(c.getOutputTotal()) + v);
            if (n.contains("尿量")) {
                c.setUrineTotal(nz(c.getUrineTotal()) + v);
            }
        } else {
            // 兼容旧逻辑（无 module_code 时按 item_name 区分）
            if (n.contains("尿量")) c.setUrineTotal(nz(c.getUrineTotal()) + v);
            if (n.contains("平衡")) c.setBalanceTotal(nz(c.getBalanceTotal()) + v);
            if (n.contains("入量")) c.setIntakeTotal(nz(c.getIntakeTotal()) + v);
            else if (n.contains("出量")) c.setOutputTotal(nz(c.getOutputTotal()) + v);
        }
        if (n.contains("平衡")) {
            c.setBalanceTotal(nz(c.getBalanceTotal()) + v);
        }
    }

    /** 检验异常判定：报警标志非空非0/非正常，或数值结果超出参考区间 */
    private boolean isAbnormalLab(Map<String, Object> l) {
        String alarm = str(l.get("alarm_flag"));
        if (!alarm.isEmpty() && !"0".equals(alarm) && !"N".equalsIgnoreCase(alarm) && !alarm.contains("正常")) {
            return true;
        }
        Double rv = parseDoubleOrNull(str(l.get("result")));
        if (rv == null) {
            return false;
        }
        Double lo = parseDoubleOrNull(str(l.get("low_value")));
        Double hi = parseDoubleOrNull(str(l.get("high_value")));
        return (lo != null && rv < lo) || (hi != null && rv > hi);
    }

    // ==================================================================
    // 工具方法
    // ==================================================================

    private LocalTime parseTime(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof LocalTime) {
            return (LocalTime) o;
        }
        if (o instanceof Time) {
            // 不使用 toLocalTime()（Java 9+ 且达梦驱动有时区偏差），改用 toString() 可靠解析
            try {
                return LocalTime.parse(o.toString());
            } catch (Exception ex) {
                return null;
            }
        }
        if (o instanceof java.util.Date) {
            return new java.sql.Timestamp(((java.util.Date) o).getTime()).toLocalDateTime().toLocalTime();
        }
        String s = String.valueOf(o).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return LocalTime.parse(s.length() >= 8 ? s.substring(0, 8) : s);
        } catch (Exception e) {
            return null;
        }
    }

    private Integer toInt(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number) {
            return ((Number) o).intValue();
        }
        Double d = parseDoubleOrNull(String.valueOf(o));
        return d == null ? null : d.intValue();
    }

    private Double parseDoubleOrNull(String s) {
        if (s == null) {
            return null;
        }
        Matcher m = NUM_PATTERN.matcher(s);
        if (!m.find()) {
            return null;
        }
        try {
            return Double.parseDouble(m.group());
        } catch (Exception e) {
            return null;
        }
    }

    /** 展示值：数值去掉多余 .0 尾缀，非数值原样 */
    private String fmtValue(Object o) {
        if (o == null) {
            return "";
        }
        String s = String.valueOf(o).trim();
        Double d = parseDoubleOrNull(s);
        if (d != null && NUM_PATTERN.matcher(s.replace(" ", "")).matches()) {
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf(d.longValue());
            }
            return round1(d).toString();
        }
        return s;
    }

    /** 时间对象统一格式化为 yyyy-MM-dd HH:mm:ss（兼容 LocalDateTime / Timestamp / String） */
    private String fmtObj(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof LocalDateTime) {
            return ((LocalDateTime) o).format(DT_FMT);
        }
        if (o instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) o).toLocalDateTime().format(DT_FMT);
        }
        if (o instanceof java.util.Date) {
            return new java.sql.Timestamp(((java.util.Date) o).getTime()).toLocalDateTime().format(DT_FMT);
        }
        String s = String.valueOf(o).trim().replace('T', ' ');
        if (s.length() >= 19) {
            return s.substring(0, 19);
        }
        return s;
    }

    private String fmt(LocalDateTime t) {
        return t == null ? "" : t.format(DT_FMT);
    }

    private String str(Object o) {
        if (o == null) return "";
        // 达梦 CLOB/NClob 类型必须主动读取内容，否则 toString() 只返回对象地址
        // （如 dm.jdbc.driver.DmdbNClob@6300cfa5），NClob 继承自 Clob，统一处理
        if (o instanceof java.sql.Clob) {
            try {
                java.sql.Clob clob = (java.sql.Clob) o;
                long len = clob.length();
                if (len <= 0) return "";
                return clob.getSubString(1, (int) Math.min(len, 65535)).trim();
            } catch (Exception e) {
                return "";
            }
        }
        return String.valueOf(o).trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private double nz(Double d) {
        return d == null ? 0d : d;
    }

    private Double round1(double d) {
        return Math.round(d * 10d) / 10d;
    }

    private <T> List<T> safe(List<T> l) {
        return l == null ? new ArrayList<>() : l;
    }
}
