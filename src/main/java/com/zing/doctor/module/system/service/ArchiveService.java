package com.zing.doctor.module.system.service;

import java.util.Map;

/**
 * 评分文书归档服务。
 *
 * <p>把 SOFA / APACHE II 的评分文书 PDF 推送到院方归档接口（地址在「参数设置」页面配置），
 * 成功推送后把该条记录标记为已归档。
 */
public interface ArchiveService {

    /** 业务编码：SOFA */
    String BIZ_SOFA = "SOFA";
    /** 业务编码：APACHE II */
    String BIZ_APACHE2 = "APACHE2";

    /**
     * 归档推送：调用院方接口，成功后把记录标记为已归档。
     *
     * @param biz 业务编码 {@link #BIZ_SOFA} / {@link #BIZ_APACHE2}
     * @param id  评分记录 ID
     * @return 接口原始返回（success / message / code），供前端提示
     */
    Map<String, Object> push(String biz, Long id);

    /**
     * 撤销归档标记（只改本地状态，不调用院方接口）。
     */
    void unmark(String biz, Long id);
}
