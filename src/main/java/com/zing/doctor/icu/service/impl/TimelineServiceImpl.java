package com.zing.doctor.icu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zing.doctor.icu.dto.TimelineEvent;
import com.zing.doctor.icu.dto.TimelineResponse;
import com.zing.doctor.icu.service.TimelineService;
import com.zing.doctor.module.antibiotic.entity.AntibioticReassessment;
import com.zing.doctor.module.antibiotic.entity.DecisionRecord;
import com.zing.doctor.module.antibiotic.mapper.AntibioticReassessmentMapper;
import com.zing.doctor.module.antibiotic.mapper.DecisionRecordMapper;
import com.zing.doctor.module.apache2.entity.Apache2ScoreRecord;
import com.zing.doctor.module.apache2.mapper.Apache2ScoreRecordMapper;
import com.zing.doctor.module.ards.prone.entity.ArdsProneRecord;
import com.zing.doctor.module.ards.prone.mapper.ArdsProneRecordMapper;
import com.zing.doctor.module.round.entity.RoundRecord;
import com.zing.doctor.module.round.mapper.RoundRecordMapper;
import com.zing.doctor.module.sepsis.entity.SepsisBundleRecord;
import com.zing.doctor.module.sepsis.mapper.SepsisBundleRecordMapper;
import com.zing.doctor.module.sofa.entity.SofaScoreRecord;
import com.zing.doctor.module.sofa.mapper.SofaScoreRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 临床时间线聚合服务
 *
 * <p>聚合已有事件：SOFA/APACHE评分、抗感染决策/复评、脓毒症集束化、
 * 俯卧位记录、查房记录。不新增数据源，纯聚合。</p>
 * <p>每个模块独立捕获异常，失败时记录到 failedSources，
 * 返回 PARTIAL 状态，避免医生误认为该模块无记录。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimelineServiceImpl implements TimelineService {

    private final SofaScoreRecordMapper sofaMapper;
    private final Apache2ScoreRecordMapper apacheMapper;
    private final DecisionRecordMapper decisionMapper;
    private final AntibioticReassessmentMapper reassessmentMapper;
    private final SepsisBundleRecordMapper sepsisBundleMapper;
    private final ArdsProneRecordMapper proneMapper;
    private final RoundRecordMapper roundMapper;

    @Override
    public TimelineResponse getPatientTimeline(String patientId) {
        long start = System.currentTimeMillis();
        List<TimelineEvent> events = new ArrayList<>();
        List<String> failedSources = new ArrayList<>();

        // SOFA 评分
        try {
            QueryWrapper<SofaScoreRecord> sofaQw = new QueryWrapper<>();
            sofaQw.eq("patient_id", patientId).eq("status", 1)
                    .orderByDesc("score_time").last("LIMIT 10");
            for (SofaScoreRecord r : sofaMapper.selectList(sofaQw)) {
                String result = r.getTotalScore() != null ? "SOFA " + r.getTotalScore() + " 分" : "已评分";
                if (r.getScoreType() != null) result += "（" + r.getScoreType() + "）";
                events.add(new TimelineEvent(r.getScoreTime(), "SOFA", "SOFA 评分", result, "DOCTOR_SCORE"));
            }
        } catch (Exception e) {
            log.warn("时间线-SOFA查询失败: patientId={}, err={}", patientId, e.getMessage());
            failedSources.add("SOFA");
        }

        // APACHE II 评分
        try {
            QueryWrapper<Apache2ScoreRecord> apacheQw = new QueryWrapper<>();
            apacheQw.eq("patient_id", patientId).eq("status", 1)
                    .orderByDesc("score_time").last("LIMIT 10");
            for (Apache2ScoreRecord r : apacheMapper.selectList(apacheQw)) {
                String result = r.getTotalScore() != null ? "APACHE II " + r.getTotalScore() + " 分" : "已评分";
                if (r.getMortalityRate() != null) result += "，预计死亡率 " + r.getMortalityRate() + "%";
                events.add(new TimelineEvent(r.getScoreTime(), "APACHE2", "APACHE II 评分", result, "DOCTOR_SCORE"));
            }
        } catch (Exception e) {
            log.warn("时间线-APACHE查询失败: patientId={}, err={}", patientId, e.getMessage());
            failedSources.add("APACHE2");
        }

        // 抗感染决策
        try {
            QueryWrapper<DecisionRecord> decQw = new QueryWrapper<>();
            decQw.eq("patient_id", patientId).orderByDesc("create_time").last("LIMIT 10");
            for (DecisionRecord r : decisionMapper.selectList(decQw)) {
                String status = r.getDecisionStatus() != null ? r.getDecisionStatus() : "已决策";
                events.add(new TimelineEvent(r.getCreateTime(), "ABX_DECISION", "抗感染决策", status, "DOCTOR_INPUT"));
            }
        } catch (Exception e) {
            log.warn("时间线-抗感染决策查询失败: patientId={}, err={}", patientId, e.getMessage());
            failedSources.add("ABX_DECISION");
        }

        // 抗菌药复评
        try {
            QueryWrapper<AntibioticReassessment> reQw = new QueryWrapper<>();
            reQw.eq("patient_id", patientId).ne("review_status", "VOID")
                    .orderByDesc("review_due_time").last("LIMIT 10");
            for (AntibioticReassessment r : reassessmentMapper.selectList(reQw)) {
                LocalDateTime time = r.getReviewTime() != null ? r.getReviewTime() : r.getReviewDueTime();
                String status = "COMPLETED".equals(r.getReviewStatus()) ? "已复评" :
                        "PENDING".equals(r.getReviewStatus()) ? "待复评" : r.getReviewStatus();
                events.add(new TimelineEvent(time, "ABX_REASSESSMENT", "抗菌药复评", status, "DOCTOR_INPUT"));
            }
        } catch (Exception e) {
            log.warn("时间线-抗菌药复评查询失败: patientId={}, err={}", patientId, e.getMessage());
            failedSources.add("ABX_REASSESSMENT");
        }

        // 脓毒症集束化
        try {
            QueryWrapper<SepsisBundleRecord> sepQw = new QueryWrapper<>();
            sepQw.eq("patient_id", patientId).orderByDesc("create_time").last("LIMIT 10");
            for (SepsisBundleRecord r : sepsisBundleMapper.selectList(sepQw)) {
                events.add(new TimelineEvent(r.getCreateTime(), "SEPSIS_BUNDLE", "脓毒症集束化评估", "已评估", "SYSTEM_CALC"));
            }
        } catch (Exception e) {
            log.warn("时间线-脓毒症集束化查询失败: patientId={}, err={}", patientId, e.getMessage());
            failedSources.add("SEPSIS_BUNDLE");
        }

        // 俯卧位记录
        try {
            QueryWrapper<ArdsProneRecord> proneQw = new QueryWrapper<>();
            proneQw.eq("patient_id", patientId).eq("status", 1)
                    .orderByDesc("start_time").last("LIMIT 10");
            for (ArdsProneRecord r : proneMapper.selectList(proneQw)) {
                String result = r.getEndTime() != null ? "已结束" : "进行中";
                if (r.getProneTimes() != null) result += "，第 " + r.getProneTimes() + " 次";
                events.add(new TimelineEvent(r.getStartTime(), "PRONE", "俯卧位通气", result, "DOCTOR_INPUT"));
            }
        } catch (Exception e) {
            log.warn("时间线-俯卧位查询失败: patientId={}, err={}", patientId, e.getMessage());
            failedSources.add("PRONE");
        }

        // 查房记录
        try {
            QueryWrapper<RoundRecord> roundQw = new QueryWrapper<>();
            roundQw.eq("patient_id", patientId).eq("status", 1)
                    .orderByDesc("round_date").last("LIMIT 10");
            for (RoundRecord r : roundMapper.selectList(roundQw)) {
                LocalDateTime time = r.getUpdateTime() != null ? r.getUpdateTime() :
                        LocalDateTime.of(r.getRoundDate() != null ? r.getRoundDate() : LocalDate.now(), LocalTime.MIN);
                String preview = buildRoundPreview(r);
                events.add(new TimelineEvent(time, "ROUND", "查房记录", preview, "DOCTOR_INPUT"));
            }
        } catch (Exception e) {
            log.warn("时间线-查房记录查询失败: patientId={}, err={}", patientId, e.getMessage());
            failedSources.add("ROUND");
        }

        // 按时间倒序排序，最多50条
        events.sort(Comparator.comparing(TimelineEvent::getTime, Comparator.nullsLast(Comparator.reverseOrder())));
        if (events.size() > 50) {
            events = events.subList(0, 50);
        }

        long cost = System.currentTimeMillis() - start;
        if (cost > 500) {
            log.warn("时间线聚合耗时较长: patientId={}, events={}, failed={}, cost={}ms",
                    patientId, events.size(), failedSources.size(), cost);
        } else {
            log.debug("时间线聚合完成: patientId={}, events={}, failed={}, cost={}ms",
                    patientId, events.size(), failedSources.size(), cost);
        }

        if (failedSources.isEmpty()) {
            return TimelineResponse.ok(events);
        } else {
            return TimelineResponse.partial(events, failedSources);
        }
    }

    /**
     * 构建查房记录摘要：主要问题 + 已填写的关键字段
     */
    private String buildRoundPreview(RoundRecord r) {
        StringBuilder sb = new StringBuilder();
        if (r.getMainProblem() != null && !r.getMainProblem().isEmpty()) {
            String mp = r.getMainProblem();
            sb.append(mp.length() > 30 ? mp.substring(0, 30) + "..." : mp);
        }
        int filled = 0;
        StringBuilder extras = new StringBuilder();
        if (r.getInfectionJudgment() != null && !r.getInfectionJudgment().isEmpty()) {
            filled++;
            if (extras.length() > 0) extras.append("｜");
            extras.append("感染").append(truncate(r.getInfectionJudgment(), 15));
        }
        if (r.getAbxPlan() != null && !r.getAbxPlan().isEmpty()) {
            filled++;
            if (extras.length() > 0) extras.append("｜");
            extras.append("抗菌药").append(truncate(r.getAbxPlan(), 15));
        }
        if (r.getRespiratoryPlan() != null && !r.getRespiratoryPlan().isEmpty()) filled++;
        if (r.getCirculatoryPlan() != null && !r.getCirculatoryPlan().isEmpty()) filled++;
        if (r.getRenalSedationPlan() != null && !r.getRenalSedationPlan().isEmpty()) filled++;
        if (r.getRecheckItems() != null && !r.getRecheckItems().isEmpty()) filled++;
        if (r.getTreatmentGoal() != null && !r.getTreatmentGoal().isEmpty()) filled++;
        if (r.getTomorrowFocus() != null && !r.getTomorrowFocus().isEmpty()) filled++;

        if (extras.length() > 0) {
            if (sb.length() > 0) sb.append("｜");
            sb.append(extras);
        }
        if (filled > 0) {
            if (sb.length() > 0) sb.append("｜");
            sb.append("共").append(filled + 1).append("项");
        }
        return sb.length() > 0 ? sb.toString() : "已记录";
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
