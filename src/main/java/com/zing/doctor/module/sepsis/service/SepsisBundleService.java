package com.zing.doctor.module.sepsis.service;

import com.zing.doctor.module.sepsis.dto.SepsisBundleView;
import com.zing.doctor.module.sepsis.entity.SepsisBundleRecord;

import java.util.List;

public interface SepsisBundleService {

    /**
     * 获取患者集束化治疗详情（自动判断1H/3H/6H项目完成情况）
     */
    SepsisBundleView getBundleDetail(String inHospitalNo);

    /**
     * 预览计算：按给定的记录时间 / 确诊时间跑一遍自动评估，<b>不落库</b>。
     * 供「新建评估」和「改时间后重新评估」使用。
     *
     * @param inHospitalNo  住院号
     * @param recordTime    记录时间（yyyy-MM-dd HH:mm:ss），决定三块系统参考的 14 天窗口结束点；为空则用当前时间
     * @param diagnosisTime 确诊时间（yyyy-MM-dd HH:mm:ss）；为空则由后端从诊断信息推断
     * @param id            在该记录基础上重算（保留它已保存的手动勾选）；为 null 表示纯新建预览
     */
    SepsisBundleView calculateBundle(String inHospitalNo, String recordTime, String diagnosisTime, Long id);

    /**
     * 获取患者历史评估记录列表
     */
    List<SepsisBundleRecord> getHistoryList(String inHospitalNo);

    /**
     * 根据ID获取评估记录详情
     */
    SepsisBundleView getBundleDetailById(Long id);

    /**
     * 保存集束化治疗记录
     */
    SepsisBundleRecord saveBundle(SepsisBundleRecord record);

    /**
     * 更新集束化治疗记录
     */
    SepsisBundleRecord updateBundle(SepsisBundleRecord record);

    /**
     * 根据住院号查询记录
     */
    SepsisBundleRecord getByInHospitalNo(String inHospitalNo);

    /**
     * 删除评估记录（支持多次评估逐条删除）
     */
    boolean deleteById(Long id);
}
