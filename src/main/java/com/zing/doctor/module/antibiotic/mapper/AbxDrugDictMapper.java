package com.zing.doctor.module.antibiotic.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zing.doctor.module.antibiotic.entity.AbxDrugDict;
import org.apache.ibatis.annotations.Mapper;

/**
 * 抗菌药物字典 Mapper（医生库 zing_doctor_db_prod.zing_abx_drug_dict）。
 *
 * <p>本表由 {@code AbxDrugSyncTask} 从 ICU 药品字典同步，读多写少：
 * <ul>
 *   <li>写：{@code AbxDrugDictServiceImpl#syncFromIcu} 增量新增/更新/置失效；</li>
 *   <li>读：{@code AbxDrugRecognizer} 构建名称快照（仅取 status=1）。</li>
 * </ul>
 */
@Mapper
public interface AbxDrugDictMapper extends BaseMapper<AbxDrugDict> {
}
