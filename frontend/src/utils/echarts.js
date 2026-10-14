/**
 * echarts 按需引入统一入口。
 *
 * 为什么：全量 `import * as echarts from 'echarts'` 会把所有图表类型、地图、3D、
 * 数据变换等全部打进一个约 1MB 的公共 chunk —— 首屏或首次进入任一图表页都要下载解析。
 * 这里只注册本项目实际用到的图表与组件（清单见下），体积可下降一半以上。
 *
 * ⚠️ 新增图表类型 / 组件时**必须**在下面登记，否则运行时才会报
 *    「Series xxx is used but not imported」或组件静默不生效（构建阶段不会报错）。
 */
import { markRaw } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  AxisPointerComponent,
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  MarkAreaComponent,
  MarkLineComponent,
  MarkPointComponent,
  TitleComponent,
  TooltipComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  // series 类型：本项目只用了折线 / 柱状 / 饼图
  LineChart, BarChart, PieChart,
  // 组件：坐标轴网格、提示、图例、标题、标点/标线/标域、内置缩放、轴指示线
  GridComponent, TooltipComponent, LegendComponent, TitleComponent,
  MarkPointComponent, MarkLineComponent, MarkAreaComponent,
  DataZoomComponent, AxisPointerComponent,
  // 渲染器：canvas（页面里 echarts.init(dom) 默认用法）
  CanvasRenderer
])

// 页面沿用了 `import * as echarts from '...'` + `echarts.init(...)` / `echarts.graphic.xxx` 的写法，
// 因此必须把 core 的具名导出（init / graphic / getInstanceByDom / dispose 等）re-export 出来，
// 否则 rollup 会按命名空间成员做静态校验并报 “xxx is not exported”。
// 注意：下面本地显式导出的 init 会覆盖（shadow）`export *` 里的同名导出，这正是我们要的统一出口。
export * from 'echarts/core'

/**
 * init 统一出口：实例必须 markRaw，禁止被 Vue 响应式系统代理。
 *
 * 为什么：页面习惯把实例存进响应式状态（`data()` 里的 `chart: null`，或 `charts: {}` 再
 * `this.charts[k] = echarts.init(el)`）。读取时拿到的是 Proxy，`setOption` 之后 ECharts
 * 内部流水线也在 Proxy 上跑，写进内部模型（GlobalModel 的坐标系缓存、axisPointer 的
 * `coordSysAxesInfo` 等）的属性落不到原始对象上，读侧取到 undefined，
 * 表现为鼠标悬浮不出 tooltip、轴指示线不跟随、部分内部状态失配。
 * 在唯一入口处包一层，全站图表一次性规避，不必逐个页面记得 markRaw。
 * 实现上本地导出的 init 优先级高于 `export * from 'echarts/core'`，因此所有
 * `import * as echarts from '../utils/echarts'` 的页面拿到的都是这个包装版。
 */
const rawInit = echarts.init
export function init(dom, theme, opts) {
  return markRaw(rawInit(dom, theme, opts))
}

export default echarts
