import { describe, it, expect } from 'vitest'
import { toImageSrc } from './staffSignature'

describe('toImageSrc', () => {
  it('空值返回空串', () => {
    expect(toImageSrc(null)).toBe('')
    expect(toImageSrc(undefined)).toBe('')
    expect(toImageSrc('')).toBe('')
    expect(toImageSrc('   ')).toBe('')
  })

  it('data URI 原样返回', () => {
    const uri = 'data:image/png;base64,iVBORw0KGgo='
    expect(toImageSrc(uri)).toBe(uri)
  })

  it('http 地址原样返回', () => {
    expect(toImageSrc('http://example.com/sig.png')).toBe('http://example.com/sig.png')
  })

  it('https 地址原样返回', () => {
    expect(toImageSrc('https://example.com/sig.png')).toBe('https://example.com/sig.png')
  })

  it('PNG base64 魔数补 data URI', () => {
    const b64 = 'iVBORw0KGgoAAAANSUhEUg=='
    expect(toImageSrc(b64)).toBe('data:image/png;base64,' + b64)
  })

  it('GIF base64 魔数补 data URI', () => {
    const b64 = 'R0lGODlhAQABAIAAAA=='
    expect(toImageSrc(b64)).toBe('data:image/gif;base64,' + b64)
  })

  it('JPEG base64 魔数补 data URI', () => {
    const b64 = '/9j/4AAQSkZJRg=='
    expect(toImageSrc(b64)).toBe('data:image/jpeg;base64,' + b64)
  })

  it('BMP base64 魔数补 data URI', () => {
    const b64 = 'Qk02UgAAAA=='
    expect(toImageSrc(b64)).toBe('data:image/bmp;base64,' + b64)
  })

  it('WebP base64 魔数补 data URI', () => {
    const b64 = 'UklGRiQAAABXRUJQVlA4'
    expect(toImageSrc(b64)).toBe('data:image/webp;base64,' + b64)
  })

  it('未知格式默认按 png', () => {
    const b64 = 'ABCDEFG123456'
    expect(toImageSrc(b64)).toBe('data:image/png;base64,' + b64)
  })

  it('自动去除空白字符', () => {
    const b64 = 'iVBORw0KGgo=\n  AAA'
    expect(toImageSrc(b64)).toBe('data:image/png;base64,iVBORw0KGgo=AAA')
  })
})
