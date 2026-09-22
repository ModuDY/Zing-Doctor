-- ============================================================
-- MySQL 8.x 版本（由达梦 DM8 脚本自动转换 + 人工校验）
-- 主键由应用雪花算法生成（MyBatis-Plus ASSIGN_ID），显式插入时以插入值为准；
-- 仅当 INSERT 省略 id 时由 AUTO_INCREMENT 兜底（对应达梦原有的 SEQ.NEXTVAL 默认值）
-- 执行：mysql -uroot -p < 本文件（需先执行 00b_idempotent_helpers.sql）
-- ============================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

-- =====================================================================

-- 医生决策系统 - 初始化数据（达梦 DM8）

-- 执行方式同 01_schema.sql（先建表，再执行本脚本，SYSDBA 执行）：

--   disql SYSDBA/Sa_20250815@100.120.1.102:14236

--   SQL> start /opt/zing-doctor/sql/02_seed.sql

-- =====================================================================

-- 页面注册：第一维度（经验性抗感染治疗决策）两个页面 + 第二维度（PK/PD 剂量优化）

-- 幂等写法：先按 page_code 删除已存在记录，再插入（达梦不支持 MySQL ON DUPLICATE KEY）

DELETE FROM `sys_page_config`
 WHERE `page_code` IN ('abx-patient-list', 'abx-decision', 'abx-pkpd', 'abx-ddd', 'abx-ddd-config', 'abx-mdro', 'abx-mdro-config', 'sepsis-bundle', 'abx-word-config', 'handover-board', 'discharge-stats', 'ards-monitor', 'apache2-overview', 'apache2-score');
INSERT INTO `sys_page_config`
    (`page_code`, `page_name`, `frontend_path`, `remark`, `status`)
VALUES
    ('abx-patient-list', '疑似感染患者列表', '/page/abx-patient-list',
     '第一维度：疑似感染/脓毒症患者总览，ICU 系统可外链打开', 1),
    ('abx-decision', '经验性抗感染治疗决策', '/page/abx-decision',
     '第一维度：单患者经验性抗感染方案决策，外链携带 patientId 打开', 1),
    ('abx-pkpd', 'PK/PD 抗菌药物剂量优化', '/page/abx-pkpd',
     '第二维度：基于肾功能/体重/低蛋白血症/CRRT 的抗菌药物剂量个体化优化，外链携带 patientId 打开', 1),
    ('abx-ddd', '抗菌药物使用强度分析', '/page/abx-ddd',
     '第三维度：全院/科室抗菌药物使用率、使用强度（DDDs）、药品排名、时间趋势、患者明细', 1),
    ('abx-ddd-config', 'DDD值配置管理', '/page/abx-ddd-config',
     '第三维度：抗菌药物DDD值知识库后台配置，支持新增/修改/停用', 1),
    ('abx-mdro', '细菌培养检出监测', '/page/abx-mdro',
     '第四维度：细菌培养检出监测与院感防控，菌株排名、标本类型分布、时间趋势、患者明细、高风险细菌预警', 1),
    ('abx-mdro-config', '细菌分类配置管理', '/page/abx-mdro-config',
     '第四维度：细菌分类（革兰阳性/阴性/真菌）、高风险细菌列表后台配置，支持新增/修改/停用', 1),
    ('sepsis-bundle', '脓毒症休克集束化治疗', '/page/sepsis-bundle',
     '脓毒症/感染性休克患者1H/3H/6H集束化治疗完成情况自动判断与记录，外链携带 patientId 打开', 1),
    ('abx-word-config', '抗菌药物识别词库配置', '/page/abx-word-config',
     '脓毒症集束化：广谱抗菌药白名单/非抗菌药黑名单后台配置，支持新增/修改/停用', 1),
    ('handover-board', '医生交班览表', '/page/handover-board',
     '第五维度：医生交班览表，按上一完整全天班次汇总在科患者病情变化/生命体征/器官支持/出入量/检验异常，支持病情变化手工交班', 1),
    ('discharge-stats', '患者出科统计', '/page/discharge-stats',
     '患者出科统计：按出科时间范围和科室查询已出科患者列表，支持导出Excel（CSV）', 1),
    ('ards-monitor', 'ARDS监测', '/page/ards-monitor',
     'ARDS监测：ARDS患者识别、柏林定义分级（轻/中/重）、肺保护性通气依从性监测、呼吸机参数与氧合指数趋势，外链携带departCode控制科室权限', 1),
    ('apache2-overview', 'APACHE II评分总览', '/page/apache2-overview',
     'APACHE II评分主任视角：科室评分统计、评分分布、各时机平均分对比、患者评分列表与详情，外链携带departCode控制科室权限', 1),
    ('apache2-score', 'APACHE II评分评估', '/page/apache2-score',
     'APACHE II评分医生视角：单患者评分操作，支持自动取数/手动录入、多次评分、GCS评分、死亡率预测，外链携带inHospitalNo打开', 1);
-- ---------------------------------------------------------------------

-- 抗菌药物 DDD 值初始数据（按 WHO ATC/DDD 最新版本）

-- 幂等写法：先清空，再批量插入

-- ---------------------------------------------------------------------

-- [v23.1.13] 幂等化：不再清空 config_ddd（避免重复部署重置后台配置），改为下方 INSERT ... WHERE NOT EXISTS 补缺失
-- 青霉素类

INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '哌拉西林他唑巴坦', 'J01CR03', 14.0, 'g', '注射', '限制', '青霉素类', '哌拉西林,他唑巴坦,特治星,邦达', '按哌拉西林计'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '哌拉西林他唑巴坦' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '哌拉西林', 'J01CA01', 14.0, 'g', '注射', '非限制', '青霉素类', '哌拉西林', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '哌拉西林' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '阿莫西林克拉维酸', 'J01CR02', 3.0, 'g', '注射', '非限制', '青霉素类', '阿莫西林,克拉维酸,安灭菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '阿莫西林克拉维酸' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '氨苄西林', 'J01CA01', 2.0, 'g', '注射', '非限制', '青霉素类', '氨苄西林', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '氨苄西林' AND `route` = '注射'
);
-- 头孢类

INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '头孢唑林', 'J01DB04', 3.0, 'g', '注射', '非限制', '头孢一代', '头孢唑林,五水头孢唑林', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '头孢唑林' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '头孢呋辛', 'J01DC02', 3.0, 'g', '注射', '非限制', '头孢二代', '头孢呋辛,西力欣,明可欣', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '头孢呋辛' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '头孢曲松', 'J01DD04', 2.0, 'g', '注射', '限制', '头孢三代', '头孢曲松,罗氏芬,菌必治', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '头孢曲松' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '头孢噻肟', 'J01DD01', 4.0, 'g', '注射', '限制', '头孢三代', '头孢噻肟,凯福隆', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '头孢噻肟' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '头孢他啶', 'J01DD02', 4.0, 'g', '注射', '限制', '头孢三代', '头孢他啶,复达欣,凯复定', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '头孢他啶' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '头孢吡肟', 'J01DE01', 2.0, 'g', '注射', '限制', '头孢四代', '头孢吡肟,马斯平', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '头孢吡肟' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '头孢哌酮舒巴坦', 'J01CR62', 4.0, 'g', '注射', '限制', '头孢三代', '头孢哌酮,舒巴坦,舒普深,铃兰欣', '按头孢哌酮计'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '头孢哌酮舒巴坦' AND `route` = '注射'
);
-- 碳青霉烯类（特殊使用级）

INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '亚胺培南西司他丁', 'J01DH51', 2.0, 'g', '注射', '特殊', '碳青霉烯类', '亚胺培南,西司他丁,泰能,齐佩能', '按亚胺培南计'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '亚胺培南西司他丁' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '美罗培南', 'J01DH02', 2.0, 'g', '注射', '特殊', '碳青霉烯类', '美罗培南,美平,罗南,倍能', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '美罗培南' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '比阿培南', 'J01DH07', 1.2, 'g', '注射', '特殊', '碳青霉烯类', '比阿培南,天册,安信', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '比阿培南' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '厄他培南', 'J01DH03', 1.0, 'g', '注射', '特殊', '碳青霉烯类', '厄他培南,怡万之', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '厄他培南' AND `route` = '注射'
);
-- 单环β内酰胺

INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '氨曲南', 'J01DF01', 4.0, 'g', '注射', '限制', '单环β内酰胺类', '氨曲南,君刻单', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '氨曲南' AND `route` = '注射'
);
-- 糖肽类（特殊使用级）

INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '万古霉素', 'J01XA01', 2.0, 'g', '注射', '特殊', '糖肽类', '万古霉素,来可信,稳可信,方刻林', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '万古霉素' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '去甲万古霉素', 'J01XA01', 1.6, 'g', '注射', '特殊', '糖肽类', '去甲万古霉素,万迅', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '去甲万古霉素' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '替考拉宁', 'J01XA02', 0.4, 'g', '注射', '特殊', '糖肽类', '替考拉宁,他格适,加立信', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '替考拉宁' AND `route` = '注射'
);
-- 恶唑烷酮类（特殊使用级）

INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '利奈唑胺', 'J01XX08', 1.2, 'g', '注射', '特殊', '恶唑烷酮类', '利奈唑胺,斯沃,易瑞达,菲康宁', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '利奈唑胺' AND `route` = '注射'
);
-- 氨基糖苷类

INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '阿米卡星', 'J01GB06', 1.0, 'g', '注射', '限制', '氨基糖苷类', '阿米卡星,丁胺卡那', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '阿米卡星' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '庆大霉素', 'J01GB03', 0.24, 'g', '注射', '限制', '氨基糖苷类', '庆大霉素', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '庆大霉素' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '妥布霉素', 'J01GB01', 0.24, 'g', '注射', '限制', '氨基糖苷类', '妥布霉素', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '妥布霉素' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '依替米星', 'J01GB19', 0.3, 'g', '注射', '限制', '氨基糖苷类', '依替米星,爱大,悉能', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '依替米星' AND `route` = '注射'
);
-- 喹诺酮类

INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '左氧氟沙星', 'J01MA12', 0.5, 'g', '注射', '限制', '喹诺酮类', '左氧氟沙星,可乐必妥,左克,来立信', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '左氧氟沙星' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '莫西沙星', 'J01MA14', 0.4, 'g', '注射', '限制', '喹诺酮类', '莫西沙星,拜复乐', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '莫西沙星' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '环丙沙星', 'J01MA02', 0.4, 'g', '注射', '限制', '喹诺酮类', '环丙沙星,西普乐', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '环丙沙星' AND `route` = '注射'
);
-- 大环内酯类

INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '阿奇霉素', 'J01FA10', 0.5, 'g', '注射', '非限制', '大环内酯类', '阿奇霉素,希舒美,其仙,维宏', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '阿奇霉素' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '红霉素', 'J01FA01', 2.0, 'g', '注射', '非限制', '大环内酯类', '红霉素', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '红霉素' AND `route` = '注射'
);
-- 四环素类

INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '替加环素', 'J01AA12', 0.1, 'g', '注射', '特殊', '四环素类', '替加环素,泰阁,泽坦', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '替加环素' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '多西环素', 'J01AA02', 0.1, 'g', '口服', '非限制', '四环素类', '多西环素,强力霉素', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '多西环素' AND `route` = '口服'
);
-- 林可酰胺类

INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '克林霉素', 'J01FF01', 1.2, 'g', '注射', '非限制', '林可酰胺类', '克林霉素,氯林可霉素,特丽仙', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '克林霉素' AND `route` = '注射'
);
-- 抗真菌药

INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '氟康唑', 'J02AC01', 0.2, 'g', '注射', '限制', '三唑类抗真菌', '氟康唑,大扶康,依利康', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '氟康唑' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '伏立康唑', 'J02AC03', 0.4, 'g', '注射', '特殊', '三唑类抗真菌', '伏立康唑,威凡,汇德立康,丽福康', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '伏立康唑' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '伊曲康唑', 'J02AC02', 0.2, 'g', '注射', '限制', '三唑类抗真菌', '伊曲康唑,斯皮仁诺', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '伊曲康唑' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '卡泊芬净', 'J02AX04', 0.05, 'g', '注射', '特殊', '棘白菌素类', '卡泊芬净,科赛斯,可赛斯', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '卡泊芬净' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '米卡芬净', 'J02AX05', 0.1, 'g', '注射', '特殊', '棘白菌素类', '米卡芬净,米开民', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '米卡芬净' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '两性霉素B', 'J02AA01', 0.035, 'g', '注射', '特殊', '多烯类抗真菌', '两性霉素B,安浮特克,锋克松', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '两性霉素B' AND `route` = '注射'
);
-- 多粘菌素（特殊使用级）

INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '多粘菌素B', 'J01XB02', 1.5, 'MU', '注射', '特殊', '多粘菌素类', '多粘菌素B,多粘菌素', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '多粘菌素B' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '粘菌素', 'J01XB01', 3.0, 'MU', '注射', '特殊', '多粘菌素类', '粘菌素,多粘菌素E,可利迈仙', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '粘菌素' AND `route` = '注射'
);
-- ---------------------------------------------------------------------

-- 第四维度：细菌培养监测配置初始数据

-- 包含细菌分类配置（革兰阳性/阴性/真菌）和高风险细菌列表

-- 幂等写法：先清空，再批量插入

-- ---------------------------------------------------------------------

-- [v23.1.13] 幂等化：不再清空 config_mdro（避免重复部署重置后台配置），改为下方 INSERT ... WHERE NOT EXISTS 补缺失
-- 革兰阴性菌（config_type=bacteria_class, bacteria_class=gram_negative）

INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '鲍曼不动杆菌', 'gram_negative', 1, '鲍曼不动杆菌,不动杆菌', 'ICU常见MDRO风险菌'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '鲍曼不动杆菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '铜绿假单胞菌', 'gram_negative', 1, '铜绿假单胞菌,铜绿,假单胞菌', 'ICU常见MDRO风险菌'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '铜绿假单胞菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '肺炎克雷伯菌', 'gram_negative', 1, '肺炎克雷伯菌,克雷伯菌,产酸克雷伯菌', 'ICU常见MDRO风险菌'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '肺炎克雷伯菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '大肠埃希菌', 'gram_negative', 0, '大肠埃希菌,大肠杆菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '大肠埃希菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '嗜麦芽窄食单胞菌', 'gram_negative', 1, '嗜麦芽窄食单胞菌,窄食单胞菌,嗜麦芽', 'ICU常见MDRO风险菌'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '嗜麦芽窄食单胞菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '阴沟肠杆菌', 'gram_negative', 0, '阴沟肠杆菌,肠杆菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '阴沟肠杆菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '产气肠杆菌', 'gram_negative', 0, '产气肠杆菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '产气肠杆菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '粘质沙雷菌', 'gram_negative', 0, '粘质沙雷菌,沙雷菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '粘质沙雷菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '变形杆菌', 'gram_negative', 0, '变形杆菌,奇异变形杆菌,普通变形杆菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '变形杆菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '摩根菌', 'gram_negative', 0, '摩根菌,摩氏摩根菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '摩根菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '普罗威登斯菌', 'gram_negative', 0, '普罗威登斯菌,雷氏普罗威登斯菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '普罗威登斯菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '流感嗜血杆菌', 'gram_negative', 0, '流感嗜血杆菌,嗜血杆菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '流感嗜血杆菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '卡他莫拉菌', 'gram_negative', 0, '卡他莫拉菌,莫拉菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '卡他莫拉菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '洋葱伯克霍尔德菌', 'gram_negative', 0, '洋葱伯克霍尔德菌,伯克霍尔德菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '洋葱伯克霍尔德菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '脑膜炎败血伊丽莎白菌', 'gram_negative', 0, '脑膜炎败血伊丽莎白菌,伊丽莎白菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '脑膜炎败血伊丽莎白菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '成团泛菌', 'gram_negative', 0, '成团泛菌,泛菌,聚团泛菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '成团泛菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '革兰阴性杆菌', 'gram_negative', 0, '革兰阴性杆菌,革兰阴性菌,G-杆菌', '培养结果只写分类名称'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '革兰阴性杆菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '不动杆菌属', 'gram_negative', 0, '不动杆菌属,醋酸钙不动杆菌,洛菲不动杆菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '不动杆菌属'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '克雷伯菌属', 'gram_negative', 0, '克雷伯菌属,催产克雷伯菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '克雷伯菌属'
);
-- 革兰阳性菌（config_type=bacteria_class, bacteria_class=gram_positive）

INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '金黄色葡萄球菌', 'gram_positive', 1, '金黄色葡萄球菌,金葡菌,葡萄球菌', 'ICU常见MDRO风险菌(MRSA)'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '金黄色葡萄球菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '表皮葡萄球菌', 'gram_positive', 0, '表皮葡萄球菌,表葡菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '表皮葡萄球菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '溶血葡萄球菌', 'gram_positive', 0, '溶血葡萄球菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '溶血葡萄球菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '屎肠球菌', 'gram_positive', 1, '屎肠球菌,肠球菌', 'ICU常见MDRO风险菌(VRE)'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '屎肠球菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '粪肠球菌', 'gram_positive', 1, '粪肠球菌,肠球菌', 'ICU常见MDRO风险菌(VRE)'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '粪肠球菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '肺炎链球菌', 'gram_positive', 0, '肺炎链球菌,链球菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '肺炎链球菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '化脓性链球菌', 'gram_positive', 0, '化脓性链球菌,A组链球菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '化脓性链球菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '草绿色链球菌', 'gram_positive', 0, '草绿色链球菌,缓症链球菌,口腔链球菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '草绿色链球菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '无乳链球菌', 'gram_positive', 0, '无乳链球菌,B组链球菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '无乳链球菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '棒状杆菌', 'gram_positive', 0, '棒状杆菌,白喉棒状杆菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '棒状杆菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '李斯特菌', 'gram_positive', 0, '李斯特菌,单核细胞增生李斯特菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '李斯特菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '诺卡菌', 'gram_positive', 0, '诺卡菌,星形诺卡菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '诺卡菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '芽孢杆菌', 'gram_positive', 0, '芽孢杆菌,枯草芽孢杆菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '芽孢杆菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '革兰阳性球菌', 'gram_positive', 0, '革兰阳性球菌,革兰阳性菌,G+球菌', '培养结果只写分类名称'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '革兰阳性球菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '纹带棒杆菌', 'gram_positive', 0, '纹带棒杆菌,棒杆菌,白喉棒状杆菌,假白喉棒状杆菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '纹带棒杆菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '微球菌', 'gram_positive', 0, '微球菌,藤黄微球菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '微球菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '消化链球菌', 'gram_positive', 0, '消化链球菌,厌氧消化链球菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '消化链球菌'
);
-- 真菌（config_type=bacteria_class, bacteria_class=fungi）

INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '白色念珠菌', 'fungi', 0, '白色念珠菌,白假丝酵母菌,念珠菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '白色念珠菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '光滑念珠菌', 'fungi', 0, '光滑念珠菌,光滑假丝酵母菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '光滑念珠菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '热带念珠菌', 'fungi', 0, '热带念珠菌,热带假丝酵母菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '热带念珠菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '近平滑念珠菌', 'fungi', 0, '近平滑念珠菌,近平滑假丝酵母菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '近平滑念珠菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '克柔念珠菌', 'fungi', 0, '克柔念珠菌,克柔假丝酵母菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '克柔念珠菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '曲霉菌', 'fungi', 0, '曲霉菌,烟曲霉,黄曲霉,黑曲霉,曲霉', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '曲霉菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '隐球菌', 'fungi', 0, '隐球菌,新型隐球菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '隐球菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '毛霉菌', 'fungi', 0, '毛霉菌,根霉菌,犁头霉', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '毛霉菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'bacteria_class', '肺孢子菌', 'fungi', 0, '肺孢子菌,耶氏肺孢子菌,卡氏肺孢子菌', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'bacteria_class' AND `bacteria_name` = '肺孢子菌'
);
-- 高风险细菌单独标记（config_type=high_risk，用于快速查询高风险菌列表）

INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'high_risk', '鲍曼不动杆菌', 'gram_negative', 1, '鲍曼不动杆菌,不动杆菌', 'CRAB高风险'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'high_risk' AND `bacteria_name` = '鲍曼不动杆菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'high_risk', '铜绿假单胞菌', 'gram_negative', 1, '铜绿假单胞菌,铜绿,假单胞菌', 'CRPA高风险'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'high_risk' AND `bacteria_name` = '铜绿假单胞菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'high_risk', '肺炎克雷伯菌', 'gram_negative', 1, '肺炎克雷伯菌,克雷伯菌', 'CRE高风险'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'high_risk' AND `bacteria_name` = '肺炎克雷伯菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'high_risk', '嗜麦芽窄食单胞菌', 'gram_negative', 1, '嗜麦芽窄食单胞菌,窄食单胞菌', '天然耐药风险'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'high_risk' AND `bacteria_name` = '嗜麦芽窄食单胞菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'high_risk', '金黄色葡萄球菌', 'gram_positive', 1, '金黄色葡萄球菌,金葡菌', 'MRSA高风险'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'high_risk' AND `bacteria_name` = '金黄色葡萄球菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'high_risk', '屎肠球菌', 'gram_positive', 1, '屎肠球菌', 'VRE高风险'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'high_risk' AND `bacteria_name` = '屎肠球菌'
);
INSERT INTO `config_mdro`
    (`config_type`, `bacteria_name`, `bacteria_class`, `is_high_risk`, `keywords`, `remark`)
SELECT 'high_risk', '粪肠球菌', 'gram_positive', 1, '粪肠球菌', 'VRE高风险'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_mdro`
    WHERE `config_type` = 'high_risk' AND `bacteria_name` = '粪肠球菌'
);
-- 其他抗菌药

INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '甲硝唑', 'J01XD01', 1.5, 'g', '注射', '非限制', '硝基咪唑类', '甲硝唑,灭滴灵,佳尔纳', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '甲硝唑' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '替硝唑', 'J01XD02', 2.0, 'g', '口服', '非限制', '硝基咪唑类', '替硝唑', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '替硝唑' AND `route` = '口服'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '磷霉素', 'J01XX01', 12.0, 'g', '注射', '非限制', '其他类', '磷霉素,复美欣', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '磷霉素' AND `route` = '注射'
);
INSERT INTO `config_ddd`
    (`drug_name`, `atc_code`, `ddd_value`, `ddd_unit`, `route`, `manage_level`, `drug_class`, `keywords`, `remark`)
SELECT '复方磺胺甲恶唑', 'J01EE01', 4.8, 'g', '口服', '非限制', '磺胺类', '复方磺胺甲恶唑,SMZco,百炎净', ''
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_ddd`
    WHERE `drug_name` = '复方磺胺甲恶唑' AND `route` = '口服'
);
-- ---------------------------------------------------------------------

-- 抗菌药物识别词库初始数据（与内置默认一致，可后台修改）

-- ---------------------------------------------------------------------

-- [v23.1.13] 幂等化：不再清空 config_abx_word（避免重复部署重置后台配置），改为下方 INSERT ... WHERE NOT EXISTS 补缺失
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '哌拉西林', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '哌拉西林'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '头孢哌酮', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '头孢哌酮'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '头孢他啶', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '头孢他啶'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '头孢吡肟', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '头孢吡肟'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '美罗培南', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '美罗培南'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '亚胺培南', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '亚胺培南'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '比阿培南', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '比阿培南'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '厄他培南', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '厄他培南'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '左氧氟沙星', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '左氧氟沙星'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '莫西沙星', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '莫西沙星'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '环丙沙星', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '环丙沙星'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '万古霉素', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '万古霉素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '利奈唑胺', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '利奈唑胺'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '替考拉宁', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '替考拉宁'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '达托霉素', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '达托霉素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '阿米卡星', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '阿米卡星'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '妥布霉素', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '妥布霉素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '庆大霉素', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '庆大霉素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '甲硝唑', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '甲硝唑'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '替硝唑', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '替硝唑'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '氟康唑', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '氟康唑'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '伏立康唑', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '伏立康唑'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '卡泊芬净', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '卡泊芬净'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '米卡芬净', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '米卡芬净'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '两性霉素', '广谱抗菌药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '两性霉素'
);
-- [v23.1.13] 幂等化：不再清空 config_abx_word（避免重复部署重置后台配置），改为下方 INSERT ... WHERE NOT EXISTS 补缺失
-- v23.1.9 追加

-- v23.1.11 追加

INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氯化钾', '电解质/酸碱', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氯化钾'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '碳酸氢钠', '电解质/酸碱', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '碳酸氢钠'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '葡萄糖酸钙', '电解质/酸碱', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '葡萄糖酸钙'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '硫酸镁', '电解质/酸碱', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '硫酸镁'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '磷酸钠', '电解质/酸碱', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '磷酸钠'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '枸橼酸钾', '电解质/酸碱', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '枸橼酸钾'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '乳酸钠', '电解质/酸碱', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '乳酸钠'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '醋酸钠', '电解质/酸碱', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '醋酸钠'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '甘油磷酸钠', '电解质/酸碱', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '甘油磷酸钠'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '门冬氨酸钾', '电解质/酸碱', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '门冬氨酸钾'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '维生素', '维生素', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '维生素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '维C', '维生素', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '维C'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', 'VC', '维生素', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = 'VC'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', 'VB', '维生素', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = 'VB'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '复合维', '维生素', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '复合维'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '脂肪乳', '营养支持', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '脂肪乳'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氨基酸', '营养支持', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氨基酸'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '白蛋白', '营养支持', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '白蛋白'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '肠内营养', '营养支持', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '肠内营养'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '安素', '营养支持', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '安素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '能全力', '营养支持', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '能全力'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '百普力', '营养支持', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '百普力'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '短肽', '营养支持', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '短肽'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '整蛋白', '营养支持', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '整蛋白'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '谷氨酰胺', '营养支持', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '谷氨酰胺'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '丙氨酰', '营养支持', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '丙氨酰'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', 'ω-3', '营养支持', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = 'ω-3'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '欧米伽', '营养支持', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '欧米伽'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '鱼油', '营养支持', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '鱼油'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '奥美拉唑', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '奥美拉唑'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '泮托拉唑', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '泮托拉唑'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '兰索拉唑', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '兰索拉唑'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '雷贝拉唑', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '雷贝拉唑'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '艾司奥美拉唑', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '艾司奥美拉唑'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '埃索美拉唑', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '埃索美拉唑'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '罗沙替丁', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '罗沙替丁'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '法莫替丁', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '法莫替丁'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '西咪替丁', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '西咪替丁'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '雷尼替丁', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '雷尼替丁'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '尼扎替丁', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '尼扎替丁'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '生长抑素', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '生长抑素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '奥曲肽', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '奥曲肽'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '加贝酯', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '加贝酯'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '乌司他丁', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '乌司他丁'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '乳果糖', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '乳果糖'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '开塞露', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '开塞露'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '蒙脱石', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '蒙脱石'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '双歧杆菌', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '双歧杆菌'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '枯草杆菌', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '枯草杆菌'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '地衣芽孢', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '地衣芽孢'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '多潘立酮', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '多潘立酮'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '甲氧氯普胺', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '甲氧氯普胺'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '莫沙必利', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '莫沙必利'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '铝碳酸镁', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '铝碳酸镁'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '碳酸钙', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '碳酸钙'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '复方消化酶', '抑酸/胃肠', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '复方消化酶'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '丙泊酚', '镇静/麻醉', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '丙泊酚'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '咪达唑仑', '镇静/麻醉', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '咪达唑仑'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '依托咪酯', '镇静/麻醉', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '依托咪酯'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '七氟烷', '镇静/麻醉', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '七氟烷'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '异氟烷', '镇静/麻醉', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '异氟烷'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '地氟烷', '镇静/麻醉', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '地氟烷'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '右美托咪定', '镇静/麻醉', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '右美托咪定'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氯胺酮', '镇静/麻醉', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氯胺酮'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '戊巴比妥', '镇静/麻醉', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '戊巴比妥'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '硫喷妥', '镇静/麻醉', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '硫喷妥'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '水合氯醛', '镇静/麻醉', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '水合氯醛'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '苯巴比妥', '镇静/麻醉', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '苯巴比妥'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '瑞芬太尼', '镇痛', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '瑞芬太尼'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '芬太尼', '镇痛', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '芬太尼'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '舒芬太尼', '镇痛', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '舒芬太尼'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '吗啡', '镇痛', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '吗啡'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '布托啡诺', '镇痛', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '布托啡诺'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '地佐辛', '镇痛', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '地佐辛'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '曲马多', '镇痛', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '曲马多'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氢吗啡酮', '镇痛', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氢吗啡酮'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '羟考酮', '镇痛', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '羟考酮'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '纳布啡', '镇痛', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '纳布啡'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '喷他佐辛', '镇痛', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '喷他佐辛'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '美沙酮', '镇痛', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '美沙酮'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '哌替啶', '镇痛', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '哌替啶'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '可待因', '镇痛', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '可待因'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '阿曲库铵', '肌松', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '阿曲库铵'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '维库溴铵', '肌松', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '维库溴铵'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '罗库溴铵', '肌松', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '罗库溴铵'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '泮库溴铵', '肌松', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '泮库溴铵'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '琥珀胆碱', '肌松', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '琥珀胆碱'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '米库溴铵', '肌松', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '米库溴铵'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '肝素', '抗凝/抗血小板/溶栓', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '肝素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '华法林', '抗凝/抗血小板/溶栓', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '华法林'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '利伐沙班', '抗凝/抗血小板/溶栓', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '利伐沙班'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '达比加群', '抗凝/抗血小板/溶栓', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '达比加群'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '阿司匹林', '抗凝/抗血小板/溶栓', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '阿司匹林'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氯吡格雷', '抗凝/抗血小板/溶栓', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氯吡格雷'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '替格瑞洛', '抗凝/抗血小板/溶栓', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '替格瑞洛'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '替罗非班', '抗凝/抗血小板/溶栓', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '替罗非班'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '比伐卢定', '抗凝/抗血小板/溶栓', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '比伐卢定'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '尿激酶', '抗凝/抗血小板/溶栓', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '尿激酶'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '阿替普酶', '抗凝/抗血小板/溶栓', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '阿替普酶'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '链激酶', '抗凝/抗血小板/溶栓', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '链激酶'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '达肝素', '抗凝/抗血小板/溶栓', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '达肝素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '亭扎肝素', '抗凝/抗血小板/溶栓', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '亭扎肝素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '磺达肝癸', '抗凝/抗血小板/溶栓', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '磺达肝癸'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '阿哌沙班', '抗凝/抗血小板/溶栓', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '阿哌沙班'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '替卡格雷', '抗凝/抗血小板/溶栓', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '替卡格雷'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '去甲肾上腺素', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '去甲肾上腺素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '间羟胺', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '间羟胺'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '多巴胺', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '多巴胺'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '多巴酚丁胺', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '多巴酚丁胺'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '肾上腺素', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '肾上腺素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '异丙肾上腺素', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '异丙肾上腺素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '去氧肾上腺素', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '去氧肾上腺素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '垂体后叶', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '垂体后叶'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '血管加压素', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '血管加压素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '特利加压素', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '特利加压素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '硝普钠', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '硝普钠'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '硝酸甘油', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '硝酸甘油'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '硝酸异山梨酯', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '硝酸异山梨酯'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '乌拉地尔', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '乌拉地尔'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '酚妥拉明', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '酚妥拉明'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '艾司洛尔', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '艾司洛尔'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '美托洛尔', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '美托洛尔'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '比索洛尔', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '比索洛尔'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '硝苯地平', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '硝苯地平'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氨氯地平', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氨氯地平'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '非洛地平', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '非洛地平'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '缬沙坦', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '缬沙坦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氯沙坦', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氯沙坦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '厄贝沙坦', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '厄贝沙坦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '贝那普利', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '贝那普利'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '培哚普利', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '培哚普利'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '卡托普利', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '卡托普利'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '依那普利', '血管活性/升压/降压', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '依那普利'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '胺碘酮', '抗心律失常/强心', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '胺碘酮'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '利多卡因', '抗心律失常/强心', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '利多卡因'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '普罗帕酮', '抗心律失常/强心', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '普罗帕酮'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '维拉帕米', '抗心律失常/强心', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '维拉帕米'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '地尔硫卓', '抗心律失常/强心', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '地尔硫卓'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '阿托品', '抗心律失常/强心', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '阿托品'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '去乙酰毛花苷', '抗心律失常/强心', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '去乙酰毛花苷'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '西地兰', '抗心律失常/强心', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '西地兰'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '毒毛花苷', '抗心律失常/强心', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '毒毛花苷'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '洋地黄', '抗心律失常/强心', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '洋地黄'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '多非利特', '抗心律失常/强心', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '多非利特'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '伊布利特', '抗心律失常/强心', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '伊布利特'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '呋塞米', '利尿/脱水', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '呋塞米'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '托拉塞米', '利尿/脱水', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '托拉塞米'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '螺内酯', '利尿/脱水', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '螺内酯'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氢氯噻嗪', '利尿/脱水', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氢氯噻嗪'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '布美他尼', '利尿/脱水', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '布美他尼'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '甘露醇', '利尿/脱水', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '甘露醇'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '乙酰唑胺', '利尿/脱水', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '乙酰唑胺'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '吲达帕胺', '利尿/脱水', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '吲达帕胺'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氨溴索', '化痰平喘', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氨溴索'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '乙酰半胱氨酸', '化痰平喘', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '乙酰半胱氨酸'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '溴己新', '化痰平喘', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '溴己新'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氨茶碱', '化痰平喘', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氨茶碱'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '多索茶碱', '化痰平喘', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '多索茶碱'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '沙丁胺醇', '化痰平喘', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '沙丁胺醇'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '异丙托溴铵', '化痰平喘', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '异丙托溴铵'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '布地奈德', '化痰平喘', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '布地奈德'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '特布他林', '化痰平喘', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '特布他林'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '茶碱', '化痰平喘', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '茶碱'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '泛影葡胺', '造影剂', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '泛影葡胺'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '碘海醇', '造影剂', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '碘海醇'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '碘帕醇', '造影剂', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '碘帕醇'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '碘佛醇', '造影剂', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '碘佛醇'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '碘普罗胺', '造影剂', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '碘普罗胺'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '碘克沙醇', '造影剂', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '碘克沙醇'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '钆', '造影剂', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '钆'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '优维显', '造影剂', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '优维显'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '欧乃派克', '造影剂', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '欧乃派克'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '造影', '造影剂', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '造影'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '地塞米松', '激素', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '地塞米松'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '甲泼尼龙', '激素', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '甲泼尼龙'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氢化可的松', '激素', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氢化可的松'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '泼尼松', '激素', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '泼尼松'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '泼尼松龙', '激素', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '泼尼松龙'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '倍他米松', '激素', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '倍他米松'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '甲强龙', '激素', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '甲强龙'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '强的松', '激素', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '强的松'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '促肾上腺皮质', '激素', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '促肾上腺皮质'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氨甲环酸', '止血/血液制品', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氨甲环酸'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '凝血酶', '止血/血液制品', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '凝血酶'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '维生素K', '止血/血液制品', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '维生素K'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '卡络磺钠', '止血/血液制品', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '卡络磺钠'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '酚磺乙胺', '止血/血液制品', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '酚磺乙胺'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '血凝酶', '止血/血液制品', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '血凝酶'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '纤维蛋白原', '止血/血液制品', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '纤维蛋白原'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '冷沉淀', '止血/血液制品', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '冷沉淀'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '血小板', '止血/血液制品', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '血小板'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '胰岛素', '内分泌/代谢', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '胰岛素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '格列', '内分泌/代谢', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '格列'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '二甲双胍', '内分泌/代谢', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '二甲双胍'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '生长激素', '内分泌/代谢', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '生长激素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '甲状腺', '内分泌/代谢', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '甲状腺'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '左甲状腺', '内分泌/代谢', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '左甲状腺'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '阿卡波糖', '内分泌/代谢', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '阿卡波糖'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '西格列汀', '内分泌/代谢', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '西格列汀'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '达格列净', '内分泌/代谢', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '达格列净'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '恩格列净', '内分泌/代谢', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '恩格列净'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '利拉鲁肽', '内分泌/代谢', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '利拉鲁肽'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '度拉糖肽', '内分泌/代谢', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '度拉糖肽'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '艾塞那肽', '内分泌/代谢', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '艾塞那肽'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '阿托伐他汀', '降脂', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '阿托伐他汀'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '瑞舒伐他汀', '降脂', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '瑞舒伐他汀'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '辛伐他汀', '降脂', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '辛伐他汀'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '普伐他汀', '降脂', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '普伐他汀'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氟伐他汀', '降脂', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氟伐他汀'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '匹伐他汀', '降脂', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '匹伐他汀'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '依折麦布', '降脂', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '依折麦布'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '非诺贝特', '降脂', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '非诺贝特'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '吉非罗齐', '降脂', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '吉非罗齐'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '普罗布考', '降脂', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '普罗布考'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '西替利嗪', '抗组胺/抗过敏', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '西替利嗪'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氯雷他定', '抗组胺/抗过敏', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氯雷他定'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '异丙嗪', '抗组胺/抗过敏', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '异丙嗪'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '依巴斯汀', '抗组胺/抗过敏', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '依巴斯汀'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '苯海拉明', '抗组胺/抗过敏', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '苯海拉明'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氯苯那敏', '抗组胺/抗过敏', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氯苯那敏'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '非索非那定', '抗组胺/抗过敏', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '非索非那定'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '地氯雷他定', '抗组胺/抗过敏', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '地氯雷他定'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '左西替利嗪', '抗组胺/抗过敏', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '左西替利嗪'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '酮替芬', '抗组胺/抗过敏', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '酮替芬'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '赛庚啶', '抗组胺/抗过敏', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '赛庚啶'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氯马斯汀', '抗组胺/抗过敏', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氯马斯汀'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '扑尔敏', '抗组胺/抗过敏', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '扑尔敏'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '息斯敏', '抗组胺/抗过敏', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '息斯敏'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '明胶', '血容量扩张（人工胶体）', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '明胶'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '羟乙基淀粉', '血容量扩张（人工胶体）', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '羟乙基淀粉'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '聚明胶肽', '血容量扩张（人工胶体）', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '聚明胶肽'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '右旋糖酐', '血容量扩张（人工胶体）', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '右旋糖酐'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '佳乐施', '血容量扩张（人工胶体）', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '佳乐施'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '血定安', '血容量扩张（人工胶体）', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '血定安'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '万汶', '血容量扩张（人工胶体）', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '万汶'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '新斯的明', '胆碱酯酶抑制剂/神经肌肉接头药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '新斯的明'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '溴吡斯的明', '胆碱酯酶抑制剂/神经肌肉接头药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '溴吡斯的明'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '加兰他敏', '胆碱酯酶抑制剂/神经肌肉接头药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '加兰他敏'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '多奈哌齐', '胆碱酯酶抑制剂/神经肌肉接头药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '多奈哌齐'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '卡巴拉汀', '胆碱酯酶抑制剂/神经肌肉接头药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '卡巴拉汀'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '安贝氯铵', '胆碱酯酶抑制剂/神经肌肉接头药', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '安贝氯铵'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氟哌啶醇', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氟哌啶醇'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '奥氮平', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '奥氮平'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '喹硫平', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '喹硫平'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '利培酮', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '利培酮'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氯氮平', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氯氮平'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氟哌噻吨', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氟哌噻吨'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '奋乃静', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '奋乃静'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '舒必利', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '舒必利'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '阿立哌唑', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '阿立哌唑'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '帕利哌酮', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '帕利哌酮'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '齐拉西酮', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '齐拉西酮'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '丙戊酸', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '丙戊酸'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '卡马西平', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '卡马西平'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '苯妥英', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '苯妥英'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '左乙拉西坦', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '左乙拉西坦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '拉莫三嗪', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '拉莫三嗪'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '托吡酯', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '托吡酯'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '奥卡西平', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '奥卡西平'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '加巴喷丁', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '加巴喷丁'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '普瑞巴林', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '普瑞巴林'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '舍曲林', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '舍曲林'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '帕罗西汀', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '帕罗西汀'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氟西汀', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氟西汀'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '艾司西酞普兰', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '艾司西酞普兰'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '文拉法辛', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '文拉法辛'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '度洛西汀', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '度洛西汀'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '阿普唑仑', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '阿普唑仑'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '地西泮', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '地西泮'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '劳拉西泮', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '劳拉西泮'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '艾司唑仑', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '艾司唑仑'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氯硝西泮', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氯硝西泮'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '丁螺环酮', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '丁螺环酮'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '米氮平', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '米氮平'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '曲唑酮', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '曲唑酮'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '左旋多巴', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '左旋多巴'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '苯海索', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '苯海索'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '金刚烷胺', '抗精神病/抗癫痫/镇静类精神药物', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '金刚烷胺'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '炉甘石', '外用/皮肤科', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '炉甘石'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氧化锌', '外用/皮肤科', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氧化锌'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '洗剂', '外用/皮肤科', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '洗剂'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '软膏', '外用/皮肤科', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '软膏'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '乳膏', '外用/皮肤科', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '乳膏'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '栓剂', '外用/皮肤科', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '栓剂'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '贴剂', '外用/皮肤科', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '贴剂'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '滴眼', '外用/皮肤科', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '滴眼'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '滴鼻', '外用/皮肤科', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '滴鼻'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '滴耳', '外用/皮肤科', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '滴耳'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '利巴韦林', '抗病毒（非抗菌药）', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '利巴韦林'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '奥司他韦', '抗病毒（非抗菌药）', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '奥司他韦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '帕拉米韦', '抗病毒（非抗菌药）', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '帕拉米韦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '阿昔洛韦', '抗病毒（非抗菌药）', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '阿昔洛韦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '更昔洛韦', '抗病毒（非抗菌药）', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '更昔洛韦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '伐昔洛韦', '抗病毒（非抗菌药）', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '伐昔洛韦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '泛昔洛韦', '抗病毒（非抗菌药）', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '泛昔洛韦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '恩替卡韦', '抗病毒（非抗菌药）', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '恩替卡韦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '替诺福韦', '抗病毒（非抗菌药）', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '替诺福韦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '拉米夫定', '抗病毒（非抗菌药）', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '拉米夫定'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '阿德福韦', '抗病毒（非抗菌药）', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '阿德福韦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '干扰素', '抗病毒（非抗菌药）', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '干扰素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '利托那韦', '抗病毒（非抗菌药）', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '利托那韦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '奈玛特韦', '抗病毒（非抗菌药）', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '奈玛特韦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '玛巴洛沙韦', '抗病毒（非抗菌药）', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '玛巴洛沙韦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '甘草酸', '保肝/脑循环/营养神经', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '甘草酸'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '水飞蓟', '保肝/脑循环/营养神经', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '水飞蓟'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '双环醇', '保肝/脑循环/营养神经', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '双环醇'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '多烯磷脂酰胆碱', '保肝/脑循环/营养神经', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '多烯磷脂酰胆碱'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '胞磷胆碱', '保肝/脑循环/营养神经', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '胞磷胆碱'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '脑苷肌肽', '保肝/脑循环/营养神经', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '脑苷肌肽'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '神经节苷脂', '保肝/脑循环/营养神经', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '神经节苷脂'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '依达拉奉', '保肝/脑循环/营养神经', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '依达拉奉'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '奥拉西坦', '保肝/脑循环/营养神经', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '奥拉西坦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '吡拉西坦', '保肝/脑循环/营养神经', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '吡拉西坦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '小牛血', '保肝/脑循环/营养神经', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '小牛血'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '银杏', '其他', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '银杏'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '丹参', '其他', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '丹参'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '血塞通', '其他', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '血塞通'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '疏血通', '其他', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '疏血通'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '醒脑静', '其他', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '醒脑静'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '参附', '其他', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '参附'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '参麦', '其他', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '参麦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '生脉', '其他', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '生脉'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '磷酸肌酸', '其他', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '磷酸肌酸'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '辅酶', '其他', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '辅酶'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '三磷酸腺苷', '其他', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '三磷酸腺苷'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '门冬氨酸鸟氨酸', '其他', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '门冬氨酸鸟氨酸'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '还原型谷胱甘肽', '其他', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '还原型谷胱甘肽'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '水溶性维生素', '其他', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '水溶性维生素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '脂溶性维生素', '其他', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '脂溶性维生素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '枸橼酸', '抗凝/血液净化', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '枸橼酸'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '枸橼酸钠', '抗凝/血液净化', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '枸橼酸钠'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '血液滤过', '抗凝/血液净化', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '血液滤过'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '置换液', '抗凝/血液净化', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '置换液'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '透析', '抗凝/血液净化', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '透析'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '血液灌流', '抗凝/血液净化', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '血液灌流'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '血液净化', '抗凝/血液净化', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '血液净化'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '血浆置换', '抗凝/血液净化', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '血浆置换'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '连续性肾脏替代', '抗凝/血液净化', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '连续性肾脏替代'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', 'CRRT', '抗凝/血液净化', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = 'CRRT'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '多沙唑嗪', '降压/心血管', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '多沙唑嗪'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '特拉唑嗪', '降压/心血管', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '特拉唑嗪'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '替米沙坦', '降压/心血管', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '替米沙坦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '坎地沙坦', '降压/心血管', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '坎地沙坦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '奥美沙坦', '降压/心血管', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '奥美沙坦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '阿利沙坦', '降压/心血管', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '阿利沙坦'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '尼卡地平', '降压/心血管', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '尼卡地平'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '尼莫地平', '降压/心血管', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '尼莫地平'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '拉贝洛尔', '降压/心血管', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '拉贝洛尔'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '卡维地洛', '降压/心血管', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '卡维地洛'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '可乐定', '降压/心血管', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '可乐定'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '甲基多巴', '降压/心血管', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '甲基多巴'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '肼屈嗪', '降压/心血管', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '肼屈嗪'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '昂丹司琼', '止吐', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '昂丹司琼'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '格拉司琼', '止吐', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '格拉司琼'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '托烷司琼', '止吐', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '托烷司琼'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '雷莫司琼', '止吐', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '雷莫司琼'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '阿扎司琼', '止吐', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '阿扎司琼'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '多拉司琼', '止吐', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '多拉司琼'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '纳洛酮', '解毒/拮抗', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '纳洛酮'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氟马西尼', '解毒/拮抗', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氟马西尼'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '戊乙奎醚', '解毒/拮抗', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '戊乙奎醚'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '亚甲蓝', '解毒/拮抗', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '亚甲蓝'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '硫代硫酸钠', '解毒/拮抗', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '硫代硫酸钠'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '依地酸', '解毒/拮抗', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '依地酸'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '青霉胺', '解毒/拮抗', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '青霉胺'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '二巯丙醇', '解毒/拮抗', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '二巯丙醇'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', 'N-乙酰半胱氨酸', '解毒/拮抗', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = 'N-乙酰半胱氨酸'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '丁苯酞', '保肝/脑循环/营养神经', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '丁苯酞'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '奥扎格雷', '保肝/脑循环/营养神经', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '奥扎格雷'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '前列地尔', '其他', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '前列地尔'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '左卡尼汀', '其他', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '左卡尼汀'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '果糖二磷酸', '其他', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '果糖二磷酸'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '磷酸肌酸钠', '其他', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '磷酸肌酸钠'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '单唾液酸四己糖神经节苷脂', '保肝/脑循环/营养神经', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '单唾液酸四己糖神经节苷脂'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '达克罗宁', '局麻/口腔护理/外用消毒', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '达克罗宁'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '布比卡因', '局麻/口腔护理/外用消毒', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '布比卡因'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '罗哌卡因', '局麻/口腔护理/外用消毒', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '罗哌卡因'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '普鲁卡因', '局麻/口腔护理/外用消毒', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '普鲁卡因'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '丁卡因', '局麻/口腔护理/外用消毒', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '丁卡因'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '苯佐卡因', '局麻/口腔护理/外用消毒', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '苯佐卡因'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '含漱', '局麻/口腔护理/外用消毒', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '含漱'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '漱口', '局麻/口腔护理/外用消毒', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '漱口'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '西吡氯铵', '局麻/口腔护理/外用消毒', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '西吡氯铵'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '氯己定', '局麻/口腔护理/外用消毒', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '氯己定'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '聚维酮碘', '局麻/口腔护理/外用消毒', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '聚维酮碘'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '碘伏', '局麻/口腔护理/外用消毒', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '碘伏'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '碘甘油', '局麻/口腔护理/外用消毒', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '碘甘油'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '锡类散', '局麻/口腔护理/外用消毒', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '锡类散'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '冰硼散', '局麻/口腔护理/外用消毒', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '冰硼散'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '西瓜霜', '局麻/口腔护理/外用消毒', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '西瓜霜'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '芒硝', '中药/外敷', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '芒硝'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '冰片', '中药/外敷', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '冰片'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '金黄散', '中药/外敷', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '金黄散'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '青黛', '中药/外敷', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '青黛'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '云南白药', '中药/外敷', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '云南白药'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '伤科灵', '中药/外敷', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '伤科灵'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '正骨水', '中药/外敷', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '正骨水'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '红花油', '中药/外敷', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '红花油'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '微量元素', '微量元素/电解质营养', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '微量元素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '多种微量元素', '微量元素/电解质营养', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '多种微量元素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '安达美', '微量元素/电解质营养', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '安达美'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '门冬氨酸钾镁', '微量元素/电解质营养', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '门冬氨酸钾镁'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '葡萄糖酸锌', '微量元素/电解质营养', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '葡萄糖酸锌'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '硫酸锌', '微量元素/电解质营养', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '硫酸锌'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '亚硒酸钠', '微量元素/电解质营养', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '亚硒酸钠'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '含D3', '微量元素/电解质营养', '内置默认'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '含D3'
);

-- -----------------------------------------------------------------------------
-- 抗菌药词库训练：基于 HIS 药品字典(config_drug)补充白名单与黑名单
-- 白名单 broad_spectrum 新增 36 个核心通用名（现有25个→训练后61个）
-- 黑名单 non_antibiotic 新增 19 个（现有406个→训练后425个）
-- 幂等插入：已存在的关键词不重复插入，不覆盖已有数据
-- -----------------------------------------------------------------------------

-- === 白名单 broad_spectrum 补充 ===
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '青霉素', '青霉素类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '青霉素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '苄星青霉素', '青霉素类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '苄星青霉素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '阿莫西林', '青霉素类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '阿莫西林'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '阿莫西林克拉维酸', '青霉素类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '阿莫西林克拉维酸'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '头孢唑林', '头孢类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '头孢唑林'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '头孢呋辛', '头孢类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '头孢呋辛'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '头孢克洛', '头孢类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '头孢克洛'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '头孢丙烯', '头孢类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '头孢丙烯'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '头孢克肟', '头孢类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '头孢克肟'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '头孢地尼', '头孢类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '头孢地尼'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '头孢唑肟', '头孢类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '头孢唑肟'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '头孢噻肟', '头孢类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '头孢噻肟'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '头孢曲松', '头孢类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '头孢曲松'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '头孢美唑', '头孢类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '头孢美唑'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '头孢比罗酯', '头孢类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '头孢比罗酯'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '氨曲南', '单环β内酰胺类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '氨曲南'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '阿奇霉素', '大环内酯类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '阿奇霉素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '克拉霉素', '大环内酯类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '克拉霉素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '罗红霉素', '大环内酯类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '罗红霉素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '克林霉素', '林可酰胺类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '克林霉素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '多西环素', '四环素类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '多西环素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '替加环素', '四环素类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '替加环素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '依拉环素', '四环素类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '依拉环素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '异帕米星', '氨基糖苷类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '异帕米星'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '奥硝唑', '硝基咪唑类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '奥硝唑'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '左奥硝唑', '硝基咪唑类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '左奥硝唑'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '吗啉硝唑', '硝基咪唑类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '吗啉硝唑'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '呋喃妥因', '硝基呋喃类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '呋喃妥因'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '磷霉素', '磷霉素类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '磷霉素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '利福平', '抗结核类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '利福平'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '利福昔明', '抗结核类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '利福昔明'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '伊曲康唑', '抗真菌类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '伊曲康唑'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '氟胞嘧啶', '抗真菌类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '氟胞嘧啶'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '特比萘芬', '抗真菌类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '特比萘芬'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '艾沙康唑', '抗真菌类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '艾沙康唑'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'broad_spectrum', '多黏菌素', '多黏菌素类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'broad_spectrum' AND `keyword` = '多黏菌素'
);

-- === 黑名单 non_antibiotic 补充 ===
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '腺苷蛋氨酸', '保肝/利胆', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '腺苷蛋氨酸'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '三磷酸腺苷', '能量合剂', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '三磷酸腺苷'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '去乙酰毛花苷', '强心药', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '去乙酰毛花苷'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '甘草酸苷', '保肝/利胆', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '甘草酸苷'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '七叶皂苷', '消肿/改善循环', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '七叶皂苷'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '丝裂霉素', '抗肿瘤药', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '丝裂霉素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '放线菌素', '抗肿瘤药', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '放线菌素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '柔红霉素', '抗肿瘤药', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '柔红霉素'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '阿糖胞苷', '抗肿瘤/抗病毒', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '阿糖胞苷'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '阿扎胞苷', '抗肿瘤药', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '阿扎胞苷'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '依托泊苷', '抗肿瘤药', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '依托泊苷'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '凝结芽孢杆菌', '益生菌', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '凝结芽孢杆菌'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '布拉氏酵母菌', '益生菌', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '布拉氏酵母菌'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '酪酸梭菌', '益生菌', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '酪酸梭菌'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '乳杆菌', '益生菌', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '乳杆菌'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '乳酸菌', '益生菌', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '乳酸菌'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '细菌溶解产物', '免疫调节剂', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '细菌溶解产物'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '卡介菌', '诊断/免疫调节剂', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '卡介菌'
);
INSERT INTO `config_abx_word`
    (`word_type`, `keyword`, `category`, `remark`)
SELECT 'non_antibiotic', '结核菌素', '诊断试剂', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `config_abx_word`
    WHERE `word_type` = 'non_antibiotic' AND `keyword` = '结核菌素'
);

-- ---------------------------------------------------------------------
-- APACHE II 评分配置初始化数据
-- ---------------------------------------------------------------------

-- 监护item_code配置
INSERT INTO `config_apache2`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'observe_item', 'temperature', 'oi_tiwen', '体温', '体温监护item_code，可配置', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `config_apache2` WHERE `config_type`='observe_item' AND `config_key`='temperature');

INSERT INTO `config_apache2`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'observe_item', 'heart_rate', 'oi_hr', '心率', '心率监护item_code，可配置', 2
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `config_apache2` WHERE `config_type`='observe_item' AND `config_key`='heart_rate');

INSERT INTO `config_apache2`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'observe_item', 'respiratory_rate', 'oi_hxpl', '呼吸频率', '实际呼吸频率监护item_code，可配置', 3
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `config_apache2` WHERE `config_type`='observe_item' AND `config_key`='respiratory_rate');

INSERT INTO `config_apache2`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'observe_item', 'map', 'oi_ycpjy,oi_pjy', '平均动脉压', 'MAP监护item_code，优先有创(oi_ycpjy)，多个用逗号分隔', 4
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `config_apache2` WHERE `config_type`='observe_item' AND `config_key`='map');

INSERT INTO `config_apache2`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'observe_item', 'fio2', 'oi_FiO2(设置值)', 'FiO2', '吸氧浓度监护item_code', 5
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `config_apache2` WHERE `config_type`='observe_item' AND `config_key`='fio2');

-- 检验lis_item_code配置
INSERT INTO `config_apache2`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'lis_item', 'sodium', '100150,5039', '血清钠', '钠lis_item_code，多个来源用逗号分隔', 1
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `config_apache2` WHERE `config_type`='lis_item' AND `config_key`='sodium');

INSERT INTO `config_apache2`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'lis_item', 'potassium', '100140,5040', '血清钾', '钾lis_item_code，多个来源用逗号分隔', 2
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `config_apache2` WHERE `config_type`='lis_item' AND `config_key`='potassium');

INSERT INTO `config_apache2`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'lis_item', 'creatinine', '100210', '血清肌酐', '肌酐lis_item_code，原单位μmol/L，评分时转mg/dL', 3
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `config_apache2` WHERE `config_type`='lis_item' AND `config_key`='creatinine');

INSERT INTO `config_apache2`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'lis_item', 'hct', '200060', '血细胞比容', 'HCT lis_item_code', 4
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `config_apache2` WHERE `config_type`='lis_item' AND `config_key`='hct');

INSERT INTO `config_apache2`
    (`config_type`, `config_key`, `config_value`, `item_name`, `remark`, `sort_no`)
SELECT 'lis_item', 'wbc', '200010', '白细胞', 'WBC lis_item_code', 5
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM `config_apache2` WHERE `config_type`='lis_item' AND `config_key`='wbc');
