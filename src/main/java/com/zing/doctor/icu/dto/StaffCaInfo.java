package com.zing.doctor.icu.dto;

import lombok.Data;

/**
 * ICU「人员 CA / 电子签名」记录（只读，来自达梦 {@code zing_icu_db_prod.config_staff_ca_info}）。
 *
 * <p>只取文书需要的字段：工号 / 姓名 / 电子签名图。ICU 侧由人员在 CA 系统录入并签章，
 * 工号（{@code work_no}）与 ICU 外链参数 {@code username} 一致，故前端拿外链工号即可反查签名。
 *
 * <p>{@code signatureImg} 在 ICU 库中是 CLOB，映射为 {@code String}（与医生库
 * {@code apache2_score_record.pdf_data} 同样的处理方式），内容为 base64（通常 GIF/PNG，
 * 不带 {@code data:} 前缀）。
 */
@Data
public class StaffCaInfo {

    /** 工号 */
    private String workNo;

    /** 姓名 */
    private String realname;

    /** 电子签名图 base64（不带 data: 前缀） */
    private String signatureImg;

    /** 状态：1 正常 */
    private String status;

    /** 删除标记：0 正常 */
    private String delFlag;
}
