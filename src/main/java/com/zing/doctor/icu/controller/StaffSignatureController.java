package com.zing.doctor.icu.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.icu.service.IcuPatientService;
import com.zing.doctor.icu.service.StaffSignatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 人员电子签名 Controller（供评分文书「评分医师」显示签名图）。
 *
 * <p>工号来源：ICU 外链参数 {@code username}（ICU 登录人工号），前端在文书预览/导出前调用；
 * 数据来自 ICU 只读库 {@code zing_icu_db_prod.config_staff_ca_info}。
 *
 * <p>查不到签名（无该工号 / 未配签名 / ICU 库不可用）返回 {@code found=false}，
 * 前端忽略即可，不视为错误——签名不影响文书本身。
 *
 * <p>路径在 {@code /api/**} 下，同样受外链鉴权拦截器保护。
 */
@Slf4j
@RestController
@RequestMapping("/api/staff")
@RequiredArgsConstructor
public class StaffSignatureController {

    private final StaffSignatureService staffSignatureService;
    private final IcuPatientService icuPatientService;

    /** 按工号取电子签名 */
    @GetMapping("/signature")
    public Result<Map<String, Object>> signature(@RequestParam(value = "workNo", required = false) String workNo) {
        try {
            return Result.ok(staffSignatureService.getByWorkNo(workNo));
        } catch (Exception e) {
            log.error("[电子签名] 查询异常 workNo={}", workNo, e);
            return Result.fail("电子签名查询失败: " + e.getMessage());
        }
    }

    /**
     * 职工检索（文书「签名 / 经管医师」等人员字段用）。
     *
     * <p>数据源 ICU 只读库 {@code zing_icu_db_prod.config_staff}，支持拼音首字母 / 工号 / 姓名模糊匹配，
     * 最多返回 50 条；keyword 为空时按排序取前 50 条，便于聚焦后直接点选。
     *
     * <p>与 {@code /api/antibiotic/staff/search} 同源，只是挂在职工域下，供非抗菌药模块（如 ARDS 文书）复用，
     * 避免文书页依赖抗菌药接口。
     */
    @GetMapping("/search")
    public Result<List<Map<String, Object>>> search(@RequestParam(value = "keyword", required = false) String keyword) {
        try {
            return Result.ok(icuPatientService.searchStaff(keyword));
        } catch (Exception e) {
            log.error("[职工检索] 查询异常 keyword={}", keyword, e);
            return Result.fail("职工检索失败: " + e.getMessage());
        }
    }
}
