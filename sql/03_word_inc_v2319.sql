-- =====================================================================
-- zing-doctor v23.1.9 抗菌药识别词库增量（非抗菌药补词）
-- 当前环境直接执行，无需重启后端（每次打开页面自动从表刷新）
-- 幂等：已存在词条自动跳过，不影响你手动添加/停用的词
--   disql SYSDBA/Sa_20250815@100.120.1.102:14236
--   SQL> start /opt/zing-doctor/sql/03_word_inc_v2319.sql
-- =====================================================================

INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '枸橼酸', '抗凝/血液净化', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='枸橼酸');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '枸橼酸钠', '抗凝/血液净化', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='枸橼酸钠');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '血液滤过', '抗凝/血液净化', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='血液滤过');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '置换液', '抗凝/血液净化', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='置换液');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '透析', '抗凝/血液净化', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='透析');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '血液灌流', '抗凝/血液净化', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='血液灌流');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '血液净化', '抗凝/血液净化', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='血液净化');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '血浆置换', '抗凝/血液净化', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='血浆置换');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '连续性肾脏替代', '抗凝/血液净化', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='连续性肾脏替代');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', 'CRRT', '抗凝/血液净化', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='CRRT');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '多沙唑嗪', '降压/心血管', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='多沙唑嗪');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '特拉唑嗪', '降压/心血管', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='特拉唑嗪');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '替米沙坦', '降压/心血管', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='替米沙坦');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '坎地沙坦', '降压/心血管', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='坎地沙坦');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '奥美沙坦', '降压/心血管', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='奥美沙坦');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '阿利沙坦', '降压/心血管', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='阿利沙坦');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '尼卡地平', '降压/心血管', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='尼卡地平');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '尼莫地平', '降压/心血管', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='尼莫地平');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '拉贝洛尔', '降压/心血管', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='拉贝洛尔');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '卡维地洛', '降压/心血管', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='卡维地洛');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '可乐定', '降压/心血管', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='可乐定');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '甲基多巴', '降压/心血管', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='甲基多巴');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '肼屈嗪', '降压/心血管', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='肼屈嗪');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '昂丹司琼', '止吐', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='昂丹司琼');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '格拉司琼', '止吐', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='格拉司琼');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '托烷司琼', '止吐', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='托烷司琼');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '雷莫司琼', '止吐', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='雷莫司琼');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '阿扎司琼', '止吐', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='阿扎司琼');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '多拉司琼', '止吐', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='多拉司琼');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '纳洛酮', '解毒/拮抗', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='纳洛酮');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '氟马西尼', '解毒/拮抗', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='氟马西尼');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '戊乙奎醚', '解毒/拮抗', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='戊乙奎醚');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '亚甲蓝', '解毒/拮抗', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='亚甲蓝');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '硫代硫酸钠', '解毒/拮抗', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='硫代硫酸钠');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '依地酸', '解毒/拮抗', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='依地酸');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '青霉胺', '解毒/拮抗', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='青霉胺');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '二巯丙醇', '解毒/拮抗', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='二巯丙醇');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', 'N-乙酰半胱氨酸', '解毒/拮抗', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='N-乙酰半胱氨酸');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '丁苯酞', '保肝/脑循环/营养神经', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='丁苯酞');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '奥扎格雷', '保肝/脑循环/营养神经', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='奥扎格雷');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '前列地尔', '其他', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='前列地尔');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '左卡尼汀', '其他', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='左卡尼汀');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '果糖二磷酸', '其他', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='果糖二磷酸');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '磷酸肌酸钠', '其他', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='磷酸肌酸钠');
INSERT INTO "zing_doctor_db_prod"."zing_abx_word_config" ("word_type","keyword","category","remark","status")
SELECT 'non_antibiotic', '单唾液酸四己糖神经节苷脂', '保肝/脑循环/营养神经', '内置默认', 1
WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."zing_abx_word_config" WHERE "word_type"='non_antibiotic' AND "keyword"='单唾液酸四己糖神经节苷脂');
