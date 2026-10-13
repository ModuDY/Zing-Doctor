package com.zing.doctor.module.department.service;

import com.zing.doctor.module.department.dto.CockpitSnapshot;

/**
 * 科室驾驶舱（阶段1：今日运营快照 + 近 N 天患者流转）。
 *
 * <p>只读聚合。口径见 {@code docs/23-科室驾驶舱-实施方案.md} §3，实现在
 * {@code DepartmentCockpitMapper} 的注释里逐条对应。
 */
public interface DepartmentCockpitService {

    /**
     * 今日快照 + 近 N 天流转。
     *
     * @param departCode 已按账号授权收敛后的科室编码；空串 = 全院（由上层 resolveQueryDepart 保证合法）
     * @param days       流转趋势天数，1~90，默认 30
     */
    CockpitSnapshot snapshot(String departCode, int days);
}
