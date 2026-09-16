package com.zing.doctor.module.system.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zing.doctor.module.apache2.entity.Apache2ScoreRecord;
import com.zing.doctor.module.apache2.mapper.Apache2ScoreRecordMapper;
import com.zing.doctor.module.sofa.entity.SofaScoreRecord;
import com.zing.doctor.module.sofa.mapper.SofaScoreRecordMapper;
import com.zing.doctor.module.system.entity.ArchiveLog;
import com.zing.doctor.module.system.mapper.ArchiveLogMapper;
import com.zing.doctor.module.system.service.ArchiveService;
import com.zing.doctor.module.system.service.SysParamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评分文书归档服务实现。
 *
 * <p>推送参数为接口约定的下划线字段，其中：
 * <ul>
 *   <li>基础 8 项：patient_id / in_hospital_no / patient_name / depart_code / score_time / pdf_data / pdf_name / create_by</li>
 *   <li>归档 3 项：doc_code（sofa|apache2）/ score_date（yyyy-MM-dd）/ file_path（按归档目录规则拼出）</li>
 * </ul>
 * file_path 同时落库评分记录与归档流水表，便于跟踪。
 */
@Slf4j
@Service
public class ArchiveServiceImpl implements ArchiveService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** PDF Base64 可能数 MB，超时放宽到 60 秒 */
    private static final int TIMEOUT_MS = 60_000;

    /** doc_code 固定值：SOFA 页传 sofa，APACHE II 页传 apache2 */
    private static final String DOC_CODE_SOFA = "sofa";
    private static final String DOC_CODE_APACHE2 = "apache2";

    /**
     * 草稿来源：系统自动初评（score_type = auto / daily）。
     *
     * <p>业务口径：自动初评只是系统内部的评估草稿，未经医生复核确认，
     * 不属于「需要归档的文书」——只有医生打开复核并保存过（score_type 转为 custom 等）
     * 的记录才允许推送归档。
     */
    private static final Set<String> DRAFT_SCORE_TYPES = new HashSet<>(Arrays.asList("auto", "daily"));

    /** 归档目录里允许出现的占位符；出现其它 #xxx# 一律视为配置错误 */
    private static final Pattern PLACEHOLDER = Pattern.compile("#[A-Za-z_]+#");

    @Autowired
    private SofaScoreRecordMapper sofaRecordMapper;

    @Autowired
    private Apache2ScoreRecordMapper apache2RecordMapper;

    @Autowired
    private SysParamService sysParamService;

    @Autowired
    private ArchiveLogMapper archiveLogMapper;

    @Override
    public Map<String, Object> push(String biz, Long id) {
        String bizCode = normalizeBiz(biz);
        if (id == null) {
            throw new IllegalArgumentException("记录 ID 不能为空");
        }

        // 1. 取记录主体 + PDF 大字段（pdf_data 默认不随行返回，单独取）
        ArchiveTarget t = loadTarget(bizCode, id);

        // 1.1 自动初评是内部评估草稿，医生未复核保存前不作为文书归档（业务口径）
        if (isDraftScoreType(t.scoreType)) {
            throw new IllegalStateException("自动初评属于系统内部评估草稿，需医生打开复核并保存后才能归档");
        }

        if (!StringUtils.hasText(t.pdfData)) {
            throw new IllegalStateException("该记录还没有评分文书 PDF，请先保存评分生成文书后再归档");
        }

        // 2. 接口地址（参数设置页面配置）
        String url = sysParamService.getArchiveApiUrl();
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("尚未配置文书归档接口地址，请先在「参数设置」页面配置");
        }

        // 3. 归档目录 → file_path（配了 ARCHIVE_DIR 就必须能拼出完整路径，否则拒绝归档）
        String filePath = buildFilePath(t);

        // 4. 组装报文
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("patient_id", t.patientId);
        payload.put("in_hospital_no", t.inHospitalNo);
        payload.put("patient_name", t.patientName);
        payload.put("depart_code", t.departCode);
        payload.put("score_time", t.scoreTime == null ? null : t.scoreTime.format(DT_FMT));
        payload.put("pdf_data", t.pdfData);
        payload.put("pdf_name", t.pdfName);
        payload.put("create_by", t.createBy);
        payload.put("doc_code", t.docCode);
        payload.put("score_date", t.scoreDate);
        payload.put("file_path", filePath);

        log.info("文书归档推送开始: biz={}, id={}, url={}, filePath={}, pdf≈{}KB",
                bizCode, id, url, filePath, t.pdfData.length() * 3 / 4 / 1024);

        int httpStatus;
        String respBody;
        try {
            HttpResponse resp = HttpRequest.post(url)
                    .contentType("application/json")
                    .body(JSONUtil.toJsonStr(payload))
                    .timeout(TIMEOUT_MS)
                    .execute();
            httpStatus = resp.getStatus();
            respBody = resp.body();
        } catch (Exception e) {
            log.error("文书归档接口调用异常: biz={}, id={}, url={}", bizCode, id, url, e);
            writeLog(bizCode, t, url, filePath, "push", false, null, null, "接口调用异常：" + e.getMessage());
            throw new IllegalStateException("归档接口调用失败：" + e.getMessage());
        }

        // 5. 判定成功：success=true 或 code=200（接口文档：{success, message, code:200|500, ...}）
        boolean ok;
        String message = null;
        Integer code = null;
        if (StringUtils.hasText(respBody)) {
            try {
                JSONObject j = JSONUtil.parseObj(respBody);
                Boolean success = j.getBool("success");
                code = j.getInt("code");
                message = j.getStr("message");
                ok = Boolean.TRUE.equals(success) || (code != null && code == 200);
            } catch (Exception e) {
                log.warn("文书归档响应解析失败，按 HTTP 状态判定: httpStatus={}, body={}", httpStatus, abbrev(respBody));
                ok = httpStatus >= 200 && httpStatus < 300;
            }
        } else {
            ok = httpStatus >= 200 && httpStatus < 300;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("httpStatus", httpStatus);
        result.put("code", code);
        result.put("message", message);
        result.put("response", abbrev(respBody));
        result.put("filePath", filePath);
        result.put("docCode", t.docCode);
        result.put("scoreDate", t.scoreDate);

        if (!ok) {
            String err = StringUtils.hasText(message) ? message : ("接口返回 " + httpStatus);
            log.error("文书归档失败: biz={}, id={}, httpStatus={}, body={}", bizCode, id, httpStatus, abbrev(respBody));
            writeLog(bizCode, t, url, filePath, "push", false, httpStatus, code, err);
            result.put("success", false);
            throw new ArchiveException("归档失败：" + err, result);
        }

        // 6. 标记已归档并记录 file_path
        markStatus(bizCode, id, 1, filePath);
        writeLog(bizCode, t, url, filePath, "push", true, httpStatus, code, message);
        log.info("文书归档成功: biz={}, id={}, filePath={}", bizCode, id, filePath);
        result.put("success", true);
        return result;
    }

    @Override
    public void unmark(String biz, Long id) {
        String bizCode = normalizeBiz(biz);
        if (id == null) {
            throw new IllegalArgumentException("记录 ID 不能为空");
        }
        ArchiveTarget t = loadTarget(bizCode, id);
        markStatus(bizCode, id, 0, null);
        // 撤销只动本地状态、不调院方接口，仍记一条流水便于追溯谁在什么时候撤销
        writeLog(bizCode, t, null, t.filePath, "unmark", true, null, null, "撤销归档标记");
        log.info("已撤销归档标记: biz={}, id={}", bizCode, id);
    }

    /**
     * 按归档目录规则拼出 file_path。
     *
     * <p>支持占位符：#in_hospital_no# / #doc_code# / #score_date# / #patient_id# / #patient_name#。
     * 目录未配置、占位符取不到值、出现不支持的占位符，一律拒绝归档并给出原因。
     */
    private String buildFilePath(ArchiveTarget t) {
        String dir = sysParamService.getArchiveDir();
        if (!StringUtils.hasText(dir)) {
            throw new IllegalStateException("尚未配置归档目录，请先在「参数设置」页面配置归档目录");
        }

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("#in_hospital_no#", t.inHospitalNo);
        vars.put("#doc_code#", t.docCode);
        vars.put("#score_date#", t.scoreDate);
        vars.put("#patient_id#", t.patientId);
        vars.put("#patient_name#", t.patientName);

        String path = dir;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            if (!dir.contains(e.getKey())) {
                continue;
            }
            if (!StringUtils.hasText(e.getValue())) {
                throw new IllegalStateException(
                        "归档目录中的占位符 " + e.getKey() + " 取不到值，无法生成完整路径，请检查该条记录的数据是否完整");
            }
            path = path.replace(e.getKey(), e.getValue());
        }

        Matcher m = PLACEHOLDER.matcher(path);
        if (m.find()) {
            throw new IllegalStateException("归档目录含不支持的占位符 " + m.group()
                    + "，仅支持 #in_hospital_no# / #doc_code# / #score_date# / #patient_id# / #patient_name#");
        }

        // 规范化：清理重复斜杠与结尾斜杠（根路径 "/" 保留）
        path = path.replaceAll("/{2,}", "/");
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    /** 读取一条评分记录（含 PDF），并算出 doc_code / score_date */
    private ArchiveTarget loadTarget(String bizCode, Long id) {
        ArchiveTarget t = new ArchiveTarget();
        t.id = id;
        if (BIZ_SOFA.equals(bizCode)) {
            SofaScoreRecord r = sofaRecordMapper.selectById(id);
            if (r == null) throw new IllegalArgumentException("评分记录不存在");
            SofaScoreRecord p = sofaRecordMapper.selectPdfById(id);
            t.patientId = r.getPatientId();
            t.inHospitalNo = r.getInHospitalNo();
            t.patientName = r.getPatientName();
            t.departCode = r.getDepartCode();
            t.scoreTime = r.getScoreTime();
            t.createTime = r.getCreateTime();
            t.createBy = r.getCreateBy();
            t.scoreType = r.getScoreType();
            t.pdfName = r.getPdfName();
            t.filePath = r.getFilePath();
            t.pdfData = p == null ? null : p.getPdfData();
        } else {
            Apache2ScoreRecord r = apache2RecordMapper.selectById(id);
            if (r == null) throw new IllegalArgumentException("评分记录不存在");
            Apache2ScoreRecord p = apache2RecordMapper.selectPdfById(id);
            t.patientId = r.getPatientId();
            t.inHospitalNo = r.getInHospitalNo();
            t.patientName = r.getPatientName();
            t.departCode = r.getDepartCode();
            t.scoreTime = r.getScoreTime();
            t.createTime = r.getCreateTime();
            t.createBy = r.getCreateBy();
            t.scoreType = r.getScoreType();
            t.pdfName = r.getPdfName();
            t.filePath = r.getFilePath();
            t.pdfData = p == null ? null : p.getPdfData();
        }
        t.docCode = BIZ_SOFA.equals(bizCode) ? DOC_CODE_SOFA : DOC_CODE_APACHE2;
        // score_date 取 score_time 的日期部分；score_time 缺失时退到记录创建时间，再缺失才取当天
        LocalDateTime base = t.scoreTime != null ? t.scoreTime : t.createTime;
        if (base == null) {
            base = LocalDateTime.now();
        }
        t.scoreDate = base.format(DATE_FMT);
        return t;
    }

    /** 只更新归档状态与路径，避免覆盖评分内容；path 为 null 时表示撤销标记（不改动已存路径） */
    private void markStatus(String bizCode, Long id, int status, String path) {
        LocalDateTime now = LocalDateTime.now();
        if (BIZ_SOFA.equals(bizCode)) {
            SofaScoreRecord upd = new SofaScoreRecord();
            upd.setId(id);
            upd.setArchiveStatus(status);
            upd.setArchiveTime(status == 1 ? now : null);
            if (path != null) {
                upd.setFilePath(path);
            }
            upd.setUpdateTime(now);
            sofaRecordMapper.updateById(upd);
        } else {
            Apache2ScoreRecord upd = new Apache2ScoreRecord();
            upd.setId(id);
            upd.setArchiveStatus(status);
            upd.setArchiveTime(status == 1 ? now : null);
            if (path != null) {
                upd.setFilePath(path);
            }
            upd.setUpdateTime(now);
            apache2RecordMapper.updateById(upd);
        }
    }

    /** 写归档流水；失败不影响主流程（流水只是留痕） */
    private void writeLog(String bizCode, ArchiveTarget t, String url, String filePath,
                          String opType, boolean success, Integer httpStatus, Integer respCode, String message) {
        try {
            ArchiveLog rec = new ArchiveLog();
            rec.setBiz(bizCode);
            rec.setRecordId(t.id);
            rec.setInHospitalNo(t.inHospitalNo);
            rec.setPatientName(t.patientName);
            rec.setDocCode(t.docCode);
            rec.setScoreDate(t.scoreDate);
            rec.setFilePath(filePath);
            rec.setApiUrl(url);
            rec.setOpType(opType);
            rec.setSuccess(success ? 1 : 0);
            rec.setHttpStatus(httpStatus);
            rec.setRespCode(respCode);
            rec.setRespMessage(message == null ? null : message.substring(0, Math.min(message.length(), 900)));
            rec.setOperator(t.createBy);
            rec.setCreateTime(LocalDateTime.now());
            archiveLogMapper.insert(rec);
        } catch (Exception e) {
            log.error("归档流水写入失败（不影响归档主流程）: biz={}, recordId={}", bizCode, t.id, e);
        }
    }

    /** 是否系统自动初评草稿：未经医生确认的评估不作为文书归档 */
    private boolean isDraftScoreType(String scoreType) {
        return scoreType != null && DRAFT_SCORE_TYPES.contains(scoreType.trim().toLowerCase());
    }

    private String normalizeBiz(String biz) {
        if (!StringUtils.hasText(biz)) {
            throw new IllegalArgumentException("业务类型不能为空");
        }
        String b = biz.trim().toUpperCase();
        if (BIZ_SOFA.equals(b)) {
            return BIZ_SOFA;
        }
        // 前端/历史写法可能传 APACHEII / APACHE_II / APACHE-II，统一归一化
        if (BIZ_APACHE2.equals(b) || "APACHEII".equals(b) || "APACHE_II".equals(b) || "APACHE-II".equals(b)) {
            return BIZ_APACHE2;
        }
        throw new IllegalArgumentException("不支持的业务类型: " + biz);
    }

    private String abbrev(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }

    /** 一次归档所需的记录快照 */
    private static class ArchiveTarget {
        Long id;
        String patientId;
        String inHospitalNo;
        String patientName;
        String departCode;
        LocalDateTime scoreTime;
        LocalDateTime createTime;
        String createBy;
        String pdfData;
        String pdfName;
        String filePath;
        String docCode;
        String scoreDate;
        /** 记录来源：auto / daily 为系统自动初评草稿，custom 等为医生确认过 */
        String scoreType;
    }

    /** 归档失败时携带接口原始返回，便于前端提示 */
    public static class ArchiveException extends RuntimeException {
        private final Map<String, Object> detail;

        public ArchiveException(String message, Map<String, Object> detail) {
            super(message);
            this.detail = detail;
        }

        public Map<String, Object> getDetail() {
            return detail;
        }
    }
}
