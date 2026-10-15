SELECT s.id AS user_id,
  s.realname AS realname,
  s.work_no AS work_no,
  s.depart_code AS depart_code,
  s.depart_name AS depart_name,
  s.post_name AS post_name,
  s.office_date AS office_date,
  s.leave_date AS leave_date,
  CASE WHEN s.post_name LIKE '%医师%' THEN 1 ELSE 0 END AS is_doctor,
  CASE WHEN s.post_name LIKE '%护士%' OR s.post_name LIKE '%护师%' THEN 1 ELSE 0 END AS is_nurse
FROM "zing_icu_db_prod"."config_staff" s
WHERE (s.del_flag = 0) AND (s.leave_date IS NULL) AND ((s.depart_name LIKE '%ICU%' OR (s.depart_name LIKE '%重症%' AND s.depart_name NOT LIKE '%呼吸与危重症%')))