package com.zing.doctor.module.deviceinfection.service;

import com.zing.doctor.module.deviceinfection.entity.DeviceInfectionRecord;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 导管相关感染确诊录入服务（VAP / CRBSI / CAUTI）。
 */
public interface DeviceInfectionService {

    /** 患者的确诊记录列表（按确诊时间倒序；infectionType 可空） */
    List<DeviceInfectionRecord> listByPatient(String patientId, String infectionType);

    /**
     * 科室近期的确诊记录（供质控核对/清单页展示）。
     *
     * @param departCode 科室编码；null/ALL = 当前账号可见范围
     * @param start      起始时间（含）
     * @param end        结束时间（不含）
     */
    List<DeviceInfectionRecord> listRecent(String departCode, String infectionType,
                                          LocalDateTime start, LocalDateTime end, int limit);

    /** 新增或更新确诊记录（同一患者同一类型同一确诊时间只保留一份，更新覆盖） */
    DeviceInfectionRecord save(DeviceInfectionRecord record);

    /**
     * 逻辑删除（status=0）。
     *
     * @param patientId 归属校验用的患者 ID：记录必须属于该患者才允许删除，
     *                  避免「拿一个自己有科室权限的 patientId + 别人的记录 id」跨科室删数据
     * @return 受影响行数；记录不存在 / 已删除 / 不属于该患者均返回 0
     */
    int deleteById(Long id, String patientId);
}
