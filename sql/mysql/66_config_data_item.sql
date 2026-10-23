-- =====================================================================
-- 66_config_data_item.sql（MySQL / MariaDB 镜像）
-- 对应达梦版：sql/66_config_data_item.sql（字段说明见该文件头）
-- 幂等：可重复执行。
-- =====================================================================
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `zing_doctor_db_prod`.`config_data_item` (
    `id`            BIGINT       NOT NULL,
    `domain`        VARCHAR(16)  NOT NULL COMMENT 'VITAL/VENT/IO/LAB',
    `group_code`    VARCHAR(32)  COMMENT 'LAB 分组：LIVER/COAG/RENAL/INFECTION',
    `group_name`    VARCHAR(64)  COMMENT '分组显示名',
    `item_key`      VARCHAR(64)  NOT NULL COMMENT '程序内部键，永不随医院变',
    `item_name`     VARCHAR(128) NOT NULL COMMENT '展示名',
    `unit`          VARCHAR(32),
    `source`        VARCHAR(16)  COMMENT '无创/有创/未标明',
    `match_type`    VARCHAR(16)  NOT NULL COMMENT 'CODE/NAME_LIKE/NAME_EXACT',
    `match_value`   VARCHAR(500) NOT NULL COMMENT '本院实际的编码或项目名关键字',
    `exclude_value` VARCHAR(500) COMMENT '排除词，| 分隔',
    `sort_no`       INT DEFAULT 0,
    `status`        TINYINT DEFAULT 1 NOT NULL COMMENT '1启用 0停用',
    `remark`        VARCHAR(255),
    `create_by`     VARCHAR(64),
    `create_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_by`     VARCHAR(64),
    `update_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_data_item_key` (`domain`, `group_code`, `item_key`, `match_value`),
    KEY `idx_config_data_item_domain` (`domain`, `status`, `sort_no`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '数据项映射配置（换院部署只改这张表）';

INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`item_key`,`item_name`,`unit`,`source`,`match_type`,`match_value`,`sort_no`,`remark`)
SELECT 6400001,'VITAL','hr','心率','次/分','无创','CODE','oi_hr',1,'无创心率' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='VITAL' AND `item_key`='hr' AND `match_value`='oi_hr');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`item_key`,`item_name`,`unit`,`source`,`match_type`,`match_value`,`sort_no`,`remark`)
SELECT 6400002,'VITAL','rr','呼吸频率','次/分','无创','CODE','oi_hxpl',2,'监护仪呼吸频率' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='VITAL' AND `item_key`='rr' AND `match_value`='oi_hxpl');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`item_key`,`item_name`,`unit`,`source`,`match_type`,`match_value`,`sort_no`,`remark`)
SELECT 6400003,'VITAL','spo2','SpO₂','%','无创','CODE','oi_spo2',3,'脉搏血氧饱和度' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='VITAL' AND `item_key`='spo2' AND `match_value`='oi_spo2');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`item_key`,`item_name`,`unit`,`source`,`match_type`,`match_value`,`sort_no`,`remark`)
SELECT 6400004,'VITAL','sbp','收缩压','mmHg','无创','CODE','oi_wcssy',4,'无创收缩压' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='VITAL' AND `item_key`='sbp' AND `match_value`='oi_wcssy');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`item_key`,`item_name`,`unit`,`source`,`match_type`,`match_value`,`sort_no`,`remark`)
SELECT 6400005,'VITAL','dbp','舒张压','mmHg','无创','CODE','oi_wcszy',5,'无创舒张压' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='VITAL' AND `item_key`='dbp' AND `match_value`='oi_wcszy');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`item_key`,`item_name`,`unit`,`source`,`match_type`,`match_value`,`sort_no`,`remark`)
SELECT 6400006,'VITAL','map','平均压','mmHg','无创','CODE','oi_pjy',6,'无创平均压' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='VITAL' AND `item_key`='map' AND `match_value`='oi_pjy');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`item_key`,`item_name`,`unit`,`source`,`match_type`,`match_value`,`sort_no`,`remark`)
SELECT 6400007,'VITAL','temp','体温','℃','未标明','CODE','oi_tiwen',7,'体温' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='VITAL' AND `item_key`='temp' AND `match_value`='oi_tiwen');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`item_key`,`item_name`,`unit`,`source`,`match_type`,`match_value`,`sort_no`,`remark`)
SELECT 6400008,'VITAL','sbp_inv','收缩压（有创）','mmHg','有创','CODE','oi_ycssy',8,'有创收缩压' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='VITAL' AND `item_key`='sbp_inv' AND `match_value`='oi_ycssy');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`item_key`,`item_name`,`unit`,`source`,`match_type`,`match_value`,`sort_no`,`remark`)
SELECT 6400009,'VITAL','dbp_inv','舒张压（有创）','mmHg','有创','CODE','oi_ycszy',9,'有创舒张压' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='VITAL' AND `item_key`='dbp_inv' AND `match_value`='oi_ycszy');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`item_key`,`item_name`,`unit`,`source`,`match_type`,`match_value`,`sort_no`,`remark`)
SELECT 6400010,'VITAL','map_inv','平均压（有创）','mmHg','有创','CODE','oi_ycpjy',10,'有创平均压' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='VITAL' AND `item_key`='map_inv' AND `match_value`='oi_ycpjy');

INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`item_key`,`item_name`,`unit`,`match_type`,`match_value`,`sort_no`,`remark`)
SELECT 6400011,'VENT','vt','呼末潮气量','mL','CODE','oi_呼末潮气量',1,'潮气量合规判读用' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='VENT' AND `item_key`='vt' AND `match_value`='oi_呼末潮气量');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`item_key`,`item_name`,`unit`,`match_type`,`match_value`,`sort_no`,`remark`)
SELECT 6400012,'VENT','peep','PEEP','cmH2O','CODE','oi_peep',2,'呼气末正压' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='VENT' AND `item_key`='peep' AND `match_value`='oi_peep');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`item_key`,`item_name`,`unit`,`match_type`,`match_value`,`sort_no`,`remark`)
SELECT 6400013,'VENT','fio2','FiO₂','%','CODE','oi_FiO2(设置值)',3,'设置值' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='VENT' AND `item_key`='fio2' AND `match_value`='oi_FiO2(设置值)');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`item_key`,`item_name`,`unit`,`match_type`,`match_value`,`sort_no`,`remark`)
SELECT 6400014,'VENT','rr_set','呼吸频率（设置值）','次/分','CODE','oi_呼吸频率(设置值)',4,'设置值' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='VENT' AND `item_key`='rr_set' AND `match_value`='oi_呼吸频率(设置值)');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`item_key`,`item_name`,`unit`,`match_type`,`match_value`,`sort_no`,`remark`)
SELECT 6400015,'VENT','mvet','呼末分钟通气量','L/min','CODE','oi_呼末分钟通气量',5,'分钟通气量' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='VENT' AND `item_key`='mvet' AND `match_value`='oi_呼末分钟通气量');

INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`item_key`,`item_name`,`unit`,`match_type`,`match_value`,`sort_no`,`remark`)
SELECT 6400016,'IO','urine','尿量','mL','CODE','ii_nl',1,'AKI 判定用 6 小时尿量' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='IO' AND `item_key`='urine' AND `match_value`='ii_nl');

INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400017,'LAB','LIVER','肝细胞损伤','alt','ALT 丙氨酸氨基转移酶','NAME_LIKE','丙氨酸氨基转移酶',NULL,1,'' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='alt' AND `match_value`='丙氨酸氨基转移酶');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400018,'LAB','LIVER','肝细胞损伤','ast','AST 天冬氨酸氨基转氨酶','NAME_LIKE','天冬氨酸氨基转氨酶',NULL,2,'' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='ast' AND `match_value`='天冬氨酸氨基转氨酶');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400019,'LAB','LIVER','胆红素','tbil','TBIL 总胆红素','NAME_LIKE','总胆红素',NULL,3,'' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='tbil' AND `match_value`='总胆红素');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400020,'LAB','LIVER','胆红素','dbil','DBIL 直接胆红素','NAME_LIKE','直接胆红素',NULL,4,'' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='dbil' AND `match_value`='直接胆红素');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400021,'LAB','LIVER','胆红素','ibil','IBIL 间接胆红素','NAME_LIKE','间接胆红素',NULL,5,'' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='ibil' AND `match_value`='间接胆红素');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400022,'LAB','LIVER','合成功能','alb','ALB 白蛋白','NAME_LIKE','白蛋白',NULL,6,'' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='alb' AND `match_value`='白蛋白');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400023,'LAB','LIVER','胆道 / 酶学','alp','ALP 碱性磷酸酶','NAME_LIKE','碱性磷酸酶',NULL,7,'' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='alp' AND `match_value`='碱性磷酸酶');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400024,'LAB','LIVER','胆道 / 酶学','ggt','GGT γ-谷氨酰转肽酶','NAME_LIKE','谷氨酰转肽酶',NULL,8,'' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='ggt' AND `match_value`='谷氨酰转肽酶');

INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400025,'LAB','COAG','凝血功能','plt','PLT 血小板计数','NAME_LIKE','血小板计数',NULL,1,'' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='plt' AND `match_value`='血小板计数');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400026,'LAB','COAG','凝血功能','pt','PT 凝血酶原时间','NAME_LIKE','凝血酶原时间',NULL,2,'' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='pt' AND `match_value`='凝血酶原时间');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400027,'LAB','COAG','凝血功能','inr','INR 凝血酶原国际比值','NAME_LIKE','凝血酶原国际比值',NULL,3,'' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='inr' AND `match_value`='凝血酶原国际比值');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400028,'LAB','COAG','凝血功能','aptt','APTT 活化部分凝血活酶','NAME_LIKE','活化部分凝血活酶',NULL,4,'' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='aptt' AND `match_value`='活化部分凝血活酶');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400029,'LAB','COAG','凝血功能','fib','Fib 纤维蛋白原','NAME_LIKE','纤维蛋白原',NULL,5,'' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='fib' AND `match_value`='纤维蛋白原');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400030,'LAB','COAG','凝血功能','ddi','D-二聚体','NAME_LIKE','D-二聚体',NULL,6,'' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='ddi' AND `match_value`='D-二聚体');

INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400031,'LAB','RENAL','肾小球滤过功能','cr','Cr 肌酐','NAME_LIKE','肌酐','尿素/肌酐|尿素／肌酐|清除率|腹透液|透析液|尿',1,'排除比值/清除率/尿肌酐/腹透液' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='cr' AND `match_value`='肌酐');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400032,'LAB','RENAL','肾小球滤过功能','bun','BUN 尿素氮','NAME_LIKE','尿素','尿素/肌酐|尿素／肌酐|呼气|腹透液|透析液|尿尿素|小时尿',2,'不能一刀切排除「尿」' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='bun' AND `match_value`='尿素');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400033,'LAB','RENAL','电解质','k','K⁺ 钾','NAME_LIKE','钾','腹透液|透析液|微量元素|尿',3,'' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='k' AND `match_value`='钾');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400034,'LAB','RENAL','电解质','na','Na⁺ 钠','NAME_LIKE','钠','腹透液|透析液|微量元素|钠肽|BNP|前体|苯妥英钠|药物浓度|尿',4,'排除脑钠肽/钠尿肽' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='na' AND `match_value`='钠');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400035,'LAB','RENAL','电解质','cl','Cl⁻ 氯','NAME_LIKE','氯','腹透液|透析液|微量元素|氯化物|氯氮平|氯胺酮|尿',5,'排除含氯药物' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='cl' AND `match_value`='氯');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400036,'LAB','RENAL','电解质','ca','Ca²⁺ 钙','NAME_LIKE','钙','肌钙蛋白|降钙素|钙卫蛋白|骨钙素|微量元素|结晶|尿',6,'排除肌钙蛋白/降钙素原' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='ca' AND `match_value`='钙');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400037,'LAB','RENAL','电解质','mg','Mg²⁺ 镁','NAME_LIKE','镁','微量元素|结晶|磷酸铵镁|尿',7,'' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='mg' AND `match_value`='镁');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400038,'LAB','RENAL','电解质','p','P 磷','NAME_LIKE','磷','微量元素|磷酸|结晶|磷脂|尿',8,'含「磷酸」的都是别的项目' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='p' AND `match_value`='磷');

INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400039,'LAB','INFECTION','感染指标','wbc','WBC 白细胞','NAME_LIKE','白细胞','尿|脊液|胸水|腹水|关节液|精',1,'只取血常规口径' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='wbc' AND `match_value`='白细胞');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400040,'LAB','INFECTION','感染指标','pct','PCT 降钙素原','NAME_LIKE','降钙素原',NULL,2,'' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='pct' AND `match_value`='降钙素原');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400041,'LAB','INFECTION','感染指标','crp','CRP C反应蛋白','NAME_LIKE','C反应蛋白','超敏',3,'排除超敏 CRP' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='crp' AND `match_value`='C反应蛋白');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400042,'LAB','INFECTION','感染指标','il6','IL-6 白介素6','NAME_LIKE','白介素6',NULL,4,'' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='LAB' AND `item_key`='il6' AND `match_value`='白介素6');

-- ---------------------------------------------------------------------
-- 术语字典（domain=DICT）：LIS 报告名 / 标本名 / 项目名 / 观察项名
-- 这些是检验与护理系统实施时配的「名称」，不是本项目定义的业务键，换院就可能换一套写法。
-- culture_item 一行两个值（培养、药敏），与改造前 `LIKE '%培养%' OR LIKE '%药敏%'` 等价。
-- ---------------------------------------------------------------------
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400050,'DICT',NULL,NULL,'gas_sample','动脉血标本','NAME_EXACT','动脉血',NULL,1,'lis_short_name，ARDS/APACHE II 血气取数' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='DICT' AND `item_key`='gas_sample');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400051,'DICT',NULL,NULL,'gas_report','血气分析报告','NAME_LIKE','血气分析',NULL,2,'lis_name，如「动脉血气分析」也能命中' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='DICT' AND `item_key`='gas_report');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400052,'DICT',NULL,NULL,'oxygen_index','氧合指数','NAME_EXACT','氧合指数',NULL,3,'lis_item_name，ARDS 分级与 SOFA 呼吸项' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='DICT' AND `item_key`='oxygen_index');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400053,'DICT',NULL,NULL,'culture_item','培养/药敏明细','NAME_LIKE','培养,药敏',NULL,4,'lis_item_name，感染判断与抗菌药决策' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='DICT' AND `item_key`='culture_item');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400054,'DICT',NULL,NULL,'culture_report','培养报告名','NAME_LIKE','培养',NULL,5,'lis_name，只区分「是否送检」' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='DICT' AND `item_key`='culture_report');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400055,'DICT',NULL,NULL,'temp_item','体温观察项','NAME_LIKE','体温',NULL,6,'item_name，排除词「来源」写在 SQL 里' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='DICT' AND `item_key`='temp_item');

-- ---------------------------------------------------------------------
-- 药品名（domain=DRUG）：医嘱/执行单里的药名关键词，一律按「名称包含」匹配
-- 四个升压药键看起来重复，其实是四处口径不同的历史实现，合并会改变已有指标结果：
--   vasopressor         在用/交班/驾驶舱打标记（8 项）
--   vasopressor_bundle  脓毒症集束化「3h 内使用升压药」判定（6 项）
--   vasopressor_exec    泵速执行单 → SOFA 循环分剂量还原（4 项）
-- ---------------------------------------------------------------------
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400060,'DRUG',NULL,NULL,'vasopressor','血管活性药（在用）','NAME_LIKE','去甲肾上腺素,多巴胺,肾上腺素,间羟胺,多巴酚丁胺,血管加压素,垂体后叶,特利加压素',NULL,1,'影响：交班、驾驶舱、工作台升压药标记' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='DRUG' AND `item_key`='vasopressor');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400061,'DRUG',NULL,NULL,'vasopressor_bundle','升压药（脓毒症集束化）','NAME_LIKE','去甲肾上腺素,多巴胺,肾上腺素,间羟胺,多巴酚丁胺,血管升压',NULL,2,'勿与 vasopressor 合并：集束化达标率按这套口径算' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='DRUG' AND `item_key`='vasopressor_bundle');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400062,'DRUG',NULL,NULL,'vasopressor_exec','升压药（泵速执行单）','NAME_LIKE','去甲肾上腺素,多巴胺,肾上腺素,多巴酚丁胺',NULL,3,'影响：SOFA 循环分剂量还原、血管活性药页' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='DRUG' AND `item_key`='vasopressor_exec');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400063,'DRUG',NULL,NULL,'opioid','阿片类镇痛药','NAME_LIKE','芬太尼,吗啡,地佐辛,布托啡诺,纳布啡,氢吗啡酮',NULL,4,'「芬太尼」已覆盖瑞芬太尼/舒芬太尼' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='DRUG' AND `item_key`='opioid');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400064,'DRUG',NULL,NULL,'solvent_exclude','溶媒排除词','NAME_LIKE','氯化钠,葡萄糖,林格,灭菌注射用水,木糖醇,转化糖,果糖,甘油果糖',NULL,5,'抗菌药医嘱里要把这些剔掉（排除匹配）' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='DRUG' AND `item_key`='solvent_exclude');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400065,'DRUG',NULL,NULL,'fluid','晶体液','NAME_LIKE','氯化钠,葡萄糖,乳酸钠林格,林格,复方氯化钠,灭菌注射用水',NULL,6,'脓毒症 30ml/kg 补液统计' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='DRUG' AND `item_key`='fluid');

-- ---------------------------------------------------------------------
-- 诊断名（domain=DIAG）：patient_info_diagnosis.diag_name 关键词
-- ---------------------------------------------------------------------
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400070,'DIAG',NULL,NULL,'infection','感染相关诊断','NAME_LIKE','脓毒,感染,肺炎,腹腔,腹膜炎,血流,菌血症,尿路,泌尿,真菌',NULL,1,'影响：疑似感染列表的「诊断命中」判定' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='DIAG' AND `item_key`='infection');
INSERT INTO `zing_doctor_db_prod`.`config_data_item`
    (`id`,`domain`,`group_code`,`group_name`,`item_key`,`item_name`,`match_type`,`match_value`,`exclude_value`,`sort_no`,`remark`)
SELECT 6400071,'DIAG',NULL,NULL,'ards','ARDS 诊断','NAME_LIKE','急性呼吸窘迫综合征,ARDS',NULL,2,'影响：ARDS 患者名单与全院筛查' FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM `zing_doctor_db_prod`.`config_data_item` WHERE `domain`='DIAG' AND `item_key`='ards');
