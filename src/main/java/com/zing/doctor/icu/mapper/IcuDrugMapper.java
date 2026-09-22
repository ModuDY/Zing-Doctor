package com.zing.doctor.icu.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * ICU 只读「药品字典」Mapper（达梦 schema：zing_icu_db_prod）。
 *
 * <p>数据表 {@code config_drug} 是 HIS/重症系统的全院药品字典，其中
 * {@code is_antibiotics = '1'} 表示该药品为抗菌药——这是「哪种药是抗菌药」的权威来源，
 * 比关键词白名单可靠得多（能覆盖新药、商品名、复方制剂）。
 *
 * <p>同步策略：{@code AbxDrugSyncTask} 夜间（默认 03:20）全量拉取本查询结果，
 * 按 {@code drug_code} 与医生库 {@code config_abx_drug_dict} 做增量比对。
 * 本 Mapper 只读（数据源 read-only: true），绝不写 ICU。
 *
 * <p>达梦方言注意事项：
 * <ul>
 *   <li>标识符用双引号保留小写："{@code zing_icu_db_prod}"."config_drug"；</li>
 *   <li>用 {@code SELECT *} 而非逐个列出列名：config_drug 是 HIS 侧表，不同医院版本的列
 *       不完全一致（可能没有 drug_normal_name / drug_pinyin 等），显式引用不存在的列会让
 *       整条查询报错、同步直接失败；{@code SELECT *} 最多只是取不到值为 null，
 *       由 Service 端按列名取值时兜底为空串；</li>
 *   <li>del_flag 用字符串比较（达梦隐式转换），不 CAST 数值以免对异常值抛错。</li>
 * </ul>
 */
@DS("icu")
@Mapper
public interface IcuDrugMapper {

    /**
     * 拉取 HIS 药品字典中的全部抗菌药（is_antibiotics = '1'）。
     *
     * <p>关于过滤条件：
     * <ul>
     *   <li>{@code is_antibiotics = '1'}：只同步 HIS 标记为抗菌药的药品；</li>
     *   <li>{@code del_flag = '0' OR del_flag IS NULL}：排除 HIS 已删除的药品；</li>
     *   <li><b>不过滤 status</b>：药品被 HIS 停用（status != 1）不代表它不是抗菌药，
     *       而历史医嘱里仍可能出现该药名，保留它能提升历史医嘱的识别率。</li>
     * </ul>
     *
     * @return 每行为「列名 → 值」的映射（Service 端统一转小写后按 drug_code / drug_name /
     *         drug_short_name / drug_normal_name / drug_pinyin / spec / dose / unit_code /
     *         drug_factory_name / is_antibiotics / antibiotics_color 取值，取不到即空串）
     */
    @Select("SELECT * FROM \"zing_icu_db_prod\".\"config_drug\" "
            + "WHERE \"is_antibiotics\" = '1' "
            + "AND (\"del_flag\" = '0' OR \"del_flag\" IS NULL)")
    List<Map<String, Object>> selectHisAntibiotics();
}
