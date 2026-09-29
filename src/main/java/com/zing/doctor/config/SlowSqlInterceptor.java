package com.zing.doctor.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.apache.ibatis.session.RowBounds;

import java.util.List;
import java.util.Properties;

/**
 * 慢 SQL 埋点。
 *
 * <p>性能优化最忌讳凭感觉加索引。这里先把超过阈值的 SQL 连参数一起打到日志，
 * 跑一段时间拿到真实清单后再决定建哪些索引、改哪些查询。
 *
 * <p>日志用 WARN 级别，便于直接 grep：
 * <pre>
 *   grep "慢SQL" zing-doctor.log | sort | uniq -c | sort -rn
 * </pre>
 *
 * <p>介入点是 MyBatis 的 Executor，因此对主库（doctor）与 ICU 只读库都生效，
 * 跨库查询慢也能抓到。
 */
@Slf4j
@Component
@Intercepts({
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class})
})
public class SlowSqlInterceptor implements Interceptor {

    /** 阈值，可用 zing.slow-sql.threshold-ms 覆盖 */
    @Value("${zing.slow-sql.threshold-ms:1000}")
    private long thresholdMs;

    /** 单条 SQL 打印长度上限，避免日志被巨型 SQL 刷爆 */
    private static final int MAX_SQL_LEN = 2000;

    /** 参数打印长度上限：评分记录带 PDF Base64，不截断能把日志写到几个 G */
    private static final int MAX_PARAM_LEN = 800;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (thresholdMs <= 0) {
            return invocation.proceed();
        }
        long t0 = System.currentTimeMillis();
        try {
            return invocation.proceed();
        } finally {
            long cost = System.currentTimeMillis() - t0;
            if (cost >= thresholdMs) {
                logSlow(invocation, cost);
            }
        }
    }

    private void logSlow(Invocation invocation, long cost) {
        try {
            Object[] args = invocation.getArgs();
            MappedStatement ms = (MappedStatement) args[0];
            Object parameter = args.length > 1 ? args[1] : null;

            String mapperId = ms.getId();
            String sql = safeSql(ms, parameter);
            String params = safeParams(ms, parameter);

            log.warn("慢SQL 耗时={}ms 阈值={}ms\n  mapper: {}\n  SQL   : {}\n  参数  : {}",
                    cost, thresholdMs, mapperId, sql, params);
        } catch (Exception e) {
            // 埋点自身出任何问题都不能影响业务
            log.debug("慢SQL 日志输出失败: {}", e.getMessage());
        }
    }

    /** 取 SQL 文本（已带占位符，参数单独打印） */
    private String safeSql(MappedStatement ms, Object parameter) {
        BoundSql boundSql = ms.getBoundSql(parameter);
        String sql = boundSql.getSql();
        if (sql == null) {
            return "";
        }
        String oneLine = sql.replaceAll("\\s+", " ").trim();
        return oneLine.length() > MAX_SQL_LEN ? oneLine.substring(0, MAX_SQL_LEN) + "…(已截断)" : oneLine;
    }

    /** 取参数值；口令类字段打码 */
    private String safeParams(MappedStatement ms, Object parameter) {
        if (parameter == null) {
            return "无";
        }
        BoundSql boundSql = ms.getBoundSql(parameter);
        Configuration configuration = ms.getConfiguration();
        TypeHandlerRegistry registry = configuration.getTypeHandlerRegistry();
        List<ParameterMapping> mappings = boundSql.getParameterMappings();

        // 没有参数映射（例如直接传了实体或 Map）时退化为 toString，够定位问题
        if (mappings == null || mappings.isEmpty() || registry.hasTypeHandler(parameter.getClass())) {
            return mask(String.valueOf(parameter));
        }

        MetaObject meta = configuration.newMetaObject(parameter);
        StringBuilder sb = new StringBuilder();
        for (ParameterMapping mapping : mappings) {
            String property = mapping.getProperty();
            Object value;
            try {
                if (boundSql.hasAdditionalParameter(property)) {
                    value = boundSql.getAdditionalParameter(property);
                } else if (meta.hasGetter(property)) {
                    value = meta.getValue(property);
                } else {
                    continue;
                }
            } catch (Exception e) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(property).append('=');
            if (isSecret(property)) {
                sb.append("***");
            } else {
                sb.append(mask(String.valueOf(value)));
            }
        }
        String s = sb.toString();
        return s.length() > MAX_PARAM_LEN ? s.substring(0, MAX_PARAM_LEN) + "…(已截断)" : s;
    }

    private static boolean isSecret(String property) {
        String p = property.toLowerCase();
        return p.contains("password") || p.contains("passwd") || p.contains("pwd") || p.contains("secret");
    }

    private static String mask(String s) {
        if (s == null) {
            return "null";
        }
        String v = s.replaceAll("\\s+", " ");
        return v.length() > 200 ? v.substring(0, 200) + "…" : v;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 无外部属性
    }
}
