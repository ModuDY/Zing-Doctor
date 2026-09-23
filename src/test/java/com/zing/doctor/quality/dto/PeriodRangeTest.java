package com.zing.doctor.quality.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * PeriodRange 解析单元测试。
 *
 * <p>钉死「周期类型 + 起始字符串 → 时间区间」的口径，防止季度/年度再次串台
 * （现场 bug：选 Q3 时 period_start=2026-07-01，与 MONTH 2026-07 撞键，
 *  查询不带 period_type 就把单月数据当季度结果返回）。
 */
class PeriodRangeTest {

    @Test
    @DisplayName("月度：yyyy-MM → 当月 1 日 00:00:00 ~ 下月 1 日 00:00:00")
    void month() {
        PeriodRange r = PeriodRange.of("MONTH", "2026-07");
        assertEquals(LocalDateTime.of(2026, 7, 1, 0, 0), r.getStart());
        assertEquals(LocalDateTime.of(2026, 8, 1, 0, 0), r.getEnd());
        assertEquals("MONTH", r.getPeriodType());
    }

    @Test
    @DisplayName("季度：yyyy-MM（季度首月） → 首月 1 日 ~ 3 个月后 1 日")
    void quarter() {
        // Q3 = 7/1 ~ 10/1
        PeriodRange q3 = PeriodRange.of("QUARTER", "2026-07");
        assertEquals(LocalDateTime.of(2026, 7, 1, 0, 0), q3.getStart());
        assertEquals(LocalDateTime.of(2026, 10, 1, 0, 0), q3.getEnd());

        // Q1 = 1/1 ~ 4/1
        PeriodRange q1 = PeriodRange.of("QUARTER", "2026-01");
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), q1.getStart());
        assertEquals(LocalDateTime.of(2026, 4, 1, 0, 0), q1.getEnd());
    }

    @Test
    @DisplayName("年度：yyyy → 1/1 ~ 次年 1/1")
    void year() {
        PeriodRange r = PeriodRange.of("YEAR", "2026");
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), r.getStart());
        assertEquals(LocalDateTime.of(2027, 1, 1, 0, 0), r.getEnd());
        assertEquals("YEAR", r.getPeriodType());
    }

    @Test
    @DisplayName("季度与月度 period_start 相同是正常的（Q1 与 1 月都在 1/1 起点），查询必须带 period_type 区分")
    void quarterAndMonthCanShareStart() {
        PeriodRange q1 = PeriodRange.of("QUARTER", "2026-01");
        PeriodRange jan = PeriodRange.of("MONTH", "2026-01");
        assertEquals(q1.getStart(), jan.getStart(),
            "Q1 与 1 月月度起点相同是设计如此；查询层必须靠 period_type 区分");
        // 但结束不同
        assertEquals(LocalDateTime.of(2026, 4, 1, 0, 0), q1.getEnd());
        assertEquals(LocalDateTime.of(2026, 2, 1, 0, 0), jan.getEnd());
    }

    @Test
    @DisplayName("空 periodType 默认 MONTH；空 periodStart 默认上月")
    void defaults() {
        PeriodRange r = PeriodRange.of(null, null);
        assertEquals("MONTH", r.getPeriodType());
        assertNotNull(r.getStart());
    }

    @Test
    @DisplayName("monthOf/yearOf 工厂方法")
    void factories() {
        PeriodRange m = PeriodRange.monthOf(2026, 8);
        assertEquals(LocalDateTime.of(2026, 8, 1, 0, 0), m.getStart());
        assertEquals(LocalDateTime.of(2026, 9, 1, 0, 0), m.getEnd());

        PeriodRange y = PeriodRange.yearOf(2026);
        assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), y.getStart());
        assertEquals(LocalDateTime.of(2027, 1, 1, 0, 0), y.getEnd());
    }
}
