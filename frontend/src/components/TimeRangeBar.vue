<template>
  <div class="trb">
    <el-radio-group :model-value="preset" size="small" @change="onPreset">
      <el-radio-button v-for="p in presetList" :key="p.value" :label="p.value">{{ p.label }}</el-radio-button>
    </el-radio-group>
    <el-date-picker
      v-model="range"
      :type="granule === 'date' ? 'daterange' : 'datetimerange'"
      size="small"
      class="trb-picker"
      range-separator="至"
      start-placeholder="自定义开始"
      end-placeholder="自定义结束"
      :value-format="granule === 'date' ? 'YYYY-MM-DD' : 'YYYY-MM-DD HH:mm:ss'"
      :default-time="granule === 'date' ? undefined : defaultTime"
      :clearable="true"
      @change="onCustom"
    />
  </div>
</template>

<script setup>
/**
 * 统一「时间区间」筛选条：快捷档（近N小时 / 本月 / 本年…）+ 自定义起止，二选一互斥。
 *
 * 取代此前各页复制的「radio-group 快捷档 + datetimerange + 30 天上限校验」三件套
 * （SedationMonitor / NutritionMonitor 原各自维护一份，行为漂移是常态）。
 *
 * @props
 *  - granule    'datetime'（默认，精确到分）| 'date'（日期级）
 *  - presets    [{ value, label, hours?, from? }]：hours=回溯小时数；from='month'|'year'=自然起点
 *  - defaultPreset 初始选中档（仅展示态，组件不在挂载时发 change，避免与页面 created 的首次加载重复）
 *  - maxSpanDays 自定义区间上限（默认 30 天，0=不限）
 * @emits change { preset, hours, start, end }
 *   - 点快捷档：{ preset:'24h', hours:24, start:回溯起点, end:now }（'YYYY-MM-DD HH:mm:ss'）
 *   - 选自定义：{ preset:'', hours:null, start:'YYYY-MM-DD HH:mm:ss', end:'…' }
 *   统一空格分隔格式；后端要 ISO 'T' 的（sedation/nutrition）由调用方 replace(' ', 'T')。
 */
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps({
  granule: { type: String, default: 'datetime' },
  presets: {
    type: Array,
    default: () => ([
      { value: '24h', label: '近24小时', hours: 24 },
      { value: '72h', label: '近3天', hours: 72 },
      { value: '7d', label: '近7天', hours: 168 },
      { value: '30d', label: '近30天', hours: 720 }
    ])
  },
  defaultPreset: { type: String, default: '' },
  maxSpanDays: { type: Number, default: 30 }
})

const emit = defineEmits(['change'])

const preset = ref(props.defaultPreset)
const range = ref(null)
const defaultTime = [new Date(2000, 0, 1, 0, 0, 0), new Date(2000, 0, 1, 23, 59, 59)]

const fmt = (d) => {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function presetStart(p, now) {
  if (p.from === 'month') return new Date(now.getFullYear(), now.getMonth(), 1)
  if (p.from === 'year') return new Date(now.getFullYear(), 0, 1)
  return new Date(now.getTime() - (p.hours || 24) * 3600 * 1000)
}

function onPreset(v) {
  const p = props.presets.find((x) => x.value === v)
  if (!p) return
  preset.value = v
  range.value = null
  const now = new Date()
  emit('change', { preset: p.value, hours: p.hours ?? null, start: fmt(presetStart(p, now)), end: fmt(now) })
}

function onCustom(v) {
  if (!v || !v[0] || !v[1]) {
    // 清空自定义 → 回落到快捷档（保持页面始终有窗口），无档位则发空窗口
    if (preset.value) { onPreset(preset.value); return }
    range.value = null
    emit('change', { preset: '', hours: null, start: null, end: null })
    return
  }
  const s = props.granule === 'date' ? v[0] + ' 00:00:00' : v[0]
  const e = props.granule === 'date' ? v[1] + ' 23:59:59' : v[1]
  const spanMs = new Date(e.replace(/-/g, '/')).getTime() - new Date(s.replace(/-/g, '/')).getTime()
  if (spanMs <= 0) {
    ElMessage.warning('结束时间必须晚于开始时间')
    range.value = null
    return
  }
  if (props.maxSpanDays > 0 && spanMs > props.maxSpanDays * 86400 * 1000) {
    ElMessage.warning(`自定义时间范围不能超过 ${props.maxSpanDays} 天`)
    range.value = null
    return
  }
  preset.value = ''
  emit('change', { preset: '', hours: null, start: s, end: e })
}
</script>

<style scoped>
.trb { display: inline-flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.trb-picker { width: 340px; }
</style>
