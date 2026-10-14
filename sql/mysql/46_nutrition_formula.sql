-- MySQL / MariaDB：营养制剂配方字典（Level 3 能量/蛋白估算权威依据）。
-- 幂等：表不存在则建；配方按 formula_code 用 INSERT IGNORE 初始化。

CREATE TABLE IF NOT EXISTS `config_nutrition_formula` (
  `id` VARCHAR(64) NOT NULL,
  `formula_code` VARCHAR(32) NOT NULL COMMENT '制剂编码',
  `formula_name` VARCHAR(128) NOT NULL COMMENT '制剂名称',
  `route_type` VARCHAR(16) NOT NULL COMMENT 'ENTERAL肠内/PARENTERAL肠外',
  `energy_kcal_per_ml` DECIMAL(8,3) NULL COMMENT '能量密度 kcal/ml',
  `protein_g_per_ml` DECIMAL(8,4) NULL COMMENT '蛋白/氨基酸密度 g/ml',
  `match_keywords` VARCHAR(512) NULL COMMENT '医嘱name匹配关键词，逗号分隔',
  `spec_note` VARCHAR(256) NULL COMMENT '规格/剂型备注',
  `source_note` VARCHAR(256) NULL COMMENT '来源：营养科/说明书',
  `status` TINYINT NOT NULL DEFAULT 1,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `ux_config_nutrition_formula_code` (`formula_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='营养制剂配方字典';

INSERT IGNORE INTO `config_nutrition_formula`
  (`id`, `formula_code`, `formula_name`, `route_type`, `energy_kcal_per_ml`, `protein_g_per_ml`, `match_keywords`, `spec_note`, `source_note`) VALUES
  (UUID(), 'ENT_RUINENG',   '瑞能',                                    'ENTERAL',    1.3,   0.0230, '瑞能',                              '肠内,营养科提供值',                          '营养科提供'),
  (UUID(), 'ENT_BAIPULI',  '百普力',                                  'ENTERAL',    1.0,   0.0400, '百普力',                            '短肽型肠内,营养科提供值',                    '营养科提供'),
  (UUID(), 'ENT_RUIDAI',   '瑞代',                                    'ENTERAL',    0.9,   0.0340, '瑞代,短肽',                         '糖尿病适用肠内,营养科提供值',                '营养科提供'),
  (UUID(), 'ENT_KANQUANLI','康全力',                                  'ENTERAL',    0.75,  0.0320, '康全力',                            '营养科提供值',                              '营养科提供'),
  (UUID(), 'ENT_NENGLI',   '能全力',                                  'ENTERAL',    1.0,   0.0400, '能全力',                            '标准整蛋白肠内,营养科提供值',                '营养科提供'),
  (UUID(), 'ENT_RUIXIAN',  '瑞先',                                    'ENTERAL',    1.5,   0.0560, '瑞先',                              '高蛋白肠内,营养科提供值',                    '营养科提供'),
  (UUID(), 'PAR_FAT20',    '脂肪乳(C14~24/中长链20%)',                'PARENTERAL', 2.0,   0.0000, '脂肪乳',                            '20%浓度2.0kcal/ml,不含氨基酸',              '说明书/DailyMed:20%脂肪乳2.0kcal/ml'),
  (UUID(), 'PAR_AA18_85',  '复方氨基酸18AA-II(8.5% 乐凡命)',          'PARENTERAL', 0.34,  0.0850, '18AA-II,乐凡命',                   '250ml:21.25g=0.085g/ml，氨基酸按4kcal/g折算0.34kcal/ml','config_drug 实测规格 250ml:21.25g/瓶'),
  (UUID(), 'PAR_KABIVEN',  '卡文 脂肪乳氨基酸(17)葡萄糖(11%)',       'PARENTERAL', 0.694, 0.0236, '脂肪乳氨基酸(17)葡萄糖,卡文,全合一', '1440ml整袋:约1000kcal/氨基酸34g;整袋密度','说明书:1440ml氨基酸34g/总能量约1000kcal');

-- 旧编码 PAR_AA18_5（复方氨基酸18AA-II(5%)）已被 PAR_AA18_85 取代：
-- 关键词 '氨基酸(18AA' 在真实医嘱名里永不命中，且真实规格为 250ml:21.25g（8.5%），
-- 原按 5% 记会低估约 40%。清理旧行避免误配。
DELETE FROM `config_nutrition_formula` WHERE `formula_code` = 'PAR_AA18_5';
