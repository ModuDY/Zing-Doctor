package com.zing.doctor.module.ards.prone.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zing.doctor.common.BizException;
import com.zing.doctor.common.OperatorContext;
import com.zing.doctor.module.ards.prone.dict.ArdsProneDict;
import com.zing.doctor.module.ards.prone.dto.ArdsProneCellSaveItem;
import com.zing.doctor.module.ards.prone.dto.ArdsProneCellVo;
import com.zing.doctor.module.ards.prone.dto.ArdsProneRecordView;
import com.zing.doctor.module.ards.prone.entity.ArdsProneCell;
import com.zing.doctor.module.ards.prone.entity.ArdsProneCellLog;
import com.zing.doctor.module.ards.prone.entity.ArdsProneConfig;
import com.zing.doctor.module.ards.prone.entity.ArdsProneRecord;
import com.zing.doctor.module.ards.prone.entity.ArdsProneTimepoint;
import com.zing.doctor.module.ards.prone.entity.ArdsProneTpTpl;
import com.zing.doctor.module.ards.prone.mapper.ArdsProneCellLogMapper;
import com.zing.doctor.module.ards.prone.mapper.ArdsProneCellMapper;
import com.zing.doctor.module.ards.prone.mapper.ArdsProneConfigMapper;
import com.zing.doctor.module.ards.prone.mapper.ArdsProneDataMapper;
import com.zing.doctor.module.ards.prone.mapper.ArdsProneRecordMapper;
import com.zing.doctor.module.ards.prone.mapper.ArdsProneTimepointMapper;
import com.zing.doctor.module.ards.prone.mapper.ArdsProneTpTplMapper;
import com.zing.doctor.module.ards.prone.service.ArdsProneService;
import com.zing.doctor.module.system.service.SysParamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ARDS 俯卧位通气治疗记录服务实现。
 *
 * <p>口径（设计方案 v4.1）：
 * <ul>
 *   <li>不做告警、不做阈值配置；参考区间内置固定，仅用于界面标红</li>
 *   <li>取消沿用：采集窗口内无数据一律置空转手工</li>
 *   <li>提交后不设修改时限，任何更正保留原值/新值/修改人/时间/原因</li>
 *   <li>打印与屏幕同一数据源，归档调用现有归档接口（与 APACHE II、SOFA 同一接口同一传参）</li>
 * </ul>
 */
@Slf4j
@Service
public class ArdsProneServiceImpl implements ArdsProneService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 入院日期 / 记录日期显示格式：yyyy-MM-dd HH:mm（打印文书与页面统一） */
    private static final DateTimeFormatter DT_MIN_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 兜底默认时点（库里模板缺失时使用）：T0 + 10 个时点 */
    private static final List<int[]> DEFAULT_TP = Arrays.asList(
            new int[]{0, 0}, new int[]{1, 15}, new int[]{2, 120}, new int[]{3, 240},
            new int[]{4, 360}, new int[]{5, 480}, new int[]{6, 600}, new int[]{7, 720},
            new int[]{8, 840}, new int[]{9, 960}, new int[]{10, 990});

    private static final List<String> DEFAULT_TP_LABEL = Arrays.asList(
            "T0 翻身前", "+15 min", "+2 h", "+4 h", "+6 h", "+8 h",
            "+10 h", "+12 h", "+14 h", "+16 h", "+16 h 30 min");

    @Autowired
    private ArdsProneRecordMapper recordMapper;

    @Autowired
    private ArdsProneTimepointMapper timepointMapper;

    @Autowired
    private ArdsProneCellMapper cellMapper;

    @Autowired
    private ArdsProneCellLogMapper cellLogMapper;

    @Autowired
    private ArdsProneTpTplMapper tplMapper;

    @Autowired
    private ArdsProneDataMapper dataMapper;

    @Autowired
    private ArdsProneConfigMapper configMapper;

    @Autowired
    private ArdsProneMappingResolver mappingResolver;

    /** patient_info 可选列探测结果缓存（in_hospital_time / in_hospital_serial_no 等，列缺失不影响主链路） */
    private final Map<String, Boolean> patientInfoCols = new ConcurrentHashMap<>();

    /** admit_date / record_date 是否已扩列到 ≥16（支持 yyyy-MM-dd HH:mm）；未扩列时降级写日期部分 */
    private volatile Boolean dateMinuteSupported = null;

    @Autowired
    private SysParamService sysParamService;

    // -------------------------------------------------------------- 查询

    @Override
    public List<ArdsProneRecord> list(String inHospitalNo, String departCode) {
        if (StringUtils.hasText(inHospitalNo)) {
            return recordMapper.selectListByPatient(inHospitalNo.trim());
        }
        return recordMapper.selectListByDepart(StringUtils.hasText(departCode) ? departCode.trim() : null);
    }

    @Override
    public ArdsProneRecordView get(Long id) {
        ArdsProneRecord r = recordMapper.selectById(id);
        if (r == null || (r.getStatus() != null && r.getStatus() == 0)) {
            return null;
        }
        ArdsProneRecordView view = new ArdsProneRecordView();
        view.setRecord(r);
        view.setTimepoints(timepointMapper.selectByRecord(id));
        view.setCells(buildCellVos(id));
        view.setApache2Show(apache2Show());
        return view;
    }

    @Override
    public List<ArdsProneCellLog> logs(Long recordId) {
        return cellLogMapper.selectByRecord(recordId);
    }

    @Override
    public List<Map<String, Object>> summary(List<Long> ids) {
        List<Map<String, Object>> res = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return res;
        }
        Map<Long, Map<Integer, Map<String, ArdsProneCell>>> idx = new LinkedHashMap<>();
        for (Long id : ids) {
            idx.put(id, new LinkedHashMap<>());
        }
        for (ArdsProneCell c : cellMapper.selectByRecordIds(ids)) {
            if (c.getRecordId() == null || c.getTpIndex() == null || c.getParamKey() == null) {
                continue;
            }
            Map<Integer, Map<String, ArdsProneCell>> byTp = idx.get(c.getRecordId());
            if (byTp == null) {
                continue;
            }
            byTp.computeIfAbsent(c.getTpIndex(), k -> new LinkedHashMap<>()).put(c.getParamKey(), c);
        }

        int total = ArdsProneDict.params().size();
        for (Long id : ids) {
            Map<Integer, Map<String, ArdsProneCell>> byTp = idx.get(id);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            List<BigDecimal> trend = new ArrayList<>();
            BigDecimal dpMin = null;
            java.util.Set<String> filled = new java.util.HashSet<>();
            if (byTp != null) {
                for (Map<String, ArdsProneCell> cells : byTp.values()) {
                    BigDecimal pf = num(cells.get("pf"));
                    trend.add(pf);
                    BigDecimal dp = num(cells.get("dp"));
                    if (dp != null && (dpMin == null || dp.compareTo(dpMin) < 0)) {
                        dpMin = dp;
                    }
                    for (Map.Entry<String, ArdsProneCell> e : cells.entrySet()) {
                        if (StringUtils.hasText(e.getValue().getValueText())) {
                            filled.add(e.getKey());
                        }
                    }
                }
            }
            m.put("pf0", trend.isEmpty() ? null : trend.get(0));
            BigDecimal pf1 = null;
            for (int i = trend.size() - 1; i >= 0; i--) {
                if (trend.get(i) != null) {
                    pf1 = trend.get(i);
                    break;
                }
            }
            m.put("pf1", pf1);
            m.put("pfTrend", trend);
            m.put("dpMin", dpMin);
            m.put("filled", filled.size());
            m.put("total", total);
            res.add(m);
        }
        return res;
    }

    @Override
    public boolean apache2Show() {
        // 默认显示、全院统一：参数未配置或已停用时按默认「显示」处理
        return sysParamService.bool(KEY_APACHE2_SHOW, true);
    }

    private List<ArdsProneCellVo> buildCellVos(Long recordId) {
        List<ArdsProneCell> cells = cellMapper.selectByRecord(recordId);
        List<ArdsProneCellVo> vos = new ArrayList<>(cells.size());
        for (ArdsProneCell c : cells) {
            ArdsProneCellVo vo = new ArdsProneCellVo();
            vo.setTpIndex(c.getTpIndex());
            vo.setParamKey(c.getParamKey());
            vo.setValue(c.getValueText());
            vo.setSource(c.getSource());
            vo.setCollectTime(c.getCollectTime());
            vo.setManualReason(c.getManualReason());
            vo.setAbnormal(isAbnormal(c.getParamKey(), c.getValueNum()));
            vos.add(vo);
        }
        return vos;
    }

    /** 是否超出内置参考区间：仅界面标红用，不做告警与阻断 */
    private boolean isAbnormal(String paramKey, BigDecimal value) {
        ArdsProneDict.ParamDef def = ArdsProneDict.get(paramKey);
        if (def == null || value == null) {
            return false;
        }
        if (def.getRefLow() != null && value.compareTo(def.getRefLow()) < 0) {
            return true;
        }
        return def.getRefHigh() != null && value.compareTo(def.getRefHigh()) > 0;
    }

    // -------------------------------------------------------------- 新建 / 保存

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArdsProneRecord create(String inHospitalNo, String patientId, String startTime,
                                  String inHospitalSerialNo, String inDepartTime) {
        if (!StringUtils.hasText(inHospitalNo)) {
            throw new BizException(400, "缺少住院号");
        }
        ArdsProneRecord r = new ArdsProneRecord();
        r.setInHospitalNo(inHospitalNo.trim());
        r.setPatientId(patientId);

        LocalDateTime start = parseTime(startTime);
        if (start == null) {
            start = LocalDateTime.now().withSecond(0).withNano(0);
        }
        r.setStartTime(start);
        // 记录日期精确到分钟：yyyy-MM-dd HH:mm（库列未扩列时自动降级为 yyyy-MM-dd）
        r.setRecordDate(recordDateValue(start));

        fillPatientInfo(r, inDepartTime, inHospitalSerialNo);

        // 本次是该患者的第几次俯卧位
        LambdaQueryWrapper<ArdsProneRecord> q = new LambdaQueryWrapper<>();
        q.eq(ArdsProneRecord::getInHospitalNo, r.getInHospitalNo());
        q.eq(ArdsProneRecord::getStatus, 1);
        int times = recordMapper.selectCount(q).intValue() + 1;
        r.setProneTimes(times);
        r.setProneDay("第 " + times + " 天");
        r.setRecordNo(nextRecordNo(start));
        r.setRecordStatus("draft");
        r.setStopType("none");
        r.setArchiveStatus(0);
        r.setStatus(1);

        String operator = OperatorContext.current();
        r.setCreateBy(operator);
        r.setCreateTime(LocalDateTime.now());
        r.setUpdateBy(operator);
        r.setUpdateTime(LocalDateTime.now());
        recordMapper.insert(r);

        createTimepoints(r.getId(), start, r.getDepartCode());
        log.info("ARDS 俯卧位记录已创建: id={}, inHospitalNo={}, start={}", r.getId(), r.getInHospitalNo(), start);
        return r;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArdsProneRecord saveRecord(ArdsProneRecord record) {
        if (record == null || record.getId() == null) {
            throw new BizException(400, "记录 ID 不能为空");
        }
        ArdsProneRecord old = recordMapper.selectById(record.getId());
        if (old == null) {
            throw new BizException(400, "记录不存在");
        }
        // 持续时长：系统按开始/结束时间计算，不接受前端传入
        if (old.getStartTime() != null && record.getEndTime() != null) {
            long minutes = java.time.Duration.between(old.getStartTime(), record.getEndTime()).toMinutes();
            record.setDurationMin(minutes >= 0 ? (int) minutes : null);
        } else {
            record.setDurationMin(null);
        }
        record.setStartTime(old.getStartTime());
        record.setInHospitalNo(old.getInHospitalNo());
        record.setPatientId(old.getPatientId());
        record.setRecordNo(old.getRecordNo());
        record.setUpdateBy(OperatorContext.current());
        record.setUpdateTime(LocalDateTime.now());
        recordMapper.updateById(record);
        // 结束时间变化时重算计算项（持续时长等不参与单元格计算，此处仅为后续扩展留位）
        recalc(record.getId());
        return recordMapper.selectById(record.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int saveCells(Long recordId, List<ArdsProneCellSaveItem> items) {
        if (recordId == null || items == null || items.isEmpty()) {
            return 0;
        }
        ArdsProneRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(400, "记录不存在");
        }
        String operator = OperatorContext.current();
        LocalDateTime now = LocalDateTime.now();
        int count = 0;

        for (ArdsProneCellSaveItem item : items) {
            if (item == null || item.getParamKey() == null || item.getTpIndex() == null) {
                continue;
            }
            ArdsProneDict.ParamDef def = ArdsProneDict.get(item.getParamKey());
            if (def == null) {
                continue;
            }
            // 系统计算项不接受手工录入
            if (def.isCalc()) {
                continue;
            }
            String newValue = item.getValue() == null ? null : item.getValue().trim();
            List<ArdsProneCell> exists = cellMapper.selectOne(recordId, item.getTpIndex(), item.getParamKey());
            ArdsProneCell current = exists == null || exists.isEmpty() ? null : exists.get(0);

            boolean autoSource = current != null
                    && (ArdsProneDict.SRC_AUTO.equals(current.getSource()) || ArdsProneDict.SRC_LIS.equals(current.getSource()));
            if (autoSource && !StringUtils.hasText(item.getReason())) {
                throw new BizException(400, "「" + def.getName() + "」为" +
                        (ArdsProneDict.SRC_AUTO.equals(current.getSource()) ? "自动采集" : "检验同步") + "值，修改须填写原因");
            }

            String oldValue = current == null ? null : current.getValueText();
            if (current == null && !StringUtils.hasText(newValue)) {
                continue;
            }
            if (current != null && eq(oldValue, newValue)) {
                continue;
            }

            ArdsProneCellLog logRec = new ArdsProneCellLog();
            logRec.setRecordId(recordId);
            logRec.setTpIndex(item.getTpIndex());
            logRec.setParamKey(item.getParamKey());
            logRec.setOldValue(oldValue);
            logRec.setNewValue(newValue);
            logRec.setOldSource(current == null ? null : current.getSource());
            logRec.setNewSource(ArdsProneDict.SRC_MAN);
            logRec.setReason(item.getReason());
            logRec.setOperator(operator);
            logRec.setCreateTime(now);

            if (current == null) {
                ArdsProneCell cell = new ArdsProneCell();
                cell.setRecordId(recordId);
                cell.setTpIndex(item.getTpIndex());
                cell.setParamKey(item.getParamKey());
                cell.setValueText(newValue);
                cell.setValueNum(parseNum(newValue));
                cell.setSource(ArdsProneDict.SRC_MAN);
                cell.setManualReason(item.getReason());
                cell.setStatus(1);
                cell.setCreateBy(operator);
                cell.setCreateTime(now);
                cell.setUpdateBy(operator);
                cell.setUpdateTime(now);
                upsertCell(cell);
            } else if (!StringUtils.hasText(newValue)) {
                current.setStatus(0);
                current.setUpdateBy(operator);
                current.setUpdateTime(now);
                cellMapper.updateById(current);
            } else {
                current.setValueText(newValue);
                current.setValueNum(parseNum(newValue));
                // 手工覆盖自动值后，来源改为手工并保留修正原因；原值已进入留痕表
                current.setSource(ArdsProneDict.SRC_MAN);
                if (StringUtils.hasText(item.getReason())) {
                    current.setManualReason(item.getReason());
                }
                current.setUpdateBy(operator);
                current.setUpdateTime(now);
                cellMapper.updateById(current);
            }
            cellLogMapper.insert(logRec);
            count++;
        }

        recalc(recordId);
        refreshTimepointStatus(recordId);
        return count;
    }

    // -------------------------------------------------------------- 自动采集

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> collect(Long recordId, Integer tpIndex) {
        return doCollect(recordId, tpIndex, false);
    }

    @Override
    public Map<String, Object> previewCollect(Long recordId, Integer tpIndex) {
        return doCollect(recordId, tpIndex, true);
    }

    /**
     * 采集 / 试采主流程（同一取数与匹配口径）。
     *
     * <p>取数走 {@code ArdsProneMappingResolver}：优先配置规则（ards_prone_config），
     * 无规则或未命中回退字典内置关键字；返回每项来源明细（哪个项目、哪个编码、源时间），
     * 供填写页「采集明细」与配置页试采核对。
     *
     * @param dryRun true=只解析不落库（配置页试采），false=正式采集
     */
    private Map<String, Object> doCollect(Long recordId, Integer tpIndex, boolean dryRun, boolean forceRefresh) {
        if (forceRefresh) {
            mappingResolver.invalidate();
        }
        ArdsProneRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(400, "记录不存在");
        }
        List<ArdsProneTimepoint> tps = timepointMapper.selectByRecord(recordId);
        ArdsProneTimepoint tp = null;
        for (ArdsProneTimepoint t : tps) {
            if (t.getTpIndex() != null && t.getTpIndex().equals(tpIndex)) {
                tp = t;
                break;
            }
        }
        if (tp == null) {
            throw new BizException(400, "时点不存在");
        }
        LocalDateTime plan = tp.getPlanTime() != null ? tp.getPlanTime() : record.getStartTime();
        if (plan == null) {
            plan = LocalDateTime.now();
        }

        // 查询窗口取「字典默认」与「配置覆盖窗口」的较大者；命中时再按各参数自身窗口二次过滤
        int obsWin = mappingResolver.windowFor(ArdsProneMappingResolver.CH_OBSERVE,
                ArdsProneDict.WINDOW_CONTINUOUS);
        int labWin = mappingResolver.windowFor(ArdsProneMappingResolver.CH_LIS,
                ArdsProneDict.WINDOW_LAB);

        List<Map<String, Object>> observe = new ArrayList<>();
        List<Map<String, Object>> labs = new ArrayList<>();
        if (StringUtils.hasText(record.getPatientId())) {
            try {
                observe = dataMapper.selectObserveRecords(record.getPatientId(),
                        plan.minusMinutes(obsWin).format(DT_FMT),
                        plan.plusMinutes(obsWin).format(DT_FMT));
            } catch (Exception e) {
                log.warn("ARDS 俯卧位采集监护数据失败: recordId={}, tpIndex={}, msg={}", recordId, tpIndex, e.getMessage());
            }
        }
        try {
            labs = dataMapper.selectLabItems(record.getInHospitalNo(),
                    plan.minusMinutes(labWin).format(DT_FMT),
                    plan.plusMinutes(labWin).format(DT_FMT));
        } catch (Exception e) {
            log.warn("ARDS 俯卧位采集检验数据失败: recordId={}, tpIndex={}, msg={}", recordId, tpIndex, e.getMessage());
        }

        String operator = OperatorContext.current();
        LocalDateTime now = LocalDateTime.now();
        int filled = 0;
        int pending = 0;
        int kept = 0;
        List<Map<String, Object>> items = new ArrayList<>();

        for (ArdsProneDict.ParamDef def : ArdsProneDict.params()) {
            if (def.isCalc()) {
                continue;
            }
            List<ArdsProneCell> exists = cellMapper.selectOne(recordId, tpIndex, def.getKey());
            ArdsProneCell current = exists == null || exists.isEmpty() ? null : exists.get(0);
            // 已有值（含手工录入）不覆盖
            if (current != null && StringUtils.hasText(current.getValueText())) {
                kept++;
                items.add(collectItem(def, current.getValueText(), current.getSource(), null, null,
                        current.getCollectTime() == null ? null : current.getCollectTime().format(DT_FMT),
                        ArdsProneMappingResolver.FROM_KEEP, null));
                continue;
            }
            Map<String, Object> hit = mappingResolver.resolve(def, observe, labs, plan);
            if (hit == null) {
                // 窗口内无数据：置空转手工，不沿用历史值
                pending++;
                items.add(collectItem(def, null, null, null, null, null,
                        ArdsProneMappingResolver.FROM_MISS, null));
                continue;
            }
            String valueText = convertedValue(str(hit.get("value")), hit);
            String source = str(hit.get("source"));
            String itemCode = str(hit.get("itemCode"));
            String itemName = str(hit.get("itemName"));
            LocalDateTime collectTime = parseTime(str(hit.get("itemTime")));

            if (!dryRun) {
                if (current == null) {
                    ArdsProneCell cell = new ArdsProneCell();
                    cell.setRecordId(recordId);
                    cell.setTpIndex(tpIndex);
                    cell.setParamKey(def.getKey());
                    cell.setValueText(valueText);
                    cell.setValueNum(parseNum(valueText));
                    cell.setSource(source);
                    cell.setCollectTime(collectTime);
                    cell.setStatus(1);
                    cell.setCreateBy(operator);
                    cell.setCreateTime(now);
                    cell.setUpdateBy(operator);
                    cell.setUpdateTime(now);
                    upsertCell(cell);
                } else {
                    current.setValueText(valueText);
                    current.setValueNum(parseNum(valueText));
                    current.setSource(source);
                    current.setCollectTime(collectTime);
                    current.setStatus(1);
                    current.setUpdateBy(operator);
                    current.setUpdateTime(now);
                    cellMapper.updateById(current);
                }
            }
            filled++;
            items.add(collectItem(def, valueText, source, itemCode, itemName,
                    collectTime == null ? null : collectTime.format(DT_FMT),
                    str(hit.get("from")), hit));
        }

        if (!dryRun) {
            recalc(recordId);
            refreshTimepointStatus(recordId);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planTime", plan.format(DT_FMT));
        result.put("filled", filled);
        result.put("pending", pending);
        result.put("kept", kept);
        result.put("observeCount", observe.size());
        result.put("labCount", labs.size());
        result.put("dryRun", dryRun);
        result.put("items", items);
        return result;
    }

    /** 采集明细行：填写页「采集明细」与配置页试采共用同一结构 */
    private Map<String, Object> collectItem(ArdsProneDict.ParamDef def, String value, String source,
                                            String itemCode, String itemName, String itemTime,
                                            String from, Map<String, Object> hit) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("paramKey", def.getKey());
        m.put("paramName", def.getName());
        m.put("group", def.getGroup());
        m.put("unit", def.getUnit());
        m.put("value", value);
        m.put("source", source);
        m.put("from", from);
        m.put("itemCode", itemCode);
        m.put("itemName", itemName);
        m.put("itemTime", itemTime);
        if (hit != null) {
            m.put("matchType", hit.get("matchType"));
            m.put("ruleId", hit.get("ruleId"));
            m.put("ruleValue", hit.get("ruleValue"));
            m.put("windowMin", hit.get("windowMin"));
        }
        return m;
    }

    /** 单位线性换算：值 × scale + offset（规则未配换算时原样返回） */
    private String convertedValue(String raw, Map<String, Object> hit) {
        if (!StringUtils.hasText(raw) || hit == null) {
            return raw;
        }
        BigDecimal scale = hit.get("unitScale") instanceof BigDecimal ? (BigDecimal) hit.get("unitScale") : null;
        BigDecimal offset = hit.get("unitOffset") instanceof BigDecimal ? (BigDecimal) hit.get("unitOffset") : null;
        if (scale == null && offset == null) {
            return raw;
        }
        BigDecimal v = parseNum(raw);
        if (v == null) {
            return raw;
        }
        BigDecimal out = scale == null ? v : v.multiply(scale);
        if (offset != null) {
            out = out.add(offset);
        }
        return out.stripTrailingZeros().toPlainString();
    }

    // -------------------------------------------------------------- 采集映射配置

    @Override
    public List<ArdsProneConfig> listConfig(String configType, String configKey) {
        return configMapper.selectForAdmin(
                StringUtils.hasText(configType) ? configType.trim() : null,
                StringUtils.hasText(configKey) ? configKey.trim() : null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveConfig(Map<String, Object> body) {
        if (body == null) {
            return false;
        }
        String type = str(body.get("configType"));
        String key = str(body.get("configKey"));
        if (!StringUtils.hasText(type) || !StringUtils.hasText(key)) {
            throw new BizException(400, "数据源通道与参数项不能为空");
        }
        if (!ArdsProneMappingResolver.CH_OBSERVE.equals(type) && !ArdsProneMappingResolver.CH_LIS.equals(type)) {
            throw new BizException(400, "数据源通道取值应为 observe_item 或 lis_item");
        }
        if (ArdsProneDict.get(key.trim()) == null) {
            throw new BizException(400, "参数项不存在：" + key);
        }
        String rawMatch = str(body.get("matchType"));
        String matchType = ArdsProneMappingResolver.MATCH_CODE.equalsIgnoreCase(rawMatch == null ? "" : rawMatch.trim())
                ? ArdsProneMappingResolver.MATCH_CODE
                : ArdsProneMappingResolver.MATCH_NAME;
        String value = str(body.get("configValue"));
        if (!StringUtils.hasText(value)) {
            throw new BizException(400, "匹配值不能为空（item_code 或项目名称关键字，多个用逗号分隔）");
        }
        String operator = OperatorContext.current();
        LocalDateTime now = LocalDateTime.now();

        ArdsProneConfig c;
        Long id = null;
        if (body.get("id") != null && StringUtils.hasText(String.valueOf(body.get("id")))) {
            id = Long.valueOf(String.valueOf(body.get("id")).trim());
            c = configMapper.selectById(id);
            if (c == null) {
                throw new BizException(404, "配置不存在：" + id);
            }
        } else {
            c = new ArdsProneConfig();
            c.setCreateBy(operator);
            c.setCreateTime(now);
        }
        c.setConfigType(type);
        c.setConfigKey(key.trim());
        c.setConfigValue(value.trim());
        c.setMatchType(matchType);
        c.setPriority(intOrNull(body.get("priority"), 10));
        c.setWindowMin(intOrNull(body.get("windowMin"), null));
        c.setUnitScale(decimalOrNull(body.get("unitScale")));
        c.setUnitOffset(decimalOrNull(body.get("unitOffset")));
        c.setItemName(str(body.get("itemName")));
        c.setRemark(str(body.get("remark")));
        c.setSortNo(intOrNull(body.get("sortNo"), 1));
        c.setStatus(intOrNull(body.get("status"), 1));
        c.setUpdateBy(operator);
        c.setUpdateTime(now);

        if (id == null) {
            configMapper.insert(c);
        } else {
            configMapper.updateById(c);
        }
        mappingResolver.invalidate();
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteConfig(Long id) {
        if (id == null) {
            return false;
        }
        boolean ok = configMapper.deleteById(id) > 0;
        if (ok) {
            mappingResolver.invalidate();
        }
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleConfig(Long id, Integer status) {
        if (id == null || status == null) {
            return false;
        }
        ArdsProneConfig c = configMapper.selectById(id);
        if (c == null) {
            return false;
        }
        c.setStatus(status);
        c.setUpdateBy(OperatorContext.current());
        c.setUpdateTime(LocalDateTime.now());
        configMapper.updateById(c);
        mappingResolver.invalidate();
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int seedConfig() {
        String operator = OperatorContext.current();
        LocalDateTime now = LocalDateTime.now();
        int created = 0;
        int sort = 1;
        for (ArdsProneDict.ParamDef def : ArdsProneDict.params()) {
            if (def.isCalc()) {
                continue;
            }
            created += seedOne(def.getKey(), ArdsProneMappingResolver.CH_OBSERVE,
                    ArdsProneMappingResolver.joinKeys(def.getObserveKeywords()), def.getName(), sort, operator, now);
            created += seedOne(def.getKey(), ArdsProneMappingResolver.CH_LIS,
                    ArdsProneMappingResolver.joinKeys(def.getLisKeywords()), def.getName(), sort, operator, now);
            sort++;
        }
        if (created > 0) {
            mappingResolver.invalidate();
        }
        return created;
    }

    /** 写入单条内置规则（同参数 + 同通道 + 同匹配值已存在则跳过，保证可重复执行） */
    private int seedOne(String paramKey, String channel, String value, String itemName,
                        int sortNo, String operator, LocalDateTime now) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        if (configMapper.countSame(paramKey, channel, value) > 0) {
            return 0;
        }
        ArdsProneConfig c = new ArdsProneConfig();
        c.setConfigType(channel);
        c.setConfigKey(paramKey);
        c.setConfigValue(value);
        c.setMatchType(ArdsProneMappingResolver.MATCH_NAME);
        // 监护优先于检验：同一参数默认先取监护/呼吸机，取不到再走检验
        c.setPriority(ArdsProneMappingResolver.CH_OBSERVE.equals(channel) ? 10 : 20);
        c.setItemName(itemName);
        c.setRemark("由内置关键字生成，可改为 item_code 精确匹配");
        c.setSortNo(sortNo);
        c.setStatus(1);
        c.setCreateBy(operator);
        c.setCreateTime(now);
        c.setUpdateBy(operator);
        c.setUpdateTime(now);
        configMapper.insert(c);
        return 1;
    }

    @Override
    public List<Map<String, Object>> candidates(String type, String keyword, String patientId, Integer limit) {
        int top = limit == null || limit <= 0 ? 200 : Math.min(limit, 500);
        String kw = StringUtils.hasText(keyword) ? keyword.trim() : null;
        if ("lis".equalsIgnoreCase(str(type))) {
            String since = LocalDateTime.now().minusDays(7).format(DT_FMT);
            return dataMapper.selectLisItemDict(kw, since, top);
        }
        return dataMapper.selectObserveItemDict(kw, top);
    }

    // -------------------------------------------------------------- 计算项

    /**
     * 驱动压 ΔP = Pplat − PEEP。任一缺失返回 null。
     * 包级私有，便于单元测试直接验证公式。
     */
    BigDecimal calcDeltaP(BigDecimal pplat, BigDecimal peep) {
        if (pplat == null || peep == null) {
            return null;
        }
        return pplat.subtract(peep);
    }

    /**
     * 氧合指数 P/F = PaO₂ × 100 ÷ FiO₂(%)。
     * FiO₂ 缺失或 ≤ 0 返回 null（避免除零与负数误导）。
     * 包级私有，便于单元测试直接验证公式。
     */
    BigDecimal calcPf(BigDecimal pao2, BigDecimal fio2) {
        if (pao2 == null || fio2 == null || fio2.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return pao2.multiply(BigDecimal.valueOf(100))
                .divide(fio2, 0, RoundingMode.HALF_UP);
    }

    /**
     * 氧合指数变化 = 当前 P/F − T0 P/F。任一缺失返回 null。
     * 包级私有，便于单元测试直接验证公式。
     */
    BigDecimal calcPfDelta(BigDecimal currentPf, BigDecimal basePf) {
        if (currentPf == null || basePf == null) {
            return null;
        }
        return currentPf.subtract(basePf);
    }

    /**
     * 氧指数 OI = FiO₂ × 平均气道压 × 100 ÷ PaO₂，
     * 平均气道压 = PEEP + ΔP/2（ΔP = Pplat − PEEP）。
     * 任一输入缺失或 PaO₂ ≤ 0 返回 null。
     * 包级私有，便于单元测试直接验证公式。
     */
    BigDecimal calcOi(BigDecimal fio2, BigDecimal peep, BigDecimal pplat, BigDecimal pao2) {
        if (fio2 == null || peep == null || pplat == null || pao2 == null
                || pao2.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal dp = pplat.subtract(peep);
        BigDecimal paw = peep.add(dp.divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP));
        return fio2.multiply(paw).multiply(BigDecimal.valueOf(100))
                .divide(pao2, 0, RoundingMode.HALF_UP);
    }

    /**
     * 重算系统计算项：驱动压 ΔP、氧合指数 P/F、氧合指数变化、氧指数 OI。
     *
     * <p>缺任一项即置空（前端显示「待补」），绝不显示 0。
     */
    private void recalc(Long recordId) {
        List<ArdsProneCell> cells = cellMapper.selectByRecord(recordId);
        Map<Integer, Map<String, ArdsProneCell>> idx = new HashMap<>();
        for (ArdsProneCell c : cells) {
            if (c.getTpIndex() == null) {
                continue;
            }
            idx.computeIfAbsent(c.getTpIndex(), k -> new HashMap<>()).put(c.getParamKey(), c);
        }
        if (idx.isEmpty()) {
            return;
        }
        Map<String, ArdsProneCell> base = idx.get(0);
        BigDecimal basePf = null;
        if (base != null) {
            basePf = num(base.get("pf"));
        }

        for (Map.Entry<Integer, Map<String, ArdsProneCell>> e : idx.entrySet()) {
            Integer tpIndex = e.getKey();
            Map<String, ArdsProneCell> m = e.getValue();
            BigDecimal peep = num(m.get("peep"));
            BigDecimal pplat = num(m.get("pplat"));
            BigDecimal fio2 = num(m.get("fio2"));
            BigDecimal pao2 = num(m.get("pao2"));

            // ΔP = Pplat − PEEP
            writeCalc(recordId, tpIndex, "dp", m, calcDeltaP(pplat, peep));

            // P/F = PaO₂ ÷ FiO₂(%)
            BigDecimal pf = calcPf(pao2, fio2);
            writeCalc(recordId, tpIndex, "pf", m, pf);

            // 氧合指数变化 = 当前 P/F − T0 的 P/F
            writeCalc(recordId, tpIndex, "pf_delta", m, calcPfDelta(pf, basePf));

            // OI = FiO₂ × 平均气道压 × 100 ÷ PaO₂，平均气道压取 PEEP + ΔP/2
            writeCalc(recordId, tpIndex, "oi", m, calcOi(fio2, peep, pplat, pao2));
        }
    }

    private void writeCalc(Long recordId, Integer tpIndex, String paramKey,
                           Map<String, ArdsProneCell> m, BigDecimal value) {
        ArdsProneCell current = m.get(paramKey);
        LocalDateTime now = LocalDateTime.now();
        if (value == null) {
            // 缺项：清除旧值，前端显示「待补」而不是 0
            if (current != null && current.getStatus() != null && current.getStatus() == 1) {
                current.setStatus(0);
                current.setUpdateTime(now);
                cellMapper.updateById(current);
            }
            return;
        }
        String text = value.stripTrailingZeros().toPlainString();
        if (current == null) {
            ArdsProneCell cell = new ArdsProneCell();
            cell.setRecordId(recordId);
            cell.setTpIndex(tpIndex);
            cell.setParamKey(paramKey);
            cell.setValueText(text);
            cell.setValueNum(value);
            cell.setSource(ArdsProneDict.SRC_CALC);
            cell.setStatus(1);
            cell.setCreateTime(now);
            cell.setUpdateTime(now);
            upsertCell(cell);
            m.put(paramKey, cell);
        } else {
            current.setValueText(text);
            current.setValueNum(value);
            current.setSource(ArdsProneDict.SRC_CALC);
            current.setStatus(1);
            current.setUpdateTime(now);
            cellMapper.updateById(current);
        }
    }

    /**
     * 单元格 upsert：不存在则 insert，唯一约束冲突时重新查询后 update。
     *
     * <p>配合数据库唯一索引 (record_id, tp_index, param_key, CASE WHEN status=1 THEN 1 ELSE NULL END)
     * 使用，保证并发安全：两个请求同时查不到→同时 insert 时，第二个会触发唯一约束冲突，
     * 此时重新查询最新记录后走 update，而不是向用户抛出原始 SQL 异常。
     *
     * <p>调用方需保证 cell 的 recordId/tpIndex/paramKey 已设置；留痕表由调用方负责写入。
     */
    private void upsertCell(ArdsProneCell cell) {
        try {
            cellMapper.insert(cell);
        } catch (DuplicateKeyException e) {
            // 并发冲突：另一个请求已插入相同 (record_id, tp_index, param_key)
            // 重新查询最新记录后走 update，不重复写留痕（调用方已写）
            List<ArdsProneCell> exists = cellMapper.selectOne(
                    cell.getRecordId(), cell.getTpIndex(), cell.getParamKey());
            if (exists != null && !exists.isEmpty()) {
                ArdsProneCell c = exists.get(0);
                c.setValueText(cell.getValueText());
                c.setValueNum(cell.getValueNum());
                c.setSource(cell.getSource());
                c.setCollectTime(cell.getCollectTime());
                c.setManualReason(cell.getManualReason());
                c.setStatus(cell.getStatus());
                c.setUpdateBy(cell.getUpdateBy());
                c.setUpdateTime(cell.getUpdateTime());
                cellMapper.updateById(c);
                log.debug("ARDS 俯卧位单元格 upsert 冲突后已转为 update: recordId={}, tpIndex={}, paramKey={}",
                        cell.getRecordId(), cell.getTpIndex(), cell.getParamKey());
            } else {
                // 极端情况：重新查询仍查不到（如事务隔离导致），记录 warn 由下次操作修正
                log.warn("ARDS 俯卧位单元格 upsert 冲突后重新查询为空，跳过本次写入: recordId={}, tpIndex={}, paramKey={}",
                        cell.getRecordId(), cell.getTpIndex(), cell.getParamKey());
            }
        }
    }

    // -------------------------------------------------------------- 时点

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ArdsProneTimepoint> addTimepoint(Long recordId, String label, Integer offsetMinutes) {
        ArdsProneRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(400, "记录不存在");
        }
        List<ArdsProneTimepoint> list = timepointMapper.selectByRecord(recordId);
        int nextIndex = 0;
        for (ArdsProneTimepoint t : list) {
            if (t.getTpIndex() != null && t.getTpIndex() >= nextIndex) {
                nextIndex = t.getTpIndex() + 1;
            }
        }
        ArdsProneTimepoint tp = new ArdsProneTimepoint();
        tp.setRecordId(recordId);
        tp.setTpIndex(nextIndex);
        tp.setTpLabel(StringUtils.hasText(label) ? label.trim() : ("+" + offsetMinutes + " min"));
        tp.setOffsetMinutes(offsetMinutes == null ? 0 : offsetMinutes);
        tp.setPlanTime(planTime(record.getStartTime(), offsetMinutes));
        tp.setCollectStatus("pending");
        tp.setStatus(1);
        tp.setCreateBy(OperatorContext.current());
        tp.setCreateTime(LocalDateTime.now());
        timepointMapper.insert(tp);
        return timepointMapper.selectByRecord(recordId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ArdsProneTimepoint> updateTimepoint(Long tpId, String label, Integer offsetMinutes) {
        ArdsProneTimepoint tp = timepointMapper.selectById(tpId);
        if (tp == null) {
            throw new BizException(400, "时点不存在");
        }
        if (StringUtils.hasText(label)) {
            tp.setTpLabel(label.trim());
        }
        if (offsetMinutes != null) {
            tp.setOffsetMinutes(offsetMinutes);
        }
        ArdsProneRecord record = recordMapper.selectById(tp.getRecordId());
        tp.setPlanTime(planTime(record == null ? null : record.getStartTime(), tp.getOffsetMinutes()));
        tp.setUpdateBy(OperatorContext.current());
        tp.setUpdateTime(LocalDateTime.now());
        timepointMapper.updateById(tp);
        return timepointMapper.selectByRecord(tp.getRecordId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ArdsProneTimepoint> deleteTimepoint(Long tpId) {
        ArdsProneTimepoint tp = timepointMapper.selectById(tpId);
        if (tp == null) {
            throw new BizException(400, "时点不存在");
        }
        // 软删除：时点与单元格都只置 status=0，历史数据保留可查
        tp.setStatus(0);
        tp.setUpdateBy(OperatorContext.current());
        tp.setUpdateTime(LocalDateTime.now());
        timepointMapper.updateById(tp);

        List<ArdsProneCell> cells = cellMapper.selectByRecord(tp.getRecordId());
        for (ArdsProneCell c : cells) {
            if (c.getTpIndex() != null && c.getTpIndex().equals(tp.getTpIndex())) {
                c.setStatus(0);
                c.setUpdateTime(LocalDateTime.now());
                cellMapper.updateById(c);
            }
        }
        return timepointMapper.selectByRecord(tp.getRecordId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ArdsProneTimepoint> resetTimepoints(Long recordId) {
        ArdsProneRecord record = recordMapper.selectById(recordId);
        if (record == null) {
            throw new BizException(400, "记录不存在");
        }
        List<ArdsProneTimepoint> old = timepointMapper.selectByRecord(recordId);
        // 收集旧时点的 tpIndex，用于同步软删除对应单元格
        java.util.Set<Integer> oldTpIndexes = new java.util.HashSet<>();
        for (ArdsProneTimepoint t : old) {
            t.setStatus(0);
            t.setUpdateTime(LocalDateTime.now());
            timepointMapper.updateById(t);
            if (t.getTpIndex() != null) {
                oldTpIndexes.add(t.getTpIndex());
            }
        }
        // 修复：重置时点时同步软删除旧时点已填的单元格数据，
        // 避免 get() 返回已删除时点的残留数据，与 deleteTimepoint 行为保持一致。
        if (!oldTpIndexes.isEmpty()) {
            List<ArdsProneCell> cells = cellMapper.selectByRecord(recordId);
            for (ArdsProneCell c : cells) {
                if (c.getTpIndex() != null && oldTpIndexes.contains(c.getTpIndex())
                        && c.getStatus() != null && c.getStatus() == 1) {
                    c.setStatus(0);
                    c.setUpdateTime(LocalDateTime.now());
                    cellMapper.updateById(c);
                }
            }
        }
        createTimepoints(recordId, record.getStartTime(), record.getDepartCode());
        return timepointMapper.selectByRecord(recordId);
    }

    @Override
    public List<ArdsProneTpTpl> tpl(String departCode) {
        List<ArdsProneTpTpl> list = new ArrayList<>();
        if (StringUtils.hasText(departCode)) {
            list = tplMapper.selectByDepart(departCode.trim());
        }
        if (list.isEmpty()) {
            list = tplMapper.selectByDepart("");
        }
        if (list.isEmpty()) {
            // 库里模板缺失时返回内置默认，保证功能可用
            for (int i = 0; i < DEFAULT_TP.size(); i++) {
                ArdsProneTpTpl t = new ArdsProneTpTpl();
                t.setDepartCode("");
                t.setTpIndex(DEFAULT_TP.get(i)[0]);
                t.setTpLabel(DEFAULT_TP_LABEL.get(i));
                t.setOffsetMinutes(DEFAULT_TP.get(i)[1]);
                t.setStatus(1);
                list.add(t);
            }
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveTpl(String departCode, List<ArdsProneTpTpl> items) {
        String dep = departCode == null ? "" : departCode.trim();
        List<ArdsProneTpTpl> old = tplMapper.selectByDepart(dep);
        for (ArdsProneTpTpl t : old) {
            tplMapper.deleteById(t.getId());
        }
        if (items == null) {
            return;
        }
        String operator = OperatorContext.current();
        LocalDateTime now = LocalDateTime.now();
        int i = 0;
        for (ArdsProneTpTpl item : items) {
            if (item == null || !StringUtils.hasText(item.getTpLabel())) {
                continue;
            }
            item.setId(null);
            item.setDepartCode(dep);
            item.setTpIndex(i++);
            item.setStatus(1);
            item.setCreateBy(operator);
            item.setCreateTime(now);
            tplMapper.insert(item);
        }
    }

    private void createTimepoints(Long recordId, LocalDateTime start, String departCode) {
        List<ArdsProneTpTpl> tpls = tpl(departCode);
        String operator = OperatorContext.current();
        LocalDateTime now = LocalDateTime.now();
        for (ArdsProneTpTpl t : tpls) {
            ArdsProneTimepoint tp = new ArdsProneTimepoint();
            tp.setRecordId(recordId);
            tp.setTpIndex(t.getTpIndex());
            tp.setTpLabel(t.getTpLabel());
            tp.setOffsetMinutes(t.getOffsetMinutes());
            tp.setPlanTime(planTime(start, t.getOffsetMinutes()));
            tp.setCollectStatus("pending");
            tp.setStatus(1);
            tp.setCreateBy(operator);
            tp.setCreateTime(now);
            timepointMapper.insert(tp);
        }
    }

    /** 刷新各时点采集状态：自动/检验项全部有值才算「已采集」（中性状态，不做告警） */
    private void refreshTimepointStatus(Long recordId) {
        List<ArdsProneTimepoint> tps = timepointMapper.selectByRecord(recordId);
        if (tps.isEmpty()) {
            return;
        }
        List<ArdsProneCell> cells = cellMapper.selectByRecord(recordId);
        Map<Integer, java.util.Set<String>> filled = new HashMap<>();
        for (ArdsProneCell c : cells) {
            if (c.getTpIndex() == null || !StringUtils.hasText(c.getValueText())) {
                continue;
            }
            filled.computeIfAbsent(c.getTpIndex(), k -> new java.util.HashSet<>()).add(c.getParamKey());
        }
        for (ArdsProneTimepoint tp : tps) {
            boolean done = true;
            for (ArdsProneDict.ParamDef def : ArdsProneDict.params()) {
                if (!ArdsProneDict.SRC_AUTO.equals(def.getSource()) && !ArdsProneDict.SRC_LIS.equals(def.getSource())) {
                    continue;
                }
                java.util.Set<String> keys = filled.get(tp.getTpIndex());
                if (keys == null || !keys.contains(def.getKey())) {
                    done = false;
                    break;
                }
            }
            String status = done ? "done" : "pending";
            if (!status.equals(tp.getCollectStatus())) {
                tp.setCollectStatus(status);
                tp.setUpdateTime(LocalDateTime.now());
                timepointMapper.updateById(tp);
            }
        }
    }

    // -------------------------------------------------------------- PDF / 删除

    @Override
    public boolean attachPdf(Long id, String pdfData, String pdfName) {
        if (id == null || !StringUtils.hasText(pdfData)) {
            return false;
        }
        ArdsProneRecord r = recordMapper.selectById(id);
        if (r == null) {
            return false;
        }
        ArdsProneRecord upd = new ArdsProneRecord();
        upd.setId(id);
        upd.setPdfData(pdfData.trim());
        if (StringUtils.hasText(pdfName)) {
            upd.setPdfName(pdfName.trim());
        }
        upd.setUpdateBy(OperatorContext.current());
        upd.setUpdateTime(LocalDateTime.now());
        recordMapper.updateById(upd);
        log.info("ARDS 俯卧位文书 PDF 已保存: id={}, 大小≈{}KB", id, pdfData.length() * 3 / 4 / 1024);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        ArdsProneRecord r = recordMapper.selectById(id);
        if (r == null) {
            return false;
        }
        r.setStatus(0);
        r.setUpdateBy(OperatorContext.current());
        r.setUpdateTime(LocalDateTime.now());
        recordMapper.updateById(r);
        for (ArdsProneTimepoint t : timepointMapper.selectByRecord(id)) {
            t.setStatus(0);
            t.setUpdateTime(LocalDateTime.now());
            timepointMapper.updateById(t);
        }
        return true;
    }

    // -------------------------------------------------------------- 工具

    private void fillPatientInfo(ArdsProneRecord r, String inDepartTime, String inHospitalSerialNo) {
        try {
            Map<String, Object> info = pickPatient(r.getInHospitalNo(), inHospitalSerialNo, inDepartTime);
            if (info == null || info.isEmpty()) {
                return;
            }
            r.setPatientName(str(info.get("name")));
            r.setSex(str(info.get("sex")));
            r.setAge(str(info.get("age")));
            r.setBedCode(str(info.get("bed_code")));
            r.setDepartCode(str(info.get("depart_code")));
            if (!StringUtils.hasText(r.getPatientId())) {
                r.setPatientId(str(info.get("patient_id")));
            }
            // 入院时间取 patient_info.in_hospital_time，缺失时回退 in_depart_time（库列未扩列时降级为 yyyy-MM-dd）
            r.setAdmitDate(admitDateValue(info));
            // 诊断：patient_info.diagnosis_content 优先；为空回退诊断表最新一条
            String diagnosis = str(info.get("diagnosis"));
            if (!StringUtils.hasText(diagnosis)) {
                diagnosis = latestDiagnosis(r.getInHospitalNo());
            }
            if (StringUtils.hasText(diagnosis)) {
                r.setDiagnosis(diagnosis.trim());
            }
        } catch (Exception e) {
            // 患者基本信息取不到不阻断建档：由护士在页面上补充
            log.warn("ARDS 俯卧位建档读取患者信息失败: inHospitalNo={}, msg={}", r.getInHospitalNo(), e.getMessage());
        }
    }

    // -------------------------------------------------------------- 患者解析

    @Override
    public Map<String, Object> lookupPatient(String inHospitalNo, String inHospitalSerialNo, String inDepartTime) {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("serialColumnExists", serialColumnExists());
        if (!StringUtils.hasText(inHospitalNo)) {
            res.put("found", false);
            res.put("message", "缺少住院号");
            return res;
        }
        Map<String, Object> info;
        try {
            info = pickPatient(inHospitalNo, inHospitalSerialNo, inDepartTime);
        } catch (Exception e) {
            log.warn("ARDS 俯卧位患者解析失败: inHospitalNo={}, msg={}", inHospitalNo, e.getMessage());
            res.put("found", false);
            res.put("message", "患者查询失败：" + e.getMessage());
            return res;
        }
        if (info == null || info.isEmpty()) {
            res.put("found", false);
            res.put("message", "ICU 患者表中未匹配到该住院号");
            return res;
        }
        String diagnosis = str(info.get("diagnosis"));
        if (!StringUtils.hasText(diagnosis)) {
            diagnosis = latestDiagnosis(inHospitalNo);
        }
        res.put("found", true);
        res.put("patientId", str(info.get("patient_id")));
        res.put("inHospitalNo", str(info.get("in_hospital_no")));
        res.put("patientName", str(info.get("name")));
        res.put("sex", str(info.get("sex")));
        res.put("age", str(info.get("age")));
        res.put("bedCode", str(info.get("bed_code")));
        res.put("departCode", str(info.get("depart_code")));
        res.put("diagnosis", diagnosis == null ? null : diagnosis.trim());
        res.put("inDepartTime", fmtMinute(info.get("in_depart_time")));
        // 入院时间：patient_info.in_hospital_time 优先，缺失回退入科时间（in_depart_time）
        String admitTime = fmtMinute(info.get("in_hospital_time"));
        if (admitTime == null) {
            admitTime = fmtMinute(info.get("in_depart_time"));
        }
        res.put("admitTime", admitTime);
        res.put("admitDate", admitTime);
        res.put("isInDepart", str(info.get("is_in_depart")));
        return res;
    }

    /**
     * 患者定位顺序：
     * <ol>
     *   <li>住院流水号（仅当 patient_info 确有 in_hospital_serial_no 列时）</li>
     *   <li>住院号 + 入科时间（与 patient_info.in_depart_time 分钟级相等）</li>
     *   <li>该住院号最近一次入科</li>
     * </ol>
     */
    private Map<String, Object> pickPatient(String inHospitalNo, String inHospitalSerialNo, String inDepartTime) {
        // 入院时间列为可选：存在才在 SQL 中带上，避免列缺失时整条查询报错
        boolean withInHospitalTime = inHospitalTimeColumnExists();
        List<Map<String, Object>> rows = null;
        if (StringUtils.hasText(inHospitalSerialNo) && serialColumnExists()) {
            try {
                rows = dataMapper.selectPatientCandidatesBySerial(inHospitalNo.trim(),
                        inHospitalSerialNo.trim(), withInHospitalTime);
            } catch (Exception e) {
                log.warn("ARDS 俯卧位按住院流水号定位失败，回退按住院号: {}", e.getMessage());
            }
        }
        if (rows == null || rows.isEmpty()) {
            rows = dataMapper.selectPatientCandidates(inHospitalNo.trim(), withInHospitalTime);
        }
        if (rows == null || rows.isEmpty()) {
            return null;
        }
        if (StringUtils.hasText(inDepartTime)) {
            String want = fmtMinuteText(inDepartTime);
            if (want != null) {
                for (Map<String, Object> row : rows) {
                    if (want.equals(fmtMinute(row.get("in_depart_time")))) {
                        return row;
                    }
                }
            }
        }
        return rows.get(0);
    }

    /** 诊断表兜底：取该住院号最新一条诊断名称 */
    private String latestDiagnosis(String inHospitalNo) {
        try {
            return dataMapper.selectLatestDiagnosis(inHospitalNo);
        } catch (Exception e) {
            log.warn("ARDS 俯卧位读取诊断表失败: inHospitalNo={}, msg={}", inHospitalNo, e.getMessage());
            return null;
        }
    }

    /** patient_info 是否存在某列：探测结果缓存，失败按不存在处理（可选列缺失不阻断取数） */
    private boolean hasPatientInfoColumn(String columnName) {
        Boolean cached = patientInfoCols.get(columnName);
        if (cached != null) {
            return cached;
        }
        boolean exists = false;
        try {
            exists = dataMapper.countPatientInfoColumn(columnName) > 0;
        } catch (Exception e) {
            log.warn("探测 patient_info.{} 失败，按不存在处理: {}", columnName, e.getMessage());
        }
        patientInfoCols.put(columnName, exists);
        return exists;
    }

    /** patient_info 是否存在 in_hospital_serial_no（住院流水号）列 */
    private boolean serialColumnExists() {
        return hasPatientInfoColumn("IN_HOSPITAL_SERIAL_NO");
    }

    /** patient_info 是否存在 in_hospital_time（入院时间）列；缺失时入院时间回退入科时间 */
    private boolean inHospitalTimeColumnExists() {
        return hasPatientInfoColumn("IN_HOSPITAL_TIME");
    }

    /**
     * admit_date / record_date 列是否已扩列（≥16 字符）。
     *
     * <p>旧库这两列是 VARCHAR(10)，直接写 16 字符的 yyyy-MM-dd HH:mm 会「超长」报错。
     * 这里探测一次并缓存：未扩列时降级写日期部分（yyyy-MM-dd），保证建档不因未执行 SQL 而失败。
     */
    private boolean dateMinuteSupported() {
        Boolean cached = dateMinuteSupported;
        if (cached != null) {
            return cached;
        }
        boolean ok = false;
        try {
            Integer len = recordMapper.selectColumnLength("RECORD_DATE");
            ok = len != null && len >= 16;
            if (!ok) {
                log.warn("ards_prone_record.record_date 列长度不足 16（当前 {}），"
                        + "入院日期/记录日期暂按 yyyy-MM-dd 写入；执行 sql/22_ards_prone_config.sql 扩列后自动恢复", len);
            }
        } catch (Exception e) {
            log.warn("探测 record_date 列长度失败，按 VARCHAR(10) 处理: {}", e.getMessage());
        }
        dateMinuteSupported = ok;
        return ok;
    }

    /** 记录日期写入值：支持分钟则 yyyy-MM-dd HH:mm，否则降级 yyyy-MM-dd */
    private String recordDateValue(LocalDateTime start) {
        return dateMinuteSupported() ? start.format(DT_MIN_FMT) : start.toLocalDate().toString();
    }

    /** 入院时间写入值：优先 in_hospital_time（入院时间），缺失回退 in_depart_time（入科时间） */
    private String admitDateValue(Map<String, Object> info) {
        if (info == null) {
            return null;
        }
        String minute = fmtMinute(info.get("in_hospital_time"));
        if (minute == null) {
            minute = fmtMinute(info.get("in_depart_time"));
        }
        if (minute == null) {
            return null;
        }
        return dateMinuteSupported() ? minute : minute.substring(0, 10);
    }

    /** 时间对象 → yyyy-MM-dd HH:mm（兼容 Timestamp / LocalDateTime / 文本） */
    private String fmtMinute(Object v) {
        LocalDateTime dt = toDateTime(v);
        return dt == null ? null : dt.format(DT_MIN_FMT);
    }

    /** 外部传入的时间文本 → yyyy-MM-dd HH:mm（无法解析返回 null） */
    private String fmtMinuteText(String text) {
        LocalDateTime dt = toDateTime(text);
        return dt == null ? null : dt.format(DT_MIN_FMT);
    }

    private LocalDateTime toDateTime(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) v).toLocalDateTime();
        }
        if (v instanceof LocalDateTime) {
            return (LocalDateTime) v;
        }
        String s = String.valueOf(v).trim();
        if (!StringUtils.hasText(s)) {
            return null;
        }
        if (s.length() >= 19) {
            s = s.substring(0, 19);
        }
        return parseTime(s.replace('T', ' '));
    }

    private String nextRecordNo(LocalDateTime start) {
        String prefix = sysParamService.value(KEY_RECORD_PREFIX);
        if (!StringUtils.hasText(prefix)) {
            prefix = "PP";
        }
        String day = start.toLocalDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String head = prefix + "-" + day + "-";
        LambdaQueryWrapper<ArdsProneRecord> q = new LambdaQueryWrapper<>();
        q.likeRight(ArdsProneRecord::getRecordNo, head);
        int seq = recordMapper.selectCount(q).intValue() + 1;
        return head + String.format("%03d", seq);
    }

    private LocalDateTime planTime(LocalDateTime start, Integer offsetMinutes) {
        if (start == null) {
            return null;
        }
        return start.plusMinutes(offsetMinutes == null ? 0 : offsetMinutes);
    }

    private LocalDateTime parseTime(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String v = text.trim();
        try {
            if (v.length() == 16) {
                v = v + ":00";
            }
            return LocalDateTime.parse(v.replace("T", " "), DT_FMT);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(v.replace("T", " "));
            } catch (Exception ignore) {
                return null;
            }
        }
    }

    private BigDecimal parseNum(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** 对象转 Integer（空/非法返回 def，用于配置项缺省） */
    private Integer intOrNull(Object v, Integer def) {
        if (v == null || !StringUtils.hasText(String.valueOf(v))) {
            return def;
        }
        try {
            return new BigDecimal(String.valueOf(v).trim()).intValue();
        } catch (Exception e) {
            return def;
        }
    }

    /** 对象转 BigDecimal（空/非法返回 null，用于单位换算系数） */
    private BigDecimal decimalOrNull(Object v) {
        if (v == null || !StringUtils.hasText(String.valueOf(v))) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal num(ArdsProneCell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getValueNum() != null) {
            return cell.getValueNum();
        }
        return parseNum(cell.getValueText());
    }

    private String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private boolean eq(String a, String b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.trim().equals(b.trim());
    }
}
