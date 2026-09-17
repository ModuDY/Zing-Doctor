package com.zing.doctor.module.ards.prone.service;

import com.zing.doctor.module.ards.prone.dto.ArdsProneCellSaveItem;
import com.zing.doctor.module.ards.prone.dto.ArdsProneRecordView;
import com.zing.doctor.module.ards.prone.entity.ArdsProneCellLog;
import com.zing.doctor.module.ards.prone.entity.ArdsProneConfig;
import com.zing.doctor.module.ards.prone.entity.ArdsProneRecord;
import com.zing.doctor.module.ards.prone.entity.ArdsProneTimepoint;
import com.zing.doctor.module.ards.prone.entity.ArdsProneTpTpl;

import java.util.List;
import java.util.Map;

/**
 * ARDS 俯卧位通气治疗记录服务。
 */
public interface ArdsProneService {

    /** 参数键：APACHE II 是否显示（默认显示、全院统一） */
    String KEY_APACHE2_SHOW = "ARDS_PRONE_APACHE2_SHOW";

    /** 参数键：记录编号前缀 */
    String KEY_RECORD_PREFIX = "ARDS_PRONE_RECORD_PREFIX";

    /**
     * 列表：按住院号或科室查询（均为空时查全部）。
     */
    List<ArdsProneRecord> list(String inHospitalNo, String departCode);

    /**
     * 填写页视图：主记录 + 时点 + 单元格矩阵。
     */
    ArdsProneRecordView get(Long id);

    /**
     * 新建一次俯卧位疗程记录：按模板生成时点，带出患者基本信息（含诊断与入院日期）。
     *
     * @param startTime           俯卧位开始时间，格式 yyyy-MM-dd HH:mm:ss
     * @param inHospitalSerialNo  住院流水号（外链参数，用于同一住院号多次入科时定位；可为空）
     * @param inDepartTime        入科时间（外链参数，与流水号共同定位患者内部 id；可为空）
     */
    ArdsProneRecord create(String inHospitalNo, String patientId, String startTime,
                           String inHospitalSerialNo, String inDepartTime);

    /**
     * 按外链参数解析患者（patient_info.id）与基本信息，供新建弹窗自动填充「患者 ID / 诊断 / 入院日期」。
     *
     * <p>定位顺序：住院流水号（仅当 patient_info 存在该列时）→ 住院号 + 入科时间（分钟级相等）
     * → 该住院号最近一次入科。查不到返回 {@code found=false}，不抛异常。
     *
     * @return found / message / patientId / patientName / sex / age / bedCode / departCode /
     *         diagnosis / inDepartTime / admitDate / serialColumnExists
     */
    Map<String, Object> lookupPatient(String inHospitalNo, String inHospitalSerialNo, String inDepartTime);

    /**
     * 保存记录头（患者信息、并发症、终止指征、签名等）。
     */
    ArdsProneRecord saveRecord(ArdsProneRecord record);

    /**
     * 批量保存单元格值；覆盖自动采集 / 检验同步值必须填原因，并写入留痕。
     *
     * @return 实际写入的单元格数
     */
    int saveCells(Long recordId, List<ArdsProneCellSaveItem> items);

    /**
     * 自动采集某个时点：按采集窗口取监护/呼吸机与血气原值，只填空值，不做沿用。
     *
     * @return 统计信息：filled 自动填充项数 / pending 待手工项数
     */
    Map<String, Object> collect(Long recordId, Integer tpIndex, boolean forceRefresh);

    /** 更正留痕 */
    List<ArdsProneCellLog> logs(Long recordId);

    /**
     * 列表页结果指标摘要（批量）：氧合指数 P/F 首末值与趋势、最低驱动压 ΔP、已填项数。
     *
     * @param ids 记录 ID 列表
     * @return 每条记录一行：id / pf0 / pf1 / pfTrend / dpMin / filled / total
     */
    List<Map<String, Object>> summary(List<Long> ids);

    /** 补传文书 PDF（与记录主体保存解耦） */
    boolean attachPdf(Long id, String pdfData, String pdfName);

    /** 逻辑删除记录（历史数据保留） */
    boolean delete(Long id);

    /** 新增时点 */
    List<ArdsProneTimepoint> addTimepoint(Long recordId, String label, Integer offsetMinutes);

    /** 修改时点 */
    List<ArdsProneTimepoint> updateTimepoint(Long tpId, String label, Integer offsetMinutes);

    /** 删除时点（软删除：已填数据保留可查） */
    List<ArdsProneTimepoint> deleteTimepoint(Long tpId);

    /** 恢复默认时点模板 */
    List<ArdsProneTimepoint> resetTimepoints(Long recordId);

    /** 时点模板（科室无模板时回退全院默认） */
    List<ArdsProneTpTpl> tpl(String departCode);

    /** 保存科室时点模板 */
    void saveTpl(String departCode, List<ArdsProneTpTpl> items);

    /** APACHE II 是否显示（参数控制，默认显示） */
    boolean apache2Show();

    // ---------------------------------------------------------- 采集映射配置

    /**
     * 采集映射配置列表（含停用项，供配置页管理）。
     *
     * @param configType observe_item / lis_item，空为全部
     * @param configKey  ARDS 参数编码，空为全部
     */
    List<ArdsProneConfig> listConfig(String configType, String configKey);

    /** 保存映射配置（id 为空新增，否则更新）；保存后解析器缓存立即失效 */
    boolean saveConfig(Map<String, Object> body);

    /** 删除映射配置（物理删除：配置项无历史价值，留痕由上层操作日志承担） */
    boolean deleteConfig(Long id);

    /** 映射配置启用 / 停用 */
    boolean toggleConfig(Long id, Integer status);

    /**
     * 一键用 {@code ArdsProneDict} 内置关键字生成配置（幂等：同参数+同通道+同值跳过）。
     *
     * @return 实际新增条数
     */
    int seedConfig();

    /**
     * 数据元候选（配置页「选择候选」）。
     *
     * @param type      observe 监护字典 / lis 近 7 天实际检验项目
     * @param keyword   编码或名称模糊过滤
     * @param patientId 仅 observe 使用（可空，预留按患者常用项排序）
     */
    List<Map<String, Object>> candidates(String type, String keyword, String patientId, Integer limit);

    /**
     * 试采（dry-run）：与正式采集同一取数与匹配口径，<b>不写库</b>，返回每项命中明细，
     * 供配置页核对「哪个项目映射到哪个值」。
     */
    Map<String, Object> previewCollect(Long recordId, Integer tpIndex);
}
