package com.zing.doctor.module.system.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.module.system.service.ArchiveService;
import com.zing.doctor.module.system.service.impl.ArchiveServiceImpl.ArchiveException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 评分文书归档。
 *
 * <p>biz：{@code SOFA} / {@code APACHE2}；id：评分记录 ID。
 */
@Slf4j
@RestController
@RequestMapping("/api/archive")
public class ArchiveController {

    @Autowired
    private ArchiveService archiveService;

    /**
     * 归档推送：调用院方接口，成功后该条记录标记为「已归档」。
     */
    @PostMapping("/push")
    public Result<Map<String, Object>> push(@RequestParam String biz, @RequestParam Long id) {
        try {
            return Result.ok(archiveService.push(biz, id));
        } catch (ArchiveException e) {
            log.warn("文书归档失败: biz={}, id={}, msg={}", biz, id, e.getMessage());
            return Result.fail(e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("文书归档异常: biz={}, id={}", biz, id, e);
            return Result.fail("归档失败: " + e.getMessage());
        }
    }

    /**
     * 撤销归档标记（只改本地状态，不调用院方接口）。
     */
    @PostMapping("/unmark")
    public Result<Boolean> unmark(@RequestParam String biz, @RequestParam Long id) {
        try {
            archiveService.unmark(biz, id);
            return Result.ok(true);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        } catch (Exception e) {
            log.error("撤销归档标记失败: biz={}, id={}", biz, id, e);
            return Result.fail("操作失败: " + e.getMessage());
        }
    }
}
