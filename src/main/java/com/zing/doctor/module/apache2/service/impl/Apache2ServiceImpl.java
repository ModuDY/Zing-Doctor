package com.zing.doctor.module.apache2.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zing.doctor.common.OperatorContext;
import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.module.apache2.entity.Apache2Config;
import com.zing.doctor.module.apache2.entity.Apache2ScoreRecord;
import com.zing.doctor.module.apache2.mapper.Apache2ConfigMapper;
import com.zing.doctor.module.apache2.mapper.Apache2ScoreRecordMapper;
import com.zing.doctor.module.apache2.service.Apache2Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * APACHE II 评分 Service 实现
 */
@Slf4j
@Service
public class Apache2ServiceImpl implements Apache2Service {

    @Autowired
    private Apache2ScoreRecordMapper scoreRecordMapper;

    @Autowired
    private Apache2ConfigMapper configMapper;

    @Autowired
    private IcuPatientMapper icuPatientMapper;

    @Override
    public Map<String, Object> getOverview(String departCode, String startTime, String endTime) {
        Map<String, Object> result = new HashMap<>();

        // 科室参数非法时直接返回空统计，避免退化为全库扫描：
        // 外链模板未替换的占位符（如 "${departCode}"）也会落到这里，前端据此提示参数缺失。
        if (departCode == null || departCode.trim().isEmpty() || departCode.contains("${")) {
            log.warn("APACHE II 总览查询科室参数非法: departCode={}", departCode);
            result.put("totalCount", 0);
            result.put("avgScore", 0.0);
            result.put("avgMortality", 0.0);
            result.put("highRiskCount", 0L);
            result.put("scoreDistribution", emptyDistribution());
            result.put("records", Collections.emptyList());
            return result;
        }

        // 查询该科室在时间范围内的评分记录。
        // 仅取列表与展开明细需要的字段，避免把 aps_data / gcs_detail / remark 等大字段整体传给前端。
        LambdaQueryWrapper<Apache2ScoreRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(Apache2ScoreRecord::getId,
                        Apache2ScoreRecord::getInHospitalNo,
                        Apache2ScoreRecord::getPatientName,
                        Apache2ScoreRecord::getDepartCode,
                        Apache2ScoreRecord::getScoreTime,
                        Apache2ScoreRecord::getScoreType,
                        Apache2ScoreRecord::getAgeScore,
                        Apache2ScoreRecord::getChronicScore,
                        Apache2ScoreRecord::getGcsScore,
                        Apache2ScoreRecord::getPhysiologyScore,
                        Apache2ScoreRecord::getTotalScore,
                        Apache2ScoreRecord::getMortalityRate,
                        Apache2ScoreRecord::getDiagnosisType,
                        Apache2ScoreRecord::getCreateBy)
                .eq(Apache2ScoreRecord::getDepartCode, departCode)
                .eq(Apache2ScoreRecord::getStatus, 1)
                .ge(Apache2ScoreRecord::getScoreTime, startTime)
                .le(Apache2ScoreRecord::getScoreTime, endTime)
                .orderByDesc(Apache2ScoreRecord::getScoreTime);
        List<Apache2ScoreRecord> records = scoreRecordMapper.selectList(wrapper);

        // 统计数据
        int totalCount = records.size();
        double avgScore = records.stream().mapToInt(r -> r.getTotalScore() != null ? r.getTotalScore() : 0).average().orElse(0);
        double avgMortality = records.stream().mapToDouble(r -> r.getMortalityRate() != null ? r.getMortalityRate().doubleValue() : 0).average().orElse(0);
        long highRiskCount = records.stream().filter(r -> r.getTotalScore() != null && r.getTotalScore() >= 20).count();

        // 评分分布
        Map<String, Integer> scoreDistribution = emptyDistribution();
        for (Apache2ScoreRecord r : records) {
            int score = r.getTotalScore() != null ? r.getTotalScore() : 0;
            if (score <= 4) scoreDistribution.merge("0-4", 1, Integer::sum);
            else if (score <= 9) scoreDistribution.merge("5-9", 1, Integer::sum);
            else if (score <= 14) scoreDistribution.merge("10-14", 1, Integer::sum);
            else if (score <= 19) scoreDistribution.merge("15-19", 1, Integer::sum);
            else if (score <= 24) scoreDistribution.merge("20-24", 1, Integer::sum);
            else if (score <= 29) scoreDistribution.merge("25-29", 1, Integer::sum);
            else scoreDistribution.merge("30+", 1, Integer::sum);
        }

        result.put("totalCount", totalCount);
        result.put("avgScore", Math.round(avgScore * 10) / 10.0);
        result.put("avgMortality", Math.round(avgMortality * 10) / 10.0);
        result.put("highRiskCount", highRiskCount);
        result.put("scoreDistribution", scoreDistribution);
        result.put("records", records);

        return result;
    }

    /** APACHE II 评分分布空桶（顺序固定，前端柱状图按此顺序渲染） */
    private static Map<String, Integer> emptyDistribution() {
        Map<String, Integer> dist = new LinkedHashMap<>();
        dist.put("0-4", 0);
        dist.put("5-9", 0);
        dist.put("10-14", 0);
        dist.put("15-19", 0);
        dist.put("20-24", 0);
        dist.put("25-29", 0);
        dist.put("30+", 0);
        return dist;
    }

    @Override
    public List<Apache2ScoreRecord> getPatientRecords(String inHospitalNo) {
        return scoreRecordMapper.selectRecordList(inHospitalNo);
    }

    @Override
    public Apache2ScoreRecord getRecordById(Long id) {
        return scoreRecordMapper.selectById(id);
    }

    @Override
    public Apache2ScoreRecord getRecordPdf(Long id) {
        return scoreRecordMapper.selectPdfById(id);
    }

    @Override
    public Map<String, Object> autoFetchPhysiologyData(String patientId, String startTime, String endTime) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 获取患者基本信息
            Map<String, Object> patient = icuPatientMapper.selectPatientById(patientId);
            if (patient != null) {
                result.put("patientName", patient.get("name"));
                result.put("age", patient.get("age"));
                result.put("gender", patient.get("gender"));
                result.put("bedCode", patient.get("bed_no"));
                result.put("inDepartTime", patient.get("in_depart_time"));
            }

            // 获取监护数据
            List<Map<String, Object>> observeRecords = icuPatientMapper.selectObserveRecords(patientId, startTime, endTime);
            Map<String, List<Double>> observeValues = new HashMap<>();
            for (Map<String, Object> rec : observeRecords) {
                String itemCode = (String) rec.get("item_code");
                String valueStr = (String) rec.get("item_value");
                if (itemCode != null && valueStr != null) {
                    try {
                        double value = Double.parseDouble(valueStr);
                        observeValues.computeIfAbsent(itemCode, k -> new ArrayList<>()).add(value);
                    } catch (NumberFormatException ignored) {}
                }
            }

            // 从配置获取item_code映射
            Map<String, String> configMap = loadConfigMap("observe_item");

            // 体温：取偏离36-38最远的值
            result.put("temperature", findWorstValue(observeValues, configMap.get("temperature"), 36.0, 38.0));
            // 心率：取偏离70-109最远的值
            result.put("heartRate", findWorstValue(observeValues, configMap.get("heart_rate"), 70.0, 109.0));
            // 呼吸频率：取偏离12-24最远的值
            result.put("respiratoryRate", findWorstValue(observeValues, configMap.get("respiratory_rate"), 12.0, 24.0));
            // MAP：取偏离70-109最远的值
            result.put("map", findWorstValue(observeValues, configMap.get("map"), 70.0, 109.0));
            // FiO2
            result.put("fio2", findLatestValue(observeValues, configMap.get("fio2")));

            // 获取检验数据（血常规和生化）
            // 注意：selectPatientById返回的住院号别名是patient_no，不是in_hospital_no
            String inHospitalNo = patient != null ? (String) patient.get("patient_no") : null;
            if (inHospitalNo != null && !inHospitalNo.isEmpty()) {
                Map<String, String> labConfigMap = loadConfigMap("lis_item");
                // 钠：取偏离135-145最远的值
                result.put("sodium", findWorstLabValue(inHospitalNo, labConfigMap.get("sodium"), 135.0, 145.0, startTime, endTime));
                // 钾：取偏离3.5-5.5最远的值
                result.put("potassium", findWorstLabValue(inHospitalNo, labConfigMap.get("potassium"), 3.5, 5.5, startTime, endTime));
                // 肌酐：取偏离50-110最远的值（原单位μmol/L，评分时转mg/dL）
                result.put("creatinine", findWorstLabValue(inHospitalNo, labConfigMap.get("creatinine"), 50.0, 110.0, startTime, endTime));
                // HCT：取偏离35-45最远的值（数据库存小数如0.14，需×100转百分比）
                Double hctValue = findWorstLabValue(inHospitalNo, labConfigMap.get("hct"), 35.0, 45.0, startTime, endTime);
                if (hctValue != null && hctValue < 1) {
                    hctValue = hctValue * 100;
                }
                result.put("hct", hctValue);
                // WBC：取偏离3-15最远的值
                result.put("wbc", findWorstLabValue(inHospitalNo, labConfigMap.get("wbc"), 3.0, 15.0, startTime, endTime));
            }

            // 血气分析：PH值、氧分压PaO2、二氧化碳分压PaCO2（同一管血气按采集时间配对）
            List<Map<String, Object>> bloodGasRecords = icuPatientMapper.selectBloodGasRecords(
                    patientId, Arrays.asList("PH值", "氧分压", "二氧化碳分压"), startTime, endTime);
            // 按血气采集时间(毫秒)分组，同一管血的PaO2/PaCO2/PH配对
            Map<Long, Map<String, Double>> bgByTime = new TreeMap<>();
            for (Map<String, Object> rec : bloodGasRecords) {
                String itemName = (String) rec.get("item_name");
                String valueStr = (String) rec.get("item_value");
                Object timeObj = rec.get("item_time");
                if (itemName == null || valueStr == null || valueStr.isEmpty() || timeObj == null) continue;
                try {
                    double val = Double.parseDouble(valueStr.trim());
                    bgByTime.computeIfAbsent(toEpochMilli(timeObj), k -> new HashMap<>()).put(itemName, val);
                } catch (NumberFormatException ignored) {}
            }

            // 构建FiO2时间序列（带时间，升序），用于匹配每管血气采集时刻最近的FiO2
            String fio2CodesCfg = configMap.get("fio2");
            Set<String> fio2CodeSet = (fio2CodesCfg == null || fio2CodesCfg.isEmpty())
                    ? Collections.emptySet() : new HashSet<>(Arrays.asList(fio2CodesCfg.split(",")));
            List<double[]> fio2Series = new ArrayList<>();
            for (Map<String, Object> rec : observeRecords) {
                String code = (String) rec.get("item_code");
                String valueStr = (String) rec.get("item_value");
                Object timeObj = rec.get("item_time");
                if (code != null && fio2CodeSet.contains(code.trim()) && valueStr != null && timeObj != null) {
                    try {
                        fio2Series.add(new double[]{toEpochMilli(timeObj), Double.parseDouble(valueStr.trim())});
                    } catch (NumberFormatException ignored) {}
                }
            }
            fio2Series.sort(Comparator.comparingDouble(a -> a[0]));
            Double latestFio2 = (Double) result.get("fio2");

            Double phWorst = null, pao2Worst = null, aado2Worst = null;
            double phMaxDev = -1, pao2Min = Double.MAX_VALUE, aado2Max = -Double.MAX_VALUE;
            for (Map.Entry<Long, Map<String, Double>> entry : bgByTime.entrySet()) {
                long bgTime = entry.getKey();
                Map<String, Double> bg = entry.getValue();
                // PH取偏离7.33-7.49最远的值
                Double ph = bg.get("PH值");
                if (ph != null) {
                    double dev = ph < 7.33 ? (7.33 - ph) : (ph > 7.49 ? (ph - 7.49) : 0);
                    if (dev > phMaxDev) { phMaxDev = dev; phWorst = ph; }
                }
                // PaO2取最低值（越低越差）
                Double pao2 = bg.get("氧分压");
                if (pao2 != null && pao2 < pao2Min) { pao2Min = pao2; pao2Worst = pao2; }
                // A-aDO2 = 713×FiO2 − 1.25×PaCO2 − PaO2（FiO2库存为百分数需/100），取最大值（越大越差）
                Double paco2 = bg.get("二氧化碳分压");
                if (pao2 != null && paco2 != null) {
                    double fio2Used;
                    if (!fio2Series.isEmpty()) {
                        fio2Used = findNearestValue(fio2Series, bgTime);
                    } else if (latestFio2 != null) {
                        fio2Used = latestFio2;
                    } else {
                        continue;
                    }
                    double aado2 = 713 * (fio2Used / 100.0) - 1.25 * paco2 - pao2;
                    if (aado2 > aado2Max) { aado2Max = aado2; aado2Worst = aado2; }
                }
            }
            result.put("ph", phWorst);
            result.put("pao2", pao2Worst);
            result.put("aado2", aado2Worst);

            // GCS：同步「取数范围内最新一条评全（非插管）」的系统 GCS，供前端回填 E/V/M 三项。
            // 之前此处漏了返回，前端虽有回填代码但 data.gcsEye 永远 undefined，
            // 导致手工「自动获取 / 自动获取并计算」都同步不到系统 GCS。
            Map<String, Object> gcs = latestSystemGcs(patientId, startTime, endTime);
            if (gcs != null) {
                result.put("gcsEye", gcs.get("eye"));
                result.put("gcsVerbal", gcs.get("verbal"));
                result.put("gcsMotor", gcs.get("motor"));
                result.put("gcsRecordTime", gcs.get("recordTime"));
            }

        } catch (Exception e) {
            log.error("自动获取生理数据失败: patientId={}", patientId, e);
        }

        return result;
    }

    /**
     * 从检验数据中取偏离正常区间最远的值（最差值）
     */
    private Double findWorstLabValue(String inHospitalNo, String itemCodes, double low, double high,
                                      String startTime, String endTime) {
        if (itemCodes == null || itemCodes.isEmpty()) return null;
        try {
            List<String> codeList = Arrays.asList(itemCodes.split(","));
            List<Map<String, Object>> records = icuPatientMapper.selectLabRecordsByCodes(inHospitalNo, codeList, startTime, endTime);
            Double worst = null;
            double maxDeviation = -1;  // 改为-1，确保正常值(deviation=0)也能被选中
            for (Map<String, Object> rec : records) {
                String valueStr = (String) rec.get("item_value");
                if (valueStr != null && !valueStr.isEmpty()) {
                    try {
                        double value = Double.parseDouble(valueStr.trim());
                        double deviation = value < low ? (low - value) : (value > high ? (value - high) : 0);
                        if (deviation > maxDeviation) {
                            maxDeviation = deviation;
                            worst = value;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
            return worst;
        } catch (Exception e) {
            log.warn("获取检验数据失败: inHospitalNo={}, itemCodes={}", inHospitalNo, itemCodes, e);
            return null;
        }
    }

    @Override
    public Map<String, Object> calculateScore(Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();

        // A 年龄评分
        int age = getIntParam(params, "age", 0);
        int ageScore = calcAgeScore(age);
        result.put("ageScore", ageScore);

        // B 慢性健康评分
        String chronicHealth = (String) params.getOrDefault("chronicHealth", "none");
        int chronicScore = "elective".equals(chronicHealth) ? 2 : ("nonoperative".equals(chronicHealth) ? 5 : 0);
        result.put("chronicScore", chronicScore);

        // C GCS评分：GCS=E(睁眼1-4)+V(言语1-5)+M(运动1-6)，三项评全后合法总分严格在[3,15]，
        // C=15-GCS（范围0-12）。缺评/未传/越界（含前端缺项误传0、1、2）一律按“未评估”计0，
        // 不允许只评部分维度就拼分，也杜绝 GCS<3 时 C 被算成13/14/15 这种超过规则上限的虚高分。
        int gcsScore = 0;
        Object gcsObj = params.get("gcsTotal");
        if (gcsObj != null) {
            try {
                int gcsTotal = Integer.parseInt(gcsObj.toString().trim());
                if (gcsTotal >= 3 && gcsTotal <= 15) {
                    gcsScore = 15 - gcsTotal;
                }
            } catch (NumberFormatException ignore) {
                gcsScore = 0;
            }
        }
        result.put("gcsScore", gcsScore);

        // D 急性生理评分（12项）
        int physiologyScore = 0;
        Map<String, Integer> apsScores = new LinkedHashMap<>();

        // 体温
        double temperature = getDoubleParam(params, "temperature", 37.0);
        int tempScore = calcTemperatureScore(temperature);
        apsScores.put("temperature", tempScore);
        physiologyScore += tempScore;

        // MAP
        double map = getDoubleParam(params, "map", 90.0);
        int mapScore = calcMapScore(map);
        apsScores.put("map", mapScore);
        physiologyScore += mapScore;

        // 心率
        double heartRate = getDoubleParam(params, "heartRate", 80.0);
        int hrScore = calcHeartRateScore(heartRate);
        apsScores.put("heartRate", hrScore);
        physiologyScore += hrScore;

        // 呼吸频率
        double respRate = getDoubleParam(params, "respiratoryRate", 16.0);
        int rrScore = calcRespRateScore(respRate);
        apsScores.put("respiratoryRate", rrScore);
        physiologyScore += rrScore;

        // 氧合
        double fio2 = getDoubleParam(params, "fio2", 21.0);
        double pao2 = getDoubleParam(params, "pao2", 100.0);
        double aado2 = getDoubleParam(params, "aado2", 10.0);
        int oxygenScore = calcOxygenScore(fio2, pao2, aado2);
        apsScores.put("oxygen", oxygenScore);
        physiologyScore += oxygenScore;

        // 动脉血pH
        double ph = getDoubleParam(params, "ph", 7.40);
        int phScore = calcPhScore(ph);
        apsScores.put("ph", phScore);
        physiologyScore += phScore;

        // 血清钠
        double sodium = getDoubleParam(params, "sodium", 140.0);
        int naScore = calcSodiumScore(sodium);
        apsScores.put("sodium", naScore);
        physiologyScore += naScore;

        // 血清钾
        double potassium = getDoubleParam(params, "potassium", 4.0);
        int kScore = calcPotassiumScore(potassium);
        apsScores.put("potassium", kScore);
        physiologyScore += kScore;

        // 血清肌酐（μmol/L转mg/dL）
        double creatinine = getDoubleParam(params, "creatinine", 80.0);
        double creatinineMgDl = creatinine / 88.4;
        boolean acuteRenalFailure = getBooleanParam(params, "acuteRenalFailure", false);
        int crScore = calcCreatinineScore(creatinineMgDl, acuteRenalFailure);
        apsScores.put("creatinine", crScore);
        physiologyScore += crScore;

        // 血细胞比容
        double hct = getDoubleParam(params, "hct", 40.0);
        int hctScore = calcHctScore(hct);
        apsScores.put("hct", hctScore);
        physiologyScore += hctScore;

        // 白细胞
        double wbc = getDoubleParam(params, "wbc", 8.0);
        int wbcScore = calcWbcScore(wbc);
        apsScores.put("wbc", wbcScore);
        physiologyScore += wbcScore;

        // GCS已在C项计算

        result.put("physiologyScore", physiologyScore);
        result.put("apsScores", apsScores);

        // 总分
        int totalScore = ageScore + chronicScore + gcsScore + physiologyScore;
        result.put("totalScore", totalScore);

        // 死亡率预测（APACHE II 标准 logistic 回归公式，Knaus 1985）
        // ln(R/1-R) = -3.5174 + (APACHE II 总分 × 0.1467) + 0.6031(非手术/急诊手术) + 诊断权重
        // 注意：方程中的分值必须是 APACHE II 总分（APS + 年龄 + 慢性健康），不是仅 APS。
        // 死亡率 = 1 / (1 + e^(-R)) × 100%
        String diagnosisType = (String) params.getOrDefault("diagnosisType", "none");
        boolean emergencySurgery = getBooleanParam(params, "emergencySurgery", false);
        double diagnosisWeight = getDoubleParam(params, "diagnosisWeight", 0.0);

        double logit = -3.5174 + (totalScore * 0.1467);
        // 非手术类或急诊手术加0.6031
        if ("nonoperative".equals(diagnosisType) || ("operative".equals(diagnosisType) && emergencySurgery)) {
            logit += 0.6031;
        }
        // 加入诊断权重
        logit += diagnosisWeight;

        double mortalityRate = 1.0 / (1.0 + Math.exp(-logit)) * 100.0;
        mortalityRate = Math.round(mortalityRate * 10.0) / 10.0; // 保留1位小数
        result.put("mortalityRate", mortalityRate);
        result.put("logit", Math.round(logit * 1000.0) / 1000.0);
        result.put("diagnosisWeight", diagnosisWeight);

        return result;
    }

    @Override
    public Apache2ScoreRecord saveScore(Apache2ScoreRecord record) {
        // 记录 PDF 大小便于排查（不打印内容本身）
        int pdfLen = record.getPdfData() == null ? 0 : record.getPdfData().length();
        long t0 = System.currentTimeMillis();
        // 操作人一律由服务端解析并覆盖：请求体里即使带着 createBy 也不采信，
        // 否则改一下请求就能把评分记录署成别人的名字
        String operator = OperatorContext.current();
        if (record.getId() == null) {
            record.setCreateTime(LocalDateTime.now());
            record.setStatus(1);
            record.setCreateBy(operator);
            scoreRecordMapper.insert(record);
        } else {
            record.setUpdateTime(LocalDateTime.now());
            record.setUpdateBy(operator);
            scoreRecordMapper.updateById(record);
        }
        log.info("APACHE2评分保存完成: id={}, inHospitalNo={}, pdfBase64Len={}, operator={}, 耗时={}ms",
                record.getId(), record.getInHospitalNo(), pdfLen, operator, System.currentTimeMillis() - t0);
        // 大字段不随保存接口回传（下载走 /record/{id}/pdf），避免响应体再次携带约 1MB base64 拖慢/超限
        record.setPdfData(null);
        return record;
    }

    @Override
    public boolean attachPdf(Long id, String pdfData, String pdfName) {
        if (id == null || pdfData == null || pdfData.isEmpty()) {
            log.warn("APACHE2文书PDF补传参数为空: id={}, pdfLen={}", id, pdfData == null ? 0 : pdfData.length());
            return false;
        }
        long t0 = System.currentTimeMillis();
        try {
            // 只更新 PDF 大字段两列，不整行重写；与评分主体解耦，慢/失败都不影响已落库的评分
            LambdaUpdateWrapper<Apache2ScoreRecord> uw = new LambdaUpdateWrapper<>();
            uw.eq(Apache2ScoreRecord::getId, id)
                    .set(Apache2ScoreRecord::getPdfData, pdfData)
                    .set(Apache2ScoreRecord::getPdfName, pdfName)
                    .set(Apache2ScoreRecord::getUpdateBy, OperatorContext.current())
                    .set(Apache2ScoreRecord::getUpdateTime, LocalDateTime.now());
            int rows = scoreRecordMapper.update(null, uw);
            log.info("APACHE2文书PDF补传完成: id={}, pdfBase64Len={}, rows={}, 耗时={}ms",
                    id, pdfData.length(), rows, System.currentTimeMillis() - t0);
            return rows > 0;
        } catch (Exception e) {
            // 大字段写入失败仅告警，不抛出：评分主体已先落库，前端可重新生成补传
            log.error("APACHE2文书PDF补传失败（不影响已保存的评分主体）: id={}, pdfLen={}, 耗时={}ms",
                    id, pdfData.length(), System.currentTimeMillis() - t0, e);
            return false;
        }
    }

    @Override
    public boolean deleteScore(Long id, String operator) {
        // operator 入参保留只为兼容旧调用方，不再采信：把它改成别人的名字只是一个请求的事
        Apache2ScoreRecord record = scoreRecordMapper.selectById(id);
        if (record != null) {
            record.setStatus(0);
            record.setUpdateBy(OperatorContext.current());
            record.setUpdateTime(LocalDateTime.now());
            scoreRecordMapper.updateById(record);
            return true;
        }
        return false;
    }

    @Override
    public List<Map<String, Object>> getConfigList(String configType) {
        LambdaQueryWrapper<Apache2Config> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Apache2Config::getConfigType, configType)
                .eq(Apache2Config::getStatus, 1)
                .orderByAsc(Apache2Config::getSortNo);
        List<Apache2Config> configs = configMapper.selectList(wrapper);
        return configs.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("configKey", c.getConfigKey());
            m.put("configValue", c.getConfigValue());
            m.put("itemName", c.getItemName());
            m.put("remark", c.getRemark());
            return m;
        }).collect(Collectors.toList());
    }

    // ========== 私有辅助方法 ==========

    private Map<String, String> loadConfigMap(String configType) {
        Map<String, String> map = new HashMap<>();
        try {
            List<Map<String, Object>> list = getConfigList(configType);
            for (Map<String, Object> m : list) {
                map.put((String) m.get("configKey"), (String) m.get("configValue"));
            }
        } catch (Exception e) {
            log.warn("加载配置失败，使用默认值: configType={}", configType);
        }
        // 默认值兜底
        if ("observe_item".equals(configType)) {
            map.putIfAbsent("temperature", "oi_tiwen");
            map.putIfAbsent("heart_rate", "oi_hr");
            map.putIfAbsent("respiratory_rate", "oi_hxpl");
            map.putIfAbsent("map", "oi_ycpjy,oi_pjy");
            map.putIfAbsent("fio2", "oi_FiO2(设置值)");
        } else if ("lis_item".equals(configType)) {
            map.putIfAbsent("sodium", "100150,5039");
            map.putIfAbsent("potassium", "100140,5040");
            map.putIfAbsent("creatinine", "100210");
            map.putIfAbsent("hct", "200060");
            map.putIfAbsent("wbc", "200010");
            map.putIfAbsent("ph", "");
        }
        return map;
    }

    private Double findWorstValue(Map<String, List<Double>> values, String itemCodes, double low, double high) {
        if (itemCodes == null || itemCodes.isEmpty()) return null;
        Double worst = null;
        double maxDeviation = -1;  // 改为-1，确保正常值(deviation=0)也能被选中
        for (String code : itemCodes.split(",")) {
            List<Double> list = values.get(code.trim());
            if (list != null) {
                for (Double v : list) {
                    double deviation = v < low ? (low - v) : (v > high ? (v - high) : 0);
                    if (deviation > maxDeviation) {
                        maxDeviation = deviation;
                        worst = v;
                    }
                }
            }
        }
        return worst;
    }

    private Double findLatestValue(Map<String, List<Double>> values, String itemCodes) {
        if (itemCodes == null || itemCodes.isEmpty()) return null;
        for (String code : itemCodes.split(",")) {
            List<Double> list = values.get(code.trim());
            if (list != null && !list.isEmpty()) {
                // SQL按item_time DESC倒序，第一个才是最新值
                return list.get(0);
            }
        }
        return null;
    }

    /**
     * 把JDBC返回的时间对象（Timestamp/Date/LocalDateTime/字符串）统一转成毫秒时间戳。
     */
    private long toEpochMilli(Object timeObj) {
        if (timeObj == null) return 0L;
        if (timeObj instanceof java.util.Date) {
            return ((java.util.Date) timeObj).getTime();
        }
        if (timeObj instanceof java.time.LocalDateTime) {
            return ((java.time.LocalDateTime) timeObj)
                    .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (timeObj instanceof java.time.LocalDate) {
            return ((java.time.LocalDate) timeObj)
                    .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        String s = timeObj.toString().replace('T', ' ');
        try {
            if (s.length() > 19) s = s.substring(0, 19);
            return java.sql.Timestamp.valueOf(s).getTime();
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 在按时间升序排列的 [timeMillis, value] 序列中，找距离 targetTime 最近的点的值。
     */
    private double findNearestValue(List<double[]> series, long targetTime) {
        int n = series.size();
        if (n == 1) return series.get(0)[1];
        int lo = 0, hi = n - 1;
        // 二分查找第一个时间 >= targetTime 的位置
        int insert = n;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (series.get(mid)[0] >= targetTime) { insert = mid; hi = mid - 1; }
            else lo = mid + 1;
        }
        if (insert == 0) return series.get(0)[1];
        if (insert >= n) return series.get(n - 1)[1];
        double before = series.get(insert - 1)[0];
        double after = series.get(insert)[0];
        return (targetTime - before <= after - targetTime) ? series.get(insert - 1)[1] : series.get(insert)[1];
    }

    private int calcAgeScore(int age) {
        if (age <= 44) return 0;
        if (age <= 54) return 2;
        if (age <= 64) return 3;
        if (age <= 74) return 5;
        return 6;
    }

    private int calcTemperatureScore(double temp) {
        if (temp >= 41) return 4;
        if (temp >= 39) return 3;
        if (temp >= 38.5) return 1;
        if (temp >= 36) return 0;
        if (temp >= 34) return 1;
        if (temp >= 32) return 2;
        if (temp >= 30) return 3;
        return 4;
    }

    private int calcMapScore(double map) {
        if (map >= 160) return 4;
        if (map >= 130) return 3;
        if (map >= 110) return 2;
        if (map >= 70) return 0;
        if (map >= 50) return 2;
        return 4;
    }

    private int calcHeartRateScore(double hr) {
        if (hr >= 180) return 4;
        if (hr >= 140) return 3;
        if (hr >= 110) return 2;
        if (hr >= 70) return 0;
        if (hr >= 55) return 2;
        if (hr >= 40) return 3;
        return 4;
    }

    private int calcRespRateScore(double rr) {
        if (rr >= 50) return 4;
        if (rr >= 35) return 3;
        if (rr >= 25) return 1;
        if (rr >= 12) return 0;
        if (rr >= 10) return 1;
        if (rr >= 6) return 2;
        return 4;
    }

    /** 构造趋势点 {time, value}（与 /metric-trend 返回结构一致） */
    private Map<String, Object> trendPoint(String timeText, Double value) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("time", timeText);
        p.put("value", value);
        return p;
    }

    /** 毫秒时间戳 → "yyyy-MM-dd HH:mm:ss"（原始时间文本缺失时的兜底） */
    private String millisToText(long ms) {
        return java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms),
                        java.time.ZoneId.systemDefault())
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 氧合三要素（FiO2 / PaO2 / A-aDO2）的时间序列 + 「评分实际选中的最差一管」。
     * 由 {@link #autoFetchPhysiologyData}（评分取数）与 {@link #getOxygenTrend}（来源弹窗趋势图）共用，
     * 保证趋势展示与评分采用同一套“就近配对 FiO2 + 分支判定”口径，避免两处公式漂移。
     *
     * <p>返回键：
     * <ul>
     *   <li>{@code phWorst}：范围内偏离 7.33-7.49 最远的 pH</li>
     *   <li>{@code fio2Points} / {@code pao2Points} / {@code aado2Points}：趋势点列表（时间升序，{time,value}）</li>
     *   <li>{@code worstFio2} / {@code worstPao2} / {@code worstAado2} / {@code worstTime} / {@code worstScore}：
     *       氧合得分最差那一管的自洽取值（FiO2≥50% 走 A-aDO2，否则走 PaO2）</li>
     * </ul>
     */
    private Map<String, Object> buildOxygenData(List<Map<String, Object>> observeRecords,
                                                String fio2CodesCfg,
                                                List<Map<String, Object>> bloodGasRecords) {
        Map<String, Object> out = new HashMap<>();

        // ---- 血气：按采集时间(毫秒)分组，同一管血的 PH / PaO2 / PaCO2 配对 ----
        Map<Long, Map<String, Double>> bgByTime = new TreeMap<>();
        Map<Long, String> bgTimeText = new HashMap<>();
        for (Map<String, Object> rec : bloodGasRecords) {
            String itemName = (String) rec.get("item_name");
            String valueStr = (String) rec.get("item_value");
            Object timeObj = rec.get("item_time");
            if (itemName == null || valueStr == null || valueStr.isEmpty() || timeObj == null) continue;
            try {
                long ms = toEpochMilli(timeObj);
                bgByTime.computeIfAbsent(ms, k -> new HashMap<>())
                        .put(itemName, Double.parseDouble(valueStr.trim()));
                bgTimeText.putIfAbsent(ms, String.valueOf(timeObj));
            } catch (NumberFormatException ignored) {}
        }

        // ---- FiO2 时间序列（升序），用于匹配每管血气采集时刻最近的 FiO2 ----
        Set<String> fio2CodeSet = (fio2CodesCfg == null || fio2CodesCfg.isEmpty())
                ? Collections.emptySet() : new HashSet<>(Arrays.asList(fio2CodesCfg.split(",")));
        List<Object[]> fio2Raw = new ArrayList<>();   // {millis, value, timeText}
        for (Map<String, Object> rec : observeRecords) {
            String code = (String) rec.get("item_code");
            String valueStr = (String) rec.get("item_value");
            Object timeObj = rec.get("item_time");
            if (code != null && fio2CodeSet.contains(code.trim()) && valueStr != null && timeObj != null) {
                try {
                    fio2Raw.add(new Object[]{toEpochMilli(timeObj),
                            Double.parseDouble(valueStr.trim()), String.valueOf(timeObj)});
                } catch (NumberFormatException ignored) {}
            }
        }
        fio2Raw.sort(Comparator.comparingLong(a -> (Long) a[0]));
        List<double[]> fio2Series = new ArrayList<>();
        List<Map<String, Object>> fio2Points = new ArrayList<>();
        for (Object[] r : fio2Raw) {
            fio2Series.add(new double[]{(Long) r[0], (Double) r[1]});
            fio2Points.add(trendPoint((String) r[2], (Double) r[1]));
        }
        Double latestFio2 = fio2Series.isEmpty() ? null : fio2Series.get(fio2Series.size() - 1)[1];

        // ---- 逐管血气：pH 最差、PaO2/A-aDO2 趋势点、氧合得分最差的那一管 ----
        Double phWorst = null;
        double phMaxDev = -1;
        Double worstFio2 = null, worstPao2 = null, worstAado2 = null;
        String worstTime = null;
        int worstScore = 0;
        List<Map<String, Object>> pao2Points = new ArrayList<>();
        List<Map<String, Object>> aado2Points = new ArrayList<>();
        for (Map.Entry<Long, Map<String, Double>> entry : bgByTime.entrySet()) {
            long bgTime = entry.getKey();
            Map<String, Double> bg = entry.getValue();
            String timeText = bgTimeText.getOrDefault(bgTime, millisToText(bgTime));

            // pH 取偏离 7.33-7.49 最远的值
            Double ph = bg.get("PH值");
            if (ph != null) {
                double dev = ph < 7.33 ? (7.33 - ph) : (ph > 7.49 ? (ph - 7.49) : 0);
                if (dev > phMaxDev) { phMaxDev = dev; phWorst = ph; }
            }

            Double pao2 = bg.get("氧分压");
            if (pao2 == null) continue;
            pao2Points.add(trendPoint(timeText, pao2));

            // 该管当时的 FiO2：优先按采集时刻就近匹配，其次取范围内最新，最后按未吸氧 21%
            double fio2Used = !fio2Series.isEmpty() ? findNearestValue(fio2Series, bgTime)
                    : (latestFio2 != null ? latestFio2 : 21.0);
            // A-aDO2 = 713×FiO2 − 1.25×PaCO2 − PaO2（FiO2 为百分数需 /100）
            Double paco2 = bg.get("二氧化碳分压");
            Double aado2 = (paco2 == null) ? null : 713 * (fio2Used / 100.0) - 1.25 * paco2 - pao2;
            if (aado2 != null) aado2Points.add(trendPoint(timeText, aado2));

            // 该管走 A-aDO2 分支却缺 PaCO2 时无法评分，跳过（仅影响“最差一管”的选取）
            if (fio2Used >= 50 && aado2 == null) continue;
            int s = calcOxygenScore(fio2Used, pao2, aado2 == null ? 0.0 : aado2);
            if (worstFio2 == null || s > worstScore) {
                worstScore = s;
                worstFio2 = fio2Used;
                worstPao2 = pao2;
                worstAado2 = aado2;
                worstTime = timeText;
            }
        }

        out.put("phWorst", phWorst);
        out.put("fio2Points", fio2Points);
        out.put("pao2Points", pao2Points);
        out.put("aado2Points", aado2Points);
        out.put("worstFio2", worstFio2);
        out.put("worstPao2", worstPao2);
        out.put("worstAado2", worstAado2);
        out.put("worstTime", worstTime);
        out.put("worstScore", worstScore);
        return out;
    }

    private int calcOxygenScore(double fio2, double pao2, double aado2) {
        if (fio2 >= 50) {
            // FiO2 >= 0.5 用A-aDO2
            if (aado2 >= 500) return 4;
            if (aado2 >= 350) return 3;
            if (aado2 >= 200) return 2;
            return 0;
        } else {
            // FiO2 < 0.5 用 PaO2：<55=4 / 55-60=3 / 61-70=1 / >70=0
            if (pao2 < 55) return 4;
            if (pao2 <= 60) return 3;
            if (pao2 <= 70) return 1;
            return 0;
        }
    }

    private int calcPhScore(double ph) {
        if (ph >= 7.7) return 4;
        if (ph >= 7.6) return 3;
        if (ph >= 7.5) return 1;
        if (ph >= 7.33) return 0;
        if (ph >= 7.25) return 2;
        if (ph >= 7.15) return 3;
        return 4;
    }

    private int calcSodiumScore(double na) {
        if (na >= 180) return 4;
        if (na >= 160) return 3;
        if (na >= 155) return 2;
        if (na >= 150) return 1;
        if (na >= 130) return 0;
        if (na >= 120) return 2;
        if (na >= 111) return 3;
        return 4;
    }

    private int calcPotassiumScore(double k) {
        if (k >= 7) return 4;
        if (k >= 6) return 3;
        if (k >= 5.5) return 1;
        if (k >= 3.5) return 0;
        if (k >= 3) return 1;
        if (k >= 2.5) return 2;
        return 4;
    }

    private int calcCreatinineScore(double crMgDl, boolean acuteRenalFailure) {
        int score;
        if (crMgDl >= 3.5) score = 4;
        else if (crMgDl >= 2) score = 3;
        else if (crMgDl >= 1.5) score = 2;
        else if (crMgDl >= 0.6) score = 0;
        else score = 2;
        if (acuteRenalFailure) score *= 2;
        return score;
    }

    private int calcHctScore(double hct) {
        if (hct >= 60) return 4;
        if (hct >= 50) return 2;
        if (hct >= 46) return 1;
        if (hct >= 30) return 0;
        if (hct >= 20) return 2;
        return 4;
    }

    private int calcWbcScore(double wbc) {
        if (wbc >= 40) return 4;
        if (wbc >= 20) return 2;
        if (wbc >= 15) return 1;
        if (wbc >= 3) return 0;
        if (wbc >= 1) return 2;
        return 4;
    }

    private int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        Object v = params.get(key);
        if (v == null) return defaultValue;
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double getDoubleParam(Map<String, Object> params, String key, double defaultValue) {
        Object v = params.get(key);
        if (v == null) return defaultValue;
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean getBooleanParam(Map<String, Object> params, String key, boolean defaultValue) {
        Object v = params.get(key);
        if (v == null) return defaultValue;
        return Boolean.parseBoolean(v.toString());
    }

    @Override
    public Map<String, Object> getPatientInfo(String inHospitalNo) {
        Map<String, Object> patient = icuPatientMapper.selectPatientByInHospitalNo(inHospitalNo);
        if (patient == null) {
            return new HashMap<>();
        }
        Map<String, Object> result = new HashMap<>();
        result.put("patientId", patient.get("patient_id"));
        result.put("inHospitalNo", patient.get("patient_no"));
        result.put("name", patient.get("name"));
        result.put("gender", patient.get("gender"));
        result.put("age", patient.get("age"));
        // 注意：selectPatientByInHospitalNo 的别名是 bed_no / department（ward_name），
        // 不是 bed_code / depart_name，取错键会恒为 null（评分页床号一直显示“—”）。
        result.put("bedCode", patient.get("bed_no"));
        result.put("inDepartTime", patient.get("in_depart_time"));
        result.put("outDepartTime", patient.get("out_depart_time"));
        result.put("departCode", patient.get("depart_code"));
        result.put("departName", patient.get("department"));
        // 入科诊断：文书预览/导出展示用（patient_info.diagnosis_content）
        result.put("diagnosis", patient.get("diagnosis"));
        return result;
    }

    @Override
    public List<Map<String, Object>> getMetricTrend(String patientId, String metricKey, String startTime, String endTime) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            // 监护类指标
            Set<String> observeMetrics = new HashSet<>(Arrays.asList(
                "temperature", "heartRate", "respiratoryRate", "map", "fio2"));
            // 检验类指标（通过lis_item_code查询）
            Set<String> labMetrics = new HashSet<>(Arrays.asList(
                "sodium", "potassium", "creatinine", "hct", "wbc"));
            // 血气分析类指标（通过lis_item_name查询：PH值、氧分压）
            Set<String> bloodGasMetrics = new HashSet<>(Arrays.asList("ph", "pao2"));

            if (observeMetrics.contains(metricKey)) {
                // 监护数据：从patient_observe_module_item_record查询
                Map<String, String> configMap = loadConfigMap("observe_item");
                // metricKey（驼峰）→ configKey（下划线）映射
                String configKey;
                switch (metricKey) {
                    case "heartRate": configKey = "heart_rate"; break;
                    case "respiratoryRate": configKey = "respiratory_rate"; break;
                    default: configKey = metricKey; break;
                }
                String itemCodes = configMap.get(configKey);
                if (itemCodes == null || itemCodes.isEmpty()) return result;

                List<Map<String, Object>> records = icuPatientMapper.selectObserveRecords(patientId, startTime, endTime);
                Set<String> codeSet = new HashSet<>(Arrays.asList(itemCodes.split(",")));
                for (Map<String, Object> rec : records) {
                    String code = (String) rec.get("item_code");
                    String valueStr = (String) rec.get("item_value");
                    Object timeObj = rec.get("item_time");
                    if (code != null && codeSet.contains(code.trim()) && valueStr != null && timeObj != null) {
                        try {
                            Map<String, Object> point = new HashMap<>();
                            point.put("time", timeObj.toString());
                            point.put("value", Double.parseDouble(valueStr.trim()));
                            result.add(point);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } else if (labMetrics.contains(metricKey)) {
                // 检验数据：从patient_info_lis_item查询
                Map<String, Object> patient = icuPatientMapper.selectPatientById(patientId);
                if (patient == null) return result;
                // 注意：selectPatientById返回的住院号别名是patient_no，不是in_hospital_no
                String inHospitalNo = (String) patient.get("patient_no");
                if (inHospitalNo == null || inHospitalNo.isEmpty()) return result;

                Map<String, String> configMap = loadConfigMap("lis_item");
                String itemCodes = configMap.get(metricKey);
                if (itemCodes == null || itemCodes.isEmpty()) return result;

                List<String> codeList = Arrays.asList(itemCodes.split(","));
                List<Map<String, Object>> records = icuPatientMapper.selectLabRecordsByCodes(inHospitalNo, codeList, startTime, endTime);
                for (Map<String, Object> rec : records) {
                    String valueStr = (String) rec.get("item_value");
                    Object timeObj = rec.get("item_time");
                    if (valueStr != null && timeObj != null) {
                        try {
                            double val = Double.parseDouble(valueStr.trim());
                            // HCT数据库存小数如0.14，需×100转百分比
                            if ("hct".equals(metricKey) && val < 1) {
                                val = val * 100;
                            }
                            Map<String, Object> point = new HashMap<>();
                            point.put("time", timeObj.toString());
                            point.put("value", val);
                            result.add(point);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } else if (bloodGasMetrics.contains(metricKey)) {
                // 血气分析：通过lis_item_name查询（PH值/氧分压）
                String itemName = "ph".equals(metricKey) ? "PH值" : "氧分压";
                List<Map<String, Object>> records = icuPatientMapper.selectBloodGasRecords(
                        patientId, Arrays.asList(itemName), startTime, endTime);
                for (Map<String, Object> rec : records) {
                    String valueStr = (String) rec.get("item_value");
                    Object timeObj = rec.get("item_time");
                    if (valueStr != null && !valueStr.isEmpty() && timeObj != null) {
                        try {
                            Map<String, Object> point = new HashMap<>();
                            point.put("time", timeObj.toString());
                            point.put("value", Double.parseDouble(valueStr.trim()));
                            result.add(point);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }

            // 按时间升序排列
            result.sort((a, b) -> {
                String ta = (String) a.get("time");
                String tb = (String) b.get("time");
                return ta != null && tb != null ? ta.compareTo(tb) : 0;
            });
        } catch (Exception e) {
            log.error("获取指标趋势数据失败: patientId={}, metricKey={}", patientId, metricKey, e);
        }
        return result;
    }

    @Override
    public Map<String, Object> getOxygenTrend(String patientId, String startTime, String endTime) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fio2", new ArrayList<>());
        out.put("pao2", new ArrayList<>());
        out.put("aado2", new ArrayList<>());
        out.put("worstTime", null);
        out.put("worstScore", 0);
        if (patientId == null || patientId.trim().isEmpty()) return out;
        try {
            List<Map<String, Object>> observeRecords = icuPatientMapper.selectObserveRecords(patientId, startTime, endTime);
            List<Map<String, Object>> bloodGasRecords = icuPatientMapper.selectBloodGasRecords(
                    patientId, Arrays.asList("PH值", "氧分压", "二氧化碳分压"), startTime, endTime);
            // 与评分取数同源同口径（含“最差一管”的选中时间），供趋势图高亮
            Map<String, Object> oxygen = buildOxygenData(
                    observeRecords, loadConfigMap("observe_item").get("fio2"), bloodGasRecords);
            out.put("fio2", oxygen.get("fio2Points"));
            out.put("pao2", oxygen.get("pao2Points"));
            out.put("aado2", oxygen.get("aado2Points"));
            out.put("worstTime", oxygen.get("worstTime"));
            out.put("worstScore", oxygen.get("worstScore"));
            out.put("worstFio2", oxygen.get("worstFio2"));
            out.put("worstPao2", oxygen.get("worstPao2"));
            out.put("worstAado2", oxygen.get("worstAado2"));
        } catch (Exception e) {
            log.error("获取氧合趋势失败: patientId={}", patientId, e);
        }
        return out;
    }

    // ==================== GCS 从重症系统同步 / 在科患者自动初评 ====================

    @Override
    public List<Map<String, Object>> listSystemGcs(String patientId) {
        if (patientId == null || patientId.trim().isEmpty()) return new ArrayList<>();
        try {
            return parseGcsRows(icuPatientMapper.selectGcsDocRecords(patientId));
        } catch (Exception e) {
            log.error("查询重症系统GCS记录失败: patientId={}", patientId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 取「指定取数范围内」最新一条“评全且非插管”的系统 GCS（E/V/M 齐全），用于评分自动同步。
     * 同步到的记录严格落在 startTime~endTime 内；无可用记录返回 null（C 项按未评估计 0）。
     */
    private Map<String, Object> latestSystemGcs(String patientId, String startTime, String endTime) {
        if (patientId == null || patientId.trim().isEmpty()) return null;
        try {
            // mapper 已按 record_time DESC 排序，第一条三项齐全的即“范围内最新一条评全记录”
            for (Map<String, Object> g : parseGcsRows(
                    icuPatientMapper.selectGcsDocRecordsByRange(patientId, startTime, endTime))) {
                if (g.get("eye") != null && g.get("verbal") != null && g.get("motor") != null) {
                    return g;
                }
            }
        } catch (Exception e) {
            // 同步 GCS 失败不影响其它生理/检验取数
            log.warn("同步重症系统GCS失败: patientId={}", patientId, e);
        }
        return null;
    }

    /** 解析系统 GCS 文书原始行 → 结构化的 E/V/M 记录（保持 SQL 的 record_time 倒序） */
    private List<Map<String, Object>> parseGcsRows(List<Map<String, Object>> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        ObjectMapper om = new ObjectMapper();
        for (Map<String, Object> row : rows) {
            try {
                Object sj = row.get("score_json");
                if (sj == null || sj.toString().trim().isEmpty()) continue;
                JsonNode node = om.readTree(sj.toString());
                Integer eye = parseGcsDim(node, "item1", 1, 4);
                Integer motor = parseGcsDim(node, "item3", 1, 6);
                String item4 = node.hasNonNull("item4") ? node.get("item4").asText("").trim() : "";
                boolean intubated = "ET".equalsIgnoreCase(item4);
                // item4=ET（气管插管/气切）时言语反应无法评估，即使 item2 带数值也不自动选 V
                Integer verbal = intubated ? null : parseGcsDim(node, "item2", 1, 5);

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("recordTime", row.get("record_time") == null ? null : row.get("record_time").toString());
                item.put("recordStaffName", row.get("record_staff_name"));
                item.put("eye", eye);
                item.put("verbal", verbal);
                item.put("motor", motor);
                item.put("intubated", intubated);
                String totalText = node.hasNonNull("totalScore") ? node.get("totalScore").asText()
                        : ((eye != null && verbal != null && motor != null) ? String.valueOf(eye + verbal + motor) : "—");
                item.put("totalText", totalText);
                // 可直接同步：E/M 必有、V 有数值（非插管且三项评全）
                item.put("auditable", eye != null && motor != null && verbal != null);
                out.add(item);
            } catch (Exception ex) {
                log.warn("解析重症系统GCS记录失败，已跳过: docRecordId={}", row.get("doc_record_id"), ex);
            }
        }
        return out;
    }

    /** 解析 GCS 单个维度数值，越界/非数字/缺失返回 null */
    private Integer parseGcsDim(JsonNode node, String key, int lo, int hi) {
        if (node == null || !node.hasNonNull(key)) return null;
        String s = node.get(key).asText("").trim();
        if (!s.matches("-?\\d+")) return null;
        try {
            int v = Integer.parseInt(s);
            return (v >= lo && v <= hi) ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 自动初评互斥锁：定时任务与手动触发（/auto-generate）可能并发，
     * 在单实例内串行化，避免“先 selectCount 再 insert”的竞态导致同一患者重复建档。
     */
    private final java.util.concurrent.locks.ReentrantLock autoGenerateLock =
            new java.util.concurrent.locks.ReentrantLock();

    @Override
    public Map<String, Object> autoGenerateScores(String departCode, int overHours) {
        autoGenerateLock.lock();
        try {
            return doAutoGenerateScores(departCode, overHours);
        } finally {
            autoGenerateLock.unlock();
        }
    }

    private Map<String, Object> doAutoGenerateScores(String departCode, int overHours) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> createdList = new ArrayList<>();
        List<Map<String, Object>> skippedList = new ArrayList<>();
        List<Map<String, Object>> failedList = new ArrayList<>();

        String dc = (departCode == null || departCode.trim().isEmpty()) ? null : departCode.trim();
        List<Map<String, Object>> patients;
        try {
            patients = icuPatientMapper.selectInDepartPatientsOverHours(overHours, dc);
        } catch (Exception e) {
            log.error("自动初评：查询在科超{}小时患者失败", overHours, e);
            throw new RuntimeException("查询在科患者失败: " + e.getMessage(), e);
        }

        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        // 12项急性生理原始值键（落 apsData，供医生打开回填复核）
        List<String> apsKeys = Arrays.asList("temperature", "map", "heartRate", "respiratoryRate",
                "fio2", "pao2", "aado2", "ph", "sodium", "potassium", "creatinine", "hct", "wbc");
        ObjectMapper om = new ObjectMapper();
        LocalDateTime now = LocalDateTime.now();

        for (Map<String, Object> p : patients) {
            String inNo = toStr(p.get("in_hospital_no"));
            String pid = toStr(p.get("patient_id"));
            String name = toStr(p.get("name"));
            try {
                if (inNo.isEmpty()) { failedList.add(brief(p, "住院号为空")); continue; }
                // 幂等：该患者已有任意正常评分记录则跳过，不重复生成
                Long exist = scoreRecordMapper.selectCount(new LambdaQueryWrapper<Apache2ScoreRecord>()
                        .eq(Apache2ScoreRecord::getInHospitalNo, inNo)
                        .eq(Apache2ScoreRecord::getStatus, 1));
                if (exist != null && exist > 0) { skippedList.add(brief(p, "已有评分记录")); continue; }

                LocalDateTime inTime = toLocalDateTime(p.get("in_depart_time"));
                String start = fmt.format(inTime != null ? inTime : now.minusHours(overHours));
                String end = fmt.format(inTime != null ? inTime.plusHours(overHours) : now);

                Map<String, Object> fetched = autoFetchPhysiologyData(pid, start, end);
                Map<String, Object> params = new HashMap<>(fetched);
                params.put("age", p.get("age"));
                params.put("chronicHealth", "none");
                params.put("diagnosisType", "none");
                // 若取数范围（入科后24h）内有评全（非插管）的系统 GCS，取最新一条计入 C 项；
                // 与手工「自动获取」同源同范围（start~end），确保同步的 GCS 不超出取数窗口。
                Map<String, Object> gcs = latestSystemGcs(pid, start, end);
                Integer gcsTotal = null;
                if (gcs != null) {
                    gcsTotal = toInt(gcs.get("eye")) + toInt(gcs.get("verbal")) + toInt(gcs.get("motor"));
                    params.put("gcsTotal", gcsTotal);
                }

                Map<String, Object> calc = calculateScore(params);

                Apache2ScoreRecord r = new Apache2ScoreRecord();
                r.setPatientId(pid);
                r.setInHospitalNo(inNo);
                r.setPatientName(name);
                r.setDepartCode(toStr(p.get("depart_code")));
                r.setScoreTime(now);
                r.setScoreType("auto");
                r.setAgeScore(toInt(calc.get("ageScore")));
                r.setChronicScore(toInt(calc.get("chronicScore")));
                r.setGcsScore(toInt(calc.get("gcsScore")));
                r.setPhysiologyScore(toInt(calc.get("physiologyScore")));
                r.setTotalScore(toInt(calc.get("totalScore")));
                Object mr = calc.get("mortalityRate");
                r.setMortalityRate(mr == null ? null : new BigDecimal(String.valueOf(mr)));
                Object dw = calc.get("diagnosisWeight");
                r.setDiagnosisWeight(dw == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(dw)));
                r.setEmergencySurgery(0);
                r.setChronicHealth("none");
                r.setDiagnosisType("none");
                r.setGcsDetail(gcsTotal == null ? "未评估" : ("系统同步GCS=" + gcsTotal));
                // 仅落客观生理原始值，供医生打开回填复核
                Map<String, Object> aps = new LinkedHashMap<>();
                for (String k : apsKeys) if (fetched.get(k) != null) aps.put(k, fetched.get(k));
                // 同步到的系统 GCS 一并落进 apsData：否则医生打开这条自动初评记录时
                // E/V/M 三项为空白（看上去像「没同步到 GCS」），也无法复核 C 项分值。
                if (gcs != null) {
                    aps.put("gcsEye", gcs.get("eye"));
                    aps.put("gcsVerbal", gcs.get("verbal"));
                    aps.put("gcsMotor", gcs.get("motor"));
                }
                r.setApsData(om.writeValueAsString(aps));
                r.setDataStartTime(inTime != null ? inTime : now.minusHours(overHours));
                r.setDataEndTime(inTime != null ? inTime.plusHours(overHours) : now);
                r.setRemark("自动评分：基于入科后" + overHours + "h客观监护/检验数据取最差值计算；GCS、慢性健康、诊断分类需主管医生复核确认。");
                r.setCreateBy("系统自动");
                r.setCreateTime(now);
                r.setStatus(1);
                scoreRecordMapper.insert(r);

                Map<String, Object> ok = brief(p, null);
                ok.put("recordId", r.getId());
                ok.put("totalScore", r.getTotalScore());
                createdList.add(ok);
            } catch (Exception ex) {
                log.error("自动初评失败: inHospitalNo={}, name={}", inNo, name, ex);
                failedList.add(brief(p, ex.getMessage()));
            }
        }

        result.put("overHours", overHours);
        result.put("scanned", patients.size());
        result.put("created", createdList.size());
        result.put("skipped", skippedList.size());
        result.put("failed", failedList.size());
        result.put("createdList", createdList);
        result.put("skippedList", skippedList);
        result.put("failedList", failedList);
        log.info("APACHE2自动初评完成: 扫描{} 新增{} 跳过{} 失败{}",
                patients.size(), createdList.size(), skippedList.size(), failedList.size());
        return result;
    }

    private Map<String, Object> brief(Map<String, Object> p, String reason) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inHospitalNo", toStr(p.get("in_hospital_no")));
        m.put("patientName", toStr(p.get("name")));
        m.put("bedCode", toStr(p.get("bed_code")));
        m.put("departCode", toStr(p.get("depart_code")));
        if (reason != null) m.put("reason", reason);
        return m;
    }

    private String toStr(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private int toInt(Object o) {
        if (o == null) return 0;
        try {
            return (int) Math.round(Double.parseDouble(String.valueOf(o)));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** ICU 时间字段可能是 LocalDateTime / Timestamp / String，统一转 LocalDateTime */
    private LocalDateTime toLocalDateTime(Object o) {
        if (o == null) return null;
        if (o instanceof LocalDateTime) return (LocalDateTime) o;
        if (o instanceof java.sql.Timestamp) return ((java.sql.Timestamp) o).toLocalDateTime();
        if (o instanceof java.util.Date) {
            return LocalDateTime.ofInstant(((java.util.Date) o).toInstant(), java.time.ZoneId.systemDefault());
        }
        String s = String.valueOf(o).trim();
        try {
            if (s.length() >= 19) {
                return LocalDateTime.parse(s.substring(0, 19).replace('T', ' '),
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
        } catch (Exception ignore) { }
        return null;
    }
}