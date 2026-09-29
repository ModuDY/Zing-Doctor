package com.zing.doctor.icu.service.impl;

import com.zing.doctor.icu.dto.StaffCaInfo;
import com.zing.doctor.icu.mapper.IcuStaffCaMapper;
import com.zing.doctor.icu.service.StaffSignatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 人员电子签名 Service 实现：按工号从 ICU 只读库取电子签名图。
 *
 * <h3>为什么需要缓存</h3>
 * ICU 库是跨网远程达梦，而签名会在「打开文书预览 / 重新预览」时被请求。签名几乎不变，
 * 这里按工号做 10 分钟内存缓存，避免频繁打远程库；ICU 库故障时用 10 秒短缓存兜底，
 * 防止文书页在库不可用期间反复重试把连接池占满。
 *
 * <h3>失败语义</h3>
 * 任何异常都被吞掉并降级为 {@code found=false}：文书照常出，只是没有签名图。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffSignatureServiceImpl implements StaffSignatureService {

    /** 正常结果缓存时长：10 分钟 */
    private static final long TTL_MS = 10 * 60 * 1000L;

    /** 查询异常时的短缓存：10 秒，避免远程库故障时被反复重试 */
    private static final long ERROR_TTL_MS = 10 * 1000L;

    private final IcuStaffCaMapper icuStaffCaMapper;

    /** workNo → 缓存结果 */
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    @Override
    public Map<String, Object> getByWorkNo(String workNo) {
        String no = workNo == null ? "" : workNo.trim();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("workNo", no);
        if (no.isEmpty()) {
            data.put("found", false);
            data.put("realname", "");
            data.put("signatureImg", "");
            return data;
        }

        long now = System.currentTimeMillis();
        Cached cached = cache.get(no);
        if (cached == null || cached.expireAt <= now) {
            cached = query(no, now);
            cache.put(no, cached);
        }

        data.put("found", cached.found);
        data.put("realname", cached.realname);
        data.put("signatureImg", cached.signatureImg);
        return data;
    }

    // ------------------------------------------------------------------

    /** 查 ICU 库并选出最适合展示的一条签名；异常降级为空结果 */
    private Cached query(String workNo, long now) {
        List<StaffCaInfo> rows;
        try {
            rows = icuStaffCaMapper.selectByWorkNo(workNo);
        } catch (Exception e) {
            log.warn("[电子签名] 查询失败 workNo={}，{}s 内不再重试：{}",
                    workNo, ERROR_TTL_MS / 1000, e.getMessage());
            return new Cached(now + ERROR_TTL_MS, false, "", "");
        }

        String realname = "";
        String signature = "";
        StaffCaInfo fallback = null;
        if (rows != null) {
            for (StaffCaInfo row : rows) {
                if (row == null || isBlank(row.getSignatureImg())) {
                    continue;
                }
                if (isEnabled(row)) {
                    // 命中「未删除 + 启用」的记录，直接用
                    realname = nvl(row.getRealname());
                    signature = cleanSignature(row.getSignatureImg());
                    break;
                }
                if (fallback == null) {
                    fallback = row;
                }
            }
        }
        if (signature.isEmpty() && fallback != null) {
            // 没有启用记录：退而用任意一条有签名的历史记录，总比文书上没签名强
            realname = nvl(fallback.getRealname());
            signature = cleanSignature(fallback.getSignatureImg());
        }

        boolean found = !signature.isEmpty();
        if (found) {
            log.debug("[电子签名] 命中 workNo={}, realname={}, base64Len={}",
                    workNo, realname, signature.length());
        } else {
            log.info("[电子签名] 未配置 workNo={} 的电子签名，文书将不显示签名", workNo);
        }
        return new Cached(now + TTL_MS, found, realname, signature);
    }

    /**
     * 记录是否可用（未删除且启用）。
     * <p>status / del_flag 为空时按可用处理：宁可多显示一个签名，也不要因为字段缺失让签名整片消失。
     */
    private static boolean isEnabled(StaffCaInfo row) {
        String delFlag = nvl(row.getDelFlag());
        String status = nvl(row.getStatus());
        boolean notDeleted = delFlag.isEmpty() || "0".equals(delFlag);
        boolean enabled = status.isEmpty() || "1".equals(status);
        return notDeleted && enabled;
    }

    /** 去掉首尾空白与内部换行（达梦 CLOB 可能带换行；base64 中空白无意义） */
    private static String cleanSignature(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        // 已经是 data URI 或图片地址时原样返回，只有纯 base64 才去空白
        if (t.isEmpty() || t.startsWith("data:") || t.startsWith("http")) {
            return t;
        }
        return t.replaceAll("\\s+", "");
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String nvl(String s) {
        return s == null ? "" : s.trim();
    }

    /** 缓存条目（Java 8：不用 record） */
    private static final class Cached {
        final long expireAt;
        final boolean found;
        final String realname;
        final String signatureImg;

        Cached(long expireAt, boolean found, String realname, String signatureImg) {
            this.expireAt = expireAt;
            this.found = found;
            this.realname = realname;
            this.signatureImg = signatureImg;
        }
    }
}
