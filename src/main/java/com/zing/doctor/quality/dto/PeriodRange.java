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

    /**
     * 解析请求参数（含结束日）。
     *
     * <p><b>为什么必须带上 periodEnd</b>：只给起始日时，CUSTOM 档位此前按「起始日 + 1 天」
     * 推导区间，于是页面上写着「9/1 ~ 10/14」，实际只算了 9/1 一天 ——
     * 这种不一致不会报任何错，只会让人拿着一个月的结论做决定。
     * 传入结束日后，区间以 periodEnd <b>当天 24:00</b> 为界（左闭右开，含结束当天）。
     *
     * @param periodEnd 结束日，支持 yyyy-MM-dd / yyyy-MM-dd HH:mm:ss；空值时退回单参数语义
     */
    public static PeriodRange of(String periodType, String periodStart, String periodEnd) {
        String type = periodType == null || periodType.trim().isEmpty()
                ? "MONTH" : periodType.trim().toUpperCase();
        if ("CUSTOM".equals(type) && periodEnd != null && !periodEnd.trim().isEmpty()) {
            LocalDateTime s = parseStart(periodStart);
            // 结束日含当天：+1 天后按左闭右开取值，避免漏掉结束当天下午与夜间的数据
            LocalDateTime e = parseStart(periodEnd).plusDays(1);
            if (e.isAfter(s)) {
                return new PeriodRange(type, s, e);
            }
            // 结束早于开始时按一天处理：宁可给出一个明确的单日区间，也不要生成倒挂区间
            return new PeriodRange(type, s, s.plusDays(1));
        }
        return of(periodType, periodStart);
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
                // 未给结束日时按天：调用方应传 periodEnd（见上面的重载）
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
