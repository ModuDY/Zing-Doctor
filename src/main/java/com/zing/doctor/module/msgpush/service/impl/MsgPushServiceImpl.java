package com.zing.doctor.module.msgpush.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.zing.doctor.module.msgpush.MsgPushKeys;
import com.zing.doctor.module.msgpush.dto.MsgPushTestRequest;
import com.zing.doctor.module.msgpush.dto.MsgPushTestResult;
import com.zing.doctor.module.msgpush.entity.MsgPushLog;
import com.zing.doctor.module.msgpush.mapper.MsgPushLogMapper;
import com.zing.doctor.module.msgpush.service.MsgPushService;
import com.zing.doctor.module.system.service.SysParamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 消息推送实现。
 *
 * <p>报文分两级渲染，这是为了现场能自己调通：
 * <ol>
 *   <li>msgHeader / msgBody 片段：用 #变量# 渲染，变量值做 XML 转义；</li>
 *   <li>信封模板：把两个片段原样嵌进去（#msgHeader# / #msgBody#），
 *       现场要改成 SOAP 就把信封换成 soap:Envelope 即可，不必改代码。</li>
 * </ol>
 * 两级顺序不能反 —— 先嵌片段再渲染变量，会把片段里的 #xx# 当成待替换变量二次处理。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MsgPushServiceImpl implements MsgPushService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 占位符：#变量名#（与 ARCHIVE_DIR 的 #占位符# 风格一致） */
    private static final Pattern PH = Pattern.compile("#([A-Za-z0-9_]+)#");

    /** 信封里的片段占位符（#msgHeader# / #msgBody#） */
    private static final Pattern FRAGMENT = Pattern.compile("#(msgHeader|msgBody)#");

    /**
     * 平台对授权码（msgHeader 的 certificate）的格式要求：32 位 UUID、不含横杠。
     *
     * <p>不符合就不发：网关只会回「无效的授权码: 授权码格式不正确，应为32位UUID(不包含横杠)」，
     * 而且它是 HTTP 200，返回成功标识没配时会被误判成发送成功。</p>
     */
    private static final Pattern CERT32 = Pattern.compile("[0-9a-fA-F]{32}");

    /** msg_push_log.resp_body 是 VARCHAR(2000)，留出余量 */
    private static final int RESP_KEEP = 1900;
    /** msg_push_log.content 是 VARCHAR(2000) */
    private static final int CONTENT_KEEP = 1900;
    /** request_body 是 CLOB，只做防御性截断 */
    private static final int REQUEST_KEEP = 20000;

    private static final int DEFAULT_TIMEOUT_MS = 5000;
    private static final String DEFAULT_TEST_CONTENT =
            "【重症预警联调测试】这是一条测试消息，收到即说明集成平台消息通道已通。";

    private final SysParamService sysParamService;
    private final MsgPushLogMapper logMapper;

    @Override
    public MsgPushTestResult testSend(MsgPushTestRequest req, String operator) {
        String url = sysParamService.value(MsgPushKeys.KEY_URL);
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("尚未配置推送接口地址，请先在「参数设置 → 消息推送」填写 MSGPUSH_URL");
        }
        String headerTpl = sysParamService.value(MsgPushKeys.KEY_HEADER_XML);
        String bodyTpl = sysParamService.value(MsgPushKeys.KEY_BODY_XML);
        if (!StringUtils.hasText(bodyTpl)) {
            throw new IllegalStateException("尚未配置 msgBody 报文模板，请先在「参数设置 → 消息推送」填写 MSGPUSH_BODY_XML");
        }
        String envelope = sysParamService.value(MsgPushKeys.KEY_ENVELOPE);
        if (!StringUtils.hasText(envelope)) {
            envelope = MsgPushKeys.DEFAULT_ENVELOPE;
        }

        List<String> mobiles = mobilesOf(req.getMobiles());
        if (mobiles.isEmpty()) {
            throw new IllegalStateException("没有接收号码：请在弹窗里填写，或先配置参数 MSGPUSH_RECEIVERS");
        }

        String content = StringUtils.hasText(req.getContent()) ? req.getContent() : DEFAULT_TEST_CONTENT;
        // 演练模式默认开（参数默认值即 1）：现场必须先看清报文再决定真发
        boolean dryRun = sysParamService.bool(MsgPushKeys.KEY_DRY_RUN, true)
                && !Boolean.TRUE.equals(req.getForceReal());

        // 授权码（certificate）不合格就不发：平台只认 32 位 UUID（不含横杠），填错时网关返回
        // 「无效的授权码: 授权码格式不正确，应为32位UUID(不包含横杠)」，且是 HTTP 200 ——
        // 与其让它跑一趟再被误记成成功，不如在本地就把话说清楚。演练模式不发，不拦。
        if (!dryRun) {
            String cert = str(sysParamService.value(MsgPushKeys.KEY_CERTIFICATE)).trim();
            if (!CERT32.matcher(cert).matches()) {
                throw new IllegalStateException("授权码（certificate）不符合平台要求：需 32 位 UUID、不含横杠"
                        + "（带横杠的 UUID 请去掉横杠再填）。当前值："
                        + (cert.isEmpty() ? "（空）" : cert)
                        + "。请在「参数设置 → 消息推送 → 授权码 certificate」填入平台分配的值"
                        + "（注意它不是 hipToken，不能填同一串）");
            }
        }

        MsgPushTestResult result = new MsgPushTestResult();
        result.setDryRun(dryRun);
        result.setUrl(url.trim());

        for (String mobile : mobiles) {
            result.getItems().add(sendOne(url.trim(), headerTpl, bodyTpl, envelope, mobile, content,
                    MsgPushKeys.SCENE_TEST, null, null, null, null, null, dryRun, operator));
        }
        log.info("消息推送测试发送完成: url={}, 号码数={}, dryRun={}, operator={}",
                url, mobiles.size(), dryRun, operator);
        return result;
    }

    // ------------------------------------------------------------------
    // 发送与审计
    // ------------------------------------------------------------------

    private MsgPushTestResult.Item sendOne(String url, String headerTpl, String bodyTpl, String envelope,
                                           String mobile, String content, String scene, String ruleCode,
                                           String patientId, String patientName, String bedNo, String deptCode,
                                           boolean dryRun, String operator) {
        long start = System.currentTimeMillis();
        Map<String, String> vars = vars(mobile, content, ruleCode, patientId, patientName, bedNo, deptCode);
        String requestBody = buildRequest(headerTpl, bodyTpl, envelope, vars);

        MsgPushTestResult.Item item = new MsgPushTestResult.Item();
        item.setMobile(mobile);
        item.setRequestBody(requestBody);

        MsgPushLog row = new MsgPushLog();
        row.setScene(scene);
        row.setRuleCode(ruleCode);
        row.setPatientId(patientId);
        row.setPatientName(patientName);
        row.setBedNo(bedNo);
        row.setDeptCode(deptCode);
        row.setMobile(mobile);
        row.setContent(abbrev(content, CONTENT_KEEP));
        row.setUrl(abbrev(url, 500));
        row.setDryRun(dryRun ? 1 : 0);
        row.setRequestBody(abbrev(requestBody, REQUEST_KEEP));
        row.setOperator(operator);
        row.setCreateTime(LocalDateTime.now());

        if (dryRun) {
            // 演练：报文照常渲染并留痕，只是不发出去 —— 现场先对着报文核对字段
            item.setSuccess(true);
            item.setResponse("演练模式：报文已生成，未真正发出");
            row.setSuccess(1);
            row.setRespBody(item.getResponse());
        } else {
            try {
                HttpResponse resp = execute(url, requestBody);
                int status = resp.getStatus();
                String respBody = resp.body();
                boolean ok = judgeSuccess(respBody, status);
                item.setHttpStatus(status);
                item.setSuccess(ok);
                item.setResponse(abbrev(respBody, RESP_KEEP));
                row.setHttpStatus(status);
                row.setSuccess(ok ? 1 : 0);
                row.setRespBody(abbrev(respBody, RESP_KEEP));
                if (!ok) {
                    log.warn("消息推送返回判定为失败: url={}, mobile={}, httpStatus={}, body={}",
                            url, mobile, status, abbrev(respBody, 300));
                }
            } catch (Exception e) {
                log.error("消息推送调用异常: url={}, mobile={}", url, mobile, e);
                item.setSuccess(false);
                item.setError(e.getMessage());
                row.setSuccess(0);
                row.setErrorMsg(abbrev(e.getMessage(), 900));
            }
        }

        item.setDurationMs(System.currentTimeMillis() - start);
        row.setDurationMs((int) item.getDurationMs());
        try {
            logMapper.insert(row);
        } catch (Exception e) {
            // 审计失败不能吞掉发送结果：现场要看的是通道通不通
            log.error("消息推送审计写入失败: mobile={}", mobile, e);
        }
        return item;
    }

    private HttpResponse execute(String url, String requestBody) {
        String method = sysParamService.value(MsgPushKeys.KEY_METHOD);
        String contentType = sysParamService.value(MsgPushKeys.KEY_CONTENT_TYPE);
        String soapAction = sysParamService.value(MsgPushKeys.KEY_SOAP_ACTION);

        HttpRequest httpReq = "GET".equalsIgnoreCase(method == null ? "" : method.trim())
                ? HttpRequest.get(url).body(requestBody)
                : HttpRequest.post(url).body(requestBody);
        httpReq.charset("UTF-8").timeout(timeoutMs());
        if (StringUtils.hasText(contentType)) {
            httpReq.contentType(contentType.trim());
        }
        if (StringUtils.hasText(soapAction)) {
            httpReq.header("SOAPAction", soapAction.trim());
        }
        for (Map.Entry<String, String> e : extraHeaders().entrySet()) {
            httpReq.header(e.getKey(), e.getValue());
        }
        return httpReq.execute();
    }

    /**
     * 成功判定：配了「返回成功标识」就按标识匹配；否则按 HTTP 2xx。
     * 医院网关的返回体是自定义 XML，判定规则只能由现场定，不能写死在代码里。
     */
    private boolean judgeSuccess(String respBody, int httpStatus) {
        String key = sysParamService.value(MsgPushKeys.KEY_SUCCESS_KEY);
        if (StringUtils.hasText(key)) {
            return respBody != null && respBody.contains(key.trim());
        }
        return httpStatus >= 200 && httpStatus < 300;
    }

    // ------------------------------------------------------------------
    // 报文渲染
    // ------------------------------------------------------------------

    private String buildRequest(String headerTpl, String bodyTpl, String envelope, Map<String, String> vars) {
        String head = render(headerTpl, vars);
        String body = render(bodyTpl, vars);
        // 信封里的 #msgHeader# / #msgBody# 是未定义占位符，render 会原样保留，这里再原样嵌入。
        // 必须一趟扫完：若分两次 String.replace，先把 header 嵌进去后，header 文本里万一含
        // #msgBody# 就会被第二次替换命中（报文被串改，且很难查）。
        String env = render(envelope, vars);
        Matcher m = FRAGMENT.matcher(env);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String rep = MsgPushKeys.PH_HEADER.equals(m.group(1)) ? head : body;
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 渲染 #变量#；未定义的占位符原样保留（信封引用片段靠的正是这一点）。 */
    private String render(String tpl, Map<String, String> vars) {
        if (tpl == null || tpl.isEmpty()) {
            return "";
        }
        Matcher m = PH.matcher(tpl);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String val = vars.get(m.group(1));
            String rep = val == null ? m.group(0) : xmlEscape(val);
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * XML 文本节点转义。
     *
     * <p>接口文档要求「内容含特殊字符须用 CDATA」，转义后 XML 同样可正常解析、解析结果一致，
     * 且不会与模板里现场自己写的 CDATA 嵌套（CDATA 内的 ]]> 无法转义，是常见的坑）。</p>
     */
    private static String xmlEscape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&apos;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    private Map<String, String> vars(String mobile, String content, String ruleCode,
                                     String patientId, String patientName, String bedNo, String deptCode) {
        Map<String, String> v = new LinkedHashMap<>();
        // ---- 平台要求不可重复的 ID：每次发送重新生成 ----
        // hipMessageNo / msgUuid / convertId 是 32 位 UUID 去横杠；reqUuid 是带横杠的 36 位
        v.put("hipMessageNo", uuid32());
        v.put("msgUuid", uuid32());
        v.put("convertId", uuid32());
        v.put("reqUuid", UUID.randomUUID().toString());
        v.put("msgUuidOrion", "orion_" + uuid32());
        v.put("stepNo", "0");

        // ---- 凭证与身份 ----
        v.put("hipToken", str(sysParamService.value(MsgPushKeys.KEY_HIP_TOKEN)));
        v.put("certificate", str(sysParamService.value(MsgPushKeys.KEY_CERTIFICATE)));
        v.put("callOperator", str(sysParamService.value(MsgPushKeys.KEY_CALL_OPERATOR)));
        v.put("districtCode", str(sysParamService.value(MsgPushKeys.KEY_DISTRICT_CODE)));
        v.put("serverName", MsgPushKeys.SERVER_NAME);

        // ---- 平台报文头里按厂家分配的字段（真实示例中逐项对应）----
        v.put("serviceId", str(sysParamService.value(MsgPushKeys.KEY_SERVICE_ID)));
        v.put("format", str(sysParamService.value(MsgPushKeys.KEY_FORMAT)));
        v.put("senderCode", str(sysParamService.value(MsgPushKeys.KEY_SENDER_CODE)));
        v.put("receivedCode", str(sysParamService.value(MsgPushKeys.KEY_RECEIVED_CODE)));
        v.put("targetType", str(sysParamService.value(MsgPushKeys.KEY_TARGET_TYPE)));
        v.put("convertType", str(sysParamService.value(MsgPushKeys.KEY_CONVERT_TYPE)));
        v.put("receiverServer", str(sysParamService.value(MsgPushKeys.KEY_RECEIVER_SERVER)));
        v.put("receiverUrl", str(sysParamService.value(MsgPushKeys.KEY_RECEIVER_URL)));
        v.put("senderOrgCode", str(sysParamService.value(MsgPushKeys.KEY_SENDER_ORG_CODE)));
        v.put("receivedOrgCode", str(sysParamService.value(MsgPushKeys.KEY_RECEIVED_ORG_CODE)));
        // visitorIp：平台按它记录来访来源。留空则取本机 IP，现场不用为这个专门配参数
        String visitorIp = str(sysParamService.value(MsgPushKeys.KEY_VISITOR_IP));
        v.put("visitorIp", visitorIp.isEmpty() ? localIp() : visitorIp);

        // ---- 业务字段 ----
        v.put("hipAction", str(sysParamService.value(MsgPushKeys.KEY_ACTION)));
        v.put("mobile", str(mobile));
        v.put("startTime", LocalDateTime.now().format(DT));
        v.put("content", content == null ? "" : content);
        v.put("patientId", str(patientId));
        v.put("patientName", str(patientName));
        v.put("bedNo", str(bedNo));
        v.put("deptCode", str(deptCode));
        v.put("ruleName", str(ruleCode));
        return v;
    }

    /** 32 位 UUID（去横杠）：平台要求 hipMessageNo / msgUuid / convertId 不可重复。 */
    private static String uuid32() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 本机 IP：作为 visitorIp 的兜底。
     *
     * <p>取不到就返回空串（而不是抛异常或写死 127.0.0.1）—— visitorIp 只是平台侧
     * 的来访记录字段，不该因为取不到 IP 就让整条短信发不出去。</p>
     */
    private static String localIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.debug("取本机 IP 失败，visitorIp 留空", e);
            return "";
        }
    }

    // ------------------------------------------------------------------
    // 参数解析
    // ------------------------------------------------------------------

    /** 号码来源：弹窗入参优先，其次参数 MSGPUSH_RECEIVERS；支持中文逗号与分号分隔。 */
    private List<String> mobilesOf(String fromRequest) {
        String raw = StringUtils.hasText(fromRequest)
                ? fromRequest
                : sysParamService.value(MsgPushKeys.KEY_RECEIVERS);
        List<String> out = new ArrayList<>();
        if (!StringUtils.hasText(raw)) {
            return out;
        }
        for (String s : raw.split("[,，;；/\\s]+")) {
            String t = s.trim();
            if (!t.isEmpty() && !out.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    /** 附加请求头：一行一个 Key: Value，# 开头的行按注释跳过。 */
    private Map<String, String> extraHeaders() {
        Map<String, String> out = new LinkedHashMap<>();
        String raw = sysParamService.value(MsgPushKeys.KEY_HEADERS);
        if (!StringUtils.hasText(raw)) {
            return out;
        }
        for (String line : raw.split("\\r?\\n")) {
            String s = line.trim();
            if (s.isEmpty() || s.startsWith("#")) {
                continue;
            }
            int i = s.indexOf(':');
            if (i <= 0) {
                continue;
            }
            String k = s.substring(0, i).trim();
            String val = s.substring(i + 1).trim();
            if (!k.isEmpty()) {
                out.put(k, val);
            }
        }
        return out;
    }

    private int timeoutMs() {
        String v = sysParamService.value(MsgPushKeys.KEY_TIMEOUT_MS);
        if (!StringUtils.hasText(v)) {
            return DEFAULT_TIMEOUT_MS;
        }
        try {
            int n = Integer.parseInt(v.trim());
            return n < 500 ? 500 : Math.min(n, 60000);
        } catch (NumberFormatException e) {
            return DEFAULT_TIMEOUT_MS;
        }
    }

    /**
     * 截断到 max 个字符（含后缀）。
     * 注意后缀也算长度：早先写成 substring(0, max) + 后缀，结果比列宽多出几个字符，
     * 审计这一列（如 url VARCHAR(500)）会直接插入失败、把发送结果一并吞掉。
     */
    private static String abbrev(String s, int max) {
        if (s == null) {
            return null;
        }
        if (s.length() <= max) {
            return s;
        }
        String suffix = "…(已截断)";
        int keep = Math.max(0, max - suffix.length());
        return s.substring(0, Math.min(keep, s.length())) + suffix;
    }

    private static String str(String s) {
        return s == null ? "" : s.trim();
    }
}
