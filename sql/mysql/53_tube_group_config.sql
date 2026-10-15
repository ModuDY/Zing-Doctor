-- =====================================================================
-- 53_tube_group_config.sql（MySQL 镜像）
-- 对应达梦版：sql/53_tube_group_config.sql
-- 幂等：可重复执行。
-- =====================================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `zing_doctor_db_prod`.`config_tube_group` (
    `id`          BIGINT NOT NULL,
    `tube_code`   VARCHAR(64) NOT NULL COMMENT 'patient_tube.tube_code，唯一',
    `tube_name`   VARCHAR(128) COMMENT '展示名（默认取 tube_name）',
    `group_name`  VARCHAR(64) NOT NULL DEFAULT '其他' COMMENT '归入统计组',
    `risk_level`  VARCHAR(8)  NOT NULL DEFAULT '中' COMMENT '危重等级：高/中/低',
    `sort_no`     INT DEFAULT 0,
    `remark`      VARCHAR(1000),
    `status`      TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `create_by`   VARCHAR(64),
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`   VARCHAR(64),
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_tube_group_code` (`tube_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '导管类型分组配置（自定义归组+危重等级）';
