-- =====================================================================
-- 55_hemodynamics_adl.sql（MySQL 镜像）
-- 对应达梦版：sql/55_hemodynamics_adl.sql
-- 幂等：可重复执行。
-- =====================================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `zing_doctor_db_prod`.`patient_doc_hemodynamics` (
    `id`             BIGINT NOT NULL,
    `patient_id`     VARCHAR(64) NOT NULL COMMENT 'ICU 患者 ID',
    `in_hospital_no` VARCHAR(64) COMMENT '住院号',
    `patient_name`   VARCHAR(64) COMMENT '患者姓名',
    `depart_code`    VARCHAR(64) COMMENT '科室编码',
    `measure_time`   DATETIME NOT NULL COMMENT '测量时间',
    `sbp`            DECIMAL(6,1) COMMENT '收缩压 mmHg',
    `dbp`            DECIMAL(6,1) COMMENT '舒张压 mmHg',
    `map_val`        DECIMAL(6,1) COMMENT '平均动脉压 mmHg（缺省按 SBP/DBP 补算）',
    `cvp`            DECIMAL(6,1) COMMENT '中心静脉压 mmHg',
    `co`             DECIMAL(6,2) COMMENT '心输出量 L/min',
    `svr`            DECIMAL(8,1) COMMENT '外周血管阻力 = (MAP-CVP)/CO×80，服务端计算',
    `ef`             DECIMAL(5,1) COMMENT '射血分数 %（心脏超声）',
    `method`         VARCHAR(32) COMMENT '测量方式：PiCCO/Swan-Ganz/床旁超声/其他',
    `remark`         VARCHAR(1000),
    `status`         TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0已删除',
    `create_by`      VARCHAR(64),
    `create_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`      VARCHAR(64),
    `update_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_phd_patient_time` (`patient_id`, `measure_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '血流动力学/心功能手工录入';

CREATE TABLE IF NOT EXISTS `zing_doctor_db_prod`.`patient_doc_adl` (
    `id`             BIGINT NOT NULL,
    `patient_id`     VARCHAR(64) NOT NULL COMMENT 'ICU 患者 ID',
    `in_hospital_no` VARCHAR(64) COMMENT '住院号',
    `patient_name`   VARCHAR(64) COMMENT '患者姓名',
    `depart_code`    VARCHAR(64) COMMENT '科室编码',
    `adl_time`       DATETIME NOT NULL COMMENT '评估时间',
    `feeding`        TINYINT COMMENT '进食 0/5/10',
    `bathing`        TINYINT COMMENT '洗澡 0/5',
    `grooming`       TINYINT COMMENT '修饰 0/5',
    `dressing`       TINYINT COMMENT '穿衣 0/5/10',
    `bowels`         TINYINT COMMENT '控制大便 0/5/10',
    `bladder`        TINYINT COMMENT '控制小便 0/5/10',
    `toilet`         TINYINT COMMENT '如厕 0/5/10',
    `transfer`       TINYINT COMMENT '床椅转移 0/5/10/15',
    `mobility`       TINYINT COMMENT '平地行走 0/5/10/15',
    `stairs`         TINYINT COMMENT '上下楼梯 0/5/10',
    `total_score`    TINYINT COMMENT '总分 0~100，服务端求和',
    `remark`         VARCHAR(1000),
    `status`         TINYINT NOT NULL DEFAULT 1 COMMENT '1正常 0已删除',
    `create_by`      VARCHAR(64),
    `create_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`      VARCHAR(64),
    `update_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_padl_patient_time` (`patient_id`, `adl_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'ADL（Barthel 指数）手工录入';
