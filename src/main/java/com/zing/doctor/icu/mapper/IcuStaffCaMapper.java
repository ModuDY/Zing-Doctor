package com.zing.doctor.icu.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.zing.doctor.icu.dto.StaffCaInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * ICU 只读「人员 CA / 电子签名」Mapper（达梦 schema：zing_icu_db_prod）。
 *
 * <p>数据表 {@code config_staff_ca_info} 由 ICU 侧 CA 系统维护，本系统只读（数据源 read-only: true），
 * 用于给评分文书（APACHE II / SOFA）的「评分医师」补上电子签名图。
 *
 * <p>达梦方言注意事项：
 * <ul>
 *   <li>标识符用双引号保留小写："{@code zing_icu_db_prod}"."config_staff_ca_info"；</li>
 *   <li>列别名也用双引号写成驼峰（与医生库 {@code selectPdfById} 同样的写法），
 *       避免不同 CASE_SENSITIVE 配置下列名映射不一致；</li>
 *   <li>{@code status} / {@code del_flag} 用 {@code CAST(... AS VARCHAR(8))} 统一成字符串：
 *       ICU 侧这两列在不同版本可能是 TINYINT/INT 或 CHAR，直接映射成数值遇到非数字值会抛错，
 *       统一成字符串后在 Java 端比较，最稳。</li>
 * </ul>
 */
@DS("icu")
@Mapper
public interface IcuStaffCaMapper {

    /**
     * 按工号取 CA 记录（可能多条：历史签章 / 失效记录）。
     *
     * <p>刻意不在 SQL 里过滤 status / del_flag：这两列类型在不同 ICU 版本不一致，
     * 放进 WHERE 会引入隐式转换风险；记录数极少，改为全部取回后在 Service 端择优。
     *
     * @param workNo 工号（外链参数 username）
     * @return CA 记录列表（无记录返回空列表）
     */
    @Select("SELECT \"work_no\" AS \"workNo\", \"realname\" AS \"realname\", "
            + "\"signature_img\" AS \"signatureImg\", "
            + "CAST(\"status\" AS VARCHAR(8)) AS \"status\", "
            + "CAST(\"del_flag\" AS VARCHAR(8)) AS \"delFlag\" "
            + "FROM \"zing_icu_db_prod\".\"config_staff_ca_info\" "
            + "WHERE \"work_no\" = #{workNo}")
    List<StaffCaInfo> selectByWorkNo(@Param("workNo") String workNo);
}
