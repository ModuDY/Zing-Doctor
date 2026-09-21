-- ============================================================
-- MySQL 8.x 版本（由达梦 DM8 脚本自动转换 + 人工校验）
-- 主键由应用雪花算法生成，不使用 AUTO_INCREMENT
-- 执行：mysql -uroot -p < 本文件（需先执行 00b_idempotent_helpers.sql）
-- ============================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

-- ---- 以下为原达梦 PL/SQL 幂等块转换得到的 DDL ----


-- =====================================================================
-- 医生决策系统 - 质控指标中台增量脚本（达梦 DM8）
--
-- 设计定位：与旧的「cal_custom_js 脚本集合」彻底区分，本方案是
--           「数仓分层 + 指标中台」：
--   ① quality_index          指标字典（127 条，含展示配置与实现状态，由 YAML 同步）
--   ② quality_calc_run       计算批次（血缘第 1 层）
--   ③ quality_metric_result  指标结果（血缘第 2 层：分子/分母/值 + 老系统对比值）
--   ④ quality_calc_trace     算子/ SQL 追溯（血缘第 3 层：SQL、扫描行数、表达式版本）
--   ⑤ quality_metric_patient 患者级命中明细（血缘第 4 层：谁进分子、谁进分母）
--   ⑥ quality_monthly_report 月度汇总宽表（1-12 月横排，页面与导出直读）
--
-- 幂等：DbInit 对 CREATE TABLE / CREATE INDEX 做存在性检查，可重复执行；
--       字典不在此处硬编码，由 QualityIndexSyncService 启动时从
--       classpath:quality/metrics/*.yaml 同步（改指标=改配置，不发版）。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/Sa_20250815@100.120.1.102:14236
--   SQL> start /opt/zing-doctor/sql/09_quality.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) 指标字典表
--    指标编号 / 名称 / 分类 / 展示配置 / 实现状态 / 口径版本
--    数据来源：classpath:quality/metrics/*.yaml（引擎启动同步，避免 SQL 与配置双维护）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `quality_index` (
  `id` BIGINT NOT NULL,
  `index_code` VARCHAR(32)   NOT NULL COMMENT '指标编号 quality_xxx',
  `index_name` VARCHAR(200),
  `domain_code` VARCHAR(32) COMMENT '所属域：患者流转/脓毒症_感染性休克/抗菌药送检/DVT预防/评分_资源/评估依从/导管_管路_院感/ARDS专项',
  `category_code` VARCHAR(8) COMMENT '一级分类编码（老系统 categoryCode）',
  `group_code` VARCHAR(8) COMMENT '分组编码（老系统 groupCode）',
  `quality_type_code` VARCHAR(8) COMMENT '质控类别编码',
  `index_standard_code` VARCHAR(64) COMMENT '指标标准编码（老系统 indexStandardCode）',
  `unit` VARCHAR(32) COMMENT '单位：%/人/天/例',
  `value_type` VARCHAR(16) COMMENT '值类型：RATE 率 / COUNT 数 / DAYS 天数 / RATIO 比 / AMOUNT 均值',
  `calc_mode` VARCHAR(16) COMMENT '计算方式：DSL 声明式 / MANUAL 人工录入 / CUSTOM_SQL 自定义SQL',
  `fact_name` VARCHAR(64) COMMENT '绑定事实层（DWD）名称，如 fact_patient_stay',
  `impl_status` VARCHAR(16) COMMENT '实现状态：IMPL 已实现 / PLACEHOLDER 空壳占位 / PENDING_SOURCE 待接数据源 / MANUAL 人工录入',
  `expression_version` INT           DEFAULT 1 COMMENT '口径版本，改口径即递增，用于回溯历史值的口径',
  `amount_show_type` VARCHAR(8),
  `analysis_count_type` VARCHAR(8),
  `is_use_zero_show` TINYINT       DEFAULT 0,
  `is_show_patient` TINYINT       DEFAULT 0,
  `legacy_script` VARCHAR(16) COMMENT '老系统是否有脚本：是 / 否-空壳 / 不适用',
  `legacy_source` VARCHAR(300),
  `new_target` VARCHAR(300),
  `reuse_level` VARCHAR(16),
  `depend_keys` VARCHAR(512) COMMENT '依赖键（老系统 dependKeys），用于血缘追溯',
  `sort_no` INT           DEFAULT 1,
  `remark` VARCHAR(1000) COMMENT '备注（老系统缺陷、口径待定说明等）',
  `status` TINYINT       DEFAULT 1,
  `create_time` TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
  `update_time` TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='质控指标字典（127 条，由 quality/metrics/*.yaml 同步）';
CALL zing_add_index('quality_index', 'idx_quality_index_domain', 0, '`domain_code`, `status`');
CALL zing_add_index('quality_index', 'uk_quality_index_code', 1, '`index_code`');

-- ---------------------------------------------------------------------
-- 2) 计算批次表（血缘第 1 层）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `quality_calc_run` (
  `id` BIGINT NOT NULL,
  `run_id` VARCHAR(40)  NOT NULL COMMENT '批次号（一次计算一个）',
  `period_type` VARCHAR(16) COMMENT '周期类型：MONTH/QUARTER/YEAR/CUSTOM',
  `period_start` TIMESTAMP,
  `period_end` TIMESTAMP,
  `depart_code` VARCHAR(64) COMMENT '科室编码，空表示全院',
  `trigger_type` VARCHAR(16) COMMENT '触发方式：MANUAL 手动 / SCHEDULED 定时',
  `engine_version` VARCHAR(32) COMMENT '引擎版本',
  `data_snapshot_id` VARCHAR(64) COMMENT '数据快照指纹（源表行数+最大更新时间），用于增量跳过',
  `start_time` TIMESTAMP,
  `end_time` TIMESTAMP,
  `duration_ms` BIGINT,
  `metric_total` INT          DEFAULT 0,
  `metric_ok` INT          DEFAULT 0,
  `metric_fail` INT          DEFAULT 0,
  `metric_placeholder` INT        DEFAULT 0,
  `status` VARCHAR(16) COMMENT '状态：RUNNING/SUCCESS/PARTIAL/FAILED',
  `operator` VARCHAR(64),
  `message` VARCHAR(1000),
  `create_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='质控计算批次（血缘第1层：谁触发、什么周期、耗时、数据快照）';
CALL zing_add_index('quality_calc_run', 'uk_quality_run_id', 1, '`run_id`');
CALL zing_add_index('quality_calc_run', 'idx_quality_run_period', 0, '`period_start`');

-- ---------------------------------------------------------------------
-- 3) 指标结果表（血缘第 2 层；含老系统对比值，供双跑核对）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `quality_metric_result` (
  `id` BIGINT NOT NULL,
  `run_id` VARCHAR(40),
  `metric_code` VARCHAR(32)   NOT NULL,
  `metric_name` VARCHAR(200),
  `domain_code` VARCHAR(32),
  `period_type` VARCHAR(16),
  `period_start` TIMESTAMP     NOT NULL,
  `period_end` TIMESTAMP,
  `depart_code` VARCHAR(64)   DEFAULT 'ALL',
  `numerator` DECIMAL(20,4) COMMENT '分子（命中数）',
  `denominator` DECIMAL(20,4) COMMENT '分母（同期总数）',
  `metric_value` DECIMAL(20,6) COMMENT '指标值（率类已×100）',
  `unit` VARCHAR(32),
  `ext_json` TEXT COMMENT '扩展明细 JSON（分型/分层等）',
  `calc_status` VARCHAR(16) COMMENT '计算状态：OK/PLACEHOLDER/PENDING_SOURCE/MANUAL/NO_DATA/ERROR',
  `error_msg` VARCHAR(1000),
  `compare_value` DECIMAL(20,6) COMMENT '老系统同周期值（双跑核对）',
  `compare_diff` DECIMAL(12,6) COMMENT '与服务端值的相对差异率',
  `expression_version` INT COMMENT '计算时所用的口径版本',
  `calc_time` TIMESTAMP,
  `update_time` TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
-- 值的归属：AUTO = 引擎计算产出；MANUAL = 人工录入且已填值。,
-- 重算时的保护范围靠这一列显式判断，不再依赖「calc_status='MANUAL' 且 metric_value 非空」,
-- 这种隐含约定 —— 隐含约定一旦被后人调整状态写法，人工录入的值就会在下次重算时被静默清空。,
  `value_source` VARCHAR(16)   DEFAULT 'AUTO' COMMENT '值归属：AUTO=引擎计算 / MANUAL=人工录入（重算时受保护，不被覆盖）',
  `operator` VARCHAR(64) COMMENT '人工录入的操作人（审计留痕；引擎写入的行此列为空）',
  `manual_note` VARCHAR(500) COMMENT '人工录入备注（说明取数依据，便于事后复核）',
  PRIMARY KEY (`id`)
) COMMENT='质控指标结果（周期×指标×部门；含分子分母与老系统对比，血缘第2层）';
CALL zing_add_index('quality_metric_result', 'uk_quality_result', 1, '`metric_code`, `period_type`, `period_start`, `depart_code`');
CALL zing_add_index('quality_metric_result', 'idx_quality_result_period', 0, '`period_start`, `domain_code`');

-- ---------------------------------------------------------------------
-- 4) 算子 / SQL 追溯表（血缘第 3 层）
--    因为所有计算都经统一引擎，血缘是引擎自动捕获，不靠人工标注
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `quality_calc_trace` (
  `id` BIGINT NOT NULL,
  `run_id` VARCHAR(40),
  `metric_code` VARCHAR(32),
  `dim_key` VARCHAR(64),
  `fact_name` VARCHAR(64),
  `expression_version` INT,
  `sql_hash` VARCHAR(64) COMMENT '编译后 SQL 指纹，口径变化可比对',
  `sql_text` LONGTEXT,
  `source_tables` VARCHAR(1000),
  `operators` VARCHAR(500) COMMENT '本指标用到的算子链，如 filter>derive>bucket>pick>aggregate',
  `scanned_rows` BIGINT COMMENT '事实层扫描行数（性能瓶颈定位）',
  `num_rows` INT,
  `den_rows` INT,
  `duration_ms` BIGINT,
  `calc_time` TIMESTAMP,
  PRIMARY KEY (`id`)
) COMMENT='质控计算追溯（血缘第3层：SQL、算子、扫描行数、表达式版本）';
CALL zing_add_index('quality_calc_trace', 'idx_quality_trace_run', 0, '`run_id`, `metric_code`');

-- ---------------------------------------------------------------------
-- 5) 患者级命中明细（血缘第 4 层：数字 → 到人）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `quality_metric_patient` (
  `id` BIGINT NOT NULL,
  `run_id` VARCHAR(40),
  `metric_code` VARCHAR(32),
  `period_start` TIMESTAMP,
  `depart_code` VARCHAR(64),
  `patient_id` VARCHAR(64),
  `in_hospital_no` VARCHAR(64),
  `patient_name` VARCHAR(64),
  `in_numerator` TINYINT     DEFAULT 0 COMMENT '是否计入分子：1是 0否',
  `in_denominator` TINYINT     DEFAULT 0 COMMENT '是否计入分母：1是 0否',
  `exclude_reason` VARCHAR(300) COMMENT '未计入原因',
  `raw_json` TEXT COMMENT '该患者参与计算的原始取值明细 JSON',
  `create_time` TIMESTAMP   DEFAULT CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='质控指标患者级明细（血缘第4层：谁进分子、谁进分母、未命中原因）';
CALL zing_add_index('quality_metric_patient', 'idx_quality_patient_metric', 0, '`metric_code`, `period_start`');
CALL zing_add_index('quality_metric_patient', 'idx_quality_patient_no', 0, '`in_hospital_no`');

-- ---------------------------------------------------------------------
-- 6) 月度汇总宽表（1-12 月横排，页面直读 + xlsx 导出）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `quality_monthly_report` (
  `id` BIGINT NOT NULL,
  `year` INT           NOT NULL,
  `index_code` VARCHAR(32)   NOT NULL,
  `index_name` VARCHAR(200),
  `domain_code` VARCHAR(32),
  `unit` VARCHAR(32),
  `value_type` VARCHAR(16),
  `depart_code` VARCHAR(64)   DEFAULT 'ALL',
  `m01` DECIMAL(20,6), `m02` DECIMAL(20,6), `m03` DECIMAL(20,6) COMMENT '1 月指标值',
  `m04` DECIMAL(20,6), `m05` DECIMAL(20,6), `m06` DECIMAL(20,6),
  `m07` DECIMAL(20,6), `m08` DECIMAL(20,6), `m09` DECIMAL(20,6),
  `m10` DECIMAL(20,6), `m11` DECIMAL(20,6), `m12` DECIMAL(20,6),
  `q1` DECIMAL(20,6), `q2` DECIMAL(20,6), `q3` DECIMAL(20,6), `q4` DECIMAL(20,6) COMMENT '一季度汇总值',
  `year_total` DECIMAL(20,6) COMMENT '全年合计（数类求和；率类为空）',
  `year_avg` DECIMAL(20,6) COMMENT '全年平均（率类为月度均值；数类为月均）',
  `max_month` TINYINT,
  `min_month` TINYINT,
  `status` TINYINT       DEFAULT 1,
  `update_time` TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='质控月度汇总宽表（一行一个指标，1-12 月横排）';
CALL zing_add_index('quality_monthly_report', 'uk_quality_monthly', 1, '`year`, `index_code`, `depart_code`');
CALL zing_add_index('quality_monthly_report', 'idx_quality_monthly_domain', 0, '`domain_code`');

-- ---------------------------------------------------------------------
-- 7) 页面注册（外链 pageCode）
-- ---------------------------------------------------------------------
DELETE FROM `zing_page_config`
 WHERE `page_code` IN ('quality-board', 'quality-monthly');
INSERT INTO `zing_page_config`
    (`page_code`, `page_name`, `frontend_path`, `remark`, `status`)
VALUES
    ('quality-board', '质控指标看板', '/page/quality-board',
     '质控指标中台：127 条指标按域分组展示、周期切换、点数字下钻分子分母与患者明细', 1),
    ('quality-monthly', '质控月度汇总', '/page/quality-monthly',
     '质控月度汇总：1-12 月横排对比，支持同比/环比与 xlsx 导出', 1);
