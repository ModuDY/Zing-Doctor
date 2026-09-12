package com.zing.doctor.icu.service;

import java.util.Map;

/**
 * 人员电子签名 Service（文书签名用）。
 *
 * <p>数据源：ICU 只读库 {@code zing_icu_db_prod.config_staff_ca_info}，按工号查询。
 */
public interface StaffSignatureService {

    /**
     * 按工号取电子签名。
     *
     * <p><b>取不到时不抛异常</b>：无该工号 / 未配签名 / ICU 库暂时不可用，一律返回
     * {@code found=false} —— 签名属于文书锦上添花，不能因为查签名失败而让医生开不出文书。
     *
     * @param workNo 工号（ICU 外链参数 username）
     * @return { workNo, found, realname, signatureImg }；signatureImg 为 base64（不带 data: 前缀）
     */
    Map<String, Object> getByWorkNo(String workNo);
}
