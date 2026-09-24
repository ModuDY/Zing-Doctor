package com.zing.doctor.icu.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.module.apache2.entity.Apache2ScoreRecord;
import com.zing.doctor.module.apache2.mapper.Apache2ScoreRecordMapper;
import com.zing.doctor.module.sofa.entity.SofaScoreRecord;
import com.zing.doctor.module.sofa.mapper.SofaScoreRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 患者工作台待办与评分 enrichment（本系统主库，非 ICU 库）。
 *
 * <p>与 {@link com.zing.doctor.icu.service.impl.SqlIcuPatientServiceImpl}（@DS("icu")）
 * 分开：后者只读 ICU 信息库；本服务查本系统自己的评分文书表
 * （patient_doc_sofa_score_record / patient_doc_apache2_score_record），
 * 用 MyBatis-Plus QueryWrapper 批量取，避免逐患者 N+1。
 *
 * <p>待办口径（第一期，克制）：
 * <ul>
 *   <li>{@code SOFA_NOT_TODAY} —— 入科 ≥24h 但今天还没有 SOFA 评分记录。
 *       新入科（icuDays ≤1）不催，给医生留当天入科评估的时间窗。</li>
 *   <li>{@code APACHE_NOT_TODAY} —— 入科当天应评 admission，24h 内应有记录；
 *       超过 24h 仍没有任何 APACHE 记录才提醒。</li>
 * </ul>
 * 抗生素无血培养 / 导管超期等需要回 ICU 库逐患者比对，放第二期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkbenchEnrichService {

    private final SofaScoreRecordMapper sofaMapper;
    private final Apache2ScoreRecordMapper apacheMapper;

    /**
     * 给在科患者列表回填：最近 SOFA 总分、当日待办。
     * 直接修改入参列表，不返回新对象。
     */
    public void enrich(List<WorkbenchPatient> patients) {
        if (patients == null || patients.isEmpty()) {
            return;
        }
        List<String> patientIds = new ArrayList<>(patients.size());
        for (WorkbenchPatient p : patients) {
            if (p.getPatientId() != null && !p.getPatientId().isEmpty()) {
                patientIds.add(p.getPatientId());
            }
        }
        if (patientIds.isEmpty()) {
            return;
        }

        // 1) 最近一次 SOFA 总分（每个患者取 score_time 最新的一条）
        Map<String, SofaScoreRecord> latestSofa = latestSofaByPatient(patientIds);
        // 2) 今天有 SOFA 评分的 patientId 集合
        Set<String> sofaToday = scoredToday("sofa", patientIds);
        // 3) 今天/近 24h 有 APACHE 评分的 patientId 集合
        Set<String> apacheToday = scoredToday("apache", patientIds);

        LocalDate today = LocalDate.now();
        for (WorkbenchPatient p : patients) {
            SofaScoreRecord s = latestSofa.get(p.getPatientId());
            if (s != null) {
                p.setLastSofaScore(s.getTotalScore());
            }

            List<String> todos = new ArrayList<>(2);
            long icuDays = p.getIcuDays() == null ? 0 : p.getIcuDays();
            // 入科 ≥24h 还没评 SOFA
            if (icuDays >= 1 && !sofaToday.contains(p.getPatientId())) {
                todos.add("SOFA_NOT_TODAY");
            }
            // 入科 ≥24h 还没有任何 APACHE 记录（admission 应在入科 24h 内完成）
            if (icuDays >= 1 && !apacheToday.contains(p.getPatientId())) {
                todos.add("APACHE_NOT_TODAY");
            }
            p.setTodos(todos);
            p.setTodoCount(todos.size());
        }
    }

    /** 每个 patientId 取 score_time 最大的一条 SOFA 记录（不返回 pdf_data 大字段）。 */
    private Map<String, SofaScoreRecord> latestSofaByPatient(List<String> patientIds) {
        QueryWrapper<SofaScoreRecord> qw = new QueryWrapper<>();
        qw.select("patient_id", "total_score", "score_time")
                .in("patient_id", patientIds)
                .eq("status", 1)
                .orderByDesc("score_time");
        List<SofaScoreRecord> list = sofaMapper.selectList(qw);
        Map<String, SofaScoreRecord> out = new HashMap<>();
        for (SofaScoreRecord r : list) {
            // 已按 score_time 倒序，putIfAbsent 保留每个 patient 的第一条（最新）
            out.putIfAbsent(r.getPatientId(), r);
        }
        return out;
    }

    /** 今天 [今天 00:00, 明天 00:00) 有评分记录的 patientId 集合。 */
    private Set<String> scoredToday(String which, List<String> patientIds) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        Set<String> out = new HashSet<>();
        try {
            if ("sofa".equals(which)) {
                QueryWrapper<SofaScoreRecord> qw = new QueryWrapper<>();
                qw.select("patient_id")
                        .in("patient_id", patientIds)
                        .eq("status", 1)
                        .ge("score_time", start)
                        .lt("score_time", end);
                for (SofaScoreRecord r : sofaMapper.selectList(qw)) {
                    out.add(r.getPatientId());
                }
            } else {
                QueryWrapper<Apache2ScoreRecord> qw = new QueryWrapper<>();
                qw.select("patient_id")
                        .in("patient_id", patientIds)
                        .eq("status", 1)
                        .ge("score_time", start)
                        .lt("score_time", end);
                for (Apache2ScoreRecord r : apacheMapper.selectList(qw)) {
                    out.add(r.getPatientId());
                }
            }
        } catch (Exception e) {
            // 主库不可用或表未建时，待办降级为空，不拖垮工作台主流程
            log.warn("工作台待办查询失败({})，降级为无待办", which, e);
        }
        return out;
    }
}
