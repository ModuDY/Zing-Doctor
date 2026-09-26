<template>
  <div class="sepsis-page abx-theme">
    <div v-if="!inHospitalNo" class="warn-bar">
      <el-alert type="warning" :closable="false" show-icon
                title="缺少患者参数"
                description="本页面需通过外链（携带 inHospitalNo）进入" />
    </div>

    <div v-else v-loading="loading" class="page-body">
      <el-alert v-if="loadError" type="error" :closable="false" show-icon :title="loadError" class="page-error" />
      <div class="sepsis-body">
      <!-- 左侧：评估记录列表（形式与 SOFA / APACHE II 评分记录一致） -->
      <aside class="side">
        <div class="side-head">
          <span>评估记录</span>
          <span class="count">{{ historyList.length }}</span>
        </div>
        <div class="side-add">
          <button class="add-record-btn" @click="onNewAssess">
            <span class="plus">＋</span>
            新建评估
          </button>
          <button class="side-ghost-btn" title="抗菌药物识别词库配置" @click="goWordConfig">词库配置</button>
        </div>
        <div class="record-list">
          <div v-for="(item, index) in historyList" :key="item.id"
               :class="['record-item', { active: currentAssessId === item.id }]"
               @click="onSelectRecord(item)">
            <div class="record-top">
              <span class="record-time">第{{ historyList.length - index }}次 · {{ fmtTime(item.createTime) }}</span>
              <span :class="['record-score', recordScoreClass(item)]">{{ recordDoneCount(item) }}</span>
            </div>
            <div class="record-meta">
              <span v-if="currentAssessId === item.id" class="record-tag cur-tag">当前</span>
              <span class="record-tag del-tag" @click.stop="onDeleteAssess(item)">删除</span>
            </div>
          </div>
          <div v-if="!historyList.length" class="record-empty">暂无评估记录</div>
        </div>
      </aside>

      <div class="main">
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
          <div class="p-cell">
            <label>记录时间</label>
            <el-date-picker
              v-model="recordTime"
              type="datetime"
              placeholder="选择记录时间"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DD HH:mm:ss"
              size="small"
              style="width: 170px"
              :disabled-date="disableFutureDate"
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
                <span v-if="data.bundle1hCompleted === 1" class="mini-check">
                  <svg viewBox="0 0 10 10"><polyline points="1.5 5.5 4 8 8.5 2" fill="none" stroke-width="2"/></svg>
                </span>
              </div>
              <span class="step-name">{{ data.bundle1hCompleted === 1 ? '已完成' : '待完成' }}</span>
            </div>
            <div class="step-line" :class="{ filled: data.bundle3hCompleted === 1 }"><span class="fill"></span></div>
            <div class="step-node" :class="{ done: data.bundle3hCompleted === 1, active: data.bundle3hCompleted !== 1 && data.bundle1hCompleted === 1 }">
              <div class="step-circle" :class="{ done: data.bundle3hCompleted === 1, active: data.bundle3hCompleted !== 1 && data.bundle1hCompleted === 1 }">
                3H
                <span v-if="data.bundle3hCompleted === 1" class="mini-check">
                  <svg viewBox="0 0 10 10"><polyline points="1.5 5.5 4 8 8.5 2" fill="none" stroke-width="2"/></svg>
                </span>
              </div>
              <span class="step-name">{{ data.bundle3hCompleted === 1 ? '已完成' : '待完成' }}</span>
            </div>
            <div class="step-line" :class="{ filled: data.bundle6hCompleted === 1 }"><span class="fill"></span></div>
            <div class="step-node" :class="{ done: data.bundle6hCompleted === 1, active: data.bundle6hCompleted !== 1 && data.bundle3hCompleted === 1 }">
              <div class="step-circle" :class="{ done: data.bundle6hCompleted === 1, active: data.bundle6hCompleted !== 1 && data.bundle3hCompleted === 1 }">
                6H
                <span v-if="data.bundle6hCompleted === 1" class="mini-check">
                  <svg viewBox="0 0 10 10"><polyline points="1.5 5.5 4 8 8.5 2" fill="none" stroke-width="2"/></svg>
                </span>
              </div>
              <span class="step-name">{{ data.bundle6hCompleted === 1 ? '已完成' : '待完成' }}</span>
            </div>
          </div>
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
            <div v-for="(item, index) in bundle1hItems" :key="index" class="bundle-item" :class="{ done: item.completed }">
              <div class="item-row">
                <input type="checkbox" class="item-check" :checked="item.completed" @change="onBundle1hItemChange(index, $event.target.checked)">
                <span class="item-name">{{ item.name }}</span>
              </div>
              <div v-if="item.detail" class="item-detail">
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
            <div v-for="(item, index) in bundle3hItems" :key="index" class="bundle-item" :class="{ done: item.completed }">
              <div class="item-row">
                <input type="checkbox" class="item-check" :checked="item.completed" @change="onBundle3hItemChange(index, $event.target.checked)">
                <span class="item-name">{{ item.name }}</span>
              </div>
              <div v-if="item.detail" class="item-detail">
                <span class="detail-tag" :class="{ warn: item.warn }">{{ item.detail }}</span>
              </div>
            </div>
          </div>
          <!-- 3小时后评估 -->
          <div v-if="hasAssessmentData" class="assessment-box">
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
            <div v-for="(item, index) in bundle6hItems" :key="index" class="bundle-item" :class="{ done: item.completed }">
              <div class="item-row">
                <input type="checkbox" class="item-check" :checked="item.completed" @change="onBundle6hItemChange(index, $event.target.checked)">
                <span class="item-name">{{ item.name }}</span>
              </div>
              <div v-if="item.detail" class="item-detail">
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
          <div class="block-title">
            <span class="dot dot-cyan"></span>感染相关信息
            <span v-if="data.refWindowStart" class="ref-window">
              参考数据窗口 {{ data.refWindowStart }} ~ {{ data.refWindowEnd }}
            </span>
          </div>
          <div class="info-grid">
            <div class="info-block">
              <div class="info-label">感染部位</div>
              <!-- 系统参考放按钮组上方：医生先看系统取到的值，再决定勾哪一项。
                   放在按钮下面时会被一屏按钮顶到看不见，失去参考意义 -->
              <div class="ref-tags">
                <span class="ref-tag prefix">系统参考</span>
                <span v-for="v in infectionSiteRefList" :key="v" class="ref-tag">{{ v }}</span>
                <span v-if="!infectionSiteRefList.length" class="ref-tag empty">窗口内无数据</span>
              </div>
              <div class="opt-group">
                <button v-for="opt in INFECTION_SITE_OPTIONS" :key="opt" type="button"
                        class="opt-btn" :class="{ on: selInfectionSite.includes(opt) }"
                        @click="toggleOpt(selInfectionSite, opt)">{{ opt }}</button>
              </div>
            </div>
            <div class="info-block">
              <div class="info-label">致病菌</div>
              <div class="ref-tags">
                <span class="ref-tag prefix">系统参考</span>
                <span v-for="v in pathogenRefList" :key="v" class="ref-tag">{{ v }}</span>
                <span v-if="!pathogenRefList.length" class="ref-tag empty">窗口内无数据</span>
              </div>
              <div class="opt-group">
                <button v-for="opt in PATHOGEN_OPTIONS" :key="opt" type="button"
                        class="opt-btn" :class="{ on: selPathogen.includes(opt) }"
                        @click="toggleOpt(selPathogen, opt)">{{ opt }}</button>
              </div>
            </div>
            <div class="info-block">
              <div class="info-label">抗菌药物</div>
              <div class="ref-tags">
                <span class="ref-tag prefix">系统参考</span>
                <span v-for="v in antibioticRefList" :key="v" class="ref-tag">{{ v }}</span>
                <span v-if="!antibioticRefList.length" class="ref-tag empty">窗口内无数据</span>
              </div>
              <div class="opt-group">
                <button v-for="opt in ANTIBIOTIC_OPTIONS" :key="opt" type="button"
                        class="opt-btn" :class="{ on: selAntibiotic.includes(opt) }"
                        @click="toggleOpt(selAntibiotic, opt)">{{ opt }}</button>
              </div>
            </div>
          </div>
        </div>

        <!-- 液体复苏未达原因 -->
        <div class="card">
          <div class="block-title"><span class="dot dot-orange"></span>液体复苏未达30ml/kg原因</div>
          <div v-if="data.fluidReason" class="fluid-reason-list">
            <div class="reason-item" :class="{ checked: data.fluidReason.volumeOverload }">
              <input v-model="data.fluidReason.volumeOverload" type="checkbox">
              <span>存在容量过负荷（肺水肿/急性左心衰）</span>
            </div>
            <div class="reason-item" :class="{ checked: data.fluidReason.organInjury }">
              <input v-model="data.fluidReason.organInjury" type="checkbox">
              <span>存在限制性液体复苏的器官损伤（AKI/ARDS等）</span>
            </div>
            <div class="reason-item" :class="{ checked: data.fluidReason.capillaryLeak }">
              <input v-model="data.fluidReason.capillaryLeak" type="checkbox">
              <span>严重毛细血管渗漏</span>
            </div>
            <div class="reason-other">
              <span class="other-label">其他：</span>
              <input v-model="data.fluidReason.other" type="text" class="other-input" placeholder="请输入其他原因">
            </div>
          </div>
          <div v-else class="no-reason">暂无液体复苏未达原因记录</div>
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
      </div><!-- /.main -->
      </div><!-- /.sepsis-body -->
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
const loadError = ref('')
const diagnosisTime = ref('')
/** 记录时间：医生可改，同时决定三块「系统参考」14 天窗口的结束点 */
const recordTime = ref('')
const historyList = ref([])
const currentAssessId = ref(null)

/** 记录时间不得晚于当前时间（否则参考窗口会算到未来） */
const disableFutureDate = (t) => t.getTime() > Date.now()

/** 当前时间字符串 yyyy-MM-dd HH:mm:ss */
function nowStr() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

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
  // 系统参考值（只读展示，不入库）
  infectionSiteRef: '',
  pathogenRef: '',
  antibioticRef: '',
  refWindowStart: '',
  refWindowEnd: '',
  fluidReason: { volumeOverload: false, organInjury: false, capillaryLeak: false, other: '' }
})

/* ===== 多值拆分（感染相关信息） ===== */
function splitMulti(str) {
  if (!str) return []
  return str.split(/[,，;；、\n]/).map(s => s.trim()).filter(s => s && s !== '—' && s !== '-')
}
const infectionSiteList = computed(() => splitMulti(data.infectionSite))
const infectionSiteText = computed(() => infectionSiteList.value[0] || '')

/* ===== 感染相关信息：医生勾选的枚举项 =====
   这三块的下拉值固定为下列枚举，医生点选哪个就存哪个（以「、」拼接入库）；
   后端自动取到的原始药名/菌名只作为「系统参考」展示，不入库。 */
const INFECTION_SITE_OPTIONS = [
  '血流感染', 'CRBSI血流感染', '非CRBSI血流感染', '肺部感染', '腹腔感染', '泌尿系感染',
  '中枢神经系统感染', '胆道感染', '胃肠道感染', '骨髓感染', '皮肤软组织感染', '其他部位感染'
]
const PATHOGEN_OPTIONS = [
  '鲍曼不动杆菌', '绿脓杆菌', '大肠杆菌', '肺炎克雷伯菌', '嗜麦芽窄食假单孢菌',
  'MRSA', '屎肠球菌', '粪肠球菌', '念珠菌', '其他院内感染致病菌'
]
const ANTIBIOTIC_OPTIONS = [
  '青霉素类', '第一代头孢菌素类', '第二代头孢菌素类', '第三代头孢菌素类', '第四代头孢菌素类',
  'β-内酰胺类', '碳青霉烯类', '氨基糖苷类', '大环内酯类', '喹诺酮类', '糖肽类',
  '磺胺类和甲氧苄啶', '林可酰胺类', '四环素类', '酰胺醇类', '硝基咪唑类', '抗真菌药物', '其他抗菌药物'
]

const selInfectionSite = ref([])
const selPathogen = ref([])
const selAntibiotic = ref([])

// 系统参考值（后端按「评估记录创建时间往前 14 天」窗口自动取到，仅展示）
const infectionSiteRefList = computed(() => splitMulti(data.infectionSiteRef))
const pathogenRefList = computed(() => splitMulti(data.pathogenRef))
const antibioticRefList = computed(() => splitMulti(data.antibioticRef))

// 注意：模板里传入的是已解包的数组（ref 在模板中自动 unwrap），这里直接按数组操作
function toggleOpt(arr, val) {
  const i = arr.indexOf(val)
  if (i >= 0) arr.splice(i, 1)
  else arr.push(val)
}

/** 把已保存的字符串回填到勾选态；只保留仍在枚举表里的值，避免历史脏数据变成幽灵选中 */
function syncSelections() {
  selInfectionSite.value = splitMulti(data.infectionSite).filter(v => INFECTION_SITE_OPTIONS.includes(v))
  selPathogen.value = splitMulti(data.pathogen).filter(v => PATHOGEN_OPTIONS.includes(v))
  selAntibiotic.value = splitMulti(data.antibiotic).filter(v => ANTIBIOTIC_OPTIONS.includes(v))
}

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
    const res = await request.get('/sepsis/bundle/history', { params: { inHospitalNo: inHospitalNo.value }, silentError: true })
    historyList.value = res || []
  } catch (e) {
    console.error('加载历史记录失败', e)
  }
}

/* ===== 加载数据 ===== */
async function loadData(assessId = null) {
  if (!inHospitalNo.value) return
  loading.value = true
  loadError.value = ''
  try {
    let res
    if (assessId) {
      res = await request.get(`/sepsis/bundle/detail/${assessId}`, { silentError: true })
    } else {
      res = await request.get('/sepsis/bundle/detail', { params: { inHospitalNo: inHospitalNo.value }, silentError: true })
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
      recordTime.value = res.createTime || ''
      syncSelections()
    }
  } catch (e) {
    console.error('加载脓毒症集束化治疗数据失败', e)
    loadError.value = '脓毒症集束化数据暂不可用：请检查 ICU 数据源、患者权限或外链是否已过期。'
  } finally {
    loading.value = false
  }
}

async function onNewAssess() {
  currentAssessId.value = null
  resetForNewAssess()
  recordTime.value = nowStr()
  // 新建态必须主动触发一次自动计算（不落库），否则 1H/3H/6H 与三块系统参考全是空的
  const ok = await runCalculate()
  ElMessage.success(ok ? '已切换到新建评估模式，并按当前时间完成自动计算' : '已切换到新建评估模式')
}

/* ===== 自动计算（预览，不落库） =====
   新建评估、或改了记录时间/确诊时间后点「重新评估」都走这里。
   keepManual=true 时只刷新详情与系统参考，保留医生当前已勾选的项目。 */
const B1_BOOLS = ['lactateMeasured', 'lactateMonitor', 'bloodCultureBeforeAntibiotic', 'broadSpectrumAntibiotic', 'fluidResuscitation', 'norepinephrine']
const B3_BOOLS = ['lactateMeasured', 'bloodCultureBeforeAntibiotic', 'broadSpectrumAntibiotic', 'fluidResuscitation']
const B6_BOOLS = ['vasopressor', 'reassessVolume', 'repeatLactate']

function omitKeys(obj, keys) {
  const o = { ...(obj || {}) }
  keys.forEach(k => { delete o[k] })
  return o
}

async function runCalculate() {
  if (!inHospitalNo.value) return false
  // 编辑态：在该记录基础上重算，保留医生已勾选项目；新建态：全量填充
  const keepManual = !!data.id
  loading.value = true
  loadError.value = ''
  try {
    const params = {
      inHospitalNo: inHospitalNo.value,
      recordTime: normTime(recordTime.value),
      diagnosisTime: normTime(diagnosisTime.value)
    }
    if (data.id) params.id = data.id
    const res = await request.get('/sepsis/bundle/calculate', { params, silentError: true })
    if (res) applyCalcResult(res, keepManual)
    return true
  } catch (e) {
    console.error('自动计算失败', e)
    ElMessage.error('自动计算失败')
    return false
  } finally {
    loading.value = false
  }
}

function applyCalcResult(res, keepManual) {
  if (res.id) data.id = res.id
  if (res.createTime) recordTime.value = res.createTime
  if (res.bundle1h) data.bundle1h = { ...data.bundle1h, ...(keepManual ? omitKeys(res.bundle1h, B1_BOOLS) : res.bundle1h) }
  if (res.bundle3h) data.bundle3h = { ...data.bundle3h, ...(keepManual ? omitKeys(res.bundle3h, B3_BOOLS) : res.bundle3h) }
  if (res.bundle6h) data.bundle6h = { ...data.bundle6h, ...(keepManual ? omitKeys(res.bundle6h, B6_BOOLS) : res.bundle6h) }
  data.infectionSiteRef = res.infectionSiteRef || ''
  data.pathogenRef = res.pathogenRef || ''
  data.antibioticRef = res.antibioticRef || ''
  data.refWindowStart = res.refWindowStart || ''
  data.refWindowEnd = res.refWindowEnd || ''
  if (res.diagnosisTime) diagnosisTime.value = res.diagnosisTime
  if (res.fluidReason) data.fluidReason = res.fluidReason
  if (res.patientName) data.patientName = res.patientName
  if (res.shockType) data.shockType = res.shockType
  if (res.inDepartTime) data.inDepartTime = res.inDepartTime
  if (res.outDepartTime) data.outDepartTime = res.outDepartTime
  // 后端不写 bundle*Completed，这里按算出的勾选重算，否则进度条永远停在「待完成」
  syncCompletedFlags()
}

function goWordConfig() {
  router.push({ path: '/page/abx-word-config', query: { inHospitalNo: inHospitalNo.value } })
}

/* ===== 评估记录列表（左侧，形式同 SOFA / APACHE II） ===== */
function onSelectRecord(item) {
  if (!item || currentAssessId.value === item.id) return
  currentAssessId.value = item.id
  loadData(item.id)
}

/** 记录条徽标：1H/3H/6H 三个模块中已完成的个数（0~3） */
function recordDoneCount(item) {
  if (!item) return 0
  return (item.bundle1hCompleted === 1 ? 1 : 0)
       + (item.bundle3hCompleted === 1 ? 1 : 0)
       + (item.bundle6hCompleted === 1 ? 1 : 0)
}
function recordScoreClass(item) {
  const n = recordDoneCount(item)
  if (n >= 3) return 'green'
  if (n > 0) return 'orange'
  return 'gray'
}

/* ===== 删除评估记录 ===== */
async function onDeleteAssess(item) {
  const id = (item && item.id) || currentAssessId.value
  if (!id) return
  try {
    await ElMessageBox.confirm('确定删除该评估记录吗？删除后不可恢复。', '删除评估', { type: 'warning', customClass: 'abx-overlay' })
  } catch {
    return
  }
  try {
    await request.post('/sepsis/bundle/delete', null, { params: { id } })
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
  // 三块改为医生逐次勾选，新建评估时不沿用上一次的选择
  data.infectionSite = ''
  data.pathogen = ''
  data.antibiotic = ''
  syncSelections()
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
  // 记录时间不得晚于当前时间
  if (recordTime.value) {
    const rt = new Date(String(recordTime.value).replace(/-/g, '/')).getTime()
    if (!isNaN(rt) && rt > Date.now()) {
      ElMessage.warning('记录时间不能晚于当前时间')
      return
    }
  }
  try {
    const payload = {
      id: data.id,
      createTime: normTime(recordTime.value),
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
      // 三块按医生实际勾选的枚举值入库（、拼接）；参考值不入
      infectionSite: selInfectionSite.value.join('、'),
      pathogen: selPathogen.value.join('、'),
      antibiotic: selAntibiotic.value.join('、'),
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

async function onRefresh() {
  // 按当前「记录时间 + 确诊时间」重算：新建态走新建预览，编辑态在其基础上重算并保留勾选。
  // 不能再用 loadData(null) —— 它拉的是最新一条已有记录，新建时点它会把旧数据覆盖回来。
  if (!recordTime.value) recordTime.value = nowStr()
  await runCalculate()
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

.page-error { margin-bottom: 16px; }

.page-body {
  padding: 16px 24px 32px;
  max-width: 1920px;
  margin: 0 auto;
}

/* ===== 左右分栏：左侧评估记录列表（同 SOFA / APACHE II），右侧评估内容 ===== */
.sepsis-body {
  display: flex;
  /* 与 SOFA / APACHE II 一致：侧栏与右侧主区等高（stretch），
     原来是 flex-start —— 侧栏高度会塌成「记录条数」决定的内容高度，记录少时卡片只占一小截 */
  align-items: stretch;
  gap: 14px;
}
.side {
  width: 232px;
  flex-shrink: 0;
  background: #fff;
  border: 1px solid #e7e5e4;
  border-radius: 10px;
  box-shadow: 0 1px 4px rgba(28, 25, 23, 0.04);
  display: flex;
  flex-direction: column;
  /* 占满视口高度（上对 16px sticky 偏移、下留 32px），记录列表 flex:1 撑满剩余空间并内部滚动。
     原来只给了 max-height，高度由内容撑，记录少时白卡片矮一截，看着不像「展开」 */
  height: calc(100vh - 48px);
  position: sticky;
  top: 16px;
}
.side-head {
  height: 46px;
  padding: 0 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f5f5f4;
  font-weight: 600;
  font-size: 14px;
  color: #1c1917;
}
.side-head .count {
  background: #ccfbf1;
  color: #0f766e;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 11px;
  font-size: 12px;
  font-weight: 600;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}
.side-add {
  padding: 10px;
  border-bottom: 1px solid #f5f5f4;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.add-record-btn {
  width: 100%;
  height: 36px;
  background: linear-gradient(135deg, #0d9488, #14b8a6);
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  box-shadow: 0 2px 6px rgba(13, 148, 136, 0.25);
}
.add-record-btn:hover {
  background: linear-gradient(135deg, #14b8a6, #0d9488);
}
.add-record-btn .plus { font-size: 18px; line-height: 1; }
.side-ghost-btn {
  width: 100%;
  height: 30px;
  background: #fff;
  color: #57534e;
  border: 1px solid #e7e5e4;
  border-radius: 6px;
  font-size: 12px;
  cursor: pointer;
}
.side-ghost-btn:hover {
  border-color: #99f6e4;
  color: #0f766e;
}
.record-list {
  flex: 1;
  /* 固定高度父容器内允许收缩，否则记录多了会顶破侧栏而不是在内部滚动 */
  min-height: 0;
  overflow-y: auto;
  padding: 8px;
}
.record-item {
  padding: 10px 12px;
  margin-bottom: 8px;
  border: 1px solid #e7e5e4;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  background: #fff;
}
.record-item:hover {
  border-color: #99f6e4;
  background: #f0fdfa;
}
.record-item.active {
  border-color: #0d9488;
  background: #f0fdfa;
}
.record-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.record-time {
  font-size: 12px;
  color: #44403c;
  font-weight: 500;
  line-height: 1.4;
}
.record-score {
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 700;
  border-radius: 50%;
}
.record-score.green { background: #dcfce7; color: #16a34a; }
.record-score.orange { background: #fef3c7; color: #d97706; }
.record-score.gray { background: #f5f5f4; color: #a8a29e; }
.record-meta {
  font-size: 11px;
  color: #a8a29e;
  line-height: 1.8;
}
.record-tag {
  display: inline-flex;
  align-items: center;
  padding: 1px 8px;
  border-radius: 10px;
  font-size: 11px;
  background: #f5f5f4;
  color: #78716c;
}
.record-tag.cur-tag { background: #ccfbf1; color: #0f766e; }
.record-tag.del-tag { cursor: pointer; }
.record-tag.del-tag:hover { background: #fee2e2; color: #dc2626; }
.record-empty {
  text-align: center;
  color: #a8a29e;
  font-size: 12px;
  padding: 28px 0;
}
.main {
  flex: 1;
  min-width: 0;
}
.warn-bar {
  padding: 16px 24px;
}

/* 卡片通用 */
.card {
  background: #fff;
  border-radius: 10px;
  /* 左侧记录列表占掉 232px 后整体收紧，空间从 1H/3H/6H 模块压出来 */
  padding: 12px 14px;
  box-shadow: 0 1px 4px rgba(28, 25, 23, 0.04);
  border: 1px solid #e7e5e4;
  margin-bottom: 12px;
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
.dot-cyan { background: #0891b2; }
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
  border-right: 1px solid #f5f5f4;
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
  border-right: 1px solid #e7e5e4;
  padding: 4px 12px;
  min-width: 88px;
}
.p-cell-group .p-cell:last-child { border-right: none; }
.p-cell label { font-size: 12px; color: #78716c; }
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
  border: 2px solid #d6d3d1;
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
  0%, 100% { box-shadow: 0 0 0 2px rgba(217, 119, 6, 0.2); }
  50% { box-shadow: 0 0 0 6px rgba(217, 119, 6, 0.3); }
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
  background: linear-gradient(90deg, #16a34a, #4ade80);
  border-radius: 2px;
}
.step-line.filled .fill { width: 100%; }

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
/* 三个时间节点卡片 */
.bundle-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 12px;
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
  padding: 10px 14px;
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
  border: 1px solid #dcfce7;
}
.status-tag.pending {
  background: #fef3c7;
  color: #d97706;
  border: 1px solid #fef3c7;
}

.bundle-card-body {
  padding: 10px 14px;
  flex: 1;
}
.bundle-item {
  padding: 7px 10px;
  margin-bottom: 6px;
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
  border-left: 3px solid #0891b2;
}
.assessment-title {
  font-size: 12px;
  font-weight: 600;
  color: #0891b2;
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
  border: 1px solid #f5f5f4;
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
  padding: 8px 14px;
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
  /* 三块改为多选按钮后每块都要横向铺开，单列才放得下 */
  grid-template-columns: 1fr;
  gap: 12px;
}
.info-block {
  padding: 10px 12px;
  background: #fafaf9;
  border-radius: 6px;
}
.info-label {
  font-size: 13px;
  color: #292524;
  font-weight: 700;
  margin-bottom: 6px;
}
/* 感染相关信息：参考窗口 / 系统参考值 / 多选枚举按钮 */
.ref-window {
  margin-left: auto;
  font-size: 11px;
  font-weight: 400;
  color: #a8a29e;
}
/* 系统参考：放在勾选按钮组「上方」——医生先看系统取到的值，再决定勾哪一项。
   原放在按钮下方，一屏按钮会把它顶到看不见，等于没有参考。 */
.ref-tags {
  margin-bottom: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.ref-tag {
  font-size: 11px;
  color: #0f766e;
  background: #f0fdfa;
  padding: 2px 8px;
  border-radius: 4px;
  display: inline-block;
  line-height: 1.5;
}
/* 「系统参考」标识：上移后作为引导视线的一行，用青色实底强调，与后面的参考值区分开 */
.ref-tag.prefix {
  color: #0f766e;
  background: #ccfbf1;
  font-weight: 600;
}
.ref-tag.empty {
  color: #a8a29e;
  background: #f5f5f4;
}
.opt-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.opt-btn {
  padding: 4px 10px;
  font-size: 12px;
  line-height: 1.5;
  color: #57534e;
  background: #ffffff;
  border: 1px solid #e7e5e4;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}
.opt-btn:hover {
  border-color: #99f6e4;
  color: #0f766e;
}
.opt-btn.on {
  color: #ffffff;
  background: #0d9488;
  border-color: #0d9488;
  font-weight: 500;
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
  border: 1px solid #dcfce7;
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
  color: #a8a29e;
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
  /* 窄屏时记录列表改为顶部横排，不再挤压右侧内容 */
  .sepsis-body {
    flex-direction: column;
  }
  .side {
    width: 100%;
    max-height: 260px;
    position: static;
  }
  .main {
    width: 100%;
  }
  .p-progress-area {
    min-width: 100%;
    padding: 8px 16px 0;
    border-top: 1px solid #f5f5f4;
    margin-top: 8px;
  }
}
</style>
