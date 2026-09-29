-- =====================================================================
-- 37) PK/PD 抗菌药物知识库（MySQL / MariaDB 10.5）
-- 对应达梦版：sql/37_abx_pkpd_knowledge.sql
-- =====================================================================

CREATE TABLE IF NOT EXISTS `zing_doctor_db_prod`.`config_abx_pkpd_knowledge` (
    `id`                   BIGINT       NOT NULL,
    `drug_name`            VARCHAR(128) NOT NULL,
    `drug_full_name`       VARCHAR(255) DEFAULT NULL,
    `pkpd_type`            VARCHAR(32)  NOT NULL,
    `target_param`         VARCHAR(32)  DEFAULT NULL,
    `target_value`         VARCHAR(64)  DEFAULT NULL,
    `protein_binding`      INT          DEFAULT 0,
    `clearance_route`      VARCHAR(32)  DEFAULT NULL,
    `usual_dose`           VARCHAR(128) DEFAULT NULL,
    `dose_adjust`          VARCHAR(500) DEFAULT NULL,
    `high_protein_binding` TINYINT      DEFAULT 0,
    `tdm_required`         TINYINT      DEFAULT 0,
    `remark`               VARCHAR(500) DEFAULT NULL,
    `status`               TINYINT      DEFAULT 1 NOT NULL,
    `create_time`          DATETIME     DEFAULT CURRENT_TIMESTAMP NOT NULL,
    `update_time`          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_abx_pkpd_drug` (`drug_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PK/PD 抗菌药物知识库（界面化配置，优先于内置枚举）';

-- 页面注册（幂等）：/entry/{page_code} 只允许访问 sys_page_config 中已注册且启用的页面
DELETE FROM `sys_page_config`
 WHERE `page_code` = 'abx-pkpd-config';

INSERT INTO `sys_page_config`
    (`page_code`, `page_name`, `frontend_path`, `remark`, `status`)
VALUES
    ('abx-pkpd-config', 'PK/PD 药物知识库配置', '/page/abx-pkpd-config',
     '抗菌药物 PK/PD 参数界面化配置，优先于内置枚举', 1);

