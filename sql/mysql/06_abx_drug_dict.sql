-- ============================================================
-- MySQL 8.x 版本（由达梦 DM8 脚本自动转换 + 人工校验）
-- 主键由应用雪花算法生成（MyBatis-Plus ASSIGN_ID），显式插入时以插入值为准；
-- 仅当 INSERT 省略 id 时由 AUTO_INCREMENT 兜底（对应达梦原有的 SEQ.NEXTVAL 默认值）
-- 执行：mysql -uroot -p < 本文件（需先执行 00b_idempotent_helpers.sql）
-- ============================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

-- ---- 以下为原达梦 PL/SQL 幂等块转换得到的 DDL ----


-- =====================================================================
-- 抗菌药物字典（HIS 药品字典同步表）
--
-- 背景：
--   抗菌药识别原本依赖 zing_abx_word_config 的「关键词白名单 + 黑名单」，
--   但 HIS 药品字典（ICU 库 zing_icu_db_prod.config_drug，字段 is_antibiotics）
--   才是`哪种药是抗菌药`的权威来源。本表用于承接 HIS 的抗菌药清单，
--   由 AbxDrugSyncTask 夜间定时同步（也可页面手动触发），
--   供 AbxDrugRecognizer 做「精确名/通用名/基名」匹配，显著提升识别率。
--
-- 数据流：
--   zing_icu_db_prod.config_drug (is_antibiotics='1')
--        │  夜间定时 / 手动触发（只读数据源，不写 ICU）
--        ▼
--   zing_doctor_db_prod.zing_abx_drug_dict（本表）
--        │  内存快照（5 分钟 TTL）
--        ▼
--   AbxDrugRecognizer#isAntibiotic / isBroadSpectrum / isNonAntibiotic
--        → 脓毒症集束化、医生交班览表、当前抗菌药、MDRO/DDD/经验性决策
--
-- 幂等：重复执行本脚本前请先 DROP TABLE（或改为 CREATE TABLE IF NOT EXISTS）。
-- 执行方式（disql，SYSDBA 执行）：
--   SQL> start /opt/zing-doctor/sql/06_abx_drug_dict.sql
-- =====================================================================

CREATE TABLE IF NOT EXISTS `zing_abx_drug_dict` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `drug_code` VARCHAR(64)  NOT NULL COMMENT 'HIS 药品编码（唯一键，用于增量比对）',
  `drug_name` VARCHAR(255) COMMENT '药品名称（含商品名，如 盐酸克林霉素胶囊(特丽仙)）',
  `drug_short_name` VARCHAR(255) COMMENT '药品简称',
  `drug_normal_name` VARCHAR(255) COMMENT '通用名（无商品名）',
  `drug_pinyin` VARCHAR(255) COMMENT '拼音码',
  `spec` VARCHAR(255) COMMENT '规格',
  `dose` VARCHAR(64) COMMENT '单次剂量',
  `unit_code` VARCHAR(32) COMMENT '剂量单位',
  `drug_factory_name` VARCHAR(255) COMMENT '生产厂家',
  `is_antibiotics` VARCHAR(8) COMMENT 'HIS 抗菌药标记（同步源固定为 1）',
  `antibiotics_color` VARCHAR(32) COMMENT 'HIS 抗菌药标识色（前端着色的数据来源）',
  `sync_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '最近一次同步写入时间（内容无变化时不刷新）',
  `status` TINYINT      DEFAULT 1 NOT NULL COMMENT '状态：1 在库（HIS 仍标记为抗菌药）0 已失效（HIS 已取消抗菌药标记）',
  `del_flag` TINYINT      DEFAULT 0 NOT NULL COMMENT '删除标记：0 正常',
  `create_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
  `update_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) COMMENT='抗菌药物字典（HIS config_drug 中 is_antibiotics=1 的同步副本）';

-- 唯一索引：药品编码，夜间同步按它做增量比对（存在则更新，不存在则新增）
CALL zing_add_index('zing_abx_drug_dict', 'uk_zing_abx_drug_dict_code', 1, '`drug_code`');

-- 识别时按状态过滤，建立普通索引
CALL zing_add_index('zing_abx_drug_dict', 'idx_zing_abx_drug_dict_status', 0, '`status`');
