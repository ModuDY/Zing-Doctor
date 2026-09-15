<template>
  <div class="abx-decision-page abx-theme">
    <div v-if="!patientId && !inHospitalNo" class="warn-bar">
      <el-alert type="warning" :closable="false" show-icon
                title="缺少患者参数"
                description="本页面需通过外链（携带 patientId 或 ICU 外链 inHospitalNo）或从患者列表进入" />
    </div>

    <div v-else v-loading="loading" class="page-body">
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
                @visible-change="v => { if (v && !this.staffOptions.length) this.searchStaffRemote('') }">
                <el-option
                  v-for="item in staffOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value">
                  <span style="float: left">{{ item.label }}</span>
                  <span style="float: right; color: #8492a6; font-size: 12px">
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
import { fetchAssessment, fetchAssessmentByNo, saveDecision, updateDecision, deleteDecision, fetchRecords, searchStaff } from '../api/antibiotic'
import LabTrendChart from './LabTrendChart.vue'
import '../styles/abx-theme.css'

export default {
  name: 'DecisionDetail',
  components: { LabTrendChart },
  data() {
    return {
      patientId: '',
      inHospitalNo: '',
      loading: false,
      saving: false,
      view: { patient: {}, assessment: {}, adviceList: [], planSummary: '', labTrends: [] },
      records: [],
      doctorDecision: '',
      doctorName: '',
      doctorId: '',
      editingId: null,
      editingTime: '',
      staffOptions: [],
      staffLoading: false
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
      try {
        const view = this.patientId
          ? await fetchAssessment(this.patientId)
          : await fetchAssessmentByNo(this.inHospitalNo)
        this.view = view || {}
        // 按住院号解析出 patientId 后，后续决策/历史接口统一用它
        if (!this.patientId && view && view.patient && view.patient.patientId) {
          this.patientId = view.patient.patientId
        }
        if (this.patientId) {
          this.records = await fetchRecords(this.patientId)
        }
      } finally {
        this.loading = false
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
        }
        this.records = await fetchRecords(this.patientId)
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
        if (this.editingId === row.id) {
          this.cancelEdit()
        }
        this.records = await fetchRecords(this.patientId)
      }).catch(() => {})
    },
    goBack() {
      this.$router.push('/page/abx-patient-list')
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
  }
}
</script>

<style scoped>
.abx-decision-page {
  min-height: 100vh;
  background: #fafaf9;
}

/* 顶部深蓝导航条 */
.top-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 24px;
  height: 56px;
  background: linear-gradient(90deg, #0f2a43, #1f4e79);
  color: #fff;
  position: sticky;
  top: 0;
  z-index: 10;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}
.back-btn {
  color: #fff !important;
  font-size: 15px;
  padding: 0 6px;
}
.top-title {
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 1px;
}
.top-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
}
.top-patient {
  font-size: 14px;
  color: #cfe3f5;
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
.dot-cyan { background: #17a2b8; }
.dot-red { background: #dc2626; }
.dot-purple { background: #8b6fd8; }
.dot-green { background: #16a34a; }
.dot-gray { background: #78716c; }

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
  border-right: 1px solid #eef1f5;
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
  border-right: 1px solid #e2e8f0;
  padding: 4px 12px;
  min-width: 90px;
}
.p-cell-group .p-cell:last-child { border-right: none; }
.p-cell label { font-size: 12px; color: #8a94a3; }
.p-cell b { font-size: 14px; color: #44403c; }
.type-highlight { color: #1f4e79; }

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
.abx-list::-webkit-scrollbar-thumb { background: #c8d4e3; border-radius: 2px; }
.abx-list::-webkit-scrollbar-thumb:hover { background: #a8b8cc; }
.abx-list li {
  padding: 8px 10px;
  background: #f0fdfa;
  border-left: 3px solid #0d9488;
  border-radius: 6px;
  margin-bottom: 8px;
  font-size: 13px;
  color: #2c4056;
}
.abx-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.abx-time { font-family: 'Courier New', monospace; font-size: 12px; color: #78716c; background: #f5f5f4; padding: 2px 6px; border-radius: 4px; white-space: nowrap; }
.abx-name { font-weight: 600; color: #292524; }
.abx-freq { font-weight: 400; font-size: 12px; color: #0f766e; background: #ccfbf1; padding: 1px 5px; border-radius: 3px; margin-left: 4px; }
.abx-method { font-size: 12px; color: #78716c; }
.abx-tag { margin-left: auto; }
.abx-list .empty-li { background: none; border: none; color: #a0a8b4; }

/* 推荐方案 */
.plan-item {
  border: 1px solid #eef1f5;
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
.reason-label { color: #8a94a3; }

/* 既往培养：横向铺开 */
.culture-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 10px;
}
.culture-chip {
  background: #f5f5f4;
  border: 1px solid #e4e9f0;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  color: #3d4a5c;
}
.culture-chip.culture-danger {
  background: #fee2e2;
  border-color: #f3c2c2;
  color: #b33636;
}
.culture-chip.culture-warn {
  background: #fef3c7;
  border-color: #ecd9a8;
  color: #8a6d1f;
}
.culture-empty { color: #a0a8b4; font-size: 13px; padding: 8px; }

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
