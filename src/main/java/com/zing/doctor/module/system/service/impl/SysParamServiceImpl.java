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

        LocalDateTime now = LocalDateTime.now();
        if (param.getId() == null) {
            param.setCreateTime(now);
            param.setUpdateTime(now);
            if (param.getStatus() == null) {
                param.setStatus(1);
            }
            return sysParamMapper.insert(param) > 0;
        }
        param.setUpdateTime(now);
        return sysParamMapper.updateById(param) > 0;
    }

    @Override
    public boolean delete(Long id) {
        return id != null && sysParamMapper.deleteById(id) > 0;
    }

    @Override
    public String getArchiveApiUrl() {
        return getValue(KEY_ARCHIVE_API_URL);
    }

    @Override
    public String getArchiveDir() {
        return getValue(KEY_ARCHIVE_DIR);
    }

    /** 取启用中的参数值；未配置、已停用、空串均返回 null */
    private String getValue(String paramKey) {
        LambdaQueryWrapper<SysParam> q = new LambdaQueryWrapper<>();
        q.eq(SysParam::getParamKey, paramKey);
        q.eq(SysParam::getStatus, 1);
        // 不用分页语法（达梦方言差异），直接取首条
        List<SysParam> list = sysParamMapper.selectList(q);
        if (list.isEmpty()) {
            return null;
        }
        String v = list.get(0).getParamValue();
        return StringUtils.hasText(v) ? v.trim() : null;
    }
}
