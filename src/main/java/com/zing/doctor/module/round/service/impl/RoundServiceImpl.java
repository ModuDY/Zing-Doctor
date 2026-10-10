package com.zing.doctor.module.round.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zing.doctor.common.OperatorContext;
import com.zing.doctor.module.round.entity.RoundRecord;
import com.zing.doctor.module.round.mapper.RoundRecordMapper;
import com.zing.doctor.module.round.service.RoundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoundServiceImpl implements RoundService {

    private final RoundRecordMapper roundRecordMapper;

    @Override
    public RoundRecord getByDate(String patientId, LocalDate roundDate) {
        QueryWrapper<RoundRecord> qw = new QueryWrapper<>();
        qw.eq("patient_id", patientId)
                .eq("round_date", roundDate)
                .eq("status", 1)
                .last("LIMIT 1");
        return roundRecordMapper.selectOne(qw);
    }

    @Override
    public List<RoundRecord> listHistory(String patientId) {
        QueryWrapper<RoundRecord> qw = new QueryWrapper<>();
        qw.eq("patient_id", patientId)
                .eq("status", 1)
                .orderByDesc("round_date")
                .last("LIMIT 30");
        return roundRecordMapper.selectList(qw);
    }

    @Override
    public RoundRecord save(RoundRecord record) {
        // 操作人由服务端解析并覆盖，不采信请求体里的 createBy
        String operator = OperatorContext.current();
        LocalDateTime now = LocalDateTime.now();

        if (record.getRoundDate() == null) {
            record.setRoundDate(LocalDate.now());
        }

        // 同一患者同一天只保留一份：查已有记录（含已删除，避免唯一索引冲突）
        QueryWrapper<RoundRecord> qw = new QueryWrapper<>();
        qw.eq("patient_id", record.getPatientId())
                .eq("round_date", record.getRoundDate())
                .last("LIMIT 1");
        RoundRecord existing = roundRecordMapper.selectOne(qw);

        if (existing != null) {
            // 更新：保留 createBy/createTime，恢复 status=1（可能之前被逻辑删除），更新业务字段
            existing.setStatus(1);
            existing.setMainProblem(record.getMainProblem());
            existing.setInfectionJudgment(record.getInfectionJudgment());
            existing.setRespiratoryPlan(record.getRespiratoryPlan());
            existing.setCirculatoryPlan(record.getCirculatoryPlan());
            existing.setRenalSedationPlan(record.getRenalSedationPlan());
            existing.setAbxPlan(record.getAbxPlan());
            existing.setRecheckItems(record.getRecheckItems());
            existing.setTreatmentGoal(record.getTreatmentGoal());
            existing.setTomorrowFocus(record.getTomorrowFocus());
            existing.setUpdateBy(operator);
            existing.setUpdateTime(now);
            roundRecordMapper.updateById(existing);
            log.info("查房记录更新: id={}, patientId={}, roundDate={}, operator={}",
                    existing.getId(), existing.getPatientId(), existing.getRoundDate(), operator);
            return existing;
        } else {
            // 新建
            record.setStatus(1);
            record.setCreateBy(operator);
            record.setCreateTime(now);
            record.setUpdateBy(operator);
            record.setUpdateTime(now);
            roundRecordMapper.insert(record);
            log.info("查房记录新建: id={}, patientId={}, roundDate={}, operator={}",
                    record.getId(), record.getPatientId(), record.getRoundDate(), operator);
            return record;
        }
    }

    @Override
    public int deleteByDate(String patientId, LocalDate roundDate) {
        String operator = OperatorContext.current();
        LocalDateTime now = LocalDateTime.now();
        RoundRecord existing = getByDate(patientId, roundDate);
        if (existing == null) {
            return 0;
        }
        existing.setStatus(0);
        existing.setUpdateBy(operator);
        existing.setUpdateTime(now);
        int rows = roundRecordMapper.updateById(existing);
        log.info("查房记录删除: id={}, patientId={}, roundDate={}, operator={}",
                existing.getId(), patientId, roundDate, operator);
        return rows;
    }
}
