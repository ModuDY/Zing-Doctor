-- ============================================================
-- MySQL 8.x 版本（由达梦 DM8 脚本自动转换 + 人工校验）
-- 主键由应用雪花算法生成，不使用 AUTO_INCREMENT
-- 执行：mysql -uroot -p < 本文件（需先执行 00b_idempotent_helpers.sql）
-- ============================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

-- ---- 以下为原达梦 PL/SQL 幂等块转换得到的 DDL ----


-- =====================================================================
-- 医生决策系统 - SOFA 评分增量脚本（达梦 DM8）
--
-- 内容：
--   1) sofa_score_record   SOFA 评分记录表
--   2) sofa_config         SOFA 配置表（item code 映射 / 血管活性药阈值 / 默认体重 / 换算系数）
--   3) zing_page_config    页面注册（sofa-score / sofa-overview）
--   4) sofa_config 默认种子（仅当表为空时写入，不覆盖院内配置）
--
-- 幂等：DbInit 对 CREATE TABLE / CREATE INDEX 做存在性检查，可重复执行；
--       种子数据用「表为空才插入」守卫，重复执行不会产生重复行。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/Sa_20250815@100.120.1.102:14236
--   SQL> start /opt/zing-doctor/sql/07_sofa.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) SOFA 评分记录表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sofa_score_record` (
  `id` BIGINT NOT NULL,
  `patient_id` VARCHAR(64) COMMENT '患者ID（patient_info.id）',
  `in_hospital_no` VARCHAR(64)   NOT NULL COMMENT '住院号',
  `patient_name` VARCHAR(64) COMMENT '患者姓名',
  `depart_code` VARCHAR(64) COMMENT '科室编码',
  `score_time` TIMESTAMP COMMENT '评分时间',
  `score_type` VARCHAR(32)   DEFAULT 'custom' NOT NULL COMMENT '来源：auto 系统自动 / daily 每日 / custom 自定义',
  `resp_score` INT           DEFAULT 0 COMMENT '呼吸评分 0-4（PaO2/FiO2）',
  `coag_score` INT           DEFAULT 0 COMMENT '凝血评分 0-4（血小板）',
  `liver_score` INT           DEFAULT 0 COMMENT '肝评分 0-4（总胆红素）',
  `cardio_score` INT           DEFAULT 0 COMMENT '循环评分 0-4（MAP/血管活性药）',
  `neuro_score` INT           DEFAULT 0 COMMENT '神经评分 0-4（GCS）',
  `renal_score` INT           DEFAULT 0 COMMENT '肾评分 0-4（肌酐/24h尿量）',
  `total_score` INT           DEFAULT 0 COMMENT 'SOFA 总分 0-24（6 项之和）',
  `resp_data` TEXT COMMENT '呼吸项原始值 JSON（含取值时间）',
  `coag_data` TEXT COMMENT '凝血项原始值 JSON',
  `liver_data` TEXT COMMENT '肝项原始值 JSON',
  `cardio_data` TEXT COMMENT '循环项原始值 JSON',
  `neuro_data` TEXT COMMENT '神经项原始值 JSON',
  `renal_data` TEXT COMMENT '肾项原始值 JSON',
  `vasopressor_json` TEXT COMMENT '血管活性药明细 JSON（药名/归一剂量 µg-kg-min/泵速/浓度/是否降级）',
  `urine_ml` DECIMAL(10,2) COMMENT '取数窗口内尿量合计（mL）',
  `gcs_total` INT COMMENT 'GCS 总分（3-15），未评全为 NULL',
  `gcs_detail` VARCHAR(64) COMMENT 'GCS 明细 E/V/M',
  `respiratory_support` TINYINT       DEFAULT 0 COMMENT '是否有呼吸支持：1是 0否',
  `weight_used` DECIMAL(6,2) COMMENT '本次剂量换算所用体重（kg）',
  `weight_source` VARCHAR(32) COMMENT '体重来源：actual 实际 / ibw 身高估算 / default_age_sex 年龄性别默认 / fallback70 兜底',
  `delta_sofa` INT COMMENT '较上一次评分的总分变化（用于恶化预警）',
  `data_start_time` TIMESTAMP COMMENT '取数开始时间',
  `data_end_time` TIMESTAMP COMMENT '取数结束时间',
  `remark` VARCHAR(500) COMMENT '备注（含体重来源、剂量降级等提示）',
  `pdf_data` LONGTEXT COMMENT '评分文书PDF的Base64（不含data前缀）；列表查询不返回',
  `pdf_name` VARCHAR(255) COMMENT 'PDF文件名',
  `status` TINYINT       DEFAULT 1 COMMENT '状态：1正常 0已删除',
  `create_by` VARCHAR(64),
  `create_time` TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
  `update_by` VARCHAR(64),
  `update_time` TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='SOFA 评分记录表';
CALL zing_add_index('sofa_score_record', 'idx_sofa_patient', 0, '`in_hospital_no`');
CALL zing_add_index('sofa_score_record', 'idx_sofa_depart', 0, '`depart_code`');
CALL zing_add_index('sofa_score_record', 'idx_sofa_time', 0, '`score_time`');

-- ---------------------------------------------------------------------
-- 2) SOFA 配置表（表空时才写入默认种子，不覆盖院内调整）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sofa_config` (
  `id` BIGINT NOT NULL,
  `config_type` VARCHAR(32)  NOT NULL COMMENT '配置类型：lis_item 检验项 / observe_item 监护项 / io_item 出入量项 / vasopressor 血管活性药 / conversion 换算系数 / default_weight 默认体重',
  `config_key` VARCHAR(128) NOT NULL COMMENT '配置键',
  `config_value` VARCHAR(500) COMMENT '配置值',
  `item_name` VARCHAR(128) COMMENT '项目名称（展示用）',
  `remark` VARCHAR(255) COMMENT '备注',
  `sort_no` INT          DEFAULT 1,
  `status` TINYINT      DEFAULT 1,
  `create_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
  `update_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='SOFA 配置表';
CALL zing_add_index('sofa_config', 'idx_sofa_config_type', 0, '`config_type`, `status`');

-- ---------------------------------------------------------------------
-- 3) 页面注册（外链 pageCode）
-- ---------------------------------------------------------------------
DELETE FROM `zing_page_config`
 WHERE `page_code` IN ('sofa-score', 'sofa-overview');
INSERT INTO `zing_page_config`
    (`page_code`, `page_name`, `frontend_path`, `remark`, `status`)
VALUES
    ('sofa-score', 'SOFA 评分', '/page/sofa-score',
     'SOFA 序贯器官衰竭评估：6 器官评分 + 总分 + 趋势，外链携带 inHospitalNo 打开', 1),
    ('sofa-overview', 'SOFA 评分总览', '/page/sofa-overview',
     'SOFA 科室总览：评分分布、ΔSOFA 恶化预警、患者列表', 1);

-- ---------------------------------------------------------------------
-- 4) sofa_config 默认种子（仅当表为空时写入）
-- ---------------------------------------------------------------------
-- 种子：逐条 NOT EXISTS 守卫（与 02_seed.sql 同写法，达梦已验证）；按 (config_type, config_key) 判重，可重复执行且不覆盖院内已改配置

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'lis_item', 'platelet', '200050', '血小板', '已现场确认；单位 10^9/L 与 SOFA ×10^3/µL 数值等同', 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'lis_item' AND `config_key` = 'platelet');

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'lis_item', 'bilirubin', '100010,100020,100030', '总胆红素', '三个候选 code，取数时按 lis_item_name 优先筛“总胆红素”（排除直接/间接）', 2 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'lis_item' AND `config_key` = 'bilirubin');

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'lis_item', 'creatinine', '100210', '肌酐', '单位 µmol/L，评分时 ÷88.4 转 mg/dL', 3 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'lis_item' AND `config_key` = 'creatinine');

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'observe_item', 'map', 'oi_ycpjy,oi_pjy', '平均动脉压', '有创/无创平均压，取窗口内最差值', 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'observe_item' AND `config_key` = 'map');

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'observe_item', 'fio2', 'oi_FiO2(设置值)', 'FiO2', '注意库内可能是百分数(60)或小数(0.6)，取数后统一归一为百分数', 2 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'observe_item' AND `config_key` = 'fio2');

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'observe_item', 'peep', 'oi_peep', 'PEEP', '辅助判断呼吸支持', 3 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'observe_item' AND `config_key` = 'peep');

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'io_item', 'urine', 'ii_nl', '尿量', '固定 item_code；窗口内求和作为 24h 尿量（名称匹配仅作兜底，二者不叠加）', 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'io_item' AND `config_key` = 'urine');

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'vasopressor', 'norepinephrine', '去甲肾上腺素,0.1', '去甲肾上腺素', '药名关键词,3/4分界；匹配时须先匹配本药（“肾上腺素”是其子串）', 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'vasopressor' AND `config_key` = 'norepinephrine');

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'vasopressor', 'epinephrine', '肾上腺素,0.1', '肾上腺素', '匹配时须排除含“去甲”的串', 2 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'vasopressor' AND `config_key` = 'epinephrine');

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'vasopressor', 'dopamine', '多巴胺,5,15', '多巴胺', '药名,2/3分界,3/4分界', 3 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'vasopressor' AND `config_key` = 'dopamine');

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'vasopressor', 'dobutamine', '多巴酚丁胺', '多巴酚丁胺', '任意剂量即 2 分', 4 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'vasopressor' AND `config_key` = 'dobutamine');

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'conversion', 'norepinephrine', '1.0', '去甲肾上腺素换算系数', '若院内按碱基计（重酒石酸盐:碱基≈2:1）则填 0.5', 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'conversion' AND `config_key` = 'norepinephrine');

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'conversion', 'epinephrine', '1.0', '肾上腺素换算系数', '按标示量计算，默认 1.0', 2 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'conversion' AND `config_key` = 'epinephrine');

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'default_weight', 'M_18_39', '70', '男 18-39 岁', '默认体重 kg，需临床确认', 1 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'default_weight' AND `config_key` = 'M_18_39');

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'default_weight', 'F_18_39', '58', '女 18-39 岁', '默认体重 kg，需临床确认', 2 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'default_weight' AND `config_key` = 'F_18_39');

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'default_weight', 'M_40_59', '70', '男 40-59 岁', '默认体重 kg，需临床确认', 3 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'default_weight' AND `config_key` = 'M_40_59');

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'default_weight', 'F_40_59', '60', '女 40-59 岁', '默认体重 kg，需临床确认', 4 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'default_weight' AND `config_key` = 'F_40_59');

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'default_weight', 'M_60_69', '67', '男 60-69 岁', '默认体重 kg，需临床确认', 5 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'default_weight' AND `config_key` = 'M_60_69');

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'default_weight', 'F_60_69', '58', '女 60-69 岁', '默认体重 kg，需临床确认', 6 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'default_weight' AND `config_key` = 'F_60_69');

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'default_weight', 'M_70_79', '65', '男 70-79 岁', '默认体重 kg，需临床确认', 7 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'default_weight' AND `config_key` = 'M_70_79');

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'default_weight', 'F_70_79', '55', '女 70-79 岁', '默认体重 kg，需临床确认', 8 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'default_weight' AND `config_key` = 'F_70_79');

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'default_weight', 'M_80', '62', '男 ≥80 岁', '默认体重 kg，需临床确认', 9 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'default_weight' AND `config_key` = 'M_80');

INSERT INTO `sofa_config`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'default_weight', 'F_80', '52', '女 ≥80 岁', '默认体重 kg，需临床确认', 10 FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `sofa_config`
                    WHERE `config_type` = 'default_weight' AND `config_key` = 'F_80');
