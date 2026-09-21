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
-- 医生决策系统 - 自有数据库结构（达梦 DM8）
--
-- 说明：
--   1. 本库存放医生决策系统自身产生的业务数据（决策记录、外链访问日志、页面配置等）
--   2. 分析判断所需的患者/医嘱/检验/微生物数据来源于 ICU 系统库 zing_icu_db_prod，
--      本库不复制 ICU 业务主数据，仅通过只读数据源访问。
--   3. 数据库为达梦 DM8。医生决策系统统一用 SYSDBA 连接达梦（与 ICU 系统访问方式一致），
--      通过显式模式前缀访问各模式。本脚本建表于模式 zing_doctor_db_prod 下
--      （SQL 中的 `xxx` 即显式模式限定）。
--      部署前先用 00_init_user.sql（SYSDBA 执行）创建该模式。
--   4. 执行方式（disql，SYSDBA 执行）：
--        disql SYSDBA/Sa_20250815@100.120.1.102:14236
--        SQL> start /opt/zing-doctor/sql/01_schema.sql
-- =====================================================================
--
-- 【主键策略】本库所有表都不使用 IDENTITY 自增，改由程序生成 15 位全局唯一 ID
-- （毫秒时间戳13 + 机器号1 + 序列号1，见 com.zing.doctor.common.ZingIdGenerator）。
-- 自增 ID 从 1 开始，多院区合并或导历史数据时必然撞车，故整体替换。
--
-- 每张表配一个 SEQ_<表名> 序列并作为 id 列的默认值，它**只**服务于初始化脚本里
-- 那些不写 id 列的 INSERT；程序运行期插入一律自带 id，默认值不生效。
-- 若达梦版本不支持「序列作列默认值」，把 id 列改回普通 NOT NULL 并改用
-- BEFORE INSERT 触发器填值即可，程序端代码不需要任何调整。
--
-- 重建库时若报「序列已存在」，先执行：
--   SELECT 'DROP SEQUENCE "'||SEQUENCE_NAME||'";'
--     FROM ALL_SEQUENCES WHERE SEQUENCE_OWNER='zing_doctor_db_prod';
-- 把查询结果整体执行一遍，再重跑本脚本。

-- ---------------------------------------------------------------------
-- 页面注册表：每个可外链打开的功能页面在此登记（外链 pageCode 唯一）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `zing_page_config` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `page_code` VARCHAR(64)  NOT NULL COMMENT '页面编码（外链 pageCode，唯一）',
  `page_name` VARCHAR(128) NOT NULL COMMENT '页面名称',
  `frontend_path` VARCHAR(255) NOT NULL COMMENT '前端路由路径，如 /page/abx-patient-list',
  `remark` VARCHAR(255) COMMENT '备注',
  `status` TINYINT      DEFAULT 1 NOT NULL COMMENT '状态：1 启用 0 停用',
  `create_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
  `update_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) COMMENT='页面注册表';
CALL zing_add_index('zing_page_config', 'uk_zing_page_config_page_code', 1, '`page_code`');

-- ---------------------------------------------------------------------
-- 外链访问日志：记录外部系统（ICU）外链进入医生决策系统的访问
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `zing_external_access_log` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `page_code` VARCHAR(64) NOT NULL COMMENT '被访问页面编码',
  `source_system` VARCHAR(64) DEFAULT 'icu' NOT NULL COMMENT '来源系统标识',
  `ip` VARCHAR(64) COMMENT '来源 IP',
  `biz_params` TEXT COMMENT '业务参数（JSON）',
  `access_time` TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '访问时间',
  PRIMARY KEY (`id`)
) COMMENT='外链访问日志';
CALL zing_add_index('zing_external_access_log', 'idx_zing_external_access_log_page', 0, '`page_code`');
CALL zing_add_index('zing_external_access_log', 'idx_zing_external_access_log_time', 0, '`access_time`');

-- ---------------------------------------------------------------------
-- 抗感染决策记录：第一维度（经验性抗感染治疗决策）医生决策留痕
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `zing_decision_record` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `patient_id` VARCHAR(64)  NOT NULL COMMENT 'ICU 患者 ID',
  `patient_no` VARCHAR(64) COMMENT '住院号/就诊号',
  `page_code` VARCHAR(64)  DEFAULT 'abx-decision' COMMENT '来源页面',
  `source_system` VARCHAR(64)  DEFAULT 'icu' NOT NULL COMMENT '来源系统',
  `infection_type` VARCHAR(64) COMMENT '感染类型/部位',
  `septic_shock` TINYINT      DEFAULT 0 NOT NULL COMMENT '是否脓毒性休克：0 否 1 是',
  `mrsa_risk` TINYINT      DEFAULT 0 NOT NULL COMMENT 'MRSA 高风险：0 否 1 是',
  `mdr_risk` TINYINT      DEFAULT 0 NOT NULL COMMENT 'MDR 高风险：0 否 1 是',
  `fungal_risk` TINYINT      DEFAULT 0 NOT NULL COMMENT '真菌高风险：0 否 1 是',
  `recommended_plan` VARCHAR(500) COMMENT '系统推荐方案摘要',
  `doctor_decision` VARCHAR(500) COMMENT '医生最终决策',
  `decision_status` VARCHAR(32)  DEFAULT 'pending' NOT NULL COMMENT '状态：pending 待决策 / accepted 采纳 / declined 拒绝',
  `doctor_id` VARCHAR(64) COMMENT '决策医生 ID',
  `doctor_name` VARCHAR(64) COMMENT '决策医生姓名',
  `create_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
  `update_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='抗感染决策记录';
CALL zing_add_index('zing_decision_record', 'idx_zing_decision_record_patient', 0, '`patient_id`');
CALL zing_add_index('zing_decision_record', 'idx_zing_decision_record_status', 0, '`decision_status`');

-- ---------------------------------------------------------------------
-- 抗感染方案推荐日志：一次决策记录对应的系统推荐方案明细（可多条）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `zing_advice_log` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `decision_record_id` BIGINT       NOT NULL COMMENT '关联决策记录 ID',
  `drug_name` VARCHAR(128) NOT NULL COMMENT '推荐药品/方案名',
  `dose_plan` VARCHAR(255) COMMENT '剂量方案',
  `route` VARCHAR(32) COMMENT '给药途径',
  `advice_level` VARCHAR(16) COMMENT '建议强度（如 强/弱，或 R1/R2/R3）',
  `reason` VARCHAR(500) COMMENT '推荐理由',
  `evidence` VARCHAR(500) COMMENT '证据说明（指南/共识出处）',
  `create_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='抗感染方案推荐日志';
CALL zing_add_index('zing_advice_log', 'idx_zing_advice_log_record', 0, '`decision_record_id`');

-- ---------------------------------------------------------------------
-- 抗菌药物 DDD 值配置表：第三维度（使用强度分析）的核心知识库
-- 初始数据按 WHO ATC/DDD 最新版本录入，支持后台页面修改
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `zing_ddd_config` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `drug_name` VARCHAR(128) NOT NULL COMMENT '药品通用名',
  `atc_code` VARCHAR(32) COMMENT 'WHO ATC编码',
  `ddd_value` DECIMAL(10,4) NOT NULL COMMENT 'DDD值（限定日剂量）',
  `ddd_unit` VARCHAR(16)  DEFAULT 'g' NOT NULL COMMENT 'DDD单位（g/mg/MU等）',
  `route` VARCHAR(16)  DEFAULT '注射' NOT NULL COMMENT '给药途径：注射/口服',
  `manage_level` VARCHAR(16)  DEFAULT '非限制' NOT NULL COMMENT '管理级别：非限制/限制/特殊',
  `drug_class` VARCHAR(64) COMMENT '药物分类（青霉素类/头孢类/碳青霉烯类等）',
  `keywords` VARCHAR(500) COMMENT '匹配关键词（逗号分隔，用于医嘱名称模糊匹配）',
  `remark` VARCHAR(500) COMMENT '备注',
  `status` TINYINT      DEFAULT 1 NOT NULL COMMENT '状态：1 启用 0 停用',
  `create_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
  `update_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='抗菌药物DDD值配置表';
CALL zing_add_index('zing_ddd_config', 'uk_zing_ddd_config_drug_route', 1, '`drug_name`, `route`');
CALL zing_add_index('zing_ddd_config', 'idx_zing_ddd_config_class', 0, '`drug_class`');
CALL zing_add_index('zing_ddd_config', 'idx_zing_ddd_config_level', 0, '`manage_level`');

-- ---------------------------------------------------------------------
-- 第四维度：细菌培养检出监测配置表
-- 用于配置细菌分类（革兰阳性/阴性/真菌）、高风险细菌列表、标本类型等
-- 由于 ICU 库细菌名称中无耐药关键词，MDRO 精确判定需药敏结果支持，
-- 本表用于细菌分类统计和高风险细菌标记。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `zing_mdro_config` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `config_type` VARCHAR(20)  NOT NULL COMMENT '配置类型：bacteria_class细菌分类, high_risk高风险细菌, specimen标本类型',
  `bacteria_name` VARCHAR(128) COMMENT '细菌名称',
  `bacteria_class` VARCHAR(20) COMMENT '细菌分类：gram_positive革兰阳性, gram_negative革兰阴性, fungi真菌, other其他',
  `is_high_risk` TINYINT      DEFAULT 0 NOT NULL COMMENT '是否高风险细菌：1是0否（ICU常见MDRO风险菌）',
  `keywords` VARCHAR(500) COMMENT '匹配关键词（逗号分隔，用于细菌名称模糊匹配分类）',
  `remark` VARCHAR(500) COMMENT '备注',
  `status` TINYINT      DEFAULT 1 NOT NULL COMMENT '状态：1 启用 0 停用',
  `create_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
  `update_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='细菌培养监测配置表（第四维度）';
CALL zing_add_index('zing_mdro_config', 'idx_zing_mdro_config_type', 0, '`config_type`');
CALL zing_add_index('zing_mdro_config', 'idx_zing_mdro_config_class', 0, '`bacteria_class`');
CALL zing_add_index('zing_mdro_config', 'idx_zing_mdro_config_risk', 0, '`is_high_risk`');

-- ---------------------------------------------------------------------
-- 脓毒症休克集束化治疗记录表
-- 用于记录脓毒症/感染性休克患者1H/3H/6H集束化治疗完成情况
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sepsis_bundle_record` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `patient_id` VARCHAR(64) COMMENT '患者ID',
  `in_hospital_no` VARCHAR(64)  NOT NULL COMMENT '住院号',
  `patient_name` VARCHAR(64) COMMENT '患者姓名',
  `depart_code` VARCHAR(64) COMMENT '科室编码',
  `diagnosis_time` TIMESTAMP COMMENT '脓毒症确诊时间',
  `in_depart_time` TIMESTAMP COMMENT '入科时间',
  `bundle_1h_completed` TINYINT      DEFAULT 0 COMMENT '1H集束化是否完成：1是 0否',
  `bundle_3h_completed` TINYINT      DEFAULT 0 COMMENT '3H集束化是否完成：1是 0否',
  `bundle_6h_completed` TINYINT      DEFAULT 0 COMMENT '6H集束化是否完成：1是 0否',
  `bundle_1h_data` TEXT COMMENT '1H项目详细数据（JSON）',
  `bundle_3h_data` TEXT COMMENT '3H项目详细数据（JSON）',
  `bundle_6h_data` TEXT COMMENT '6H项目详细数据（JSON）',
  `infection_site` VARCHAR(255) COMMENT '感染部位',
  `pathogen` VARCHAR(255) COMMENT '致病菌',
  `antibiotic` VARCHAR(255) COMMENT '抗生素',
  `fluid_reason` TEXT COMMENT '液体复苏未达30ml/kg原因（JSON）',
  `status` TINYINT      DEFAULT 1 COMMENT '状态：1 正常 0 已删除',
  `create_by` VARCHAR(64) COMMENT '创建人',
  `create_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
  `update_by` VARCHAR(64) COMMENT '更新人',
  `update_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) COMMENT='脓毒症休克集束化治疗记录表';
CALL zing_add_index('sepsis_bundle_record', 'idx_sepsis_bundle_patient', 0, '`in_hospital_no`');
CALL zing_add_index('sepsis_bundle_record', 'idx_sepsis_bundle_depart', 0, '`depart_code`');
CALL zing_add_index('sepsis_bundle_record', 'idx_sepsis_bundle_time', 0, '`diagnosis_time`');

-- ---------------------------------------------------------------------
-- 抗菌药物识别词库配置表（脓毒症集束化：广谱抗菌药白名单 + 非抗菌药黑名单）
-- 后台页面 abx-word-config 可增删改，启动/评估时加载，表空时回退内置默认
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `zing_abx_word_config` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `word_type` VARCHAR(32)  NOT NULL COMMENT '词类型：broad_spectrum 广谱抗菌药白名单 / non_antibiotic 非抗菌药黑名单',
  `keyword` VARCHAR(128) NOT NULL COMMENT '匹配关键词',
  `category` VARCHAR(64) COMMENT '分组（如：电解质/抗组胺/广谱抗菌药）',
  `remark` VARCHAR(255) COMMENT '备注',
  `status` TINYINT      DEFAULT 1 COMMENT '状态：1启用 0停用',
  `create_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
  `update_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='抗菌药物识别词库配置';
CALL zing_add_index('zing_abx_word_config', 'uk_zing_abx_word_type_kw', 1, '`word_type`, `keyword`');
CALL zing_add_index('zing_abx_word_config', 'idx_zing_abx_word_type_status', 0, '`word_type`, `status`');

-- ---------------------------------------------------------------------
-- 第五维度：医生交班览表 - 医生手工交班记录
-- 病情变化（condition_change）为 P0 手工录入字段；下一班计划/待办字段 P1 预留
-- 一个患者一个`已封板全天班次`一条，按 (in_hospital_no, shift_begin_time) 唯一
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `zing_doctor_handover` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `patient_id` VARCHAR(64) COMMENT '患者ID（patient_info.id）',
  `in_hospital_no` VARCHAR(64)  NOT NULL COMMENT '住院号',
  `patient_name` VARCHAR(64) COMMENT '患者姓名',
  `depart_code` VARCHAR(64) COMMENT '科室编码（sys_depart.org_code）',
  `shift_begin_time` TIMESTAMP COMMENT '绑定班次开始时间（上一完整全天班起点）',
  `shift_end_time` TIMESTAMP COMMENT '绑定班次结束时间（上一完整全天班终点）',
  `condition_change` TEXT COMMENT '本班病情变化（医生手工录入，P0）',
  `plan_next` TEXT COMMENT '下一班诊疗计划（P1预留）',
  `todo_note` TEXT COMMENT '待办事项（P1预留）',
  `status` TINYINT      DEFAULT 1 COMMENT '状态：1 正常 0 已删除',
  `create_by` VARCHAR(64) COMMENT '创建人（交班医生）',
  `create_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
  `update_by` VARCHAR(64) COMMENT '更新人',
  `update_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) COMMENT='医生交班览表-手工交班记录';
CALL zing_add_index('zing_doctor_handover', 'uk_zdh_patient_shift', 1, '`in_hospital_no`, `shift_begin_time`');
CALL zing_add_index('zing_doctor_handover', 'idx_zdh_depart', 0, '`depart_code`');
CALL zing_add_index('zing_doctor_handover', 'idx_zdh_shift', 0, '`shift_begin_time`');

-- ---------------------------------------------------------------------
-- APACHE II 评分记录表
-- 支持多次评分（入科时/24h/48h/自定义），每次评分一条记录
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `apache2_score_record` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `patient_id` VARCHAR(64) COMMENT '患者ID（patient_info.id）',
  `in_hospital_no` VARCHAR(64)  NOT NULL COMMENT '住院号',
  `patient_name` VARCHAR(64) COMMENT '患者姓名',
  `depart_code` VARCHAR(64) COMMENT '科室编码',
  `score_time` TIMESTAMP COMMENT '评分时间',
  `score_type` VARCHAR(32) COMMENT '评分时机：admission入科时/24h/48h/custom自定义',
  `age_score` INT          DEFAULT 0 COMMENT 'A年龄评分',
  `chronic_score` INT          DEFAULT 0 COMMENT 'B慢性健康状况评分',
  `gcs_score` INT          DEFAULT 0 COMMENT 'C GCS评分（15-GCS）',
  `physiology_score` INT          DEFAULT 0 COMMENT 'D急性生理评分（12项合计）',
  `total_score` INT          DEFAULT 0 COMMENT 'APACHE II总分（A+B+C+D）',
  `mortality_rate` DECIMAL(5,2) COMMENT '预计院内死亡率（%）',
  `aps_data` TEXT COMMENT '12项急性生理数据JSON',
  `diagnosis_type` VARCHAR(32) COMMENT '疾病分类：nonoperative非手术/operative手术/none以上都不是',
  `diagnosis_weight` DECIMAL(8,4) COMMENT '诊断权重',
  `emergency_surgery` TINYINT      DEFAULT 0 COMMENT '是否急诊手术：1是 0否',
  `chronic_health` VARCHAR(32) COMMENT '慢性健康状况：none/nonoperative/elective',
  `gcs_detail` VARCHAR(64) COMMENT 'GCS明细（E/V/M）',
  `data_start_time` TIMESTAMP COMMENT '取数开始时间',
  `data_end_time` TIMESTAMP COMMENT '取数结束时间',
  `remark` VARCHAR(500) COMMENT '备注',
  `pdf_data` TEXT COMMENT '评分文书PDF的Base64（不含data前缀）',
  `pdf_name` VARCHAR(200) COMMENT 'PDF文件名',
  `status` TINYINT      DEFAULT 1 COMMENT '状态：1正常 0已删除',
  `create_by` VARCHAR(64) COMMENT '创建人（评分医生）',
  `create_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
  `update_by` VARCHAR(64) COMMENT '更新人',
  `update_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`)
) COMMENT='APACHE II评分记录表';
CALL zing_add_index('apache2_score_record', 'idx_apache2_patient', 0, '`in_hospital_no`');
CALL zing_add_index('apache2_score_record', 'idx_apache2_depart', 0, '`depart_code`');
CALL zing_add_index('apache2_score_record', 'idx_apache2_time', 0, '`score_time`');

-- ---------------------------------------------------------------------
-- APACHE II 配置表
-- 监护item_code配置、检验lis_item_code配置、慢性健康关键词配置
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `apache2_config` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `config_type` VARCHAR(32)  NOT NULL COMMENT '配置类型：observe_item监护item_code/lis_item检验item_code/chronic_keyword慢性健康关键词',
  `config_key` VARCHAR(128) NOT NULL COMMENT '配置键（如：temperature/heart_rate/sodium/potassium/creatinine/hct/wbc）',
  `config_value` VARCHAR(500) COMMENT '配置值（item_code或lis_item_code，多个用逗号分隔）',
  `item_name` VARCHAR(128) COMMENT '项目名称',
  `remark` VARCHAR(255) COMMENT '备注',
  `sort_no` INT          DEFAULT 1 COMMENT '排序号',
  `status` TINYINT      DEFAULT 1 COMMENT '状态：1启用 0停用',
  `create_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
  `update_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='APACHE II配置表';
CALL zing_add_index('apache2_config', 'idx_apache2_config_type', 0, '`config_type`, `status`');
