<template>
  <div class="abx-decision-page abx-theme">
    <div v-if="!patientId && !inHospitalNo" class="warn-bar">
      <el-alert type="warning" :closable="false" show-icon
                title="缺少患者参数"
                description="本页面需通过外链（携带 patientId 或 ICU 外链 inHospitalNo）或从患者列表进入" />
    </div>

    <div v-else v-loading="loading" class="page-body">
      <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" class="page-error" />
      <!-- 患者信息横条：风险/休克靠前，避免窄屏换行错位 -->
      <div class="card patient-bar">
        <div class="p-cell"><label>姓名</label><b>{{ view.patient?.name || '—' }}</b></div>
        <div class="p-cell"><label>住院号</label><b>{{ view.patient?.patientNo || '—' }}</b></div>
        <div class="p-cell"><label>年龄 / 性别</label><b>{{ view.patient?.age || '—' }} / {{ view.patient?.gender || '—' }}</b></div>
        <div class="p-cell"><label>床位</label><b>{{ view.patient?.bedNo || '—' }}</b></div>
        <div class="p-cell-group">
          <div class="p-cell">
            <label>风险等级</label>
            <el-tag v-if="view.patient?.riskLevel"
                    :type="view.patient.riskLevel === '高风险' ? 'danger' : (view.patient.riskLevel === '中风险' ? 'warning' : 'success')"
                    size="small" effect="dark">{{ view.patient.riskLevel }}</el-tag>
            <b v-else>—</b>
          </div>
          <div class="p-cell">
            <label>休克状态</label>
            <el-tag v-if="view.patient" :type="shockTagType(view.patient.shockType)" size="small" effect="plain">
              {{ shockText(view.patient.shockType) }}
            </el-tag>
            <b v-else>—</b>
          </div>
        </div>
        <div class="p-cell"><label>疑似感染类型</label><b class="type-highlight">{{ view.patient?.infectionType || '—' }}</b></div>
        <div class="p-cell"><label>过敏史</label><b>{{ (view.assessment?.allergies || []).join('；') || '—' }}</b></div>
        <div class="p-cell"><label>肌酐</label><b>{{ view.assessment?.creatinine || '—' }}</b></div>
        <div class="p-cell"><label>体重</label><b>{{ view.assessment?.weight ? view.assessment.weight + ' kg' : '—' }}</b></div>
        <div class="p-cell p-cell-grow"><label>病区</label><b>{{ view.patient?.department || '—' }}</b></div>
      </div>

      <!-- 主体三栏 -->
      <div class="main-grid">
        <!-- 左栏：关键检验指标（趋势图） -->
        <div class="card">
          <div class="block-title"><span class="dot dot-blue"></span>关键检验指标</div>
          <LabTrendChart :trends="view.assessment?.labTrends || []" :latest-map="view.assessment?.labs || {}" />
        </div>

        <!-- 中栏：当前抗菌药物（独占一块） -->
        <div class="card">
          <div class="block-title"><span class="dot dot-cyan"></span>当前抗菌药物</div>
          <ul class="abx-list">
            <li v-for="(a, i) in view.assessment?.currentAntibiotics || []" :key="i">
              <div class="abx-row">
                <span class="abx-time">{{ a.startTime || '—' }}</span>
                <span class="abx-name">{{ a.name }}<span v-if="a.freq" class="abx-freq"> {{ a.freq }}</span></span>
                <span class="abx-method">{{ a.method || '' }}</span>
              </div>
            </li>
            <li v-if="!view.assessment?.currentAntibiotics || view.assessment.currentAntibiotics.length === 0" class="empty-li">—</li>
          </ul>
        </div>

        <!-- 右栏：系统推荐方案 -->
        <div class="card plan-card">
          <div class="block-title">
            <span class="dot dot-red"></span>系统推荐方案
            <el-tag v-if="view.planSummary" size="small" type="success" style="margin-left: 8px">{{ view.planSummary }}</el-tag>
          </div>
          <el-empty v-if="!view.adviceList || view.adviceList.length === 0" description="暂无推荐" :image-size="60" />
          <div v-else>
            <div v-for="(item, idx) in view.adviceList" :key="idx" class="plan-item">
              <div class="plan-head">
                <span class="plan-drug">{{ item.drugName }}</span>
                <el-tag :type="item.adviceLevel === '强' ? 'danger' : 'warning'" size="small" effect="dark">{{ item.adviceLevel }}</el-tag>
                <el-tag v-if="item.route" size="small" type="info" effect="plain">{{ item.route }}</el-tag>
              </div>
              <div class="plan-dose">{{ item.dosePlan }}</div>
              <div class="plan-reason"><span class="reason-label">理由：</span>{{ item.reason }}</div>
              <div class="plan-evidence"><span class="reason-label">证据：</span>{{ item.evidence }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 既往微生物培养 / 药敏（带时间，横向铺开） -->
      <div class="card">
        <div class="block-title"><span class="dot dot-purple"></span>既往微生物培养 / 药敏</div>
        <div v-if="view.assessment?.pastCultures && view.assessment.pastCultures.length" class="culture-grid">
          <div v-for="(c, i) in view.assessment.pastCultures" :key="i" class="culture-chip" :class="cultureClass(c)">{{ c }}</div>
        </div>
        <div v-else class="culture-empty">无明确微生物培养 / 多耐药记录</div>
      </div>

      <!-- 抗感染 48～72 小时复评：与原始经验性决策分开留痕，避免覆盖历史方案 -->
      <div class="card reassessment-card">
        <div class="block-title reassessment-title">
          <span class="dot dot-orange"></span>抗感染 48～72 小时复评
          <template v-if="latestReassessment">
            <el-tag :type="reassessmentTagType(latestReassessment.reviewStatus)" size="small" effect="plain">
              {{ reassessmentStatusText(latestReassessment.reviewStatus) }}
            </el-tag>
            <span v-if="latestReassessment.reviewDueTime" class="due-text">
              计划 {{ formatTime(latestReassessment.reviewDueTime) }}
            </span>
            <el-tag v-if="isOverdue(latestReassessment)" type="danger" size="small">已逾期</el-tag>
          </template>
        </div>
        <el-alert v-if="reassessmentLoadError" type="warning" :closable="false" show-icon
                  :title="reassessmentLoadError" />
        <el-empty v-else-if="!reassessments.length" description="当前没有待复评任务；采纳新的抗感染决策后会自动生成" :image-size="54" />
        <template v-else>
          <div v-if="latestReassessment && latestReassessment.reviewStatus === 'PENDING'" class="reassessment-form">
            <div class="reassessment-summary">
              <div><span>关联决策</span><strong>{{ formatTime(latestReassessment.createTime) }}</strong></div>
              <div><span>任务状态</span><strong>{{ isOverdue(latestReassessment) ? '已超过计划时间' : '待复评' }}</strong></div>
              <div><span>原始方案</span><strong>{{ latestDecisionPlan || '—' }}</strong></div>
            </div>
            <el-form label-position="top">
              <div class="reassessment-grid">
                <el-form-item label="培养 / 药敏复核摘要">
                  <el-input v-model="reassessmentForm.cultureSummary" type="textarea" :rows="3"
                            placeholder="填写最新培养、药敏及耐药菌结果；没有新结果请明确写‘暂无’" />
                </el-form-item>
                <el-form-item label="临床疗效评价">
                  <el-input v-model="reassessmentForm.clinicalResponse" type="textarea" :rows="3"
                            placeholder="如：体温下降、感染指标改善 / 无改善 / 恶化" />
                </el-form-item>
                <el-form-item label="PCT 趋势">
                  <el-input v-model="reassessmentForm.pctTrend" type="textarea" :rows="3"
                            placeholder="如：0.82 → 0.31 ng/mL，呈下降趋势；无连续结果请说明" />
                </el-form-item>
                <el-form-item label="复评动作" required>
                  <el-select v-model="reassessmentForm.decisionAction" placeholder="请选择复评动作" style="width: 100%">
                    <el-option label="继续当前方案" value="CONTINUE" />
                    <el-option label="降阶梯" value="DE_ESCALATE" />
                    <el-option label="升阶梯" value="ESCALATE" />
                    <el-option label="换药" value="SWITCH" />
                    <el-option label="停药" value="STOP" />
                    <el-option label="其他" value="OTHER" />
                  </el-select>
                </el-form-item>
                <el-form-item label="医生复评结论" class="reassessment-wide">
                  <el-input v-model="reassessmentForm.doctorDecision" type="textarea" :rows="3"
                            placeholder="写明本次调整或继续治疗的临床依据" />
                </el-form-item>
                <el-form-item label="复评医生" required>
                  <el-select
                    v-model="reassessmentForm.doctorId"
                    filterable
                    remote
                    reserve-keyword
                    clearable
                    popper-class="abx-popper"
                    :remote-method="searchStaffRemote"
                    :loading="staffLoading"
                    placeholder="输入姓名/拼音首字母/工号搜索"
                    style="width: 100%"
                    @change="handleReassessmentStaffSelect"
                    @visible-change="v => { if (v && !staffOptions.length) searchStaffRemote('') }">
                    <el-option v-for="item in staffOptions" :key="item.value" :label="item.label" :value="item.value">
                      <span style="float: left">{{ item.label }}</span>
                      <span style="float: right; color: #a8a29e; font-size: 12px">{{ item.workNo }} · {{ item.depart }}</span>
                    </el-option>
                  </el-select>
                </el-form-item>
                <el-form-item label="备注">
                  <el-input v-model="reassessmentForm.remark" type="textarea" :rows="3"
                            placeholder="可填写随访安排、限制因素或跳过复评原因" />
                </el-form-item>
              </div>
              <div class="reassessment-actions">
                <el-button type="primary" :loading="reassessmentSaving" @click="completeReassessmentForm">保存复评</el-button>
                <el-button :loading="reassessmentSaving" @click="skipReassessmentForm">跳过复评</el-button>
              </div>
            </el-form>
          </div>
          <el-table :data="reassessments" size="small" stripe max-height="240">
            <el-table-column label="计划时间" width="150">
              <template #default="{ row }">{{ formatTime(row.reviewDueTime) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="reassessmentTagType(row.reviewStatus)" size="small">{{ reassessmentStatusText(row.reviewStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="动作" width="110">
              <template #default="{ row }">{{ reassessmentActionText(row.decisionAction) }}</template>
            </el-table-column>
            <el-table-column prop="doctorDecision" label="复评结论" min-width="220" show-overflow-tooltip />
            <el-table-column prop="doctorName" label="医生" width="90" />
            <el-table-column prop="reviewTime" label="完成时间" width="150" />
          </el-table>
        </template>
      </div>

      <!-- 下部：医生决策 + 决策历史 -->
      <div class="bottom-grid">
        <div class="card">
          <div class="block-title"><span class="dot dot-green"></span>医生决策留痕
            <el-tag v-if="editingId" type="warning" size="small" effect="plain" style="margin-left: 8px">
              正在编辑 {{ formatTime(editingTime) }} 的历史记录
            </el-tag>
          </div>
          <el-form label-position="top">
            <el-form-item label="最终决策">
              <el-input v-model="doctorDecision" type="textarea" :rows="4"
                        placeholder="如：采纳系统方案 / 调整为 xxx（写明依据）" />
            </el-form-item>
            <el-form-item label="医生姓名">
              <el-select
                v-model="doctorId"
                filterable
                remote
                reserve-keyword
                clearable
                popper-class="abx-popper"
                :remote-method="searchStaffRemote"
                :loading="staffLoading"
                placeholder="输入姓名/拼音首字母/工号搜索"
                style="max-width: 320px; width: 100%"
                @change="handleStaffSelect"
                @visible-change="v => { if (v && !staffOptions.length) searchStaffRemote('') }">
                <el-option
                  v-for="item in staffOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value">
                  <span style="float: left">{{ item.label }}</span>
                  <span style="float: right; color: #a8a29e; font-size: 12px">
                    {{ item.workNo }} · {{ item.pinyin }} · {{ item.depart }}
                  </span>
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="submit('accepted')">{{ editingId ? '保存修改' : '采纳并保存' }}</el-button>
              <el-button :loading="saving" @click="submit('declined')">{{ editingId ? '保存修改' : '拒绝并说明' }}</el-button>
              <el-button v-if="editingId" @click="cancelEdit">取消编辑</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="card">
          <div class="block-title"><span class="dot dot-gray"></span>决策历史</div>
          <el-table v-if="records.length" :data="records" size="small" stripe max-height="300">
            <el-table-column label="时间" width="160">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column prop="recommendedPlan" label="系统推荐" min-width="150" show-overflow-tooltip />
            <el-table-column prop="doctorDecision" label="医生决策" min-width="150" show-overflow-tooltip />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.decisionStatus === 'accepted' ? 'success' : 'danger'" size="small">
                  {{ row.decisionStatus === 'accepted' ? '采纳' : '拒绝' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="doctorName" label="医生" width="90" />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="onEdit(row)">编辑</el-button>
                <el-button type="danger" link size="small" @click="onDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="暂无决策记录" :image-size="60" />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { fetchAssessment, fetchAssessmentByNo, saveDecision, updateDecision, deleteDecision, fetchRecords, searchStaff, fetchReassessments, completeReassessment, skipReassessment } from '../api/antibiotic'
import LabTrendChart from './LabTrendChart.vue'
import '../styles/abx-theme.css'
import { markWorkbenchRefresh } from '../utils/patientContext'

export default {
  name: 'DecisionDetail',
  components: { LabTrendChart },
  data() {
    return {
      patientId: '',
      inHospitalNo: '',
      loading: false,
      loadError: '',
      saving: false,
      view: { patient: {}, assessment: {}, adviceList: [], planSummary: '', labTrends: [] },
      records: [],
      reassessments: [],
      reassessmentLoadError: '',
      reassessmentSaving: false,
      reassessmentForm: {
        cultureSummary: '',
        clinicalResponse: '',
        pctTrend: '',
        decisionAction: '',
        doctorDecision: '',
        doctorId: '',
        doctorName: '',
        remark: ''
      },
      doctorDecision: '',
      doctorName: '',
      doctorId: '',
      editingId: null,
      editingTime: '',
      staffOptions: [],
      staffLoading: false
    }
  },
  computed: {
    latestReassessment() {
      return this.reassessments.find(r => r.reviewStatus === 'PENDING') || this.reassessments[0] || null
    },
    latestDecisionPlan() {
      const accepted = this.records.find(r => r.decisionStatus === 'accepted')
      return accepted ? accepted.recommendedPlan : ''
    }
  },
  created() {
    // 支持两种入口：内部列表带 patientId；ICU 外链带 inHospitalNo（住院号）
    this.patientId = this.$route.query.patientId || ''
    this.inHospitalNo = this.$route.query.inHospitalNo || ''
    if (this.patientId || this.inHospitalNo) {
      this.load()
    }
  },
  methods: {
    abxTagType(code) {
      if (code === 'running') return 'success'
      if (code === 'finished') return 'info'
      if (code === 'pending') return 'warning'
      return 'info'
    },
    shockTagType(type) {
      if (type === 'septic') return 'danger'
      if (type === 'infectious') return 'warning'
      return 'info'
    },
    shockText(type) {
      if (type === 'septic') return '脓毒性休克'
      if (type === 'infectious') return '感染性休克'
      return '非休克'
    },
    formatTime(val) {
      if (!val) return '—'
      // 兼容 "2026-09-05T02:23:17" / "2026-09-05 02:23:17" / 带毫秒
      const s = String(val).replace('T', ' ').replace(/\.\d+$/, '').replace(/Z$/, '')
      return s.length >= 16 ? s.substring(0, 16) : s
    },
    async searchStaffRemote(query) {
      if (query === undefined || query === null) query = ''
      this.staffLoading = true
      try {
        const list = await searchStaff(query)
        this.staffOptions = (list || []).map(s => ({
          value: s.user_id,
          label: s.realname,
          workNo: s.work_no,
          pinyin: s.pinyin,
          depart: s.depart_name
        }))
      } catch (e) {
        this.staffOptions = []
      } finally {
        this.staffLoading = false
      }
    },
    handleStaffSelect(val) {
      const picked = this.staffOptions.find(s => s.value === val)
      if (picked) {
        this.doctorName = picked.label
        this.doctorId = picked.value
      }
    },
    async load() {
      this.loading = true
      this.loadError = ''
      this.records = []
      this.reassessments = []
      try {
        const view = this.patientId
          ? await fetchAssessment(this.patientId)
          : await fetchAssessmentByNo(this.inHospitalNo)
        if (!view || !view.patient) {
          this.loadError = '未找到患者信息，请从患者工作台重新进入，或检查外链住院号是否有效。'
          return
        }
        this.view = view
        // 按住院号解析出 patientId 后，后续决策/历史接口统一用它
        if (!this.patientId && view.patient.patientId) {
          this.patientId = view.patient.patientId
        }
        if (this.patientId) {
          try {
            this.records = await fetchRecords(this.patientId)
          } catch (e) {
            this.records = []
            this.loadError = '患者基础信息已加载，但决策历史暂不可用，请检查医生库连接后重试。'
          }
          await this.loadReassessments()
        }
      } catch (e) {
        this.view = { patient: {}, assessment: {}, adviceList: [], planSummary: '', labTrends: [] }
        this.loadError = '患者数据暂不可用：请检查 ICU 数据源、患者权限或外链是否已过期。'
      } finally {
        this.loading = false
      }
    },
    async loadReassessments() {
      this.reassessmentLoadError = ''
      try {
        this.reassessments = await fetchReassessments(this.patientId)
        const pending = this.reassessments.find(r => r.reviewStatus === 'PENDING')
        if (pending) {
          this.reassessmentForm = {
            cultureSummary: pending.cultureSummary || '',
            clinicalResponse: pending.clinicalResponse || '',
            pctTrend: pending.pctTrend || '',
            decisionAction: pending.decisionAction || '',
            doctorDecision: pending.doctorDecision || '',
            doctorId: pending.doctorId || '',
            doctorName: pending.doctorName || '',
            remark: pending.remark || ''
          }
          if (pending.doctorId && pending.doctorName && !this.staffOptions.some(s => s.value === pending.doctorId)) {
            this.staffOptions.unshift({ value: pending.doctorId, label: pending.doctorName })
          }
        }
      } catch (e) {
        this.reassessments = []
        this.reassessmentLoadError = '复评数据暂不可用，请确认数据库升级脚本已执行；原始决策仍可继续使用。'
      }
    },
    handleReassessmentStaffSelect(val) {
      const picked = this.staffOptions.find(s => s.value === val)
      this.reassessmentForm.doctorName = picked ? picked.label : ''
    },
    reassessmentStatusText(status) {
      return { PENDING: '待复评', COMPLETED: '已完成', SKIPPED: '已跳过', VOID: '已作废' }[status] || status || '—'
    },
    reassessmentTagType(status) {
      return { PENDING: 'warning', COMPLETED: 'success', SKIPPED: 'info', VOID: 'danger' }[status] || 'info'
    },
    reassessmentActionText(action) {
      return { CONTINUE: '继续当前方案', DE_ESCALATE: '降阶梯', ESCALATE: '升阶梯', SWITCH: '换药', STOP: '停药', OTHER: '其他' }[action] || action || '—'
    },
    isOverdue(task) {
      return task && task.reviewStatus === 'PENDING' && task.reviewDueTime && new Date(task.reviewDueTime).getTime() < Date.now()
    },
    async completeReassessmentForm() {
      const task = this.latestReassessment
      if (!task || task.reviewStatus !== 'PENDING') return
      if (!this.reassessmentForm.decisionAction) return this.$message.warning('请选择复评动作')
      if (!this.reassessmentForm.doctorName) return this.$message.warning('请先选择复评医生')
      this.reassessmentSaving = true
      try {
        await completeReassessment({ id: task.id, ...this.reassessmentForm })
        this.$message.success('复评已保存')
        markWorkbenchRefresh('antibiotic-reassessment-saved')
        await this.loadReassessments()
      } finally {
        this.reassessmentSaving = false
      }
    },
    async skipReassessmentForm() {
      const task = this.latestReassessment
      if (!task || task.reviewStatus !== 'PENDING') return
      if (!this.reassessmentForm.doctorName) return this.$message.warning('请先选择复评医生')
      if (!this.reassessmentForm.remark) return this.$message.warning('跳过复评时请填写原因')
      this.reassessmentSaving = true
      try {
        await skipReassessment({ id: task.id, ...this.reassessmentForm })
        this.$message.success('已跳过本次复评，并保留原因')
        markWorkbenchRefresh('antibiotic-reassessment-saved')
        await this.loadReassessments()
      } finally {
        this.reassessmentSaving = false
      }
    },
    async submit(status) {
      // 保存/修改前必须选择医生姓名，避免产生无责任人的脏记录
      if (!this.doctorName) {
        this.$message.warning('请先选择医生姓名后再保存')
        return
      }
      this.saving = true
      try {
        if (this.editingId) {
          await updateDecision({
            id: this.editingId,
            doctorDecision: this.doctorDecision,
            decisionStatus: status,
            doctorName: this.doctorName,
            doctorId: this.doctorId
          })
          this.$message.success('决策已更新')
          markWorkbenchRefresh('antibiotic-decision-saved')
          this.cancelEdit()
        } else {
          await saveDecision({
            patientId: this.patientId,
            doctorDecision: this.doctorDecision,
            decisionStatus: status,
            doctorName: this.doctorName,
            doctorId: this.doctorId
          })
          this.$message.success('决策已保存')
          markWorkbenchRefresh('antibiotic-decision-saved')
        }
        this.records = await fetchRecords(this.patientId)
        await this.loadReassessments()
      } finally {
        this.saving = false
      }
    },
    /** 编辑历史记录：回填表单进入编辑态 */
    onEdit(row) {
      this.doctorDecision = row.doctorDecision || ''
      this.doctorId = row.doctorId || ''
      this.doctorName = row.doctorName || ''
      this.editingId = row.id
      this.editingTime = row.createTime
      // 确保下拉框能回显当前医生（不在已加载选项时补一条）
      if (row.doctorId && !this.staffOptions.some(s => s.value === row.doctorId)) {
        this.staffOptions.unshift({ value: row.doctorId, label: row.doctorName || row.doctorId })
      }
    },
    /** 取消编辑态 */
    cancelEdit() {
      this.editingId = null
      this.editingTime = ''
      this.doctorDecision = ''
      this.doctorId = ''
      this.doctorName = ''
    },
    /** 删除历史记录 */
    onDelete(row) {
      this.$confirm(`确认删除 ${this.formatTime(row.createTime)} 的决策记录？删除后不可恢复。`, '删除确认', {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        customClass: 'abx-overlay'
      }).then(async () => {
        await deleteDecision(row.id)
        this.$message.success('已删除')
        markWorkbenchRefresh('antibiotic-decision-deleted')
        if (this.editingId === row.id) {
          this.cancelEdit()
        }
        this.records = await fetchRecords(this.patientId)
        await this.loadReassessments()
      }).catch(() => {})
    },
    goBack() {
      this.$router.push({ path: '/page/abx-patient-list', query: { ...this.$route.query } })
    },
    cultureClass(text) {
      const t = String(text || '')
      if (t.includes('MRSA') || t.includes('耐药') || t.includes('CRE') || t.includes('鲍曼') || t.includes('ESBL')) {
        return 'culture-danger'
      }
      if (t.includes('真菌') || t.includes('念珠') || t.includes('曲霉')) {
        return 'culture-warn'
      }
      return ''
    }
  },
}
</script>

<style scoped>
.abx-decision-page {
  min-height: 100vh;
  background: #fafaf9;
}

.page-body {
  padding: 16px 24px 32px;
  max-width: 1920px;
  margin: 0 auto;
}
.warn-bar {
  padding: 16px 24px;
}

/* 卡片通用 */
.card {
  background: #fff;
  border-radius: 10px;
  padding: 16px 18px;
  box-shadow: 0 1px 4px rgba(28, 25, 23, 0.04);
  border: 1px solid #e7e5e4;
}
.block-title {
  font-size: 15px;
  font-weight: 600;
  color: #292524;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
}
.dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 8px; }
.dot-blue { background: linear-gradient(180deg, #0d9488, #2dd4bf); }
.dot-cyan { background: #0891b2; }
.dot-red { background: #dc2626; }
.dot-purple { background: #8b6fd8; }
.dot-green { background: #16a34a; }
.dot-gray { background: #78716c; }
.dot-orange { background: #ea580c; }

/* 患者信息横条 */
.patient-bar {
  display: flex;
  align-items: stretch;
  flex-wrap: wrap;
  padding: 12px 0;
  margin-bottom: 16px;
}
.p-cell {
  flex: 0 0 auto;
  min-width: 100px;
  padding: 4px 16px;
  border-right: 1px solid #f5f5f4;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.p-cell-grow { flex: 1 1 auto; border-right: none; }
.p-cell-group {
  display: flex;
  align-items: stretch;
  background: #fafaf9;
  border-radius: 8px;
  padding: 4px 8px;
}
.p-cell-group .p-cell {
  border-right: 1px solid #e7e5e4;
  padding: 4px 12px;
  min-width: 90px;
}
.p-cell-group .p-cell:last-child { border-right: none; }
.p-cell label { font-size: 12px; color: #78716c; }
.p-cell b { font-size: 14px; color: #44403c; }
.type-highlight { color: #0f766e; }

/* 主体三栏 */
.main-grid {
  display: grid;
  grid-template-columns: 1.1fr 1.25fr 0.95fr;
  gap: 16px;
  margin-bottom: 16px;
}
@media (max-width: 1500px) {
  .main-grid { grid-template-columns: 1fr 1fr; }
  .main-grid > .card:last-child { grid-column: 1 / -1; }
}
@media (max-width: 900px) {
  .main-grid { grid-template-columns: 1fr; }
  .main-grid > .card:last-child { grid-column: auto; }
}

/* 当前抗菌药 */
.abx-list { margin: 0; padding: 0; list-style: none; max-height: 220px; overflow-y: auto; padding-right: 4px; }
.abx-list::-webkit-scrollbar { width: 4px; }
.abx-list::-webkit-scrollbar-track { background: transparent; }
.abx-list::-webkit-scrollbar-thumb { background: #d6d3d1; border-radius: 2px; }
.abx-list::-webkit-scrollbar-thumb:hover { background: #a8a29e; }
.abx-list li {
  padding: 8px 10px;
  background: #f0fdfa;
  border-left: 3px solid #0d9488;
  border-radius: 6px;
  margin-bottom: 8px;
  font-size: 13px;
  color: #292524;
}
.abx-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.abx-time { font-family: 'Courier New', monospace; font-size: 12px; color: #78716c; background: #f5f5f4; padding: 2px 6px; border-radius: 4px; white-space: nowrap; }
.abx-name { font-weight: 600; color: #292524; }
.abx-freq { font-weight: 400; font-size: 12px; color: #0f766e; background: #ccfbf1; padding: 1px 5px; border-radius: 3px; margin-left: 4px; }
.abx-method { font-size: 12px; color: #78716c; }
.abx-list .empty-li { background: none; border: none; color: #a8a29e; }

/* 推荐方案 */
.plan-item {
  border: 1px solid #f5f5f4;
  border-left: 4px solid #dc2626;
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 12px;
  background: #fff;
}
.plan-head { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.plan-drug { font-size: 16px; font-weight: 700; color: #292524; }
.plan-dose { margin-top: 8px; font-size: 15px; color: #292524; font-weight: 600; }
.plan-reason { margin-top: 6px; font-size: 13px; color: #44403c; }
.plan-evidence { margin-top: 2px; font-size: 12px; color: #78716c; }
.reason-label { color: #78716c; }

/* 既往培养：横向铺开 */
.culture-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 10px;
}
.culture-chip {
  background: #f5f5f4;
  border: 1px solid #e7e5e4;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  color: #44403c;
}
.culture-chip.culture-danger {
  background: #fee2e2;
  border-color: #fecaca;
  color: #b91c1c;
}
.culture-chip.culture-warn {
  background: #fef3c7;
  border-color: #fde68a;
  color: #92400e;
}
.culture-empty { color: #a8a29e; font-size: 13px; padding: 8px; }

/* 48～72 小时复评 */
.reassessment-card { margin-top: 16px; }
.reassessment-title { gap: 10px; }
.reassessment-title .el-tag { margin-left: 2px; }
.due-text { color: #78716c; font-size: 12px; font-weight: 400; }
.reassessment-summary { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin-bottom: 14px; padding: 12px; border-radius: 8px; background: #fff7ed; }
.reassessment-summary div { display: flex; flex-direction: column; gap: 4px; }
.reassessment-summary span { color: #a8a29e; font-size: 12px; }
.reassessment-summary strong { color: #44403c; font-size: 13px; }
.reassessment-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 0 14px; }
.reassessment-grid .reassessment-wide { grid-column: span 2; }
.reassessment-actions { display: flex; gap: 10px; margin: 2px 0 14px; }
@media (max-width: 1100px) { .reassessment-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .reassessment-grid .reassessment-wide { grid-column: span 2; } }
@media (max-width: 680px) { .reassessment-summary { grid-template-columns: 1fr; } .reassessment-grid { grid-template-columns: 1fr; } .reassessment-grid .reassessment-wide { grid-column: auto; } }

/* 下部：决策 + 历史 */
.bottom-grid {
  display: grid;
  grid-template-columns: 1.1fr 1.3fr;
  gap: 16px;
}
@media (max-width: 1100px) {
  .bottom-grid { grid-template-columns: 1fr; }
}
</style>
