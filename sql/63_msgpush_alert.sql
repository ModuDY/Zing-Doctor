-- =====================================================================
-- 63_msgpush_alert.sql
-- 高风险预警「自动推送」：预警文案模板 + 预警接收号码 + 规则清单补全（达梦 DM8）
-- MySQL 镜像见 sql/mysql/63_msgpush_alert.sql
--
-- 背景：
--   58 号脚本落地了通道（地址、报文模板、凭证、审计），59/61/62 修了模板与成功标识，
--   但推送一直是「人工点测试发送」。本脚本补齐自动推送所需的配置：
--   每类预警一条短信文案，可由现场逐条改写。
--
-- 触发方式（代码，不在本脚本）：
--   MsgPushAlertTask 每 30 分钟扫一次工作台的 7 类待办，命中 MSGPUSH_RULES 就发；
--   同一患者同一规则当天已成功发过的不再发（依据 msg_push_log，不新增去重表）。
--
-- 文案变量（写在每条的 remark 里，现场可照抄）：
--   #time#（发送时刻 MM-dd HH:mm）#bedNo# #patientName# #inHospitalNo#
--   #departName# #wardName# #age# #gender#
--   GCS 另有 #gcsFrom# #gcsTo# #gcsDrop#；俯卧位 #proneStart# #proneHours#；
--   呼吸抑制 #rrValue#；脑损伤 #brainDiag#（命中的关键字，不是整条诊断原文）。
--   写错的占位符会原样出现在短信里（便于发现），不会静默变成空白。
--
-- 幂等：可重复执行（各项均为「不存在才插入」；规则清单仅在仍是旧默认值时更新）。
--
-- 注意：sys_param.remark 是 VARCHAR(500)，达梦里按「字节」算，中文一字 3 字节 ——
--       本脚本里最长的 remark 已压在 160 字节以内（约 60 个中文字），
--       后续往 remark 里补说明时请留意，否则会报「列[remark]长度超出定义」。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1) 预警接收号码（与测试号码分开：联调发自己手机，正式启用改值班手机）
-- ---------------------------------------------------------------------
INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_ALERT_RECEIVERS', '预警接收号码', '', 'msgpush', 19, 1, 'textarea',
       NULL, '', 0, NULL,
       '逗号分隔。自动推送的预警短信发给这些号码；留空则回退用「接收号码」。'
       || '建议联调期先填自己的手机，正式启用再改为值班手机或护士站号码 —— '
       || '与「接收号码」分开配，就不必把测试号码从公共项里删掉。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_ALERT_RECEIVERS');

-- ---------------------------------------------------------------------
-- 2) 七类预警的短信文案（默认值与 MsgPushAlertTemplates 逐字一致）
--    默认体例：时间 + 床位 + 姓名 + 住院号 + 触发依据 + 处置指向，控制在一条短信内
-- ---------------------------------------------------------------------
INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_CONTENT_ARDS_VENTILATED', '文案·ARDS 且机械通气', 
       '【ICU预警】#time# #bedNo# #patientName#（住院号#inHospitalNo#）已标识ARDS且机械通气中，请关注肺保护通气与俯卧位评估。',
       'msgpush', 21, 1, 'textarea',
       NULL,
       '【ICU预警】#time# #bedNo# #patientName#（住院号#inHospitalNo#）已标识ARDS且机械通气中，请关注肺保护通气与俯卧位评估。',
       0, NULL,
       '变量：#time#（发送时刻 MM-dd HH:mm）#bedNo# #patientName# #inHospitalNo# #departName# #wardName# #age# #gender#。'
       || '该患者只要仍满足「已标识 ARDS + 机械通气中」，每天最多发 1 条。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_CONTENT_ARDS_VENTILATED');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_CONTENT_GCS_DROP_2', '文案·GCS 较首查降 ≥2 分',
       '【ICU预警】#time# #bedNo# #patientName#（住院号#inHospitalNo#）今日GCS由#gcsFrom#降至#gcsTo#（降#gcsDrop#分），警惕意识状态恶化，请查看意识监测。',
       'msgpush', 22, 1, 'textarea',
       NULL,
       '【ICU预警】#time# #bedNo# #patientName#（住院号#inHospitalNo#）今日GCS由#gcsFrom#降至#gcsTo#（降#gcsDrop#分），警惕意识状态恶化，请查看意识监测。',
       0, NULL,
       '变量：#gcsFrom#（今日首查）#gcsTo#（最新一次）#gcsDrop#（降幅），另有 #time# #bedNo# #patientName# #inHospitalNo# 等。'
       || '插管未查言语（记 3T）的记录不参与比较 —— 与工作台同一口径，避免虚报下降。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_CONTENT_GCS_DROP_2');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_CONTENT_PRONE_OVERTIME', '文案·俯卧位超 16 小时未结束',
       '【ICU预警】#time# #bedNo# #patientName#（住院号#inHospitalNo#）俯卧位治疗自#proneStart#起已持续#proneHours#小时未记录结束，请评估是否复位。',
       'msgpush', 23, 1, 'textarea',
       NULL,
       '【ICU预警】#time# #bedNo# #patientName#（住院号#inHospitalNo#）俯卧位治疗自#proneStart#起已持续#proneHours#小时未记录结束，请评估是否复位。',
       0, NULL,
       '变量：#proneStart#（开始时刻 MM-dd HH:mm）#proneHours#（已持续小时数，取整），另有 #time# #bedNo# #patientName# #inHospitalNo# 等。'
       || '阈值 16 小时与工作台提示一致；该条较长，若被拆成两条短信，可删掉「自#proneStart#起」。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_CONTENT_PRONE_OVERTIME');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_CONTENT_RR_DEPRESSION', '文案·呼吸频率 < 8 次/分',
       '【ICU预警】#time# #bedNo# #patientName#（住院号#inHospitalNo#）最新呼吸频率#rrValue#次/分（<8次/分），警惕呼吸抑制，请查看监护记录。',
       'msgpush', 24, 1, 'textarea',
       NULL,
       '【ICU预警】#time# #bedNo# #patientName#（住院号#inHospitalNo#）最新呼吸频率#rrValue#次/分（<8次/分），警惕呼吸抑制，请查看监护记录。',
       0, NULL,
       '变量：#rrValue#（今日监护观察项最新一次呼吸频率），另有 #time# #bedNo# #patientName# #inHospitalNo# 等。'
       || '带单位或非数字的记录不参与判定（与工作台同一口径）。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_CONTENT_RR_DEPRESSION');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_CONTENT_BRAIN_INJURY', '文案·诊断含脑损伤',
       '【ICU预警】#time# #bedNo# #patientName#（住院号#inHospitalNo#）诊断含#brainDiag#，属急性脑损伤监测对象，请关注意识与瞳孔变化。',
       'msgpush', 25, 1, 'textarea',
       NULL,
       '【ICU预警】#time# #bedNo# #patientName#（住院号#inHospitalNo#）诊断含#brainDiag#，属急性脑损伤监测对象，请关注意识与瞳孔变化。',
       0, NULL,
       '变量：#brainDiag#（命中的关键字，如「脑出血」；不是整条诊断原文），另有 #time# #bedNo# #patientName# #inHospitalNo# 等。'
       || '注意这是「诊断标记」类提醒：只要诊断还在，每天都会各发一条，'
       || '不想收就从「触发推送的预警类型」里去掉 BRAIN_INJURY。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_CONTENT_BRAIN_INJURY');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_CONTENT_SOFA_NOT_TODAY', '文案·今日未评 SOFA',
       '【ICU预警】#time# #bedNo# #patientName#（住院号#inHospitalNo#）今日尚未评SOFA，请及时完成评分。',
       'msgpush', 26, 1, 'textarea',
       NULL,
       '【ICU预警】#time# #bedNo# #patientName#（住院号#inHospitalNo#）今日尚未评SOFA，请及时完成评分。',
       0, NULL,
       '变量：#time# #bedNo# #patientName# #inHospitalNo#。#departName#（科室名）也可用。'
       || '入科未满 24 小时不催（与工作台同一前置条件），当天评过后不再发。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_CONTENT_SOFA_NOT_TODAY');

INSERT INTO "zing_doctor_db_prod"."sys_param"
    ("id", "param_key", "param_name", "param_value", "param_group", "sort_no",
     "status", "param_type", "options", "default_value", "required", "regex", "remark")
SELECT (SELECT NVL(MAX("id"), 0) + 1 FROM "zing_doctor_db_prod"."sys_param"),
       'MSGPUSH_CONTENT_APACHE_NOT_TODAY', '文案·今日未评 APACHE II',
       '【ICU预警】#time# #bedNo# #patientName#（住院号#inHospitalNo#）今日尚未评APACHE II，请及时完成评分。',
       'msgpush', 27, 1, 'textarea',
       NULL,
       '【ICU预警】#time# #bedNo# #patientName#（住院号#inHospitalNo#）今日尚未评APACHE II，请及时完成评分。',
       0, NULL,
       '变量：#time# #bedNo# #patientName# #inHospitalNo#。'
       || '口径由「工作台评分待办口径」参数决定：按天催（TODAY_NO_SCORE）或仅催从未评分（ADMIT_24H_NEVER）。'
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM "zing_doctor_db_prod"."sys_param" WHERE "param_key" = 'MSGPUSH_CONTENT_APACHE_NOT_TODAY');

-- ---------------------------------------------------------------------
-- 3) 规则清单补全为 7 类（原说明只提了「工作台 5 类」且只列了 4 个代码，与实现不符）
--    仅在仍是 58 号的旧默认值（或还没配）时更新：现场自己调过的清单不覆盖
-- ---------------------------------------------------------------------
UPDATE "zing_doctor_db_prod"."sys_param"
   SET "param_value" = 'ARDS_VENTILATED,GCS_DROP_2,PRONE_OVERTIME,RR_DEPRESSION,BRAIN_INJURY,SOFA_NOT_TODAY,APACHE_NOT_TODAY',
       "default_value" = 'ARDS_VENTILATED,GCS_DROP_2,PRONE_OVERTIME,RR_DEPRESSION,BRAIN_INJURY,SOFA_NOT_TODAY,APACHE_NOT_TODAY',
       "remark" = '逗号分隔，留空=一律不发。可选：ARDS_VENTILATED、GCS_DROP_2、PRONE_OVERTIME、'
       || 'RR_DEPRESSION、BRAIN_INJURY、SOFA_NOT_TODAY、APACHE_NOT_TODAY（含义同工作台对应待办）。'
       || '同一患者同一规则当天最多 1 条；逐条文案见 MSGPUSH_CONTENT_* 参数。'
 WHERE "param_key" = 'MSGPUSH_RULES'
   AND ("param_value" IS NULL OR "param_value" = '' OR "param_value" = 'ARDS_VENTILATED,GCS_DROP_2');
