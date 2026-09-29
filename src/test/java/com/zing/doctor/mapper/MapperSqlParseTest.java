package com.zing.doctor.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.zing.doctor.icu.mapper.IcuPatientMapper;
import com.zing.doctor.quality.engine.QualitySqlMapper;
import com.zing.doctor.quality.mapper.QualityCalcRunMapper;
import com.zing.doctor.quality.mapper.QualityCalcTraceMapper;
import com.zing.doctor.quality.mapper.QualityIndexMapper;
import com.zing.doctor.quality.mapper.QualityMetricPatientMapper;
import com.zing.doctor.quality.mapper.QualityMetricResultMapper;
import com.zing.doctor.quality.mapper.QualityMonthlyReportMapper;
import com.zing.doctor.module.antibiotic.mapper.AbxWordConfigMapper;
import com.zing.doctor.module.antibiotic.mapper.AdviceLogMapper;
import com.zing.doctor.module.antibiotic.mapper.DecisionRecordMapper;
import com.zing.doctor.module.antibiotic.mapper.DddConfigMapper;
import com.zing.doctor.module.antibiotic.mapper.ExternalAccessLogMapper;
import com.zing.doctor.module.antibiotic.mapper.MdroConfigMapper;
import com.zing.doctor.module.antibiotic.mapper.PageConfigMapper;
import com.zing.doctor.module.apache2.mapper.Apache2ConfigMapper;
import com.zing.doctor.module.apache2.mapper.Apache2ScoreRecordMapper;
import com.zing.doctor.module.handover.mapper.HandoverNoteMapper;
import com.zing.doctor.module.sepsis.mapper.SepsisBundleRecordMapper;
import com.zing.doctor.module.sofa.mapper.SofaConfigMapper;
import com.zing.doctor.module.sofa.mapper.SofaScoreRecordMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mapper 注解 SQL 解析测试（<b>不需要数据库、不启动 Spring</b>）。
 *
 * <p><b>为什么要有这个测试</b>：{@code @Select} 中一旦使用 {@code <script>} 块，
 * 其内 SQL 会按 <b>XML</b> 解析，未转义的 {@code <}、{@code &} 会导致
 * <b>启动期</b> {@code SAXParseException: 元素内容必须由格式正确的字符数据或标记组成}，
 * 应用直接起不来。而普通单测不启动 Spring，这类问题无法暴露（曾因此漏到线上），
 * 故在此直接让 MyBatis/MyBatis-Plus 解析全部 Mapper 予以拦截。
 *
 * <p><b>约定</b>：{@code <script>} 块内的 SQL，比较符必须写成
 * {@code &lt;} / {@code &lt;=} / {@code &gt;} / {@code &gt;=}；
 * 非 script 的普通 {@code @Select} 不经过 XML 解析，可保留原样。
 */
class MapperSqlParseTest {

    /** 全量 Mapper 接口（新增 Mapper 请同步登记，解析失败会在此暴露） */
    private static final List<Class<?>> MAPPERS = Arrays.asList(
            IcuPatientMapper.class,
            SofaScoreRecordMapper.class,
            SofaConfigMapper.class,
            Apache2ScoreRecordMapper.class,
            Apache2ConfigMapper.class,
            HandoverNoteMapper.class,
            SepsisBundleRecordMapper.class,
            PageConfigMapper.class,
            MdroConfigMapper.class,
            ExternalAccessLogMapper.class,
            DecisionRecordMapper.class,
            DddConfigMapper.class,
            AdviceLogMapper.class,
            AbxWordConfigMapper.class,
            // 质控指标中台（其中 3 个含 <script> 批量插入，块内裸 < / & 会导致启动期解析失败）
            QualityIndexMapper.class,
            QualityCalcRunMapper.class,
            QualityCalcTraceMapper.class,
            QualityMetricResultMapper.class,
            QualityMetricPatientMapper.class,
            QualityMonthlyReportMapper.class,
            QualitySqlMapper.class
    );

    @Test
    @DisplayName("全部 Mapper 的注解 SQL 均可被 MyBatis 解析（script 块内比较符须转义）")
    void allMapperSqlParsable() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        for (Class<?> mapper : MAPPERS) {
            // 解析失败（如 script 块内裸 <）会在此抛出 BuilderException / SAXParseException
            cfg.addMapper(mapper);
        }
        for (Class<?> mapper : MAPPERS) {
            assertTrue(cfg.hasMapper(mapper), "Mapper 未成功注册: " + mapper.getName());
        }
    }

    @Test
    @DisplayName("IcuPatientMapper：时间窗重叠等带 <script> 的语句可解析")
    void icuPatientMapperScriptStatementsParsable() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.addMapper(IcuPatientMapper.class);
        assertTrue(cfg.hasMapper(IcuPatientMapper.class));
    }
}
