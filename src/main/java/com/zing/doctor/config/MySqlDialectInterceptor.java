package com.zing.doctor.config;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MySQL 方言拦截器：仅在 spring.profiles.active=mysql 时注册。
 *
 * <p>达梦/Oracle 风格 SQL 在执行前自动翻译成 MySQL 等价写法，
 * 让同一套 Mapper 注解 SQL 同时兼容达梦 DM8 和 MySQL 8.x。
 *
 * <p>转换规则（按执行顺序）：
 * <ol>
 *   <li>{@code ) WHERE ROWNUM <= n} → {@code ) t LIMIT n}（子查询取前 N 条）</li>
 *   <li>{@code AND ROWNUM <= n}（WHERE 子句内）→ 移除并在语句末尾补 LIMIT n</li>
 *   <li>{@code SYSDATE - n}（天）→ {@code DATE_SUB(NOW(), INTERVAL n DAY)}</li>
 *   <li>{@code SYSDATE - (expr / 24.0)}（小时）→ {@code DATE_SUB(NOW(), INTERVAL expr HOUR)}</li>
 *   <li>残留的裸 {@code SYSDATE} → {@code NOW()}</li>
 *   <li>{@code DATEDIFF(HOUR, a, b)}（达梦带单位）→ {@code TIMESTAMPDIFF(HOUR, a, b)}</li>
 *   <li>{@code REGEXP_LIKE(x, 'p')} → {@code (x REGEXP 'p')}</li>
 *   <li>{@code CAST(x AS VARCHAR(n))} → {@code CAST(x AS CHAR(n))}；{@code AS INT/INTEGER} → {@code AS SIGNED}</li>
 *   <li>{@code ALL_TAB_COLUMNS/OWNER/DATA_LENGTH} 元数据查询 → {@code information_schema.COLUMNS/TABLE_SCHEMA/CHARACTER_MAXIMUM_LENGTH}</li>
 *   <li>{@code a || b} 字符串连接 → {@code CONCAT(a, b)}</li>
 *   <li>{@code ORDER BY col NULLS LAST} → {@code ORDER BY col IS NULL, col}</li>
 * </ol>
 *
 * <p>注意两点：
 * <ul>
 *   <li>{@code #{param}} 在 BoundSql 里已变成 {@code ?}，所以正则必须同时接受 {@code ?} 与 {@code #{name}}；</li>
 *   <li>质控事实层 SQL 经 {@code QualitySqlMapper}（MyBatis）执行，同样会被本拦截器覆盖。</li>
 * </ul>
 *
 * <p>双引号标识符（{@code "col_name"}）通过 JDBC URL 的 {@code ANSI_QUOTES} 模式兼容，不在此处理。
 */
@Component
@Profile({"mysql", "mariadb"})
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                        org.apache.ibatis.cache.CacheKey.class, BoundSql.class})
})
public class MySqlDialectInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(MySqlDialectInterceptor.class);

    // ) WHERE ROWNUM <= n / ?  →  ) t LIMIT n / ?
    private static final Pattern ROWNUM_WRAPPER = Pattern.compile(
            "\\)\\s+WHERE\\s+ROWNUM\\s*<=\\s*(\\d+|\\?|#\\{\\w+})", Pattern.CASE_INSENSITIVE);

    // AND ROWNUM <= n（在 WHERE 子句末尾、没有外层包装的情况）
    private static final Pattern ROWNUM_IN_WHERE = Pattern.compile(
            "\\s+AND\\s+ROWNUM\\s*<=\\s*(\\d+|\\?)", Pattern.CASE_INSENSITIVE);

    // SYSDATE - 7 / SYSDATE - ?（天）
    private static final Pattern SYSDATE_DAYS = Pattern.compile(
            "SYSDATE\\s*-\\s*(\\d+|\\?|#\\{\\w+})", Pattern.CASE_INSENSITIVE);

    // SYSDATE - (expr / 24.0)（小时换算；expr 可能是 #{hours} 也可以是 ? 或列名）
    private static final Pattern SYSDATE_HOURS = Pattern.compile(
            "SYSDATE\\s*-\\s*\\(\\s*([^()]*?)\\s*/\\s*24\\.0\\s*\\)", Pattern.CASE_INSENSITIVE);

    // 残留裸 SYSDATE（如 >= SYSDATE）→ NOW()
    private static final Pattern SYSDATE_BARE = Pattern.compile(
            "\\bSYSDATE\\b(?!\\s*\\()", Pattern.CASE_INSENSITIVE);

    // 达梦带单位的 DATEDIFF(HOUR, a, b) → MySQL TIMESTAMPDIFF(HOUR, a, b)
    // ⚠️ 语义提醒：TIMESTAMPDIFF 是「整单位差」，与按边界计数的 DATEDIFF 在跨零点时可能差 1
    //（例：23:00 → 次日 01:00，TIMESTAMPDIFF(DAY)=0）。质控里「住院天数/ICU 天数」这类
    // 指标若与达梦库对不上 ±1，需改成按日历日翻译（DATEDIFF(DATE(b), DATE(a))），
    // 这属于业务口径问题，先按整单位差实现并在现场核对具体指标。
    private static final Pattern DATEDIFF_UNIT = Pattern.compile(
            "DATEDIFF\\s*\\(\\s*(DAY|HOUR|MINUTE|SECOND|WEEK|MONTH|YEAR)\\s*,",
            Pattern.CASE_INSENSITIVE);

    // REGEXP_LIKE(x, '^[0-9]+$') → (x REGEXP '^[0-9]+$')（仅两参数形式）
    private static final Pattern REGEXP_LIKE = Pattern.compile(
            "REGEXP_LIKE\\s*\\(\\s*([^,]+?)\\s*,\\s*('[^']*')\\s*\\)", Pattern.CASE_INSENSITIVE);

    // CAST(x AS VARCHAR(n))
    private static final Pattern CAST_VARCHAR = Pattern.compile(
            "AS\\s+VARCHAR\\s*\\(\\s*(\\d+)\\s*\\)", Pattern.CASE_INSENSITIVE);

    // CAST(x AS INT/INTEGER) → CAST(x AS SIGNED)
    private static final Pattern CAST_INT = Pattern.compile(
            "AS\\s+(INT|INTEGER)(?!\\w)", Pattern.CASE_INSENSITIVE);

    // 达梦数据字典 → information_schema（列存在性/列长度探测）
    private static final Pattern METADATA_TABLE = Pattern.compile(
            "\\bALL_TAB_COLUMNS\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern METADATA_OWNER = Pattern.compile(
            "\\bOWNER\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern METADATA_DATA_LENGTH = Pattern.compile(
            "\\bDATA_LENGTH\\b", Pattern.CASE_INSENSITIVE);

    // ORDER BY col NULLS LAST（简单单列，不处理多列复杂情况——项目里只有一处）
    private static final Pattern NULLS_LAST = Pattern.compile(
            "ORDER\\s+BY\\s+([\\w.]+)\\s+NULLS\\s+LAST", Pattern.CASE_INSENSITIVE);

    // || 字符串连接（LIKE 模式里）：'%' || expr || '%'
    private static final Pattern CONCAT_LIKE_TRIPLE = Pattern.compile(
            "LIKE\\s+'%'\\s*\\|\\|\\s*(.+?)\\s*\\|\\|\\s*'%'", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONCAT_LIKE_PREFIX = Pattern.compile(
            "LIKE\\s+UPPER\\((.+?)\\)\\s*\\|\\|\\s*'%'", Pattern.CASE_INSENSITIVE);

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];

        BoundSql boundSql;
        if (args.length == 6) {
            boundSql = (BoundSql) args[5];
        } else {
            boundSql = ms.getBoundSql(parameter);
        }

        String originalSql = boundSql.getSql();
        String mysqlSql = translate(originalSql);

        if (!mysqlSql.equals(originalSql)) {
            // 通过反射替换 BoundSql 里的 SQL
            Field sqlField = BoundSql.class.getDeclaredField("sql");
            sqlField.setAccessible(true);
            sqlField.set(boundSql, mysqlSql);
            if (log.isDebugEnabled()) {
                log.debug("SQL dialect translated [{}]:\n  DM: {}\n  MySQL: {}",
                        ms.getId(), compact(originalSql), compact(mysqlSql));
            }
        }

        return invocation.proceed();
    }

    /**
     * 把达梦/Oracle 方言 SQL 翻译成 MySQL 等价写法。包级可见便于单元测试。
     */
    static String translate(String sql) {
        if (sql == null || sql.isEmpty()) return sql;
        String s = sql;

        // 1) 子查询包装的 ROWNUM：) WHERE ROWNUM <= n  →  ) t LIMIT n
        Matcher m = ROWNUM_WRAPPER.matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            m.appendReplacement(sb, ") t LIMIT " + m.group(1));
        }
        m.appendTail(sb);
        s = sb.toString();

        // 2) WHERE 里的 AND ROWNUM <= n（PageConfigMapper 那种）
        m = ROWNUM_IN_WHERE.matcher(s);
        sb = new StringBuffer();
        String trailingLimit = null;
        while (m.find()) {
            trailingLimit = m.group(1);
            m.appendReplacement(sb, "");
        }
        m.appendTail(sb);
        s = sb.toString();
        if (trailingLimit != null) {
            s = s.trim() + " LIMIT " + trailingLimit;
        }

        // 3) SYSDATE - n（天）
        s = SYSDATE_DAYS.matcher(s).replaceAll("DATE_SUB(NOW(), INTERVAL $1 DAY)");

        // 4) SYSDATE - (expr / 24.0)（小时）
        s = SYSDATE_HOURS.matcher(s).replaceAll("DATE_SUB(NOW(), INTERVAL $1 HOUR)");

        // 5) 残留裸 SYSDATE → NOW()（如 xxx >= SYSDATE）
        s = SYSDATE_BARE.matcher(s).replaceAll("NOW()");

        // 6) DATEDIFF(HOUR, a, b) → TIMESTAMPDIFF(HOUR, a, b)
        s = DATEDIFF_UNIT.matcher(s).replaceAll("TIMESTAMPDIFF($1,");

        // 7) REGEXP_LIKE(x, 'p') → (x REGEXP 'p')
        s = REGEXP_LIKE.matcher(s).replaceAll("($1 REGEXP $2)");

        // 8) CAST AS VARCHAR(n) → CAST AS CHAR(n)；AS INT/INTEGER → AS SIGNED
        s = CAST_VARCHAR.matcher(s).replaceAll("AS CHAR($1)");
        s = CAST_INT.matcher(s).replaceAll("AS SIGNED");

        // 9) 数据字典：ALL_TAB_COLUMNS → information_schema.COLUMNS
        s = METADATA_TABLE.matcher(s).replaceAll("information_schema.COLUMNS");
        s = METADATA_OWNER.matcher(s).replaceAll("TABLE_SCHEMA");
        s = METADATA_DATA_LENGTH.matcher(s).replaceAll("CHARACTER_MAXIMUM_LENGTH");

        // 10) NULLS LAST
        s = NULLS_LAST.matcher(s).replaceAll("ORDER BY $1 IS NULL, $1");

        // 11) || 字符串连接
        s = CONCAT_LIKE_TRIPLE.matcher(s).replaceAll("LIKE CONCAT('%', $1, '%')");
        s = CONCAT_LIKE_PREFIX.matcher(s).replaceAll("LIKE CONCAT(UPPER($1), '%')");

        return s;
    }

    private static String compact(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }

    @Override
    public Object plugin(Object target) {
        return target instanceof Executor ? Plugin.wrap(target, this) : target;
    }

    @Override
    public void setProperties(Properties properties) {
        // no-op
    }
}
