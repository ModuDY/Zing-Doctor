package com.zing.doctor.module.msgpush.task;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.icu.service.WorkbenchEnrichService;
import com.zing.doctor.module.msgpush.MsgPushAlertTemplates;
import com.zing.doctor.module.msgpush.MsgPushKeys;
import com.zing.doctor.module.msgpush.dto.MsgPushAlertRequest;
import com.zing.doctor.module.msgpush.entity.MsgPushLog;
import com.zing.doctor.module.msgpush.mapper.MsgPushLogMapper;
import com.zing.doctor.module.msgpush.service.MsgPushService;
import com.zing.doctor.module.system.service.SysParamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/**
 * 高风险预警短信自动推送：扫描工作台的预警待办，按规则推给值班号码。
 *
 * <p><b>数据从哪来</b>：直接复用工作台那条流水线（{@code listInpatients → enrichCrisisFlags → enrich}），
 * 不另写一套判定 SQL —— 短信里说「GCS 降了 3 分」而工作台页面上没这条待办，
 * 现场没法解释，也没法复现。这里只负责「谁命中、什么时候发、发什么文案」。
 *
 * <p><b>为什么是 30 分钟一轮</b>：GCS 下降、呼吸抑制这类恶化事件要能及时出去；
 * ARDS、俯卧位这类持续状态靠去重保证每天只发一条，不会因为它一直成立就反复扰人。
 * 轮询间隔可用 {@code zing.msgpush.alert-cron} 覆盖（6 段式 cron）。
 *
 * <p><b>去重口径</b>：同患者 + 同规则 + 当天已有「成功」记录就不再发（查询直接打在
 * {@code msg_push_log} 上，不新增去重表）。只认 success=1，于是：
 * <ul>
 *   <li>演练模式下发出的记录 success 也是 1 —— 演练一轮之后当天不再重复刷日志；</li>
 *   <li>真发失败（如授权码错、网关不响应）<b>不算已推送</b>，下一轮会重试，
 *       现场把配置改对之后不需要手工补发，也不会因为一次失败就整天静默。</li>
 * </ul>
 *
 * <p><b>多实例提醒</b>：去重是「先查后发」，两个实例同时跑同一轮会各发一次。
 * 当前按单实例部署设计；若将来多实例，需要改用库级唯一键或分布式锁。
 *
 * <p><b>BRAIN_INJURY 提示</b>：它是诊断标记（诊断含脑出血/脑梗死等），只要诊断还在，
 * 每天都会各发一条 —— 想减少噪声，把它从 {@code MSGPUSH_RULES} 里去掉即可，
 * 不必改代码。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MsgPushAlertTask {

    private final IcuPatientService icuPatientService;
    private final WorkbenchEnrichService workbenchEnrichService;
    private final MsgPushService msgPushService;
    private final SysParamService sysParamService;
    private final MsgPushLogMapper logMapper;

    /** 每 30 分钟扫描一轮（cron 为 6 段式：秒 分 时 日 月 周）。 */
    @Scheduled(cron = "${zing.msgpush.alert-cron:0 0/30 * * * ?}")
    public void scanAndPush() {
        long t0 = System.currentTimeMillis();
        try {
            // 总开关关着就整轮跳过：连查询都不做，避免空跑 ICU 库的大查询
            if (!sysParamService.bool(MsgPushKeys.KEY_ENABLED, false)) {
                return;
            }
            Set<String> rules = MsgPushAlertTemplates.parseRules(sysParamService.value(MsgPushKeys.KEY_RULES));
            if (rules.isEmpty()) {
                log.info("[预警推送] 未配置 MSGPUSH_RULES，本轮不推送");
                return;
            }

            // 定时任务没有登录账号，按「不限科室」取全量在科患者：
            // 值班号码是全科共用的，按某个人的科室授权过滤会漏掉别人的患者。
            List<WorkbenchPatient> patients = icuPatientService.listInpatients(null);
            if (patients == null || patients.isEmpty()) {
                return;
            }
            icuPatientService.enrichCrisisFlags(patients, null);
            workbenchEnrichService.enrich(patients);

            int candidates = 0;
            int sent = 0;
            int skipped = 0;
            int unsuccessful = 0;
            for (WorkbenchPatient p : patients) {
                if (p == null || !StringUtils.hasText(p.getPatientId()) || p.getTodos() == null) {
                    continue;
                }
                for (String rule : p.getTodos()) {
                    if (!rules.contains(rule)) {
                        continue;
                    }
                    candidates++;
                    try {
                        if (pushedToday(p.getPatientId(), rule)) {
                            skipped++;
                            continue;
                        }
                        if (pushOne(p, rule) > 0) {
                            sent++;
                        } else {
                            unsuccessful++;
                        }
                    } catch (Exception e) {
                        // 一条发不出去不能影响其它患者：定时任务里单个失败往上抛会中断整轮
                        unsuccessful++;
                        log.warn("[预警推送] 单条推送失败: rule={}, patientId={}", rule, p.getPatientId(), e);
                    }
                }
            }
            log.info("[预警推送] 本轮完成: 在科患者={}, 命中待办={}, 当日已推跳过={}, 已发出={}, 未成功={}, 耗时={}ms",
                    patients.size(), candidates, skipped, sent, unsuccessful, System.currentTimeMillis() - t0);
        } catch (Exception e) {
            log.error("[预警推送] 本轮扫描异常", e);
        }
    }

    /** 渲染并发送一条；返回成功号码数（0 表示配置缺失或发送失败，均已记日志）。 */
    private int pushOne(WorkbenchPatient p, String rule) {
        MsgPushAlertRequest req = new MsgPushAlertRequest();
        req.setRuleCode(rule);
        req.setPatientId(p.getPatientId());
        req.setPatientName(p.getName());
        req.setBedNo(p.getBedNo());
        req.setDeptCode(p.getDepartCode());
        req.setVars(p.getAlertVars() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(p.getAlertVars()));
        // 文案模板：参数 MSGPUSH_CONTENT_<规则> 优先；为空时由推送实现回退到内置默认值
        req.setContentTemplate(sysParamService.value(MsgPushKeys.contentKey(rule)));
        return msgPushService.pushAlert(req);
    }

    /**
     * 当天是否已成功推送过（同患者 + 同规则）。
     *
     * <p>只认 success=1：失败的记录不算「已推送」，下一轮继续重试。
     */
    private boolean pushedToday(String patientId, String ruleCode) {
        LocalDateTime start = LocalDate.now().atStartOfDay();
        Long n = logMapper.selectCount(new QueryWrapper<MsgPushLog>()
                .eq("scene", MsgPushKeys.SCENE_ALERT)
                .eq("rule_code", ruleCode)
                .eq("patient_id", patientId)
                .eq("success", 1)
                .ge("create_time", start)
                .lt("create_time", start.plusDays(1)));
        return n != null && n > 0;
    }
}
