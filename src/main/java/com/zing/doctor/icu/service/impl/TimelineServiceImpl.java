package com.zing.doctor.icu.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zing.doctor.icu.dto.TimelineEvent;
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
 * <p>聚合已有事件：入科、SOFA/APACHE评分、抗感染决策/复评、脓毒症集束化、
 * 俯卧位记录、查房记录。不新增数据源，纯聚合。</p>
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
    public List<TimelineEvent> getPatientTimeline(String patientId) {
        List<TimelineEvent> events = new ArrayList<>();

        try {
            // SOFA 评分
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
        }

        try {
            // APACHE II 评分
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
        }

        try {
            // 抗感染决策
            QueryWrapper<DecisionRecord> decQw = new QueryWrapper<>();
            decQw.eq("patient_id", patientId).orderByDesc("create_time").last("LIMIT 10");
            for (DecisionRecord r : decisionMapper.selectList(decQw)) {
                String status = r.getDecisionStatus() != null ? r.getDecisionStatus() : "已决策";
                events.add(new TimelineEvent(r.getCreateTime(), "ABX_DECISION", "抗感染决策", status, "DOCTOR_INPUT"));
            }
        } catch (Exception e) {
            log.warn("时间线-抗感染决策查询失败: patientId={}, err={}", patientId, e.getMessage());
        }

        try {
            // 抗菌药复评
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
        }

        try {
            // 脓毒症集束化
            QueryWrapper<SepsisBundleRecord> sepQw = new QueryWrapper<>();
            sepQw.eq("patient_id", patientId).orderByDesc("create_time").last("LIMIT 10");
            for (SepsisBundleRecord r : sepsisBundleMapper.selectList(sepQw)) {
                events.add(new TimelineEvent(r.getCreateTime(), "SEPSIS_BUNDLE", "脓毒症集束化评估", "已评估", "SYSTEM_CALC"));
            }
        } catch (Exception e) {
            log.warn("时间线-脓毒症集束化查询失败: patientId={}, err={}", patientId, e.getMessage());
        }

        try {
            // 俯卧位记录
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
        }

        try {
            // 查房记录
            QueryWrapper<RoundRecord> roundQw = new QueryWrapper<>();
            roundQw.eq("patient_id", patientId).eq("status", 1)
                    .orderByDesc("round_date").last("LIMIT 10");
            for (RoundRecord r : roundMapper.selectList(roundQw)) {
                LocalDateTime time = r.getUpdateTime() != null ? r.getUpdateTime() :
                        LocalDateTime.of(r.getRoundDate() != null ? r.getRoundDate() : LocalDate.now(), LocalTime.MIN);
                String preview = r.getMainProblem() != null && !r.getMainProblem().isEmpty()
                        ? r.getMainProblem().length() > 40 ? r.getMainProblem().substring(0, 40) + "..." : r.getMainProblem()
                        : "已记录";
                events.add(new TimelineEvent(time, "ROUND", "查房记录", preview, "DOCTOR_INPUT"));
            }
        } catch (Exception e) {
            log.warn("时间线-查房记录查询失败: patientId={}, err={}", patientId, e.getMessage());
        }

        // 按时间倒序排序，最多50条
        events.sort(Comparator.comparing(TimelineEvent::getTime, Comparator.nullsLast(Comparator.reverseOrder())));
        if (events.size() > 50) {
            events = events.subList(0, 50);
        }
        return events;
    }
}
