package com.zing.doctor.module.deviceinfection.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zing.doctor.common.OperatorContext;
import com.zing.doctor.module.deviceinfection.entity.DeviceInfectionRecord;
import com.zing.doctor.module.deviceinfection.mapper.DeviceInfectionRecordMapper;
import com.zing.doctor.module.deviceinfection.service.DeviceInfectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 导管相关感染确诊录入实现。
 *
 * <p>与查房记录同一个套路：操作人由服务端解析、不采信请求体； departCode /
 * patientName / inHospitalNo 由 Controller 从患者基础信息覆写。删除走逻辑删除，
 * 已确认的历史确诊不允许物理删除（质控结果要可回溯）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceInfectionServiceImpl implements DeviceInfectionService {

    private final DeviceInfectionRecordMapper mapper;

    @Override
    public List<DeviceInfectionRecord> listByPatient(String patientId, String infectionType) {
        QueryWrapper<DeviceInfectionRecord> qw = new QueryWrapper<>();
        qw.eq("patient_id", patientId).eq("status", 1);
        if (infectionType != null && !infectionType.isEmpty()) {
            qw.eq("infection_type", infectionType);
        }
        qw.orderByDesc("confirm_time").last("LIMIT 100");
        return mapper.selectList(qw);
    }

    @Override
    public List<DeviceInfectionRecord> listRecent(String departCode, String infectionType,
                                                  LocalDateTime start, LocalDateTime end, int limit) {
        QueryWrapper<DeviceInfectionRecord> qw = new QueryWrapper<>();
        qw.eq("status", 1);
        if (departCode != null && !departCode.isEmpty() && !"ALL".equalsIgnoreCase(departCode)) {
            qw.eq("depart_code", departCode);
        }
        if (infectionType != null && !infectionType.isEmpty()) {
            qw.eq("infection_type", infectionType);
        }
        if (start != null) {
            qw.ge("confirm_time", start);
        }
        if (end != null) {
            qw.lt("confirm_time", end);
        }
        qw.orderByDesc("confirm_time").last("LIMIT " + Math.max(1, Math.min(limit, 500)));
        return mapper.selectList(qw);
    }

    @Override
    public DeviceInfectionRecord save(DeviceInfectionRecord record) {
        String operator = OperatorContext.current();
        LocalDateTime now = LocalDateTime.now();

        if (record.getConfirmTime() == null) {
            record.setConfirmTime(LocalDateTime.now());
        }

        // 同一患者 + 同一类型 + 同一确诊时间视为一条记录的重复提交，更新覆盖
        QueryWrapper<DeviceInfectionRecord> qw = new QueryWrapper<>();
        qw.eq("patient_id", record.getPatientId())
                .eq("infection_type", record.getInfectionType())
                .eq("confirm_time", record.getConfirmTime())
                .last("LIMIT 1");
        DeviceInfectionRecord existing = mapper.selectOne(qw);

        if (existing != null) {
            existing.setDiagnosisBasis(record.getDiagnosisBasis());
            existing.setClinicalEvidence(record.getClinicalEvidence());
            existing.setPathogen(record.getPathogen());
            existing.setRelatedTubeCode(record.getRelatedTubeCode());
            existing.setRelatedTubeName(record.getRelatedTubeName());
            existing.setDoctorName(record.getDoctorName());
            existing.setDoctorWorkNo(record.getDoctorWorkNo());
            existing.setRemark(record.getRemark());
            existing.setStatus(1);
            existing.setUpdateBy(operator);
            existing.setUpdateTime(now);
            mapper.updateById(existing);
            log.info("导管感染确诊更新: id={}, patientId={}, type={}, operator={}",
                    existing.getId(), existing.getPatientId(), existing.getInfectionType(), operator);
            return existing;
        }

        record.setStatus(1);
        record.setCreateBy(operator);
        record.setCreateTime(now);
        record.setUpdateBy(operator);
        record.setUpdateTime(now);
        mapper.insert(record);
        log.info("导管感染确诊新建: id={}, patientId={}, type={}, confirmTime={}, operator={}",
                record.getId(), record.getPatientId(), record.getInfectionType(),
                record.getConfirmTime(), operator);
        return record;
    }

    @Override
    public int deleteById(Long id, String patientId) {
        if (id == null || patientId == null || patientId.isEmpty()) {
            return 0;
        }
        // 按 id + patient_id 同时匹配：删除前先做归属校验，
        // 否则调用方只要传一个自己有权限的 patientId 就能删掉别人的记录
        QueryWrapper<DeviceInfectionRecord> qw = new QueryWrapper<>();
        qw.eq("id", id).eq("patient_id", patientId).eq("status", 1).last("LIMIT 1");
        DeviceInfectionRecord existing = mapper.selectOne(qw);
        if (existing == null) {
            return 0;
        }
        existing.setStatus(0);
        existing.setUpdateBy(OperatorContext.current());
        existing.setUpdateTime(LocalDateTime.now());
        int rows = mapper.updateById(existing);
        log.info("导管感染确诊删除: id={}, patientId={}, type={}",
                id, existing.getPatientId(), existing.getInfectionType());
        return rows;
    }
}
