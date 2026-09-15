<template>
  <div class="sepsis-page abx-theme">
    <div v-if="!inHospitalNo" class="warn-bar">
      <el-alert type="warning" :closable="false" show-icon
                title="缺少患者参数"
                description="本页面需通过外链（携带 inHospitalNo）进入" />
    </div>

    <div v-else v-loading="loading" class="page-body">
      <!-- 患者信息横条（含内嵌进度条） -->
      <div class="card patient-bar">
        <div class="p-cell"><label>姓名</label><b>{{ data.patientName || '—' }}</b></div>
        <div class="p-cell"><label>住院号</label><b>{{ data.inHospitalNo || '—' }}</b></div>
        <div class="p-cell"><label>年龄 / 性别</label><b>{{ data.age || '—' }} / {{ data.gender || '—' }}</b></div>
        <div class="p-cell"><label>床位</label><b>{{ data.bedNo || '—' }}</b></div>
        <div class="p-cell-group">
          <div class="p-cell">
            <label>休克类型</label>
            <b :class="{ 'danger-text': data.shockType }">{{ data.shockType || '—' }}</b>
          </div>
          <div class="p-cell">
            <label>入科时间</label>
            <b>{{ fmtTime(data.inDepartTime) }}</b>
          </div>
          <div class="p-cell">
            <label>出科时间</label>
            <b>{{ data.outDepartTime ? fmtTime(data.outDepartTime) : '在科中' }}</b>
          </div>
          <div class="p-cell">
            <label>确诊时间</label>
            <el-date-picker
              v-model="diagnosisTime"
              type="datetime"
              placeholder="选择确诊时间"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DD HH:mm:ss"
              size="small"
              style="width: 170px"
              popper-class="abx-popper"
            />
          </div>
        </div>
        <div class="p-cell"><label>感染部位</label><b>{{ infectionSiteText || '—' }}</b></div>

        <!-- 集束化进度条（感染部位右侧空白区） -->
        <div class="p-progress-area">
          <div class="progress-inline">
            <div class="step-node" :class="{ done: data.bundle1hCompleted === 1 }">
              <div class="step-circle" :class="{ done: data.bundle1hCompleted === 1 }">
                1H
                <span class="mini-check" v-if="data.bundle1hCompleted === 1">
                  <svg viewBox="0 0 10 10"><polyline points="1.5 5.5 4 8 8.5 2" fill="none" stroke-width="2"/></svg>
                </span>
              </div>
              <span class="step-name">{{ data.bundle1hCompleted === 1 ? '已完成' : '待完成' }}</span>
            </div>
            <div class="step-line" :class="{ filled: data.bundle3hCompleted === 1 }"><span class="fill"></span></div>
            <div class="step-node" :class="{ done: data.bundle3hCompleted === 1, active: data.bundle3hCompleted !== 1 && data.bundle1hCompleted === 1 }">
              <div class="step-circle" :class="{ done: data.bundle3hCompleted === 1, active: data.bundle3hCompleted !== 1 && data.bundle1hCompleted === 1 }">
                3H
                <span class="mini-check" v-if="data.bundle3hCompleted === 1">
                  <svg viewBox="0 0 10 10"><polyline points="1.5 5.5 4 8 8.5 2" fill="none" stroke-width="2"/></svg>
                </span>
              </div>
              <span class="step-name">{{ data.bundle3hCompleted === 1 ? '已完成' : '待完成' }}</span>
            </div>
            <div class="step-line" :class="{ filled: data.bundle6hCompleted === 1 }"><span class="fill"></span></div>
            <div class="step-node" :class="{ done: data.bundle6hCompleted === 1, active: data.bundle6hCompleted !== 1 && data.bundle3hCompleted === 1 }">
              <div class="step-circle" :class="{ done: data.bundle6hCompleted === 1, active: data.bundle6hCompleted !== 1 && data.bundle3hCompleted === 1 }">
                6H
                <span class="mini-check" v-if="data.bundle6hCompleted === 1">
                  <svg viewBox="0 0 10 10"><polyline points="1.5 5.5 4 8 8.5 2" fill="none" stroke-width="2"/></svg>
                </span>
              </div>
              <span class="step-name">{{ data.bundle6hCompleted === 1 ? '已完成' : '待完成' }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 评估切换栏 -->
      <div class="card assess-bar">
        <div class="assess-left">
          <span class="assess-label">评估记录</span>
          <el-select v-model="currentAssessId" placeholder="选择评估记录" style="width: 300px" popper-class="abx-popper" @change="onAssessChange">
            <el-option label="新建评估" :value="null" />
            <el-option
              v-for="(item, index) in historyList"
              :key="item.id"
              :label="`第${historyList.length - index}次评估 - ${fmtTime(item.createTime)}${currentAssessId === item.id ? '（当前）' : ''}`"
              :value="item.id"
            />
          </el-select>
          <button class="btn btn-primary" @click="onNewAssess">
            <svg viewBox="0 0 24 24"><path d="M12 5v14M5 12h14"/></svg>
            新建评估
          </button>
        </div>
        <div class="assess-right">
          <span class="assess-count">共 <strong>{{ historyList.length }}</strong> 次评估</span>
          <button v-if="currentAssessId" class="btn btn-danger-ghost" @click="onDeleteAssess" title="删除当前评估记录">
            <svg viewBox="0 0 24 24"><path d="M3 6h18M8 6V4h8v2M19 6l-1 14H6L5 6M10 11v6M14 11v6"/></svg>
            删除评估
          </button>
          <button class="btn btn-ghost" @click="goWordConfig" title="抗菌药物识别词库配置">
            <svg viewBox="0 0 24 24"><path d="M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6"/></svg>
            词库配置
          </button>
        </div>
      </div>

      <!-- 三个时间节点卡片 -->
      <div class="bundle-grid">
        <!-- 1H 卡片 -->
        <div class="bundle-card" :class="data.bundle1hCompleted === 1 ? 'completed' : 'pending'">
          <div class="bundle-card-header">
            <div class="bundle-card-title">
              <div class="time-badge h1">1H</div>
              <div>
                <div class="bundle-name">1小时集束化</div>
                <div class="bundle-sub">确诊后1小时内完成</div>
              </div>
            </div>
            <span class="status-tag" :class="data.bundle1hCompleted === 1 ? 'done' : 'pending'">
              {{ data.bundle1hCompleted === 1 ? '已完成' : '未完成' }}
            </span>
          </div>
          <div class="bundle-card-body">
            <div class="bundle-item" :class="{ done: item.completed }" v-for="(item, index) in bundle1hItems" :key="index">
              <div class="item-row">
                <input type="checkbox" class="item-check" :checked="item.completed" @change="onBundle1hItemChange(index, $event.target.checked)">
                <span class="item-name">{{ item.name }}</span>
              </div>
              <div class="item-detail" v-if="item.detail">
                <span class="detail-tag" :class="{ warn: item.warn }">{{ item.detail }}</span>
              </div>
            </div>
          </div>
          <div class="bundle-card-footer">
            <label class="all-check">
              <input type="checkbox" :checked="data.bundle1hCompleted === 1" @change="onBundle1hAllChange($event.target.checked)">
              标记全部完成
            </label>
            <span class="progress-text"><strong>{{ bundle1hDoneCount }}/{{ bundle1hItems.length }}</strong> 项</span>
          </div>
        </div>

        <!-- 3H 卡片 -->
        <div class="bundle-card" :class="data.bundle3hCompleted === 1 ? 'completed' : 'pending'">
          <div class="bundle-card-header">
            <div class="bundle-card-title">
              <div class="time-badge h3">3H</div>
              <div>
                <div class="bundle-name">3小时集束化</div>
                <div class="bundle-sub">确诊后3小时内完成</div>
              </div>
            </div>
            <span class="status-tag" :class="data.bundle3hCompleted === 1 ? 'done' : 'pending'">
              {{ data.bundle3hCompleted === 1 ? '已完成' : '未完成' }}
            </span>
          </div>
          <div class="bundle-card-body">
            <div class="bundle-item" :class="{ done: item.completed }" v-for="(item, index) in bundle3hItems" :key="index">
              <div class="item-row">
                <input type="checkbox" class="item-check" :checked="item.completed" @change="onBundle3hItemChange(index, $event.target.checked)">
                <span class="item-name">{{ item.name }}</span>
              </div>
              <div class="item-detail" v-if="item.detail">
                <span class="detail-tag" :class="{ warn: item.warn }">{{ item.detail }}</span>
              </div>
            </div>
          </div>
          <!-- 3小时后评估 -->
          <div class="assessment-box" v-if="hasAssessmentData">
            <div class="assessment-title">📊 3小时后评估</div>
            <div class="assessment-grid">
              <div class="assess-item">
                <div class="assess-label">CVP</div>
                <div class="assess-value">{{ data.bundle3h.cvp || '—' }}<small>mmHg</small></div>
              </div>
              <div class="assess-item">
                <div class="assess-label">MAP</div>
                <div class="assess-value">{{ data.bundle3h.map || '—' }}<small>mmHg</small></div>
              </div>
              <div class="assess-item">
                <div class="assess-label">去甲肾上腺素</div>
                <div class="assess-value">{{ data.bundle3h.norepiDose || '—' }}</div>
              </div>
              <div class="assess-item">
                <div class="assess-label">乳酸</div>
                <div class="assess-value">{{ data.bundle3h.lactate3h || '—' }}<small>mmol/L</small></div>
              </div>
              <div class="assess-item">
                <div class="assess-label">ScvO₂</div>
                <div class="assess-value">{{ data.bundle3h.scvo2 || '—' }}<small>%</small></div>
              </div>
              <div class="assess-item">
                <div class="assess-label">尿量</div>
                <div class="assess-value">{{ data.bundle3h.urineOutput || '—' }}<small>ml</small></div>
              </div>
            </div>
          </div>
          <div class="bundle-card-footer">
            <label class="all-check">
              <input type="checkbox" :checked="data.bundle3hCompleted === 1" @change="onBundle3hAllChange($event.target.checked)">
              标记全部完成
            </label>
            <span class="progress-text"><strong>{{ bundle3hDoneCount }}/{{ bundle3hItems.length }}</strong> 项</span>
          </div>
        </div>

        <!-- 6H 卡片 -->
        <div class="bundle-card" :class="data.bundle6hCompleted === 1 ? 'completed' : 'pending'">
          <div class="bundle-card-header">
            <div class="bundle-card-title">
              <div class="time-badge h6">6H</div>
              <div>
                <div class="bundle-name">6小时集束化</div>
                <div class="bundle-sub">确诊后6小时内完成</div>
              </div>
            </div>
            <span class="status-tag" :class="data.bundle6hCompleted === 1 ? 'done' : 'pending'">
              {{ data.bundle6hCompleted === 1 ? '已完成' : '未完成' }}
            </span>
          </div>
          <div class="bundle-card-body">
            <div class="bundle-item" :class="{ done: item.completed }" v-for="(item, index) in bundle6hItems" :key="index">
              <div class="item-row">
                <input type="checkbox" class="item-check" :checked="item.completed" @change="onBundle6hItemChange(index, $event.target.checked)">
                <span class="item-name">{{ item.name }}</span>
              </div>
              <div class="item-detail" v-if="item.detail">
                <span class="detail-tag" :class="{ warn: item.warn }">{{ item.detail }}</span>
              </div>
            </div>
          </div>
          <div class="bundle-card-footer">
            <label class="all-check">
              <input type="checkbox" :checked="data.bundle6hCompleted === 1" @change="onBundle6hAllChange($event.target.checked)">
              标记全部完成
            </label>
            <span class="progress-text"><strong>{{ bundle6hDoneCount }}/{{ bundle6hItems.length }}</strong> 项</span>
          </div>
        </div>
      </div>

      <!-- 底部信息区 -->
      <div class="bottom-grid">
        <!-- 感染相关信息 -->
        <div class="card">
          <div class="block-title"><span class="dot dot-cyan"></span>感染相关信息</div>
          <div class="info-grid">
            <div class="info-block">
              <div class="info-label">感染部位</div>
              <div class="value-tags" v-if="infectionSiteList.length">
                <span class="value-tag" v-for="(v, i) in infectionSiteList" :key="i">{{ v }}</span>
              </div>
              <div class="info-value" v-else>—</div>
            </div>
            <div class="info-block">
              <div class="info-label">致病菌</div>
              <div class="value-tags" v-if="pathogenList.length">
                <span class="value-tag" v-for="(v, i) in pathogenList" :key="i">{{ v }}</span>
              </div>
              <div class="info-value" v-else>—</div>
            </div>
            <div class="info-block">
              <div class="info-label">抗菌药物</div>
              <div class="value-tags" v-if="antibioticList.length">
                <span class="value-tag" v-for="(v, i) in antibioticList" :key="i">{{ v }}</span>
              </div>
              <div class="info-value" v-else>—</div>
            </div>
          </div>
        </div>

        <!-- 液体复苏未达原因 -->
        <div class="card">
          <div class="block-title"><span class="dot dot-orange"></span>液体复苏未达30ml/kg原因</div>
          <div class="fluid-reason-list" v-if="data.fluidReason">
            <div class="reason-item" :class="{ checked: data.fluidReason.volumeOverload }">
              <input type="checkbox" v-model="data.fluidReason.volumeOverload">
              <span>存在容量过负荷（肺水肿/急性左心衰）</span>
            </div>
            <div class="reason-item" :class="{ checked: data.fluidReason.organInjury }">
              <input type="checkbox" v-model="data.fluidReason.organInjury">
              <span>存在限制性液体复苏的器官损伤（AKI/ARDS等）</span>
            </div>
            <div class="reason-item" :class="{ checked: data.fluidReason.capillaryLeak }">
              <input type="checkbox" v-model="data.fluidReason.capillaryLeak">
              <span>严重毛细血管渗漏</span>
            </div>
            <div class="reason-other">
              <span class="other-label">其他：</span>
              <input type="text" class="other-input" v-model="data.fluidReason.other" placeholder="请输入其他原因">
            </div>
          </div>
          <div class="no-reason" v-else>暂无液体复苏未达原因记录</div>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="action-bar">
        <button class="btn btn-success" @click="onSave">
          <svg viewBox="0 0 24 24"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"/><polyline points="17 21 17 13 7 13 7 21"/><polyline points="7 3 7 8 15 8"/></svg>
          保存评估
        </button>
        <button class="btn" @click="onRefresh">
          <svg viewBox="0 0 24 24"><polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"/></svg>
          重新评估
        </button>
        <button class="btn" @click="onBack">
          <svg viewBox="0 0 24 24"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>
          返回
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '../api/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import '../styles/abx-theme.css'

const route = useRoute()
const router = useRouter()

const inHospitalNo = computed(() => route.query.inHospitalNo || route.params.inHospitalNo || '')

const loading = ref(false)
const diagnosisTime = ref('')
const historyList = ref([])
const currentAssessId = ref(null)

const data = reactive({
  id: null,
  patientId: '',
  inHospitalNo: '',
  patientName: '',
  departCode: '',
  departName: '',
  bedNo: '',
  gender: '',
  age: '',
  shockType: '',
  diagnosisTime: '',
  inDepartTime: '',
  outDepartTime: '',
  bundle1hCompleted: 0,
  bundle3hCompleted: 0,
  bundle6hCompleted: 0,
  bundle1h: {
    lactateMeasured: false, lactateMonitor: false, lactateValue: '', lactateTime: '', lactateMonitorCount: 0,
    bloodCultureBeforeAntibiotic: false, bloodCultureTime: '', antibioticStartTime: '',
    broadSpectrumAntibiotic: false, antibioticName: '',
    fluidResuscitation: false, fluidAmount: 0, fluidTarget: 0, weight: 0, hypotensionOrLactateHigh: false,
    norepinephrine: false, norepinephrineDose: ''
  },
  bundle3h: {
    lactateMeasured: false, lactate3h: '',
    bloodCultureBeforeAntibiotic: false,
    broadSpectrumAntibiotic: false,
    fluidResuscitation: false, fluidAmount: 0, fluidTarget: 0,
    cvp: '', map: '', norepiDose: '', scvo2: '', urineOutput: ''
  },
  bundle6h: { vasopressor: false, reassessVolume: false, repeatLactate: false },
  infectionSite: '',
  pathogen: '',
  antibiotic: '',
  fluidReason: { volumeOverload: false, organInjury: false, capillaryLeak: false, other: '' }
})

/* ===== 多值拆分（感染相关信息） ===== */
function splitMulti(str) {
  if (!str) return []
  return str.split(/[,，;；、\n]/).map(s => s.trim()).filter(s => s && s !== '—' && s !== '-')
}
const infectionSiteList = computed(() => splitMulti(data.infectionSite))
const pathogenList = computed(() => splitMulti(data.pathogen))
const antibioticList = computed(() => splitMulti(data.antibiotic))
const infectionSiteText = computed(() => infectionSiteList.value[0] || '')

/* ===== 1H项目列表（乳酸测量+动态监测合并为一项） ===== */
const bundle1hItems = computed(() => {
  const b1 = data.bundle1h || {}
  const lactateDetail = []
  if (b1.lactateValue) lactateDetail.push(`乳酸：${b1.lactateValue} mmol/L`)
  if (b1.lactateTime) lactateDetail.push(`时间：${fmtTime(b1.lactateTime)}`)
  if (b1.lactateMonitor && b1.lactateMonitorCount) lactateDetail.push(`已监测 ${b1.lactateMonitorCount} 次`)

  const bcDetail = []
  if (b1.bloodCultureTime) bcDetail.push(`血培养：${fmtTime(b1.bloodCultureTime)}`)
  if (b1.antibioticStartTime) bcDetail.push(`用药：${fmtTime(b1.antibioticStartTime)}`)

  const fluidDetail = []
  if (b1.fluidAmount !== undefined && b1.fluidTarget) fluidDetail.push(`液体：${b1.fluidAmount || 0}/${b1.fluidTarget}ml`)
  if (b1.weight) fluidDetail.push(`体重：${b1.weight}kg`)

  return [
    {
      name: '测量乳酸水平，若初始>2.0mmol/L需动态监测',
      completed: !!(b1.lactateMeasured),
      detail: lactateDetail.join('，')
    },
    {
      name: '抗菌药物前获取血培养',
      completed: !!(b1.bloodCultureBeforeAntibiotic),
      detail: bcDetail.join('，')
    },
    {
      name: '应用广谱抗菌药物',
      completed: !!(b1.broadSpectrumAntibiotic),
      detail: b1.antibioticName ? `药物：${b1.antibioticName}` : ''
    },
    {
      name: '低血压/乳酸≥4予30ml/kg晶体液',
      completed: !!(b1.fluidResuscitation),
      detail: fluidDetail.join('，')
    },
    {
      name: '液体复苏后去甲肾上腺素维持MAP>65',
      completed: !!(b1.norepinephrine),
      detail: b1.norepinephrineDose ? `剂量：${b1.norepinephrineDose}` : ''
    }
  ]
})

/* ===== 3H项目列表 ===== */
const bundle3hItems = computed(() => {
  const b3 = data.bundle3h || {}
  const fluidDetail = []
  if (b3.fluidAmount !== undefined && b3.fluidTarget) fluidDetail.push(`液体：${b3.fluidAmount || 0}/${b3.fluidTarget}ml`)
  return [
    {
      name: '监测血乳酸水平',
      completed: !!(b3.lactateMeasured),
      detail: b3.lactate3h ? `3H后乳酸：${b3.lactate3h} mmol/L` : ''
    },
    {
      name: '抗生素前提取血培养标本',
      completed: !!(b3.bloodCultureBeforeAntibiotic),
      detail: ''
    },
    {
      name: '使用广谱抗生素',
      completed: !!(b3.broadSpectrumAntibiotic),
      detail: ''
    },
    {
      name: '30ml/kg晶体液复苏',
      completed: !!(b3.fluidResuscitation),
      detail: fluidDetail.join('，')
    }
  ]
})

/* ===== 6H项目列表 ===== */
const bundle6hItems = computed(() => {
  const b6 = data.bundle6h || {}
  const vpDetail = b6.norepiDose ? `药物：${b6.norepiDose}` : ''
  const rvDetail = b6.lactateMonitorCount ? `乳酸复查${b6.lactateMonitorCount}次` : ''
  const rlDetail = b6.lactate6h ? `6H后乳酸：${b6.lactate6h} mmol/L` : ''
  return [
    {
      name: '血管升压药维持MAP≥65mmHg',
      completed: !!(b6.vasopressor),
      detail: vpDetail
    },
    {
      name: '重复评估容量状态和组织灌注',
      completed: !!(b6.reassessVolume),
      detail: rvDetail
    },
    {
      name: '早期乳酸升高时重复测量',
      completed: !!(b6.repeatLactate),
      detail: rlDetail
    }
  ]
})

/* ===== 完成计数 ===== */
const bundle1hDoneCount = computed(() => bundle1hItems.value.filter(i => i.completed).length)
const bundle3hDoneCount = computed(() => bundle3hItems.value.filter(i => i.completed).length)
const bundle6hDoneCount = computed(() => bundle6hItems.value.filter(i => i.completed).length)

/* ===== 3小时后评估是否有数据 ===== */
const hasAssessmentData = computed(() => {
  const b3 = data.bundle3h || {}
  return b3.cvp || b3.map || b3.norepiDose || b3.lactate3h || b3.scvo2 || b3.urineOutput
})

/* ===== 时间格式化 ===== */
function fmtTime(t) {
  if (!t) return '—'
  const s = String(t).replace('T', ' ')
  return s.length > 16 ? s.substring(0, 16) : s
}

/* ===== 保存前标准化时间为 yyyy-MM-dd HH:mm:ss ===== */
function normTime(t) {
  if (!t) return null
  let s = String(t).replace('T', ' ').replace(/\.\d+$/, '').trim()
  if (s.length === 16) s = s + ':00'
  return s
}

/* ===== 加载历史记录 ===== */
async function loadHistory() {
  if (!inHospitalNo.value) return
  try {
    const res = await request.get('/sepsis/bundle/history', { params: { inHospitalNo: inHospitalNo.value } })
    historyList.value = res || []
  } catch (e) {
    console.error('加载历史记录失败', e)
  }
}

/* ===== 加载数据 ===== */
async function loadData(assessId = null) {
  if (!inHospitalNo.value) return
  loading.value = true
  try {
    let res
    if (assessId) {
      res = await request.get(`/sepsis/bundle/detail/${assessId}`)
    } else {
      res = await request.get('/sepsis/bundle/detail', { params: { inHospitalNo: inHospitalNo.value } })
    }
    if (res) {
      // 合并返回数据，但保留 bundle1h/3h/6h 对象引用
      Object.assign(data, res)
      // 首次加载（未指定评估ID）时，若有最新记录则下拉框定位到它
      if (!assessId && res.id) {
        currentAssessId.value = res.id
      }
      if (!data.bundle1h) {
        data.bundle1h = { lactateMeasured: false, lactateMonitor: false, lactateValue: '', lactateTime: '', lactateMonitorCount: 0, bloodCultureBeforeAntibiotic: false, bloodCultureTime: '', antibioticStartTime: '', broadSpectrumAntibiotic: false, antibioticName: '', fluidResuscitation: false, fluidAmount: 0, fluidTarget: 0, weight: 0, hypotensionOrLactateHigh: false, norepinephrine: false, norepinephrineDose: '' }
      }
      if (!data.bundle3h) {
        data.bundle3h = { lactateMeasured: false, lactate3h: '', bloodCultureBeforeAntibiotic: false, broadSpectrumAntibiotic: false, fluidResuscitation: false, fluidAmount: 0, fluidTarget: 0, cvp: '', map: '', norepiDose: '', scvo2: '', urineOutput: '' }
      }
      if (!data.bundle6h) {
        data.bundle6h = { vasopressor: false, reassessVolume: false, repeatLactate: false, lactate6h: '', lactateMonitorCount: 0, norepiDose: '' }
      }
      if (!data.fluidReason) {
        data.fluidReason = { volumeOverload: false, organInjury: false, capillaryLeak: false, other: '' }
      }
      diagnosisTime.value = res.diagnosisTime || ''
    }
  } catch (e) {
    console.error('加载脓毒症集束化治疗数据失败', e)
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

/* ===== 评估记录切换 ===== */
function onAssessChange(val) {
  if (val) {
    loadData(val)
  } else {
    resetForNewAssess()
  }
}

function onNewAssess() {
  currentAssessId.value = null
  resetForNewAssess()
  ElMessage.success('已切换到新建评估模式')
}

function goWordConfig() {
  router.push({ path: '/page/abx-word-config', query: { inHospitalNo: inHospitalNo.value } })
}

/* ===== 删除评估记录 ===== */
async function onDeleteAssess() {
  try {
    await ElMessageBox.confirm('确定删除当前评估记录吗？删除后不可恢复。', '删除评估', { type: 'warning', customClass: 'abx-overlay' })
  } catch {
    return
  }
  try {
    await request.post('/sepsis/bundle/delete', null, { params: { id: currentAssessId.value } })
    ElMessage.success('删除成功')
    currentAssessId.value = null
    resetForNewAssess()
    loadHistory()
    loadData(null)
  } catch (e) {
    console.warn('删除失败: ', e.message || e)
  }
}

function resetForNewAssess() {
  // 保留患者基本信息（姓名/住院号/床号/性别/年龄/休克类型/入科出科/诊断时间/感染部位等），
  // 仅清空评估项目数据，确保保存时走 save（新增记录）而不是 update
  data.id = null
  data.bundle1hCompleted = 0
  data.bundle3hCompleted = 0
  data.bundle6hCompleted = 0
  data.bundle1h = { lactateMeasured: false, lactateMonitor: false, lactateValue: '', lactateTime: '', lactateMonitorCount: 0, bloodCultureBeforeAntibiotic: false, bloodCultureTime: '', antibioticStartTime: '', broadSpectrumAntibiotic: false, antibioticName: '', fluidResuscitation: false, fluidAmount: 0, fluidTarget: 0, weight: 0, hypotensionOrLactateHigh: false, norepinephrine: false, norepinephrineDose: '' }
  data.bundle3h = { lactateMeasured: false, lactate3h: '', bloodCultureBeforeAntibiotic: false, broadSpectrumAntibiotic: false, fluidResuscitation: false, fluidAmount: 0, fluidTarget: 0, cvp: '', map: '', norepiDose: '', scvo2: '', urineOutput: '' }
  data.bundle6h = { vasopressor: false, reassessVolume: false, repeatLactate: false, lactate6h: '', lactateMonitorCount: 0, norepiDose: '' }
  data.fluidReason = { volumeOverload: false, organInjury: false, capillaryLeak: false, other: '' }
  if (!data.inHospitalNo) data.inHospitalNo = inHospitalNo.value
  if (!data.patientName) data.patientName = ''
  // 不重新加载最新记录，避免覆盖空模板
}

/* ===== 单个项目变更 ===== */
function onBundle1hItemChange(index, val) {
  // index: 0=乳酸(合并) 1=血培养 2=广谱抗生素 3=液体复苏 4=去甲肾上腺素
  const b1 = data.bundle1h
  if (index === 0) {
    b1.lactateMeasured = val
    if (val && !b1.lactateMonitor) b1.lactateMonitor = true
  } else if (index === 1) {
    b1.bloodCultureBeforeAntibiotic = val
  } else if (index === 2) {
    b1.broadSpectrumAntibiotic = val
  } else if (index === 3) {
    b1.fluidResuscitation = val
  } else if (index === 4) {
    b1.norepinephrine = val
  }
  syncCompletedFlags()
}

function onBundle3hItemChange(index, val) {
  const b3 = data.bundle3h
  if (index === 0) b3.lactateMeasured = val
  else if (index === 1) b3.bloodCultureBeforeAntibiotic = val
  else if (index === 2) b3.broadSpectrumAntibiotic = val
  else if (index === 3) b3.fluidResuscitation = val
  syncCompletedFlags()
}

function onBundle6hItemChange(index, val) {
  const b6 = data.bundle6h
  if (index === 0) b6.vasopressor = val
  else if (index === 1) b6.reassessVolume = val
  else if (index === 2) b6.repeatLactate = val
  syncCompletedFlags()
}

/* ===== 同步整体完成标记 ===== */
function syncCompletedFlags() {
  const b1 = data.bundle1h
  const all1 = b1.lactateMeasured && b1.bloodCultureBeforeAntibiotic && b1.broadSpectrumAntibiotic && b1.fluidResuscitation && b1.norepinephrine
  data.bundle1hCompleted = all1 ? 1 : 0

  const b3 = data.bundle3h
  const all3 = b3.lactateMeasured && b3.bloodCultureBeforeAntibiotic && b3.broadSpectrumAntibiotic && b3.fluidResuscitation
  data.bundle3hCompleted = all3 ? 1 : 0

  const b6 = data.bundle6h
  const all6 = b6.vasopressor && b6.reassessVolume && b6.repeatLactate
  data.bundle6hCompleted = all6 ? 1 : 0
}

/* ===== 全部完成 ===== */
function onBundle1hAllChange(val) {
  const b1 = data.bundle1h
  b1.lactateMeasured = val
  b1.lactateMonitor = val
  b1.bloodCultureBeforeAntibiotic = val
  b1.broadSpectrumAntibiotic = val
  b1.fluidResuscitation = val
  b1.norepinephrine = val
  data.bundle1hCompleted = val ? 1 : 0
}

function onBundle3hAllChange(val) {
  const b3 = data.bundle3h
  b3.lactateMeasured = val
  b3.bloodCultureBeforeAntibiotic = val
  b3.broadSpectrumAntibiotic = val
  b3.fluidResuscitation = val
  data.bundle3hCompleted = val ? 1 : 0
}

function onBundle6hAllChange(val) {
  const b6 = data.bundle6h
  b6.vasopressor = val
  b6.reassessVolume = val
  b6.repeatLactate = val
  data.bundle6hCompleted = val ? 1 : 0
}

/* ===== 保存 ===== */
async function onSave() {
  try {
    const payload = {
      id: data.id,
      patientId: data.patientId,
      inHospitalNo: data.inHospitalNo,
      patientName: data.patientName,
      departCode: data.departCode,
      diagnosisTime: normTime(diagnosisTime.value),
      inDepartTime: normTime(data.inDepartTime),
      bundle1hCompleted: data.bundle1hCompleted,
      bundle3hCompleted: data.bundle3hCompleted,
      bundle6hCompleted: data.bundle6hCompleted,
      bundle1hData: JSON.stringify(data.bundle1h),
      bundle3hData: JSON.stringify(data.bundle3h),
      bundle6hData: JSON.stringify(data.bundle6h),
      infectionSite: data.infectionSite,
      pathogen: data.pathogen,
      antibiotic: data.antibiotic,
      fluidReason: data.fluidReason ? JSON.stringify(data.fluidReason) : null
    }
    let savedId = data.id
    if (data.id) {
      await request.post('/sepsis/bundle/update', payload)
    } else {
      const saved = await request.post('/sepsis/bundle/save', payload)
      if (saved && saved.id) savedId = saved.id
    }
    ElMessage.success('保存成功')
    await loadHistory()
    // 保存后刷新当前视图并定位到当前记录
    if (savedId) {
      data.id = savedId
      currentAssessId.value = savedId
      loadData(savedId)
    } else {
      loadData(null)
    }
  } catch (e) {
    console.error('保存失败', e)
    ElMessage.error('保存失败')
  }
}

function onRefresh() {
  loadData(currentAssessId.value)
  ElMessage.success('已重新评估')
}

function onBack() {
  router.back()
}

watch(() => route.query.inHospitalNo, () => {
  loadHistory()
  loadData(null)
})

onMounted(() => {
  loadHistory()
  loadData(null)
})
</script>

<style scoped>
.sepsis-page {
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
  margin-bottom: 16px;
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
.dot-cyan { background: #17a2b8; }
.dot-orange { background: #d97706; }

/* 患者信息横条 */
.patient-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  padding: 12px 0;
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
.p-cell-group {
  display: flex;
  align-items: stretch;
  background: #fafaf9;
  border-radius: 8px;
  padding: 4px 8px;
  margin-right: 16px;
}
.p-cell-group .p-cell {
  border-right: 1px solid #e2e8f0;
  padding: 4px 12px;
  min-width: 88px;
}
.p-cell-group .p-cell:last-child { border-right: none; }
.p-cell label { font-size: 12px; color: #8a94a3; }
.p-cell b { font-size: 14px; color: #44403c; font-weight: 600; }
.danger-text { color: #dc2626; font-weight: 600; }

/* 内嵌进度条（左对齐，紧跟感染部位） */
.p-progress-area {
  flex: 1 1 auto;
  min-width: 380px;
  padding: 0 8px 0 24px;
  display: flex;
  align-items: center;
}
.progress-inline {
  display: flex;
  align-items: center;
  width: 100%;
  max-width: 460px;
  margin: 0;
}
.step-node {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 3px;
  flex-shrink: 0;
  position: relative;
  z-index: 2;
}
.step-circle {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: #78716c;
  border: 2px solid #d3d7de;
  background: #fafaf9;
  position: relative;
}
.step-circle.done {
  background: #16a34a;
  border-color: #16a34a;
  color: #fff;
}
.step-circle.active {
  background: #d97706;
  border-color: #d97706;
  color: #fff;
  animation: pulse 2s infinite;
}
@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 2px rgba(230, 162, 60, 0.2); }
  50% { box-shadow: 0 0 0 6px rgba(230, 162, 60, 0.3); }
}
.step-circle .mini-check {
  position: absolute;
  right: -4px;
  bottom: -2px;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #fff;
  border: 1.5px solid #16a34a;
  display: flex;
  align-items: center;
  justify-content: center;
}
.step-circle .mini-check svg {
  width: 8px;
  height: 8px;
  stroke: #16a34a;
  stroke-width: 3;
  fill: none;
}
.step-name {
  font-size: 11px;
  color: #44403c;
  font-weight: 600;
}
.step-node.done .step-name { color: #16a34a; }
.step-node.active .step-name { color: #d97706; }
.step-line {
  flex: 1;
  height: 3px;
  border-radius: 2px;
  background: #d6d3d1;
  margin: 0 6px;
  margin-bottom: 17px;
  position: relative;
  overflow: hidden;
  min-width: 20px;
}
.step-line .fill {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 0;
  background: linear-gradient(90deg, #16a34a, #85ce61);
  border-radius: 2px;
}
.step-line.filled .fill { width: 100%; }

/* 评估切换栏 */
.assess-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 18px;
}
.assess-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.assess-label {
  font-size: 14px;
  font-weight: 600;
  color: #292524;
}
.btn {
  padding: 8px 16px;
  border: 1px solid #e7e5e4;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  background: #fff;
  color: #44403c;
  transition: all 0.2s;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.btn:hover {
  color: #0f766e;
  border-color: #99f6e4;
  background: #f0fdfa;
}
.btn-primary {
  background: #0d9488;
  border-color: #0d9488;
  color: #fff;
}
.btn-primary:hover {
  background: #115e59;
  border-color: #115e59;
  color: #fff;
}
.btn-ghost {
  background: #fff;
  border-color: #99f6e4;
  color: #0f766e;
}
.btn-ghost:hover {
  background: #f0fdfa;
  border-color: #0f766e;
  color: #115e59;
}
.btn-danger-ghost {
  background: #fff;
  border-color: #fbc4c4;
  color: #dc2626;
}
.btn-danger-ghost:hover {
  background: #fee2e2;
  border-color: #dc2626;
  color: #d03050;
}
.btn-success {
  background: #16a34a;
  border-color: #16a34a;
  color: #fff;
}
.btn-success:hover {
  background: #16a34a;
  border-color: #16a34a;
  color: #fff;
}
.btn svg {
  width: 14px;
  height: 14px;
  stroke: currentColor;
  fill: none;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.assess-count {
  font-size: 13px;
  color: #78716c;
}
.assess-count strong {
  color: #0f766e;
  font-size: 16px;
}

/* 三个时间节点卡片 */
.bundle-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}
.bundle-card {
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 1px 4px rgba(28, 25, 23, 0.04);
  border: 1px solid #e7e5e4;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.bundle-card.completed {
  border-top: 3px solid #16a34a;
}
.bundle-card.pending {
  border-top: 3px solid #d97706;
}
.bundle-card-header {
  padding: 14px 18px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #e7e5e4;
  background: #fafaf9;
}
.bundle-card-title {
  display: flex;
  align-items: center;
  gap: 10px;
}
.time-badge {
  width: 38px;
  height: 38px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  color: #fff;
}
.time-badge.h1 { background: #dc2626; }
.time-badge.h3 { background: #d97706; }
.time-badge.h6 { background: #16a34a; }
.bundle-name {
  font-size: 15px;
  font-weight: 600;
  color: #292524;
}
.bundle-sub {
  font-size: 11px;
  color: #78716c;
  margin-top: 2px;
}
.status-tag {
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 4px;
}
.status-tag.done {
  background: #f0fdf4;
  color: #16a34a;
  border: 1px solid #e1f3d8;
}
.status-tag.pending {
  background: #fef3c7;
  color: #d97706;
  border: 1px solid #faecd8;
}

.bundle-card-body {
  padding: 14px 18px;
  flex: 1;
}
.bundle-item {
  padding: 10px 12px;
  margin-bottom: 8px;
  background: #fafaf9;
  border-radius: 6px;
  border-left: 3px solid #d6d3d1;
  transition: all 0.2s;
}
.bundle-item:last-child {
  margin-bottom: 0;
}
.bundle-item.done {
  background: #fafaf9;
  border-left-color: #16a34a;
}
.item-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.item-check {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  margin-top: 2px;
  cursor: pointer;
  appearance: none;
  -webkit-appearance: none;
  border: 1.5px solid #a8a29e;
  border-radius: 3px;
  position: relative;
  transition: all 0.2s;
}
.item-check:checked {
  background: #16a34a;
  border-color: #16a34a;
}
.item-check:checked::after {
  content: '';
  position: absolute;
  left: 4px;
  top: 1px;
  width: 5px;
  height: 9px;
  border: solid #fff;
  border-width: 0 2px 2px 0;
  transform: rotate(45deg);
}
.item-name {
  font-size: 13px;
  color: #44403c;
  line-height: 1.5;
  flex: 1;
  cursor: default;
  user-select: none;
}
.bundle-item.done .item-name {
  color: #44403c;
}
.item-detail {
  margin-left: 24px;
  margin-top: 6px;
}
.detail-tag {
  font-size: 11px;
  color: #0f766e;
  background: #f0fdfa;
  padding: 2px 8px;
  border-radius: 4px;
  display: inline-block;
  line-height: 1.5;
}
.detail-tag.warn {
  color: #d97706;
  background: #fef3c7;
}

/* 3小时后评估 */
.assessment-box {
  margin: 0 18px 14px;
  padding: 12px 14px;
  background: #fafaf9;
  border-radius: 8px;
  border-left: 3px solid #17a2b8;
}
.assessment-title {
  font-size: 12px;
  font-weight: 600;
  color: #17a2b8;
  margin-bottom: 10px;
}
.assessment-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.assess-item {
  padding: 6px 8px;
  background: #fff;
  border-radius: 4px;
  border: 1px solid #eef1f5;
}
.assess-label {
  font-size: 10px;
  color: #78716c;
  font-weight: 500;
}
.assess-value {
  font-size: 14px;
  font-weight: 700;
  color: #44403c;
  margin-top: 2px;
}
.assess-value small {
  font-size: 10px;
  font-weight: 400;
  color: #a8a29e;
  margin-left: 2px;
}

.bundle-card-footer {
  padding: 10px 18px;
  border-top: 1px solid #e7e5e4;
  background: #fafaf9;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.all-check {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-size: 12.5px;
  color: #44403c;
  font-weight: 500;
}
.all-check input[type="checkbox"] {
  width: 14px;
  height: 14px;
  cursor: pointer;
  accent-color: #16a34a;
}
.progress-text {
  font-size: 12px;
  color: #78716c;
}
.progress-text strong {
  color: #0f766e;
  font-size: 14px;
}

/* 底部信息区 */
.bottom-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}
.info-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.info-block {
  padding: 10px 12px;
  background: #fafaf9;
  border-radius: 6px;
}
.info-label {
  font-size: 11px;
  color: #78716c;
  font-weight: 500;
  margin-bottom: 6px;
}
.info-value {
  font-size: 14px;
  color: #44403c;
  font-weight: 600;
  line-height: 1.5;
}
.value-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.value-tag {
  font-size: 12px;
  color: #0f766e;
  background: #f0fdfa;
  border: 1px solid #d9ecff;
  padding: 2px 8px;
  border-radius: 4px;
  line-height: 1.5;
}

/* 液体复苏未达原因 */
.fluid-reason-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.reason-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: #fafaf9;
  border-radius: 6px;
}
.reason-item.checked {
  background: #f0fdf4;
  border: 1px solid #e1f3d8;
}
.reason-item input[type="checkbox"] {
  width: 15px;
  height: 15px;
  cursor: pointer;
  accent-color: #d97706;
}
.reason-item span {
  font-size: 13px;
  color: #44403c;
  cursor: default;
  user-select: none;
}
.reason-other {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: #fafaf9;
  border-radius: 6px;
}
.other-label {
  font-size: 13px;
  color: #44403c;
  font-weight: 600;
  white-space: nowrap;
}
.other-input {
  flex: 1;
  padding: 6px 10px;
  border: 1px solid #e7e5e4;
  border-radius: 4px;
  font-size: 13px;
  outline: none;
}
.other-input:focus {
  border-color: #0d9488;
}
.no-reason {
  padding: 16px;
  text-align: center;
  color: #a0aec0;
  font-size: 13px;
}

/* 操作按钮 */
.action-bar {
  background: #fff;
  border-radius: 10px;
  padding: 14px 24px;
  display: flex;
  justify-content: center;
  gap: 16px;
  box-shadow: 0 1px 4px rgba(28, 25, 23, 0.04);
  border: 1px solid #e7e5e4;
  position: sticky;
  bottom: 16px;
  z-index: 100;
}
.action-bar .btn {
  padding: 10px 24px;
  font-size: 14px;
  border-radius: 6px;
}

/* 响应式 */
@media (max-width: 1400px) {
  .bundle-grid {
    grid-template-columns: 1fr;
  }
  .bottom-grid {
    grid-template-columns: 1fr;
  }
  .p-progress-area {
    min-width: 100%;
    padding: 8px 16px 0;
    border-top: 1px solid #eef1f5;
    margin-top: 8px;
  }
}
</style>

