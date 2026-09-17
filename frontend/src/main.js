import { createApp } from 'vue'
import { captureExternalContext } from './utils/external'
import App from './App.vue'
import router from './router'

// Element Plus 已改为按需引入（见 vite.config.js 的 Components/ElementPlus 插件），
// 不再全量 `import ElementPlus` + `element-plus/dist/index.css`；
// 全局中文语言包改由 App.vue 的 <el-config-provider :locale> 提供。

// 必须在 createApp 之前同步捕获外链上下文：
// App.vue 在 setup 时就会读取 isExternalMode() 决定是否渲染侧边栏，
// 而 router.beforeEach 的捕获发生在其后（首次导航是异步的），会导致外链页误显示侧边栏。
captureExternalContext()

const app = createApp(App)

/**
 * 是否是可以忽略的错误。
 *
 * <p>目前只有一类：ResizeObserver 的 "loop completed with undelivered notifications"。
 * 它不是代码缺陷，而是浏览器的实现限制 —— observer 回调里改了布局，浏览器检测到循环，
 * 就丢弃本轮未投递的通知并抛这个错；下一帧会重新计算，功能上没有任何影响。
 *
 * <p>触发者大多是组件库内部（Element Plus 的表格、布局都用 ResizeObserver 自适应），
 * 业务代码改不掉。不忽略的话，它会反复混在真正的前端错误里，把需要看的报错淹掉 ——
 * 现场排查时最难的不是「有报错」，而是「报错太多看不出哪条是根因」。
 */
function isIgnorableError(msg) {
  const s = msg == null ? '' : String(msg)
  return s.indexOf('ResizeObserver loop') >= 0
}

// 全局错误可见化：外链页面跑在客户内网，拿不到日志也不能直连排查，
// 统一把「哪个组件、哪个阶段、什么错误」打到控制台，避免现场只看到一个孤立的报错。
app.config.errorHandler = (err, instance, info) => {
  const message = (err && (err.message || err)) || ''
  if (isIgnorableError(message)) {
    return
  }
  const name =
    (instance && instance.$options && instance.$options.name) ||
    (instance && instance.type && instance.type.__name) ||
    'unknown'
  console.error(
    `[frontend-error] ${message} | component=${name} | phase=${info || 'unknown'}`
  )
}
window.addEventListener('error', (e) => {
  if (isIgnorableError(e.message)) {
    return
  }
  console.error(`[frontend-error] ${e.message} | file=${e.filename || 'inline'}:${e.lineno || 0}`)
})
window.addEventListener('unhandledrejection', (e) => {
  // reason 通常是 Error 对象，取 message 判断；否则退回整体字符串
  const reason = e && e.reason
  const msg = reason && reason.message ? reason.message : reason
  if (isIgnorableError(msg)) {
    return
  }
  console.error('[frontend-error] unhandled rejection', reason)
})

app.use(router)
app.mount('#app')
