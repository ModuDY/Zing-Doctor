package com.zing.doctor.module.deviceinfection.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 导管相关感染确诊录入（VAP / CRBSI / CAUTI）。
 *
 * <p>三型感染共用一套录入表单（按 {@code infectionType} 区分），确诊记录驱动：
 * <ul>
 *   <li>质控导管域指标：quality_24（VAP）/ quality_26（CRBSI）/ quality_425（CAUTI）</li>
 *   <li>规则层发病率：VAP÷通气天数、CRBSI÷导管留置天数、CAUTI÷导尿管留置天数（‰）</li>
 * </ul>
 *
 * <p>口径（《重症医学专业医疗质量控制指标》）：VAP = 使用有创呼吸机期间或停止使用
 * 48h 内新发肺部感染；CRBSI = 留置血管导管期间或拔出 48h 内原发性血流感染；
 * CAUTI = 插管或拔管 48h 内新发尿路感染。诊断依据分「临床诊断 / 病原学确诊」两级，
 * 参照《医院感染诊断标准》。</p>
 */
@Data
@TableName("\"zing_doctor_db_prod\".\"patient_doc_device_infection\"")
public class DeviceInfectionRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String patientId;

    private String inHospitalNo;

    private String patientName;

    private String departCode;

    /** 感染类型：VAP / CRBSI / CAUTI */
    private String infectionType;

    /** 确诊时间（质控口径窗口 = 置入 ~ 拔除 + 48h） */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime confirmTime;

    /** 诊断依据：临床诊断 / 病原学确诊 */
    private String diagnosisBasis;

    /** 临床依据（症状 / 影像 / 检验摘要） */
    private String clinicalEvidence;

    /** 病原学结果（培养 / 涂片 / 分子诊断） */
    private String pathogen;

    /** 关联导管编码（patient_tube.tube_code，可空） */
    private String relatedTubeCode;

    /** 关联导管名称（可空） */
    private String relatedTubeName;

    /** 确诊医师 */
    private String doctorName;

    /** 确诊医师工号 */
    private String doctorWorkNo;

    private String remark;

    /** 状态：1正常 0已删除 */
    private Integer status;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
