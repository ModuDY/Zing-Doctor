package com.zing.doctor.module.handover.service;

import com.zing.doctor.module.handover.dto.HandoverOverview;
import com.zing.doctor.module.handover.dto.HandoverPatientDetail;
import com.zing.doctor.module.handover.entity.HandoverNote;

import java.util.List;
import java.util.Map;

/**
 * 医生交班览表 Service。
 * 默认取数区间 = 最近一个已完整封板的全天班次（config_shift.is_all=1）。
 */
public interface HandoverService {

    /** 科室交班总览（在科患者卡片 + 汇总统计），shiftDate 为空时默认取最近一个已封板全天班次 */
    HandoverOverview getWardOverview(String departCode, String shiftDate);

    /** 单患者交班详情（抽屉懒加载），shiftDate 为空时默认取最近一个已封板全天班次 */
    HandoverPatientDetail getPatientDetail(String inHospitalNo, String shiftDate);

    /** 该患者"上一个班次"的手工交班记录（供医生一键导入本班输入框），没有则返回 null */
    HandoverNote getPreviousNote(String inHospitalNo, String shiftDate);

    /** 保存/更新本班病情变化（一患者一班一条，重复提交走更新，保留首次创建人/时间） */
    HandoverNote saveNote(HandoverNote note);

    /** 逻辑删除一条交班记录 */
    boolean deleteNote(Long id);

    /** 启用科室下拉（sys_depart） */
    List<Map<String, Object>> listDepartments();

    /** 患者出科统计：按出科时间范围和科室查询 */
    List<Map<String, Object>> listDischargedPatients(String startTime, String endTime, String departCode);

    /** 导出患者出科统计CSV（UTF-8 BOM，Excel可直接打开） */
    byte[] exportDischargedCsv(String startTime, String endTime, String departCode);
}
