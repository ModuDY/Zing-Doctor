<template>
  <div class="quality-patient qb-theme">
    <!-- 筛选栏 -->
    <div class="filter-bar qb-card">
      <div class="filter-left">
        <el-select
          v-model="departCode"
          placeholder="全院"
          style="width: 160px"
          clearable
          popper-class="qb-popper"
          @change="onDepartChange"
        >
          <el-option
            v-for="d in departments"
            :key="d.org_code"
            :label="d.depart_name"
            :value="d.org_code"
          />
        </el-select>

        <!--
          患者切换器：清单要求「在此模块界面快速切换不同患者查看」。
          下拉里同时列在科与（可选的）已出科患者，切换即重新取数，
          并配「上一个 / 下一个」—— 查房时是按床依次看，而不是回下拉里找名字。
        -->
        <el-select
          v-model="patientKey"
          filterable
          placeholder="选择患者"
          style="width: 250px"
          popper-class="qb-popper"
          :loading="loadingPatients"
        >
          <el-option
            v-for="p in patientOptions"
            :key="p.key"
            :label="p.label"
            :value="p.key"
          />
        </el-select>
        <el-button-group>
          <el-button :disabled="patientOptions.length < 2" @click="stepPatient(-1)">上一个</el-button>
          <el-button :disabled="patientOptions.length < 2" @click="stepPatient(1)">下一个</el-button>
        </el-button-group>

        <el-date-picker
          v-model="month"
          type="month"
          placeholder="选择月份"
          value-format="YYYY-MM"
          :clearable="false"
          style="width: 140px"
          popper-class="qb-popper"
          @change="doQuery"
        />
        <el-button type="primary" :loading="loading" @click="doQuery">
          <el-icon><Search /></el-icon> 查询
        </el-button>
      </div>

      <div class="filter-right">
        <el-checkbox v-model="historyMode" @change="loadPatients">含已出科患者</el-checkbox>
        <el-input
          v-model="keyword"
          placeholder="姓名 / 住院号"
          clearable
          style="width: 180px"
          @keyup.enter="loadPatients"
        >
          <template #append>
            <el-button @click="loadPatients"><el-icon><Search /></el-icon></el-button>
          </template>
        </el-input>
        <el-button :disabled="!metrics.length" @click="doExport">
          <el-icon><Download /></el-icon> 导出 Excel
        </el-button>
        <el-button :disabled="!metrics.length" @click="doPrint">
          <el-icon><Printer /></el-icon> 打印
        </el-button>
      </div>
    </div>

    <!-- 患者信息条 -->
    <div v-if="result" class="patient-bar qb-card">
      <span class="pb-name">{{ result.patientName || '未知患者' }}</span>
      <span class="pb-item">住院号 {{ result.inHospitalNo || '—' }}</span>
      <span class="pb-item">期间 {{ result.periodStart || '—' }} ~ {{ result.periodEnd || '—' }}</span>
      <span class="pb-item">命中 <b>{{ result.hitCount }}</b> 项</span>
      <span v-if="result.skippedCount" class="pb-item muted">
        另有 {{ result.skippedCount }} 项不产生患者明细
      </span>
      <span v-if="result.message" class="pb-item muted">{{ result.message }}</span>
    </div>

    <!-- 命中指标 -->
    <div class="table-card qb-card">
      <div class="block-title">
        <span class="dot"></span>
        <span>本月命中的质控指标</span>
        <span class="title-note">
          「已达标」= 计入分子；「未达标」= 只计入分母；「未纳入」= 分子分母都没进
          —— 这三档互斥且并集为全部命中项，人数可直接相加对账
        </span>
      </div>
      <el-table
        v-loading="loading"
        :data="metrics"
        border
        size="small"
        max-height="560"
        empty-text="暂无命中记录（可切换患者或月份）"
      >
        <el-table-column prop="name" label="指标名称" min-width="300" show-overflow-tooltip />
        <el-table-column prop="domain" label="所属域" width="150" />
        <el-table-column label="纳入情况" width="104" align="center">
          <template #default="{ row }">
            <el-tag :type="stateTag(row.inclusionState).type" size="small" effect="light">
              {{ stateTag(row.inclusionState).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="76" align="center" />
        <el-table-column label="患者相关明细" min-width="280">
          <template #default="{ row }">
            <span class="extra-text">{{ extraText(row) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 不产生明细的指标：说清「为什么有些项没出现」，否则看着像漏算了 -->
    <div v-if="skipped.length" class="table-card qb-card">
      <div class="block-title">
        <span class="dot"></span>
        <span>不产生患者明细的指标（{{ skipped.length }} 项）</span>
        <el-button
          link
          type="primary"
          size="small"
          class="skip-toggle"
          @click="showSkipped = !showSkipped"
        >
          {{ showSkipped ? '收起' : '展开' }}
        </el-button>
      </div>
      <el-table v-if="showSkipped" :data="skipped" border size="small" max-height="360">
        <el-table-column prop="code" label="指标编号" width="120" />
        <el-table-column prop="name" label="指标名称" min-width="280" />
        <el-table-column prop="domain" label="所属域" width="150" />
        <el-table-column prop="reason" label="无明细原因" min-width="240" />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Download, Printer } from '@element-plus/icons-vue'
import { externalParam } from '../utils/external'
import { currentDepart } from '../utils/departContext'
import '../styles/quality-theme.css'
import * as XLSX from 'xlsx'
import { fetchQualityDepartments, fetchQualityPatientMetrics } from '../api/quality'
import { fetchInpatients, fetchDischargedPatients } from '../api/workbench'

/** 纳入状态 → 文案与配色。取值与后端 inclusionState 一一对应（互斥且并集完整） */
const INCLUSION = {
  achieved: { label: '已达标', type: 'success' },
  missed: { label: '未达标', type: 'danger' },
  excluded: { label: '未纳入', type: 'info' },
  abnormal: { label: '口径异常', type: 'warning' }
}

/** 明细字段的中文名：事实层补充列由配置给出，常见列在这里翻成临床看得懂的字样 */
const EXTRA_LABEL = {
  bed_no: '床号',
  bedno: '床号',
  in_time: '入科时间',
  out_time: '出科时间',
  stay_days: '床日数',
  diagnosis: '诊断',
  depart_code: '科室',
  in_hospital_no: '住院号',
  patient_name: '姓名',
  tube_days: '留置天数',
  vent_days: '通气天数'
}

/** 后端返回的基础列，其余键都视为「患者相关明细」 */
const BASE_KEYS = new Set([
  'code', 'name', 'domain', 'unit', 'inNumerator', 'inDenominator', 'inclusionState'
])

function lastMonth() {
  const d = new Date()
  d.setDate(1)
  d.setMonth(d.getMonth() - 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

const extDepartCode = externalParam('departCode')
const departCode = ref(extDepartCode || currentDepart.departCode)
const departments = ref([])

const month = ref(lastMonth())
const patientKey = ref('')
const keyword = ref('')
const historyMode = ref(false)
const loadingPatients = ref(false)
const loading = ref(false)
const result = ref(null)
const showSkipped = ref(false)

const inpatients = ref([])
const histories = ref([])

/**
 * 患者下拉项：住院号优先作为查询键。
 *
 * 同一患者多次入科时 patientId 会变（每次入科都是一条新记录），
 * 只带 patientId 会让「上个月的数据」查不到人；住院号才是患者维度的稳定键，
 * 因此选项的 value 里两个都带上，查询时优先用住院号。
 */
const patientOptions = computed(() => {
  const list = historyMode.value ? [...inpatients.value, ...histories.value] : inpatients.value
  const seen = new Set()
  const out = []
  for (const p of list) {
    const no = p.inHospitalNo || p.in_hospital_no || ''
    const id = p.patientId || p.patient_id || ''
    if (!no && !id) continue
    const key = `${no}||${id}`
    if (seen.has(key)) continue
    seen.add(key)
    const name = p.patientName || p.patient_name || p.name || '未知'
    const bed = p.bedNo || p.bed_no || ''
    out.push({
      key,
      inHospitalNo: no,
      patientId: id,
      label: `${bed ? bed + ' · ' : ''}${name}${no ? ' · ' + no : ''}`
    })
  }
  return out
})

const currentPatient = computed(
  () => patientOptions.value.find((p) => p.key === patientKey.value) || null
)

const metrics = computed(() => (result.value && result.value.metrics) || [])
const skipped = computed(() => (result.value && result.value.skipped) || [])

function stateTag(state) {
  return INCLUSION[state] || { label: state || '—', type: 'info' }
}

function extraText(row) {
  const parts = []
  for (const k of Object.keys(row || {})) {
    if (BASE_KEYS.has(k)) continue
    const v = row[k]
    if (v === null || v === undefined || v === '') continue
    const label = EXTRA_LABEL[String(k).toLowerCase()] || k
    parts.push(`${label} ${v}`)
  }
  return parts.length ? parts.join(' · ') : '—'
}

async function loadDepartments() {
  try {
    const res = await fetchQualityDepartments()
    departments.value = Array.isArray(res) ? res : []
  } catch (e) {
    console.warn('科室列表加载失败:', e.message || e)
  }
}

async function loadPatients() {
  loadingPatients.value = true
  try {
    const dept = departCode.value || ''
    const [cur, his] = await Promise.all([
      fetchInpatients(dept).catch(() => []),
      historyMode.value
        ? fetchDischargedPatients(dept, keyword.value || '', 300).catch(() => [])
        : Promise.resolve([])
    ])
    inpatients.value = Array.isArray(cur) ? cur : []
    histories.value = Array.isArray(his) ? his : []
    // 选中项在新列表里不存在时清掉：否则页面显示着「已选患者」而查询用的是旧对象
    if (patientKey.value && !patientOptions.value.some((p) => p.key === patientKey.value)) {
      patientKey.value = ''
    }
    if (!patientKey.value && patientOptions.value.length) {
      patientKey.value = patientOptions.value[0].key
    }
  } catch (e) {
    console.warn('患者列表加载失败:', e.message || e)
  } finally {
    loadingPatients.value = false
  }
}

function onDepartChange() {
  patientKey.value = ''
  result.value = null
  loadPatients()
}

/** 上一个 / 下一个：查房是按床依次看，不该回下拉里找人 */
function stepPatient(step) {
  const list = patientOptions.value
  if (list.length < 2) return
  const idx = list.findIndex((p) => p.key === patientKey.value)
  if (idx < 0) {
    patientKey.value = list[0].key
    return
  }
  const next = (idx + step + list.length) % list.length
  patientKey.value = list[next].key
}

async function doQuery() {
  const p = currentPatient.value
  if (!p) {
    ElMessage.warning('请先选择患者')
    return
  }
  loading.value = true
  try {
    const res = await fetchQualityPatientMetrics({
      inHospitalNo: p.inHospitalNo || '',
      patientId: p.patientId || '',
      periodStart: month.value,
      departCode: departCode.value || ''
    })
    result.value = res || null
    if (res && !res.hitCount && !res.skippedCount) {
      ElMessage.info('该患者本月未命中任何质控项')
    }
  } catch (e) {
    // 首次查询要逐条指标构建/读取事实层，慢是正常的；提示里说清楚，
    // 否则使用者会当成页面坏了，反复点击反而把服务端压得更慢
    ElMessage.error('查询失败：' + (e.message || e) + '（首次查询较慢，可稍后重试）')
  } finally {
    loading.value = false
  }
}

function fileBase() {
  const p = currentPatient.value || {}
  return `患者月度质控指标_${p.inHospitalNo || p.patientId || 'unknown'}_${month.value}`
}

function doExport() {
  if (!metrics.value.length) return
  try {
    const head = ['指标名称', '所属域', '纳入情况', '单位', '患者相关明细']
    const rows = metrics.value.map((r) => [
      r.name,
      r.domain || '',
      stateTag(r.inclusionState).label,
      r.unit || '',
      extraText(r)
    ])
    const title = [
      ['患者月度质控指标'],
      ['患者', result.value.patientName || ''],
      ['住院号', result.value.inHospitalNo || ''],
      ['期间', `${result.value.periodStart || ''} ~ ${result.value.periodEnd || ''}`],
      []
    ]
    const ws = XLSX.utils.aoa_to_sheet([...title, head, ...rows])
    ws['!cols'] = [{ wch: 44 }, { wch: 16 }, { wch: 12 }, { wch: 10 }, { wch: 52 }]
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '患者质控指标')
    XLSX.writeFile(wb, fileBase() + '.xlsx')
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败：' + (e.message || e))
  }
}

function esc(s) {
  return String(s == null ? '' : s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

/** 打印：新窗口里排一份 A4 纵向的档案表，与页面同数据、同顺序 */
function doPrint() {
  if (!metrics.value.length) return
  const w = window.open('', '_blank')
  if (!w) {
    ElMessage.warning('浏览器拦截了打印窗口，请允许本站弹窗后重试')
    return
  }
  const p = currentPatient.value || {}
  const rowsHtml = metrics.value
    .map(
      (r) =>
        `<tr><td>${esc(r.name)}</td><td>${esc(r.domain || '')}</td>` +
        `<td>${esc(stateTag(r.inclusionState).label)}</td>` +
        `<td>${esc(r.unit || '')}</td><td>${esc(extraText(r))}</td></tr>`
    )
    .join('')
  w.document.write(
    `<!DOCTYPE html><html><head><meta charset="utf-8"><title>${esc(fileBase())}</title>
    <style>
      body{font-family:'Microsoft YaHei','PingFang SC',sans-serif;color:#1c1917;padding:14px}
      h1{font-size:17px;text-align:center;margin:0 0 6px}
      .sub{text-align:center;font-size:12px;color:#78716c;margin-bottom:12px}
      table{border-collapse:collapse;width:100%;font-size:12px;table-layout:fixed}
      th,td{border:1px solid #d6d3d1;padding:5px 7px;text-align:left;word-break:break-all}
      th{background:#f5f5f4;text-align:center}
      @page{size:A4 portrait;margin:12mm}
    </style></head><body>
    <h1>患者月度质控指标</h1>
    <div class="sub">${esc(result.value.patientName || '')} · 住院号 ${esc(result.value.inHospitalNo || '—')}
      · 期间 ${esc(result.value.periodStart || '')} ~ ${esc(result.value.periodEnd || '')}
      · 命中 ${metrics.value.length} 项${p.patientId ? '' : ''}</div>
    <table><thead><tr><th style="width:26%">指标名称</th><th style="width:12%">所属域</th>
      <th style="width:9%">纳入情况</th><th style="width:7%">单位</th><th>患者相关明细</th></tr></thead>
      <tbody>${rowsHtml}</tbody></table>
    </body></html>`
  )
  w.document.close()
  w.focus()
  setTimeout(() => w.print(), 400)
}

// 切患者 / 切月份即重新取数：清单要求「快速切换患者」「切换月份纵向对比」，
// 要的是一步到位，不该每次再点一次「查询」
watch([patientKey, month], () => {
  if (patientKey.value) doQuery()
})

onMounted(async () => {
  await loadDepartments()
  await loadPatients()
  // 外链可直接带住院号进来（如从工作台某患者的「质控」入口跳转）
  const no = externalParam('inHospitalNo')
  if (no) {
    const hit = patientOptions.value.find((p) => p.inHospitalNo === no)
    if (hit) patientKey.value = hit.key
  }
})
</script>

<style scoped>
.quality-patient {
  padding: 24px;
  background: #fafaf9;
  min-height: 100vh;
}

.qb-card {
  background: #ffffff;
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(28, 25, 23, 0.04);
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 18px;
  padding: 14px 18px;
}

.filter-left,
.filter-right {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.patient-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  padding: 14px 18px;
  margin-bottom: 18px;
}

.pb-name {
  font-size: 16px;
  font-weight: 700;
  color: #1c1917;
}

.pb-item {
  font-size: 13px;
  color: #57534e;
}

.pb-item.muted {
  color: #a8a29e;
}

.table-card {
  padding: 18px 20px;
  margin-bottom: 18px;
}

.block-title {
  display: flex;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
  color: #44403c;
  margin-bottom: 12px;
}

.dot {
  width: 4px;
  height: 16px;
  background: linear-gradient(180deg, #f97316, #fb923c);
  border-radius: 2px;
  margin-right: 8px;
  flex-shrink: 0;
}

.title-note {
  font-size: 12px;
  font-weight: 400;
  color: #a8a29e;
  margin-left: 10px;
}

.skip-toggle {
  margin-left: auto;
}

.extra-text {
  color: #57534e;
  font-size: 12px;
}

@media (max-width: 640px) {
  .quality-patient {
    padding: 12px;
  }
}
</style>
