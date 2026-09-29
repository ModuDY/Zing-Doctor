package com.zing.doctor.module.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zing.doctor.config.CacheConfig;
import com.zing.doctor.module.system.entity.SysUser;
import com.zing.doctor.module.system.mapper.SysUserMapper;
import com.zing.doctor.module.system.service.SysParamService;
import com.zing.doctor.module.system.util.PasswordHasher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 外链工号自动注册。
 *
 * <p>第三方系统（ICU）外链带来的工号在本系统没有对应账号时，按参数配置自动建一个：
 * 账号=工号，姓名=外链带来的姓名，初始口令按 {@code AUTO_REGISTER_PASSWORD_RULE}
 * 取「同工号」或「固定口令」。
 *
 * <p><b>开关默认关闭</b>（{@code AUTO_REGISTER_ENABLED}）：extToken 是固定明文，
 * 谁拿到外链谁就能注册账号，必须在确认 ICU 侧传参正确后再打开。
 *
 * <p>这里只负责「建账号」，不做登录态——外链通道本身免登录，不查这张表。
 */
@Slf4j
@Service
public class ExternalUserAutoRegister {

    /**
     * 单日注册上限：外链凭证一旦泄露，没有这个限制会被批量灌账号。
     * 超出后只记日志告警，不影响本次访问。
     */
    private static final int DAILY_LIMIT = 200;

    /** 工号格式：数字字母及 - _ ，最长 32 位 */
    private static final Pattern WORK_NO_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysParamService sysParamService;

    @Autowired
    private CacheManager cacheManager;

    private final AtomicInteger dailyCount = new AtomicInteger();
    private volatile String today = LocalDate.now().toString();

    /**
     * 外链访问时调用：必要时为工号建账号。
     *
     * <p>任何异常都被吞掉并只记日志——<b>注册失败绝不能阻断医生使用系统</b>，
     * 它只是让「直连登录」这条通道也能用，外链本身不需要账号。
     *
     * @param workNo   外链带来的工号，为空则跳过
     * @param realName 外链带来的姓名，可为空
     */
    public void onExternalVisit(String workNo, String realName) {
        if (!StringUtils.hasText(workNo)) {
            return;
        }
        String no = workNo.trim();
        try {
            // 开关默认关：这一句走参数缓存，不会每次都查库
            if (!sysParamService.isAutoRegisterEnabled()) {
                return;
            }
            if (!WORK_NO_PATTERN.matcher(no).matches()) {
                log.warn("自动注册跳过：工号格式不合法 workNo={}", no);
                return;
            }
            // 已确认存在过的工号直接返回，避免每个接口都查一次用户表
            if (isKnown(no)) {
                return;
            }
            registerIfAbsent(no, realName);
        } catch (Exception e) {
            log.warn("外链工号自动注册失败（不影响本次访问）workNo={}, 原因={}", no, e.getMessage(), e);
        }
    }

    private void registerIfAbsent(String workNo, String realName) {
        SysUser exist = findByUsername(workNo);
        if (exist != null) {
            markKnown(workNo);
            // 账号已存在但姓名为空：顺手补上，省得管理员手工维护
            if (!StringUtils.hasText(exist.getRealName()) && StringUtils.hasText(realName)) {
                SysUser upd = new SysUser();
                upd.setId(exist.getId());
                upd.setRealName(realName.trim());
                upd.setUpdateTime(LocalDateTime.now());
                sysUserMapper.updateById(upd);
                log.info("自动补全账号姓名：workNo={}, realName={}", workNo, realName);
            }
            return;
        }

        if (!checkDailyLimit(workNo)) {
            return;
        }

        SysUser user = new SysUser();
        user.setUsername(workNo);
        user.setRealName(StringUtils.hasText(realName) ? realName.trim() : workNo);
        user.setPasswordHash(PasswordHasher.hash(initialPassword(workNo)));
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        try {
            sysUserMapper.insert(user);
            markKnown(workNo);
            dailyCount.incrementAndGet();
            log.info("外链工号自动注册成功：workNo={}, realName={}", workNo, user.getRealName());
        } catch (Exception e) {
            // 并发下两人同时注册同一工号会撞唯一索引，属正常竞争，按已存在处理
            log.info("自动注册并发冲突，按已存在处理 workNo={}, 原因={}", workNo, e.getMessage());
            markKnown(workNo);
        }
    }

    /** 初始口令：固定口令为空时一律退回「口令同工号」 */
    private String initialPassword(String workNo) {
        if (SysParamService.PWD_RULE_FIXED.equals(sysParamService.getRegisterPasswordRule())) {
            String fixed = sysParamService.getRegisterFixedPassword();
            if (StringUtils.hasText(fixed)) {
                return fixed.trim();
            }
            log.warn("已配置固定初始口令规则但未填写口令，回退为「口令同工号」workNo={}", workNo);
        }
        return workNo;
    }

    private SysUser findByUsername(String username) {
        LambdaQueryWrapper<SysUser> q = new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username);
        java.util.List<SysUser> list = sysUserMapper.selectList(q);
        return list.isEmpty() ? null : list.get(0);
    }

    private boolean checkDailyLimit(String workNo) {
        String now = LocalDate.now().toString();
        if (!now.equals(today)) {
            synchronized (this) {
                if (!now.equals(today)) {
                    today = now;
                    dailyCount.set(0);
                }
            }
        }
        if (dailyCount.get() >= DAILY_LIMIT) {
            log.warn("自动注册已达单日上限 {}，本次跳过 workNo={}（若非预期请检查外链凭证是否泄露）",
                    DAILY_LIMIT, workNo);
            return false;
        }
        return true;
    }

    private boolean isKnown(String workNo) {
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_AUTO_REG);
        return cache != null && cache.get(workNo) != null;
    }

    private void markKnown(String workNo) {
        Cache cache = cacheManager.getCache(CacheConfig.CACHE_AUTO_REG);
        if (cache != null) {
            cache.put(workNo, Boolean.TRUE);
        }
    }
}
