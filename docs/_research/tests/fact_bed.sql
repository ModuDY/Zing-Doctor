SELECT b.id AS bed_id,
  b.bed_code AS bed_code,
  b.bed_name AS bed_name,
  b.depart_code AS depart_code,
  b.begin_use_time AS begin_use_time,
  b.stop_use_time AS stop_use_time,
  CASE WHEN b.begin_use_time IS NULL OR b.begin_use_time > TIMESTAMP '2026-10-01 00:00:00' THEN TIMESTAMP '2026-10-01 00:00:00' ELSE b.begin_use_time END AS open_to,
  DATEDIFF(DAY, CASE WHEN b.begin_use_time < TIMESTAMP '2026-09-01 00:00:00' THEN TIMESTAMP '2026-09-01 00:00:00' ELSE b.begin_use_time END, CASE WHEN (b.stop_use_time IS NULL OR b.stop_use_time > TIMESTAMP '2026-10-01 00:00:00') THEN TIMESTAMP '2026-10-01 00:00:00' ELSE b.stop_use_time END) AS bed_days,
  CASE WHEN (b.begin_use_time IS NULL OR b.begin_use_time < TIMESTAMP '2026-10-01 00:00:00') AND (b.stop_use_time IS NULL OR b.stop_use_time >= TIMESTAMP '2026-09-01 00:00:00') THEN 1 ELSE 0 END AS open_in_period
FROM "zing_icu_db_prod"."config_bed" b
WHERE (b.del_flag = 0)