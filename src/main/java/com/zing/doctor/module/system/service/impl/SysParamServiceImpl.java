package com.zing.doctor.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zing.doctor.module.system.entity.SysParam;
import com.zing.doctor.module.system.mapper.SysParamMapper;
import com.zing.doctor.module.system.service.SysParamService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统参数服务实现。
 */
@Slf4j
@Service
public class SysParamServiceImpl implements SysParamService {

    @Autowired
    private SysParamMapper sysParamMapper;

    @Autowired
    private ParamStore paramStore;

    @Override
    public List<SysParam> list(String paramGroup) {
        LambdaQueryWrapper<SysParam> q = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(paramGroup)) {
            q.eq(SysParam::getParamGroup, paramGroup.trim());
        }
        q.orderByAsc(SysParam::getSortNo).orderByAsc(SysParam::getId);
        return sysParamMapper.selectList(q);
    }

    @Override
    public boolean save(SysParam param) {
        if (param == null || !StringUtils.hasText(param.getParamKey())) {
            return false;
        }
        param.setParamKey(param.getParamKey().trim());
        // param_key 唯一：改 key 时不能撞到别的行
        LambdaQueryWrapper<SysParam> dup = new LambdaQueryWrapper<>();
        dup.eq(SysParam::getParamKey, param.getParamKey());
        if (param.getId() != null) {
            dup.ne(SysParam::getId, param.getId());
        }
        if (sysParamMapper.selectCount(dup) > 0) {
            log.warn("系统参数保存失败：param_key 已存在 key={}", param.getParamKey());
            return false;
        }
        // 必填与正则校验：参数值本身要合法，否则运行时读到一个瞎填的值更难排查
        String value = param.getParamValue();
        if (param.getRequired() != null && param.getRequired() == 1 && !StringUtils.hasText(value)) {
            log.warn("系统参数保存失败：参数必填 key={}", param.getParamKey());
            return false;
        }
        if (StringUtils.hasText(param.getRegex()) && StringUtils.hasText(value)
                && !value.trim().matches(param.getRegex())) {
            log.warn("系统参数保存失败：参数值未通过正则校验 key={} regex={}", param.getParamKey(), param.getRegex());
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        boolean ok;
        if (param.getId() == null) {
            param.setCreateTime(now);
            param.setUpdateTime(now);
            if (param.getStatus() == null) {
                param.setStatus(1);
            }
            ok = sysParamMapper.insert(param) > 0;
        } else {
            param.setUpdateTime(now);
            ok = sysParamMapper.updateById(param) > 0;
        }
        if (ok) {
            // 参数缓存清干净：不清的话页面上改完要等缓存过期才生效
            paramStore.evictAll();
        }
        return ok;
    }

    @Override
    public boolean delete(Long id) {
        if (id == null) {
            return false;
        }
        boolean ok = sysParamMapper.deleteById(id) > 0;
        if (ok) {
            paramStore.evictAll();
        }
        return ok;
    }

    @Override
    public String getArchiveApiUrl() {
        return paramStore.value(KEY_ARCHIVE_API_URL);
    }

    @Override
    public String getArchiveDir() {
        return paramStore.value(KEY_ARCHIVE_DIR);
    }

    @Override
    public String value(String paramKey) {
        return paramStore.value(paramKey);
    }

    @Override
    public boolean bool(String paramKey, boolean defaultValue) {
        return paramStore.bool(paramKey, defaultValue);
    }

    @Override
    public boolean isAutoRegisterEnabled() {
        // 默认关：extToken 是固定明文，开启后任何持有外链者都能注册账号
        return paramStore.bool(KEY_AUTO_REGISTER_ENABLED, false);
    }

    @Override
    public String getRegisterPasswordRule() {
        String rule = paramStore.value(KEY_AUTO_REGISTER_PASSWORD_RULE);
        if (!PWD_RULE_FIXED.equals(rule)) {
            return PWD_RULE_WORK_NO;
        }
        return PWD_RULE_FIXED;
    }

    @Override
    public String getRegisterFixedPassword() {
        return paramStore.value(KEY_AUTO_REGISTER_FIXED_PASSWORD);
    }
}
