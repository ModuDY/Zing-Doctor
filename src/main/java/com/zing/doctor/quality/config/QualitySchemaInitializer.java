package com.zing.doctor.quality.config;

import com.zing.doctor.quality.mapper.QualityMetricDefMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 质控表结构启动自检：代码已用到、但老库要靠增量 SQL 才有的列。
 *
 * <p><b>为什么要有这个检查</b>：质控的配置真源是数据库表，代码与增量 SQL 必须同步升级。
 * 只换 jar、漏跑增量脚本时，MyBatis 的 {@code selectList}（列名是显式列出的）会直接报
 * <pre>无效的列名[patient_fields]</pre>
 * 页面表现为「质控指标配置」打开即 500，而栈里全是 JDBC / MyBatis 调用链，
 * 完全看不出「该去哪里补这一列」——现场已经因此排了一轮。
 * 这里在启动时把缺的列点名，并直接给出要执行的脚本，省掉那次排查。
 *
 * <p><b>刻意只记 ERROR、不抛异常</b>：首次部署时表可能都还没建（由 install.sh 负责），
 * 自检在这里阻断启动只会让问题更难定位。
 */
@Component
public class QualitySchemaInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(QualitySchemaInitializer.class);

    /** 质控表所在 schema（与各 Mapper 里硬编码的一致）。 */
    private static final String SCHEMA = "zing_doctor_db_prod";

    /**
     * 需要自检的列：表 / 列 / 对应增量脚本。
     *
     * <p>只登记「代码读得到、老库缺了会 500」的列；新增一次表结构变更就在这里加一行。
     */
    private static final String[][] REQUIRED_COLUMNS = {
            {"quality_metric_def", "patient_fields", "sql/15_quality_patient_fields.sql"},
            {"quality_metric_def", "numerator_metric", "sql/16_quality_fatality_ref.sql"},
            {"quality_count_rule", "origin", "sql/17_quality_rule_local.sql"},
    };

    private final QualityMetricDefMapper metricDefMapper;

    public QualitySchemaInitializer(QualityMetricDefMapper metricDefMapper) {
        this.metricDefMapper = metricDefMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String[] required : REQUIRED_COLUMNS) {
            String table = required[0];
            String column = required[1];
            String script = required[2];
            try {
                if (metricDefMapper.countColumn(SCHEMA, table, column) > 0) {
                    continue;
                }
                log.error("[质控] 表 {}.{} 缺少列 [{}]：代码已用到它，数据库还没有 —— "
                                + "相关页面会报「无效的列名[{}]」（如质控指标配置页打开即 500）。"
                                + "请执行 {} 后刷新页面，无需重启。",
                        SCHEMA, table, column, column, script);
            } catch (Exception e) {
                // 表尚未创建（首次部署还没跑初始化）或账号无元数据权限：
                // 交给 install.sh 的提示，自检不阻断启动
                log.debug("[质控] 表结构自检跳过 {}.{}: {}", SCHEMA, table, e.getMessage());
            }
        }
    }
}
