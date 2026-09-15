---
name: "ep-design-theme-adapt"
description: "Adapt HTML/CSS design mockups into existing Vue3 + Element Plus pages as theme-only changes. Invoke when porting a design board's visual style into system pages while preserving columns/buttons/logic."
---

# 设计稿风格适配到 Element Plus 存量页面

把外部 HTML 设计稿（如独立 redesign 目录下的静态稿）的视觉风格，适配进已有的 Vue3 + Element Plus 业务页面。
**只换皮，不动骨**：列名、操作按钮、接口调用、业务逻辑、路由全部保留；仅视觉风格迁移，外加用户明确批准的"总览/头图区域完全照设计稿"及其前端数据聚合。

## 何时触发

- 用户说"把 XX 设计图/设计稿的样式适配到系统 XX 功能里"、"按这个风格改页面"、"只换风格，功能不动"
- 工作区中存在 `*-redesign/`、`design/`、`mockup/` 等目录，内含与业务页对应的静态 HTML/CSS 稿
- 需要把一套新的设计令牌（配色/圆角/阴影/标签风）铺到多个 EP 页面

不适用：从零新建页面（用前端设计类 skill）、纯 bug 修复、需要改交互/增删功能的需求。

## 铁律（违反必返工）

1. **先问清再动手**：适配页面范围、哪些区域"完全照设计稿"（如总览区）、统计口径是否变化、设计稿多出的控件（搜索框/下拉/双层表头等）加不加、原有列名与操作按钮一律保留——用 AskUserQuestion 一次性问完，获批后再改。
2. **样式与逻辑分离**：不改接口、不改字段绑定、不改事件处理、不改列。设计稿数据口径不同时，用前端 computed 聚合现有数据，不动后端。
3. **不污染全局**：禁止改全局 EP 样式或 App.vue 影响其他模块。所有覆盖限定在专属作用域类下（如 `.qb-theme`）。
4. **保留有价值的中文业务注释**（口径说明、开闭区间、空壳指标等），改样式时不要误删。
5. **不主动 git commit、不主动建文档**。

## 标准流程

### 第 1 步：研读与提取（只读）

1. 读设计稿的令牌源 CSS（变量定义文件）与每个对应 HTML 页，提取：
   - 主色阶（primary + hover/active/light 软底）、中性灰阶、状态色（success/warning/danger/info 各自的实色与 soft 底色）
   - 圆角阶梯、阴影（注意层级与透明度）、边框色、页面底色、字号阶梯、字体栈、数字是否 tabular-nums
   - 卡片/标签（是否胶囊 9999px）/表头/代码块/弹层等组件级规范
2. 读每个目标 Vue 页面全文：模板结构、scoped 样式现状、弹窗/抽屉/确认框、ECharts 配色、原生 HTML 控件（可能非 EP）、现有 import 与图标。
3. 建立"设计值 → 页面现状值"颜色替换清单（grep 旧色值，如 `#409eff`/`#1d4ed8`/`#f5f7fa`，确保无遗漏）。

### 第 2 步：建共享主题文件（多页面共用一套时）

在 `src/styles/` 下新建一个 `<scope>-theme.css`，结构固定为三块：

```css
/* 1) 业务页内组件：根作用域类上重定义 EP 变量（唯一主色入口，不要到处写死选择器） */
.<scope>-theme {
  --el-color-primary: #ea580c;
  --el-color-primary-light-3: ...;   /* hover 浅 */
  --el-color-primary-light-5/7/8/9: ...;
  --el-color-primary-dark-2: ...;    /* hover 深 */
  --el-color-success/warning/danger/info: ...; /* 及各自 light-3/5/7/8/9 */
  --el-text-color-primary/regular/secondary/placeholder: ...;
  --el-border-color(-light/-lighter/-extra-light/-dark): ...;
  --el-fill-color(-light/-lighter/-extra-light/-blank): ...;
  --el-border-radius-base/small: 8px/...;
  --el-font-size-base: ...;
}
/* 2) 传送层下拉：popper-class 挂类，下拉项 hover/selected、日期面板圆角 */
.<scope>-popper .el-select-dropdown__item ... { ... }
/* 3) 传送层弹层：dialog/drawer/message-box 圆角、分隔线、抽屉 body 底色 */
.<scope>-overlay .el-dialog ... { ... }
```

再在该文件内补组件级覆盖（全部限定在三个作用域类下）：
`el-button`（hover/active/disabled 状态矩阵）、`el-tag`（胶囊+软底+无边框）、`el-alert`、
`el-table`（表头底色/字号/字色、单元格 padding、格线、斑马纹、hover 行）、
`el-tabs`（item padding、激活色、active-bar 2px）、`el-descriptions`、
`el-collapse`（每个分组独立卡片：12px 圆角、margin-bottom、展开态 header/wrap 分瓣圆角）。

注意：
- EP light-N 数字越大颜色越浅，别映射反；写完用取色逻辑核对 light-3（hover）比主色浅。
- CSS 无效声明（如色值里混入空格 `#eeede c;`）会被浏览器静默忽略，靠 build 查不出来，写完人工通读一遍。

### 第 3 步：逐页适配（每页固定 checklist）

模板：
- [ ] 根节点加作用域类：`<div class="xxx qb-theme">`（与原 class 并存）
- [ ] 每个 `el-select / el-date-picker / el-cascader / el-time-picker / el-autocomplete` 加 `popper-class="<scope>-popper"`（含筛选栏、弹窗内、表格内的所有实例，grep 兜底）
- [ ] `el-dialog / el-drawer` 加 `class="<scope>-overlay"`
- [ ] 所有 `ElMessageBox.confirm/alert/prompt` 调用加 `customClass: '<scope>-overlay'`（EP 2.x 支持；grep `ElMessageBox` 兜底）
- [ ] `<script setup>` 顶部 `import '../styles/<scope>-theme.css'`
- [ ] `el-collapse` 加域分卡片类；容器卡片类（filter/chart/table/tabs 外壳）按设计稿加 `.qb-card`
- [ ] 完全照设计稿的区域（如总览）：改模板结构 + 新增 computed 聚合；周期文案优先从现有筛选条件本地拼闭区间，保证其他 Tab 下也能展示
- [ ] ECharts：`lineStyle/itemStyle/areaStyle 渐变/axisLabel 色/splitLine 色`全套换令牌色（设计稿常见线宽 3px、面积渐变 0.18→0.01）
- [ ] 图标映射：设计稿图标库（如 lucide）→ `@element-plus/icons-vue`，映射后必须去 `node_modules/@element-plus/icons-vue/dist/types/components/` 确认组件存在（如 eye-off 对应 `Hide`），再替换 import

样式（重写 `<style scoped>`）：
- [ ] 页面底色/留白（设计稿常见 `#fafaf9` + 24px padding）
- [ ] 卡片：`1px solid` 暖灰描边 + 12px 圆角 + 极轻阴影 `0 1px 2px rgba(28,25,23,.04)`
- [ ] 标题竖条 `.dot` 渐变、链接色（蓝→橙 + hover 深色）、代码块（设计稿近黑底 `#1c1917`/暖灰字）
- [ ] 原生 HTML 页面（无 EP）：按钮/输入/表格/标签/弹窗/Tab 全部手工按令牌重写；输入框 focus 用边框色 + 3px 12% 透明 ring
- [ ] 状态矩阵走查：default / hover / active / focus / selected / disabled 六态都要有值
- [ ] 响应式断点（1200/1100 两列、640 一列）

### 第 4 步：验证（三步全过）

1. `npm run build`（vite build）无编译错误；chunk 体积警告属存量可忽略。
2. GetDiagnostics 检查所有改过的 .vue，零诊断。
3. `npm run dev` 起服务，OpenPreview 逐页肉眼核对；后端没起时接口报错正常，重点看静态样式；切换 Tab、打开所有弹窗/抽屉/确认框、点开下拉看传送层配色。

## 常见坑（本工作区实测）

- **EP 传送层不继承作用域类**：下拉/弹窗默认 append 到 body，忘记 `popper-class`/`customClass` 会残留旧蓝主题——这是最常见的漏网项，用 grep 兜底。
- **scoped 样式碰不到 EP 内部**：用 `:deep(.el-xxx)`，但只做窄覆盖；主色一律走 CSS 变量。
- **不要试错式局部改色**（历史教训：用户评价"丑/不一致"）：先定令牌再统一替换，一轮成体系，不要改一个组件问一次。
- **总览常驻改造**：移除旧 `v-if` 后检查旧 computed（如 summary/rate）是否还有模板引用，grep 变量名清理干净；el-tabs 外壳包白卡时用 `:deep(.el-tabs__header/__content)` 调内边距。
- **Windows 环境**：PowerShell 可能报 profile 执行策略红字（不影响命令输出，忽略即可）；若 `npm` 不在 PATH，找到 node 安装目录后 `$env:PATH = '<nodeDir>;' + $env:PATH; & npm.cmd run build`（用 npm.cmd 全名，npm.ps1 会被执行策略拦）。
- **Glob 搜不到 node_modules 内文件**是工具默认忽略，不代表依赖没装；用 LS 直接列目录确认。

## 交付汇报要点

说明：改了哪些文件、哪些区域完全照设计稿、总览数据口径如何聚合、哪些原有元素刻意保留、构建/诊断结果、预览地址；并注明未提交 git。
