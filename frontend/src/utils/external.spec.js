import { describe, it, expect, afterEach } from 'vitest'
import { externalParam, isExternalMode } from './external'

/**
 * jsdom 下 history.replaceState 要求 URL 与当前页面同源，
 * 且不能含未编码的特殊字符，统一基于当前 origin + pathname 构造。
 */
function setSearch(search) {
  const base = window.location.origin + window.location.pathname
  window.history.replaceState({}, '', base + search)
}

describe('externalParam', () => {
  afterEach(() => {
    setSearch('')
  })

  it('读取 URL 参数', () => {
    setSearch('?departCode=20070131')
    expect(externalParam('departCode')).toBe('20070131')
  })

  it('参数不存在返回空串', () => {
    setSearch('?foo=bar')
    expect(externalParam('departCode')).toBe('')
  })

  it('空参数返回空串', () => {
    setSearch('?departCode=')
    expect(externalParam('departCode')).toBe('')
  })

  it('去除首尾空格', () => {
    setSearch('?departCode=%2020070131%20')
    expect(externalParam('departCode')).toBe('20070131')
  })

  it('未替换的模板变量返回空串', () => {
    setSearch('?departCode=%24%7BdepartCode%7D')
    expect(externalParam('departCode')).toBe('')
  })

  it('其他模板变量也返回空串', () => {
    setSearch('?patientId=%24%7Bpatient.id%7D')
    expect(externalParam('patientId')).toBe('')
  })
})

describe('isExternalMode', () => {
  afterEach(() => {
    sessionStorage.clear()
  })

  it('未设置外链模式时返回 false', () => {
    expect(isExternalMode()).toBe(false)
  })

  it('设置外链模式后返回 true', () => {
    sessionStorage.setItem('extMode', '1')
    expect(isExternalMode()).toBe(true)
  })
})
