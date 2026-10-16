package com.zing.doctor.module.msgpush;

/**
 * 消息推送（高风险预警多通道）参数键与占位符约定。
 *
 * <p>参数全部落在「参数设置 → 消息推送」分组（sys_param.param_group = msgpush），
 * 由现场按医院集成平台实际情况调整，改完点「测试发送」即可联调，不需要改代码发版。</p>
 */
public final class MsgPushKeys {

    private MsgPushKeys() {
    }

    /** 参数分组编码 */
    public static final String GROUP = "msgpush";

    /** 总开关；关闭时「测试发送」仍可用 */
    public static final String KEY_ENABLED = "MSGPUSH_ENABLED";
    /** 演练模式：只渲染并记录报文，不真正发出 */
    public static final String KEY_DRY_RUN = "MSGPUSH_DRY_RUN";
    /** 集成平台接口地址 */
    public static final String KEY_URL = "MSGPUSH_URL";
    /** 请求方法 POST / GET */
    public static final String KEY_METHOD = "MSGPUSH_METHOD";
    /** Content-Type */
    public static final String KEY_CONTENT_TYPE = "MSGPUSH_CONTENT_TYPE";
    /** SOAPAction（留空则不发送该请求头） */
    public static final String KEY_SOAP_ACTION = "MSGPUSH_SOAP_ACTION";
    /** 附加请求头，一行一个 Key: Value */
    public static final String KEY_HEADERS = "MSGPUSH_HEADERS";
    /** msgHeader 片段模板 */
    public static final String KEY_HEADER_XML = "MSGPUSH_HEADER_XML";
    /** msgBody 片段模板 */
    public static final String KEY_BODY_XML = "MSGPUSH_BODY_XML";
    /** 请求体信封模板（把两个片段包成最终报文；支持 #msgHeader# / #msgBody#） */
    public static final String KEY_ENVELOPE = "MSGPUSH_ENVELOPE";
    /** hipToken（现场分发） */
    public static final String KEY_HIP_TOKEN = "MSGPUSH_HIP_TOKEN";
    /** 消息类型代码 hipAction */
    public static final String KEY_ACTION = "MSGPUSH_ACTION";
    /** 调用方标识 callOperator */
    public static final String KEY_CALL_OPERATOR = "MSGPUSH_CALL_OPERATOR";
    /** 平台凭证 certificate */
    public static final String KEY_CERTIFICATE = "MSGPUSH_CERTIFICATE";
    /** 区划代码（apply / exec 共用） */
    public static final String KEY_DISTRICT_CODE = "MSGPUSH_DISTRICT_CODE";

    // ---- 以下 11 项来自平台提供的真实成功示例（他厂家报文），平台按厂家分配 ----
    /** 发送方代码 senderCode（示例为体检系统 PE，本系统需现场分配） */
    public static final String KEY_SENDER_CODE = "MSGPUSH_SENDER_CODE";
    /** 接收方代码 receivedCode（示例为 MP） */
    public static final String KEY_RECEIVED_CODE = "MSGPUSH_RECEIVED_CODE";
    /** 目标类型 targetType（示例 1） */
    public static final String KEY_TARGET_TYPE = "MSGPUSH_TARGET_TYPE";
    /** 转换类型 convertType（示例 0） */
    public static final String KEY_CONVERT_TYPE = "MSGPUSH_CONVERT_TYPE";
    /** 数据格式 format（示例 HL7V3；注意不是 "xml"） */
    public static final String KEY_FORMAT = "MSGPUSH_FORMAT";
    /** 服务标识 serviceId（示例与 serverName 同值） */
    public static final String KEY_SERVICE_ID = "MSGPUSH_SERVICE_ID";
    /** 调用方 IP visitorIp；留空则自动取本机 IP（平台按此记录来访来源） */
    public static final String KEY_VISITOR_IP = "MSGPUSH_VISITOR_IP";
    /** 接收服务 receiverServer（示例为字符串 null，不是空节点） */
    public static final String KEY_RECEIVER_SERVER = "MSGPUSH_RECEIVER_SERVER";
    /** 接收地址 receiverUrl（示例为字符串 null） */
    public static final String KEY_RECEIVER_URL = "MSGPUSH_RECEIVER_URL";
    /** 发送方机构码 senderOrgCode（示例为空，可留空） */
    public static final String KEY_SENDER_ORG_CODE = "MSGPUSH_SENDER_ORG_CODE";
    /** 接收方机构码 receivedOrgCode（示例为空，可留空） */
    public static final String KEY_RECEIVED_ORG_CODE = "MSGPUSH_RECEIVED_ORG_CODE";

    /** 接收号码，逗号分隔（测试发送用；预警推送留空时也回退到这里） */
    public static final String KEY_RECEIVERS = "MSGPUSH_RECEIVERS";
    /**
     * 预警推送的接收号码，逗号分隔。
     *
     * <p>与 {@link #KEY_RECEIVERS} 分开是为了「测试号码」和「值班号码」能各配各的：
     * 联调时往自己手机上发，正式启用后改这一项指向值班手机/护士站，
     * 不必把测试号码从公共项里删掉。留空则回退 MSGPUSH_RECEIVERS。
     */
    public static final String KEY_ALERT_RECEIVERS = "MSGPUSH_ALERT_RECEIVERS";
    /**
     * 触发推送的预警类型，逗号分隔（取值见 {@code MsgPushAlertTemplates.ALL_RULES}）。
     *
     * <p>留空 = <b>不推送任何预警</b>：未配置就往外发短信是现场最难解释的事故，
     * 所以这里刻意不做「空即全推」的兜底。逐条文案见
     * {@link #contentKey(String)} 对应的参数。
     */
    public static final String KEY_RULES = "MSGPUSH_RULES";
    /** 返回成功标识：返回体包含该串即判成功；留空按 HTTP 2xx 判定 */
    public static final String KEY_SUCCESS_KEY = "MSGPUSH_SUCCESS_KEY";
    /** 超时毫秒 */
    public static final String KEY_TIMEOUT_MS = "MSGPUSH_TIMEOUT_MS";

    /** 场景：测试发送 */
    public static final String SCENE_TEST = "test";
    /** 场景：预警推送（自动扫描工作台待办后发出） */
    public static final String SCENE_ALERT = "alert";

    /**
     * 预警文案参数键前缀：{@code MSGPUSH_CONTENT_<规则代码>}。
     *
     * <p>如 {@code MSGPUSH_CONTENT_GCS_DROP_2}。现场在参数页逐条改写措辞，
     * 改哪条发哪条变，不必发版。
     */
    public static final String CONTENT_KEY_PREFIX = "MSGPUSH_CONTENT_";

    /** 预警文案参数键。 */
    public static String contentKey(String ruleCode) {
        return CONTENT_KEY_PREFIX
                + (ruleCode == null ? "" : ruleCode.trim().toUpperCase(java.util.Locale.ROOT));
    }

    /**
     * 平台统一入口名（msgHeader 的 serverName）。
     *
     * <p>文档写明平台只暴露一个 WebService，所有业务靠 serverName 路由，
     * 真实报文里 serverName 与 serviceId 都是这个值，故固定为常量、不做成参数。</p>
     */
    public static final String SERVER_NAME = "sendMessagesToCenterPlatform";

    /** 信封里引用两个片段的占位符（原样嵌入，不做转义） */
    public static final String PH_HEADER = "msgHeader";
    public static final String PH_BODY = "msgBody";

    /**
     * 信封模板缺省值（兜底；正常由参数 MSGPUSH_ENVELOPE 提供）。
     *
     * <p>按平台提供的他厂家真实成功报文：SOAP **1.2**（2003/05 命名空间，不是 schemas.xmlsoap.org），
     * 命名空间 {@code xmlns:zys="http://www.zysoft.com.cn/"}，操作元素是
     * {@code <zys:CallInterface>}（不是 serverName 同名元素），两个入参是带命名空间的
     * {@code <zys:msgHeader>} / {@code <zys:msgBody>}，内容各自用 CDATA 承载一份独立 XML 文档。
     *
     * <p>SOAP 1.2 不再使用 SOAPAction 请求头（改由 Content-Type: application/soap+xml 标识），
     * 故 MSGPUSH_SOAP_ACTION 默认为空。
     */
    public static final String DEFAULT_ENVELOPE =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\""
            + " xmlns:zys=\"http://www.zysoft.com.cn/\">\n"
            + "  <soap:Header/>\n"
            + "  <soap:Body>\n"
            + "    <zys:CallInterface>\n"
            + "      <zys:msgHeader><![CDATA[#msgHeader#]]></zys:msgHeader>\n"
            + "      <zys:msgBody><![CDATA[#msgBody#]]></zys:msgBody>\n"
            + "    </zys:CallInterface>\n"
            + "  </soap:Body>\n"
            + "</soap:Envelope>";
}
