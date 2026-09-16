/**
 * 依赖漏洞门禁（CI 用）。
 *
 * 为什么不直接用 `npm audit --audit-level=high`：
 *   1. 它没有豁免机制。遇到「官方尚无向后兼容修复版本」的漏洞（如 jspdf 需跨大版本升级），
 *      CI 会长期变红，团队就会习惯性忽略红灯 —— 比漏洞本身更危险。
 *   2. 它无法区分「可直接升级修掉」与「必须破坏性升级/人工回归才能修」两类问题。
 *
 * 本脚本策略：
 *   - 检查**生产依赖**（--omit=dev）中 severity >= high 的项；
 *   - 命中豁免清单（audit-allowlist.json）的：打印告警 + 复核日期，不阻断；
 *   - 未命中豁免清单的：阻断（exit 1），并给出修复建议；
 *   - 豁免项超过 reviewBy 日期仍未消除：**升级为阻断**，强制重新评估（避免豁免变永久）。
 *
 * 用法：npm run audit:ci
 */
import { execSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const frontendRoot = join(here, '..')

const BLOCKING_LEVELS = ['critical', 'high']

function loadAllowlist() {
  try {
    // 去掉可能的 UTF-8 BOM：Windows 编辑器另存为「UTF-8 带 BOM」时，
    // JSON.parse 会直接报 Unexpected token，导致豁免静默失效
    const raw = readFileSync(join(frontendRoot, 'audit-allowlist.json'), 'utf8').replace(/^\uFEFF/, '')
    return JSON.parse(raw).waivers || []
  } catch (e) {
    console.error('[audit] 读取 audit-allowlist.json 失败：', e.message)
    return []
  }
}

function runAudit() {
  const cmd = 'npm audit --omit=dev --json'
  try {
    return JSON.parse(execSync(cmd, { cwd: frontendRoot, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }))
  } catch (e) {
    // npm audit 发现漏洞时退出码非 0，但 stdout 里仍然是完整 JSON
    const out = e.stdout ? e.stdout.toString() : ''
    if (!out.trim()) {
      console.error('[audit] npm audit 未返回可解析结果：', e.message)
      process.exit(2)
    }
    return JSON.parse(out)
  }
}

const allowlist = loadAllowlist()
const report = runAudit()
const vulns = report.vulnerabilities || {}
const today = new Date().toISOString().slice(0, 10)

const waived = []
const blocking = []
const expired = []

for (const [name, info] of Object.entries(vulns)) {
  if (!BLOCKING_LEVELS.includes(info.severity)) continue
  const waiver = allowlist.find((w) => w.package === name)
  if (!waiver) {
    blocking.push({ name, info })
    continue
  }
  if (waiver.reviewBy && waiver.reviewBy < today) {
    expired.push({ name, waiver })
  } else {
    waived.push({ name, info, waiver })
  }
}

const line = (s) => console.log(s)

if (waived.length) {
  line('\n[audit] 以下高危/严重漏洞已登记豁免（不计入阻断）：')
  for (const { name, info, waiver } of waived) {
    line(`  · ${name}（${info.severity}）— ${waiver.reason}`)
    line(`    复核期限：${waiver.reviewBy || '未设置'}；计划：${waiver.plan || '未填写'}`)
  }
}

if (!blocking.length && !expired.length) {
  const total = Object.keys(vulns).length
  line(`\n[audit] 通过：无未豁免的 high/critical 生产依赖漏洞（当前共 ${total} 条告警，均低于门槛或已豁免）`)
  process.exit(0)
}

if (expired.length) {
  line('\n[audit] 以下豁免已过期，必须重新评估（本次按阻断处理）：')
  for (const { name, waiver } of expired) {
    line(`  · ${name} — 复核期限 ${waiver.reviewBy} 已过，请升级依赖或更新豁免并写明新的复核期限`)
  }
}

if (blocking.length) {
  line('\n[audit] 以下 high/critical 漏洞未登记豁免，阻断本次构建：')
  for (const { name, info } of blocking) {
    const fix = info.fixAvailable
    const fixText = fix === true
      ? '可执行 npm audit fix 修复'
      : fix
        ? `需升级到 ${fix.name}@${fix.version}${fix.isSemVerMajor ? '（跨大版本，属破坏性变更）' : ''}`
        : '官方暂无修复版本'
    line(`  · ${name}（${info.severity}）：${fixText}`)
  }
  line('\n[audit] 处理方式：能升级就升级；确需暂缓的，写入 frontend/audit-allowlist.json 并注明原因、计划与复核期限。')
}

process.exit(1)
