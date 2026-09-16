package com.zing.doctor.quality.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zing.doctor.quality.dsl.FactDefinition;
import com.zing.doctor.quality.dsl.MetricDefinition;
import com.zing.doctor.quality.dsl.PatientFieldDefinition;
import com.zing.doctor.quality.entity.QualityDefHistory;
import com.zing.doctor.quality.entity.QualityFactDef;
import com.zing.doctor.quality.entity.QualityMetricDef;
import com.zing.doctor.quality.mapper.QualityDefHistoryMapper;
import com.zing.doctor.quality.mapper.QualityFactDefMapper;
import com.zing.doctor.quality.mapper.QualityMetricDefMapper;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 质控配置仓储：DB 行 ↔ DSL 定义对象的双向转换。
 *
 * <p><b>为什么这样设计</b>：{@code MetricDefinition} / {@code FactDefinition} 是纯 POJO，
 * 只要能从 DB 还原出这两个对象，{@code SqlCompiler} 与 {@code QualityEngine} 就<b>完全不需要改动</b>，
 * 改动点收敛到 {@code QualityDslLoader} 一处分支。这是把配置真源从 YAML 切到 DB 的最小侵入方案。
 *
 * <p>DB 中 {@code dims} / {@code select_cols} 等以 JSON 数组存 TEXT，转换在本类完成，
 * 因此上层（Loader / Service / Controller）看到的始终是 {@code List<String>}。
 */
@Repository
public class QualityConfigRepository {

    private final QualityMetricDefMapper metricMapper;
    private final QualityFactDefMapper factMapper;
    private final QualityDefHistoryMapper historyMapper;
    private final ObjectMapper om = new ObjectMapper();

    public QualityConfigRepository(QualityMetricDefMapper metricMapper,
                                   QualityFactDefMapper factMapper,
                                   QualityDefHistoryMapper historyMapper) {
        this.metricMapper = metricMapper;
        this.factMapper = factMapper;
        this.historyMapper = historyMapper;
    }

    // ------------------------------------------------------------------
    // 加载（供 QualityDslLoader 使用）
    // ------------------------------------------------------------------

    /** 加载启用中的指标定义（按域 → 排序号 → 编号，与页面展示顺序一致）。 */
    public List<MetricDefinition> loadMetrics() {
        List<QualityMetricDef> rows = metricMapper.selectList(
                new LambdaQueryWrapper<QualityMetricDef>()
                        .eq(QualityMetricDef::getStatus, 1)
                        .orderByAsc(QualityMetricDef::getDomainCode)
                        .orderByAsc(QualityMetricDef::getSortNo)
                        .orderByAsc(QualityMetricDef::getIndexCode));
        Map<String, MetricDefinition> map = new LinkedHashMap<>();
        for (QualityMetricDef row : rows) {
            MetricDefinition m = toMetric(row);
            map.put(m.getCode(), m);
        }
        return new ArrayList<>(map.values());
    }

    /** 加载全部事实层定义。 */
    public List<FactDefinition> loadFacts() {
        List<QualityFactDef> rows = factMapper.selectList(
                new LambdaQueryWrapper<QualityFactDef>().orderByAsc(QualityFactDef::getFactName));
        List<FactDefinition> list = new ArrayList<>();
        for (QualityFactDef row : rows) {
            FactDefinition f = toFact(row);
            if (StringUtils.hasText(f.getFact())) {
                list.add(f);
            }
        }
        return list;
    }

    /** 两张配置表是否都为空（用于判断是否需要从 YAML 导入出厂种子）。 */
    public boolean isEmpty() {
        return metricMapper.selectCount(null) == 0 && factMapper.selectCount(null) == 0;
    }

    // ------------------------------------------------------------------
    // 指标配置读写
    // ------------------------------------------------------------------

    public List<QualityMetricDef> listMetricRows(String domain, String keyword) {
        LambdaQueryWrapper<QualityMetricDef> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(domain)) {
            q.eq(QualityMetricDef::getDomainCode, domain);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            q.and(w -> w.like(QualityMetricDef::getIndexCode, kw)
                    .or().like(QualityMetricDef::getIndexName, kw));
        }
        q.orderByAsc(QualityMetricDef::getDomainCode)
                .orderByAsc(QualityMetricDef::getSortNo)
                .orderByAsc(QualityMetricDef::getIndexCode);
        return metricMapper.selectList(q);
    }

    /** 按编号取一行（含停用行，供历史/回滚场景）。 */
    public QualityMetricDef metricRow(String indexCode) {
        return first(metricMapper.selectList(new LambdaQueryWrapper<QualityMetricDef>()
                .eq(QualityMetricDef::getIndexCode, indexCode)));
    }

    public void insertMetric(QualityMetricDef row) {
        metricMapper.insert(row);
    }

    public void updateMetric(QualityMetricDef row) {
        metricMapper.updateById(row);
    }

    /** 停用：不物理删除，保留历史结果可回溯。 */
    public void disableMetric(String indexCode, String operator) {
        QualityMetricDef row = metricRow(indexCode);
        if (row == null) {
            return;
        }
        row.setStatus(0);
        row.setOperator(operator);
        row.setUpdateTime(LocalDateTime.now());
        metricMapper.updateById(row);
    }

    public int countMetrics() {
        return metricMapper.selectCount(new LambdaQueryWrapper<QualityMetricDef>()
                .eq(QualityMetricDef::getStatus, 1)).intValue();
    }

    // ------------------------------------------------------------------
    // 事实层配置读写
    // ------------------------------------------------------------------

    public List<QualityFactDef> listFactRows() {
        return factMapper.selectList(
                new LambdaQueryWrapper<QualityFactDef>().orderByAsc(QualityFactDef::getFactName));
    }

    public QualityFactDef factRow(String factName) {
        return first(factMapper.selectList(new LambdaQueryWrapper<QualityFactDef>()
                .eq(QualityFactDef::getFactName, factName)));
    }

    public void insertFact(QualityFactDef row) {
        factMapper.insert(row);
    }

    public void updateFact(QualityFactDef row) {
        factMapper.updateById(row);
    }

    // ------------------------------------------------------------------
    // 变更历史
    // ------------------------------------------------------------------

    public void saveHistory(String defType, String defKey, Integer exprVersion,
                            String changeType, String snapshot, String operator) {
        QualityDefHistory h = new QualityDefHistory();
        h.setDefType(defType);
        h.setDefKey(defKey);
        h.setExprVersion(exprVersion);
        h.setChangeType(changeType);
        h.setSnapshot(snapshot);
        h.setOperator(operator);
        h.setCreateTime(LocalDateTime.now());
        historyMapper.insert(h);
    }

    public List<QualityDefHistory> listHistory(String defType, String defKey, int limit) {
        LambdaQueryWrapper<QualityDefHistory> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(defType)) {
            q.eq(QualityDefHistory::getDefType, defType);
        }
        if (StringUtils.hasText(defKey)) {
            q.eq(QualityDefHistory::getDefKey, defKey);
        }
        // 刻意不用 LIMIT / ROWNUM：达梦对 LIMIT 的支持不像 ROWNUM 那样有保证，
        // 而按 (def_type, def_key) 过滤后单个配置的历史只有几十行，在内存里截断足够且无方言风险。
        q.orderByDesc(QualityDefHistory::getId);
        List<QualityDefHistory> list = historyMapper.selectList(q);
        int n = Math.max(1, limit);
        return list.size() > n ? new ArrayList<>(list.subList(0, n)) : list;
    }

    private <T> T first(List<T> list) {
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    public QualityDefHistory historyById(Long id) {
        return historyMapper.selectById(id);
    }

    // ------------------------------------------------------------------
    // 转换：DB 行 → DSL 定义
    // ------------------------------------------------------------------

    public MetricDefinition toMetric(QualityMetricDef r) {
        MetricDefinition m = new MetricDefinition();
        m.setCode(r.getIndexCode());
        m.setName(r.getIndexName());
        m.setDomain(r.getDomainCode());
        m.setFact(r.getFactName());
        m.setUnit(r.getUnit());
        m.setValueType(def(r.getValueType(), "COUNT"));
        m.setAgg(def(r.getAgg(), "PT_COUNT"));
        m.setCalcMode(def(r.getCalcMode(), "DSL"));
        m.setImplStatus(def(r.getImplStatus(), "IMPL"));
        m.setScale(r.getScale() == null ? 100 : r.getScale());
        m.setWhere(r.getExprWhere());
        m.setNumerator(r.getExprNumerator());
        m.setDenominatorWhere(r.getExprDenominatorWhere());
        m.setNumeratorMetric(r.getNumeratorMetric());
        m.setDenominatorMetric(r.getDenominatorMetric());
        m.setDims(readList(r.getDims()));
        m.setPatientFields(readPatientFields(r.getPatientFields()));
        m.setVersion(r.getExprVersion() == null ? 1 : r.getExprVersion());
        m.setSortNo(r.getSortNo());
        m.setRemark(r.getRemark());
        m.setCategoryCode(r.getCategoryCode());
        m.setGroupCode(r.getGroupCode());
        m.setQualityTypeCode(r.getQualityTypeCode());
        m.setIndexStandardCode(r.getIndexStandardCode());
        m.setAmountShowType(r.getAmountShowType());
        m.setAnalysisCountType(r.getAnalysisCountType());
        m.setLegacyScript(r.getLegacyScript());
        m.setLegacySource(r.getLegacySource());
        m.setNewTarget(r.getNewTarget());
        m.setReuseLevel(r.getReuseLevel());
        return m;
    }

    public FactDefinition toFact(QualityFactDef r) {
        FactDefinition f = new FactDefinition();
        f.setFact(r.getFactName());
        f.setDomain(r.getDomainCode());
        f.setStatus(def(r.getStatus(), "ACTIVE"));
        f.setSource(r.getSourceTable());
        f.setAlias(def(r.getAlias(), "t"));
        f.setPatientKey(def(r.getPatientKey(), "patient_id"));
        f.setInHospitalNoKey(def(r.getInHospitalNoKey(), "in_hospital_no"));
        f.setPatientNameKey(def(r.getPatientNameKey(), "patient_name"));
        f.setDepartKey(def(r.getDepartKey(), "depart_code"));
        f.setSelect(readList(r.getSelectCols()));
        f.setDerive(readList(r.getDeriveCols()));
        f.setWhere(readList(r.getWhereConds()));
        f.setGroup(readList(r.getGroupCols()));
        f.setNote(r.getNote());
        return f;
    }

    // ------------------------------------------------------------------
    // 转换：DSL 定义 / 行 → 写库
    // ------------------------------------------------------------------

    /** 用 DSL 定义填充一行实体（新增与更新共用；id 与时间戳由调用方决定）。 */
    public void fillRow(QualityMetricDef row, MetricDefinition m) {
        row.setIndexCode(m.getCode());
        row.setIndexName(m.getName());
        row.setDomainCode(m.getDomain());
        row.setFactName(m.getFact());
        row.setUnit(m.getUnit());
        row.setValueType(def(m.getValueType(), "COUNT"));
        row.setAgg(def(m.getAgg(), "PT_COUNT"));
        row.setCalcMode(def(m.getCalcMode(), "DSL"));
        row.setImplStatus(def(m.getImplStatus(), "IMPL"));
        row.setScale(m.getScale() == null ? 100 : m.getScale());
        row.setExprWhere(m.getWhere());
        row.setExprNumerator(m.getNumerator());
        row.setExprDenominatorWhere(m.getDenominatorWhere());
        row.setNumeratorMetric(m.getNumeratorMetric());
        row.setDenominatorMetric(m.getDenominatorMetric());
        row.setDims(writeList(m.getDims()));
        row.setPatientFields(writePatientFields(m.getPatientFields()));
        row.setExprVersion(m.getVersion() == null ? 1 : m.getVersion());
        row.setSortNo(m.getSortNo());
        row.setRemark(m.getRemark());
        row.setCategoryCode(m.getCategoryCode());
        row.setGroupCode(m.getGroupCode());
        row.setQualityTypeCode(m.getQualityTypeCode());
        row.setIndexStandardCode(m.getIndexStandardCode());
        row.setAmountShowType(m.getAmountShowType());
        row.setAnalysisCountType(m.getAnalysisCountType());
        row.setLegacyScript(m.getLegacyScript());
        row.setLegacySource(m.getLegacySource());
        row.setNewTarget(m.getNewTarget());
        row.setReuseLevel(m.getReuseLevel());
    }

    public void fillRow(QualityFactDef row, FactDefinition f) {
        row.setFactName(f.getFact());
        row.setDomainCode(f.getDomain());
        row.setStatus(def(f.getStatus(), "ACTIVE"));
        row.setSourceTable(f.getSource());
        row.setAlias(def(f.getAlias(), "t"));
        row.setPatientKey(def(f.getPatientKey(), "patient_id"));
        row.setInHospitalNoKey(def(f.getInHospitalNoKey(), "in_hospital_no"));
        row.setPatientNameKey(def(f.getPatientNameKey(), "patient_name"));
        row.setDepartKey(def(f.getDepartKey(), "depart_code"));
        row.setSelectCols(writeList(f.getSelect()));
        row.setDeriveCols(writeList(f.getDerive()));
        row.setWhereConds(writeList(f.getWhere()));
        row.setGroupCols(writeList(f.getGroup()));
        row.setNote(f.getNote());
    }

    // ------------------------------------------------------------------
    // JSON 工具
    // ------------------------------------------------------------------

    public List<String> readList(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            List<String> list = om.readValue(json, new TypeReference<List<String>>() { });
            return list == null ? new ArrayList<>() : list;
        } catch (Exception e) {
            // 单条配置的 JSON 损坏不应拖垮整个配置加载，退化为「无维度/无列」并由校验链暴露
            throw new IllegalStateException("配置 JSON 解析失败: " + json, e);
        }
    }

    public String writeList(List<String> list) {
        try {
            return om.writeValueAsString(list == null ? new ArrayList<String>() : list);
        } catch (Exception e) {
            throw new IllegalStateException("配置 JSON 序列化失败", e);
        }
    }

    /** 患者明细字段配置：JSON 数组 ↔ 对象列表；损坏时退化为空（只影响明细列，不影响出数）。 */
    public List<PatientFieldDefinition> readPatientFields(String json) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            List<PatientFieldDefinition> list =
                    om.readValue(json, new TypeReference<List<PatientFieldDefinition>>() { });
            return list == null ? new ArrayList<PatientFieldDefinition>() : list;
        } catch (Exception e) {
            // 与 dims 不同，这一项损坏不改变口径、不影响任何数值，没必要让整个配置加载失败
            return new ArrayList<>();
        }
    }

    public String writePatientFields(List<PatientFieldDefinition> fields) {
        try {
            return om.writeValueAsString(fields == null ? new ArrayList<PatientFieldDefinition>() : fields);
        } catch (Exception e) {
            throw new IllegalStateException("患者明细字段序列化失败", e);
        }
    }

    /** 快照：任意对象 → JSON（用于 quality_def_history）。 */
    public String toJson(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("快照序列化失败", e);
        }
    }

    public MetricDefinition metricFromJson(String json) {
        try {
            return om.readValue(json, MetricDefinition.class);
        } catch (Exception e) {
            throw new IllegalStateException("指标快照解析失败", e);
        }
    }

    private String def(String v, String fallback) {
        return StringUtils.hasText(v) ? v : fallback;
    }
}
