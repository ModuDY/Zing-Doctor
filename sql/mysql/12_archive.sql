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
-- 医生决策系统 - 系统参数 + 评分文书归档增量（达梦 DM8）
--
-- 内容：
--   1) zing_sys_param        系统参数表（归档接口地址 / 归档目录）
--   2) zing_archive_log      归档推送流水表（跟踪每次推送与结果）
--   3) sofa_score_record     新增归档字段 archive_status / archive_time / file_path
--   4) apache2_score_record  同上
--   5) zing_page_config      页面注册（参数设置 /page/param-config）
--
-- 幂等：ALTER TABLE ADD COLUMN 由部署脚本做列存在检查，可重复执行；
--       页面注册与默认参数用「先删后插 / NOT EXISTS」守卫，重复执行不产生重复行。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/Sa_20250815@100.120.1.102:14236
--   SQL> start /opt/zing-doctor/sql/12_archive.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) 系统参数表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `zing_sys_param` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `param_key` VARCHAR(64)   NOT NULL COMMENT '参数键（唯一，如 ARCHIVE_API_URL / ARCHIVE_DIR）',
  `param_name` VARCHAR(128)  NOT NULL COMMENT '参数名称（页面展示）',
  `param_value` VARCHAR(1000) COMMENT '参数值',
  `param_group` VARCHAR(64)   DEFAULT 'archive' COMMENT '参数分组（archive=文书归档）',
  `sort_no` INT           DEFAULT 0 COMMENT '排序号',
  `status` TINYINT       DEFAULT 1 COMMENT '状态：1启用 0停用',
  `remark` VARCHAR(500),
  `create_by` VARCHAR(64),
  `create_time` TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
  `update_by` VARCHAR(64),
  `update_time` TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='系统参数表（参数设置页面维护，如文书归档接口地址、归档目录）';

CALL zing_add_index('zing_sys_param', 'uk_zing_sys_param_key', 1, '`param_key`');

-- ---------------------------------------------------------------------
-- 2) 归档推送流水表：记录每次归档/撤销的时间、file_path 与对方响应，便于追溯
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `zing_archive_log` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `biz` VARCHAR(32) COMMENT '业务：SOFA / APACHE2',
  `record_id` BIGINT COMMENT '评分记录 ID',
  `in_hospital_no` VARCHAR(64),
  `patient_name` VARCHAR(64),
  `doc_code` VARCHAR(32) COMMENT '文书编码：sofa / apache2',
  `score_date` VARCHAR(10) COMMENT '评分日期（yyyy-MM-dd）',
  `file_path` VARCHAR(500) COMMENT '按归档目录规则拼出的文件路径',
  `api_url` VARCHAR(500),
  `op_type` VARCHAR(16) COMMENT '操作：push=归档推送 unmark=撤销标记',
  `success` TINYINT       DEFAULT 0 COMMENT '是否成功：1成功 0失败',
  `http_status` INT,
  `resp_code` INT,
  `resp_message` VARCHAR(1000) COMMENT '失败原因 / 对方返回报文摘要',
  `operator` VARCHAR(64),
  `create_time` TIMESTAMP     DEFAULT CURRENT_TIMESTAMP NOT NULL,
  PRIMARY KEY (`id`)
) COMMENT='文书归档推送流水';

CALL zing_add_index('zing_archive_log', 'idx_zing_archive_log_rec', 0, '`biz`, `record_id`');
CALL zing_add_index('zing_archive_log', 'idx_zing_archive_log_no', 0, '`in_hospital_no`');

-- ---------------------------------------------------------------------
-- 3) 评分记录归档字段
--    archive_status：0 待归档（默认） 1 已归档
--    archive_time  ：最近一次归档成功时间
--    file_path     ：按归档目录规则拼出的文件路径（每次归档刷新为最新）
-- ---------------------------------------------------------------------
CALL zing_add_column('sofa_score_record', 'archive_status', 'TINYINT DEFAULT 0 COMMENT ''归档状态：0待归档 1已归档''');
CALL zing_add_column('sofa_score_record', 'archive_time', 'TIMESTAMP COMMENT ''最近一次归档成功时间''');
CALL zing_add_column('sofa_score_record', 'file_path', 'VARCHAR(500) COMMENT ''文书归档路径（按参数设置页的归档目录规则生成）''');

CALL zing_add_column('apache2_score_record', 'archive_status', 'TINYINT DEFAULT 0 COMMENT ''归档状态：0待归档 1已归档''');
CALL zing_add_column('apache2_score_record', 'archive_time', 'TIMESTAMP COMMENT ''最近一次归档成功时间''');
CALL zing_add_column('apache2_score_record', 'file_path', 'VARCHAR(500) COMMENT ''文书归档路径（按参数设置页的归档目录规则生成）''');

-- ---------------------------------------------------------------------
-- 4) 页面注册（外链 pageCode）
-- ---------------------------------------------------------------------
DELETE FROM `zing_page_config` WHERE `page_code` = 'param-config';
INSERT INTO `zing_page_config`
    (`page_code`, `page_name`, `frontend_path`, `remark`, `status`)
VALUES
    ('param-config', '参数设置', '/page/param-config',
     '系统参数设置：文书归档接口地址、归档目录等', 1);

-- ---------------------------------------------------------------------
-- 5) 默认参数（不存在才写入，不覆盖院内已改配置）
--    ARCHIVE_API_URL：归档接口地址（完整 URL，含 jodName）
--    ARCHIVE_DIR    ：归档目录规则，占位符 #in_hospital_no# / #doc_code# / #score_date#
--                     也支持 #patient_id# / #patient_name#
-- ---------------------------------------------------------------------
INSERT INTO `zing_sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`, `status`, `remark`)
SELECT 'ARCHIVE_API_URL', '文书归档接口地址', '', 'archive', 1, 1,
       '评分文书归档推送地址（完整 URL，含 jodName），SOFA 与 APACHE II 共用'
  FROM DUAL
 WHERE NOT EXISTS (
        SELECT 1 FROM `zing_sys_param` WHERE `param_key` = 'ARCHIVE_API_URL'
  );

INSERT INTO `zing_sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`, `status`, `remark`)
SELECT 'ARCHIVE_DIR', '归档目录', '/ICU/#in_hospital_no#/#doc_code#/#score_date#', 'archive', 2, 1,
       '文书存放目录规则：占位符 #in_hospital_no# 住院号 / #doc_code# 文书编码(sofa|apache2) / #score_date# 评分日期，也支持 #patient_id# / #patient_name#'
  FROM DUAL
 WHERE NOT EXISTS (
        SELECT 1 FROM `zing_sys_param` WHERE `param_key` = 'ARCHIVE_DIR'
  );
