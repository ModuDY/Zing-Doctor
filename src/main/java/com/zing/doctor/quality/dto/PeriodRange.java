package com.zing.doctor.quality.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 统计周期区间（左闭右开）。
 *
 * <p>统一在这里把「周期类型 + 起始」解析成时间区间，避免各处重复推导，
 * 也让跨月切分（床日等）有唯一口径。
 */
public class PeriodRange {

    public static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String periodType;
    private final LocalDateTime start;
    private final LocalDateTime end;

    public PeriodRange(String periodType, LocalDateTime start, LocalDateTime end) {
        this.periodType = periodType;
        this.start = start;
        this.end = end;
    }

    /** 解析请求参数：periodStart 支持 yyyy-MM-dd / yyyy-MM / yyyy-MM-dd HH:mm:ss。 */
    public static PeriodRange of(String periodType, String periodStart) {
        String type = periodType == null || periodType.trim().isEmpty()
                ? "MONTH" : periodType.trim().toUpperCase();
        LocalDateTime s = parseStart(periodStart);
        LocalDateTime e;
        switch (type) {
            case "YEAR":
                e = s.plusYears(1);
                break;
            case "QUARTER":
                e = s.plusMonths(3);
                break;
            case "CUSTOM":
                // 自定义周期默认按天
                e = s.plusDays(1);
                break;
            case "MONTH":
            default:
                e = s.plusMonths(1);
                break;
        }
        return new PeriodRange(type, s, e);
    }

    public static PeriodRange monthOf(int year, int month) {
        LocalDateTime s = LocalDateTime.of(year, month, 1, 0, 0, 0);
        return new PeriodRange("MONTH", s, s.plusMonths(1));
    }

    public static PeriodRange yearOf(int year) {
        LocalDateTime s = LocalDateTime.of(year, 1, 1, 0, 0, 0);
        return new PeriodRange("YEAR", s, s.plusYears(1));
    }

    /** 自定义区间。 */
    public static PeriodRange custom(LocalDateTime start, LocalDateTime end) {
        return new PeriodRange("CUSTOM", start, end);
    }

    private static LocalDateTime parseStart(String text) {
        if (text == null || text.trim().isEmpty()) {
            // 缺省为上个月
            LocalDate d = LocalDate.now().withDayOfMonth(1).minusMonths(1);
            return d.atStartOfDay();
        }
        String t = text.trim();
        try {
            if (t.length() == 4) {
                return LocalDate.of(Integer.parseInt(t), 1, 1).atStartOfDay();
            }
            if (t.length() == 7) {
                return LocalDate.parse(t + "-01").atStartOfDay();
            }
            if (t.length() > 10) {
                if (t.length() > 19) {
                    t = t.substring(0, 19);
                }
                return LocalDateTime.parse(t.replace('T', ' '), DT);
            }
            return LocalDate.parse(t.length() == 10 ? t : t.substring(0, 10)).atStartOfDay();
        } catch (Exception e) {
            LocalDate d = LocalDate.now().withDayOfMonth(1).minusMonths(1);
            return d.atStartOfDay();
        }
    }

    public String getPeriodType() {
        return periodType;
    }

    public LocalDateTime getStart() {
        return start;
    }

    public LocalDateTime getEnd() {
        return end;
    }

    /** 事实表后缀（yyyyMMdd）。 */
    public String suffix() {
        return String.format("%04d%02d%02d", start.getYear(), start.getMonthValue(), start.getDayOfMonth());
    }

    public int getYear() {
        return start.getYear();
    }

    public int getMonth() {
        return start.getMonthValue();
    }

    public String getStartText() {
        return DT.format(start);
    }

    public String getEndText() {
        return DT.format(end);
    }
}
