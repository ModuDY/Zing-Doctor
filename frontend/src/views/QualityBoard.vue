<template>
  <div class="quality-board">
    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-left">
        <el-select v-model="periodType" style="width: 100px" @change="onPeriodTypeChange">
          <el-option label="按月" value="MONTH" />
          <el-option label="按季" value="QUARTER" />
          <el-option label="按年" value="YEAR" />
        </el-select>

        <el-date-picker
          v-if="periodType === 'MONTH'"
          v-model="monthValue"
          type="month"
          placeholder="选择月份"
          value-format="YYYY-MM"
          :clearable="false"
          @change="loadOverview"
        />
        <el-date-picker
          v-else-if="periodType === 'YEAR'"
          v-model="yearValue"
          type="year"
          placeholder="选择年份"
          value-format="YYYY"
          :clearable="false"
          @change="loadOverview"
        />
        <template v-else>
          <el-date-picker
            v-model="quarterYear"
            type="year"
            placeholder="年份"
            value-format="YYYY"
            :clearable="false"
            style="width: 110px"
            @change="loadOverview"
          />
          <el-select v-model="quarter" style="width: 84px" @change="loadOverview">
            <el-option v-for="q in 4" :key="q" :label="`Q${q}`" :value="q" />
          </el-select>
        </template>

        <el-select
          v-model="departCode"
          placeholder="全院"
          style="width: 170px"
          clearable
          @change="loadOverview"
        >
          <el-option
            v-for="dept in departments"
            :key="dept.org_code"
            :label="dept.depart_name"
            :value="dept.org_code"
          />
        </el-select>

        <el-button type="primary" :loading="loading" @click="loadOverview">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>

      <div class="filter-right">
        <el-button :loading="recalcing" @click="doRecalc">
          <el-icon><Cpu /></el-icon> 触发计算
        </el-button>
        <el-button :loading="syncing" @click="doSync">
          <el-icon><Upload /></el-icon> 同步字典
        </el-button>
      </div>
    </div>

    <!-- 摘要卡 -->
    <div class="metric-cards">
      <div class="metric-card info">
        <div class="metric-label">指标总数</div>
        <div class="metric-value">{{ summary.total ?? 0 }}</div>
        <div class="metric-sub">按域分 {{ domainPanels.length }} 组</div>
      </div>
      <div class="metric-card ok">
        <div class="metric-label">本期已出数</div>
        <div class="metric-value">{{ summary.ok ?? 0 }}</div>
        <div class="metric-sub">calc_status = OK</div>
      </div>
      <div class="metric-card warn">
        <div class="metric-label">本期无数据</div>
        <div class="metric-value">{{ summary.noData ?? 0 }}</div>
        <div class="metric-sub">口径成立但本周期无命中</div>
      </div>
      <div class="metric-card muted">
        <div class="metric-label">未出数（含空壳）</div>
        <div class="metric-value">{{ summary.placeholder ?? 0 }}</div>
        <div class="metric-sub">待接数据源 / 口径待定 / 未计算 / 人工录入</div>
      </div>
    </div>

    <div v-if="overview.periodStart" class="period-hint">
      统计周期（左闭右开）：{{ overview.periodStart }} ~ {{ overview.periodEnd }}
      · 科室 {{ overview.departCode }}
      <span class="hint-note">空壳指标也占据完整行位，数值列显示「—」，页面始终是 127 行</span>
    </div>

    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- ---------------- 指标看板 ---------------- -->
      <el-tab-pane label="指标看板" name="board">
        <div v-loading="loading" class="tab-body">
          <el-alert
            v-if="!loading && domainPanels.length === 0"
            type="warning"
            show-icon
            :closable="false"
            title="指标字典为空"
            description="请先点击右上角「同步字典」，从 classpath:quality/metrics/*.yaml 同步指标定义。"
          />
          <el-collapse v-else v-model="activeDomains">
            <el-collapse-item
              v-for="group in domainPanels"
              :key="group.domain"
              :name="group.domain"
            >
              <template #title>
                <span class="domain-title">{{ group.domain }}</span>
                <span class="domain-count">
                  {{ group.metrics.length }} 条 · 已出数 {{ group.okCount }}
                </span>
              </template>
              <el-table :data="group.metrics" stripe border size="small">
                <el-table-column prop="code" label="指标编号" width="118" />
                <el-table-column label="指标名称" min-width="280">
                  <template #default="{ row }">
                    <span>{{ row.name }}</span>
                    <el-tooltip v-if="row.remark" placement="top" :content="row.remark">
                      <span class="remark-dot">?</span>
                    </el-tooltip>
                  </template>
                </el-table-column>
                <el-table-column prop="unit" label="单位" width="66" align="center" />
                <el-table-column label="本期值" width="110" align="right">
                  <template #default="{ row }">
                    <span v-if="isBlank(row.value)" class="empty-value">—</span>
                    <a v-else class="value-link" @click="openMetric(row)">{{ num(row.value) }}</a>
                  </template>
                </el-table-column>
                <el-table-column label="分子 / 分母" width="128" align="right">
                  <template #default="{ row }">
                    <span v-if="isBlank(row.numerator) && isBlank(row.denominator)" class="empty-value">—</span>
                    <span v-else>{{ num(row.numerator) }} / {{ num(row.denominator) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="计算状态" width="98" align="center">
                  <template #default="{ row }">
                    <el-tag :type="calcTag(row.calcStatus).type" size="small" effect="light">
                      {{ calcTag(row.calcStatus).label }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="实现状态" width="112" align="center">
                  <template #default="{ row }">
                    <el-tag :type="implTag(row.implStatus).type" size="small" effect="plain">
                      {{ implTag(row.implStatus).label }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="150" align="center" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" size="small" @click="openMetric(row)">
                      口径血缘
                    </el-button>
                    <el-button
                      v-if="row.implStatus === 'MANUAL'"
                      link
                      type="warning"
                      size="small"
                      @click="openManual(row)"
                    >
                      录入
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-collapse-item>
          </el-collapse>
        </div>
      </el-tab-pane>

      <!-- ---------------- 覆盖率报告 ---------------- -->
      <el-tab-pane label="覆盖率报告" name="coverage">
        <div v-loading="coverageLoading" class="tab-body">
          <div class="block-title">
            <span class="dot"></span>
            <span>按域承诺清单（配置层对 127 条的「可算 / 空壳 / 待接数据源」承诺）</span>
          </div>
          <el-table :data="coverage.summary || []" border size="small" row-key="domain">
            <el-table-column prop="domain" label="所属域" min-width="160" />
            <el-table-column prop="total" label="指标数" width="90" align="right" />
            <el-table-column prop="impl" label="可算" width="90" align="right" />
            <el-table-column prop="placeholder" label="空壳" width="90" align="right" />
            <el-table-column prop="pending" label="待接数据源" width="110" align="right" />
          </el-table>

          <div class="block-title" style="margin-top: 18px">
            <span class="dot"></span>
            <span>逐条明细</span>
          </div>
          <el-table :data="coverage.metrics || []" border size="small" max-height="520">
            <el-table-column prop="code" label="指标编号" width="118" />
            <el-table-column prop="name" label="指标名称" min-width="240" />
            <el-table-column prop="domain" label="所属域" width="130" />
            <el-table-column prop="fact" label="事实层" width="150" />
            <el-table-column prop="factStatus" label="事实层状态" width="120" align="center">
              <template #default="{ row }">
                <el-tag
                  :type="row.factStatus === 'ACTIVE' ? 'success' : row.factStatus ? 'warning' : 'info'"
                  size="small"
                  effect="plain"
                >
                  {{ row.factStatus || '—' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="有效状态" width="112" align="center">
              <template #default="{ row }">
                <el-tag :type="implTag(row.implStatus).type" size="small">
                  {{ implTag(row.implStatus).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="valueType" label="值类型" width="86" align="center" />
            <el-table-column prop="expressionVersion" label="口径版本" width="90" align="center">
              <template #default="{ row }">v{{ row.expressionVersion ?? '—' }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="说明" min-width="260" show-overflow-tooltip />
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ---------------- 事实层 ---------------- -->
      <el-tab-pane label="事实层" name="facts">
        <div v-loading="factsLoading" class="tab-body">
          <el-alert
            type="info"
            show-icon
            :closable="false"
            title="改 YAML 前先在页面上确认口径"
            description="事实层（DWD）由 DSL 声明、引擎编译成 SQL 并物化。下面展示的是按当前周期注入 :periodStart / :periodEnd 后编译出的实际 SQL。"
            style="margin-bottom: 14px"
          />
          <el-table :data="facts || []" border size="small">
            <el-table-column type="expand">
              <template #default="{ row }">
                <pre class="sql-box">{{ factSql(row.sourceTables) }}</pre>
              </template>
            </el-table-column>
            <el-table-column prop="fact" label="事实层" width="170" />
            <el-table-column prop="domain" label="所属域" width="140" />
            <el-table-column prop="status" label="状态" width="130" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'warning'" size="small" effect="plain">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="source" label="源表" width="160" />
            <el-table-column prop="note" label="说明" min-width="300" show-overflow-tooltip />
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ---------------- 计算批次 ---------------- -->
      <el-tab-pane label="计算批次" name="runs">
        <div v-loading="runsLoading" class="tab-body">
          <el-table :data="runs || []" border size="small" max-height="560">
            <el-table-column prop="runId" label="批次号" width="220" />
            <el-table-column prop="periodType" label="周期类型" width="96" align="center" />
            <el-table-column prop="periodStart" label="周期开始" width="160" />
            <el-table-column prop="periodEnd" label="周期结束" width="160" />
            <el-table-column prop="departCode" label="科室" width="110" />
            <el-table-column prop="triggerType" label="触发" width="96" align="center" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="runTag(row.status).type" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="metricTotal" label="总数" width="76" align="right" />
            <el-table-column prop="metricOk" label="成功" width="76" align="right" />
            <el-table-column prop="metricFail" label="失败" width="76" align="right" />
            <el-table-column prop="metricPlaceholder" label="占位" width="76" align="right" />
            <el-table-column prop="durationMs" label="耗时(ms)" width="100" align="right" />
            <el-table-column prop="operator" label="触发人" width="100" />
            <el-table-column prop="createTime" label="创建时间" width="160" />
            <el-table-column prop="message" label="信息" min-width="200" show-overflow-tooltip />
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- ---------------- 指标详情抽屉 ---------------- -->
    <el-drawer v-model="detailVisible" :title="detail.name || detail.code || '指标详情'" size="62%">
      <div v-loading="detailLoading" class="detail-body">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="指标编号">{{ detail.code || '—' }}</el-descriptions-item>
          <el-descriptions-item label="所属域">{{ detail.domain || '—' }}</el-descriptions-item>
          <el-descriptions-item label="单位">{{ detail.unit || '—' }}</el-descriptions-item>
          <el-descriptions-item label="事实层">{{ detail.factName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="实现状态">
            <el-tag :type="implTag(detail.implStatus).type" size="small">
              {{ implTag(detail.implStatus).label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="口径版本">v{{ detail.expressionVersion ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="统计周期" :span="2">
            {{ detail.periodStart || '—' }} ~ {{ detail.periodEnd || '—' }} · 科室 {{ detail.departCode || 'ALL' }}
          </el-descriptions-item>
          <el-descriptions-item label="口径说明" :span="2">
            {{ detail.remark || '—' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="!detail.result"
          type="info"
          show-icon
          :closable="false"
          style="margin-top: 14px"
          title="本期无计算结果"
          :description="noResultReason(detail.implStatus)"
        />

        <div v-else class="result-cards">
          <div class="result-card">
            <div class="result-label">指标值</div>
            <div class="result-value">{{ num(detail.result.metricValue, 4) }}</div>
            <div class="result-unit">{{ detail.result.unit || detail.unit || '' }}</div>
          </div>
          <div class="result-card">
            <div class="result-label">分子 / 分母</div>
            <div class="result-value small">
              {{ num(detail.result.numerator, 4) }} / {{ num(detail.result.denominator, 4) }}
            </div>
          </div>
          <div class="result-card">
            <div class="result-label">计算状态</div>
            <div class="result-value small">{{ detail.result.calcStatus || '—' }}</div>
          </div>
          <div class="result-card">
            <div class="result-label">数据快照批次</div>
            <div class="result-value small mono">{{ detail.result.runId || '—' }}</div>
          </div>
        </div>

        <el-tabs v-model="detailTab" style="margin-top: 16px">
          <el-tab-pane label="血缘与 SQL" name="trace">
            <el-table :data="detail.traces || []" border size="small" max-height="420">
              <el-table-column type="expand">
                <template #default="{ row }">
                  <pre class="sql-box">{{ row.sql || '（未记录 SQL 原文）' }}</pre>
                </template>
              </el-table-column>
              <el-table-column prop="dimKey" label="维度" width="120" />
              <el-table-column prop="factName" label="事实层" width="150" />
              <el-table-column prop="operators" label="算子链" min-width="200" show-overflow-tooltip />
              <el-table-column prop="scannedRows" label="扫描行数" width="110" align="right" />
              <el-table-column prop="numRows" label="分子行" width="90" align="right" />
              <el-table-column prop="denRows" label="分母行" width="90" align="right" />
              <el-table-column prop="durationMs" label="耗时(ms)" width="100" align="right" />
              <el-table-column prop="expressionVersion" label="版本" width="70" align="center">
                <template #default="{ row }">v{{ row.expressionVersion ?? '—' }}</template>
              </el-table-column>
            </el-table>
            <el-empty
              v-if="(detail.traces || []).length === 0"
              description="本周期无血缘记录（指标未出数或未预落库）"
              :image-size="70"
            />
          </el-tab-pane>

          <el-tab-pane :label="`患者明细（${(detail.patients || []).length}）`" name="patients">
            <el-table :data="detail.patients || []" border size="small" max-height="420">
              <el-table-column prop="patientName" label="姓名" width="100" />
              <el-table-column prop="inHospitalNo" label="住院号" width="150" />
              <el-table-column prop="patientId" label="患者ID" width="130" />
              <el-table-column prop="departCode" label="科室" width="110" />
              <el-table-column label="入分子" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.inNumerator === 1 ? 'success' : 'info'" size="small" effect="plain">
                    {{ row.inNumerator === 1 ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="入分母" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.inDenominator === 1 ? 'success' : 'info'" size="small" effect="plain">
                    {{ row.inDenominator === 1 ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
            <el-empty
              v-if="(detail.patients || []).length === 0"
              description="无患者级命中明细（下钻按需生成，指标未出数时为空）"
              :image-size="70"
            />
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <!-- ---------------- 人工录入弹窗 ---------------- -->
    <el-dialog v-model="manualVisible" title="人工录入指标值" width="440px">
      <div class="manual-row">
        <span class="manual-label">指标</span>
        <span>{{ manualForm.name }}</span>
      </div>
      <div class="manual-row">
        <span class="manual-label">编号</span>
        <span class="mono">{{ manualForm.code }}</span>
      </div>
      <div class="manual-row">
        <span class="manual-label">周期</span>
        <span>{{ overview.periodStart }} ~ {{ overview.periodEnd }}</span>
      </div>
      <div class="manual-row">
        <span class="manual-label">值</span>
        <el-input-number
          v-model="manualForm.value"
          :precision="4"
          :controls="false"
          style="width: 170px"
        />
      </div>
      <template #footer>
        <el-button @click="manualVisible = false">取消</el-button>
        <el-button type="primary" :loading="manualSaving" @click="doManual">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Cpu, Upload } from '@element-plus/icons-vue'
import {
  fetchQualityOverview,
  fetchQualityMetric,
  fetchQualityCoverage,
  fetchQualityFacts,
  fetchQualityRuns,
  fetchQualityDepartments,
  recalcQuality,
  saveQualityManual,
  syncQualityIndex
} from '../api/quality'

// ---------------- 状态字典 ----------------
const CALC_STATUS = {
  OK: { label: '已出数', type: 'success' },
  NO_DATA: { label: '无数据', type: 'warning' },
  NOT_CALC: { label: '未计算', type: 'info' },
  PLACEHOLDER: { label: '空壳', type: 'info' },
  PENDING_SOURCE: { label: '待接源', type: 'warning' },
  MANUAL: { label: '人工录入', type: 'primary' },
  ERROR: { label: '计算异常', type: 'danger' }
}
const IMPL_STATUS = {
  IMPL: { label: '已实现', type: 'success' },
  PENDING_SOURCE: { label: '待接数据源', type: 'warning' },
  PLACEHOLDER: { label: '口径待定', type: 'info' },
  MANUAL: { label: '人工录入', type: 'primary' }
}
const RUN_STATUS = {
  RUNNING: { type: 'primary' },
  SUCCESS: { type: 'success' },
  PARTIAL: { type: 'warning' },
  FAILED: { type: 'danger' }
}

function calcTag(code) {
  return CALC_STATUS[code] || { label: code || '—', type: 'info' }
}
function implTag(code) {
  return IMPL_STATUS[code] || { label: code || '—', type: 'info' }
}
function runTag(code) {
  return RUN_STATUS[code] || { type: 'info' }
}

// ---------------- 筛选条件 ----------------
const periodType = ref('MONTH')
const quarter = ref(1)

/** 默认取「上个月」，与后端 PeriodRange 缺省口径保持一致 */
function lastMonth() {
  const d = new Date()
  d.setDate(1)
  d.setMonth(d.getMonth() - 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}
function thisYear() {
  return String(new Date().getFullYear())
}

const monthValue = ref(lastMonth())
const yearValue = ref(thisYear())
const quarterYear = ref(thisYear())
const departCode = ref('')
const departments = ref([])

/** 周期起始（后端 PeriodRange.of 支持 yyyy-MM / yyyy） */
const periodStart = computed(() => {
  if (periodType.value === 'MONTH') return monthValue.value
  if (periodType.value === 'YEAR') return yearValue.value
  const m = (Number(quarter.value) - 1) * 3 + 1
  return `${quarterYear.value}-${String(m).padStart(2, '0')}`
})

function onPeriodTypeChange() {
  // 切周期类型时清掉另一类型的空值，避免把空串传给后端（会被当作「缺省=上个月」）
  if (periodType.value === 'MONTH' && !monthValue.value) monthValue.value = lastMonth()
  if (periodType.value === 'YEAR' && !yearValue.value) yearValue.value = thisYear()
  if (periodType.value === 'QUARTER' && !quarterYear.value) quarterYear.value = thisYear()
  loadOverview()
}

// ---------------- 看板数据 ----------------
const loading = ref(false)
const activeTab = ref('board')
const overview = reactive({})
const activeDomains = ref([])

const summary = computed(() => overview.summary || {})
const domainPanels = computed(() =>
  (overview.groups || []).map((g) => ({
    domain: g.domain,
    metrics: g.metrics || [],
    okCount: (g.metrics || []).filter((m) => m.calcStatus === 'OK').length
  }))
)

async function loadOverview() {
  loading.value = true
  try {
    const res = await fetchQualityOverview({
      periodType: periodType.value,
      periodStart: periodStart.value,
      departCode: departCode.value || ''
    })
    Object.assign(overview, res || {})
    activeDomains.value = (res && res.groups ? res.groups : []).map((g) => g.domain)
  } catch (e) {
    console.warn('质控看板加载失败:', e.message || e)
  } finally {
    loading.value = false
  }
}

async function loadDepartments() {
  try {
    const res = await fetchQualityDepartments()
    departments.value = Array.isArray(res) ? res : []
  } catch (e) {
    // 科室下拉是锦上添花，取不到不影响看板
    console.warn('科室列表加载失败:', e.message || e)
  }
}

// ---------------- 覆盖率 / 事实层 / 批次（按 Tab 懒加载） ----------------
const coverage = reactive({})
const coverageLoading = ref(false)
const facts = ref([])
const factsLoading = ref(false)
const runs = ref([])
const runsLoading = ref(false)

async function loadCoverage() {
  coverageLoading.value = true
  try {
    Object.assign(coverage, (await fetchQualityCoverage()) || {})
  } catch (e) {
    console.warn('覆盖率报告加载失败:', e.message || e)
  } finally {
    coverageLoading.value = false
  }
}

async function loadFacts() {
  factsLoading.value = true
  try {
    facts.value = (await fetchQualityFacts(periodStart.value)) || []
  } catch (e) {
    console.warn('事实层加载失败:', e.message || e)
  } finally {
    factsLoading.value = false
  }
}

async function loadRuns() {
  runsLoading.value = true
  try {
    runs.value = (await fetchQualityRuns()) || []
  } catch (e) {
    console.warn('批次加载失败:', e.message || e)
  } finally {
    runsLoading.value = false
  }
}

function onTabChange(name) {
  if (name === 'coverage' && !coverage.metrics) loadCoverage()
  if (name === 'facts' && facts.value.length === 0) loadFacts()
  if (name === 'runs' && runs.value.length === 0) loadRuns()
}

// ---------------- 指标详情抽屉 ----------------
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailTab = ref('trace')
const detail = reactive({})

async function openMetric(row) {
  detailVisible.value = true
  detailLoading.value = true
  detailTab.value = 'trace'
  // 先清空，避免快速连点时短暂显示上一条指标的数据
  Object.keys(detail).forEach((k) => delete detail[k])
  try {
    const res = await fetchQualityMetric(row.code, {
      periodStart: periodStart.value,
      departCode: departCode.value || ''
    })
    Object.assign(detail, res || {})
  } catch (e) {
    ElMessage.error('指标详情加载失败：' + (e.message || e))
  } finally {
    detailLoading.value = false
  }
}

function noResultReason(implStatus) {
  if (implStatus === 'PENDING_SOURCE') return '该指标的事实层尚未接入数据源，引擎不给出假 0，接入后即生效。'
  if (implStatus === 'PLACEHOLDER') return '该指标口径待业务确认（如判定标准未定），当前为空壳占位。'
  if (implStatus === 'MANUAL') return '该指标为人工录入类，请在指标行点击「录入」填写。'
  return '该指标在本周期尚未计算，可点击右上角「触发计算」。'
}

// ---------------- 触发计算 / 同步字典 ----------------
const recalcing = ref(false)
const syncing = ref(false)

async function doRecalc() {
  try {
    await ElMessageBox.confirm(
      `将重算 ${overview.periodStart || periodStart.value} ~ ${overview.periodEnd || ''} 的指标结果（同周期同科室幂等覆盖），是否继续？`,
      '触发计算',
      { type: 'warning', confirmButtonText: '开始计算', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  recalcing.value = true
  try {
    const res = await recalcQuality({
      periodType: periodType.value,
      periodStart: periodStart.value,
      departCode: departCode.value || ''
    })
    const ok = res && res.metricOk !== undefined ? res.metricOk : ''
    ElMessage.success(`计算完成${ok !== '' ? `：成功 ${ok} 条` : ''}`)
    await loadOverview()
  } catch (e) {
    ElMessage.error('计算失败：' + (e.message || e))
  } finally {
    recalcing.value = false
  }
}

async function doSync() {
  syncing.value = true
  try {
    const count = await syncQualityIndex()
    ElMessage.success(`指标字典同步完成，共 ${count ?? 0} 条`)
    await loadOverview()
  } catch (e) {
    ElMessage.error('同步失败：' + (e.message || e))
  } finally {
    syncing.value = false
  }
}

// ---------------- 人工录入 ----------------
const manualVisible = ref(false)
const manualSaving = ref(false)
const manualForm = reactive({ code: '', name: '', value: null })

function openManual(row) {
  manualForm.code = row.code
  manualForm.name = row.name
  manualForm.value = row.value === null || row.value === undefined ? null : Number(row.value)
  manualVisible.value = true
}

async function doManual() {
  if (manualForm.value === null || manualForm.value === undefined || Number.isNaN(manualForm.value)) {
    ElMessage.warning('请填写指标值')
    return
  }
  manualSaving.value = true
  try {
    await saveQualityManual({
      code: manualForm.code,
      value: manualForm.value,
      periodType: periodType.value,
      periodStart: periodStart.value,
      departCode: departCode.value || ''
    })
    ElMessage.success('录入成功')
    manualVisible.value = false
    await loadOverview()
  } catch (e) {
    ElMessage.error('录入失败：' + (e.message || e))
  } finally {
    manualSaving.value = false
  }
}

// ---------------- 展示工具 ----------------
function isBlank(v) {
  return v === null || v === undefined || v === ''
}

/** 数值展示：整数不带小数，否则保留 digits 位并去掉尾随 0 */
function num(v, digits = 2) {
  if (isBlank(v)) return '—'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  if (Number.isInteger(n)) return String(n)
  return n.toFixed(digits).replace(/0+$/, '').replace(/\.$/, '')
}

/** facts 接口的 sourceTables 可能是数组或字符串，统一成可读文本 */
function factSql(sourceTables) {
  if (!sourceTables) return '（暂无编译 SQL）'
  return Array.isArray(sourceTables) ? sourceTables.join('\n') : String(sourceTables)
}

onMounted(() => {
  loadDepartments()
  loadOverview()
})
</script>

<style scoped>
.quality-board {
  padding: 16px;
  background: #f5f7fa;
  min-height: 100vh;
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.filter-left,
.filter-right {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.metric-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.metric-card {
  background: #fff;
  border-radius: 8px;
  padding: 18px 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
  border-left: 4px solid #909399;
}

.metric-card.ok {
  border-left-color: #67c23a;
}
.metric-card.warn {
  border-left-color: #e6a23c;
}
.metric-card.muted {
  border-left-color: #909399;
}
.metric-card.info {
  border-left-color: #409eff;
}

.metric-label {
  font-size: 14px;
  color: #606266;
  margin-bottom: 8px;
}

.metric-value {
  font-size: 30px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.metric-sub {
  font-size: 12px;
  color: #909399;
  margin-top: 6px;
}

.period-hint {
  font-size: 12px;
  color: #606266;
  margin-bottom: 8px;
  padding: 8px 12px;
  background: #ecf5ff;
  border-radius: 6px;
}

.hint-note {
  color: #909399;
  margin-left: 8px;
}

.tab-body {
  min-height: 200px;
}

.domain-title {
  font-weight: 600;
  color: #303133;
  margin-right: 10px;
}

.domain-count {
  font-size: 12px;
  color: #909399;
}

.remark-dot {
  display: inline-block;
  width: 15px;
  height: 15px;
  line-height: 15px;
  text-align: center;
  margin-left: 6px;
  border-radius: 50%;
  background: #f0f2f5;
  color: #909399;
  font-size: 11px;
  cursor: help;
}

.empty-value {
  color: #c0c4cc;
}

.value-link {
  color: #409eff;
  font-weight: 600;
  cursor: pointer;
}

.value-link:hover {
  text-decoration: underline;
}

.block-title {
  display: flex;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.dot {
  width: 4px;
  height: 16px;
  background: linear-gradient(180deg, #409eff, #66b1ff);
  border-radius: 2px;
  margin-right: 8px;
}

.sql-box {
  margin: 0;
  padding: 12px;
  background: #1e1e2e;
  color: #a6e3a1;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 320px;
  overflow: auto;
}

.detail-body {
  padding: 0 4px 20px;
}

.result-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-top: 14px;
}

.result-card {
  background: #f7f9fc;
  border-radius: 8px;
  padding: 14px 16px;
}

.result-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}

.result-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
}

.result-value.small {
  font-size: 16px;
  font-weight: 600;
}

.result-unit {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.mono {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 12px;
}

.manual-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.manual-label {
  width: 48px;
  color: #909399;
  font-size: 13px;
  flex-shrink: 0;
}

@media (max-width: 1200px) {
  .metric-cards,
  .result-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
