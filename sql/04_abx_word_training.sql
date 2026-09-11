-- =============================================================================
-- 抗菌药词库训练升级脚本（基于 HIS 药品字典 config_drug）
-- 适用：已部署环境直接执行，幂等插入，不覆盖已有数据
-- 执行方式：disql SYSDBA/密码@IP:端口 后 start 04_abx_word_training.sql
-- =============================================================================
-- 训练来源：HIS 药品字典 config_drug 表，is_antibiotics=1 共 127 条/83 个通用名
-- 训练结果：
--   白名单 broad_spectrum：现有 25 个 → 补充 36 个核心通用名 → 共 61 个
--   黑名单 non_antibiotic：现有 406 个 → 补充 19 个（抗肿瘤/强心/保肝/益生菌等）→ 共 425 个
-- 说明：HIS is_antibiotics 字段仅标记全身用抗菌药，外用/眼科/妇科抗菌药标记为0（ICU影响小）；
--       用户明确指出的非抗菌药（芒硝/西吡氯铵/达克罗宁/多种微量元素/氨溴索）已在现有黑名单中覆盖。
-- =============================================================================

-- === 白名单 broad_spectrum 补充（36个）===
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '青霉素', '青霉素类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '青霉素'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '苄星青霉素', '青霉素类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '苄星青霉素'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '阿莫西林', '青霉素类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '阿莫西林'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '阿莫西林克拉维酸', '青霉素类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '阿莫西林克拉维酸'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '头孢唑林', '头孢类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '头孢唑林'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '头孢呋辛', '头孢类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '头孢呋辛'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '头孢克洛', '头孢类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '头孢克洛'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '头孢丙烯', '头孢类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '头孢丙烯'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '头孢克肟', '头孢类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '头孢克肟'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '头孢地尼', '头孢类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '头孢地尼'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '头孢唑肟', '头孢类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '头孢唑肟'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '头孢噻肟', '头孢类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '头孢噻肟'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '头孢曲松', '头孢类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '头孢曲松'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '头孢美唑', '头孢类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '头孢美唑'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '头孢比罗酯', '头孢类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '头孢比罗酯'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '氨曲南', '单环β内酰胺类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '氨曲南'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '阿奇霉素', '大环内酯类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '阿奇霉素'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '克拉霉素', '大环内酯类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '克拉霉素'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '罗红霉素', '大环内酯类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '罗红霉素'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '克林霉素', '林可酰胺类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '克林霉素'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '多西环素', '四环素类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '多西环素'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '替加环素', '四环素类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '替加环素'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '依拉环素', '四环素类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '依拉环素'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '异帕米星', '氨基糖苷类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '异帕米星'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '奥硝唑', '硝基咪唑类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '奥硝唑'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '左奥硝唑', '硝基咪唑类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '左奥硝唑'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '吗啉硝唑', '硝基咪唑类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '吗啉硝唑'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '呋喃妥因', '硝基呋喃类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '呋喃妥因'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '磷霉素', '磷霉素类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '磷霉素'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '利福平', '抗结核类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '利福平'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '利福昔明', '抗结核类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '利福昔明'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '伊曲康唑', '抗真菌类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '伊曲康唑'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '氟胞嘧啶', '抗真菌类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '氟胞嘧啶'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '特比萘芬', '抗真菌类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '特比萘芬'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '艾沙康唑', '抗真菌类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '艾沙康唑'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'broad_spectrum', '多黏菌素', '多黏菌素类', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'broad_spectrum' AND "keyword" = '多黏菌素'
);

-- === 黑名单 non_antibiotic 补充（19个）===
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'non_antibiotic', '腺苷蛋氨酸', '保肝/利胆', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'non_antibiotic' AND "keyword" = '腺苷蛋氨酸'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'non_antibiotic', '三磷酸腺苷', '能量合剂', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'non_antibiotic' AND "keyword" = '三磷酸腺苷'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'non_antibiotic', '去乙酰毛花苷', '强心药', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'non_antibiotic' AND "keyword" = '去乙酰毛花苷'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'non_antibiotic', '甘草酸苷', '保肝/利胆', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'non_antibiotic' AND "keyword" = '甘草酸苷'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'non_antibiotic', '七叶皂苷', '消肿/改善循环', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'non_antibiotic' AND "keyword" = '七叶皂苷'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'non_antibiotic', '丝裂霉素', '抗肿瘤药', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'non_antibiotic' AND "keyword" = '丝裂霉素'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'non_antibiotic', '放线菌素', '抗肿瘤药', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'non_antibiotic' AND "keyword" = '放线菌素'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'non_antibiotic', '柔红霉素', '抗肿瘤药', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'non_antibiotic' AND "keyword" = '柔红霉素'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'non_antibiotic', '阿糖胞苷', '抗肿瘤/抗病毒', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'non_antibiotic' AND "keyword" = '阿糖胞苷'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'non_antibiotic', '阿扎胞苷', '抗肿瘤药', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'non_antibiotic' AND "keyword" = '阿扎胞苷'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'non_antibiotic', '依托泊苷', '抗肿瘤药', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'non_antibiotic' AND "keyword" = '依托泊苷'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'non_antibiotic', '凝结芽孢杆菌', '益生菌', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'non_antibiotic' AND "keyword" = '凝结芽孢杆菌'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'non_antibiotic', '布拉氏酵母菌', '益生菌', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'non_antibiotic' AND "keyword" = '布拉氏酵母菌'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'non_antibiotic', '酪酸梭菌', '益生菌', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'non_antibiotic' AND "keyword" = '酪酸梭菌'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'non_antibiotic', '乳杆菌', '益生菌', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'non_antibiotic' AND "keyword" = '乳杆菌'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'non_antibiotic', '乳酸菌', '益生菌', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'non_antibiotic' AND "keyword" = '乳酸菌'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'non_antibiotic', '细菌溶解产物', '免疫调节剂', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'non_antibiotic' AND "keyword" = '细菌溶解产物'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'non_antibiotic', '卡介菌', '诊断/免疫调节剂', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'non_antibiotic' AND "keyword" = '卡介菌'
);
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config"
    ("word_type", "keyword", "category", "remark")
SELECT 'non_antibiotic', '结核菌素', '诊断试剂', 'HIS药品字典训练补充'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config"
    WHERE "word_type" = 'non_antibiotic' AND "keyword" = '结核菌素'
);

-- 验证：训练后词库统计
SELECT 'broad_spectrum' AS word_type, COUNT(*) AS cnt FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type" = 'broad_spectrum' AND "status" = 1
UNION ALL
SELECT 'non_antibiotic', COUNT(*) FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type" = 'non_antibiotic' AND "status" = 1;
