import { describe, it, expect } from 'vitest'
import { isPlaceholderOperator, operatorLabel } from './operator'

describe('isPlaceholderOperator', () => {
  it('识别空值', () => {
    expect(isPlaceholderOperator(null)).toBe(true)
    expect(isPlaceholderOperator(undefined)).toBe(true)
    expect(isPlaceholderOperator('')).toBe(true)
    expect(isPlaceholderOperator('   ')).toBe(true)
  })

  it('识别 unknown 及常见拼错', () => {
    expect(isPlaceholderOperator('unknown')).toBe(true)
    expect(isPlaceholderOperator('unkonw')).toBe(true)
    expect(isPlaceholderOperator('unkown')).toBe(true)
    expect(isPlaceholderOperator('unknow')).toBe(true)
  })

  it('识别其他占位词', () => {
    expect(isPlaceholderOperator('null')).toBe(true)
    expect(isPlaceholderOperator('undefined')).toBe(true)
    expect(isPlaceholderOperator('none')).toBe(true)
    expect(isPlaceholderOperator('n/a')).toBe(true)
    expect(isPlaceholderOperator('na')).toBe(true)
    expect(isPlaceholderOperator('匿名')).toBe(true)
    expect(isPlaceholderOperator('未知')).toBe(true)
    expect(isPlaceholderOperator('无')).toBe(true)
    expect(isPlaceholderOperator('-')).toBe(true)
    expect(isPlaceholderOperator('--')).toBe(true)
  })

  it('识别未替换的模板变量', () => {
    expect(isPlaceholderOperator('${realname}')).toBe(true)
    expect(isPlaceholderOperator('${user.name}')).toBe(true)
  })

  it('大小写不敏感', () => {
    expect(isPlaceholderOperator('UNKNOWN')).toBe(true)
    expect(isPlaceholderOperator('Unknown')).toBe(true)
  })

  it('真实人名不被误判', () => {
    expect(isPlaceholderOperator('张医生')).toBe(false)
    expect(isPlaceholderOperator('丁妮')).toBe(false)
    expect(isPlaceholderOperator('Dr. Smith')).toBe(false)
    expect(isPlaceholderOperator('李')).toBe(false)
  })

  it('去除首尾空格后判断', () => {
    expect(isPlaceholderOperator('  unknown  ')).toBe(true)
    expect(isPlaceholderOperator('  丁妮  ')).toBe(false)
  })
})

describe('operatorLabel', () => {
  it('占位值返回空串', () => {
    expect(operatorLabel('unknown')).toBe('')
    expect(operatorLabel(null)).toBe('')
    expect(operatorLabel('${realname}')).toBe('')
  })

  it('真实人名返回去除空格后的值', () => {
    expect(operatorLabel('丁妮')).toBe('丁妮')
    expect(operatorLabel('  张医生  ')).toBe('张医生')
  })
})
