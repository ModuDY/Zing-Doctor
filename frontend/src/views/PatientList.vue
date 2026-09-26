<!--
  已并入「患者工作台」的感染风险视图（见 PatientWorkbench.vue 的视图切换与感染状态列）。

  保留本文件仅作回退参考：路由 /page/abx-patient-list 现在直接重定向到
  /page/patient-workbench?view=infection，不再加载这个组件，构建产物里也不含它。
  真要回退，把 router/index.js 里该路由的 redirect 换回 component 指向本文件即可。
-->
<template>
  <div class="abx-theme abx-page">
    <!-- ===================== 标题行 ===================== -->
    <div class="abx-head">
      <div>
        <h2 class="abx-h2">疑似感染患者</h2>
        <div class="abx-sub">
          {{ departName || '全部科室' }}
          <span v-if="updatedAt"> · 更新于 {{ updatedAt }}</span>
        </div>
      </div>
      <div class="abx-head-actions">
        <el-select
          v-if="departOptions.length > 1"
          v-model="departCode"
          size="small"
          style="width: 190px"
          @change="onDepartChange">
          <el-option v-for="d in departOptions" :key="d.org_code" :label="d.depart_name" :value="d.org_code" />
        </el-select>
        <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <!-- ===================== 统计条（可点，点即筛选） ===================== -->
    <div class="abx-stats">
      <div
        v-for="s in statCards"
        :key="s.key"
        class="abx-stat"
        :class="{ 'is-active': isStatActive(s.key) }"
        @click="toggleStat(s.key)">
        <div class="k">{{ s.label }}</div>
        <div class="v" :style="{ color: s.color }">{{ s.value }}</div>
      </div>
    </div>

    <!-- ===================== 筛选行 ===================== -->
    <div class="abx-filters">
      <el-select v-model="filters.risk" clearable size="small" placeholder="风险等级" style="width: 120px">
        <el-option label="高风险" value="高风险" />
        <el-option label="中风险" value="中风险" />
        <el-option label="低风险" value="低风险" />
      </el-select>
      <el-select
        v-model="filters.infectionType"
        clearable
        filterable
        size="small"
        placeholder="感染部位"
        style="width: 190px">
        <el-option v-for="t in infectionTypeOptions" :key="t" :label="t" :value="t" />
      </el-select>
      <el-select
        v-model="filters.tags"
        multiple
        collapse-tags
        collapse-tags-tooltip
        clearable
        size="small"
        placeholder="危险标签"
        style="width: 210px">
        <el-option label="脓毒性休克" value="shock" />
        <el-option label="MDR" value="mdr" />
        <el-option label="MRSA" value="mrsa" />
        <el-option label="真菌风险" value="fungal" />
      </el-select>
      <el-select v-model="filters.decision" clearable size="small" placeholder="决策状态" style="width: 130px">
        <el-option :label="pendingLabel" value="pending" />
        <el-option label="已决策" value="done" />
      </el-select>
      <el-input
        v-model="filters.keyword"
        clearable
        size="small"
        placeholder="姓名 / 住院号 / 床位"
        style="width: 200px" />
      <el-button size="small" @click="resetFilters">重置</el-button>
      <span class="abx-count">共 {{ filtered.length }} 人</span>
    </div>

    <!-- ===================== 列表 ===================== -->
    <el-table
      v-loading="loading"
      :data="paged"
      stripe
      size="small"
      style="width: 100%"
      :row-class-name="rowClass"
      @row-click="openDetail">
      <el-table-column label="患者" min-width="150">
        <template #default="{ row }">
          <div class="abx-p">
            <span class="abx-p-name">{{ row.name || '—' }}</span>
            <span class="abx-p-meta">{{ row.gender || '' }} {{ row.age != null ? row.age + '岁' : '' }}</span>
          </div>
          <div class="abx-p-no">{{ row.patientNo || '—' }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="bedNo" label="床位" width="72" />
      <el-table-column label="入科" width="104">
        <template #default="{ row }">
          <div>{{ fmtDate(row.inDepartTime, '—') }}</div>
          <div class="abx-p-no">{{ inDepartDays(row.inDepartTime) }}</div>
        </template>
      </el-table-column>
      <el-table-column label="感染类型" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.infectionType || '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="pct" label="PCT" width="82" sortable :sort-method="sortByPct">
        <template #default="{ row }">
          <span :class="{ 'abx-num-danger': pctHigh(row) }">{{ num(row.pct) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="wbc" label="WBC" width="82">
        <template #default="{ row }">{{ num(row.wbc) }}</template>
      </el-table-column>
      <el-table-column prop="temperature" label="体温" width="82">
        <template #default="{ row }">{{ num(row.temperature) }}</template>
      </el-table-column>
      <el-table-column label="危险标签" width="170">
        <template #default="{ row }">
          <el-tag v-if="row.septicShock" type="danger" size="small">休克</el-tag>
          <el-tag v-if="row.mdrRisk" type="warning" size="small">MDR</el-tag>
          <el-tag v-if="row.mrsaRisk" type="warning" size="small">MRSA</el-tag>
          <el-tag v-if="row.fungalRisk" type="warning" size="small">真菌</el-tag>
          <span v-if="!row.septicShock && !row.mdrRisk && !row.mrsaRisk && !row.fungalRisk">—</span>
        </template>
      </el-table-column>
      <el-table-column label="风险" width="86" align="center">
        <template #default="{ row }">
          <el-tag :type="riskType(row.riskLevel)" size="small">{{ row.riskLevel || '—' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="当前抗菌药" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.currentAbx && row.currentAbx.length">{{ row.currentAbx.join('、') }}</span>
          <span v-else class="abx-p-no">无在用抗菌药</span>
        </template>
      </el-table-column>
      <el-table-column label="决策状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="decisionType(row)" size="small">{{ decisionText(row) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" @click.stop="goDecision(row)">抗感染决策</el-button>
          <el-button size="small" @click.stop="goPkpd(row)">PK/PD</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <div class="abx-empty">
          {{ loading ? '' : (patients.length ? '没有符合筛选条件的患者' : '当前科室暂无疑似感染患者') }}
        </div>
      </template>
    </el-table>

    <div v-if="filtered.length > page.size" class="abx-pager">
      <el-pagination
        v-model:current-page="page.num"
        :page-size="page.size"
        :total="filtered.length"
        layout="total, prev, pager, next"
        background
        small />
    </div>

    <!-- ===================== 患者详情抽屉 ===================== -->
    <el-drawer v-model="detailVisible" :title="detailTitle" size="520px" class="abx-overlay">
      <div v-if="detail.name" class="abx-drawer">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="姓名">{{ detail.name }}</el-descriptions-item>
          <el-descriptions-item label="住院号">{{ detail.patientNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="床位">{{ detail.bedNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="科室">{{ detail.department || departName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="入科时间">{{ fmtDateTime(detail.inDepartTime, '—') }}</el-descriptions-item>
          <el-descriptions-item label="风险等级">
            <el-tag :type="riskType(detail.riskLevel)" size="small">{{ detail.riskLevel || '—' }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <div class="abx-block">
          <div class="abx-block-title">感染类型</div>
          <div class="abx-block-body">
            <div class="abx-evidence-type">{{ detail.infectionType || '—' }}</div>
            <div class="abx-evidence">{{ detail.infectionEvidence || '无判定依据记录' }}</div>
          </div>
        </div>

        <div class="abx-block">
          <div class="abx-block-title">关键指标</div>
          <div class="abx-block-body abx-metrics">
            <div><span>PCT</span><b :class="{ 'abx-num-danger': pctHigh(detail) }">{{ num(detail.pct) }}</b></div>
            <div><span>WBC</span><b>{{ num(detail.wbc) }}</b></div>
            <div><span>体温</span><b>{{ num(detail.temperature) }}</b></div>
            <div><span>休克</span><b>{{ detail.septicShock ? '是' : '否' }}</b></div>
          </div>
        </div>

        <div class="abx-block">
          <div class="abx-block-title">当前抗菌药</div>
          <div class="abx-block-body">
            <div v-if="detail.currentAbx && detail.currentAbx.length">{{ detail.currentAbx.join('、') }}</div>
            <div v-else class="abx-p-no">无在用抗菌药</div>
            <div class="abx-p-no">开始时间：{{ fmtDateTime(detail.abxStartTime, '—') }}</div>
          </div>
        </div>

        <div class="abx-block">
          <div class="abx-block-title">决策状态</div>
          <div class="abx-block-body">
            <el-tag :type="decisionType(detail)" size="small">{{ decisionText(detail) }}</el-tag>
            <div class="abx-p-no">
              {{ detail.decisionTime ? `${fmtDateTime(detail.decisionTime)} · ${detail.decisionDoctor || '未记录医生'}` : '暂无决策记录' }}
            </div>
          </div>
        </div>

        <div class="abx-drawer-actions">
          <el-button type="primary" @click="goDecision(detail)">进入抗感染决策</el-button>
          <el-button @click="goPkpd(detail)">PK/PD 剂量优化</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script>
import { fetchPatients, fetchPendingRule } from '../api/antibiotic'
import { fetchDepartScope } from '../api/workbench'
import { setCurrentPatient } from '../utils/patientContext'
import { currentDepart, resolvePageDepartCode, setCurrentDepart } from '../utils/departContext'
import { fmtDate, fmtDateTime } from '../utils/datetime'
import '../styles/abx-theme.css'

const RISK_ORDER = { '高风险': 0, '中风险': 1, '低风险': 2 }

export default {
  name: 'PatientList',
  data() {
    return {
      loading: false,
      patients: [],
      updatedAt: '',
      departCode: '',
      departOptions: [],
      // 科室是否由外链 URL 指定：是的话全局科室下拉不应该覆盖它
      fromUrl: false,
      // 「待决策」口径，来自参数 ABX_PENDING_DECISION_RULE（取不到按后端默认）
      pendingRule: 'TODAY_NO_DECISION',
      filters: { risk: '', infectionType: '', tags: [], decision: '', keyword: '' },
      page: { num: 1, size: 20 },
      detailVisible: false,
      detail: {}
    }
  },
  computed: {
    /** 统计卡与筛选的「待决策」文案跟着参数口径走，别让文案和实际口径对不上 */
    pendingLabel() {
      return this.pendingRule === 'ADMIT_24H_NEVER' ? '待评估（入科>24h）' : '今日待决策'
    },
    /** 全局科室上下文（侧边栏下拉切换时会变，用于联动本页） */
    contextDepartCode() {
      return currentDepart.departCode || ''
    },
    departName() {
      const hit = this.departOptions.find(d => d.org_code === this.departCode)
      return hit ? hit.depart_name : (currentDepart.departName || '')
    },
    /** 感染部位选项从数据里派生，避免写死一份和后端规则对不上的枚举 */
    infectionTypeOptions() {
      const set = new Set()
      this.patients.forEach(p => { if (p.infectionType) set.add(p.infectionType) })
      return Array.from(set).sort()
    },
    filtered() {
      const kw = (this.filters.keyword || '').trim().toLowerCase()
      const tags = this.filters.tags || []
      let list = this.patients.filter(p => {
        if (this.filters.risk && p.riskLevel !== this.filters.risk) return false
        if (this.filters.infectionType && p.infectionType !== this.filters.infectionType) return false
        if (tags.includes('shock') && !p.septicShock) return false
        if (tags.includes('mdr') && !p.mdrRisk) return false
        if (tags.includes('mrsa') && !p.mrsaRisk) return false
        if (tags.includes('fungal') && !p.fungalRisk) return false
        if (this.filters.decision === 'pending' && !this.pendingOf(p)) return false
        if (this.filters.decision === 'done' && this.pendingOf(p)) return false
        if (kw) {
          const hay = `${p.name || ''} ${p.patientNo || ''} ${p.bedNo || ''}`.toLowerCase()
          if (!hay.includes(kw)) return false
        }
        return true
      })
      // 默认排序：高风险优先，其次 PCT 高、入科晚——医生最该先看的排在最前面
      list = list.slice().sort((a, b) => {
        const ra = RISK_ORDER[a.riskLevel] != null ? RISK_ORDER[a.riskLevel] : 9
        const rb = RISK_ORDER[b.riskLevel] != null ? RISK_ORDER[b.riskLevel] : 9
        if (ra !== rb) return ra - rb
        const pa = a.pct != null ? Number(a.pct) : -1
        const pb = b.pct != null ? Number(b.pct) : -1
        if (pa !== pb) return pb - pa
        return String(b.inDepartTime || '').localeCompare(String(a.inDepartTime || ''))
      })
      return list
    },
    paged() {
      const start = (this.page.num - 1) * this.page.size
      return this.filtered.slice(start, start + this.page.size)
    },
    statCards() {
      const list = this.patients
      return [
        { key: 'total', label: '疑似感染总数', value: list.length, color: 'var(--el-color-primary)' },
        { key: 'high', label: '高风险', value: list.filter(p => p.riskLevel === '高风险').length, color: 'var(--el-color-danger)' },
        { key: 'shock', label: '脓毒性休克', value: list.filter(p => p.septicShock).length, color: 'var(--el-color-danger)' },
        { key: 'mdr', label: 'MDR 阳性', value: list.filter(p => p.mdrRisk).length, color: 'var(--el-color-warning)' },
        { key: 'pending', label: this.pendingLabel, value: list.filter(p => this.pendingOf(p)).length, color: 'var(--el-color-warning)' }
      ]
    },
    detailTitle() {
      return this.detail.name ? `${this.detail.name} · 患者详情` : '患者详情'
    }
  },
  watch: {
    /** 筛选条件变化后回到第一页，否则会停在一个空白页上 */
    filters: {
      deep: true,
      handler() { this.page.num = 1 }
    },
    /** 侧边栏切换科室后本页跟着刷新（外链进来的以 URL 为准，不跟随） */
    contextDepartCode(v) {
      if (!v || this.fromUrl || v === this.departCode) return
      this.departCode = v
      this.page.num = 1
      this.load()
    }
  },
  async created() {
    const q = this.$route.query.departCode
    this.fromUrl = !!(q && !String(q).includes('${'))
    this.departCode = resolvePageDepartCode(q)
    // 先取科室范围再查列表：普通账号没选科室时，列表接口会直接拒绝（不退回全院）
    await this.loadScope()
    this.load()
    this.loadRule()
  },

  methods: {
    fmtDate,
    fmtDateTime,
    async loadScope() {
      try {
        const scope = await fetchDepartScope()
        if (scope && Array.isArray(scope.departs)) {
          this.departOptions = scope.departs
          if (!this.departCode && !this.fromUrl && scope.departs.length) {
            this.departCode = scope.departs[0].org_code
          }
        }
      } catch (e) {
        // 科室范围取不到不影响列表本身：后端仍会按账号授权校验
        this.departOptions = []
      }
    },
    /** 待决策口径：取不到就按后端默认「当日无决策记录」 */
    async loadRule() {
      try {
        const v = await fetchPendingRule()
        this.pendingRule = String(v || '').toUpperCase().includes('24H')
          ? 'ADMIT_24H_NEVER'
          : 'TODAY_NO_DECISION'
      } catch (e) {
        this.pendingRule = 'TODAY_NO_DECISION'
      }
    },
    /** 后端没带 pendingDecision（旧版本）时按默认口径兜底，避免整列空白 */
    pendingOf(p) {
      if (p.pendingDecision != null) return !!p.pendingDecision
      return !this.isToday(p.decisionTime)
    },
    async load() {
      this.loading = true
      try {
        // 科室边界：外链以 URL 参数为准，菜单进入用侧边栏选中的科室
        this.patients = await fetchPatients(this.departCode) || []
        const now = new Date()
        const pad = n => String(n).padStart(2, '0')
        this.updatedAt = `${pad(now.getHours())}:${pad(now.getMinutes())}`
      } finally {
        this.loading = false
      }
    },
    onDepartChange(code) {
      const hit = this.departOptions.find(d => d.org_code === code)
      // 写回全局科室上下文：之后从这里进 SOFA / APACHE II 等页面也用同一个科室
      if (hit) setCurrentDepart(hit)
      this.page.num = 1
      this.load()
    },
    resetFilters() {
      this.filters = { risk: '', infectionType: '', tags: [], decision: '', keyword: '' }
    },
    isStatActive(key) {
      if (key === 'high') return this.filters.risk === '高风险'
      if (key === 'shock') return (this.filters.tags || []).includes('shock')
      if (key === 'mdr') return (this.filters.tags || []).includes('mdr')
      if (key === 'pending') return this.filters.decision === 'pending'
      return !this.filters.risk && !this.filters.infectionType
        && !(this.filters.tags || []).length && !this.filters.decision && !this.filters.keyword
    },
    toggleStat(key) {
      if (this.isStatActive(key) && key !== 'total') {
        this.resetFilters()
        return
      }
      this.resetFilters()
      if (key === 'high') this.filters.risk = '高风险'
      if (key === 'shock') this.filters.tags = ['shock']
      if (key === 'mdr') this.filters.tags = ['mdr']
      if (key === 'pending') this.filters.decision = 'pending'
    },
    openDetail(row) {
      this.detail = row
      this.detailVisible = true
      // 与工作台保持一致：打开详情即把患者写进全局上下文
      this.remember(row)
    },
    remember(row) {
      setCurrentPatient({
        patientId: row.patientId,
        inHospitalNo: row.patientNo,
        name: row.name,
        bedNo: row.bedNo,
        departCode: currentDepart.departCode || this.departCode || '',
        departName: currentDepart.departName || this.departName || row.department || ''
      })
    },
    goDecision(row) {
      this.remember(row)
      this.detailVisible = false
      // 内部跳转携带 patientId；外链签名上下文已存于 sessionStorage，自动随 API 请求带上
      this.$router.push(`/page/abx-decision?patientId=${row.patientId}`)
    },
    goPkpd(row) {
      this.remember(row)
      this.detailVisible = false
      // PK/PD 页 watch 的是 inHospitalNo，两个都带上，切换患者时页面才会跟着刷新
      this.$router.push(`/page/abx-pkpd?patientId=${row.patientId}&inHospitalNo=${row.patientNo || ''}`)
    },
    num(v) {
      return v == null || v === '' ? '—' : v
    },
    pctHigh(row) {
      return row.pct != null && Number(row.pct) >= 2
    },
    inDepartDays(v) {
      if (!v) return ''
      const d = new Date(String(v).replace(' ', 'T'))
      if (isNaN(d.getTime())) return ''
      const days = Math.floor((Date.now() - d.getTime()) / 86400000)
      return days < 0 ? '' : `已入科 ${days} 天`
    },
    isToday(v) {
      if (!v) return false
      const s = String(v).slice(0, 10)
      const now = new Date()
      const pad = n => String(n).padStart(2, '0')
      return s === `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
    },
    riskType(level) {
      if (level === '高风险') return 'danger'
      if (level === '中风险') return 'warning'
      return 'info'
    },
    decisionText(row) {
      if (!row.decisionStatus) return '未决策'
      if (row.decisionStatus === 'accepted') return '已接受'
      if (row.decisionStatus === 'declined') return '已调整'
      return '待确认'
    },
    decisionType(row) {
      if (!row.decisionStatus) return 'warning'
      if (row.decisionStatus === 'accepted') return 'success'
      if (row.decisionStatus === 'declined') return 'info'
      return 'warning'
    },
    rowClass({ row }) {
      return row.riskLevel === '高风险' ? 'abx-row-high' : ''
    },
    sortByPct(a, b) {
      const pa = a.pct != null ? Number(a.pct) : -1
      const pb = b.pct != null ? Number(b.pct) : -1
      return pa - pb
    }
  }
}
</script>

<style scoped>
.abx-page {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
  min-height: 100vh;
  background: var(--el-bg-color-page);
}

.abx-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.abx-h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.abx-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.abx-head-actions {
  display: flex;
  gap: 8px;
}

.abx-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.abx-stat {
  background: #fff;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  padding: 12px 14px;
  cursor: pointer;
  transition: border-color .15s, box-shadow .15s;
}

.abx-stat:hover {
  border-color: var(--el-color-primary-light-5);
}

.abx-stat.is-active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 3px var(--el-color-primary-light-9);
}

.abx-stat .k {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.abx-stat .v {
  margin-top: 4px;
  font-size: 24px;
  font-weight: 600;
  line-height: 1.1;
}

.abx-filters {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 12px;
  margin-bottom: 12px;
  background: #fff;
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
}

.abx-count {
  margin-left: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.abx-p-name {
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.abx-p-meta {
  margin-left: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.abx-p-no {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.abx-num-danger {
  color: var(--el-color-danger);
  font-weight: 600;
}

.abx-empty {
  padding: 24px 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.abx-pager {
  display: flex;
  justify-content: flex-end;
  padding: 12px 0;
}

.abx-drawer {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.abx-block {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
}

.abx-block-title {
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  background: var(--el-fill-color-light);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.abx-block-body {
  padding: 12px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.abx-evidence-type {
  font-weight: 600;
  margin-bottom: 4px;
}

.abx-evidence {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}

.abx-metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  text-align: center;
}

.abx-metrics span {
  display: block;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.abx-metrics b {
  font-size: 16px;
}

.abx-drawer-actions {
  display: flex;
  gap: 8px;
  padding-top: 4px;
}

/* 高风险行左侧红条：扫列表时先看到最该处理的人 */
:deep(.abx-row-high td:first-child) {
  box-shadow: inset 3px 0 0 var(--el-color-danger);
}
</style>
