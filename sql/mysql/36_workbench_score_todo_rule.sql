-- 36_workbench_score_todo_rule.sql
-- 患者工作台：评分待办判定规则参数（MySQL / MariaDB）
-- 对应达梦版：sql/36_workbench_score_todo_rule.sql（真源，改动请先改那边）
-- 方言差异：双引号 → 反引号；|| → CONCAT；达梦版显式取 MAX(id)+1，
--          MySQL 侧 id 为 AUTO_INCREMENT，不传即可。
-- =====================================================================

INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'WORKBENCH_SCORE_TODO_RULE', '评分待办判定规则', '', 'workbench', 3, 1, 'select',
       CONCAT('[{"label":"当日无评分记录","value":"TODAY_NO_SCORE"},',
              '{"label":"入科超 24 小时从未评分","value":"ADMIT_24H_NEVER"}]'),
       'TODAY_NO_SCORE', 0, NULL,
       CONCAT('工作台「今日待办」中 SOFA / APACHE II 未评分提醒的判定口径，全院统一。',
              'TODAY_NO_SCORE：当日无评分记录即计入（含从未评分）；',
              'ADMIT_24H_NEVER：入科满 24 小时且从未评分才计入。',
              '两者都要求入科满 24 小时，当天入科不催。')
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM `sys_param` WHERE `param_key` = 'WORKBENCH_SCORE_TODO_RULE');

COMMIT;
