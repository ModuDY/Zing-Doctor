<template>
  <div v-loading="loading" class="handover-board abx-theme">
    <!-- 顶部筛选栏 -->
    <div class="filter-bar">
      <div class="filter-left">
        <el-select v-model="selectedDepartCode" placeholder="选择科室" style="width: 170px" popper-class="abx-popper" @change="loadOverview">
          <el-option v-for="d in departments" :key="d.org_code" :label="d.depart_name" :value="d.org_code" />
        </el-select>
        <div class="shift-tag">
          <el-icon><Clock /></el-icon>
          <span>交班日期：</span>
          <el-date-picker
            v-model="shiftDate"
            type="date"
            placeholder="选择交班日期"
            value-format="YYYY-MM-DD"
            style="width: 180px"
            popper-class="abx-popper"
            @change="onShiftDateChange"
          />
        </div>
        <el-button type="primary" :loading="loading" @click="loadOverview">
          <el-icon><Refresh /></el-icon>&nbsp;刷新
        </el-button>
      </div>
      <div class="filter-right">
        <span class="doctor-label">交班医生</span>
        <el-input v-model="currentDoctor" placeholder="填写交班医生姓名" style="width: 150px" clearable />
      </div>
    </div>

    <!-- 汇总统计条 -->
    <div class="summary-bar">
      <div class="sum-chip primary"><span class="num">{{ s.totalPatients }}</span><span class="lbl">在科</span></div>
      <div class="sum-chip"><span class="num">{{ s.newInCount }}</span><span class="lbl">新入</span></div>
      <div class="sum-chip"><span class="num">{{ s.dischargeCount }}</span><span class="lbl">出科</span></div>
      <div class="sum-chip"><span class="num">{{ s.ventilatorCount }}</span><span class="lbl">呼吸机</span></div>
      <div class="sum-chip"><span class="num">{{ s.crrtCount }}</span><span class="lbl">CRRT</span></div>
      <div class="sum-chip"><span class="num">{{ s.ecmoCount }}</span><span class="lbl">ECMO</span></div>
      <div class="sum-chip warn"><span class="num">{{ s.vasopressorCount }}</span><span class="lbl">升压药</span></div>
      <div class="sum-chip danger"><span class="num">{{ s.feverCount }}</span><span class="lbl">发热</span></div>
      <div class="sum-chip danger"><span class="num">{{ s.abnormalLabCount }}</span><span class="lbl">检验异常</span></div>
      <div class="sum-chip danger"><span class="num">{{ s.sepsisShockCount }}</span><span class="lbl">脓毒性休克</span></div>
      <div class="sum-chip"><span class="num">{{ s.isolationCount }}</span><span class="lbl">隔离</span></div>
      <div class="sum-chip success"><span class="num">{{ s.noteFilledCount }}/{{ s.totalPatients }}</span><span class="lbl">已交班</span></div>
    </div>

    <!-- 患者卡片网格 -->
    <div class="patient-grid">
      <div v-for="p in patients" :key="p.patientId" class="patient-card" @click="openDetail(p)">
        <div class="pc-head">
          <span class="bed">{{ p.bedCode || '—' }}</span>
          <span class="pname">{{ p.name || '未命名' }}</span>
          <span class="pmeta">{{ p.gender || '' }} {{ formatAge(p) }}</span>
          <span class="tags">
            <el-tag v-if="p.newIn" type="success" size="small" effect="dark">新入</el-tag>
            <el-tag v-if="p.isolation" type="warning" size="small">隔离</el-tag>
            <el-tag v-if="p.sepsisShock" type="danger" size="small">休克</el-tag>
            <el-tag v-if="p.ards" type="info" size="small">ARDS</el-tag>
            <el-tag v-if="p.noteId" type="success" size="small" effect="plain">已交班</el-tag>
          </span>
        </div>
        <div class="pc-line dim">住院号：{{ p.inHospitalNo || '—' }}&emsp;主管：{{ p.chargeDoctorName || p.residentDoctorName || '—' }}</div>
        <div class="pc-line diagnosis" :title="p.diagnosisContent">{{ p.diagnosisContent || '暂无诊断' }}</div>

        <div class="vital-row">
          <span class="vital" :class="{ hot: p.fever }">T <b>{{ p.temp || '—' }}</b></span>
          <span class="vital">HR <b>{{ p.hr || '—' }}</b></span>
          <span class="vital">RR <b>{{ p.rr || '—' }}</b></span>
          <span class="vital">SpO₂ <b>{{ p.spo2 || '—' }}</b></span>
          <span class="vital">BP <b>{{ p.sbp ? p.sbp + '/' + (p.dbp || '—') : '—' }}</b></span>
        </div>

        <div class="support-row">
          <el-tag v-if="p.ventilator" size="small" type="primary" effect="plain">呼吸机</el-tag>
          <el-tag v-if="p.crrt" size="small" type="primary" effect="plain">CRRT</el-tag>
          <el-tag v-if="p.ecmo" size="small" type="primary" effect="plain">ECMO</el-tag>
          <el-tag v-for="v in p.vasopressors" :key="v" size="small" type="warning" effect="plain">{{ shortDrug(v) }}</el-tag>
        </div>

        <div class="io-row">
          <span>入 <b>{{ fmtNum(p.intakeTotal) }}</b></span>
          <span>出 <b>{{ fmtNum(p.outputTotal) }}</b></span>
          <span>尿 <b>{{ fmtNum(p.urineTotal) }}{{ p.urineCatheter ? '/C' : '' }}</b></span>
          <span>平衡 <b :class="balanceClass(p.balanceTotal)">{{ fmtNum(p.balanceTotal) }}</b></span>
        </div>

        <div v-if="p.abnormalLabCount > 0" class="lab-row">
          <el-icon class="danger-text"><Warning /></el-icon>
          <span class="danger-text"><b>{{ p.abnormalLabCount }}</b> 项检验异常</span>
          <span class="lab-names">{{ p.abnormalLabNames.join('，') }}</span>
        </div>

        <div class="note-row" :class="{ filled: !!p.conditionChange }">
          <el-icon><EditPen /></el-icon>
          <template v-if="p.conditionChange">
            <span class="note-text" :title="p.conditionChange">{{ p.conditionChange }}</span>
            <span class="note-by">{{ p.noteCreateBy }}</span>
          </template>
          <span v-else class="note-empty">点击填写本班病情变化</span>
        </div>
      </div>

      <el-empty v-if="!loading && patients.length === 0" description="该班次/科室暂无在科患者" />
    </div>

    <!-- 单患者交班详情抽屉 -->
    <el-drawer v-model="detailVisible" size="62%" :title="detailTitle" destroy-on-close class="abx-overlay">
      <div v-if="detail" class="detail-wrap">
        <!-- 患者信息条 -->
        <div class="d-patient">
          <div><span class="d-bed">{{ patientMap.bed_no }}</span>
            <b class="d-name">{{ patientMap.name }}</b>
            <span class="d-meta">{{ patientMap.gender }}&emsp;{{ patientMap.age }}岁&emsp;住院号 {{ patientMap.patient_no }}</span>
          </div>
          <div class="d-jump">
            <el-button size="small" @click="jumpOther('/page/abx-decision')">抗感染决策</el-button>
            <el-button size="small" @click="jumpOther('/page/abx-pkpd')">PK/PD</el-button>
            <el-button size="small" @click="jumpOther('/page/sepsis-bundle')">集束化治疗</el-button>
          </div>
        </div>
        <div class="d-info-line">
          <span>入科：{{ fmtT(patientMap.in_depart_time) }}</span>
          <span>出科：{{ fmtT(patientMap.out_depart_time) || '在科' }}</span>
          <span>科室：{{ patientMap.department }}</span>
          <span>体重：{{ patientMap.weight || '—' }} kg</span>
          <span v-if="patientMap.allergy_content">过敏：<b class="danger-text">{{ patientMap.allergy_content }}</b></span>
        </div>

        <!-- 病情变化手工交班 -->
        <div class="d-section">
          <div class="block-title"><span class="dot"></span><span>本班病情变化（医生手工交班）</span></div>
          <el-input v-model="noteText" type="textarea" :rows="4" placeholder="记录本班病情变化：症状/体征变化、重要检查结果、调整治疗、需下一班关注事项……" />
          <div class="note-actions">
            <span v-if="detail.note" class="note-meta">上次保存：{{ detail.note.createBy }} {{ detail.note.createTime }}</span>
            <span v-else class="note-meta">本班尚未填写</span>
            <div>
              <el-button size="small" :loading="importing" style="margin-right: 6px" @click="importPrevNote">
                <el-icon><Download /></el-icon>&nbsp;导入上一班
              </el-button>
              <el-button v-if="detail.note" type="danger" plain size="small" @click="deleteNote">
                <el-icon><Delete /></el-icon>&nbsp;删除
              </el-button>
              <el-button type="primary" size="small" :loading="saving" @click="saveNote">
                <el-icon><Check /></el-icon>&nbsp;保存病情变化
              </el-button>
            </div>
          </div>
        </div>

        <!-- 生命体征与器官支持 -->
        <div class="d-section">
          <div class="block-title"><span class="dot"></span><span>班内生命体征 / 监护（截至当前最新在前）</span></div>
          <el-table :data="detail.vitals" size="small" max-height="260" stripe>
            <el-table-column prop="item_name" label="项目" min-width="160" />
            <el-table-column label="数值" width="140">
              <template #default="{ row }">{{ row.item_value }} {{ row.item_unit || '' }}</template>
            </el-table-column>
            <el-table-column label="时间" width="180"><template #default="{ row }">{{ fmtT(row.item_time) }}</template></el-table-column>
          </el-table>
        </div>

        <!-- 治疗用药 -->
        <div class="d-section">
          <div class="block-title"><span class="dot"></span><span>治疗用药</span></div>
          <div class="d-sub">血管活性 / 升压药</div>
          <div class="tag-box">
            <el-tag v-for="(v, i) in detail.vasopressors" :key="'v'+i" type="warning" effect="plain" style="margin:2px 6px 2px 0">
              {{ v.name }} {{ v.drug_one_dosage || '' }}{{ v.drug_one_dosage_unit || '' }} · {{ v.freq_name || '' }}
            </el-tag>
            <span v-if="!detail.vasopressors.length" class="dim">无</span>
          </div>
          <div class="d-sub">当前抗菌药物</div>
          <div class="tag-box">
            <el-tag v-for="(a, i) in detail.antibiotics" :key="'a'+i" type="primary" effect="plain" style="margin:2px 6px 2px 0">
              {{ a.name }} · {{ a.freq_name || '' }}
            </el-tag>
            <span v-if="!detail.antibiotics.length" class="dim">无</span>
          </div>
          <div v-if="detail.crrt" class="d-sub">
            CRRT：{{ detail.crrt.crrt_plan || '—' }}，{{ detail.crrt.is_end === 0 ? '进行中' : '已结束' }}，
            血流速 {{ detail.crrt.blood_rate || '—' }}，
            抗凝方式 {{ detail.crrt.anticaking_method || '—' }}
          </div>
        </div>

        <!-- 出入量 -->
        <div class="d-section">
          <div class="block-title"><span class="dot"></span><span>本班出入量</span></div>
          <!-- 出入量汇总 -->
          <div v-if="detail.ioSummary" class="io-summary">
            <div class="io-row">
              <span class="io-label">总入量：</span>
              <span class="io-value intake">{{ detail.ioSummary.intakeTotal || 0 }} ml</span>
              <span class="io-label" style="margin-left:24px">总出量：</span>
              <span class="io-value output">{{ detail.ioSummary.outputTotal || 0 }} ml</span>
              <span class="io-label" style="margin-left:24px">平衡：</span>
              <span class="io-value" :class="detail.ioSummary.balanceTotal >= 0 ? 'intake' : 'output'">{{ detail.ioSummary.balanceTotal || 0 }} ml</span>
            </div>
            <!-- 尿量 -->
            <div class="io-row" style="margin-top:6px">
              <span class="io-label">尿量：</span>
              <span class="io-value">{{ detail.ioSummary.urineTotal || 0 }} ml<span v-if="detail.ioSummary.urineCatheter" class="catheter-tag">/C</span></span>
            </div>
            <!-- 出量项目分组汇总 -->
            <div v-if="detail.outputItemSummary && detail.outputItemSummary.length" class="io-items">
              <div class="io-items-title">具体出量项目：</div>
              <div class="io-items-list">
                <span v-for="(item, idx) in detail.outputItemSummary" :key="idx" class="io-item-tag">
                  {{ item.item_name }}：{{ item.total_value }} ml
                </span>
              </div>
            </div>
          </div>
          <!-- 出入量明细 -->
          <div class="block-title" style="margin-top:12px"><span class="dot"></span><span>出入量明细</span></div>
          <el-table :data="detail.ioRecords" size="small" max-height="240" stripe>
            <el-table-column prop="item_name" label="项目" min-width="160" />
            <el-table-column label="数值" width="140">
              <template #default="{ row }">{{ row.item_value }} {{ row.item_unit || '' }}</template>
            </el-table-column>
            <el-table-column label="时间" width="180"><template #default="{ row }">{{ fmtT(row.item_time) }}</template></el-table-column>
          </el-table>
        </div>

        <!-- 检验异常 -->
        <div class="d-section">
          <div class="block-title"><span class="dot"></span><span>本班检验结果（异常标红）</span></div>
          <el-table :data="detail.labs" size="small" max-height="300" stripe>
            <el-table-column prop="item_name" label="项目" min-width="160" />
            <el-table-column label="结果" width="130">
              <template #default="{ row }">
                <span :class="{ 'danger-text': row.abnormal }"><b>{{ row.result }}</b> {{ row.unit || '' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="参考范围" width="150">
              <template #default="{ row }">{{ row.low_value || '—' }} ~ {{ row.high_value || '—' }}</template>
            </el-table-column>
            <el-table-column label="时间" width="180"><template #default="{ row }">{{ fmtT(row.check_time) }}</template></el-table-column>
          </el-table>
        </div>

        <!-- 检查与培养 -->
        <div class="d-section">
          <div class="block-title"><span class="dot"></span><span>微生物培养 / 药敏</span></div>
          <el-table :data="detail.microbiology" size="small" max-height="240" stripe>
            <el-table-column prop="item_name" label="项目" min-width="150" />
            <el-table-column prop="result" label="结果" min-width="160" />
            <el-table-column label="时间" width="180"><template #default="{ row }">{{ fmtT(row.check_time) }}</template></el-table-column>
          </el-table>
        </div>

        <!-- 诊断 -->
        <div class="d-section">
          <div class="block-title"><span class="dot"></span><span>诊断</span></div>
          <div class="tag-box">
            <el-tag v-for="(d, i) in detail.diagnoses" :key="i" effect="plain" style="margin:2px 6px 2px 0">
              {{ d.diag_name }}（{{ fmtT(d.diag_time) }}）
            </el-tag>
            <span v-if="!detail.diagnoses.length" class="dim">无</span>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Clock, Warning, EditPen, Delete, Check, Download } from '@element-plus/icons-vue'
import request from '../api/request'
import '../styles/abx-theme.css'

const router = useRouter()

const loading = ref(false)
const saving = ref(false)
const importing = ref(false)
const departments = ref([])
const selectedDepartCode = ref('20070131')
const shiftDate = ref(null)  // 交班日期，默认=后端返回的上一完整全天班的起始日期
const ZERO_SUMMARY = { totalPatients: 0, newInCount: 0, dischargeCount: 0, ventilatorCount: 0, crrtCount: 0, ecmoCount: 0, vasopressorCount: 0, feverCount: 0, abnormalLabCount: 0, sepsisShockCount: 0, isolationCount: 0, noteFilledCount: 0 }
const overview = reactive({ shiftRange: null, summary: { ...ZERO_SUMMARY }, patients: [] })
const patients = computed(() => overview.patients || [])
const s = computed(() => overview.summary || {})

// 交班医生（本地记忆，避免每次重填；外链传参 realname=姓名默认填充）
const routeQuery = new URLSearchParams(window.location.search)
const linkRealname = routeQuery.get('realname') || ''
const currentDoctor = ref(localStorage.getItem('handoverDoctor') || linkRealname || '')

// 详情抽屉
const detailVisible = ref(false)
const detail = ref(null)
const noteText = ref('')
const currentCard = ref(null)

const detailTitle = computed(() => {
  if (!detail.value) return '患者交班详情'
  const p = detail.value.patient || {}
  return `${p.bed_no || ''} ${p.name || ''} · 交班详情`
})
const patientMap = computed(() => (detail.value && detail.value.patient) || {})

function formatAge(p) {
  if (!p.age) return ''
  return p.age + (p.ageUnit || '岁')
}
function fmtNum(v) {
  if (v === null || v === undefined || v === '') return '—'
  return (Math.round(Number(v) * 10) / 10).toString()
}
function balanceClass(v) {
  if (v === null || v === undefined) return ''
  return Number(v) > 0 ? 'pos' : Number(v) < 0 ? 'neg' : ''
}
function shortDrug(name) {
  if (!name) return ''
  // 卡片标签去掉括号里的商品名，保留通用名
  return name.replace(/[（(].*?[)）]/g, '')
}
function fmtT(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').substring(0, 19)
}

async function loadDepartments() {
  try {
    departments.value = (await request.get('/handover/departments')) || []
  } catch (e) {
    console.error('科室加载失败', e)
  }
}

async function loadOverview() {
  loading.value = true
  try {
    const params = { departCode: selectedDepartCode.value || '' }
    if (shiftDate.value) {
      params.shiftDate = shiftDate.value
    }
    const res = await request.get('/handover/ward-overview', { params })
    overview.shiftRange = res.shiftRange
    overview.summary = res.summary || { ...ZERO_SUMMARY }
    overview.patients = res.patients || []
    // 首次加载或用户未手动选择时，用后端返回的默认区间结束日期（交班日期=第二天）填充选择器
    if (!shiftDate.value && res.shiftRange && res.shiftRange.endTime) {
      shiftDate.value = res.shiftRange.endTime.substring(0, 10)
    }
  } catch (e) {
    console.error('交班数据加载失败', e)
  } finally {
    loading.value = false
  }
}

function onShiftDateChange() {
  // 用户修改交班日期后自动刷新
  if (shiftDate.value) {
    loadOverview()
  }
}

async function openDetail(card) {
  currentCard.value = card
  detailVisible.value = true
  detail.value = null
  noteText.value = card.conditionChange || ''
  try {
    detail.value = await request.get('/handover/patient-detail', {
      params: { inHospitalNo: card.inHospitalNo, shiftDate: shiftDate.value || '' }
    })
    noteText.value = detail.value.note?.conditionChange || ''
  } catch (e) {
    console.error('患者详情加载失败', e)
  }
}

async function saveNote() {
  if (!currentDoctor.value.trim()) {
    ElMessage.warning('请先在右上角填写交班医生姓名')
    return
  }
  if (!noteText.value.trim()) {
    ElMessage.warning('病情变化内容不能为空')
    return
  }
  const p = detail.value.patient || {}
  saving.value = true
  try {
    const saved = await request.post('/handover/save-note', {
      patientId: p.patient_id,
      inHospitalNo: p.patient_no,
      patientName: p.name,
      departCode: selectedDepartCode.value,
      shiftBeginTime: overview.shiftRange.startTime,
      shiftEndTime: overview.shiftRange.endTime,
      conditionChange: noteText.value.trim(),
      createBy: currentDoctor.value.trim()
    })
    localStorage.setItem('handoverDoctor', currentDoctor.value.trim())
    ElMessage.success('病情变化已保存')
    detail.value.note = {
      id: saved.id,
      conditionChange: saved.conditionChange,
      createBy: saved.createBy,
      createTime: fmtT(saved.createTime)
    }
    await loadOverview()
  } catch (e) {
    console.error('病情变化保存失败', e)
  } finally {
    saving.value = false
  }
}

/**
 * 导入上一个班次的交班记录。
 * 追加到输入框末尾（带班次前缀），绝不覆盖本班已写内容。
 */
async function importPrevNote() {
  const p = detail.value?.patient || {}
  if (!p.patient_no) return
  importing.value = true
  try {
    const prev = await request.get('/handover/previous-note', {
      params: { inHospitalNo: p.patient_no, shiftDate: shiftDate.value || '' }
    })
    const text = prev && prev.conditionChange ? prev.conditionChange.trim() : ''
    if (!text) {
      ElMessage.info('上一个班次没有交班记录')
      return
    }
    const day = prev.shiftBeginTime ? String(prev.shiftBeginTime).substring(5, 10) : ''
    const block = (day ? `【上一班 ${day}】\n` : '【上一班】\n') + text
    noteText.value = noteText.value.trim() ? noteText.value.trim() + '\n\n' + block : block
    ElMessage.success('已追加到末尾，可直接修改')
  } catch (e) {
    console.error('导入上一班记录失败', e)
    ElMessage.warning('导入失败：' + (e?.message || '请稍后重试'))
  } finally {
    importing.value = false
  }
}

async function deleteNote() {
  if (!detail.value.note?.id) return
  try {
    await ElMessageBox.confirm('确认删除本班病情变化记录？', '提示', { type: 'warning', customClass: 'abx-overlay' })
  } catch {
    return
  }
  try {
    await request.post('/handover/delete-note', null, { params: { id: detail.value.note.id } })
    ElMessage.success('已删除')
    detail.value.note = null
    noteText.value = ''
    await loadOverview()
  } catch (e) {
    console.error('交班记录删除失败', e)
  }
}

// 跳转其他维度（当前标签页，保持外链会话）
function jumpOther(path) {
  const p = detail.value.patient || {}
  const query = { inHospitalNo: p.patient_no }
  if (path !== '/page/sepsis-bundle') {
    query.patientId = p.patient_id
  }
  router.push({ path, query })
}

onMounted(() => {
  loadDepartments()
  loadOverview()
})
</script>

<style scoped>
.handover-board { padding: 16px; background: #fafaf9; min-height: 100vh; }

.filter-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; flex-wrap: wrap; gap: 12px; }
.filter-left, .filter-right { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.shift-tag { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #44403c; background: #fff; padding: 7px 12px; border-radius: 6px; box-shadow: 0 2px 8px rgba(28,25,23,0.04); }
.shift-tag b { color: #292524; font-weight: 600; }
.doctor-label { font-size: 13px; color: #44403c; }

.summary-bar { display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 14px; }
.sum-chip { background: #fff; border-radius: 8px; padding: 10px 14px; display: flex; align-items: baseline; gap: 6px; box-shadow: 0 2px 8px rgba(28,25,23,0.04); border-left: 3px solid #78716c; }
.sum-chip.primary { border-left-color: #0d9488; }
.sum-chip.success { border-left-color: #16a34a; }
.sum-chip.warn { border-left-color: #d97706; }
.sum-chip.danger { border-left-color: #dc2626; }
.sum-chip .num { font-size: 20px; font-weight: 700; color: #292524; }
.sum-chip .lbl { font-size: 12px; color: #78716c; }

.patient-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(390px, 1fr)); gap: 12px; }
.patient-card { background: #fff; border-radius: 8px; padding: 14px; box-shadow: 0 2px 8px rgba(28,25,23,0.04); border-left: 4px solid #0d9488; cursor: pointer; transition: box-shadow .2s, transform .2s; }
.patient-card:hover { box-shadow: 0 4px 14px rgba(0,0,0,0.12); transform: translateY(-1px); }
.pc-head { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; flex-wrap: wrap; }
.bed { background: #0d9488; color: #fff; font-weight: 700; font-size: 13px; padding: 2px 8px; border-radius: 4px; }
.pname { font-size: 16px; font-weight: 600; color: #292524; }
.pmeta { font-size: 12px; color: #78716c; }
.tags { display: flex; gap: 4px; margin-left: auto; flex-wrap: wrap; }
.pc-line { font-size: 12.5px; color: #44403c; margin-bottom: 4px; }
.pc-line.dim { color: #78716c; }
.diagnosis { color: #292524; line-height: 1.4; height: 1.8em; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.vital-row { display: flex; flex-wrap: wrap; gap: 10px; margin: 8px 0 6px; padding: 8px; background: #fafaf9; border-radius: 6px; }
.vital { font-size: 12px; color: #78716c; }
.vital b { color: #292524; font-size: 13.5px; font-weight: 600; margin-left: 2px; }
.vital.hot b, .danger-text { color: #dc2626; }

.support-row { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 6px; min-height: 24px; }
.io-row { display: flex; flex-wrap: wrap; gap: 14px; font-size: 12.5px; color: #44403c; margin-bottom: 6px; }
.io-row b { color: #292524; font-weight: 600; }
.io-row .pos { color: #d97706; }
.io-row .neg { color: #0d9488; }

.lab-row { display: flex; align-items: center; gap: 6px; font-size: 12.5px; margin-bottom: 6px; flex-wrap: wrap; }
.lab-names { color: #78716c; font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 100%; }

.note-row { display: flex; align-items: center; gap: 6px; font-size: 12.5px; padding: 7px 8px; border-radius: 6px; background: #fafaf9; border: 1px dashed #e7e5e4; color: #78716c; }
.note-row.filled { background: #f0fdf4; border-color: #bbf7d0; border-style: solid; color: #292524; }
.note-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.note-by { color: #16a34a; font-size: 12px; white-space: nowrap; }
.note-empty { color: #0d9488; }

.detail-wrap { padding: 0 20px 24px; }
.d-patient { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; margin-bottom: 10px; }
.d-bed { background: #0d9488; color: #fff; font-weight: 700; padding: 3px 10px; border-radius: 4px; margin-right: 8px; }
.d-name { font-size: 18px; font-weight: 600; margin-right: 10px; }
.d-meta { color: #78716c; font-size: 13px; }
.d-jump { display: flex; gap: 8px; }
.d-info-line { display: flex; flex-wrap: wrap; gap: 18px; font-size: 13px; color: #44403c; background: #fafaf9; padding: 8px 12px; border-radius: 6px; margin-bottom: 14px; }

.d-section { background: #fff; border: 1px solid #e7e5e4; border-radius: 8px; padding: 14px; margin-bottom: 12px; }
.block-title { display: flex; align-items: center; font-size: 15px; font-weight: 600; color: #292524; margin-bottom: 10px; }
.block-title .dot { width: 4px; height: 16px; background: #0d9488; border-radius: 2px; margin-right: 8px; }
.note-actions { display: flex; justify-content: space-between; align-items: center; margin-top: 10px; flex-wrap: wrap; gap: 8px; }
.note-meta { font-size: 12px; color: #78716c; }
.io-summary {
  background: #fafaf9;
  border-radius: 6px;
  padding: 10px 14px;
  margin-bottom: 4px;
}
.io-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}
.io-label {
  color: #44403c;
  font-size: 13px;
}
.io-value {
  font-size: 14px;
  font-weight: 600;
  color: #292524;
}
.io-value.intake { color: #0d9488; }
.io-value.output { color: #dc2626; }
.catheter-tag {
  display: inline-block;
  margin-left: 4px;
  padding: 0 4px;
  background: #d97706;
  color: #fff;
  border-radius: 3px;
  font-size: 11px;
  font-weight: normal;
}
.io-items {
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed #e7e5e4;
}
.io-items-title {
  color: #44403c;
  font-size: 12px;
  margin-bottom: 6px;
}
.io-items-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.io-item-tag {
  display: inline-block;
  padding: 2px 8px;
  background: #fff;
  border: 1px solid #e7e5e4;
  border-radius: 4px;
  font-size: 12px;
  color: #44403c;
}
.d-sub { font-size: 13px; color: #44403c; font-weight: 600; margin: 8px 0 4px; }
.tag-box { display: flex; flex-wrap: wrap; }
.dim { color: #a8a29e; font-size: 13px; }

@media (max-width: 900px) {
  .patient-grid { grid-template-columns: 1fr; }
}
</style>
