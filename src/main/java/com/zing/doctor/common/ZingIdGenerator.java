package com.zing.doctor.common;

/**
 * 全局唯一 ID 生成器：15 位 = 毫秒时间戳(13) + 机器号(1) + 序列号(1)。
 *
 * <p>为什么是 15 位而不是标准雪花算法的 19 位：19 位超过 JavaScript 的安全整数上限
 * {@code 9007199254740991}，前端 {@code JSON.parse} 后末几位会变成 0，必须把所有 Long
 * 序列化成字符串，改动面很大。15 位最大值 {@code 999999999999999} 在安全范围内，
 * 前端 Number 直接放得下，<b>不需要动任何序列化配置</b>。
 *
 * <p><b>同一毫秒内超过 10 个时不阻塞，直接把时间戳进位到下一毫秒</b>。
 * 这一点很关键：早先用「秒 + 3 位序列、每秒 1000 个、超出就 sleep」的写法，
 * 质控批算一次插几万条要等几十秒。改为进位后，批量插入不再受配额限制，
 * 代价只是 ID 里的时间戳略超前于真实时间——ID 只用于唯一标识与排序，不影响业务。
 *
 * <p>机器号 0~9：单实例部署为 0；将来横向扩多副本时各实例配不同值即可
 * （{@code zing.worker-id}）。毫秒时间戳 13 位可用到 2286 年。
 */
public final class ZingIdGenerator {

    /** 单毫秒内可发出的序列号上限（1 位） */
    private static final int MAX_SEQUENCE = 9;

    private static final int MAX_WORKER_ID = 9;

    private final long workerId;

    private final Object lock = new Object();

    /** 当前毫秒内已发出的序列号 */
    private int sequence;

    /** 上一次生成时落在哪一毫秒 */
    private long lastMillis = -1;

    private ZingIdGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("机器号必须在 0~9 之间: " + workerId);
        }
        this.workerId = workerId;
    }

    private static volatile ZingIdGenerator instance;

    /** 按机器号初始化；只生效一次，重复调用忽略 */
    public static void init(long workerId) {
        if (instance == null) {
            synchronized (ZingIdGenerator.class) {
                if (instance == null) {
                    instance = new ZingIdGenerator(workerId);
                }
            }
        }
    }

    /** 取生成器；未 init 时按机器号 0 兜底，避免漏配直接 NPE */
    public static ZingIdGenerator getInstance() {
        if (instance == null) {
            init(0);
        }
        return instance;
    }

    /** 生成下一个 ID */
    public static long nextId() {
        return getInstance().next();
    }

    private long next() {
        synchronized (lock) {
            long millis = System.currentTimeMillis();

            // 时钟回拨：沿用上一毫秒继续发，靠序列号与时间单调推进保证不重复，
            // 绝不 sleep——批量插入时一次回拨就把整个任务卡住是不可接受的
            if (millis < lastMillis) {
                millis = lastMillis;
            }

            if (millis == lastMillis) {
                if (sequence >= MAX_SEQUENCE) {
                    // 本毫秒配额用尽：时间戳进位，不等待
                    millis = lastMillis + 1;
                    sequence = 0;
                } else {
                    sequence++;
                }
            } else {
                sequence = 0;
            }
            lastMillis = millis;
            return millis * 100L + workerId * 10L + sequence;
        }
    }
}
