-- 50_quality_gap_metrics_only.sql

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'dvt_prevent_status = 1',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '任一预防措施（药物/机械/滤器）即计入；2024版ICU-07口径，源为医嘱侧',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_14';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'dvt_only_drug = 1',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '仅药物预防（未做机械预防、未置滤器）',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_404';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'dvt_drug_status = 1',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '含合并其他预防措施者',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_405';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'dvt_only_mech = 1',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '仅机械预防（肢体加压泵/梯度压力弹力袜等）',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_406';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'dvt_mech_status = 1',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '含合并其他预防措施者',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_407';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'dvt_only_filter = 1',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '仅滤器（未做药物/机械预防）',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_408';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'dvt_filter_status = 1',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '含合并其他预防措施者',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_409';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'dvt_prevent_status = 1 AND dvt_diag = 1',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '预防 + 诊断（诊断文本含深静脉血栓/下肢静脉血栓/肺栓塞）',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_411';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'dvt_prevent_status = 1 AND died_in_period = 1',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '预防 + 本周期内死亡',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_412';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'dvt_prevent_status = 1 AND dvt_diag = 1 AND died_in_period = 1',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '预防 + 诊断 + 本周期内死亡',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_413';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'unplanned_removal = 1 AND device_kind = ''气道导管''',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '2024版ICU-12分子：patient_tube.is_not_plan_extubation=1；源与导管统计页同表同口径',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_17';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'removed_in_period = 1 AND device_kind = ''气道导管''',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '2024版ICU-12/13共同分母：本周期内拔管例数',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_18';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'reintubation_48h = 1 AND device_kind = ''气道导管''',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '2024版ICU-13分子：计划拔管后48h再插管（is_reintubation24 字段名为24、实际口径48h，见 TubeStatsMapper）；源字段无法区分再插管是否继发于非计划拔管，按排除非计划拔管行近似',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_19';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'device_infection = 1 AND device_kind = ''气道导管''',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = '[{"key": "insert_time", "label": "置管时间", "width": 160}, {"key": "remove_time", "label": "拔管时间", "width": 160}]',
       "remark"                 = '2024版ICU-16分子：以医生确诊录入（patient_doc_device_infection, VAP）为准，确诊时间落在置入～拔除+48h窗口；需先在「导管相关感染确诊」页录入',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_24';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'SUM',
       "agg"                    = 'SUM',
       "scale"                  = NULL,
       "expr_where"             = 'device_kind = ''气道导管''',
       "expr_numerator"         = 'device_days',
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '2024版ICU-16分母。当前以气管插管/气切留置天数代理有创通气天数（呼吸机使用记录接入后替换事实层取数列）',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_25';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'device_infection = 1 AND device_kind = ''CVC''',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = '[{"key": "insert_time", "label": "置管时间", "width": 160}, {"key": "remove_time", "label": "拔管时间", "width": 160}]',
       "remark"                 = '2024版ICU-17分子：医生确诊录入（CRBSI），窗口=置入～拔除+48h；血管导管含CVC/PICC/PORT/脐静脉导管，当前按CVC白名单覆盖',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_26';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'SUM',
       "agg"                    = 'SUM',
       "scale"                  = NULL,
       "expr_where"             = 'device_kind = ''CVC''',
       "expr_numerator"         = 'device_days',
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '2024版ICU-17分母：CVC留置天数，按「本周期∩留置区间」切分，未拔截止转出带管/周期末',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_27';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'device_infection = 1 AND device_kind = ''导尿管''',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = '[{"key": "insert_time", "label": "置管时间", "width": 160}, {"key": "remove_time", "label": "拔管时间", "width": 160}]',
       "remark"                 = '2015版十五：CAUTI发生例数，以医生确诊录入（CAUTI）为准，窗口=置入～拔除+48h',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_425';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'SUM',
       "agg"                    = 'SUM',
       "scale"                  = NULL,
       "expr_where"             = 'device_kind = ''导尿管''',
       "expr_numerator"         = 'device_days',
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '2015版十五分母：导尿管留置总天数（例/千导尿管日）',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_426';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'SUM',
       "scale"                  = NULL,
       "expr_where"             = 'bed_days > 0',
       "expr_numerator"         = 'bed_days',
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '2024版ICU-01分母：床位开放记录按「本周期∩开放区间」切分求和；与占用床日（quality_2）同构，二者相除即床位使用率（规则层 LOCAL_BED_USAGE）',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_301';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'is_doctor = 1',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '2026-10-15连库激活：config_staff 在职人员按档案科室（ICU/重症相关，排除呼吸与危重症医学科）+岗位含医师计；排班表未启用，暂无在岗班次过滤（2024版ICU-02口径）',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_302';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'is_nurse = 1',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '同 quality_302：岗位含护士/护师计（2024版ICU-03口径）',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_303';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'open_in_period = 1',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '2024版ICU-02/03分母：本周期内处于开放状态的床位数',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_304';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'AVG',
       "agg"                    = 'SUM',
       "scale"                  = NULL,
       "expr_where"             = 'ards = 1 AND mv_days > 0',
       "expr_numerator"         = 'mv_days',
       "expr_denominator_where" = 'ards = 1 AND mv_days > 0',
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '以气管插管/气切留置天数代理（呼吸机使用记录接入后替换事实层取数列）；分母限定有创通气的ARDS患者',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_477';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'AVG',
       "agg"                    = 'SUM',
       "scale"                  = NULL,
       "expr_where"             = 'ards = 1 AND prone_min > 0',
       "expr_numerator"         = 'prone_min / 1440.0',
       "expr_denominator_where" = 'ards = 1 AND prone_min > 0',
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '取已提交的俯卧位通气治疗记录（patient_doc_prone_record）时长求和÷患者数；分母限定实施过俯卧位的ARDS患者',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_486';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'ards = 1 AND died_in_period = 1',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '按ICU转归死亡计（国家ICU-11口径）；严格院内死亡需病案首页出院结局',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_487';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'ards_mild = 1 AND died_in_period = 1',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '同 quality_487 口径',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_488';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'ards_moderate = 1 AND died_in_period = 1',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '同 quality_487 口径',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_489';

UPDATE "zing_doctor_db_prod"."quality_metric_def"
   SET "impl_status"            = 'IMPL',
       "value_type"             = 'COUNT',
       "agg"                    = 'PT_COUNT',
       "scale"                  = NULL,
       "expr_where"             = 'ards_severe = 1 AND died_in_period = 1',
       "expr_numerator"         = NULL,
       "expr_denominator_where" = NULL,
       "dims"                   = '["depart_code"]',
       "patient_fields"         = NULL,
       "remark"                 = '同 quality_487 口径',
       "expr_version"           = "expr_version" + 1,
       "update_time"            = CURRENT_TIMESTAMP
 WHERE "index_code" = 'quality_490';

-- ---------------------------------------------------------------------
-- D) 核对（手动执行）
--   SELECT "fact_name", "status", "source_table" FROM "zing_doctor_db_prod"."quality_fact_def" ORDER BY "fact_name";
--   SELECT "index_code", "impl_status", "agg", "expr_where" FROM "zing_doctor_db_prod"."quality_metric_def"
--    WHERE "index_code" IN ('quality_14','quality_17','quality_24','quality_301','quality_426','quality_486');
--   预期：全部 IMPL；fact_device/fact_dvt/fact_bed/fact_ards = ACTIVE。
--   重算：POST /api/quality/recalc（async=true 返回 runId，轮询 /api/quality/run）。
-- ---------------------------------------------------------------------
