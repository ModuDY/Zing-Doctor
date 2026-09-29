package com.zing.doctor.module.ards.prone.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * ARDS 俯卧位采集映射配置表（与 config_sofa / config_apache2 同构，追加表达力字段）。
 *
 * <p>一行 = 一条映射规则：把「ARDS 参数项（config_key）」映射到「数据源项目」。
 * 同一参数可配多条规则，取数时按 priority 升序尝试（数字小优先），
 * 同一优先级先监护通道、后检验通道；全部未命中再回退 {@code ArdsProneDict} 内置关键字。
 *
 * <p>config_type 取值：
 * <ul>
 *   <li>{@code observe_item}：监护 / 呼吸机观察项（ICU 库 patient_observe_module_item_record）</li>
 *   <li>{@code lis_item}：检验 / 血气（ICU 库 patient_info_lis_item）</li>
 * </ul>
 *
 * <p>match_type 取值：
 * <ul>
 *   <li>{@code code}：按 item_code / lis_item_code 精确匹配（config_value 逗号分隔多个编码）</li>
 *   <li>{@code name}：按项目名称包含匹配（config_value 逗号分隔多个关键字）</li>
 * </ul>
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"config_prone_item\"")
public class ArdsProneConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 数据源通道：observe_item 监护 / lis_item 检验 */
    private String configType;

    /** ARDS 参数编码（ArdsProneDict 的 key，如 hr / map / peep / pao2） */
    private String configKey;

    /** 匹配值：item_code 列表 或 名称关键字列表（逗号分隔） */
    private String configValue;

    /** 匹配方式：code 精确 / name 包含 */
    private String matchType;

    /** 优先级：数字小优先（同参数多渠道时用于表达「监护优先 / 检验兜底」） */
    private Integer priority;

    /** 覆盖默认采集窗口（分钟），空则走字典默认（±15 / ±60） */
    private Integer windowMin;

    /** 单位线性换算系数：值 × scale + offset（如 FiO₂ 0.4 → 40 时 scale=100） */
    private BigDecimal unitScale;

    /** 单位线性换算偏移量 */
    private BigDecimal unitOffset;

    /** 项目名称（展示用） */
    private String itemName;

    private String remark;

    private Integer sortNo;

    /** 状态：1启用 0停用（停用项不参与取数） */
    private Integer status;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
