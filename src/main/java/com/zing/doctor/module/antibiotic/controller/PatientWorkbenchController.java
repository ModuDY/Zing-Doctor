package com.zing.doctor.module.antibiotic.controller;

import com.zing.doctor.common.Result;
import com.zing.doctor.icu.dto.WorkbenchPatient;
import com.zing.doctor.icu.service.IcuPatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** ICU 全量在科患者工作台接口。 */
@RestController
@RequestMapping("/api/workbench")
@RequiredArgsConstructor
public class PatientWorkbenchController {
    private final IcuPatientService icuPatientService;

    @GetMapping("/patients")
    public Result<List<WorkbenchPatient>> patients() {
        return Result.ok(icuPatientService.listInpatients());
    }
}
