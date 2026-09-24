<template>
  <div class="wb-page qb-theme">
    <header class="page-heading">
      <div>
        <div class="eyebrow">ICU · CLINICAL WORKSPACE</div>
        <h1>患者工作台</h1>
        <p>汇总当前在科患者，作为进入各临床工具的统一入口。</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="loadPatients">刷新</el-button>
    </header>

    <el-alert class="notice" type="info" :closable="false" show-icon>
      首版仅汇总在科患者与工作入口，<strong>不自动判定高危或临床待办</strong>；姓名脱敏，住院号保留前后段供人工核对。
    </el-alert>

    <!-- 账号的科室边界：多科室要选、未绑定要说明，避免页面空着让人去猜是不是数据源坏了 -->
    <el-alert
      v-if="scope.message"
      class="notice"
      :type="scope.admin ? 'info' : (scope.matched ? 'success' : 'warning')"
      :closable="false"
      show-icon
      :title="scope.message"
    />
    <el-alert
      v-if="needPick"
      class="notice"
      type="warning"
      :closable="false"
      show-icon
      title="该账号有多个科室权限，请先选择科室后再查看患者。"
    />

    <div class="filter-bar wb-card">
      <div class="filter-fields">
        <div class="filter-field">
          <span class="filter-label">科室</span>
          <el-select v-model="departCode" clearable placeholder="全部科室" @change="onDepartChange">
            <el-option v-for="d in departments" :key="d.org_code" :label="d.depart_name" :value="d.org_code" />
          </el-select>
        </div>
        <div class="filter-field grow">
          <span class="filter-label">搜索</span>
          <el-input v-model="keyword" clearable placeholder="姓名 / 住院号 / 床位 / 病区" :prefix-icon="Search" />
        </div>
        <div class="filter-field">
          <span class="filter-label">排序</span>
          <el-select v-model="sortBy" placeholder="排序方式">
            <el-option label="入科时间：新到旧" value="newest" />
            <el-option label="入科时间：旧到新" value="oldest" />
            <el-option label="在科天数：长到短" value="stay" />
            <el-option label="按床位" value="bed" />
          </el-select>
        </div>
      </div>
    </div>

    <div class="wb-card overview">
      <div class="ov-head">
        <span>在科概览</span>
        <span class="ov-scope">{{ scopeText }} · 更新于 {{ updatedAt || '—' }}</span>
      </div>
      <div class="stat-grid">
        <div class="stat-card">
          <div class="stat-head"><span class="stat-label">在科患者</span><span class="stat-icon">👥</span></div>
          <div class="stat-value">{{ stats.total }}<em> 人</em></div>
          <div class="stat-foot">当前科室口径下的在科人数</div>
        </div>
        <div class="stat-card">
          <div class="stat-head"><span class="stat-label">危重患者</span><span class="stat-icon">🚨</span></div>
          <div class="stat-value danger">{{ stats.critical }}<em> 人</em></div>
          <div class="stat-foot">机械通气 / 血管活性药 / CRRT</div>
        </div>
        <div class="stat-card">
          <div class="stat-head"><span class="stat-label">今日待办</span><span class="stat-icon">📌</span></div>
          <div class="stat-value todo">{{ stats.todoCount }}<em> 项</em></div>
          <div class="stat-foot">未评 SOFA / APACHE II 等</div>
        </div>
        <div class="stat-card">
          <div class="stat-head"><span class="stat-label">平均在科天数</span><span class="stat-icon">📅</span></div>
          <div class="stat-value">{{ stats.avgDays }}<em> 天</em></div>
          <div class="stat-foot">仅统计有入科时间的患者</div>
        </div>
      </div>
    </div>

    <div class="wb-card table-card">
      <el-table v-loading="loading" :data="filteredPatients" stripe row-key="patientId"
                empty-text="当前科室口径下暂无在科患者" @row-click="openPatient">
        <el-table-column label="患者" min-width="200">
          <template #default="{ row }">
            <div class="patient-name">
              {{ row.name || '未知' }}
              <span v-if="row.lastSofaScore != null"
                    :class="['sofa-badge', row.lastSofaScore >= 10 ? 'severe' : '']">
                SOFA {{ row.lastSofaScore }}
              </span>
            </div>
            <div class="patient-sub">住院号 {{ row.patientNo || '—' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="性别 / 年龄" width="110">
          <template #default="{ row }">{{ row.gender || '—' }}<span class="muted"> · </span>{{ row.age == null ? '—' : `${row.age} 岁` }}</template>
        </el-table-column>
        <el-table-column label="科室 / 病区" min-width="150">
          <template #default="{ row }">
            <div>{{ deptLabel(row) }}</div>
            <div v-if="row.wardName && row.wardName !== deptLabel(row)" class="patient-sub">{{ row.wardName }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="bedNo" label="床位" width="90">
          <template #default="{ row }"><span class="bed-pill">{{ row.bedNo || '待分配' }}</span></template>
        </el-table-column>
        <el-table-column label="危重" width="110">
          <template #default="{ row }">
            <span v-if="row.ventilated" class="crit-tag vent">机械通气</span>
            <span v-if="row.onVasopressor" class="crit-tag vaso">血管活性药</span>
            <span v-if="row.onCrrt" class="crit-tag crrt">CRRT</span>
            <span v-if="!row.ventilated && !row.onVasopressor && !row.onCrrt" class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="待办" width="90" align="center">
          <template #default="{ row }">
            <el-tooltip v-if="row.todoCount > 0" :disabled="!row.todos || !row.todos.length" placement="top">
              <template #content>
                <div style="max-width:220px">
                  <div v-for="t in row.todos" :key="t">{{ todoLabel(t) }}</div>
                </div>
              </template>
              <span class="todo-badge">{{ row.todoCount }}</span>
            </el-tooltip>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="入科时间" min-width="150">
          <template #default="{ row }">{{ formatTime(row.inDepartmentTime) }}</template>
        </el-table-column>
        <el-table-column label="在科天数" width="90">
          <template #default="{ row }">{{ row.icuDays == null ? '—' : `${row.icuDays} 天` }}</template>
        </el-table-column>
        <el-table-column label="工作入口" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click.stop="goDecision(row)">抗感染</el-button>
            <el-button link type="primary" @click.stop="goSofa(row)">SOFA</el-button>
            <el-dropdown trigger="click" @command="(cmd) => jump(cmd, row)" @click.stop>
              <el-button link type="primary">更多<el-icon class="el-icon--right"><arrow-down /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="/page/apache2-score">APACHE II</el-dropdown-item>
                  <el-dropdown-item command="/page/sepsis-bundle">脓毒症集束化</el-dropdown-item>
                  <el-dropdown-item command="/page/ards-prone-record">ARDS 俯卧位</el-dropdown-item>
                  <el-dropdown-item command="/page/abx-pkpd">PK/PD 剂量</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="!loading && patients.length === 0" class="empty-foot">{{ emptyText }}</div>
      <div v-else-if="!loading && filteredPatients.length === 0" class="empty-foot">没有匹配的患者，试试清空搜索条件。</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Search, ArrowDown } from '@element-plus/icons-vue'
import { externalParam } from '../utils/external'
import '../styles/quality-theme.css'
import { fetchInpatients, fetchDepartScope } from '../api/workbench'
import { normalizeDepartParam, deptNameOf } from '../utils/depart'
import { setCurrentPatient, clearCurrentPatient } from '../utils/patientContext'

const router = useRouter()
const loading = ref(false)
const patients = ref([])
const departments = ref([])
// 科室边界由服务端判定（见 UserDepartScopeService），前端只负责呈现与让用户选：
//   admin   —— 名单内账号豁免限制，可看全部科室
//   matched —— 重症侧是否匹配到该账号；false 表示无科室，<b>不会</b>退回全院
//   departs —— 可选科室列表；多个时由用户自选，前端不替他猜
// departCode 必须是 org_code，不能是病区名 —— 见 utils/depart.js 的说明。
const departCode = ref(externalParam('departCode'))
// 多科室账号尚未选定科室：此时不查数据、也不退回全院，等用户在下拉里选
const needPick = ref(false)
const scope = ref({ admin: false, matched: false, departs: [], message: '' })
const keyword = ref('')
const sortBy = ref('newest')
const updatedAt = ref('')

const scopeText = computed(() => {
  if (needPick.value) return '待选择科室'
  return departCode.value ? `科室：${deptNameOf(departCode.value, departments.value)}` : '全院（未限定科室）'
})

const stats = computed(() => {
  const list = patients.value
  const withDays = list.filter((p) => p.icuDays != null)
  const avg = withDays.length
    ? Math.round(withDays.reduce((sum, p) => sum + p.icuDays, 0) / withDays.length)
    : 0
  const critical = list.filter((p) => p.ventilated || p.onVasopressor || p.onCrrt).length
  const todoCount = list.reduce((sum, p) => sum + (p.todoCount || 0), 0)
  return { total: list.length, critical, todoCount, avgDays: avg }
})

const TODO_LABELS = {
  SOFA_NOT_TODAY: '今日尚未评 SOFA',
  APACHE_NOT_TODAY: '今日尚未评 APACHE II'
}
function todoLabel(code) {
  return TODO_LABELS[code] || code
}

const filteredPatients = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  const list = patients.value.filter((p) => !q
    || [p.name, p.patientNo, p.bedNo, p.wardName, deptLabel(p)].some((v) => String(v || '').toLowerCase().includes(q)))
  return list.slice().sort((a, b) => {
    if (sortBy.value === 'oldest') return timeOf(a) - timeOf(b)
    if (sortBy.value === 'stay') return (b.icuDays || 0) - (a.icuDays || 0)
    if (sortBy.value === 'bed') {
      return String(a.bedNo || '').localeCompare(String(b.bedNo || ''), 'zh-CN', { numeric: true })
    }
    return timeOf(b) - timeOf(a)
  })
})

function deptLabel(row) {
  return deptNameOf(row.departCode, departments.value) || row.wardName || '未分配'
}

function timeOf(row) {
  const t = row.inDepartmentTime ? new Date(row.inDepartmentTime).getTime() : 0
  return Number.isNaN(t) ? 0 : t
}

function formatTime(value) {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 16)
}

/** 无数据时的说明：先把「没权限 / 没选科室」和「数据源没数据」区分开，别都推给数据源 */
const emptyText = computed(() => {
  if (needPick.value) return '该账号有多个科室权限，请先在上方选择科室。'
  if (scope.value.username && !scope.value.admin && !scope.value.matched) {
    return '当前账号未在重症系统绑定在用科室，请联系管理员配置科室权限。'
  }
  return '请检查 ICU 数据源配置，或切换科室后重试。'
})

/**
 * 先取账号的科室范围，再查患者 —— 顺序不能颠倒。
 *
 * 下拉里能选哪些科室、默认进哪个、以及能不能看全科，全都取决于这份范围。
 * 没拿到范围就发列表请求，会被服务端判成越权而报错，页面先闪一遍错误再恢复。
 */
async function initScope() {
  try {
    const s = await fetchDepartScope()
    scope.value = s || {}
    // 字典统一用服务端返回的那份：管理员和外链拿到全部在用科室，普通账号只拿被授权的科室。
    // 这样下拉里不会出现「选了也查不动」的科室。
    departments.value = Array.isArray(s?.departs) ? s.departs : []

    const ext = normalizeDepartParam(departCode.value, departments.value)
    if (ext !== departCode.value) {
      console.warn(`[工作台] 外链科室参数「${departCode.value}」不是有效编码，已按科室名称匹配为「${ext}」`)
      departCode.value = ext
    }

    if (s.admin) {
      // 管理员：留空即全院口径，下拉仍可切到具体科室
      needPick.value = false
    } else if (departments.value.length === 1) {
      departCode.value = departments.value[0].org_code
      needPick.value = false
    } else if (departments.value.length > 1) {
      // 多科室：不替用户猜，让他选
      departCode.value = ext || ''
      needPick.value = !departCode.value
    } else {
      // 无科室：matched=false，由模板提示并拦住请求，不退回全院
      departCode.value = ''
      needPick.value = false
    }
  } catch (e) {
    ElMessage.error(`科室范围加载失败：${e.message}`)
    departments.value = []
  }
}

async function loadPatients() {
  // 已登录的非管理员账号，还没选定科室时直接不发请求：
  // 服务端会把「留空」判为越权（也不会退回全院），这里主动拦下只是免得白报错一次。
  // 服务端那层校验必须保留 —— 前端拦不住直接调接口的情况。
  if (scope.value.username && !scope.value.admin && !departCode.value) {
    patients.value = []
    return
  }
  loading.value = true
  try {
    patients.value = await fetchInpatients(departCode.value)
    updatedAt.value = new Date().toLocaleString('zh-CN', { hour12: false })
  } catch (e) {
    patients.value = []
    ElMessage.error(`患者列表加载失败：${e.message}`)
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await initScope()
  loadPatients()
})

function openPatient(row) { goDecision(row) }
function goDecision(row) { jump('/page/abx-decision', row) }
function goSofa(row) { jump('/page/sofa-score', row) }

/**
 * 跳转一律带 patientId（patient_info.id，库内唯一）。
 * 各业务模块的关联列是 in_hospital_no，但那些表里的 patientId 与这里是同一套；
 * 用 id 比用住院号稳 —— 住院号可能对应多次入科，id 不会。
 */
function jump(path, row) {
  // 先写进全局上下文再跳：只带 URL 参数的话，从侧边栏切到别的菜单时参数全没了，
  // 那个页面读不到患者就空着 —— 正是「进其他页面就丢掉了」的原因。
  // 注入逻辑见 router/index.js 的 PATIENT_PAGES。
  setCurrentPatient(row)
  router.push({ path, query: { patientId: row.patientId } })
}

/**
 * 换了科室就放弃当前患者。
 *
 * 患者只属于一个科室，带着 A 科室的患者切到 B 科室，
 * 再进 SOFA / APACHE 那些页面看到的还是 A 科室那个人 —— 比丢掉更糟，
 * 因为它不报错，看着也像对的。
 */
function onDepartChange() {
  clearCurrentPatient()
  loadPatients()
}
</script>

<style scoped>
.wb-page { min-height: 100vh; max-width: 1440px; margin: 0 auto; padding: 30px 34px 48px; background: #faf8f5; color: #292524; }
.page-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 22px; }
.eyebrow { color: #c2410c; font-size: 11px; font-weight: 700; letter-spacing: 1.5px; }
h1 { margin: 7px 0 5px; font-size: 27px; letter-spacing: -.5px; }
.page-heading p { margin: 0; color: #78716c; font-size: 14px; }
.notice { margin-bottom: 18px; }

.wb-card { border: 1px solid #f0e0d0; border-radius: 12px; background: #fff; box-shadow: 0 1px 2px rgba(120, 53, 15, .04); }
.filter-bar { padding: 16px 18px; margin-bottom: 14px; }
.filter-fields { display: flex; gap: 14px; flex-wrap: wrap; }
.filter-field { display: flex; align-items: center; gap: 8px; }
.filter-field.grow { flex: 1 1 320px; }
.filter-field.grow :deep(.el-input) { width: 100%; }
.filter-label { color: #78716c; font-size: 12px; white-space: nowrap; }
.filter-field :deep(.el-select) { width: 178px; }

.overview { padding: 18px 20px 6px; margin-bottom: 14px; }
.ov-head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 14px; color: #1c1917; font-size: 15px; font-weight: 600; }
.ov-scope { color: #a8a29e; font-size: 12px; font-weight: 400; }
.stat-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.stat-card { padding: 14px 16px; border: 1px solid #f5e6d8; border-radius: 10px; background: #fffdfa; }
.stat-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.stat-label { color: #78716c; font-size: 12px; }
.stat-icon { font-size: 16px; opacity: .75; }
.stat-value { color: #1c1917; font-size: 24px; font-weight: 600; line-height: 1.2; }
.stat-value em { color: #a8a29e; font-size: 12px; font-style: normal; font-weight: 400; }
.stat-foot { margin-top: 6px; color: #a8a29e; font-size: 11px; }
.time-value { font-size: 16px !important; }

.table-card { padding: 6px 8px 12px; }
.patient-name { color: #292524; font-weight: 600; }
.patient-sub { margin-top: 3px; color: #a8a29e; font-size: 12px; }
.muted { color: #a8a29e; }
.bed-pill { display: inline-block; padding: 3px 8px; border-radius: 6px; background: #f5f5f4; color: #57534e; font-size: 12px; }
.empty-foot { padding: 16px; text-align: center; color: #a8a29e; font-size: 12px; }

/* 危重标签 */
.crit-tag { display: inline-block; margin: 1px 2px 1px 0; padding: 1px 6px; border-radius: 4px; font-size: 11px; line-height: 18px; white-space: nowrap; }
.crit-tag.vent { background: #dbeafe; color: #1d4ed8; }
.crit-tag.vaso { background: #fee2e2; color: #b91c1c; }
.crit-tag.crrt { background: #fef3c7; color: #92400e; }

/* 待办徽章 */
.todo-badge { display: inline-block; min-width: 20px; padding: 2px 6px; border-radius: 10px; background: #dc2626; color: #fff; font-size: 12px; font-weight: 600; text-align: center; cursor: help; }

/* 患者名旁的 SOFA 分 */
.sofa-badge { display: inline-block; margin-left: 6px; padding: 1px 6px; border-radius: 4px; background: #f5f5f4; color: #57534e; font-size: 11px; font-weight: 500; }
.sofa-badge.severe { background: #fee2e2; color: #b91c1c; font-weight: 700; }

/* 统计卡高亮 */
.stat-value.danger { color: #b91c1c; }
.stat-value.todo { color: #dc2626; }

@media (max-width: 900px) {
  .wb-page { padding: 20px 14px; }
  .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .filter-field :deep(.el-select) { width: 100%; }
  .filter-field { width: 100%; }
}
</style>
