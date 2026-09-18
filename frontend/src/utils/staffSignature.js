/**
 * 评分文书签名医师的电子签名。
 *
 * 数据来源：ICU 只读库 `config_staff_ca_info`，按工号（外链参数 username）反查，
 * 由后端 `/api/staff/signature` 提供。签名取到后渲染在文书「评分医师」下方，
 * 并随文书一起导出到 PDF。
 *
 * 设计原则：签名属于锦上添花——任何一步失败都静默降级为「无签名」，绝不打断文书流程。
 */
import { ref, reactive } from 'vue'
import { fetchStaffSignature } from '../api/staff'

/**
 * 后端返回的签名串 → 可直接给 `<img src>` 用的地址。
 * 支持：data URI（原样）、http(s) 地址（原样）、纯 base64（按魔数补 data:image/xxx;base64,）。
 */
export function toImageSrc(raw) {
  const s = String(raw == null ? '' : raw).replace(/\s+/g, '')
  if (!s) return ''
  if (s.startsWith('data:')) return s
  if (/^https?:\/\//i.test(s)) return s
  return 'data:image/' + sniffFormat(s) + ';base64,' + s
}

/** base64 魔数 → 图片格式（ICU 侧签名多为 GIF/PNG） */
function sniffFormat(b64) {
  if (b64.startsWith('iVBOR')) return 'png'
  if (b64.startsWith('R0lGOD')) return 'gif'
  if (b64.startsWith('/9j/')) return 'jpeg'
  if (b64.startsWith('Qk')) return 'bmp'
  if (b64.startsWith('UklGR')) return 'webp'
  return 'png'
}

/** 预加载图片：确保文书生成 PDF 时图片已解码，html2canvas 才能画进画布 */
function preload(src) {
  return new Promise((resolve) => {
    const img = new Image()
    img.onload = () => resolve(true)
    img.onerror = () => resolve(false)
    img.src = src
  })
}

/**
 * 文书签名加载器。
 * @returns {{ signatureSrc: object, load: (workNo?: string) => Promise<void> }}
 *          signatureSrc 为空字符串时表示「无签名」，模板用 v-if 判断即可。
 */
export function useStaffSignature() {
  const signatureSrc = ref('')

  async function load(workNo) {
    const no = String(workNo == null ? '' : workNo).trim()
    // 非外链进入（无工号）时不请求，避免无谓报错
    if (!no) return
    try {
      const res = await fetchStaffSignature(no)
      const src = toImageSrc(res && res.signatureImg)
      if (!src) return
      if (await preload(src)) {
        signatureSrc.value = src
      } else {
        console.warn('[电子签名] 图片解码失败，文书将不显示签名')
      }
    } catch (e) {
      console.warn('[电子签名] 加载失败，文书将不显示签名', e)
    }
  }

  return { signatureSrc, load }
}

/**
 * 文书上多个签名人（记录医师 / 记录护士 / 上级医师）的签名加载器。
 *
 * 与 useStaffSignature 的区别：它按工号缓存多张图，并在取图时核对「工号 ↔ 姓名」——
 * 工号与签名人对不上时宁可不显示图（退回打印姓名），
 * 也不能在医疗文书上盖一个同名不同人的章。
 *
 * @returns {{ signMap: object, load: Function, loadAll: Function }}
 *          signMap[workNo] = 可直接给 `<img src>` 用的地址；没有该键 = 无签名。
 */
export function useStaffSignatures() {
  const signMap = reactive({})

  async function load(workNo, expectName) {
    const no = String(workNo == null ? '' : workNo).trim()
    if (!no || signMap[no]) return
    try {
      const res = await fetchStaffSignature(no)
      const realName = res && res.realname ? String(res.realname).trim() : ''
      if (expectName && realName && realName !== String(expectName).trim()) {
        console.warn('[电子签名] 工号 ' + no + ' 的姓名为「' + realName + '」，与签名人「'
          + expectName + '」不符，文书将不显示该签名')
        return
      }
      const src = toImageSrc(res && res.signatureImg)
      if (!src) return
      if (await preload(src)) {
        signMap[no] = src
      } else {
        console.warn('[电子签名] 工号 ' + no + ' 的签名图片解码失败，文书将不显示签名')
      }
    } catch (e) {
      console.warn('[电子签名] 工号 ' + no + ' 的签名加载失败，文书将不显示签名', e)
    }
  }

  /** 批量加载：items = [{ workNo, name }]；打印前调用，确保 html2canvas 截图时图已解码 */
  async function loadAll(items) {
    await Promise.all((items || []).map(it => load(it && it.workNo, it && it.name)))
  }

  return { signMap, load, loadAll }
}
