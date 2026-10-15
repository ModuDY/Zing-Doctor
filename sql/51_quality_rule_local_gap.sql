-- =====================================================================
-- 51_quality_rule_local_gap.sql
-- 质控规则层补齐：CAUTI 发病率（本院自建 LOCAL 规则，达梦 DM8）
--
-- 背景：业务指标 = 原子项 ÷ 原子项 × 放大系数，由 quality_count_rule 配置。
--       生产配置快照（27_restore_config_snapshot.sql）里的 20 条 ICU 侧规则
--       已覆盖：VAP 发病率（quality_24÷quality_25×1000）、CRBSI 发病率
--       （quality_26÷quality_27×1000）、非计划拔管率（17÷18）、48h 再插管率
--       （19÷18）、床位使用率（2÷301）、医师/护士床位比（302/303÷304）、
--       DVT 预防率（14÷403）等 —— 这些规则在原子项落地后自动出数，
--       **不需要**也不应该重复自建（同一指标两条规则会在看板出现两行）。
--
--       唯一缺口：CAUTI（导尿管相关泌尿系感染）发病率没有对应规则，
--       本脚本按 2015 版官方口径补一条 LOCAL 规则：
--         ICU导尿管相关泌尿系感染（CAUTI）发生率（例/千导尿管日）
--           = CAUTI 发生例数 ÷ 同期患者导尿管留置总天数 × 1000‰
--
-- 幂等：只插 rule_id 不存在的行，可重复执行。
-- 规则编号空间：LOCAL_ 前缀，与 ICU 侧纯数字 id 天然不冲突
--               （见 sql/17_quality_rule_local.sql 的约定，不要改）。
-- 执行方式（SYSDBA）：
--   disql SYSDBA/口令@host:port
--   SQL> start /opt/zing-doctor/sql/51_quality_rule_local_gap.sql
-- =====================================================================

BEGIN
  FOR r IN (
    SELECT 'LOCAL_CAUTI_RATE' AS rule_id FROM DUAL
     WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."quality_count_rule" WHERE "rule_id" = 'LOCAL_CAUTI_RATE')
  ) LOOP
    INSERT INTO "zing_doctor_db_prod"."quality_count_rule"
      ("rule_id", "count_name", "quality_type_code", "is_visible", "is_show_page", "sort_no", "remark",
       "numerator_code", "denominator_code", "percent_unit", "percent_rate", "percent_precision",
       "depart_code", "target_direction", "origin", "local_override", "source_status", "status")
    VALUES
      ('LOCAL_CAUTI_RATE', 'ICU导尿管相关泌尿系感染（CAUTI）发生率', NULL, 1, 1, 130,
       '本院自建：2015版重症质控指标十五 = CAUTI发生例数 ÷ 导尿管留置总天数 × 1000（例/千导尿管日）；原子项 quality_425/426 由 fact_device 计算',
       'quality_425', 'quality_426', '‰', 1000, 2,
       NULL, 'DOWN', 'LOCAL', 0, 1, 1);
  END LOOP;
END;
/
