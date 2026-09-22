package com.zing.doctor.module.sepsis.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.module.antibiotic.service.AbxDrugRecognizer;
import com.zing.doctor.module.sepsis.dto.SepsisBundleView;
import com.zing.doctor.module.sepsis.entity.SepsisBundleRecord;
import com.zing.doctor.module.sepsis.mapper.SepsisBundleRecordMapper;
import com.zing.doctor.module.sepsis.service.SepsisBundleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 脓毒症休克集束化治疗 Service 实现
 * 自动从 ICU 系统数据中提取相关信息，自动判断1H/3H/6H项目完成情况
 */
@Slf4j
@Service
public class SepsisBundleServiceImpl implements SepsisBundleService {

    @Autowired
    private SepsisBundleRecordMapper bundleRecordMapper;

    @Autowired
    private IcuPatientMapper icuPatientMapper;

    /** 抗菌药统一识别器：本模块的广谱/非抗菌药判定全部委托给它，避免维护第三份词表 */
    @Autowired
    private AbxDrugRecognizer abxDrugRecognizer;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 感染部位关键词映射 */
    private static final Map<String, String> INFECTION_SITE_MAP = new LinkedHashMap<>();
    static {
        // 肺部
        INFECTION_SITE_MAP.put("肺部感染", "肺部感染");
        INFECTION_SITE_MAP.put("肺炎", "肺部感染");
        INFECTION_SITE_MAP.put("肺脓肿", "肺部感染");
        // 血流
        INFECTION_SITE_MAP.put("血流感染", "血流感染");
        INFECTION_SITE_MAP.put("菌血症", "血流感染");
        INFECTION_SITE_MAP.put("败血症", "血流感染");
        INFECTION_SITE_MAP.put("脓毒血症", "血流感染");
        INFECTION_SITE_MAP.put("CRBSI", "CRBSI血流感染");
        // 中枢神经
        INFECTION_SITE_MAP.put("中枢神经系统感染", "中枢神经系统感染");
        INFECTION_SITE_MAP.put("脑膜炎", "中枢神经系统感染");
        INFECTION_SITE_MAP.put("脑炎", "中枢神经系统感染");
        INFECTION_SITE_MAP.put("颅内感染", "中枢神经系统感染");
        INFECTION_SITE_MAP.put("脑脓肿", "中枢神经系统感染");
        // 腹腔
        INFECTION_SITE_MAP.put("腹腔感染", "腹腔感染");
        INFECTION_SITE_MAP.put("腹膜炎", "腹腔感染");
        INFECTION_SITE_MAP.put("肝脓肿", "腹腔感染");
        INFECTION_SITE_MAP.put("阑尾炎", "腹腔感染");
        INFECTION_SITE_MAP.put("盆腔炎", "腹腔感染");
        // 胆道
        INFECTION_SITE_MAP.put("胆道感染", "胆道感染");
        INFECTION_SITE_MAP.put("胆管炎", "胆道感染");
        INFECTION_SITE_MAP.put("胆囊炎", "胆道感染");
        // 胃肠道
        INFECTION_SITE_MAP.put("胃肠道感染", "胃肠道感染");
        INFECTION_SITE_MAP.put("肠炎", "胃肠道感染");
        INFECTION_SITE_MAP.put("胃肠炎", "胃肠道感染");
        INFECTION_SITE_MAP.put("感染性腹泻", "胃肠道感染");
        // 泌尿系（含结石伴感染）
        INFECTION_SITE_MAP.put("泌尿系感染", "泌尿系感染");
        INFECTION_SITE_MAP.put("尿路感染", "泌尿系感染");
        INFECTION_SITE_MAP.put("泌尿", "泌尿系感染");
        INFECTION_SITE_MAP.put("尿路", "泌尿系感染");
        INFECTION_SITE_MAP.put("肾盂肾炎", "泌尿系感染");
        INFECTION_SITE_MAP.put("膀胱炎", "泌尿系感染");
        INFECTION_SITE_MAP.put("尿道炎", "泌尿系感染");
        INFECTION_SITE_MAP.put("前列腺炎", "泌尿系感染");
        INFECTION_SITE_MAP.put("输尿管结石", "泌尿系感染");
        INFECTION_SITE_MAP.put("肾结石", "泌尿系感染");
        INFECTION_SITE_MAP.put("膀胱结石", "泌尿系感染");
        INFECTION_SITE_MAP.put("尿路结石", "泌尿系感染");
        // 骨关节
        INFECTION_SITE_MAP.put("骨髓感染", "骨髓感染");
        INFECTION_SITE_MAP.put("骨髓炎", "骨髓感染");
        INFECTION_SITE_MAP.put("化脓性关节炎", "骨髓感染");
        INFECTION_SITE_MAP.put("感染性关节炎", "骨髓感染");
        // 皮肤软组织（脓肿兜底放最后）
        INFECTION_SITE_MAP.put("皮肤软组织感染", "皮肤软组织感染");
        INFECTION_SITE_MAP.put("蜂窝织炎", "皮肤软组织感染");
        INFECTION_SITE_MAP.put("丹毒", "皮肤软组织感染");
        INFECTION_SITE_MAP.put("切口感染", "皮肤软组织感染");
        INFECTION_SITE_MAP.put("伤口感染", "皮肤软组织感染");
        INFECTION_SITE_MAP.put("软组织感染", "皮肤软组织感染");
        INFECTION_SITE_MAP.put("压疮感染", "皮肤软组织感染");
        INFECTION_SITE_MAP.put("褥疮感染", "皮肤软组织感染");
        INFECTION_SITE_MAP.put("脓肿", "皮肤软组织感染");
    }

    @Override
    public SepsisBundleView getBundleDetail(String inHospitalNo) {
        return buildView(inHospitalNo, getByInHospitalNo(inHospitalNo), null, null);
    }

    @Override
    public SepsisBundleView calculateBundle(String inHospitalNo, String recordTime, String diagnosisTime, Long id) {
        // 传 id = 在该记录基础上按新时间重算（保留它已保存的手动勾选）；不传 = 纯新建预览。
        // 两者都只算不存。
        SepsisBundleRecord record = (id != null) ? bundleRecordMapper.selectById(id) : null;
        return buildView(inHospitalNo, record, parseDateTime(recordTime), parseDateTime(diagnosisTime));
    }

    /**
     * 构建评估视图（getBundleDetail / calculateBundle 共用）
     *
     * @param record                 已保存记录，可为 null（新建、或该患者尚无记录）
     * @param overrideRecordTime     覆盖记录时间（决定三块系统参考的 14 天窗口结束点），可为 null
     * @param overrideDiagnosisTime  覆盖确诊时间，可为 null
     */
    private SepsisBundleView buildView(String inHospitalNo, SepsisBundleRecord record,
                                       LocalDateTime overrideRecordTime, LocalDateTime overrideDiagnosisTime) {
        // 每次评估前刷新抗菌药词库快照（配置页修改即时生效；识别器自身也有 5 分钟兜底刷新）
        abxDrugRecognizer.refresh();
        SepsisBundleView view = new SepsisBundleView();

        // 传入的时间只参与计算，不回写数据库：在记录副本上覆盖，避免污染真实记录
        SepsisBundleRecord effective = record;
        if (overrideRecordTime != null || overrideDiagnosisTime != null) {
            effective = (record != null) ? copyRecord(record) : new SepsisBundleRecord();
            if (overrideRecordTime != null) {
                effective.setCreateTime(overrideRecordTime);
            } else if (record == null) {
                effective.setCreateTime(LocalDateTime.now());
            }
            if (overrideDiagnosisTime != null) {
                effective.setDiagnosisTime(overrideDiagnosisTime);
            }
        }

        // 保存已手动修改的项目状态（自动评估后需要恢复）
        SepsisBundleView.BundleItem savedBundle1h = null;
        SepsisBundleView.BundleItem savedBundle3h = null;
        SepsisBundleView.BundleItem savedBundle6h = null;

        if (record != null) {
            view.setId(record.getId());
            view.setCreateTime(record.getCreateTime() != null ? record.getCreateTime().format(DT_FMT) : null);
            view.setPatientId(record.getPatientId());
            view.setInHospitalNo(record.getInHospitalNo());
            view.setPatientName(record.getPatientName());
            view.setDepartCode(record.getDepartCode());
            view.setDiagnosisTime(record.getDiagnosisTime() != null ? record.getDiagnosisTime().format(DT_FMT) : null);
            view.setInDepartTime(record.getInDepartTime() != null ? record.getInDepartTime().format(DT_FMT) : null);
            view.setBundle1hCompleted(record.getBundle1hCompleted());
            view.setBundle3hCompleted(record.getBundle3hCompleted());
            view.setBundle6hCompleted(record.getBundle6hCompleted());
            view.setInfectionSite(record.getInfectionSite());
            view.setPathogen(record.getPathogen());
            view.setAntibiotic(record.getAntibiotic());
            if (record.getFluidReason() != null) {
                view.setFluidReason(JSONUtil.toBean(record.getFluidReason(), SepsisBundleView.FluidReason.class));
            }
            // 反序列化已保存的项目状态
            if (record.getBundle1hData() != null) {
                try {
                    savedBundle1h = JSONUtil.toBean(record.getBundle1hData(), SepsisBundleView.BundleItem.class);
                } catch (Exception ignored) {}
            }
            if (record.getBundle3hData() != null) {
                try {
                    savedBundle3h = JSONUtil.toBean(record.getBundle3hData(), SepsisBundleView.BundleItem.class);
                } catch (Exception ignored) {}
            }
            if (record.getBundle6hData() != null) {
                try {
                    savedBundle6h = JSONUtil.toBean(record.getBundle6hData(), SepsisBundleView.BundleItem.class);
                } catch (Exception ignored) {}
            }
        }

        // 记录时间 / 确诊时间被前端覆盖时，视图同样展示覆盖后的值（仅展示，不回写数据库）
        if (effective != null && effective.getCreateTime() != null) {
            view.setCreateTime(effective.getCreateTime().format(DT_FMT));
        }
        if (overrideDiagnosisTime != null) {
            view.setDiagnosisTime(overrideDiagnosisTime.format(DT_FMT));
        }

        // 2. 从 ICU 系统自动获取数据并判断
        try {
            autoAssessBundle(view, inHospitalNo, effective);
        } catch (Exception e) {
            log.error("自动评估脓毒症集束化治疗失败: inHospitalNo={}", inHospitalNo, e);
        }

        // 3. 恢复用户手动修改的项目状态（布尔字段），保留自动评估的详情字段
        restoreManualStatus(view, savedBundle1h, savedBundle3h, savedBundle6h);

        return view;
    }

    /**
     * 恢复用户手动修改的项目状态（只恢复布尔字段，保留自动评估的详情字段）
     */
    private void restoreManualStatus(SepsisBundleView view,
                                      SepsisBundleView.BundleItem saved1h,
                                      SepsisBundleView.BundleItem saved3h,
                                      SepsisBundleView.BundleItem saved6h) {
        if (saved1h != null && view.getBundle1h() != null) {
            if (saved1h.getLactateMeasured() != null) view.getBundle1h().setLactateMeasured(saved1h.getLactateMeasured());
            if (saved1h.getLactateMonitor() != null) view.getBundle1h().setLactateMonitor(saved1h.getLactateMonitor());
            if (saved1h.getBloodCultureBeforeAntibiotic() != null) view.getBundle1h().setBloodCultureBeforeAntibiotic(saved1h.getBloodCultureBeforeAntibiotic());
            if (saved1h.getBroadSpectrumAntibiotic() != null) view.getBundle1h().setBroadSpectrumAntibiotic(saved1h.getBroadSpectrumAntibiotic());
            if (saved1h.getFluidResuscitation() != null) view.getBundle1h().setFluidResuscitation(saved1h.getFluidResuscitation());
            if (saved1h.getNorepinephrine() != null) view.getBundle1h().setNorepinephrine(saved1h.getNorepinephrine());
        }
        if (saved3h != null && view.getBundle3h() != null) {
            if (saved3h.getLactateMeasured() != null) view.getBundle3h().setLactateMeasured(saved3h.getLactateMeasured());
            if (saved3h.getBloodCultureBeforeAntibiotic() != null) view.getBundle3h().setBloodCultureBeforeAntibiotic(saved3h.getBloodCultureBeforeAntibiotic());
            if (saved3h.getBroadSpectrumAntibiotic() != null) view.getBundle3h().setBroadSpectrumAntibiotic(saved3h.getBroadSpectrumAntibiotic());
            if (saved3h.getFluidResuscitation() != null) view.getBundle3h().setFluidResuscitation(saved3h.getFluidResuscitation());
        }
        if (saved6h != null && view.getBundle6h() != null) {
            if (saved6h.getVasopressor() != null) view.getBundle6h().setVasopressor(saved6h.getVasopressor());
            if (saved6h.getReassessVolume() != null) view.getBundle6h().setReassessVolume(saved6h.getReassessVolume());
            if (saved6h.getRepeatLactate() != null) view.getBundle6h().setRepeatLactate(saved6h.getRepeatLactate());
        }
    }

    /**
     * 自动评估集束化治疗完成情况
     */
    private void autoAssessBundle(SepsisBundleView view, String inHospitalNo, SepsisBundleRecord record) {
        // 获取患者基本信息
        Map<String, Object> patient = icuPatientMapper.selectPatientByInHospitalNo(inHospitalNo);
        if (patient == null) {
            log.warn("未找到患者信息: inHospitalNo={}", inHospitalNo);
            return;
        }

        if (view.getPatientName() == null) {
            view.setPatientName(str(patient.get("name")));
        }
        if (view.getInHospitalNo() == null) {
            view.setInHospitalNo(inHospitalNo);
        }
        if (view.getPatientId() == null) {
            view.setPatientId(str(patient.get("patient_id")));
        }
        if (view.getBedNo() == null) {
            view.setBedNo(str(patient.get("bed_no")));
        }
        if (view.getGender() == null) {
            view.setGender(str(patient.get("gender")));
        }
        if (view.getAge() == null) {
            view.setAge(str(patient.get("age")));
        }
        if (view.getDepartName() == null) {
            view.setDepartName(str(patient.get("department")));
        }
        if (view.getInDepartTime() == null) {
            Object inDepartTime = patient.get("in_depart_time");
            if (inDepartTime != null) {
                view.setInDepartTime(inDepartTime.toString());
            }
        }
        if (view.getOutDepartTime() == null) {
            Object outDepartTime = patient.get("out_depart_time");
            if (outDepartTime != null) {
                view.setOutDepartTime(outDepartTime.toString());
            }
        }

        // 获取诊断信息（先获取，用于确定确诊时间和休克类型）
        List<Map<String, Object>> diagnosisList = icuPatientMapper.selectDiagnosis(inHospitalNo);

        // 入科时间和出科时间（用于过滤诊断时间范围）
        LocalDateTime inDepartTimeDt = parseDateTime(view.getInDepartTime());
        LocalDateTime outDepartTimeDt = parseDateTime(view.getOutDepartTime());

        // 休克类型判断
        String shockType = determineShockType(diagnosisList, inDepartTimeDt, outDepartTimeDt);
        if (view.getShockType() == null || view.getShockType().isEmpty()) {
            view.setShockType(shockType);
        }

        // 确诊时间：优先用已保存的，否则从诊断信息中查找脓毒症/感染性休克诊断时间（只取入科后到出科前），最后用入科时间
        LocalDateTime diagnosisTime = null;
        if (record != null && record.getDiagnosisTime() != null) {
            diagnosisTime = record.getDiagnosisTime();
        } else {
            // 从诊断信息中查找脓毒症/感染性休克/败血症诊断（只取入科后到出科前）
            for (Map<String, Object> diag : diagnosisList) {
                String diagName = str(diag.get("diag_name"));
                if (diagName.contains("脓毒症") || diagName.contains("感染性休克")
                        || diagName.contains("脓毒性休克") || diagName.contains("败血症")
                        || diagName.contains("脓毒血症") || diagName.contains("sepsis")
                        || diagName.contains("Sepsis")) {
                    // 优先用诊断时间 diag_time（真实诊断时间），为空时回退 create_time（同步/录入时间），与休克类型判断口径一致
                    Object diagTimeObj = diag.get("diag_time") != null ? diag.get("diag_time") : diag.get("create_time");
                    if (diagTimeObj != null) {
                        LocalDateTime diagTime = parseDateTime(diagTimeObj.toString());
                        if (diagTime != null) {
                            // 只取入科后到出科前的诊断
                            boolean inRange = true;
                            if (inDepartTimeDt != null && diagTime.isBefore(inDepartTimeDt)) {
                                inRange = false;
                            }
                            if (outDepartTimeDt != null && diagTime.isAfter(outDepartTimeDt)) {
                                inRange = false;
                            }
                            if (inRange) {
                                diagnosisTime = diagTime;
                                break;
                            }
                        }
                    }
                }
            }
            // 如果没找到脓毒症诊断，用入科时间
            if (diagnosisTime == null) {
                diagnosisTime = inDepartTimeDt;
            }
        }
        if (diagnosisTime == null) {
            diagnosisTime = LocalDateTime.now();
        }
        if (view.getDiagnosisTime() == null) {
            view.setDiagnosisTime(diagnosisTime.format(DT_FMT));
        }

        // ========== 三块「参考值」的取数窗口 ==========
        // 口径：本条评估记录的创建时间往前 14 天（新建评估时 = 当前时间）。
        // 只作用于「感染部位/致病菌/抗菌药物」三块的系统参考值；
        // 1H/3H/6H 集束化项目的判定仍用各自原始列表，不受此窗口影响。
        LocalDateTime refEnd = (record != null && record.getCreateTime() != null)
                ? record.getCreateTime() : LocalDateTime.now();
        LocalDateTime refStart = refEnd.minusDays(14);
        view.setRefWindowStart(refStart.format(DT_FMT));
        view.setRefWindowEnd(refEnd.format(DT_FMT));
        // 窗口内的诊断（感染部位参考用）；休克类型、液体复苏原因判定继续用全量 diagnosisList
        List<Map<String, Object>> refDiagnosisList = diagnosisList.stream()
                .filter(d -> {
                    Object t = d.get("diag_time") != null ? d.get("diag_time") : d.get("create_time");
                    LocalDateTime dt = parseDateTime(str(t));
                    return dt != null && !dt.isBefore(refStart) && !dt.isAfter(refEnd);
                })
                .collect(Collectors.toList());

        // 体重
        Double weight = null;
        Object weightObj = patient.get("weight");
        if (weightObj != null) {
            try {
                weight = Double.parseDouble(weightObj.toString());
            } catch (Exception ignored) {}
        }

        // 获取乳酸检验结果
        List<Map<String, Object>> lactateList = icuPatientMapper.selectLactateResults(inHospitalNo);

        // 获取血培养送检记录
        List<Map<String, Object>> bloodCultureList = icuPatientMapper.selectBloodCultureRecords(inHospitalNo);

        // 获取抗菌药物医嘱
        List<Map<String, Object>> antibioticList = icuPatientMapper.selectAntibioticAdvice(inHospitalNo);
        // 抗菌药识别：白名单包含匹配（命中抗菌药通用名）+ 黑名单排除（双重保险）
        // 必须用白名单，不能纯黑名单排除：中药/营养药/解热镇痛药等非抗菌药有几千种，黑名单永远列不全
        // 白名单来自 config_abx_word(broad_spectrum)，已训练61个核心抗菌药通用名（含内置默认兜底）
        antibioticList = antibioticList.stream()
                .filter(a -> isBroadSpectrum(str(a.get("name"))))
                .filter(a -> !isNonAntibiotic(str(a.get("name"))))
                .collect(Collectors.toList());

        // 获取晶体液医嘱
        List<Map<String, Object>> fluidList = icuPatientMapper.selectFluidAdvice(inHospitalNo);

        // 获取血管升压药医嘱
        List<Map<String, Object>> vasopressorList = icuPatientMapper.selectVasopressorAdvice(inHospitalNo);

        // 获取细菌培养结果（致病菌）
        // 窗口 = [评估记录创建时间 - 14天, 评估记录创建时间]
        List<Map<String, Object>> bacteriaList = icuPatientMapper.selectBacteriaCultureForStats(
                refStart.format(DT_FMT),
                refEnd.format(DT_FMT),
                "");
        bacteriaList = bacteriaList.stream()
                .filter(b -> inHospitalNo.equals(str(b.get("in_hospital_no"))))
                .collect(Collectors.toList());

        // 抗菌药物【系统参考】专用：同样收窄到 14 天窗口。
        // 上面 antibioticList 保持全量——1H「抗菌药物前血培养」「应用广谱抗菌药物」判定依赖它，不能收窄。
        List<Map<String, Object>> refAntibioticList = icuPatientMapper.selectAntibioticAdvice(
                inHospitalNo, refStart.format(DT_FMT), refEnd.format(DT_FMT));
        refAntibioticList = (refAntibioticList == null ? new ArrayList<Map<String, Object>>() : refAntibioticList)
                .stream()
                .filter(a -> isBroadSpectrum(str(a.get("name"))))
                .filter(a -> !isNonAntibiotic(str(a.get("name"))))
                .collect(Collectors.toList());

        // ========== 1H 项目评估 ==========
        SepsisBundleView.BundleItem bundle1h = new SepsisBundleView.BundleItem();
        LocalDateTime time1h = diagnosisTime.plusHours(1);

        // 1. 测量乳酸水平
        List<Map<String, Object>> lactate1h = lactateList.stream()
                .filter(l -> {
                    LocalDateTime checkTime = parseDateTime(str(l.get("check_time")));
                    return checkTime != null && !checkTime.isAfter(time1h);
                })
                .collect(Collectors.toList());
        if (!lactate1h.isEmpty()) {
            bundle1h.setLactateMeasured(true);
            Map<String, Object> firstLactate = lactate1h.get(0);
            bundle1h.setLactateValue(str(firstLactate.get("result")));
            bundle1h.setLactateTime(str(firstLactate.get("check_time")));

            // 初始>2.0mmol/L需动态监测
            try {
                double lactateVal = Double.parseDouble(str(firstLactate.get("result")));
                if (lactateVal > 2.0) {
                    int monitorCount = (int) lactateList.stream()
                            .filter(l -> {
                                LocalDateTime checkTime = parseDateTime(str(l.get("check_time")));
                                return checkTime != null && checkTime.isAfter(time1h);
                            })
                            .count();
                    bundle1h.setLactateMonitor(monitorCount > 0);
                    bundle1h.setLactateMonitorCount(monitorCount);
                }
            } catch (Exception ignored) {}
        }

        // 2. 应用抗菌药物前获取血培养
        LocalDateTime firstAntibioticTime = null;
        String firstAntibioticName = null;
        if (!antibioticList.isEmpty()) {
            for (Map<String, Object> abx : antibioticList) {
                LocalDateTime startTime = parseDateTime(str(abx.get("start_time")));
                if (startTime != null && (firstAntibioticTime == null || startTime.isBefore(firstAntibioticTime))) {
                    firstAntibioticTime = startTime;
                    firstAntibioticName = str(abx.get("name"));
                }
            }
        }

        LocalDateTime firstBloodCultureTime = null;
        if (!bloodCultureList.isEmpty()) {
            for (Map<String, Object> bc : bloodCultureList) {
                LocalDateTime checkTime = parseDateTime(str(bc.get("check_time")));
                if (checkTime != null && (firstBloodCultureTime == null || checkTime.isBefore(firstBloodCultureTime))) {
                    firstBloodCultureTime = checkTime;
                }
            }
        }

        if (firstBloodCultureTime != null && firstAntibioticTime != null) {
            bundle1h.setBloodCultureBeforeAntibiotic(!firstBloodCultureTime.isAfter(firstAntibioticTime));
            bundle1h.setBloodCultureTime(firstBloodCultureTime.format(DT_FMT));
            bundle1h.setAntibioticStartTime(firstAntibioticTime.format(DT_FMT));
        } else if (firstBloodCultureTime != null) {
            bundle1h.setBloodCultureBeforeAntibiotic(true);
            bundle1h.setBloodCultureTime(firstBloodCultureTime.format(DT_FMT));
        }

        // 3. 应用广谱抗菌药物（只认匹配广谱关键词的抗菌药，避免肝素/白蛋白等非抗菌药误判）
        String firstBroadSpectrumName = null;
        if (!antibioticList.isEmpty()) {
            for (Map<String, Object> abx : antibioticList) {
                String name = str(abx.get("name"));
                LocalDateTime startTime = parseDateTime(str(abx.get("start_time")));
                if (startTime == null || startTime.isAfter(time1h)) {
                    continue;
                }
                if (isBroadSpectrum(name)) {
                    firstBroadSpectrumName = name;
                    break;
                }
            }
        }
        bundle1h.setBroadSpectrumAntibiotic(firstBroadSpectrumName != null);
        bundle1h.setAntibioticName(firstBroadSpectrumName);

        // 4. 低血压或乳酸≥4时按30ml/kg晶体液
        boolean hypotensionOrLactateHigh = false;
        try {
            if (bundle1h.getLactateValue() != null) {
                double lactateVal = Double.parseDouble(bundle1h.getLactateValue());
                if (lactateVal >= 4.0) {
                    hypotensionOrLactateHigh = true;
                }
            }
        } catch (Exception ignored) {}
        bundle1h.setHypotensionOrLactateHigh(hypotensionOrLactateHigh);

        if (hypotensionOrLactateHigh && weight != null) {
            double targetFluid = 30.0 * weight; // ml
            double totalFluid = calculateFluidAmount(fluidList, diagnosisTime, time1h);
            bundle1h.setFluidAmount(totalFluid);
            bundle1h.setFluidTarget(targetFluid);
            bundle1h.setWeight(weight);
            bundle1h.setFluidResuscitation(totalFluid >= targetFluid * 0.9); // 允许10%误差
        }

        // 5. 液体复苏后需去甲肾上腺素维持MAP>65
        boolean hasNorepinephrine = vasopressorList.stream()
                .anyMatch(v -> str(v.get("name")).contains("去甲肾上腺素"));
        bundle1h.setNorepinephrine(hasNorepinephrine);
        if (hasNorepinephrine) {
            for (Map<String, Object> v : vasopressorList) {
                if (str(v.get("name")).contains("去甲肾上腺素")) {
                    bundle1h.setNorepinephrineDose(str(v.get("drug_one_dosage")) + str(v.get("drug_one_dosage_unit")));
                    break;
                }
            }
        }

        view.setBundle1h(bundle1h);

        // ========== 3H 项目评估 ==========
        SepsisBundleView.BundleItem bundle3h = new SepsisBundleView.BundleItem();
        LocalDateTime time3h = diagnosisTime.plusHours(3);

        // 监测血乳酸水平（3H内）
        boolean lactate3h = lactateList.stream()
                .anyMatch(l -> {
                    LocalDateTime checkTime = parseDateTime(str(l.get("check_time")));
                    return checkTime != null && !checkTime.isAfter(time3h);
                });
        bundle3h.setLactateMeasured(lactate3h);

        // 应用抗生素前提取血培养
        bundle3h.setBloodCultureBeforeAntibiotic(bundle1h.getBloodCultureBeforeAntibiotic());

        // 使用广谱抗生素
        bundle3h.setBroadSpectrumAntibiotic(bundle1h.getBroadSpectrumAntibiotic());
        bundle3h.setAntibioticName(bundle1h.getAntibioticName());

        // 液体复苏（3H内单独计算）
        if (bundle1h.getHypotensionOrLactateHigh() != null && bundle1h.getHypotensionOrLactateHigh()
                && bundle1h.getWeight() != null) {
            double targetFluid3h = 30.0 * bundle1h.getWeight();
            double totalFluid3h = calculateFluidAmount(fluidList, diagnosisTime, time3h);
            bundle3h.setFluidAmount(totalFluid3h);
            bundle3h.setFluidTarget(targetFluid3h);
            bundle3h.setWeight(bundle1h.getWeight());
            bundle3h.setFluidResuscitation(totalFluid3h >= targetFluid3h * 0.9);
        } else {
            bundle3h.setFluidResuscitation(bundle1h.getFluidResuscitation());
            bundle3h.setFluidAmount(bundle1h.getFluidAmount());
            bundle3h.setFluidTarget(bundle1h.getFluidTarget());
            bundle3h.setWeight(bundle1h.getWeight());
        }

        // 3小时后评估数据（从3H到6H之间的检验/监护数据）
        // 这里简化处理，取3H后的最新乳酸值
        List<Map<String, Object>> lactateAfter3h = lactateList.stream()
                .filter(l -> {
                    LocalDateTime checkTime = parseDateTime(str(l.get("check_time")));
                    return checkTime != null && checkTime.isAfter(time3h);
                })
                .collect(Collectors.toList());
        if (!lactateAfter3h.isEmpty()) {
            bundle3h.setLactate3h(str(lactateAfter3h.get(lactateAfter3h.size() - 1).get("result")));
        }

        // ========== 3小时后评估数据（3H~6H 窗口内的监护/出入量/去甲肾上腺素） ==========
        // 去甲肾上腺素：从医嘱 patient_advice 取（selectVasopressorAdvice 已含去甲肾上腺素），显示完整名称
        Map<String, Object> latestNorepi = null;
        LocalDateTime latestNorepiTime = null;
        for (Map<String, Object> v : vasopressorList) {
            if (str(v.get("name")).contains("去甲肾上腺素")) {
                LocalDateTime vt = parseDateTime(str(v.get("start_time")));
                if (vt != null && (latestNorepiTime == null || vt.isAfter(latestNorepiTime))) {
                    latestNorepiTime = vt;
                    latestNorepi = v;
                }
            }
        }
        if (latestNorepi != null) {
            String dose = str(latestNorepi.get("drug_one_dosage"));
            String unit = str(latestNorepi.get("drug_one_dosage_unit"));
            // 值只显示剂量+单位，药名已由标签"去甲肾上腺素"展示，不重复
            bundle3h.setNorepiDose(dose != null && !dose.isEmpty() ? dose + (unit == null ? "" : unit) : "");
        }

        // 监护数据（CVP/MAP/ScvO2）：patient_observe_module_item_record 关联 patient_observe_module_item
        // 按 patient_id + item_code 关联，项目名称（CVPm/无创平均压/有创平均压等）匹配，取窗口内最新一条
        // 尿量：patient_io_module_item_record 关联 patient_io_module_item，按项目名称"尿量"匹配
        LocalDateTime time6hForAssess = diagnosisTime.plusHours(6);
        String assessStart = time3h.format(DT_FMT);
        String assessEnd = time6hForAssess.format(DT_FMT);
        try {
            String patientId = view.getPatientId();
            if (patientId != null && !patientId.isEmpty()) {
                List<Map<String, Object>> observeRecords = icuPatientMapper.selectObserveRecords(patientId, assessStart, assessEnd);
                if (observeRecords != null) {
                    for (Map<String, Object> rec : observeRecords) {
                        String itemName = str(rec.get("item_name"));
                        if (itemName == null || itemName.isEmpty()) {
                            continue;
                        }
                        if (bundle3h.getCvp() == null || bundle3h.getCvp().isEmpty()) {
                            if (itemName.contains("CVP")) {
                                bundle3h.setCvp(formatMonitorValue(str(rec.get("item_value"))));
                            }
                        }
                        if (bundle3h.getMap() == null || bundle3h.getMap().isEmpty()) {
                            if (itemName.contains("平均压")) {
                                bundle3h.setMap(formatMonitorValue(str(rec.get("item_value"))));
                            }
                        }
                        if (bundle3h.getScvo2() == null || bundle3h.getScvo2().isEmpty()) {
                            if (itemName.contains("ScvO2") || itemName.contains("ScvO\u2082")
                                    || itemName.contains("中心静脉血氧饱和度")) {
                                bundle3h.setScvo2(formatMonitorValue(str(rec.get("item_value"))));
                            }
                        }
                    }
                }
                // 尿量：取确诊后3小时内（[diagnosisTime, time3h]）的尿量记录求和
                List<Map<String, Object>> ioRecords = icuPatientMapper.selectIoRecords(patientId,
                        diagnosisTime.format(DT_FMT), time3h.format(DT_FMT));
                if (ioRecords != null) {
                    double urineTotal = 0.0;
                    for (Map<String, Object> rec : ioRecords) {
                        String itemName = str(rec.get("item_name"));
                        if (itemName != null && itemName.contains("尿量")) {
                            try {
                                urineTotal += Double.parseDouble(str(rec.get("item_value")));
                            } catch (Exception ignored) {}
                        }
                    }
                    if (urineTotal > 0) {
                        bundle3h.setUrineOutput(formatMonitorValue(String.valueOf(urineTotal)));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取3H后监护/出入量数据失败: inHospitalNo={}, msg={}", inHospitalNo, e.getMessage());
        }

        view.setBundle3h(bundle3h);

        // ========== 6H 项目评估 ==========
        SepsisBundleView.BundleItem bundle6h = new SepsisBundleView.BundleItem();
        LocalDateTime time6h = diagnosisTime.plusHours(6);

        // 应用血管升压药维持MAP≥65mmHg
        boolean hasVasopressor = !vasopressorList.isEmpty();
        bundle6h.setVasopressor(hasVasopressor);
        if (hasVasopressor) {
            Map<String, Object> vp = vasopressorList.get(0);
            String vpName = str(vp.get("name"));
            String vpDose = str(vp.get("drug_one_dosage"));
            String vpUnit = str(vp.get("drug_one_dosage_unit"));
            bundle6h.setNorepiDose(vpName
                    + (vpDose != null && !vpDose.isEmpty() ? " " + vpDose + (vpUnit == null ? "" : vpUnit) : ""));
        }

        // 重复评估容量状态和组织灌注（简化：6H内有多次乳酸测量或有血管升压药）
        int lactateCount6h = (int) lactateList.stream()
                .filter(l -> {
                    LocalDateTime checkTime = parseDateTime(str(l.get("check_time")));
                    return checkTime != null && !checkTime.isAfter(time6h);
                })
                .count();
        bundle6h.setReassessVolume(lactateCount6h >= 2 || hasVasopressor);
        bundle6h.setLactateMonitorCount(lactateCount6h);

        // 6H内最新乳酸值（重复测量明细）
        List<Map<String, Object>> lactate6hList = lactateList.stream()
                .filter(l -> {
                    LocalDateTime checkTime = parseDateTime(str(l.get("check_time")));
                    return checkTime != null && !checkTime.isAfter(time6h);
                })
                .collect(Collectors.toList());
        if (!lactate6hList.isEmpty()) {
            bundle6h.setLactate6h(str(lactate6hList.get(lactate6hList.size() - 1).get("result")));
        }

        // 早期乳酸升高时重复测量
        try {
            if (bundle1h.getLactateValue() != null) {
                double lactateVal = Double.parseDouble(bundle1h.getLactateValue());
                if (lactateVal > 2.0) {
                    bundle6h.setRepeatLactate(lactateCount6h >= 2);
                }
            }
        } catch (Exception ignored) {}

        view.setBundle6h(bundle6h);

        // ========== 感染部位 / 致病菌 / 抗菌药物：系统参考值 ==========
        // 这三个字段只用于页面「参考」展示，不写库、不覆盖医生勾选结果。
        // 医生勾选的枚举值存在 infectionSite / pathogen / antibiotic 上，由前端提交后入库。
        {
            Set<String> infectionSites = new LinkedHashSet<>();
            for (Map<String, Object> diag : refDiagnosisList) {
                String diagName = str(diag.get("diag_name"));
                for (Map.Entry<String, String> entry : INFECTION_SITE_MAP.entrySet()) {
                    if (diagName.contains(entry.getKey())) {
                        infectionSites.add(entry.getValue());
                        break;
                    }
                }
            }
            view.setInfectionSiteRef(String.join("、", infectionSites));
        }

        {
            Set<String> pathogens = new LinkedHashSet<>();
            for (Map<String, Object> b : bacteriaList) {
                String bacteriaName = str(b.get("bacteria_name"));
                if (isValidBacteriaName(bacteriaName)) {
                    pathogens.add(bacteriaName);
                }
            }
            view.setPathogenRef(String.join("、", pathogens));
        }

        {
            Set<String> antibiotics = new LinkedHashSet<>();
            for (Map<String, Object> abx : refAntibioticList) {
                String name = str(abx.get("name"));
                if (name != null && !name.isEmpty()) {
                    antibiotics.add(name);
                }
            }
            view.setAntibioticRef(String.join("、", antibiotics));
        }

        // ========== 液体复苏未达标原因自动判断 ==========
        if (view.getFluidReason() == null) {
            SepsisBundleView.FluidReason fluidReason = new SepsisBundleView.FluidReason();
            boolean hasPulmonaryEdema = diagnosisList.stream()
                    .anyMatch(d -> str(d.get("diag_name")).contains("肺水肿")
                            || str(d.get("diag_name")).contains("急性左心衰")
                            || str(d.get("diag_name")).contains("容量过负荷"));
            fluidReason.setVolumeOverload(hasPulmonaryEdema);

            boolean hasOrganInjury = diagnosisList.stream()
                    .anyMatch(d -> str(d.get("diag_name")).contains("AKI")
                            || str(d.get("diag_name")).contains("急性肾损伤")
                            || str(d.get("diag_name")).contains("ARDS")
                            || str(d.get("diag_name")).contains("急性呼吸窘迫"));
            fluidReason.setOrganInjury(hasOrganInjury);

            fluidReason.setCapillaryLeak(false);
            view.setFluidReason(fluidReason);
        }
    }

    @Override
    public SepsisBundleRecord saveBundle(SepsisBundleRecord record) {
        // 每次保存都是新的评估记录（支持多次评估）
        // 记录时间由前端传入（医生可改，用于补录过去的评估）；为空时才取当前时间。
        // 它同时决定三块「系统参考」14 天窗口的结束点。
        if (record.getCreateTime() == null) {
            record.setCreateTime(LocalDateTime.now());
        }
        record.setUpdateTime(LocalDateTime.now());
        if (record.getStatus() == null) {
            record.setStatus(1);
        }
        bundleRecordMapper.insert(record);
        return record;
    }

    @Override
    public SepsisBundleRecord updateBundle(SepsisBundleRecord record) {
        // 感染部位/致病菌/抗菌药物 改为医生勾选的枚举值，必须允许更新（旧逻辑置 null 会丢勾选）。
        // 系统自动取到的原始值只放在 *Ref 字段展示，不入库，因此这里不存在"覆盖历史值"的问题。
        // createTime（记录时间）允许医生回改：传入非空即更新，为空时 updateById 会跳过该字段。
        record.setUpdateTime(LocalDateTime.now());
        bundleRecordMapper.updateById(record);
        return record;
    }

    @Override
    public SepsisBundleRecord getByInHospitalNo(String inHospitalNo) {
        LambdaQueryWrapper<SepsisBundleRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SepsisBundleRecord::getInHospitalNo, inHospitalNo)
                .eq(SepsisBundleRecord::getStatus, 1)
                .orderByDesc(SepsisBundleRecord::getCreateTime)
                .last("LIMIT 1");
        return bundleRecordMapper.selectOne(wrapper);
    }

    @Override
    public boolean deleteById(Long id) {
        SepsisBundleRecord record = bundleRecordMapper.selectById(id);
        if (record == null) {
            return false;
        }
        // 逻辑删除：status 置 0，避免物理删除影响关联数据
        SepsisBundleRecord del = new SepsisBundleRecord();
        del.setId(id);
        del.setStatus(0);
        del.setUpdateTime(LocalDateTime.now());
        bundleRecordMapper.updateById(del);
        return true;
    }

    @Override
    public List<SepsisBundleRecord> getHistoryList(String inHospitalNo) {
        LambdaQueryWrapper<SepsisBundleRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SepsisBundleRecord::getInHospitalNo, inHospitalNo)
                .eq(SepsisBundleRecord::getStatus, 1)
                .orderByDesc(SepsisBundleRecord::getCreateTime);
        return bundleRecordMapper.selectList(wrapper);
    }

    @Override
    public SepsisBundleView getBundleDetailById(Long id) {
        SepsisBundleRecord record = bundleRecordMapper.selectById(id);
        if (record == null) {
            return null;
        }
        SepsisBundleView view = new SepsisBundleView();
        // 填充基本信息
        view.setId(record.getId());
        view.setCreateTime(record.getCreateTime() != null ? record.getCreateTime().format(DT_FMT) : null);
        view.setPatientId(record.getPatientId());
        view.setInHospitalNo(record.getInHospitalNo());
        view.setPatientName(record.getPatientName());
        view.setDepartCode(record.getDepartCode());
        view.setDiagnosisTime(record.getDiagnosisTime() != null ? record.getDiagnosisTime().format(DT_FMT) : null);
        view.setInDepartTime(record.getInDepartTime() != null ? record.getInDepartTime().format(DT_FMT) : null);
        view.setBundle1hCompleted(record.getBundle1hCompleted());
        view.setBundle3hCompleted(record.getBundle3hCompleted());
        view.setBundle6hCompleted(record.getBundle6hCompleted());
        view.setInfectionSite(record.getInfectionSite());
        view.setPathogen(record.getPathogen());
        view.setAntibiotic(record.getAntibiotic());
        if (record.getFluidReason() != null) {
            view.setFluidReason(JSONUtil.toBean(record.getFluidReason(), SepsisBundleView.FluidReason.class));
        }
        // 反序列化项目数据
        if (record.getBundle1hData() != null) {
            try {
                view.setBundle1h(JSONUtil.toBean(record.getBundle1hData(), SepsisBundleView.BundleItem.class));
            } catch (Exception ignored) {}
        }
        if (record.getBundle3hData() != null) {
            try {
                view.setBundle3h(JSONUtil.toBean(record.getBundle3hData(), SepsisBundleView.BundleItem.class));
            } catch (Exception ignored) {}
        }
        if (record.getBundle6hData() != null) {
            try {
                view.setBundle6h(JSONUtil.toBean(record.getBundle6hData(), SepsisBundleView.BundleItem.class));
            } catch (Exception ignored) {}
        }
        // 填充患者信息
        try {
            Map<String, Object> patient = icuPatientMapper.selectPatientByInHospitalNo(record.getInHospitalNo());
            if (patient != null) {
                view.setBedNo(str(patient.get("bed_no")));
                view.setGender(str(patient.get("gender")));
                view.setAge(str(patient.get("age")));
                view.setDepartName(str(patient.get("department")));
                Object outDepartTime = patient.get("out_depart_time");
                if (outDepartTime != null) {
                    view.setOutDepartTime(outDepartTime.toString());
                }
                // 休克类型
                List<Map<String, Object>> diagnosisList = icuPatientMapper.selectDiagnosis(record.getInHospitalNo());
                LocalDateTime inDt = parseDateTime(view.getInDepartTime());
                LocalDateTime outDt = parseDateTime(view.getOutDepartTime());
                view.setShockType(determineShockType(diagnosisList, inDt, outDt));
            }
        } catch (Exception e) {
            log.error("填充患者信息失败: id={}", id, e);
        }
        return view;
    }

    // ========== 工具方法 ==========

    /** 拷贝一条记录：用于在不改动数据库的前提下，覆盖时间参与预览计算 */
    private SepsisBundleRecord copyRecord(SepsisBundleRecord src) {
        SepsisBundleRecord copy = new SepsisBundleRecord();
        BeanUtil.copyProperties(src, copy);
        return copy;
    }

    /**
     * 判断休克类型
     * 优先判断感染性休克/脓毒性休克，其次是脓毒症
     * 只取入科后到出科前的诊断
     */
    String determineShockType(List<Map<String, Object>> diagnosisList,
                              LocalDateTime inDepartTime,
                              LocalDateTime outDepartTime) {
        boolean hasSepticShock = false;
        boolean hasSepsis = false;

        for (Map<String, Object> diag : diagnosisList) {
            String diagName = str(diag.get("diag_name"));
            // 优先用诊断时间 diag_time（真实诊断时间），为空时回退 create_time（同步/录入时间）
            Object diagTimeObj = diag.get("diag_time") != null ? diag.get("diag_time") : diag.get("create_time");
            LocalDateTime diagTime = diagTimeObj != null ? parseDateTime(diagTimeObj.toString()) : null;

            // 只取入科后到出科前的诊断
            if (diagTime != null) {
                if (inDepartTime != null && diagTime.isBefore(inDepartTime)) continue;
                if (outDepartTime != null && diagTime.isAfter(outDepartTime)) continue;
            }

            if (diagName.contains("感染性休克") || diagName.contains("脓毒性休克")) {
                hasSepticShock = true;
            }
            if (diagName.contains("脓毒症") || diagName.contains("败血症")
                    || diagName.contains("脓毒血症") || diagName.contains("sepsis")
                    || diagName.contains("Sepsis")) {
                hasSepsis = true;
            }
        }

        if (hasSepticShock) {
            return "感染性休克";
        } else if (hasSepsis) {
            return "脓毒症";
        }
        return "";
    }

    /**
     * 计算指定时间范围内的晶体液总量（ml）
     * 只计算单位为ml/mL的液体，跳过单位为g的药物
     */
    double calculateFluidAmount(List<Map<String, Object>> fluidList,
                               LocalDateTime startTime, LocalDateTime endTime) {
        double total = 0.0;
        for (Map<String, Object> fluid : fluidList) {
            LocalDateTime adviceTime = parseDateTime(str(fluid.get("start_time")));
            if (adviceTime != null && !adviceTime.isBefore(startTime) && !adviceTime.isAfter(endTime)) {
                try {
                    double dosage = Double.parseDouble(str(fluid.get("drug_one_dosage")));
                    String unit = str(fluid.get("drug_one_dosage_unit"));
                    // 只计算体积单位（ml/mL），跳过质量单位（g/mg）
                    if (unit.toLowerCase().contains("ml")) {
                        total += dosage;
                    }
                } catch (Exception ignored) {}
            }
        }
        return total;
    }

    private String str(Object obj) {
        return obj == null ? "" : obj.toString();
    }

    /**
     * 格式化监护/出入量记录值：清理空白，数值型去掉多余小数位（如 150.00 -> 150）
     */
    private String formatMonitorValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        String v = value.trim();
        try {
            double d = Double.parseDouble(v);
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
            return v;
        } catch (Exception e) {
            return v;
        }
    }

    private LocalDateTime parseDateTime(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        try {
            // 支持多种格式
            if (str.contains("T")) {
                str = str.replace("T", " ");
            }
            if (str.contains(".")) {
                str = str.substring(0, str.indexOf("."));
            }
            if (str.length() == 16) {
                str = str + ":00";
            }
            return LocalDateTime.parse(str, DT_FMT);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(str);
            } catch (Exception e2) {
                return null;
            }
        }
    }

    /**
     * 判断是否为有效的细菌/真菌名称
     * 排除：未检出、阴性、无细菌、正常菌群、培养X天未检出、杂菌生长等非菌株结果
     */
    boolean isValidBacteriaName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        // 排除明确的阴性结果
        if (name.contains("未检出") || name.contains("阴性") || name.contains("无细菌")
                || name.contains("正常菌群") || name.contains("无致病菌") || name.contains("未见致病菌")) {
            return false;
        }
        // 排除"培养X天未检出细菌"这类描述（包含数字和"天"）
        if (name.matches(".*\\d+.*天.*")) {
            return false;
        }
        // 排除包含"生长"但不是具体菌株名称的结果（如"杂菌生长"、"正常菌群生长"）
        if (name.contains("生长") && (name.contains("杂菌") || name.contains("正常")
                || name.contains("无") || name.contains("未"))) {
            return false;
        }
        return true;
    }

    /** 判断是否为溶媒。包内可见：供单测直接验证溶媒剔除口径 */
    boolean isSolvent(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String[] solventKeywords = {
                "氯化钠", "葡萄糖", "乳酸钠林格", "灭菌注射用水", "木糖醇",
                "转化糖", "果糖", "复方氯化钠", "甘油果糖", "林格"
        };
        for (String kw : solventKeywords) {
            if (name.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为广谱抗菌药物。委托统一识别器 {@link AbxDrugRecognizer#isBroadSpectrum(String)}，
     * 词表口径 = config_abx_word(broad_spectrum)，表空回退内置默认；与全系统保持一致。
     */
    private boolean isBroadSpectrum(String name) {
        return abxDrugRecognizer.isBroadSpectrum(name);
    }

    /**
     * 判断是否为非抗菌药物。委托统一识别器 {@link AbxDrugRecognizer#isNonAntibiotic(String)}，
     * 用于"抗菌药物"列表与时间节点的抗菌药识别，剔除不是抗菌药的医嘱。
     */
    private boolean isNonAntibiotic(String name) {
        return abxDrugRecognizer.isNonAntibiotic(name);
    }
}