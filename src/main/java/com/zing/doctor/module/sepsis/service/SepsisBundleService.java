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
