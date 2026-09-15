package com.zing.doctor.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zing.doctor.module.system.entity.ParamGroup;
import com.zing.doctor.module.system.entity.SysParam;
import com.zing.doctor.module.system.mapper.ParamGroupMapper;
import com.zing.doctor.module.system.mapper.SysParamMapper;
import com.zing.doctor.module.system.service.ParamGroupService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 参数分组服务实现。
 */
@Slf4j
@Service
public class ParamGroupServiceImpl implements ParamGroupService {

    @Autowired
    private ParamGroupMapper paramGroupMapper;

    @Autowired
    private SysParamMapper sysParamMapper;

    @Override
    public List<ParamGroup> list(boolean onlyEnabled) {
        LambdaQueryWrapper<ParamGroup> q = new LambdaQueryWrapper<>();
        if (onlyEnabled) {
            q.eq(ParamGroup::getStatus, 1);
        }
        q.orderByAsc(ParamGroup::getSortNo).orderByAsc(ParamGroup::getId);
        return paramGroupMapper.selectList(q);
    }

    @Override
    public boolean save(ParamGroup group) {
        if (group == null || !StringUtils.hasText(group.getGroupCode()) || !StringUtils.hasText(group.getGroupName())) {
            return false;
        }
        group.setGroupCode(group.getGroupCode().trim());
        group.setGroupName(group.getGroupName().trim());

        // group_code 唯一：改编码时不能撞到别的分组
        LambdaQueryWrapper<ParamGroup> dup = new LambdaQueryWrapper<>();
        dup.eq(ParamGroup::getGroupCode, group.getGroupCode());
        if (group.getId() != null) {
            dup.ne(ParamGroup::getId, group.getId());
        }
        if (paramGroupMapper.selectCount(dup) > 0) {
            log.warn("参数分组保存失败：group_code 已存在 code={}", group.getGroupCode());
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        if (group.getId() == null) {
            group.setCreateTime(now);
            group.setUpdateTime(now);
            if (group.getStatus() == null) {
                group.setStatus(1);
            }
            if (group.getSortNo() == null) {
                group.setSortNo(99);
            }
            return paramGroupMapper.insert(group) > 0;
        }
        group.setUpdateTime(now);
        return paramGroupMapper.updateById(group) > 0;
    }

    @Override
    public boolean delete(Long id) {
        if (id == null) {
            return false;
        }
        ParamGroup group = paramGroupMapper.selectById(id);
        if (group == null) {
            return false;
        }
        // 分组下还有参数就拒绝删除：否则这些参数在左侧导航里消失，只能改库才找得回来
        LambdaQueryWrapper<SysParam> used = new LambdaQueryWrapper<>();
        used.eq(SysParam::getParamGroup, group.getGroupCode());
        long count = sysParamMapper.selectCount(used);
        if (count > 0) {
            log.warn("参数分组删除失败：分组下仍有 {} 个参数 code={}", count, group.getGroupCode());
            return false;
        }
        return paramGroupMapper.deleteById(id) > 0;
    }
}
