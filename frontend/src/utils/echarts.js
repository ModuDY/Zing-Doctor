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
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
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
  // 组件：坐标轴网格、提示、图例、标题、标点/标线/标域、内置缩放
  GridComponent, TooltipComponent, LegendComponent, TitleComponent,
  MarkPointComponent, MarkLineComponent, MarkAreaComponent,
  DataZoomComponent,
  // 渲染器：canvas（页面里 echarts.init(dom) 默认用法）
  CanvasRenderer
])

// 页面沿用了 `import * as echarts from '...'` + `echarts.init(...)` / `echarts.graphic.xxx` 的写法，
// 因此必须把 core 的具名导出（init / graphic / getInstanceByDom / dispose 等）re-export 出来，
// 否则 rollup 会按命名空间成员做静态校验并报 “xxx is not exported”。
export * from 'echarts/core'
export default echarts
