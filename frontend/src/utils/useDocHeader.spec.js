import { describe, it, expect, vi, beforeEach } from 'vitest'

// 用 hoisted 保证 mock 在 vi.mock 工厂提升后仍可用
const mocks = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('../api/request', () => ({ default: { get: mocks.get } }))

beforeEach(() => {
  mocks.get.mockReset()
  vi.resetModules()
})

/** 每个用例拿到未被模块级缓存污染的新鲜模块 */
async function fresh() {
  return await import('./useDocHeader')
}

describe('nameFontSize', () => {
  it('前三行 20/18/16，超出统一 14', async () => {
    const m = await fresh()
    expect(m.nameFontSize(0)).toBe(20)
    expect(m.nameFontSize(1)).toBe(18)
    expect(m.nameFontSize(2)).toBe(16)
    expect(m.nameFontSize(3)).toBe(14)
    expect(m.nameFontSize(99)).toBe(14)
  })
})

describe('nameStyle', () => {
  it('返回院名行样式对象', async () => {
    const m = await fresh()
    const s0 = m.nameStyle(0)
    expect(s0.fontSize).toBe('20px')
    expect(s0.fontWeight).toBe(700)
    expect(s0.letterSpacing).toBe('2px')
    expect(s0.fontFamily).toContain('SimHei')
    expect(m.nameStyle(2).fontSize).toBe('16px')
  })
})

describe('useDocHeader', () => {
  function answer(map) {
    mocks.get.mockImplementation((url, cfg) =>
      Promise.resolve(cfg && cfg.params && map[cfg.params.key] !== undefined
        ? map[cfg.params.key]
        : null)
    )
  }

  it('读取参数并解析多行院名', async () => {
    answer({
      DOC_HOSPITAL_LOGO: '/my-logo.png',
      DOC_HOSPITAL_NAME: 'XX医院\nYY分院'
    })
    const m = await fresh()
    const h = m.useDocHeader()
    await h.load()
    expect(h.logo.value).toBe('/my-logo.png')
    expect(h.hospitalNames.value).toEqual(['XX医院', 'YY分院'])
    expect(h.loaded.value).toBe(true)
  })

  it('请求失败回退内置默认', async () => {
    mocks.get.mockRejectedValue(new Error('network'))
    const m = await fresh()
    const h = m.useDocHeader()
    await h.load()
    expect(h.logo.value).toBe('/logo.png')
    expect(h.hospitalNames.value).toEqual([
      '福州市第二总医院',
      '福州市第二医院',
      '福建省福州中西医结合医院'
    ])
  })

  it('院名参数全是空白行时回退默认', async () => {
    answer({ DOC_HOSPITAL_NAME: '  \n   ' })
    const m = await fresh()
    const h = m.useDocHeader()
    await h.load()
    expect(h.hospitalNames.value.length).toBe(3)
  })

  it('院名自动去除空行和首尾空格', async () => {
    answer({ DOC_HOSPITAL_NAME: '  第一行  \n\n\n第二行\n' })
    const m = await fresh()
    const h = m.useDocHeader()
    await h.load()
    expect(h.hospitalNames.value).toEqual(['第一行', '第二行'])
  })
})
