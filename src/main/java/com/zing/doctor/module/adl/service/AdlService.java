package com.zing.doctor.module.adl.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zing.doctor.common.OperatorContext;
import com.zing.doctor.module.adl.entity.AdlRecord;
import com.zing.doctor.module.adl.mapper.AdlRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ADL（Barthel 指数）手工录入服务：保存（总分服务端求和）/ 趋势 / 逻辑删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdlService {

    private final AdlRecordMapper mapper;

    /** 患者全部有效评估（时间升序，供趋势图）；上限 200 条。 */
    public List<AdlRecord> listByPatient(String patientId) {
        QueryWrapper<AdlRecord> qw = new QueryWrapper<>();
        qw.eq("patient_id", patientId).eq("status", 1);
        qw.orderByAsc("adl_time").last("LIMIT 200");
        return mapper.selectList(qw);
    }

    /** 新增 / 更新（同一患者 + 同一评估时间视为同一条记录的重复提交，覆盖更新）。 */
    public AdlRecord save(AdlRecord record) {
        String operator = OperatorContext.current();
        LocalDateTime now = LocalDateTime.now();
        if (record.getAdlTime() == null) {
            record.setAdlTime(now);
        }
        record.setTotalScore(totalOf(record));

        QueryWrapper<AdlRecord> qw = new QueryWrapper<>();
        qw.eq("patient_id", record.getPatientId())
                .eq("adl_time", record.getAdlTime())
                .eq("status", 1)
                .last("LIMIT 1");
        AdlRecord existing = mapper.selectOne(qw);

        if (existing != null) {
            record.setId(existing.getId());
            record.setCreateBy(existing.getCreateBy());
            record.setCreateTime(existing.getCreateTime());
            record.setUpdateBy(operator);
            record.setUpdateTime(now);
            record.setStatus(1);
            mapper.updateById(record);
            log.info("ADL 评估更新: id={}, patientId={}, operator={}",
                    existing.getId(), existing.getPatientId(), operator);
            return record;
        }

        record.setStatus(1);
        record.setCreateBy(operator);
        record.setCreateTime(now);
        record.setUpdateBy(operator);
        record.setUpdateTime(now);
        mapper.insert(record);
        log.info("ADL 评估新建: id={}, patientId={}, adlTime={}, total={}, operator={}",
                record.getId(), record.getPatientId(), record.getAdlTime(), record.getTotalScore(), operator);
        return record;
    }

    /** 逻辑删除（id + patient_id 归属校验）。 */
    public int deleteById(Long id, String patientId) {
        if (id == null || patientId == null || patientId.isEmpty()) {
            return 0;
        }
        QueryWrapper<AdlRecord> qw = new QueryWrapper<>();
        qw.eq("id", id).eq("patient_id", patientId).eq("status", 1).last("LIMIT 1");
        AdlRecord existing = mapper.selectOne(qw);
        if (existing == null) {
            return 0;
        }
        existing.setStatus(0);
        existing.setUpdateBy(OperatorContext.current());
        existing.setUpdateTime(LocalDateTime.now());
        return mapper.updateById(existing);
    }

    /** Barthel 十项求和（null 按 0 计，整体全空则总分 null）。 */
    static Integer totalOf(AdlRecord r) {
        Integer[] items = {r.getFeeding(), r.getBathing(), r.getGrooming(), r.getDressing(),
                r.getBowels(), r.getBladder(), r.getToilet(), r.getTransfer(),
                r.getMobility(), r.getStairs()};
        int sum = 0;
        boolean any = false;
        for (Integer v : items) {
            if (v != null) {
                any = true;
                sum += v;
            }
        }
        return any ? sum : null;
    }
}
