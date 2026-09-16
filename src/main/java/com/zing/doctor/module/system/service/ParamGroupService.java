package com.zing.doctor.module.system.service;

import com.zing.doctor.module.system.entity.ParamGroup;

import java.util.List;

/**
 * 参数分组服务（参数设置页左侧导航，页面可维护）。
 */
public interface ParamGroupService {

    /**
     * 分组列表。
     *
     * @param onlyEnabled true 只返回启用中的
     */
    List<ParamGroup> list(boolean onlyEnabled);

    /**
     * 保存（id 为空新增，否则更新）。
     */
    boolean save(ParamGroup group);

    /**
     * 删除。分组下仍挂着参数时拒绝删除，避免参数变成「孤儿」在页面上找不到。
     */
    boolean delete(Long id);
}
