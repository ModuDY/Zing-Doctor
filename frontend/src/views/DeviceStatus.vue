<template>
  <div class="device-page qb-theme">
    <section class="page-header">
      <div>
        <h1>设备状态</h1>
        <p class="sub">科室医疗设备使用/分布与采集连接状态 · 只读，数据来自重症系统设备配置表</p>
      </div>
      <div class="header-right">
        <el-radio-group v-model="typeFilter" size="small">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button v-for="t in typeOptions" :key="t.value" :label="t.value">{{ t.label }}</el-radio-button>
        </el-radio-group>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </section>

    <div v-loading="loading" class="page-body">
      <!-- 汇总：一眼看资源态势 -->
      <div class="summary-row">
        <div class="sum-card"><b>{{ rows.length }}</b><span>配置设备</span></div>
        <div class="sum-card"><b class="c-blue">{{ countUse }}</b><span>使用中（绑定在科患者）</span></div>
        <div class="sum-card"><b>{{ countIdle }}</b><span>未绑定</span></div>
        <div class="sum-card"><b class="c-green">{{ countReceiving }}</b><span>数据正在接收</span></div>
        <div class="sum-card"><b class="c-red">{{ countBad }}</b><span>停止/异常/断开</span></div>
        <div class="sum-card"><b class="c-gray">{{ countUnknown }}</b><span>未接入采集</span></div>
      </div>

      <el-table :data="filtered" size="small" border stripe>
        <el-table-column prop="device_code" label="设备编码" width="150" />
        <el-table-column label="类型" width="110">
          <template #default="{ row }">
            <span class="type-tag">{{ typeLabel(row.device_type) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="depart_code" label="科室" width="110" />
        <el-table-column label="使用状态" width="110" align="center">
          <template #default="{ row }">
            <span :class="row.bound_patient ? 'tag-in' : 'tag-idle'">{{ row.bound_patient ? '使用中' : '未绑定' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="当前绑定患者" min-width="150">
          <template #default="{ row }">{{ row.bound_patient || '—' }}</template>
        </el-table-column>
        <el-table-column label="采集状态" width="140">
          <template #default="{ row }">
            <span :class="['st-tag', statusClass(row.device_status)]">{{ statusText(row.device_status) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="最近更新" width="150">
          <template #default="{ row }">{{ fmt(row.latest_update) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !filtered.length" description="当前科室没有有效设备配置" />
      <p class="foot-note">
        采集连接状态（数据正在接收 / 端口已连接 / 连接初始化 / 停止 / 异常）仅代表<b>数据采集链路</b>；
        重症系统无设备维护台账，本页不展示维修、库存或临床可用性状态。
        绑定关系 = 在科患者的监护仪/呼吸机/CRRT/ECMO 设备编码与设备配置精确匹配。
      </p>
    </div>
  </div>
</template>

<script>
import request from '../api/request'

const TYPE_LABELS = { monitor: '监护仪', ventilator: '呼吸机', crrtDevice: 'CRRT', ecmoDevice: 'ECMO' }

function typeLabel(t) {
  if (!t) return '未分类'
  return TYPE_LABELS[t] || t
}

/** 与驾驶舱同一状态分档，两处颜色/文案必须一致 */
function statusText(s) {
  const v = String(s || '').trim()
  if (!v || v === '未接入采集套件') return '未接入采集'
  return v
}
function statusClass(s) {
  const v = String(s || '').trim()
  if (v === '数据正在接收') return 'st-ok'
  if (v === '端口已连接') return 'st-info'
  if (v === '连接初始化') return 'st-warn'
  if (v.includes('异常') || v.includes('断开') || v === '数据停止接收') return 'st-bad'
  return 'st-none'
}

export default {
  name: 'DeviceStatusPage',
  data() {
    return {
      loading: false,
      typeFilter: '',
      rows: []
    }
  },
  computed: {
    typeOptions() {
      const map = new Map()
      for (const r of this.rows) {
        const v = r.device_type || ''
        if (!map.has(v)) map.set(v, { value: v, label: typeLabel(v) })
      }
      return [...map.values()]
    },
    filtered() {
      if (!this.typeFilter) return this.rows
      return this.rows.filter((r) => (r.device_type || '') === this.typeFilter)
    },
    countUse() { return this.rows.filter((r) => r.bound_patient).length },
    countIdle() { return this.rows.length - this.countUse },
    countReceiving() { return this.rows.filter((r) => String(r.device_status || '').trim() === '数据正在接收').length },
    countBad() {
      return this.rows.filter((r) => {
        const v = String(r.device_status || '').trim()
        return v.includes('异常') || v.includes('断开') || v === '数据停止接收'
      }).length
    },
    countUnknown() {
      return this.rows.filter((r) => {
        const v = String(r.device_status || '').trim()
        return !v || v === '未接入采集套件'
      }).length
    }
  },
  created() { this.load() },
  methods: {
    typeLabel,
    statusText,
    statusClass,
    async load() {
      this.loading = true
      try {
        const list = await request.get('/department-cockpit/device-status', {
          params: { departCode: this.$route.query.departCode || '' },
          silentError: true
        })
        this.rows = Array.isArray(list) ? list : []
      } catch (e) {
        console.error('[DeviceStatus]', e)
        this.rows = []
      } finally {
        this.loading = false
      }
    },
    fmt(t) { return (t || '').replace('T', ' ').slice(0, 16) }
  }
}
</script>

<style scoped>
.device-page { padding: 16px; }
.page-header { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 14px; gap: 12px; flex-wrap: wrap; }
.page-header h1 { margin: 0; font-size: 18px; font-weight: 700; color: #1c1917; }
.sub { margin: 4px 0 0; color: #78716c; font-size: 12px; }
.header-right { display: flex; align-items: center; gap: 10px; }
.summary-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 1px; background: #f0efee; border: 1px solid #e7e5e4; border-radius: 12px; overflow: hidden; margin-bottom: 14px; }
.sum-card { background: #fff; padding: 12px 16px; }
.sum-card b { display: block; font-size: 24px; font-weight: 700; color: #1c1917; font-variant-numeric: tabular-nums; }
.sum-card b.c-blue { color: #2563eb; }
.sum-card b.c-green { color: #15803d; }
.sum-card b.c-red { color: #dc2626; }
.sum-card b.c-gray { color: #a8a29e; }
.sum-card span { color: #78716c; font-size: 12px; }
.type-tag { font-size: 11px; padding: 1px 8px; border-radius: 10px; background: #eff6ff; color: #2563eb; }
.tag-in { font-size: 11px; padding: 1px 8px; border-radius: 10px; background: #f0f9f3; color: #15803d; }
.tag-idle { font-size: 11px; padding: 1px 8px; border-radius: 10px; background: #f5f5f4; color: #78716c; }
.st-tag { font-size: 11px; padding: 1px 8px; border-radius: 10px; }
.st-ok { background: #f0f9f3; color: #15803d; }
.st-info { background: #eff6ff; color: #2563eb; }
.st-warn { background: #fffbeb; color: #b45309; }
.st-bad { background: #fef2f2; color: #dc2626; }
.st-none { background: #f5f5f4; color: #a8a29e; }
.foot-note { color: #a8a29e; font-size: 12px; margin: 10px 2px 0; }
.foot-note b { color: #78716c; }
</style>
