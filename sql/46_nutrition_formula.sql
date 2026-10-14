-- 46_nutrition_formula.sql
-- 达梦 DM8：营养支持分析——营养制剂配方字典（Level 3 能量/蛋白估算的权威依据）。
-- 放应用库 zing_doctor_db_prod（非 ICU 源库），由营养科/说明书维护。
-- 密度单位：能量 kcal/ml，蛋白 g/ml；按“每ml”存储以适配 500/1000/1500ml 不同规格。
-- 肠内值由营养科提供；肠外值取自药品说明书，上线前须经营养科核对规格（尤其氨基酸 5%/8.5%/11.4%）。

DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count FROM ALL_TABLES
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND UPPER(TABLE_NAME) = 'CONFIG_NUTRITION_FORMULA';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE TABLE "zing_doctor_db_prod"."config_nutrition_formula" (
            "id" VARCHAR(64) NOT NULL,
            "formula_code" VARCHAR(32) NOT NULL,
            "formula_name" VARCHAR(128) NOT NULL,
            "route_type" VARCHAR(16) NOT NULL,
            "energy_kcal_per_ml" NUMBER(8,3),
            "protein_g_per_ml" NUMBER(8,4),
            "match_keywords" VARCHAR(512),
            "spec_note" VARCHAR(256),
            "source_note" VARCHAR(256),
            "status" TINYINT DEFAULT 1,
            "create_time" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            "update_time" TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY ("id")
        )';
    END IF;
END;
/

-- 唯一索引：formula_code
DECLARE
    v_count INT;
BEGIN
    SELECT COUNT(*) INTO v_count FROM ALL_INDEXES
     WHERE UPPER(OWNER) = 'ZING_DOCTOR_DB_PROD'
       AND UPPER(INDEX_NAME) = 'UX_CONFIG_NUTRITION_FORMULA_CODE';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX "zing_doctor_db_prod"."ux_config_nutrition_formula_code" ON "zing_doctor_db_prod"."config_nutrition_formula" ("formula_code")';
    END IF;
END;
/

COMMENT ON TABLE "zing_doctor_db_prod"."config_nutrition_formula" IS '营养制剂配方字典：肠内/肠外能量与蛋白密度，营养科维护';
COMMENT ON COLUMN "zing_doctor_db_prod"."config_nutrition_formula"."route_type" IS 'ENTERAL肠内/PARENTERAL肠外';
COMMENT ON COLUMN "zing_doctor_db_prod"."config_nutrition_formula"."energy_kcal_per_ml" IS '能量密度 kcal/ml';
COMMENT ON COLUMN "zing_doctor_db_prod"."config_nutrition_formula"."protein_g_per_ml" IS '蛋白质/氨基酸密度 g/ml';
COMMENT ON COLUMN "zing_doctor_db_prod"."config_nutrition_formula"."match_keywords" IS '医嘱name匹配关键词，逗号分隔；命中即映射本制剂';
COMMENT ON COLUMN "zing_doctor_db_prod"."config_nutrition_formula"."spec_note" IS '规格/剂型备注，如浓度、整袋';

-- 初始化（按 formula_code 幂等：已存在则更新密度，不存在则插入）
MERGE INTO "zing_doctor_db_prod"."config_nutrition_formula" t
USING (
    SELECT 'ENT_RUINENG'  AS formula_code, '瑞能'                                   AS formula_name, 'ENTERAL'    AS route_type,
           1.3   AS energy_kcal_per_ml, 0.0230 AS protein_g_per_ml, '瑞能'                 AS match_keywords, '肠内营养乳剂(TPF)类,营养科提供值' AS spec_note, '营养科提供' AS source_note FROM DUAL UNION ALL
    SELECT 'ENT_BAIPULI', '百普力', 'ENTERAL', 1.0, 0.0400, '百普力', '短肽型肠内,营养科提供值', '营养科提供' FROM DUAL UNION ALL
    SELECT 'ENT_RUIDAI',   '瑞代',   'ENTERAL', 0.9, 0.0340, '瑞代,短肽', '糖尿病适用肠内,营养科提供值', '营养科提供' FROM DUAL UNION ALL
    SELECT 'ENT_KANQUANLI','康全力','ENTERAL', 0.75, 0.0320, '康全力', '营养科提供值', '营养科提供' FROM DUAL UNION ALL
    SELECT 'ENT_NENGLI','能全力', 'ENTERAL', 1.0, 0.0400, '能全力', '标准整蛋白肠内,营养科提供值', '营养科提供' FROM DUAL UNION ALL
    SELECT 'ENT_RUIXIAN', '瑞先',   'ENTERAL', 1.5, 0.0560, '瑞先', '高蛋白肠内,营养科提供值', '营养科提供' FROM DUAL UNION ALL
    SELECT 'PAR_FAT20',    '脂肪乳(C14~24/中长链20%)', 'PARENTERAL', 2.0, 0.0000, '脂肪乳', '20%浓度2.0kcal/ml,不含氨基酸', '说明书/DailyMed:20%脂肪乳2.0kcal/ml' FROM DUAL UNION ALL
    SELECT 'PAR_AA18_85', '复方氨基酸18AA-II(8.5% 乐凡命)', 'PARENTERAL', 0.34, 0.0850, '18AA-II,乐凡命', '250ml:21.25g=0.085g/ml，氨基酸按4kcal/g折算0.34kcal/ml', 'config_drug 实测规格 250ml:21.25g/瓶' FROM DUAL UNION ALL
    SELECT 'PAR_KABIVEN',  '卡文 脂肪乳氨基酸(17)葡萄糖(11%)', 'PARENTERAL', 0.694, 0.0236, '脂肪乳氨基酸(17)葡萄糖,卡文,全合一', '1440ml整袋:总能量约1000kcal,氨基酸34g;整袋密度', '说明书:1440ml氨基酸34g/总能量约1000kcal' FROM DUAL
) s
ON (t."formula_code" = s.formula_code)
WHEN MATCHED THEN UPDATE SET
    t."formula_name" = s.formula_name, t."route_type" = s.route_type,
    t."energy_kcal_per_ml" = s.energy_kcal_per_ml, t."protein_g_per_ml" = s.protein_g_per_ml,
    t."match_keywords" = s.match_keywords, t."spec_note" = s.spec_note,
    t."source_note" = s.source_note, t."update_time" = CURRENT_TIMESTAMP
WHEN NOT MATCHED THEN
    ("id", "formula_code", "formula_name", "route_type", "energy_kcal_per_ml",
     "protein_g_per_ml", "match_keywords", "spec_note", "source_note", "status")
    VALUES (SYS_GUID(), s.formula_code, s.formula_name, s.route_type, s.energy_kcal_per_ml,
     s.protein_g_per_ml, s.match_keywords, s.spec_note, s.source_note, 1);

-- 旧编码 PAR_AA18_5（复方氨基酸18AA-II(5%)）已被 PAR_AA18_85 取代：
-- 其关键词 '氨基酸(18AA' 在真实医嘱名（复方氨基酸注射液(18AA-II)(乐凡命)）里永不命中，
-- 且真实规格为 250ml:21.25g（8.5%，config_drug 实测），原按 5% 记会低估约 40%。
-- 这里清理旧行，避免留下误配；新库首次执行时本就没有该行，DELETE 影响 0 行。
DELETE FROM "zing_doctor_db_prod"."config_nutrition_formula" WHERE formula_code = 'PAR_AA18_5';

COMMIT;
