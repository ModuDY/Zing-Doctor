<template>
  <div class="tube-stats">
    <div class="filter-bar">
      <el-date-picker
        v-model="month"
        type="month"
        placeholder="选择月份"
        value-format="YYYY-MM"
        :clearable="false"
        popper-class="qb-popper"
        @change="load"
      />
      <span class="hint">置管/拔管/带出按事件发生月份统计；留置日未拔者算到患者出科时间</span>
    </div>

    <el-alert
      v-if="data.infectionNote"
      :title="data.infectionNote"
      type="info"
      :closable="false"
      style="margin-bottom: 12px"
    />

    <!-- 三管汇总 -->
    <div class="group-grid">
      <div v-for="g in data.groups" :key="g.group" class="group-card" :class="groupClass(g.group)">
        <div class="group-title">{{ groupLabel(g.group) }}</div>
        <div class="group-stats">
          <div class="stat"><label>置管</label><b>{{ g.placed }}</b></div>
          <div class="stat"><label>带入</label><b>{{ g.broughtIn }}</b></div>
          <div class="stat"><label>本科置管</label><b>{{ g.selfPlaced }}</b></div>
          <div class="stat"><label>拔管</label><b>{{ g.removed }}</b></div>
          <div class="stat"><label>转出带出</label><b>{{ g.takenAway }}</b></div>
          <div class="stat danger"><label>非计划拔管</label><b>{{ g.unplannedRemoval }}</b></div>
          <div class="stat"><label>48h再插管</label><b>{{ g.reintubation24h }}</b></div>
          <div class="stat"><label>留置日合计</label><b>{{ g.totalDays }}</b></div>
        </div>
      </div>
    </div>

    <!-- 明细 -->
    <el-table :data="data.rows" size="small" border stripe style="margin-top:16px">
      <el-table-column prop="tubeName" label="导管类型" min-width="140" />
      <el-table-column label="归类" width="90">
        <template #default="{ row }">
          <span class="g-tag" :class="groupClass(row.group)">{{ groupLabel(row.group) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="placed" label="置管" width="70" />
      <el-table-column prop="broughtIn" label="带入" width="70" />
      <el-table-column prop="selfPlaced" label="本科置管" width="80" />
      <el-table-column prop="removed" label="拔管" width="70" />
      <el-table-column prop="takenAway" label="转出带出" width="90" />
      <el-table-column label="非计划拔管" width="90">
        <template #default="{ row }">
          <span :style="{ color: row.unplannedRemoval > 0 ? '#dc2626' : 'inherit', fontWeight: row.unplannedRemoval > 0 ? 700 : 400 }">{{ row.unplannedRemoval }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="reintubation24h" label="48h再插管" width="90" />
      <el-table-column label="平均留置日" width="90">
        <template #default="{ row }">
          {{ row.avgDays == null ? '—' : row.avgDays + '天' }}
        </template>
      </el-table-column>
      <el-table-column label="留置覆盖率" width="100">
        <template #default="{ row }">
          <span :title="'有明确拔管/出科上界的比例'">{{ row.daysCoverage }}%</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import request from '../api/request'

const month = ref(new Date().toISOString().slice(0, 7))
const data = reactive({ groups: [], rows: [], infectionNote: '' })

function groupLabel(g) {
  return { CVC: '中心静脉导管(CVC)', 导尿管: '导尿管', 气道导管: '气道导管(插管/气切)', 其他: '其他导管' }[g] || g
}
function groupClass(g) {
  return { CVC: 'cvc', 导尿管: 'urine', 气道导管: 'airway' }[g] || ''
}

async function load() {
  try {
    const res = await request.get('/tube/monthly', { params: { yearMonth: month.value } })
    // 兼容两种信封：统一 request 已解包（直接是业务对象），但若上层改动或
    // 换用裸 axios 会拿到 {code,message,data}。整包 Object.assign 的后果是
    // data.groups 永远为空 —— 页面「暂无数据」而接口明明返回了 4 组 30 行。
    Object.assign(data, res && res.groups ? res : (res && res.data) || {})
  } catch (e) {
    console.error(e)
  }
}
onMounted(load)
</script>

<style scoped>
.tube-stats { padding: 16px; }
.filter-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.hint { color: #78716c; font-size: 12px; }
.group-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.group-card { border: 1px solid #e7e5e4; border-radius: 10px; padding: 12px 14px; background: #fafaf9; }
.group-card.cvc { border-top: 3px solid #2563eb; }
.group-card.urine { border-top: 3px solid #059669; }
.group-card.airway { border-top: 3px solid #dc2626; }
.group-title { font-weight: 700; font-size: 14px; color: #1c1917; margin-bottom: 10px; }
.group-stats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
.stat label { display: block; font-size: 11px; color: #78716c; }
.stat b { font-size: 18px; color: #1c1917; font-variant-numeric: tabular-nums; }
.stat.danger b { color: #dc2626; }
.g-tag { font-size: 11px; padding: 1px 6px; border-radius: 4px; background: #f1f5f9; color: #475569; }
@media (max-width: 1200px) { .group-grid { grid-template-columns: 1fr; } }
</style>
