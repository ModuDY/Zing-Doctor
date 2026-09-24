<template>
  <div class="wb-page qb-theme">
    <header class="wb-hero">
      <div class="hero-copy">
        <div class="eyebrow">ICU · CLINICAL WORKSPACE</div>
        <h1>患者工作台</h1>
        <p>以患者为中心的 ICU 临床入口，集中查看在科状态、评分待办和常用决策工具。</p>
        <div class="hero-meta">
          <span class="meta-pill"><i class="meta-dot active"></i>{{ scopeText }}</span>
          <span class="meta-pill">数据更新于 {{ updatedAt || '—' }}</span>
        </div>
      </div>
      <div class="hero-actions">
        <el-button class="refresh-btn" :icon="Refresh" :loading="loading" @click="loadPatients">刷新数据</el-button>
      </div>
    </header>

    <el-alert class="wb-notice" type="info" :closable="false" show-icon>
      危重标签与评分待办由系统自动汇总；点击患者行可进入抗感染决策，更多入口可展开查看。
    </el-alert>

    <el-alert
      v-if="scope.message"
      class="wb-notice"
      :type="scope.admin ? 'info' : (scope.matched ? 'success' : 'warning')"
      :closable="false"
      show-icon
      :title="scope.message"
    />
    <el-alert
      v-if="needPick"
      class="wb-notice"
      type="warning"
      :closable="false"
      show-icon
      title="该账号有多个科室权限，请先选择科室后再查看患者。"
    />

    <section class="wb-card filter-card">
      <div class="section-heading filter-heading">
        <div>
          <div class="section-kicker">PATIENT DIRECTORY</div>
          <h2>患者检索</h2>
        </div>
        <span class="section-hint">支持姓名、住院号、床位和病区搜索</span>
      </div>
      <div class="filter-fields">
        <div class="filter-field">
          <span class="filter-label">科室范围</span>
          <el-select v-model="departCode" clearable placeholder="全部科室" popper-class="qb-popper" @change="onDepartChange">
            <el-option v-for="d in departments" :key="d.org_code" :label="d.depart_name" :value="d.org_code" />
          </el-select>
        </div>
        <div class="filter-field search-field">
          <span class="filter-label">快速搜索</span>
          <el-input v-model="keyword" clearable placeholder="姓名 / 住院号 / 床位 / 病区" :prefix-icon="Search" />
        </div>
        <div class="filter-field">
          <span class="filter-label">排序方式</span>
          <el-select v-model="sortBy" placeholder="排序方式" popper-class="qb-popper">
            <el-option label="入科时间：新到旧" value="newest" />
            <el-option label="入科时间：旧到新" value="oldest" />
            <el-option label="在科天数：长到短" value="stay" />
            <el-option label="按床位" value="bed" />
          </el-select>
        </div>
      </div>
    </section>

    <section class="wb-card overview-card">
      <div class="section-heading">
        <div>
          <div class="section-kicker">CURRENT CENSUS</div>
          <h2>在科概览</h2>
        </div>
        <div class="overview-tags">
          <span class="summary-tag"><i class="tag-dot orange"></i>当前筛选 {{ filteredPatients.length }} 人</span>
          <span class="summary-tag muted-tag">共 {{ patients.length }} 人</span>
        </div>
      </div>
      <div class="stat-grid">
        <div class="stat-card stat-card-primary">
          <div class="stat-head"><span class="stat-label">在科患者</span><span class="stat-icon orange-icon">人</span></div>
          <div class="stat-value">{{ stats.total }}<em> 人</em></div>
          <div class="stat-foot">当前科室口径下的在科人数</div>
        </div>
        <div class="stat-card stat-card-danger">
          <div class="stat-head"><span class="stat-label">危重患者</span><span class="stat-icon red-icon">重</span></div>
          <div class="stat-value danger">{{ stats.critical }}<em> 人</em></div>
          <div class="stat-foot">机械通气 / 血管活性药 / CRRT</div>
        </div>
        <div class="stat-card stat-card-warning">
          <div class="stat-head"><span class="stat-label">今日待办</span><span class="stat-icon amber-icon">待</span></div>
          <div class="stat-value todo">{{ stats.todoCount }}<em> 项</em></div>
          <div class="stat-foot">未评 SOFA / APACHE II 等</div>
        </div>
        <div class="stat-card stat-card-neutral">
          <div class="stat-head"><span class="stat-label">平均在科天数</span><span class="stat-icon gray-icon">天</span></div>
          <div class="stat-value">{{ stats.avgDays }}<em> 天</em></div>
          <div class="stat-foot">仅统计有入科时间的患者</div>
        </div>
      </div>
    </section>

    <section class="wb-card patient-list-card">
      <div class="list-heading">
        <div class="section-heading list-title">
          <div>
            <div class="section-kicker">PATIENT LIST</div>
            <h2>患者列表 <span class="heading-count">{{ filteredPatients.length }}</span></h2>
          </div>
          <span class="section-hint">点击患者行进入抗感染决策</span>
        </div>
        <el-radio-group v-model="viewMode" class="view-switch" size="small" @change="onViewModeChange">
          <el-radio-button value="table">列表视图</el-radio-button>
          <el-radio-button value="cards">床头卡视图</el-radio-button>
        </el-radio-group>
      </div>

      <div class="list-body">
        <el-table
          v-show="viewMode === 'table'"
          v-loading="loading"
          :data="filteredPatients"
          stripe
          row-key="patientId"
          empty-text="当前科室口径下暂无在科患者"
          @row-click="openPatient"
        >
          <el-table-column label="患者信息" min-width="218">
            <template #default="{ row }">
              <div class="patient-name-line">
                <span class="patient-name">{{ row.name || '未知' }}</span>
                <span v-if="row.lastSofaScore != null" :class="['sofa-badge', row.lastSofaScore >= 10 ? 'severe' : '']">SOFA {{ row.lastSofaScore }}</span>
              </div>
              <div class="patient-sub">住院号 {{ row.patientNo || '—' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="基本信息" width="128">
            <template #default="{ row }"><span>{{ row.gender || '—' }}</span><span class="table-divider">·</span><span>{{ row.age == null ? '—' : `${row.age} 岁` }}</span></template>
          </el-table-column>
          <el-table-column label="所在位置" min-width="150">
            <template #default="{ row }">
              <div class="location-main"><span class="bed-pill">{{ row.bedNo || '待分配' }}</span><span>{{ deptLabel(row) }}</span></div>
              <div class="patient-sub">{{ row.wardName || '未分配病区' }}</div>
            </template>
          </el-table-column>
          <el-table-column label="危重状态" min-width="145">
            <template #default="{ row }">
              <div class="status-tags">
                <span v-if="row.ventilated" class="crit-tag vent">机械通气</span>
                <span v-if="row.onVasopressor" class="crit-tag vaso">血管活性药</span>
                <span v-if="row.onCrrt" class="crit-tag crrt">CRRT</span>
                <span v-if="!row.ventilated && !row.onVasopressor && !row.onCrrt" class="muted">无标记</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="待办" width="80" align="center">
            <template #default="{ row }">
              <el-tooltip v-if="row.todoCount > 0" :disabled="!row.todos || !row.todos.length" placement="top">
                <template #content><div class="todo-tooltip"><div v-for="t in row.todos" :key="t">{{ todoLabel(t) }}</div></div></template>
                <span class="todo-badge">{{ row.todoCount }}</span>
              </el-tooltip>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="在科情况" min-width="142">
            <template #default="{ row }"><div>{{ row.icuDays == null ? '—' : `${row.icuDays} 天` }}</div><div class="patient-sub">{{ formatTime(row.inDepartmentTime) }}</div></template>
          </el-table-column>
          <el-table-column label="工作入口" width="238" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="goDecision(row)">抗感染</el-button>
              <el-button link type="primary" @click.stop="goSofa(row)">SOFA</el-button>
              <el-dropdown trigger="click" popper-class="workbench-more-popper" @command="(cmd) => jump(cmd, row)">
                <el-button link type="primary" @click.stop>更多<el-icon class="el-icon--right"><arrow-down /></el-icon></el-button>
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

        <div v-if="viewMode === 'cards' && !loading" class="bed-card-grid">
          <div v-for="row in filteredPatients" :key="row.patientId" class="bed-card" @click="openPatient(row)">
            <div class="bed-card-topline"><span class="bed-no">{{ row.bedNo || '—' }}床</span><span class="bed-dept">{{ deptLabel(row) }}</span></div>
            <div class="bed-card-patient">
              <div class="bed-card-name">{{ row.name || '未知' }}</div>
              <div class="bed-card-sub">{{ row.patientNo || '—' }} · {{ row.gender || '—' }} · {{ row.age == null ? '—' : `${row.age} 岁` }}</div>
            </div>
            <div class="bed-card-status">
              <span v-if="row.ventilated" class="crit-tag vent">机械通气</span>
              <span v-if="row.onVasopressor" class="crit-tag vaso">血管活性药</span>
              <span v-if="row.onCrrt" class="crit-tag crrt">CRRT</span>
              <span v-if="!row.ventilated && !row.onVasopressor && !row.onCrrt" class="muted">暂无危重标记</span>
            </div>
            <div class="bed-card-metrics">
              <div><span>SOFA</span><strong :class="{ 'score-danger': row.lastSofaScore >= 10 }">{{ row.lastSofaScore == null ? '—' : row.lastSofaScore }}</strong></div>
              <div><span>待办</span><strong :class="{ 'todo-number': row.todoCount > 0 }">{{ row.todoCount || 0 }}</strong></div>
              <div><span>在科</span><strong>{{ row.icuDays == null ? '—' : `${row.icuDays}天` }}</strong></div>
            </div>
            <div class="bed-card-actions" @click.stop>
              <el-button link type="primary" size="small" @click="goDecision(row)">抗感染</el-button>
              <el-button link type="primary" size="small" @click="goSofa(row)">SOFA</el-button>
              <el-dropdown trigger="click" popper-class="workbench-more-popper" @command="(cmd) => jump(cmd, row)">
                <el-button link type="primary" size="small" @click.stop>更多<el-icon class="el-icon--right"><arrow-down /></el-icon></el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="/page/apache2-score">APACHE II</el-dropdown-item>
                    <el-dropdown-item command="/page/sepsis-bundle">脓毒症集束化</el-dropdown-item>
                    <el-dropdown-item command="/page/ards-prone-record">ARDS 俯卧位</el-dropdown-item>
                    <el-dropdown-item command="/page/abx-pkpd">PK/PD 剂量</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
          <div v-if="filteredPatients.length === 0" class="empty-foot">没有匹配的患者，试试清空搜索条件。</div>
        </div>

        <div v-if="!loading && patients.length === 0" class="empty-foot">{{ emptyText }}</div>
        <div v-else-if="!loading && filteredPatients.length === 0 && viewMode === 'table'" class="empty-foot">没有匹配的患者，试试清空搜索条件。</div>
      </div>
    </section>
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
import request from '../api/request'
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
// 视图模式：table 表格 / cards 床头卡；默认读参数设置 WORKBENCH_VIEW_MODE
const viewMode = ref('table')
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

// 读参数设置里的默认视图模式（WORKBENCH_VIEW_MODE=table/cards）；sessionStorage 有临时覆盖时优先
async function initViewMode() {
  const saved = sessionStorage.getItem("zing_workbench_view")
  if (saved === "table" || saved === "cards") {
    viewMode.value = saved
    return
  }
  try {
    const mode = await request.get("/sys-param/get", { params: { key: "WORKBENCH_VIEW_MODE" } })
    if (mode === "cards" || mode === "table") viewMode.value = mode
  } catch (e) { /* 参数未配置时用默认 table */ }
}
function onViewModeChange(mode) {
  sessionStorage.setItem("zing_workbench_view", mode)
}

onMounted(async () => {
  await initViewMode()
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
/* 患者工作台：沿用质控模块的暖橙 / stone 体系，但把信息层级调整为临床工作流。 */
.wb-page {
  min-height: 100vh;
  padding: 24px;
  background: #fafaf9;
  color: #292524;
}
.wb-card {
  background: #fff;
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(28, 25, 23, .04);
}
.wb-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  max-width: 1440px;
  margin: 0 auto 18px;
}
.hero-copy { min-width: 0; }
.eyebrow, .section-kicker {
  color: #c2410c;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.45px;
}
.hero-copy h1 {
  margin: 7px 0 6px;
  color: #1c1917;
  font-size: 28px;
  line-height: 1.2;
  letter-spacing: -.6px;
}
.hero-copy p { margin: 0; color: #78716c; font-size: 14px; }
.hero-meta { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 14px; }
.meta-pill, .summary-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 11px;
  border-radius: 999px;
  background: #f5f5f4;
  color: #57534e;
  font-size: 12px;
}
.meta-dot, .tag-dot { width: 7px; height: 7px; border-radius: 50%; display: inline-block; }
.meta-dot.active, .tag-dot.orange { background: #ea580c; }
.hero-actions { padding-top: 3px; flex-shrink: 0; }
.refresh-btn { border-color: #e7e5e4; color: #57534e; background: #fff; }
.refresh-btn:hover { color: #c2410c; border-color: #fdba74; background: #fff7ed; }
.wb-notice { max-width: 1440px; margin: 0 auto 12px; }
.filter-card, .overview-card, .patient-list-card { max-width: 1440px; margin: 0 auto 18px; }
.filter-card { padding: 18px 20px 20px; }
.section-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 14px; }
.section-heading h2 { margin: 4px 0 0; color: #1c1917; font-size: 17px; line-height: 1.3; }
.section-hint { color: #a8a29e; font-size: 12px; white-space: nowrap; }
.filter-heading { margin-bottom: 16px; }
.filter-fields { display: flex; align-items: flex-end; gap: 14px; flex-wrap: wrap; }
.filter-field { display: flex; flex-direction: column; gap: 7px; min-width: 178px; }
.filter-field.search-field { flex: 1 1 320px; min-width: 260px; }
.filter-label { color: #78716c; font-size: 12px; }
.filter-field :deep(.el-input), .filter-field :deep(.el-select) { width: 100%; }
.overview-card { padding: 20px 22px 22px; }
.overview-tags { display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
.summary-tag { color: #c2410c; background: #fff7ed; }
.summary-tag.muted-tag { color: #78716c; background: #f5f5f4; }
.stat-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 14px; margin-top: 18px; }
.stat-card {
  position: relative;
  min-height: 116px;
  padding: 16px 17px 14px;
  overflow: hidden;
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  background: linear-gradient(180deg, #fff 0%, #fafaf9 100%);
  transition: transform .2s, box-shadow .2s, border-color .2s;
}
.stat-card::before { content: ''; position: absolute; inset: 0 auto 0 0; width: 3px; background: #d6d3d1; }
.stat-card:hover { border-color: #d6d3d1; box-shadow: 0 4px 14px rgba(28, 25, 23, .06); transform: translateY(-1px); }
.stat-card-primary::before { background: #ea580c; }
.stat-card-danger::before { background: #dc2626; }
.stat-card-warning::before { background: #d97706; }
.stat-head { display: flex; align-items: center; justify-content: space-between; }
.stat-label { color: #78716c; font-size: 13px; font-weight: 500; }
.stat-icon { display: inline-flex; align-items: center; justify-content: center; width: 32px; height: 32px; border-radius: 8px; font-size: 12px; font-weight: 700; }
.orange-icon { color: #c2410c; background: #ffedd5; }
.red-icon { color: #b91c1c; background: #fee2e2; }
.amber-icon { color: #b45309; background: #fef3c7; }
.gray-icon { color: #57534e; background: #f5f5f4; }
.stat-value { margin-top: 7px; color: #1c1917; font-size: 28px; font-weight: 700; line-height: 1.2; font-variant-numeric: tabular-nums; }
.stat-value em { margin-left: 3px; color: #a8a29e; font-size: 12px; font-style: normal; font-weight: 400; }
.stat-value.danger { color: #b91c1c; }
.stat-value.todo { color: #b45309; }
.stat-foot { margin-top: 7px; color: #a8a29e; font-size: 11px; }
.patient-list-card { overflow: hidden; }
.list-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; padding: 19px 22px 15px; border-bottom: 1px solid #f5f5f4; }
.list-title { align-items: flex-end; }
.heading-count { display: inline-flex; align-items: center; justify-content: center; min-width: 24px; height: 22px; margin-left: 5px; padding: 0 7px; border-radius: 999px; color: #c2410c; background: #fff7ed; font-size: 12px; font-weight: 600; vertical-align: 2px; }
.view-switch :deep(.el-radio-button__inner) { border-color: #e7e5e4; color: #78716c; box-shadow: none; }
.view-switch :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) { color: #c2410c; background: #fff7ed; border-color: #fdba74; box-shadow: -1px 0 0 0 #fdba74; }
.list-body { padding: 0 8px 12px; }
.list-body :deep(.el-table) { --el-table-row-hover-bg-color: #fffaf5; --el-table-header-bg-color: #fafaf9; --el-table-border-color: #e7e5e4; color: #44403c; }
.list-body :deep(.el-table th.el-table__cell) { height: 48px; color: #57534e; font-size: 12px; font-weight: 600; }
.list-body :deep(.el-table td.el-table__cell) { padding: 12px 0; }
.list-body :deep(.el-table__row) { cursor: pointer; }
.patient-name-line { display: flex; align-items: center; gap: 6px; min-width: 0; }
.patient-name { color: #292524; font-weight: 600; }
.patient-sub { margin-top: 4px; color: #a8a29e; font-size: 12px; }
.table-divider { margin: 0 6px; color: #d6d3d1; }
.location-main { display: flex; align-items: center; gap: 7px; }
.bed-pill { display: inline-flex; align-items: center; min-height: 23px; padding: 2px 8px; border-radius: 6px; color: #57534e; background: #f5f5f4; font-size: 12px; white-space: nowrap; }
.status-tags { display: flex; flex-wrap: wrap; gap: 3px; }
.crit-tag { display: inline-flex; align-items: center; min-height: 20px; margin: 1px 2px 1px 0; padding: 1px 7px; border-radius: 999px; font-size: 11px; line-height: 18px; white-space: nowrap; }
.crit-tag.vent { color: #1d4ed8; background: #dbeafe; }
.crit-tag.vaso { color: #b91c1c; background: #fee2e2; }
.crit-tag.crrt { color: #92400e; background: #fef3c7; }
.sofa-badge { display: inline-flex; padding: 2px 6px; border-radius: 999px; color: #78716c; background: #f5f5f4; font-size: 11px; font-weight: 500; }
.sofa-badge.severe { color: #b91c1c; background: #fee2e2; font-weight: 700; }
.todo-badge { display: inline-flex; align-items: center; justify-content: center; min-width: 23px; height: 23px; padding: 0 7px; border-radius: 999px; color: #fff; background: #dc2626; font-size: 12px; font-weight: 700; }
.todo-tooltip { min-width: 150px; line-height: 1.8; }
.muted { color: #a8a29e; }
.empty-foot { padding: 38px 16px; text-align: center; color: #a8a29e; font-size: 12px; }
.bed-card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 14px; padding: 16px 8px 8px; }
.bed-card { position: relative; overflow: hidden; min-height: 218px; padding: 16px; border: 1px solid #e7e5e4; border-radius: 12px; background: linear-gradient(180deg, #fff 0%, #fafaf9 100%); cursor: pointer; transition: transform .2s, box-shadow .2s, border-color .2s; }
.bed-card::before { content: ''; position: absolute; inset: 0 0 auto; height: 3px; background: #ea580c; opacity: .75; }
.bed-card:hover { border-color: #fdba74; box-shadow: 0 5px 16px rgba(120, 53, 15, .08); transform: translateY(-2px); }
.bed-card-topline, .bed-card-metrics, .bed-card-actions { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.bed-card-topline { margin-bottom: 16px; }
.bed-no { color: #1c1917; font-size: 18px; font-weight: 700; }
.bed-dept { max-width: 130px; overflow: hidden; color: #a8a29e; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.bed-card-name { color: #292524; font-size: 18px; font-weight: 700; }
.bed-card-sub { margin-top: 5px; color: #78716c; font-size: 12px; }
.bed-card-status { display: flex; flex-wrap: wrap; min-height: 28px; margin: 16px 0 14px; align-items: center; }
.bed-card-metrics { padding: 11px 0; border-top: 1px solid #eeedec; border-bottom: 1px solid #eeedec; }
.bed-card-metrics > div { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.bed-card-metrics span { color: #a8a29e; font-size: 11px; }
.bed-card-metrics strong { color: #44403c; font-size: 15px; font-weight: 700; }
.bed-card-metrics .score-danger { color: #b91c1c; }
.bed-card-metrics .todo-number { color: #b45309; }
.bed-card-actions { justify-content: flex-start; padding-top: 10px; }
@media (max-width: 980px) { .stat-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .list-heading { align-items: flex-start; flex-direction: column; } }
@media (max-width: 680px) { .wb-page { padding: 16px 12px 30px; } .wb-hero { flex-direction: column; } .hero-actions { padding-top: 0; } .filter-card, .overview-card { padding: 16px; } .filter-field, .filter-field.search-field { width: 100%; min-width: 0; } .stat-grid { grid-template-columns: 1fr; } .section-heading { align-items: flex-start; flex-direction: column; } .section-hint { white-space: normal; } .overview-tags { justify-content: flex-start; } .list-heading { padding: 16px; } .list-body { padding: 0 2px 8px; overflow-x: auto; } .list-body :deep(.el-table) { min-width: 920px; } .bed-card-grid { grid-template-columns: 1fr; padding-left: 2px; padding-right: 2px; } }
</style>
