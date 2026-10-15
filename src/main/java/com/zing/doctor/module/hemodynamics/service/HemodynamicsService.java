package com.zing.doctor.module.hemodynamics.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zing.doctor.common.OperatorContext;
import com.zing.doctor.module.hemodynamics.entity.HemodynamicsRecord;
import com.zing.doctor.module.hemodynamics.mapper.HemodynamicsRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 血流动力学 / 心功能手工录入服务（保存 / 趋势 / 逻辑删除）。
 *
 * <p>派生计算（MAP 补算、SVR 公式）在服务端完成，前端只采集原始值；
 * 录入与查询同一套公式，历史记录修改后重算覆盖。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HemodynamicsService {

    private final HemodynamicsRecordMapper mapper;

    /** 患者的全部有效记录（时间升序，供趋势图）；上限 200 条。 */
    public List<HemodynamicsRecord> listByPatient(String patientId) {
        QueryWrapper<HemodynamicsRecord> qw = new QueryWrapper<>();
        qw.eq("patient_id", patientId).eq("status", 1);
        qw.orderByAsc("measure_time").last("LIMIT 200");
        return mapper.selectList(qw);
    }

    /** 新增 / 更新（同一患者 + 同一测量时间视为同一条记录的重复提交，覆盖更新）。 */
    public HemodynamicsRecord save(HemodynamicsRecord record) {
        String operator = OperatorContext.current();
        LocalDateTime now = LocalDateTime.now();
        if (record.getMeasureTime() == null) {
            record.setMeasureTime(now);
        }
        derive(record);

        QueryWrapper<HemodynamicsRecord> qw = new QueryWrapper<>();
        qw.eq("patient_id", record.getPatientId())
                .eq("measure_time", record.getMeasureTime())
                .eq("status", 1)
                .last("LIMIT 1");
        HemodynamicsRecord existing = mapper.selectOne(qw);

        if (existing != null) {
            record.setId(existing.getId());
            record.setCreateBy(existing.getCreateBy());
            record.setCreateTime(existing.getCreateTime());
            record.setUpdateBy(operator);
            record.setUpdateTime(now);
            record.setStatus(1);
            mapper.updateById(record);
            log.info("血流动力学记录更新: id={}, patientId={}, operator={}",
                    existing.getId(), existing.getPatientId(), operator);
            return record;
        }

        record.setStatus(1);
        record.setCreateBy(operator);
        record.setCreateTime(now);
        record.setUpdateBy(operator);
        record.setUpdateTime(now);
        mapper.insert(record);
        log.info("血流动力学记录新建: id={}, patientId={}, measureTime={}, operator={}",
                record.getId(), record.getPatientId(), record.getMeasureTime(), operator);
        return record;
    }

    /** 逻辑删除（id + patient_id 归属校验，防止跨患者删除）。 */
    public int deleteById(Long id, String patientId) {
        if (id == null || patientId == null || patientId.isEmpty()) {
            return 0;
        }
        QueryWrapper<HemodynamicsRecord> qw = new QueryWrapper<>();
        qw.eq("id", id).eq("patient_id", patientId).eq("status", 1).last("LIMIT 1");
        HemodynamicsRecord existing = mapper.selectOne(qw);
        if (existing == null) {
            return 0;
        }
        existing.setStatus(0);
        existing.setUpdateBy(OperatorContext.current());
        existing.setUpdateTime(LocalDateTime.now());
        return mapper.updateById(existing);
    }

    /**
     * 派生计算：
     * MAP = DBP + (SBP − DBP)/3（未直接录入且 SBP/DBP 齐全时）；
     * SVR = (MAP − CVP)/CO × 80（三项齐全且 CO>0）。
     */
    static void derive(HemodynamicsRecord r) {
        if (r.getMapVal() == null && r.getSbp() != null && r.getDbp() != null && r.getDbp() > 0) {
            r.setMapVal(round1(r.getDbp() + (r.getSbp() - r.getDbp()) / 3.0));
        }
        if (r.getSvr() == null && r.getMapVal() != null && r.getCvp() != null && r.getCo() != null && r.getCo() > 0) {
            r.setSvr(round1((r.getMapVal() - r.getCvp()) / r.getCo() * 80.0));
        }
    }

    private static Double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
