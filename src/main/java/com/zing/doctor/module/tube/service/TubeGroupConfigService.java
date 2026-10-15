package com.zing.doctor.module.tube.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zing.doctor.common.OperatorContext;
import com.zing.doctor.module.tube.entity.TubeGroupConfig;
import com.zing.doctor.module.tube.mapper.TubeGroupConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 导管类型分组配置服务。
 *
 * <p> TubeStatsService.groupOf 每次统计都会读本表（行数个位数，无缓存必要），
 * tube_code 命中配置行则按配置归类并带出危重等级；未配置的走三管白名单。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TubeGroupConfigService {

    private final TubeGroupConfigMapper mapper;

    public List<TubeGroupConfig> listAll() {
        QueryWrapper<TubeGroupConfig> qw = new QueryWrapper<>();
        qw.eq("status", 1).orderByAsc("sort_no").orderByDesc("create_time");
        return mapper.selectList(qw);
    }

    /** tube_code → 配置行（仅启用行）。 */
    public Map<String, TubeGroupConfig> activeMap() {
        Map<String, TubeGroupConfig> map = new LinkedHashMap<>();
        for (TubeGroupConfig c : listAll()) {
            map.putIfAbsent(c.getTubeCode(), c);
        }
        return map;
    }

    public TubeGroupConfig save(TubeGroupConfig cfg) {
        String operator = OperatorContext.current();
        if (cfg.getTubeCode() == null || cfg.getTubeCode().trim().isEmpty()) {
            throw new IllegalArgumentException("tubeCode 不能为空");
        }
        if (cfg.getRiskLevel() == null || cfg.getRiskLevel().trim().isEmpty()) {
            cfg.setRiskLevel("中");
        }
        cfg.setTubeCode(cfg.getTubeCode().trim());
        if (cfg.getGroupName() == null || cfg.getGroupName().trim().isEmpty()) {
            cfg.setGroupName("其他");
        }
        LocalDateTime now = LocalDateTime.now();

        // tube_code 唯一：命中即更新
        QueryWrapper<TubeGroupConfig> qw = new QueryWrapper<>();
        qw.eq("tube_code", cfg.getTubeCode()).last("LIMIT 1");
        TubeGroupConfig existing = mapper.selectOne(qw);
        if (existing != null) {
            cfg.setId(existing.getId());
            cfg.setCreateBy(existing.getCreateBy());
            cfg.setCreateTime(existing.getCreateTime());
        } else {
            cfg.setCreateBy(operator);
            cfg.setCreateTime(now);
        }
        // status 只允许 1（启用）/ 0（停用），缺省按启用。
        // 前端新增/编辑不传该字段（停用走 DELETE /tube/groups/{id}），
        // 这里收紧取值是为了避免请求体直接写入脏值或绕过停用接口改状态。
        Integer status = cfg.getStatus();
        if (status == null) {
            cfg.setStatus(1);
        } else if (status != 1 && status != 0) {
            throw new IllegalArgumentException("status 只能是 1（启用）或 0（停用）");
        }
        cfg.setUpdateBy(operator);
        cfg.setUpdateTime(now);
        if (cfg.getId() != null && mapper.selectById(cfg.getId()) != null) {
            mapper.updateById(cfg);
        } else {
            cfg.setId(null);
            mapper.insert(cfg);
        }
        log.info("导管类型配置保存: code={}, group={}, risk={}, operator={}",
                cfg.getTubeCode(), cfg.getGroupName(), cfg.getRiskLevel(), operator);
        return cfg;
    }

    /** 停用（软删）。 */
    public int disable(Long id) {
        TubeGroupConfig cfg = mapper.selectById(id);
        if (cfg == null) return 0;
        cfg.setStatus(0);
        cfg.setUpdateBy(OperatorContext.current());
        cfg.setUpdateTime(LocalDateTime.now());
        return mapper.updateById(cfg);
    }
}
