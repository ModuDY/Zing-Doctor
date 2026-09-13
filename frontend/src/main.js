import { createApp } from 'vue'
import { captureExternalContext } from './utils/external'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'

// 必须在 createApp 之前同步捕获外链上下文：
// App.vue 在 setup 时就会读取 isExternalMode() 决定是否渲染侧边栏，
// 而 router.beforeEach 的捕获发生在其后（首次导航是异步的），会导致外链页误显示侧边栏。
captureExternalContext()

const app = createApp(App)

// 全局错误可见化：外链页面跑在客户内网，拿不到日志也不能直连排查，
// 统一把「哪个组件、哪个阶段、什么错误」打到控制台，避免现场只看到一个孤立的报错。
app.config.errorHandler = (err, instance, info) => {
  const name =
    (instance && instance.$options && instance.$options.name) ||
    (instance && instance.type && instance.type.__name) ||
    'unknown'
  console.error(
    `[frontend-error] ${(err && (err.message || err)) || ''} | component=${name} | phase=${info || 'unknown'}`
  )
}
window.addEventListener('error', (e) => {
  console.error(`[frontend-error] ${e.message} | file=${e.filename || 'inline'}:${e.lineno || 0}`)
})
window.addEventListener('unhandledrejection', (e) => {
  console.error('[frontend-error] unhandled rejection', e.reason)
})

app.use(ElementPlus, { locale: zhCn })
app.use(router)
app.mount('#app')
