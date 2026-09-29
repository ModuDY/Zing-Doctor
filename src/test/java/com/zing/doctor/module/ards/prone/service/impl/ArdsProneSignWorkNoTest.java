package com.zing.doctor.module.ards.prone.service.impl;

import com.zing.doctor.module.ards.prone.entity.ArdsProneRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * ARDS 俯卧位记录「签名的姓名 / 工号成对」规格测试。
 *
 * <p>工号是文书电子签名图的主键（按它从 ICU 只读库 config_staff_ca_info 取图）。
 * 姓名改了或清了、工号还留着旧值，文书盖的就是别人的签名 —— 在医疗文书上
 * 这属于「张冠李戴」，比没有签名更糟，因此这条规则必须有测试守住。
 *
 * <p>不加载 Spring、不连数据库：normalizeSigns 为纯函数。
 */
class ArdsProneSignWorkNoTest {

    private ArdsProneRecord record(String doctorName, String doctorNo,
                                   String nurseName, String nurseNo,
                                   String seniorName, String seniorNo) {
        ArdsProneRecord r = new ArdsProneRecord();
        r.setDoctorSign(doctorName);
        r.setDoctorWorkNo(doctorNo);
        r.setNurseSign(nurseName);
        r.setNurseWorkNo(nurseNo);
        r.setSeniorSign(seniorName);
        r.setSeniorWorkNo(seniorNo);
        return r;
    }

    @Test
    @DisplayName("姓名 + 工号都在 → 原样保留（文书显示电子签名图）")
    void keepsPair() {
        ArdsProneRecord r = record("张三", "1001", "李四", "1002", "王五", "1003");
        ArdsProneServiceImpl.normalizeSigns(r);

        assertEquals("张三", r.getDoctorSign());
        assertEquals("1001", r.getDoctorWorkNo());
        assertEquals("李四", r.getNurseSign());
        assertEquals("1002", r.getNurseWorkNo());
        assertEquals("王五", r.getSeniorSign());
        assertEquals("1003", r.getSeniorWorkNo());
    }

    @Test
    @DisplayName("签名人姓名被清空 → 对应工号一并作废，绝不留下旧工号盖别人的章")
    void clearsWorkNoWhenNameEmpty() {
        ArdsProneRecord r = record("   ", "1001", null, "1002", "", "1003");
        ArdsProneServiceImpl.normalizeSigns(r);

        assertNull(r.getDoctorSign());
        assertNull(r.getDoctorWorkNo(), "姓名为空却留着工号，文书会盖上陌生人的签名");
        assertNull(r.getNurseWorkNo());
        assertNull(r.getSeniorWorkNo());
    }

    @Test
    @DisplayName("只填姓名没填工号（进修 / 外院会诊不在职工库）→ 允许，文书退化为打印姓名")
    void allowsNameWithoutWorkNo() {
        ArdsProneRecord r = record("张三", null, null, null, null, null);
        ArdsProneServiceImpl.normalizeSigns(r);

        assertEquals("张三", r.getDoctorSign());
        assertNull(r.getDoctorWorkNo());
    }

    @Test
    @DisplayName("姓名/工号去首尾空格，纯空白按空处理")
    void trimsValues() {
        ArdsProneRecord r = record(" 张三 ", " 1001 ", "  ", " 1002 ", null, null);
        ArdsProneServiceImpl.normalizeSigns(r);

        assertEquals("张三", r.getDoctorSign());
        assertEquals("1001", r.getDoctorWorkNo());
        assertNull(r.getNurseSign());
    }

    @Test
    @DisplayName("工号超长（库列 VARCHAR(32)）→ 截断而不是让整条记录保存失败")
    void truncatesOverlongWorkNo() {
        String tooLong = "1234567890123456789012345678901234567890";
        ArdsProneRecord r = record("张三", tooLong, null, null, null, null);
        ArdsProneServiceImpl.normalizeSigns(r);

        assertEquals(32, r.getDoctorWorkNo().length());
        assertEquals(tooLong.substring(0, 32), r.getDoctorWorkNo());
    }

    @Test
    @DisplayName("整条记录为 null → 不抛异常（防御调用方传空）")
    void nullSafe() {
        ArdsProneServiceImpl.normalizeSigns(null);
    }
}
