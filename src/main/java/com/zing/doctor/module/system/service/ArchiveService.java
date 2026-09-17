package com.zing.doctor.module.system.service;

import java.util.Map;

/**
 * 文书归档服务。
 *
 * <p>把 SOFA / APACHE II / ARDS 俯卧位治疗记录的文书 PDF 推送到院方归档接口
 * （地址在「参数设置」页面配置），成功推送后把该条记录标记为已归档。
 *
 * <p>三种业务走<b>同一个接口、同一套传参</b>，仅 doc_code 不同（sofa / apache2 / ARDS_PRONE_REC）。
 */
public interface ArchiveService {

    /** 业务编码：SOFA */
    String BIZ_SOFA = "SOFA";
    /** 业务编码：APACHE II */
    String BIZ_APACHE2 = "APACHE2";
    /** 业务编码：ARDS 俯卧位通气治疗记录 */
    String BIZ_ARDS_PRONE = "ARDS_PRONE";

    /**
     * 归档推送：调用院方接口，成功后把记录标记为已归档。
     *
     * <p>ARDS 俯卧位记录具备幂等：已归档成功再次推送时不重复推送、不产生重复文档
     * （设计方案 SR-19）。失败的记录可重新推送。
     *
     * @param biz 业务编码 {@link #BIZ_SOFA} / {@link #BIZ_APACHE2} / {@link #BIZ_ARDS_PRONE}
     * @param id  记录 ID
     * @return 接口原始返回（success / message / code），供前端提示
     */
    Map<String, Object> push(String biz, Long id);

    /**
     * 撤销归档标记（只改本地状态，不调用院方接口）。
     */
    void unmark(String biz, Long id);
}
