/**
 * APACHE II 总览页 端到端联调桩服务（仅用于本地验证，不入交付包）
 *
 * 复刻真实链路：
 *   /entry/{pageCode}            -> 校验 extToken，302 到前端路由（透传 pageCode/extToken/业务参数）
 *   /api/apache2/overview        -> 校验 X-External-PageCode + X-External-Token，返回 Result 信封
 *   其余                          -> 静态托管 frontend/dist，未知路径回退 index.html（SPA history）
 */
const http = require('http')
const fs = require('fs')
const path = require('path')

const DIST = path.resolve(__dirname, '..', 'frontend', 'dist')
const PORT = Number(process.env.PORT || 5199)
const TOKEN = process.env.EXT_TOKEN || 'zing-icu-link-token-2026'
const LOG = path.join(__dirname, 'server.log')

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
  '.ttf': 'font/ttf'
}

// ---- 与后端 Result 信封完全一致的返回体 ----
function envelope(code, message, data) {
  return JSON.stringify({ code, message, data })
}

const OVERVIEW = {
  totalCount: 3,
  avgScore: 18.7,
  avgMortality: 32.5,
  highRiskCount: 1,
  scoreDistribution: { '0-4': 0, '5-9': 0, '10-14': 1, '15-19': 1, '20-24': 1, '25-29': 0, '30+': 0 },
  records: [
    { id: 101, patientName: '测试患者甲', inHospitalNo: 'ZY0001', departCode: 'ICU01', scoreTime: '2026-09-12 08:30:00', scoreType: 'auto', ageScore: 5, chronicScore: 0, gcsScore: 3, physiologyScore: 12, totalScore: 20, mortalityRate: 45.6, diagnosisType: 'none', createBy: '系统自动' },
    { id: 102, patientName: '测试患者乙', inHospitalNo: 'ZY0002', departCode: 'ICU01', scoreTime: '2026-09-11 19:05:00', scoreType: 'custom', ageScore: 3, chronicScore: 0, gcsScore: 5, physiologyScore: 9, totalScore: 17, mortalityRate: 28.1, diagnosisType: 'operative', createBy: '张医生' },
    { id: 103, patientName: '测试患者丙', inHospitalNo: 'ZY0003', departCode: 'ICU01', scoreTime: '2026-09-10 10:00:00', scoreType: 'reviewed', ageScore: 2, chronicScore: 2, gcsScore: 4, physiologyScore: 11, totalScore: 19, mortalityRate: 33.4, diagnosisType: 'nonoperative', createBy: '李医生' }
  ]
}

function log(line) {
  fs.appendFileSync(LOG, line + '\n')
}

function sendJson(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' })
  res.end(body)
}

// 在 index.html 中注入错误收集器：运行期错误/警告会写进 <html data-err="...">，
// 这样 --dump-dom 的输出里就能直接看到报错，不依赖浏览器 stderr
const ERR_SNIFF = `<script>
window.__errs = [];
function __flush() { document.documentElement.setAttribute('data-err', window.__errs.join(' || ')); }
window.addEventListener('error', function (e) {
  window.__errs.push('ERR:' + (e.message || '') + ' @ ' + (e.filename || '') + ':' + (e.lineno || ''));
  __flush();
}, true);
window.addEventListener('unhandledrejection', function (e) {
  window.__errs.push('REJ:' + ((e.reason && (e.reason.message || e.reason)) || ''));
  __flush();
});
['error', 'warn'].forEach(function (k) {
  var o = console[k];
  console[k] = function () {
    try { window.__errs.push(k.toUpperCase() + ':' + Array.prototype.join.call(arguments, ' ')); __flush(); } catch (err) {}
    o.apply(console, arguments);
  };
});
</script>`

function serveStatic(req, res, pathname) {
  let rel = pathname === '/' ? '/index.html' : pathname
  let file = path.join(DIST, rel)
  if (!file.startsWith(DIST) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
    // SPA history 回退
    file = path.join(DIST, 'index.html')
  }
  const ext = path.extname(file).toLowerCase()
  if (ext === '.html') {
    const html = fs.readFileSync(file, 'utf8').replace('</head>', ERR_SNIFF + '</head>')
    res.writeHead(200, { 'Content-Type': MIME['.html'] })
    return res.end(html)
  }
  res.writeHead(200, { 'Content-Type': MIME[ext] || 'application/octet-stream' })
  res.end(fs.readFileSync(file))
}

const server = http.createServer((req, res) => {
  const u = new URL(req.url, 'http://127.0.0.1')
  const p = u.pathname
  if (!p.startsWith('/api/')) log(`[static] ${p}`)

  // ---- 外链入口：校验 extToken 后 302 ----
  if (p.startsWith('/entry/')) {
    const pageCode = p.slice('/entry/'.length)
    const token = u.searchParams.get('extToken')
    log(`[entry] ${p} extToken=${token}`)
    if (token !== TOKEN) {
      log('[entry] REJECT token mismatch')
      return sendJson(res, 401, envelope(401, 'ICU 外链 token 校验失败', null))
    }
    const q = new URLSearchParams()
    q.set('pageCode', pageCode)
    q.set('extToken', token)
    for (const [k, v] of u.searchParams) {
      if (k !== 'extToken') q.set(k, v)
    }
    const target = `/page/${pageCode}?${q.toString()}`
    log(`[entry] 302 -> ${target}`)
    res.writeHead(302, { Location: target })
    return res.end()
  }

  // ---- 业务接口：校验外链请求头 ----
  if (p.startsWith('/api/')) {
    const headerPage = req.headers['x-external-pagecode']
    const headerToken = req.headers['x-external-token']
    log(`[api] ${p}${u.search} pageCode=${headerPage} token=${headerToken}`)
    if (!headerPage || headerToken !== TOKEN) {
      log('[api] REJECT missing/invalid external headers')
      return sendJson(res, 401, envelope(401, '缺少外链 signature/token', null))
    }
    if (p === '/api/apache2/overview') {
      return sendJson(res, 200, envelope(0, 'ok', OVERVIEW))
    }
    if (p === '/api/apache2/auto-generate') {
      return sendJson(res, 200, envelope(0, 'ok', { scanned: 5, created: 1, skipped: 4, failed: 0 }))
    }
    return sendJson(res, 404, envelope(404, '未注册的接口 ' + p, null))
  }

  serveStatic(req, res, p)
})

server.listen(PORT, '127.0.0.1', () => {
  log(`[boot] listening on ${PORT}, dist=${DIST}`)
  console.log(`mock server on http://127.0.0.1:${PORT}`)
})
