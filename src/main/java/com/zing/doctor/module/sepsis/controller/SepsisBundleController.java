package com.zing.doctor.module.sepsis.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.module.sepsis.dto.SepsisBundleView;
import com.zing.doctor.module.sepsis.entity.SepsisBundleRecord;
import com.zing.doctor.module.sepsis.service.SepsisBundleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 脓毒症休克集束化治疗 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/sepsis/bundle")
public class SepsisBundleController {

    @Autowired
    private SepsisBundleService sepsisBundleService;

    /**
     * 获取患者集束化治疗详情（自动判断1H/3H/6H项目完成情况）
     */
    @GetMapping("/detail")
    public Result<SepsisBundleView> getBundleDetail(@RequestParam String inHospitalNo) {
        try {
            SepsisBundleView view = sepsisBundleService.getBundleDetail(inHospitalNo);
            return Result.ok(view);
        } catch (Exception e) {
            log.error("获取脓毒症集束化治疗详情失败: inHospitalNo={}", inHospitalNo, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID获取评估记录详情
     */
    @GetMapping("/detail/{id}")
    public Result<SepsisBundleView> getBundleDetailById(@PathVariable Long id) {
        try {
            SepsisBundleView view = sepsisBundleService.getBundleDetailById(id);
            if (view == null) {
                return Result.fail("记录不存在");
            }
            return Result.ok(view);
        } catch (Exception e) {
            log.error("获取脓毒症集束化治疗详情失败: id={}", id, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取患者历史评估记录列表
     */
    @GetMapping("/history")
    public Result<List<SepsisBundleRecord>> getHistoryList(@RequestParam String inHospitalNo) {
        try {
            List<SepsisBundleRecord> list = sepsisBundleService.getHistoryList(inHospitalNo);
            return Result.ok(list);
        } catch (Exception e) {
            log.error("获取脓毒症集束化治疗历史记录失败: inHospitalNo={}", inHospitalNo, e);
            return Result.fail("获取失败: " + e.getMessage());
        }
    }

    /**
     * 保存集束化治疗记录
     */
    @PostMapping("/save")
    public Result<SepsisBundleRecord> saveBundle(@RequestBody SepsisBundleRecord record) {
        try {
            SepsisBundleRecord saved = sepsisBundleService.saveBundle(record);
            return Result.ok(saved);
        } catch (Exception e) {
            log.error("保存脓毒症集束化治疗记录失败", e);
            return Result.fail("保存失败: " + e.getMessage());
        }
    }

    /**
     * 更新集束化治疗记录
     */
    @PostMapping("/update")
    public Result<SepsisBundleRecord> updateBundle(@RequestBody SepsisBundleRecord record) {
        try {
            SepsisBundleRecord updated = sepsisBundleService.updateBundle(record);
            return Result.ok(updated);
        } catch (Exception e) {
            log.error("更新脓毒症集束化治疗记录失败", e);
            return Result.fail("更新失败: " + e.getMessage());
        }
    }

    /**
     * 根据住院号查询记录
     */
    @GetMapping("/record")
    public Result<SepsisBundleRecord> getRecord(@RequestParam String inHospitalNo) {
        try {
            SepsisBundleRecord record = sepsisBundleService.getByInHospitalNo(inHospitalNo);
            return Result.ok(record);
        } catch (Exception e) {
            log.error("查询脓毒症集束化治疗记录失败: inHospitalNo={}", inHospitalNo, e);
            return Result.fail("查询失败: " + e.getMessage());
        }
    }

    /**
     * 删除评估记录（逻辑删除，支持多次评估逐条删除）
     */
    @PostMapping("/delete")
    public Result<Boolean> deleteBundle(@RequestParam Long id) {
        try {
            boolean ok = sepsisBundleService.deleteById(id);
            if (!ok) {
                return Result.fail("记录不存在");
            }
            return Result.ok(true);
        } catch (Exception e) {
            log.error("删除脓毒症集束化治疗记录失败: id={}", id, e);
            return Result.fail("删除失败: " + e.getMessage());
        }
    }
}
