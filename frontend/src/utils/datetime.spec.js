import { describe, it, expect } from 'vitest'
import {
  fmtDate,
  fmtDateTime,
  fmtDateTimeSec,
  fmtMonthDay,
  EMPTY
} from './datetime'

describe('空值处理', () => {
  it('null / undefined / 空串返回占位符', () => {
    expect(fmtDate(null)).toBe(EMPTY)
    expect(fmtDate(undefined)).toBe(EMPTY)
    expect(fmtDate('')).toBe(EMPTY)
    expect(fmtDateTime(null)).toBe(EMPTY)
    expect(fmtDateTimeSec(null)).toBe(EMPTY)
    expect(fmtMonthDay(null)).toBe(EMPTY)
  })

  it('字符串 "null" 也视为空', () => {
    expect(fmtDate('null')).toBe(EMPTY)
  })

  it('支持自定义占位符', () => {
    expect(fmtDate(null, '--')).toBe('--')
  })
})

describe('fmtDate', () => {
  it('ISO 字符串截取日期', () => {
    expect(fmtDate('2026-09-22T16:30:00')).toBe('2026-09-22')
  })

  it('空格分隔的日期时间字符串', () => {
    expect(fmtDate('2026-09-22 16:30:00')).toBe('2026-09-22')
  })

  it('Date 对象按本地时间展开', () => {
    const d = new Date(2026, 8, 22, 16, 30, 0)
    expect(fmtDate(d)).toBe('2026-09-22')
  })
})

describe('fmtDateTime', () => {
  it('截取到分钟', () => {
    expect(fmtDateTime('2026-09-22T16:30:45')).toBe('2026-09-22 16:30')
  })

  it('Date 对象', () => {
    const d = new Date(2026, 8, 22, 16, 30, 45)
    expect(fmtDateTime(d)).toBe('2026-09-22 16:30')
  })
})

describe('fmtDateTimeSec', () => {
  it('截取到秒', () => {
    expect(fmtDateTimeSec('2026-09-22T16:30:45')).toBe('2026-09-22 16:30:45')
  })
})

describe('fmtMonthDay', () => {
  it('省去年份，截取到分钟', () => {
    expect(fmtMonthDay('2026-09-22T16:30:00')).toBe('09-22 16:30')
  })
})
