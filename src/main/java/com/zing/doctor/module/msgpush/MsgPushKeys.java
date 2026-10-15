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
    /** 接收号码，逗号分隔 */
    public static final String KEY_RECEIVERS = "MSGPUSH_RECEIVERS";
    /** 触发推送的预警类型，逗号分隔（本期仅登记，不自动推送） */
    public static final String KEY_RULES = "MSGPUSH_RULES";
    /** 返回成功标识：返回体包含该串即判成功；留空按 HTTP 2xx 判定 */
    public static final String KEY_SUCCESS_KEY = "MSGPUSH_SUCCESS_KEY";
    /** 超时毫秒 */
    public static final String KEY_TIMEOUT_MS = "MSGPUSH_TIMEOUT_MS";

    /** 场景：测试发送 */
    public static final String SCENE_TEST = "test";
    /** 场景：预警推送（后续接入自动推送时使用） */
    public static final String SCENE_ALERT = "alert";

    /** 信封里引用两个片段的占位符（原样嵌入，不做转义） */
    public static final String PH_HEADER = "msgHeader";
    public static final String PH_BODY = "msgBody";

    /** 信封模板缺省值：把 msgHeader / msgBody 两段包在一起。
     *  走 SOAP 的现场按 WSDL 在此套上 soap:Envelope 即可，无需改代码。 */
    public static final String DEFAULT_ENVELOPE =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<root>\n#msgHeader#\n#msgBody#\n</root>";
}
