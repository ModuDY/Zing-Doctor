package com.zing.doctor.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zing.doctor.config.CacheConfig;
import com.zing.doctor.module.system.entity.SysParam;
import com.zing.doctor.module.system.mapper.SysParamMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 参数读取出口（带缓存）。
 *
 * <p>单独拆一个 Bean 是因为 Spring 的缓存注解靠代理生效：如果把这些方法写在
 * {@code SysParamServiceImpl} 里，被同类内部调用时不会走代理，缓存直接失效。
 *
 * <p>所有 public 方法各自缓存，内部统一走不缓存的 {@link #raw(String)}，
 * 避免出现「缓存套缓存」的半失效状态。
 */
@Slf4j
@Component
public class ParamStore {

    @Autowired
    private SysParamMapper sysParamMapper;

    /**
     * 取启用中的参数值。
     *
     * @return 未配置、已停用、空串均返回 null
     */
    @Cacheable(value = CacheConfig.CACHE_PARAM, key = "#paramKey")
    public String value(String paramKey) {
        return raw(paramKey);
    }

    /** 带默认值：参数没配就用默认 */
    public String valueOrDefault(String paramKey, String defaultValue) {
        String v = value(paramKey);
        return v == null ? defaultValue : v;
    }

    /**
     * 开关型参数：值等于 1 / true / on（忽略大小写）视为开。
     * 参数没配时返回传入的默认值。
     */
    @Cacheable(value = CacheConfig.CACHE_PARAM_BOOL, key = "#paramKey + ':' + #defaultValue")
    public boolean bool(String paramKey, boolean defaultValue) {
        String v = raw(paramKey);
        if (v == null) {
            return defaultValue;
        }
        return "1".equals(v) || "true".equalsIgnoreCase(v) || "on".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v);
    }

    /** 保存/删除参数后调用：清掉全部参数缓存 */
    @CacheEvict(value = {CacheConfig.CACHE_PARAM, CacheConfig.CACHE_PARAM_BOOL}, allEntries = true)
    public void evictAll() {
        log.debug("系统参数缓存已清空");
    }

    /** 实际查询，不缓存 */
    private String raw(String paramKey) {
        LambdaQueryWrapper<SysParam> q = new LambdaQueryWrapper<>();
        q.eq(SysParam::getParamKey, paramKey);
        q.eq(SysParam::getStatus, 1);
        // 不用分页语法（达梦方言差异），直接取首条
        java.util.List<SysParam> list = sysParamMapper.selectList(q);
        if (list.isEmpty()) {
            return null;
        }
        SysParam p = list.get(0);
        String v = p.getParamValue();
        // 参数值为空时退回默认值：页面清空输入框就能恢复出厂设置，不用改库
        if (!StringUtils.hasText(v) && StringUtils.hasText(p.getDefaultValue())) {
            return p.getDefaultValue().trim();
        }
        return StringUtils.hasText(v) ? v.trim() : null;
    }
}
