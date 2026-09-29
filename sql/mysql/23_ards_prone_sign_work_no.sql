-- ============================================================
-- MySQL 8.x 版本（由达梦 DM8 脚本自动转换 + 人工校验）
-- 主键由应用雪花算法生成（MyBatis-Plus ASSIGN_ID），显式插入时以插入值为准；
-- 仅当 INSERT 省略 id 时由 AUTO_INCREMENT 兜底（对应达梦原有的 SEQ.NEXTVAL 默认值）
-- 执行：mysql -uroot -p < 本文件（需先执行 00b_idempotent_helpers.sql）
-- ============================================================
SET NAMES utf8mb4;
USE `zing_doctor_db_prod`;

-- ---- 以下为原达梦 PL/SQL 幂等块转换得到的 DDL ----
CALL zing_add_column('patient_doc_prone_record', 'doctor_work_no', 'VARCHAR(32) COMMENT ''记录医师工号：按此从 config_staff_ca_info 取电子签名图；空则文书打印姓名''');
CALL zing_add_column('patient_doc_prone_record', 'nurse_work_no', 'VARCHAR(32) COMMENT ''记录护士工号（同上）''');
CALL zing_add_column('patient_doc_prone_record', 'senior_work_no', 'VARCHAR(32) COMMENT ''上级医师工号（同上）''');

-- =====================================================================
-- 23_ards_prone_sign_work_no.sql
-- ARDS 俯卧位通气治疗记录 —— 签名人「工号」落库
--
-- 背景：
--   patient_doc_prone_record 只存了签名人姓名（nurse_sign / doctor_sign / senior_sign），
--   工号没落库，于是文书上无法还原电子签名图：
--     · ICU 侧的电子签名存在只读库 config_staff_ca_info.signature_img，
--       只能按工号（work_no）查，重名、改名、同名不同人时按姓名查必然出错；
--     · 姓名是给人看的，工号才是签名图的主键 —— 少了它，文书只能打印文字，
--       与院里「电子签名图片」的文书规范不符。
--   本脚本补上三个工号列，姓名列保持不变（外院会诊 / 进修人员不在职工库，
--   签名允许手工输入姓名，此时工号为空、文书退化为打印姓名）。
--
-- 口径：
--   · 姓名与工号必须成对：签名人姓名被清空时，后端保存逻辑会把对应工号一并清空，
--     避免「人改了、签名图还是上一任的」——这类张冠李戴在文书上是医疗安全问题。
--   · 工号只用于反查签名图，不参与任何业务判断；查不到签名图时文书自动退回打印姓名。
--   · 存量数据工号为空（历史记录没有来源），不影响既有文书与归档。
--
-- 幂等（⚠️ 踩过的坑，勿回退）：
--   列存在性判断必须用**裸列名**与 UPPER(COLUMN_NAME) 比较。曾把带双引号的标识符
--   （'`doctor_work_no`'）传进比较式，匹配结果恒为 0，于是每次执行都去 ALTER，
--   第二次起直接报「列[doctor_work_no]已存在」并中断整个初始化流程。
--   列名在 EXECUTE IMMEDIATE 里才加双引号。
--
--   另外：表不存在（比 SQL 更老的环境）时直接跳过，不报「无效的表或视图名」；
--   COMMENT 也放进 PL/SQL 块里，保证整个脚本只有一条语句，迁移动作要么全做要么不做。
--
-- 执行方式（SYSDBA）：
--   disql SYSDBA/Sa_20250815@100.120.1.102:14236
--   SQL> start /opt/zing-doctor/sql/23_ards_prone_sign_work_no.sql
-- =====================================================================
