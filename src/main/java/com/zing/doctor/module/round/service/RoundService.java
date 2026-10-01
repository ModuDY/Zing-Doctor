package com.zing.doctor.module.round.service;

import com.zing.doctor.module.round.entity.RoundRecord;

import java.time.LocalDate;
import java.util.List;

public interface RoundService {

    /**
     * 获取指定患者指定日期的查房记录；不存在返回 null。
     */
    RoundRecord getByDate(String patientId, LocalDate roundDate);

    /**
     * 获取指定患者的历史查房记录列表（按日期倒序）。
     */
    List<RoundRecord> listHistory(String patientId);

    /**
     * 保存查房记录。同一患者同一天只保留一份：
     * - 已存在则更新（保留 createBy/createTime，更新 updateBy/updateTime）
     * 不存在则新建
     */
    RoundRecord save(RoundRecord record);

    /**
     * 逻辑删除指定患者指定日期的查房记录（status=0）。
     * 返回被删除的记录数，0 表示没有找到记录。
     */
    int deleteByDate(String patientId, LocalDate roundDate);
}
