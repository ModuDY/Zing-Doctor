// 说明：本文件必须是 .mjs —— 项目 package.json 未声明 "type": "module"，
// 而 unplugin-vue-components / unplugin-element-plus 是 ESM-only 包，
// 若沿用 vite.config.js，vite 会把配置当 CJS 加载并报
// “resolved to an ESM file. ESM file cannot be loaded by `require`”。
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import ElementPlus from 'unplugin-element-plus/vite'

// 前端开发服务器：/api 与 /entry 代理到后端 8081
export default defineConfig({
  plugins: [
    vue(),
    // Element Plus 按需引入（替代原先 main.js 里的全量注册 + element-plus/dist/index.css）：
    //  1) 模板中的 <el-xxx> 由 Components + ElementPlusResolver 自动导入，并只注入该组件样式；
    //  2) 脚本里手写的 `import { ElMessage } from 'element-plus'`（全项目 20 余处）
    //     由 unplugin-element-plus 注入对应样式 —— 两者缺一会出现「组件能渲染但没有样式」。
    Components({ resolvers: [ElementPlusResolver()], dts: false }),
    ElementPlus()
  ],
  server: {
    port: 5173,
    host: '0.0.0.0',
    proxy: {
      '/api': { target: 'http://localhost:8081', changeOrigin: true },
      '/entry': { target: 'http://localhost:8081', changeOrigin: true }
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        // 分包：把体积大、且非首屏必需的依赖单独成 chunk。
        // 此前全部挤进入口 chunk（实测入口 1.1MB + echarts 1.0MB 公共块），首屏代价高；
        // 拆分后首屏只加载 vendor（vue/axios 等）+ 当前页 chunk，图表/导出库按需到达。
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          // 只给「全站高频共享」的两个大库固定分包，保证跨页缓存命中。
          // ⚠️ 不要加 `return 'vendor'` 之类的兜底：那会把仅个别页面用到的库
          // （xlsx / jspdf / html2canvas）也塞进被入口引用的公共 chunk，
          // 首屏白白多下载 1MB+（实测过）。其余依赖交给 Rollup 自动分包：
          // 多页共享的会提升为公共 chunk，页面专属的留在页面 chunk 里。
          if (id.includes('echarts') || id.includes('zrender')) return 'vendor-echarts'
          if (id.includes('element-plus') || id.includes('@element-plus')) return 'vendor-element-plus'
        }
      }
    }
  }
})
