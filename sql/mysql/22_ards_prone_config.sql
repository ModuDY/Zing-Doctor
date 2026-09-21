-- ============================================================
-- MySQL 8.x 版本（由达梦 DM8 脚本自动转换 + 人工校验）
-- 主键由应用雪花算法生成（MyBatis-Plus ASSIGN_ID），显式插入时以插入值为准；
-- 仅当 INSERT 省略 id 时由 AUTO_INCREMENT 兜底（对应达梦原有的 SEQ.NEXTVAL 默认值）
-- 执行：mysql -uroot -p < 本文件（需先执行 00b_idempotent_helpers.sql）
-- ============================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

-- ---- 以下为原达梦 PL/SQL 幂等块转换得到的 DDL ----
CREATE TABLE IF NOT EXISTS `ards_prone_config` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `config_type` VARCHAR(32)  NOT NULL COMMENT '数据源通道：observe_item 监护/呼吸机 / lis_item 检验/血气',
  `config_key` VARCHAR(128) NOT NULL COMMENT 'ARDS 参数编码（ArdsProneDict 的 key，如 hr / map / peep / pao2）',
  `config_value` VARCHAR(500) COMMENT '匹配值：item_code 列表 或 名称关键字列表（逗号分隔多值，任一命中即可）',
  `match_type` VARCHAR(16)  DEFAULT 'name' NOT NULL COMMENT '匹配方式：code 按 item_code 精确 / name 按项目名称包含（忽略大小写与空格）',
  `priority` INT          DEFAULT 10 COMMENT '优先级：数字小优先（同参数多渠道时表达「监护优先 / 检验兜底」）',
  `window_min` INT COMMENT '采集窗口覆盖（分钟），空则走字典默认（体征 ±15 / 检验 ±60）',
  `unit_scale` DECIMAL(18, 6) COMMENT '单位线性换算系数：值 × scale + offset（如 FiO₂ 0.4 → 40 时 scale=100）',
  `unit_offset` DECIMAL(18, 6) COMMENT '单位线性换算偏移量',
  `item_name` VARCHAR(128) COMMENT '项目名称（展示用）',
  `remark` VARCHAR(255),
  `sort_no` INT          DEFAULT 1,
  `status` TINYINT      DEFAULT 1 COMMENT '状态：1启用 0停用（停用项不参与取数，该参数回退内置关键字）',
  `create_by` VARCHAR(64),
  `create_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
  `update_by` VARCHAR(64),
  `update_time` TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
PRIMARY KEY (`id`)
) COMMENT='ARDS 俯卧位采集映射配置表（参数项 ← 数据源项目；表空或未命中回退字典内置关键字）';
CALL zing_add_index('ards_prone_config', 'idx_ards_prone_config_key', 0, '`config_key`, `config_type`, `status`');
ALTER TABLE `ards_prone_record` MODIFY `admit_date` VARCHAR(20);
ALTER TABLE `ards_prone_record` MODIFY `record_date` VARCHAR(20);

-- =====================================================================
-- 22_ards_prone_config.sql
-- ARDS 俯卧位通气治疗记录 —— 采集映射配置（参数项 → 数据源项目）
--
-- 背景：
--   原先把「ARDS 参数项 ← 监护/LIS 项目」的匹配关键字硬编码在 ArdsProneDict，
--   现场数据元命名一变就要改代码发版。本脚本把映射搬到配置表，与 sofa_config /
--   apache2_config 同一套做法（config_type + config_key + config_value），
--   并追加 match_type / priority / window_min / unit_scale / unit_offset 表达力字段。
--
-- 内容：
--   1) ards_prone_config 表 + 序列 + 索引（监测项目映射配置）
--   2) ards_prone_record 的 admit_date / record_date 扩列：VARCHAR(10) → VARCHAR(20)
--      （支持显示格式 yyyy-MM-dd HH:mm；未执行时代码自动降级为只存日期）
--   3) zing_page_config 注册映射配置页（ards-prone-config）
--   4) 不预置种子：由配置页「一键从内置生成」按 ArdsProneDict 写入（幂等，现场可改）
--
-- 说明：本脚本不改动 ards_prone_cell 表结构。取值来源项目（item_code / 项目名称）
--       在采集时随接口返回并在「采集明细」中即时展示，不落库，
--       以避免「jar 已升级但库未加列」导致的取数报错。
--
-- 取值口径（与实现一致）：
--   · 规则按 priority 升序尝试（数字小优先），同优先级先监护（observe_item）后退检验（lis_item）
--   · match_type=code 按 item_code / lis_item_code 精确匹配；name 按项目名称包含匹配（忽略大小写与空格）
--   · config_value 逗号分隔多值，任一命中即可；命中时间超出该参数窗口（window_min 或字典默认 ±15/±60）跳过
--   · 该参数无启用规则或规则全部未命中 → 回退 ArdsProneDict 内置关键字（零回归）
--   · 窗口内无数据一律置空转手工，不沿用历史值
--
-- 幂等：所有 DDL 用 PL/SQL 先判存在性；页面注册用 DELETE + INSERT 守卫，可重复执行。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/Sa_20250815@100.120.1.102:14236
--   SQL> start /opt/zing-doctor/sql/22_ards_prone_config.sql
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) 映射配置表
-- ---------------------------------------------------------------------

-- ---------------------------------------------------------------------
-- 2) 入院日期 / 记录日期扩列：VARCHAR(10) → VARCHAR(20)
--    显示格式要求 yyyy-MM-dd HH:mm（16 字符），原 10 位只能存日期。
--    代码侧自适应：未扩列时自动降级写 yyyy-MM-dd（降级期间不报错，只是少了时间）。
-- ---------------------------------------------------------------------

-- ---------------------------------------------------------------------
-- 3) 页面注册（外链 pageCode）
-- ---------------------------------------------------------------------
DELETE FROM `zing_page_config`
 WHERE `page_code` = 'ards-prone-config';
INSERT INTO `zing_page_config`
    (`page_code`, `page_name`, `frontend_path`, `remark`, `status`)
VALUES
    ('ards-prone-config', 'ARDS 俯卧位数据映射配置', '/page/ards-prone-config',
     'ARDS 俯卧位采集映射配置：参数项 → 监护/LIS 项目映射、候选选择、试采核对', 1);

-- ---------------------------------------------------------------------
-- 4) 核对（可选）
-- ---------------------------------------------------------------------
--   SELECT `config_key`, `config_type`, `match_type`, `config_value`, `priority`, `status`
--     FROM `ards_prone_config` ORDER BY `config_key`, `priority`;
--   SELECT `page_code`, `page_name`, `frontend_path`, `status`
--     FROM `zing_page_config` WHERE `page_code` = 'ards-prone-config';
