/**
 * 日期时间格式化统一出口（供新写的页面使用）。
 *
 * 背景：各页面此前各自写了十几份 fmtDate / fmtTime / formatTime，看着同名，
 * 实际口径并不一致 —— 有的带年份有的不带、有的截到分钟有的到秒、
 * 空值占位符还有「—」「--」「空串」三种。硬合并成一个 fmtDate 会改掉现有页面显示，
 * 所以这里按「语义」拆成具名函数，一个名字只对应一种口径。
 *
 * 用法：新页面直接 import；历史页面保持不动，等因别的需求改动时再顺手替换。
 */

/** 空值默认占位符，与现有多数页面一致（全角破折号） */
export const EMPTY = '—'

function pad(n) {
  return String(n).padStart(2, '0')
}

/**
 * 空值判定。除 null / undefined / 空串外，还判 'null' 字符串 ——
 * 后端个别接口真会返回字面量 "null"（MdroPatients 原先就是为此单独加的判断）。
 */
function isBlank(v) {
  return v == null || v === '' || v === 'null'
}

/** 规整：Date 按本地时间展开，字符串去掉 ISO 的 T 分隔符 */
function normalize(v) {
  if (v instanceof Date) {
    return `${v.getFullYear()}-${pad(v.getMonth() + 1)}-${pad(v.getDate())} `
      + `${pad(v.getHours())}:${pad(v.getMinutes())}:${pad(v.getSeconds())}`
  }
  return String(v).trim().replace('T', ' ')
}

/** 2026-09-22（对应 Apache2Overview / QualityBoard 里入参为 Date 的两份 fmtDate 的日期口径） */
export function fmtDate(v, empty = EMPTY) {
  return isBlank(v) ? empty : normalize(v).slice(0, 10)
}

/** 2026-09-22 16:30（对应 ARDSMonitor / DischargeStats / ArdsProneRecord 的截断到分钟口径） */
export function fmtDateTime(v, empty = EMPTY) {
  return isBlank(v) ? empty : normalize(v).slice(0, 16)
}

/** 2026-09-22 16:30:00（对应 HandoverBoard 的 19 位口径、Apache2Score 的 formatLocalDateTime） */
export function fmtDateTimeSec(v, empty = EMPTY) {
  return isBlank(v) ? empty : normalize(v).slice(0, 19)
}

/** 09-22 16:30（对应 ArdsProneConfig / ArdsProneList 刻意省去年份的窄列口径） */
export function fmtMonthDay(v, empty = EMPTY) {
  return isBlank(v) ? empty : normalize(v).slice(5, 16)
}
