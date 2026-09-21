-- ============================================================
-- MySQL 8.x 版本（由达梦 DM8 脚本自动转换 + 人工校验）
-- 主键由应用雪花算法生成（MyBatis-Plus ASSIGN_ID），显式插入时以插入值为准；
-- 仅当 INSERT 省略 id 时由 AUTO_INCREMENT 兜底（对应达梦原有的 SEQ.NEXTVAL 默认值）
-- 执行：mysql -uroot -p < 本文件（需先执行 00b_idempotent_helpers.sql）
-- ============================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

-- ---- 以下为原达梦 PL/SQL 幂等块转换得到的 DDL ----
CREATE TABLE IF NOT EXISTS `ards_prone_record` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `record_no` VARCHAR(64) COMMENT '记录编号（PP-yyyyMMdd-序号）',
  `patient_id` VARCHAR(64) COMMENT '患者ID（patient_info.id）',
  `in_hospital_no` VARCHAR(64)  NOT NULL COMMENT '住院号',
  `patient_name` VARCHAR(64),
  `sex` VARCHAR(8),
  `age` VARCHAR(16),
  `bed_code` VARCHAR(32),
  `depart_code` VARCHAR(64),
  `diagnosis` VARCHAR(500),
  `ards_grade` VARCHAR(16) COMMENT 'ARDS 分级：轻度/中度/重度',
  `admit_date` VARCHAR(10),
  `prone_day` VARCHAR(32) COMMENT '俯卧位天数（如 第2天）',
  `prone_times` INT           DEFAULT 1,
  `attending_doctor` VARCHAR(64),
  `record_date` VARCHAR(10),
  `start_time` TIMESTAMP,
  `end_time` TIMESTAMP,
  `duration_min` INT COMMENT '俯卧位持续时长（分钟，系统按开始/结束时间计算）',
  `apache2_score` VARCHAR(16) COMMENT 'APACHE II 评分（是否显示由参数 ARDS_PRONE_APACHE2_SHOW 控制）',
  `complication_json` TEXT COMMENT '并发症勾选（JSON 数组）',
  `complication_desc` TEXT,
  `stop_type` VARCHAR(32) COMMENT '终止类型：none/reach/emergency',
  `stop_detail` TEXT,
  `remark` TEXT,
  `nurse_sign` VARCHAR(64),
  `doctor_sign` VARCHAR(64),
  `senior_sign` VARCHAR(64),
  `sign_time` TIMESTAMP,
  `record_status` VARCHAR(16)   DEFAULT 'draft' COMMENT '记录状态：draft 填写中 / submitted 已提交',
  `pdf_data` LONGTEXT,
  `pdf_name` VARCHAR(255),
  `archive_status` TINYINT       DEFAULT 0 COMMENT '归档状态：0未归档 1已归档',
  `archive_time` TIMESTAMP,
  `archive_doc_no` VARCHAR(128) COMMENT '归档文档号（HIS 返回）',
  `file_path` VARCHAR(500),
  `status` TINYINT       DEFAULT 1 COMMENT '状态：1正常 0已删除',
  `create_by` VARCHAR(64),
  `create_time` TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
  `update_by` VARCHAR(64),
  `update_time` TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
PRIMARY KEY (`id`)
) COMMENT='ARDS 俯卧位通气治疗记录主表';
CALL zing_add_index('ards_prone_record', 'idx_ards_prone_patient', 0, '`in_hospital_no`');
CALL zing_add_index('ards_prone_record', 'idx_ards_prone_depart', 0, '`depart_code`');
CALL zing_add_index('ards_prone_record', 'idx_ards_prone_time', 0, '`start_time`');
CREATE TABLE IF NOT EXISTS `ards_prone_timepoint` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `record_id` BIGINT        NOT NULL,
  `tp_index` INT           NOT NULL COMMENT '时点序号（0 起，0 = T0 翻身前）',
  `tp_label` VARCHAR(64),
  `offset_minutes` INT COMMENT '相对俯卧位开始时间的偏移（分钟）',
  `plan_time` TIMESTAMP,
  `collect_status` VARCHAR(16)   DEFAULT 'pending' COMMENT '采集状态：pending 待采集 / done 已采集 / closed 已关闭（不做告警，仅中性状态）',
  `status` TINYINT       DEFAULT 1 COMMENT '状态：1正常 0已删除（软删除，历史数据保留可查）',
  `create_by` VARCHAR(64),
  `create_time` TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
  `update_by` VARCHAR(64),
  `update_time` TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
PRIMARY KEY (`id`)
) COMMENT='ARDS 俯卧位记录时点表';
CALL zing_add_index('ards_prone_timepoint', 'idx_ards_prone_tp_rec', 0, '`record_id`');
CREATE TABLE IF NOT EXISTS `ards_prone_cell` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `record_id` BIGINT        NOT NULL,
  `tp_index` INT           NOT NULL,
  `param_key` VARCHAR(64)   NOT NULL,
  `value_text` VARCHAR(500),
  `value_num` DECIMAL(18, 4),
  `source` VARCHAR(16)   DEFAULT 'man' COMMENT '来源：auto 自动采集 / lis 检验同步 / calc 系统计算 / man 手工录入',
  `collect_time` TIMESTAMP COMMENT '采集时间（自动采集与检验同步值必带）',
  `manual_reason` VARCHAR(500) COMMENT '人工修正原因（自动值被覆盖时必填）',
  `status` TINYINT       DEFAULT 1,
  `create_by` VARCHAR(64),
  `create_time` TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
  `update_by` VARCHAR(64),
  `update_time` TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
PRIMARY KEY (`id`)
) COMMENT='ARDS 俯卧位记录单元格值表（参数 × 时点）';
CALL zing_add_index('ards_prone_cell', 'idx_ards_prone_cell_rec', 0, '`record_id`, `tp_index`');
CREATE TABLE IF NOT EXISTS `ards_prone_cell_log` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `record_id` BIGINT        NOT NULL,
  `tp_index` INT,
  `param_key` VARCHAR(64),
  `old_value` VARCHAR(500),
  `new_value` VARCHAR(500),
  `old_source` VARCHAR(16),
  `new_source` VARCHAR(16),
  `reason` VARCHAR(500) COMMENT '更正原因（覆盖自动值时必填）',
  `operator` VARCHAR(64),
  `create_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
PRIMARY KEY (`id`)
) COMMENT='ARDS 俯卧位记录单元格更正留痕表（提交后不限时更正，全程留痕）';
CALL zing_add_index('ards_prone_cell_log', 'idx_ards_prone_log_rec', 0, '`record_id`');
CREATE TABLE IF NOT EXISTS `ards_prone_tp_tpl` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `depart_code` VARCHAR(64)   DEFAULT '',
  `tp_index` INT           NOT NULL,
  `tp_label` VARCHAR(64)   NOT NULL,
  `offset_minutes` INT           NOT NULL COMMENT '相对开始时间偏移（分钟），T0 为 0',
  `status` TINYINT       DEFAULT 1,
  `create_by` VARCHAR(64),
  `create_time` TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
  `update_by` VARCHAR(64),
  `update_time` TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
PRIMARY KEY (`id`)
) COMMENT='ARDS 俯卧位时点模板（科室为空=全院默认）';
CALL zing_add_index('ards_prone_tp_tpl', 'idx_ards_prone_tpl_dep', 0, '`depart_code`');
CALL zing_add_index('ards_prone_cell', 'uk_ards_prone_cell', 1, '`record_id`, `tp_index`, `param_key`, CASE WHEN `status` = 1 THEN 1 ELSE NULL END');

-- =====================================================================
-- 21) ARDS 俯卧位通气治疗记录模块增量（达梦 DM8）
--
-- 内容：
--   1) ards_prone_record       俯卧位疗程主记录（患者信息 + 并发症/终止/签名 + 归档字段）
--   2) ards_prone_timepoint    时点（T0 / +15min / +2h …，支持自定义与软删除）
--   3) ards_prone_cell         单元格值（参数 × 时点），每值带来源与采集时间
--   4) ards_prone_cell_log     单元格更正留痕（原值/新值/修改人/时间/原因）
--   5) ards_prone_tp_tpl       时点模板（depart_code 为空 = 全院默认）
--   6) zing_page_config        页面注册（列表页 / 填写页）
--   7) zing_param_group + zing_sys_param  参数分组与参数种子
--      （归档接口地址 ARCHIVE_API_URL、归档目录 ARCHIVE_DIR 与 SOFA/APACHE II 共用，本脚本不重复建）
--
-- 设计依据：docs/ards-prone/设计方案.md v4.1
--   · 不做告警、不做阈值配置、不沿用历史值、提交后不限时更正
--   · 归档调用现有归档接口，与 APACHE II、SOFA 同一接口同一传参，仅 doc_code 不同（ARDS_PRONE_REC）
--   · APACHE II 由参数开关 ARDS_PRONE_APACHE2_SHOW 控制（默认显示、全院统一）
--
-- 幂等：所有 DDL 用 PL/SQL 先判存在性；页面注册与参数种子用 NOT EXISTS 守卫，可重复执行。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/Sa_20250815@100.120.1.102:14236
--   SQL> start /opt/zing-doctor/sql/21_ards_prone.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) 主记录表
-- ---------------------------------------------------------------------

-- ---------------------------------------------------------------------
-- 2) 时点表
-- ---------------------------------------------------------------------

-- ---------------------------------------------------------------------
-- 3) 单元格值表（参数 × 时点，每值带来源与采集时间）
-- ---------------------------------------------------------------------

-- ---------------------------------------------------------------------
-- 4) 单元格更正留痕表
-- ---------------------------------------------------------------------

-- ---------------------------------------------------------------------
-- 5) 时点模板表（depart_code 为空表示全院默认模板）
-- ---------------------------------------------------------------------

-- 全院默认时点模板：T0 翻身前 + 10 个时点（与纸质原表一致）

-- ---------------------------------------------------------------------
-- 6) 页面注册（外链 pageCode → 前端路由）
-- ---------------------------------------------------------------------
DELETE FROM `zing_page_config`
 WHERE `page_code` IN ('ards-prone-list', 'ards-prone-record');
INSERT INTO `zing_page_config`
    (`page_code`, `page_name`, `frontend_path`, `remark`, `status`)
VALUES
    ('ards-prone-list', 'ARDS 俯卧位通气记录', '/page/ards-prone-list',
     'ARDS 俯卧位通气治疗记录列表：按住院号查看历史疗程与归档状态', 1),
    ('ards-prone-record', 'ARDS 俯卧位通气记录填写', '/page/ards-prone-record',
     'ARDS 俯卧位通气治疗记录填写：37 项参数 × 时点矩阵、时点配置、打印预览与 HIS 归档回传', 1);

-- ---------------------------------------------------------------------
-- 7) 参数分组与参数种子
--    归档接口地址 ARCHIVE_API_URL / 归档目录 ARCHIVE_DIR 与 SOFA、APACHE II 共用，此处不重复建
-- ---------------------------------------------------------------------
INSERT INTO `zing_param_group`
    (`group_code`, `group_name`, `sort_no`, `status`, `remark`, `create_time`, `update_time`)
SELECT 'ards_prone', 'ARDS 俯卧位通气', 30, 1, 'ARDS 俯卧位通气治疗记录：归档参数、APACHE II 显示开关、时点模板',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `zing_param_group` WHERE `group_code` = 'ards_prone');

INSERT INTO `zing_sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `param_type`, `default_value`, `sort_no`, `status`, `remark`)
SELECT 'ARDS_PRONE_APACHE2_SHOW', 'APACHE II 评分显示', '1', 'ards_prone', 'switch', '1', 1, 1,
       '是否显示 APACHE II：作用于填写页、文书预览、打印文书、回传文书四处；默认显示，全院统一（不允许科室单独设置）'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `zing_sys_param` WHERE `param_key` = 'ARDS_PRONE_APACHE2_SHOW');

INSERT INTO `zing_sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `param_type`, `default_value`, `sort_no`, `status`, `remark`)
SELECT 'ARDS_PRONE_ARCHIVE_ENABLED', '归档回传功能启用', '0', 'ards_prone', 'switch', '0', 3, 1,
       '是否显示归档回传按钮与状态列：关闭后列表页隐藏归档列、填写页隐藏归档按钮与打印并归档按钮；默认关闭，院方未对接归档接口时不要开启'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `zing_sys_param` WHERE `param_key` = 'ARDS_PRONE_ARCHIVE_ENABLED');

INSERT INTO `zing_sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `param_type`, `default_value`, `sort_no`, `status`, `remark`)
SELECT 'ARDS_PRONE_DOC_CODE', '归档文档类型编码', 'ARDS_PRONE_REC', 'ards_prone', 'text', 'ARDS_PRONE_REC', 2, 1,
       '文书归档 doc_code：与 APACHE II(apache2)、SOFA(sofa) 走同一归档接口与传参，仅此编码不同'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `zing_sys_param` WHERE `param_key` = 'ARDS_PRONE_DOC_CODE');

INSERT INTO `zing_sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `param_type`, `default_value`, `sort_no`, `status`, `remark`)
SELECT 'ARDS_PRONE_TPL_NO', '文书模板编号', '', 'ards_prone', 'text', '', 3, 0,
       '院方归档接口要求的模板编号（如无要求可留空）'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `zing_sys_param` WHERE `param_key` = 'ARDS_PRONE_TPL_NO');

INSERT INTO `zing_sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `param_type`, `default_value`, `sort_no`, `status`, `remark`)
SELECT 'ARDS_PRONE_ARCHIVE_ENABLED', '启用文书归档', '1', 'ards_prone', 'switch', '1', 4, 1,
       '关闭后打印与保存不再推送归档，仅本地留档'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `zing_sys_param` WHERE `param_key` = 'ARDS_PRONE_ARCHIVE_ENABLED');

INSERT INTO `zing_sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `param_type`, `default_value`, `sort_no`, `status`, `remark`)
SELECT 'ARDS_PRONE_RECORD_PREFIX', '记录编号前缀', 'PP', 'ards_prone', 'text', 'PP', 5, 1,
       '记录编号前缀：编号规则为 前缀-yyyyMMdd-当日序号'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM `zing_sys_param` WHERE `param_key` = 'ARDS_PRONE_RECORD_PREFIX');

-- ---------------------------------------------------------------------
-- 8) 单元格唯一约束：防止并发保存产生重复记录
--    函数索引兼容软删除：status=0 时第四列为 NULL，不参与唯一约束；
--    仅 status=1 的记录受 (record_id, tp_index, param_key) 唯一约束。
--    建索引前先清理历史重复数据（保留 id 最大的一条，其余软删除）。
-- ---------------------------------------------------------------------
