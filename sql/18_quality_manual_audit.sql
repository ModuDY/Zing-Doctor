-- =====================================================================
-- 18 人工录入审计与重算保护
--
-- 背景（现场问题）：
--   MANUAL 类指标（如 ICU 实际病死率）的值由科室手工录入。而重算的删除范围是
--   「整个周期」，判据只有 period_type + period_start，不看这个值是谁写的 ——
--   于是每月 1 日凌晨的定时批算会连人工录入值一起删掉，再写回 value=null 的占位行。
--   现场表现是「上个月录的数这个月不见了」，而日志里没有任何异常，极难定位。
--
-- 本脚本给结果表补三列，让「这个值是人录的」成为显式事实：
--   value_source  值的归属（AUTO / MANUAL）—— 重算删除时据此排除人工值
--   operator      录入人 —— 上报时被追问「谁录的、什么时候录的」可答
--   manual_note   录入备注 —— 说明取数依据，便于事后复核
--
-- 幂等：先查数据字典再决定是否 ADD COLUMN，重复执行不报错、不重复加列。
--   这不是锦上添花。本脚本挂在 install.sh 的 INCREMENTAL_SQL 里，而增量通道对
--   执行失败只 warn 后继续（见 apply_incremental）：若写成裸 ALTER TABLE ADD COLUMN，
--   每跑一次 install.sh 都会刷一批「列已存在」，真正的失败被淹没在这些噪声里；
--   更要紧的是「漏执行的库」就没法用同一份脚本补救，只能人工挑语句出来执行
--   —— 现场正是这样漏掉的：脚本一直在 install.sh 里，但生产库从未加过这三列，
--   表现为人工录入保存 500（无效的列名[value_source]）。
--
--   判定只看表名 + 列名，不带 OWNER：本库中 quality_metric_result 唯一，
--   多带一个 schema 条件只是多一处不同环境下数据字典视图的差异风险。
-- =====================================================================

DECLARE
  v_cnt INT;
BEGIN
  SELECT COUNT(*) INTO v_cnt FROM ALL_TAB_COLUMNS
   WHERE UPPER(TABLE_NAME) = 'QUALITY_METRIC_RESULT'
     AND UPPER(COLUMN_NAME) = 'VALUE_SOURCE';
  IF v_cnt = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE "zing_doctor_db_prod"."quality_metric_result" ADD COLUMN "value_source" VARCHAR(16) DEFAULT ''AUTO''';
  END IF;

  SELECT COUNT(*) INTO v_cnt FROM ALL_TAB_COLUMNS
   WHERE UPPER(TABLE_NAME) = 'QUALITY_METRIC_RESULT'
     AND UPPER(COLUMN_NAME) = 'OPERATOR';
  IF v_cnt = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE "zing_doctor_db_prod"."quality_metric_result" ADD COLUMN "operator" VARCHAR(64)';
  END IF;

  SELECT COUNT(*) INTO v_cnt FROM ALL_TAB_COLUMNS
   WHERE UPPER(TABLE_NAME) = 'QUALITY_METRIC_RESULT'
     AND UPPER(COLUMN_NAME) = 'MANUAL_NOTE';
  IF v_cnt = 0 THEN
    EXECUTE IMMEDIATE 'ALTER TABLE "zing_doctor_db_prod"."quality_metric_result" ADD COLUMN "manual_note" VARCHAR(500)';
  END IF;
END;
/

COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_result"."value_source" IS '值归属：AUTO=引擎计算 / MANUAL=人工录入（重算时受保护，不被覆盖）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_result"."operator"     IS '人工录入的操作人（审计留痕；引擎写入的行此列为空）';
COMMENT ON COLUMN "zing_doctor_db_prod"."quality_metric_result"."manual_note"  IS '人工录入备注（说明取数依据，便于事后复核）';

-- ---------------------------------------------------------------------
-- 历史数据回填（关键，不可省略）
--
-- 升级前录入的值没有 value_source，若只加列不回填，这些行会被判为 AUTO，
-- 升级后第一次重算照样把它们删掉 —— 等于本次修复对存量数据完全无效。
-- 判据沿用旧代码识别「已录入」的口径：calc_status=MANUAL 且有值。
-- ---------------------------------------------------------------------
UPDATE "zing_doctor_db_prod"."quality_metric_result"
SET "value_source" = 'MANUAL'
WHERE "calc_status" = 'MANUAL'
  AND "metric_value" IS NOT NULL
  AND ("value_source" IS NULL OR "value_source" <> 'MANUAL');

COMMIT;

-- ---------------------------------------------------------------------
-- 校验（执行后自查，应返回 0 行）
--   存在「人工录入但未标记」的行即说明回填未生效，此时不要重启服务。
-- ---------------------------------------------------------------------
SELECT COUNT(*) AS "未标记的人工录入行"
FROM "zing_doctor_db_prod"."quality_metric_result"
WHERE "calc_status" = 'MANUAL' AND "metric_value" IS NOT NULL
  AND ("value_source" IS NULL OR "value_source" <> 'MANUAL');
