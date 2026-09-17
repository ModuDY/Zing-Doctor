package com.zing.doctor.module.ards.prone.service.impl;

import com.zing.doctor.common.BizException;
import com.zing.doctor.module.ards.prone.entity.ArdsProneCell;
import com.zing.doctor.module.ards.prone.entity.ArdsProneRecord;
import com.zing.doctor.module.ards.prone.entity.ArdsProneTimepoint;
import com.zing.doctor.module.ards.prone.entity.ArdsProneTpTpl;
import com.zing.doctor.module.ards.prone.mapper.ArdsProneCellLogMapper;
import com.zing.doctor.module.ards.prone.mapper.ArdsProneCellMapper;
import com.zing.doctor.module.ards.prone.mapper.ArdsProneDataMapper;
import com.zing.doctor.module.ards.prone.mapper.ArdsProneRecordMapper;
import com.zing.doctor.module.ards.prone.mapper.ArdsProneTimepointMapper;
import com.zing.doctor.module.ards.prone.mapper.ArdsProneTpTplMapper;
import com.zing.doctor.module.system.service.SysParamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ARDS 俯卧位 resetTimepoints 集成测试（Mock Mapper，不连数据库、不启动 Spring）。
 *
 * <p><b>为什么必须给这个方法写测试</b>：resetTimepoints 是「重置时点」操作，
 * 会把旧时点全部软删除并重新创建。如果不同步清理旧时点已填的单元格数据，
 * get() 接口会返回已删除时点的残留数据，前端显示的数据与实际时点不匹配——
 * 这是一个已修复的 Bug（P0 #1），必须用测试钉死，防止回归。
 *
 * <p>覆盖：
 * <ul>
 *   <li>正常重置：旧时点 + 对应单元格都被软删除，新时点被创建</li>
 *   <li>记录不存在：抛出 BizException</li>
 *   <li>无旧时点：只创建新时点，不触发任何删除</li>
 *   <li>已删除的单元格（status=0）不被重复软删除</li>
 * </ul>
 */
class ArdsProneResetTimepointsTest {

    private ArdsProneRecordMapper recordMapper;
    private ArdsProneTimepointMapper timepointMapper;
    private ArdsProneCellMapper cellMapper;
    private ArdsProneTpTplMapper tplMapper;
    private ArdsProneServiceImpl svc;

    @BeforeEach
    void setUp() throws Exception {
        recordMapper = mock(ArdsProneRecordMapper.class);
        timepointMapper = mock(ArdsProneTimepointMapper.class);
        cellMapper = mock(ArdsProneCellMapper.class);
        tplMapper = mock(ArdsProneTpTplMapper.class);
        // 以下 Mapper 在 resetTimepaths 路径中不被调用，仅为注入占位
        ArdsProneCellLogMapper cellLogMapper = mock(ArdsProneCellLogMapper.class);
        ArdsProneDataMapper dataMapper = mock(ArdsProneDataMapper.class);
        SysParamService sysParamService = mock(SysParamService.class);

        svc = new ArdsProneServiceImpl();
        inject(svc, "recordMapper", recordMapper);
        inject(svc, "timepointMapper", timepointMapper);
        inject(svc, "cellMapper", cellMapper);
        inject(svc, "cellLogMapper", cellLogMapper);
        inject(svc, "tplMapper", tplMapper);
        inject(svc, "dataMapper", dataMapper);
        inject(svc, "sysParamService", sysParamService);

        // 默认：科室模板为空 → 走内置默认模板（11 个时点）
        // 注意：必须用可变 ArrayList，因为 tpl() 方法会对返回列表调用 add()
        when(tplMapper.selectByDepart(anyString())).thenReturn(new ArrayList<>());
    }

    /** 通过反射向目标对象的私有字段注入值 */
    private void inject(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private ArdsProneRecord buildRecord(Long id, String departCode) {
        ArdsProneRecord r = new ArdsProneRecord();
        r.setId(id);
        r.setDepartCode(departCode);
        r.setStartTime(LocalDateTime.of(2026, 9, 17, 10, 0));
        r.setStatus(1);
        return r;
    }

    private ArdsProneTimepoint buildTp(Long id, Integer tpIndex, String label) {
        ArdsProneTimepoint t = new ArdsProneTimepoint();
        t.setId(id);
        t.setRecordId(100L);
        t.setTpIndex(tpIndex);
        t.setTpLabel(label);
        t.setStatus(1);
        return t;
    }

    private ArdsProneCell buildCell(Long id, Integer tpIndex, String paramKey, String value, Integer status) {
        ArdsProneCell c = new ArdsProneCell();
        c.setId(id);
        c.setRecordId(100L);
        c.setTpIndex(tpIndex);
        c.setParamKey(paramKey);
        c.setValueText(value);
        c.setStatus(status);
        return c;
    }

    // -------------------------------------------------------------- 测试用例

    @Test
    @DisplayName("正常重置：旧时点(2个) + 对应单元格(3个)都被软删除，新时点(11个)被创建")
    void normalReset_softDeletesOldTimepointsAndCells() {
        ArdsProneRecord record = buildRecord(100L, "ICU01");
        when(recordMapper.selectById(100L)).thenReturn(record);

        // 2 个旧时点
        List<ArdsProneTimepoint> oldTps = new ArrayList<>();
        oldTps.add(buildTp(1L, 0, "T0 翻身前"));
        oldTps.add(buildTp(2L, 1, "+15 min"));
        // timepointMapper.selectByRecord 第一次返回旧时点，第二次（reset 末尾）返回新时点
        when(timepointMapper.selectByRecord(100L))
                .thenReturn(oldTps)
                .thenReturn(Collections.emptyList());

        // 3 个单元格：tpIndex=0 的 2 个，tpIndex=1 的 1 个
        List<ArdsProneCell> cells = new ArrayList<>();
        cells.add(buildCell(11L, 0, "peep", "5", 1));
        cells.add(buildCell(12L, 0, "fio2", "60", 1));
        cells.add(buildCell(13L, 1, "peep", "8", 1));
        when(cellMapper.selectByRecord(100L)).thenReturn(cells);

        List<ArdsProneTimepoint> result = svc.resetTimepoints(100L);

        assertNotNull(result);

        // 验证：2 个旧时点被软删除（updateById 调用，status 被置 0）
        verify(timepointMapper, times(2)).updateById(any(ArdsProneTimepoint.class));
        // 验证：3 个单元格都被软删除
        verify(cellMapper, times(3)).updateById(any(ArdsProneCell.class));
        // 验证：11 个新时点被创建（默认模板 11 个）
        verify(timepointMapper, times(11)).insert(any(ArdsProneTimepoint.class));

        // 验证被软删除的单元格 status 确实被置 0
        for (ArdsProneCell c : cells) {
            assertEquals(0, c.getStatus(), "单元格 id=" + c.getId() + " 应被软删除");
        }
        // 验证被软删除的时点 status 确实被置 0
        for (ArdsProneTimepoint t : oldTps) {
            assertEquals(0, t.getStatus(), "时点 id=" + t.getId() + " 应被软删除");
        }
    }

    @Test
    @DisplayName("记录不存在：抛出 BizException，不执行任何删除或创建")
    void recordNotFound_throwsException() {
        when(recordMapper.selectById(999L)).thenReturn(null);

        BizException ex = assertThrows(BizException.class, () -> svc.resetTimepoints(999L));
        assertEquals(400, ex.getCode());

        verify(timepointMapper, never()).updateById(any());
        verify(cellMapper, never()).updateById(any());
        verify(timepointMapper, never()).insert(any());
    }

    @Test
    @DisplayName("无旧时点：只创建新时点(11个)，不触发任何删除")
    void noOldTimepoints_onlyCreatesNew() {
        ArdsProneRecord record = buildRecord(100L, "ICU01");
        when(recordMapper.selectById(100L)).thenReturn(record);

        // 无旧时点
        when(timepointMapper.selectByRecord(100L))
                .thenReturn(Collections.emptyList())
                .thenReturn(Collections.emptyList());

        // 即使有单元格数据，因为没有旧时点，不应该删除任何单元格
        List<ArdsProneCell> cells = new ArrayList<>();
        cells.add(buildCell(11L, 0, "peep", "5", 1));
        when(cellMapper.selectByRecord(100L)).thenReturn(cells);

        svc.resetTimepoints(100L);

        verify(timepointMapper, never()).updateById(any());
        verify(cellMapper, never()).updateById(any());
        verify(timepointMapper, times(11)).insert(any(ArdsProneTimepoint.class));
    }

    @Test
    @DisplayName("已删除的单元格(status=0)不被重复软删除：只删除 status=1 的")
    void alreadyDeletedCells_notRepeatedlyDeleted() {
        ArdsProneRecord record = buildRecord(100L, "ICU01");
        when(recordMapper.selectById(100L)).thenReturn(record);

        List<ArdsProneTimepoint> oldTps = new ArrayList<>();
        oldTps.add(buildTp(1L, 0, "T0 翻身前"));
        when(timepointMapper.selectByRecord(100L))
                .thenReturn(oldTps)
                .thenReturn(Collections.emptyList());

        // 2 个单元格：1 个 status=1（应删除），1 个 status=0（不应重复删除）
        List<ArdsProneCell> cells = new ArrayList<>();
        cells.add(buildCell(11L, 0, "peep", "5", 1));   // 应删除
        cells.add(buildCell(12L, 0, "fio2", "60", 0));  // 已删除，不应重复操作
        when(cellMapper.selectByRecord(100L)).thenReturn(cells);

        svc.resetTimepoints(100L);

        // 只有 1 个单元格被 updateById（status=1 的那个）
        verify(cellMapper, times(1)).updateById(any(ArdsProneCell.class));
        // status=0 的单元格保持不变
        assertEquals(0, cells.get(1).getStatus(), "已删除的单元格不应被修改");
    }

    @Test
    @DisplayName("多个旧时点：单元格按 tpIndex 匹配，不属于旧时点的单元格不删除")
    void multipleOldTimepoints_cellsMatchedByTpIndex() {
        ArdsProneRecord record = buildRecord(100L, "ICU01");
        when(recordMapper.selectById(100L)).thenReturn(record);

        // 旧时点只有 tpIndex=0 和 1
        List<ArdsProneTimepoint> oldTps = new ArrayList<>();
        oldTps.add(buildTp(1L, 0, "T0"));
        oldTps.add(buildTp(2L, 1, "+15"));
        when(timepointMapper.selectByRecord(100L))
                .thenReturn(oldTps)
                .thenReturn(Collections.emptyList());

        // 单元格：tpIndex=0(1个), tpIndex=1(1个), tpIndex=5(1个，不属于旧时点，不应删除)
        List<ArdsProneCell> cells = new ArrayList<>();
        cells.add(buildCell(11L, 0, "peep", "5", 1));
        cells.add(buildCell(12L, 1, "fio2", "60", 1));
        cells.add(buildCell(13L, 5, "pao2", "90", 1));  // tpIndex=5 不在旧时点中
        when(cellMapper.selectByRecord(100L)).thenReturn(cells);

        svc.resetTimepoints(100L);

        // 只有 2 个单元格被删除（tpIndex=0 和 1）
        verify(cellMapper, times(2)).updateById(any(ArdsProneCell.class));
        // tpIndex=5 的单元格保持 status=1
        assertEquals(1, cells.get(2).getStatus(), "不属于旧时点的单元格不应被删除");
    }
}
