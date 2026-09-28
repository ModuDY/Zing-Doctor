-- 38_doc_header.sql
-- 评分文书抬头参数：院徽 + 院名（MySQL / MariaDB）
-- 对应达梦版：sql/38_doc_header.sql（真源，改动请先改那边）
-- 方言差异：双引号 → 反引号；|| / CHR(10) → CONCAT / CHAR(10)；
--          达梦版显式取 MAX(id)+1，MySQL 侧 id 为 AUTO_INCREMENT，不传。
-- =====================================================================

-- 1) 文书院名（多行，CHAR(10) 换行）
INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'DOC_HOSPITAL_NAME', '文书医院抬头', '', 'score', 10, 1, 'textarea',
       NULL,
       CONCAT('福州市第二总医院', CHAR(10), '福州市第二医院', CHAR(10), '福建省福州中西医结合医院'),
       0, NULL,
       'APACHE II / SOFA 评分文书抬头院名，每行一个，字号自上而下递减（20/18/16px）。留空恢复默认。'
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM `sys_param` WHERE `param_key` = 'DOC_HOSPITAL_NAME');

-- 2) 文书院徽（图片）
INSERT INTO `sys_param`
    (`param_key`, `param_name`, `param_value`, `param_group`, `sort_no`,
     `status`, `param_type`, `options`, `default_value`, `required`, `regex`, `remark`)
SELECT 'DOC_HOSPITAL_LOGO', '文书院徽', '', 'score', 11, 1, 'image',
       NULL, '/logo.png', 0, NULL,
       'APACHE II / SOFA 评分文书抬头院徽，可上传图片（转 base64）或填图片地址。留空恢复默认 /logo.png。'
  FROM DUAL
 WHERE NOT EXISTS (
    SELECT 1 FROM `sys_param` WHERE `param_key` = 'DOC_HOSPITAL_LOGO');

COMMIT;
