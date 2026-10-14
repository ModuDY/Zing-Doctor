package com.zing.doctor.quality.engine;

import com.zing.doctor.quality.config.QualityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 事实层物化键的窗口指纹（离线，不连库、不启动 Spring）。
 *
 * <p>守的是一类<b>不报错的错</b>：物化表名与缓存键原先只用窗口起点，
 * 于是「10-01~10-07」和「10-01~10-15」共用一个键 —— 第二次查询直接复用第一张表，
 * 拿到的是第一段的数据。数字偏小、页面正常、日志干净，只有对着源数据核才发现。
 *
 * <p>另一头必须钉住的是「自然周期不能改表名」：日/月/季/年窗口的终点由起点唯一决定，
 * 本来就不会撞，加指纹只会让既有的物理表白白重建一次。
 */
class QualityEngineWindowKeyTest {

    private QualityEngine engine;

    @BeforeEach
    void setUp() {
        QualityDslLoader dsl = new QualityDslLoader(new QualityProperties(), null);
        dsl.reload();
        engine = new QualityEngine(dsl, new SqlCompiler(), null, null, null, new QualityExpressionAnalyzer());
    }

    @Test
    @DisplayName("自然周期窗口不带终点指纹，物理表名与改造前一致")
    void naturalWindowsKeepLegacySuffix() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);

        assertEquals("", engine.windowSuffix(start, start.plusDays(1)));
        assertEquals("", engine.windowSuffix(start, start.plusMonths(1)));
        assertEquals("", engine.windowSuffix(start, start.plusMonths(3)));
        assertEquals("", engine.windowSuffix(start, start.plusYears(1)));
    }

    @Test
    @DisplayName("自定义区间带终点指纹：起点相同、终点不同的窗口不再共用一张物化表")
    void customWindowsCarryEndFingerprint() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);

        String week = engine.windowSuffix(start, start.plusDays(7));
        String fortnight = engine.windowSuffix(start, start.plusDays(14));

        assertEquals("_e20261008", week);
        assertEquals("_e20261015", fortnight);
        assertNotEquals(week, fortnight);
    }

    @Test
    @DisplayName("从 1 号起的跨月自定义区间也要与自然月区分开")
    void customWindowStartingOnMonthFirstStaysDistinctFromNaturalMonth() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);

        String naturalMonth = engine.windowSuffix(start, start.plusMonths(1));
        String custom = engine.windowSuffix(start, start.plusDays(20));

        assertNotEquals(naturalMonth, custom);
        assertTrue(custom.startsWith("_e"), custom);
    }

    @Test
    @DisplayName("起点或终点缺失时不加指纹：缺参数的调用不制造新表名")
    void nullBoundariesFallBackToLegacySuffix() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 0, 0);

        assertEquals("", engine.windowSuffix(start, null));
        assertEquals("", engine.windowSuffix(null, start));
    }
}
