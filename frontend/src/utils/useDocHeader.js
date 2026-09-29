/**
 * 评分文书（APACHE II / SOFA）医院抬头：院徽 + 院名。
 *
 * 数据来源：系统参数
 *  - DOC_HOSPITAL_LOGO：院徽，图片 URL（如 /logo.png）或 base64 dataURI，直接给 <img src>
 *  - DOC_HOSPITAL_NAME：院名，多行文本，每行一个院名（字号按行递减，见 nameFontSize）
 *
 * 任一参数未配置或读取失败，静默回退到内置默认（与签名同样的降级原则，绝不打断文书）。
 * 模块级缓存：同一会话两个文书共用一次查询。
 */
import { ref } from 'vue'
import request from '../api/request'
import { getToken } from './auth'

const KEY_LOGO = 'DOC_HOSPITAL_LOGO'
const KEY_NAME = 'DOC_HOSPITAL_NAME'

/** 默认院徽：frontend/public/logo.png */
const DEFAULT_LOGO = '/logo.png'
/** 默认院名（多行） */
const DEFAULT_NAMES = ['福州市第二总医院', '福州市第二医院', '福建省福州中西医结合医院']
/** 院名字号按行递减（px），超出三行统一 14 */
const NAME_FONT_SIZES = [20, 18, 16]

/** 院名某一行的字号 */
export function nameFontSize(index) {
  return NAME_FONT_SIZES[index] || 14
}

/** 院名某一行的样式对象（避免在模板里拼字符串） */
export function nameStyle(index) {
  return {
    fontFamily: "'SimHei','黑体',sans-serif",
    fontSize: nameFontSize(index) + 'px',
    fontWeight: 700,
    letterSpacing: '2px'
  }
}

// 模块级缓存
let cache = null
let inflight = null

function parseNames(raw) {
  const lines = String(raw == null ? '' : raw)
    .split(/\r?\n/)
    .map((s) => s.trim())
    .filter(Boolean)
  return lines.length ? lines : [...DEFAULT_NAMES]
}

async function fetchHeader() {
  if (cache) return cache
  if (inflight) return inflight
  // 未登录时不请求需要认证的参数接口，直接用默认抬头（不缓存，登录后会重新请求）
  if (!getToken()) {
    return { logo: DEFAULT_LOGO, names: [...DEFAULT_NAMES] }
  }
  inflight = (async () => {
    let logo = DEFAULT_LOGO
    let names = [...DEFAULT_NAMES]
    try {
      // 抬头属非关键增强：silentError 关闭全局错误弹窗，失败在下方 catch 静默回退默认
      const [logoRes, nameRes] = await Promise.all([
        request.get('/sys-param/get', { params: { key: KEY_LOGO }, silentError: true }),
        request.get('/sys-param/get', { params: { key: KEY_NAME }, silentError: true })
      ])
      if (logoRes) logo = String(logoRes)
      if (nameRes) names = parseNames(nameRes)
    } catch (e) {
      // 降级：保持默认
      console.warn('文书抬头参数读取失败，使用默认抬头', e)
    }
    cache = { logo, names }
    return cache
  })()
  return inflight
}

export function useDocHeader() {
  const logo = ref(DEFAULT_LOGO)
  const hospitalNames = ref([...DEFAULT_NAMES])
  const loaded = ref(false)

  async function load() {
    const h = await fetchHeader()
    logo.value = h.logo
    hospitalNames.value = h.names
    loaded.value = true
  }

  return { logo, hospitalNames, loaded, load }
}
