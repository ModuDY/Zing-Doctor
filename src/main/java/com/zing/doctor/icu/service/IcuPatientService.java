package com.zing.doctor.icu.service;

import com.zing.doctor.icu.dto.IcuPatientAssessment;
import com.zing.doctor.icu.dto.IcuPatientBrief;
import com.zing.doctor.icu.dto.WorkbenchPatient;

import java.util.List;
import java.util.Map;

/**
 * ICU 数据访问适配层（只读 zing_icu_db_prod）。
 *
 * <p>第一维度所需的数据契约。P0 使用 {@code mock} 实现打通流程；
 * 待 ICU 系统表结构提供后，新增 SQL 实现（@DS("icu")）并切换
 * application.yml 中 {@code zing.doctor.icu-data-provider=sql} 即可，接口保持不变。
 */
public interface IcuPatientService {

    /**
     * ICU 在科患者工作台（仅基础信息，不聚合检验/医嘱大表）
     *
     * @param departCode 科室编码，取 sys_depart.org_code；null / 空 / "ALL" 均表示不限科室。
     *                   注意不要传 ward_name（病区名）：那与 org_code 不是同一套编码。
     */
    List<WorkbenchPatient> listInpatients(String departCode);

    /** 疑似感染/脓毒症患者列表（可按感染类型等条件扩展） */
    List<IcuPatientBrief> listSuspectInfections();

    /**
     * 单患者基础信息（轻量，只查患者主表，不跑检验/微生物等关联查询）。
     * 供保存决策等仅需患者基本信息（如 patientNo）的场景使用，避免触发全院列表查询。
     * 未找到返回 null。
     */
    IcuPatientBrief getPatientBrief(String patientId);

    /** 单患者抗感染决策评估数据 */
    IcuPatientAssessment getAssessment(String patientId);

    /**
     * 按住院号（ICU 外链参数 inHospitalNo）解析患者内部 ID（patient_info.id）。
     * 未找到返回 null。默认兜底：住院号即患者标识（兼容 mock 场景）。
     */
    default String resolvePatientIdByInHospitalNo(String inHospitalNo) {
        return inHospitalNo;
    }

    /**
     * 职工字典搜索（医生下拉框用）。
     * 过滤：user_id 非空、status=1、del_flag=0。
     * 搜索：pinyin 首字母（不区分大小写）/ work_no 工号 / realname 姓名，任一匹配。
     * 返回 List&lt;Map&gt;，key：user_id / realname / pinyin / work_no / depart_name。
     */
    default List<Map<String, Object>> searchStaff(String keyword) {
        return java.util.Collections.emptyList();
    }
}
