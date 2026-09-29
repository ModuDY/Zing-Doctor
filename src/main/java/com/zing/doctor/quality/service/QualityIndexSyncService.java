package com.zing.doctor.quality.service;

import com.zing.doctor.quality.config.QualityProperties;
import com.zing.doctor.quality.dsl.MetricDefinition;
import com.zing.doctor.quality.engine.QualityDslLoader;
import com.zing.doctor.quality.entity.QualityIndex;
import com.zing.doctor.quality.mapper.QualityIndexMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 指标字典同步：{@code quality/metrics/*.yaml} → {@code quality_index} 表。
 *
 * <p>这是「改指标不改代码」的最后一环：配置改完，重启即自动落库到字典表，
 * 页面与接口读字典表，因此新增指标不需要写 SQL、不需要改前端。
 *
 * <p>同步是幂等的：以 {@code index_code} 为键，存在则更新、不存在则插入；
 * 配置里被删除的指标只置为停用，不物理删除（历史结果仍可回溯）。
 */
@Service
public class QualityIndexSyncService {

    private static final Logger log = LoggerFactory.getLogger(QualityIndexSyncService.class);

    private final QualityProperties props;
    private final QualityDslLoader dsl;
    private final QualityIndexMapper indexMapper;

    public QualityIndexSyncService(QualityProperties props, QualityDslLoader dsl,
                                   QualityIndexMapper indexMapper) {
        this.props = props;
        this.dsl = dsl;
        this.indexMapper = indexMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        if (!props.isEnabled()) {
            return;
        }
        try {
            int n = sync();
            log.info("[质控] 指标字典同步完成：{} 条", n);
        } catch (Exception e) {
            // 数据库不可用不应阻断应用启动；质控页面会返回空看板
            log.warn("[质控] 指标字典同步失败（不影响其他功能）: {}", e.getMessage());
        }
    }

    /**
     * 重读配置文件并同步字典（页面「同步字典」按钮的入口）。
     *
     * <p>与 {@link #sync()} 的区别：{@code sync()} 只把<b>内存中已加载</b>的配置落库，
     * 启动完成后单独调用它并不会让配置文件的修改生效 —— 这正是此前
     * 「改完 YAML 点同步即热生效」不成立的原因（{@code reload()} 一直无人调用）。
     *
     * <p>{@code reload()} 内部是原子替换：解析失败时旧配置原样保留，因此这里直接抛出，
     * 由调用方提示失败，不会出现「内存是旧配置、字典表已按未变配置重写一遍」的错乱。
     */
    public int reloadAndSync() {
        dsl.reload();
        return sync();
    }

    /** 全量同步，返回处理条数。 */
    public int sync() {
        List<MetricDefinition> metrics = dsl.sortedMetrics();
        if (metrics.isEmpty()) {
            log.warn("[质控] 未加载到任何指标配置，跳过字典同步");
            return 0;
        }
        int count = 0;
        for (MetricDefinition m : metrics) {
            QualityIndex exist = indexMapper.selectByCode(m.getCode());
            QualityIndex e = exist == null ? new QualityIndex() : exist;
            fill(e, m);
            LocalDateTime now = LocalDateTime.now();
            if (exist == null) {
                e.setStatus(1);
                e.setCreateTime(now);
                e.setUpdateTime(now);
                indexMapper.insert(e);
            } else {
                e.setUpdateTime(now);
                indexMapper.updateById(e);
            }
            count++;
        }
        // 配置里已删除的指标：置停用而非物理删除，历史结果仍可按字典回溯。
        // 注意 metrics 为空时上面已提前返回，避免配置加载失败误停用全部指标。
        Set<String> codes = new HashSet<>();
        for (MetricDefinition m : metrics) {
            codes.add(m.getCode());
        }
        int disabled = 0;
        for (String code : indexMapper.selectEnabledCodes()) {
            if (!codes.contains(code)) {
                indexMapper.disableByCode(code, LocalDateTime.now());
                disabled++;
            }
        }
        if (disabled > 0) {
            log.info("[质控] 指标字典停用 {} 条（配置中已删除，保留历史结果）", disabled);
        }
        return count;
    }

    private void fill(QualityIndex e, MetricDefinition m) {
        e.setIndexCode(m.getCode());
        e.setIndexName(m.getName());
        e.setDomainCode(m.getDomain());
        e.setCategoryCode(m.getCategoryCode());
        e.setGroupCode(m.getGroupCode());
        e.setQualityTypeCode(m.getQualityTypeCode());
        e.setIndexStandardCode(m.getIndexStandardCode());
        e.setUnit(m.getUnit());
        e.setValueType(m.getValueType());
        e.setCalcMode(calcMode(m));
        e.setFactName(m.getFact());
        e.setImplStatus(m.getImplStatus() == null ? "IMPL" : m.getImplStatus().toUpperCase());
        e.setExpressionVersion(m.getVersion() == null ? 1 : m.getVersion());
        e.setAmountShowType(m.getAmountShowType());
        e.setAnalysisCountType(m.getAnalysisCountType());
        e.setIsUseZeroShow("RATE".equalsIgnoreCase(m.getValueType()) ? 1 : 0);
        e.setIsShowPatient(1);
        e.setLegacyScript(m.getLegacyScript());
        e.setLegacySource(m.getLegacySource());
        e.setNewTarget(m.getNewTarget());
        e.setReuseLevel(m.getReuseLevel());
        e.setSortNo(m.getSortNo());
        e.setRemark(m.getRemark());
    }

    private String calcMode(MetricDefinition m) {
        String s = m.getImplStatus() == null ? "IMPL" : m.getImplStatus().toUpperCase();
        if ("MANUAL".equals(s)) {
            return "MANUAL";
        }
        if ("CUSTOM_SQL".equalsIgnoreCase(m.getCalcMode())) {
            return "CUSTOM_SQL";
        }
        return StringUtils.hasText(m.getCalcMode()) ? m.getCalcMode() : "DSL";
    }
}
