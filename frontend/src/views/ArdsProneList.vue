<template>
  <div class="ards-page ards-theme">
    <div class="topbar">
      <div>
        <h1>俯卧位通气治疗记录</h1>
        <div class="sub">
          重症医学科 · 按记录（一次俯卧位疗程）管理，可查看 / 编辑 / 打印 / 复制新建
        </div>
      </div>
      <div class="spacer"></div>
      <button class="btn btn-new" @click="onNew">+ 新建记录</button>
    </div>

    <!-- 筛选 -->
    <div class="filter">
      <select v-model="q.status" class="ipt w110">
        <option value="">状态：全部</option>
        <option value="ing">进行中</option>
        <option value="submitted">已提交</option>
        <option value="stop">已终止</option>
        <option value="draft">草稿</option>
      </select>
      <button class="btn primary" @click="load">查询</button>
      <button class="btn" @click="onReset">重置</button>
      <span class="spacer"></span>
      <span class="cnt">共 <b>{{ rowsView.length }}</b> 条记录 · {{ patientCount }} 例患者</span>
    </div>

    <!-- 统计 -->
    <div class="stat-row">
      <div class="stat">
        <div class="k">俯卧位例次</div>
        <div class="v">{{ stats.times }}<small>次</small></div>
        <div class="s">当前筛选范围内</div>
      </div>
      <div class="stat">
        <div class="k">{{ stats.patients === 1 ? '累计俯卧位时长' : '涉及患者' }}</div>
        <div class="v">{{ stats.patients === 1 ? stats.totalMin : stats.patients }}<small>{{ stats.patients === 1 ? 'h' : '例' }}</small></div>
        <div class="s">{{ stats.patients === 1 ? '已完成 ' + stats.doneCount + ' 次 · 进行中实时计入' : '人均 ' + stats.avgPerPatient + ' 次' }}</div>
      </div>
      <div class="stat">
        <div class="k">平均持续时长</div>
        <div class="v">{{ stats.avgDuration }}<small>h</small></div>
        <div class="s">目标 ≥ 12h</div>
      </div>
      <div class="stat">
        <div class="k">氧合改善率</div>
        <div class="v">{{ stats.improveRate }}<small>%</small></div>
        <div class="s">P/F 提升 ≥ 20 mmHg</div>
      </div>
      <div class="stat">
        <div class="k">并发症发生率</div>
        <div class="v">{{ stats.compRate }}<small>%</small></div>
        <div class="s">{{ stats.compCount }} / {{ stats.times }} 例次</div>
      </div>
    </div>

    <!-- 列表 -->
    <div class="card">
      <div class="hd">
        <h2>记录列表</h2>
        <span class="hint">点击行展开时点摘要</span>
        <div class="spacer"></div>
        <span class="tag gray">已提交记录可更正，不限时，修改全程留痕</span>
      </div>
      <div class="bd" style="padding: 0">
        <div v-if="loading" class="loading">加载中…</div>
        <table v-else class="lst">
          <thead>
            <tr>
              <th>记录日期</th>
              <th>患者</th>
              <th>疗程</th>
              <th>开始 → 结束</th>
              <th>持续</th>
              <th>氧合指数 P/F</th>
              <th>最低 ΔP</th>
              <th>并发症</th>
              <th>状态</th>
              <th v-if="archiveEnabled">归档回传</th>
              <th>最后更新</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!rowsView.length">
              <td :colspan="archiveEnabled ? 12 : 11" class="empty">暂无记录，可点击右上角「新建记录」开始</td>
            </tr>
            <template v-for="r in rowsView" :key="r.id">
              <tr class="row" @click="toggleExpand(r.id)">
                <td>{{ fmtDate(r.recordDate || r.startTime) }}</td>
                <td>
                  <span class="nm">{{ r.bedCode ? r.bedCode + ' ' : '' }}{{ r.patientName || '—' }}</span>
                  <div class="sub">{{ r.inHospitalNo }}</div>
                </td>
                <td>{{ r.proneDay || '—' }} / 第 {{ r.proneTimes || 1 }} 次</td>
                <td>{{ fmtTime(r.startTime) }} → {{ r.endTime ? fmtTime(r.endTime) : '进行中' }}</td>
                <td>
                  {{ durationText(r) }}
                  <span v-if="summaryOf(r).filled != null" class="fill">{{ summaryOf(r).filled }}/{{ summaryOf(r).total }}</span>
                </td>
                <td v-html="pfCell(r)"></td>
                <td>{{ summaryOf(r).dpMin != null ? summaryOf(r).dpMin + ' cmH₂O' : '—' }}</td>
                <td v-html="compCell(r)"></td>
                <td v-html="statCell(r)"></td>
                <td v-if="archiveEnabled" v-html="arcCell(r)"></td>
                <td class="upd">
                  <span :title="updByTitle(r)">{{ updBy(r) }}</span> {{ fmtTime(r.updateTime) }}
                </td>
                <td class="ops" @click.stop>
                  <span @click="toggleExpand(r.id)">{{ expanded === r.id ? '收起' : '展开' }}</span>
                  <span @click="onEdit(r)">编辑</span>
                  <span @click="onPrint(r)">打印</span>
                  <span @click="onCopy(r)">复制新建</span>
                  <span class="del" @click="onDelete(r)">删除</span>
                </td>
              </tr>
              <tr v-if="expanded === r.id" class="exp-row">
                <td :colspan="archiveEnabled ? 12 : 11">
                  <div class="exp">
                    <div class="row">
                      <span class="k">氧合指数 P/F（翻身前 → 复仰后）</span>
                      <div class="spark">
                        <i v-for="(v, i) in summaryOf(r).pfTrend || []" :key="i"
                           :class="sparkClass(v)"
                           :style="{ height: sparkHeight(v) + 'px' }"
                           :title="v == null ? '未采集' : v"></i>
                      </div>
                      <span class="k">最低 ΔP</span>
                      <b>{{ summaryOf(r).dpMin != null ? summaryOf(r).dpMin + ' cmH₂O' : '—' }}</b>
                      <span class="k">并发症</span>
                      <b>{{ compText(r) }}</b>
                      <span class="k">签名</span>
                      <b>{{ signText(r) }}</b>
                      <span class="k">记录编号</span>
                      <b>{{ r.recordNo || '—' }}</b>
                      <span class="spacer"></span>
                      <button v-if="archiveEnabled" class="btn" @click.stop="onArchive(r)">
                        {{ r.archiveStatus === 1 ? '重新回传（幂等）' : '归档回传' }}
                      </button>
                    </div>
                  </div>
                </td>
              </tr>
            </template>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 新建记录 -->
    <div v-if="showNew" class="mask" @click.self="showNew = false">
      <div class="dialog xwide">
        <div class="dh"><h3>新建俯卧位记录</h3></div>
        <div class="db">
          <div class="f">
            <label>住院号 <b class="req">*</b></label>
            <div class="inline">
              <input v-model="newForm.inHospitalNo" class="ipt block" placeholder="如 2026091708"
                     @keyup.enter="onLookup(false)" />
              <button class="btn" @click="onLookup(false)">查询患者</button>
            </div>
          </div>
          <div v-if="lookupMsg" class="tip" :class="{ warn: !newForm.patientId }">{{ lookupMsg }}</div>
          <div class="f">
            <label>患者 ID（自动采集监护 / 呼吸机数据用，可手工修改）</label>
            <input v-model="newForm.patientId" class="ipt block" placeholder="按住院号 + 入科时间自动匹配，留空则按住院号匹配" />
          </div>
          <div class="grid2">
            <div class="f"><label>患者姓名</label><div class="v">{{ newForm.patientName || '—' }}</div></div>
            <div class="f"><label>床号 / 科室</label><div class="v">{{ newForm.bedCode || '—' }} {{ newForm.departCode || '' }}</div></div>
            <div class="f"><label>入院时间</label><div class="v">{{ newForm.admitDate || '—' }}</div></div>
            <div class="f"><label>入科时间</label><div class="v">{{ newForm.inDepartTime || '—' }}</div></div>
          </div>
          <div class="f">
            <label>诊断（取自 HIS，可在填写页修改）</label>
            <div class="v multi">{{ newForm.diagnosis || '—' }}</div>
          </div>
          <div class="f">
            <label>俯卧位开始时间（默认当前时间）</label>
            <el-date-picker
              v-model="newForm.startTime"
              type="datetime"
              value-format="YYYY-MM-DD HH:mm:ss"
              format="YYYY-MM-DD HH:mm"
              placeholder="选择俯卧位开始时间"
              class="w-full"
              :clearable="false"
            />
          </div>
          <div class="tip">
            新建后按<b>科室时点模板</b>自动生成时点；患者基本信息（含诊断、入院日期）由 HIS 带入，缺失项可在填写页补充。
          </div>
        </div>
        <div class="df">
          <span class="spacer"></span>
          <button class="btn" @click="showNew = false">取消</button>
          <button class="btn primary" @click="submitNew">创建</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import '../styles/ards-theme.css'
import {
  fetchArdsProneList, fetchArdsProneSummary, createArdsProneRecord,
  deleteArdsProneRecord, pushArdsProneArchive, lookupArdsPronePatient,
  fetchArdsProneArchiveEnabled
} from '../api/ardsProne'
import { operatorLabel, OPERATOR_MISSING_HINT } from '../utils/operator'

const route = useRoute()
const router = useRouter()

/** 外链参数：住院流水号 + 入科时间（ICU 外链模板带入，用于同一住院号多次入科时精确定位患者） */
const ext = reactive({
  inHospitalSerialNo: route.query.inHospitalSerialNo || '',
  inDepartTime: route.query.inDepartTime || ''
})

const loading = ref(false)
const rows = ref([])
const summaryMap = ref({})
const expanded = ref(null)
const archiveEnabled = ref(false)

const q = reactive({
  inHospitalNo: route.query.inHospitalNo || '',
  departCode: route.query.departCode || '',
  status: ''
})

const showNew = ref(false)
const lookupMsg = ref('')
const newForm = reactive({
  inHospitalNo: '', patientId: '', startTime: '',
  patientName: '', bedCode: '', departCode: '', admitDate: '', inDepartTime: '', diagnosis: ''
})

onMounted(async () => {
  if (route.query.inHospitalNo) q.inHospitalNo = route.query.inHospitalNo
  try { archiveEnabled.value = await fetchArdsProneArchiveEnabled() } catch (e) { archiveEnabled.value = false }
  load()
})

async function load() {
  loading.value = true
  try {
    const list = await fetchArdsProneList(q.inHospitalNo || null, q.departCode || null)
    rows.value = Array.isArray(list) ? list : []
    const ids = rows.value.map(r => r.id)
    if (ids.length) {
      const sum = await fetchArdsProneSummary(ids)
      const m = {}
      ;(sum || []).forEach(s => { m[s.id] = s })
      summaryMap.value = m
    } else {
      summaryMap.value = {}
    }
  } catch (e) {
    console.error('加载俯卧位记录失败', e)
  } finally {
    loading.value = false
  }
}

function onReset() {
  q.inHospitalNo = ''
  q.departCode = ''
  q.status = ''
  load()
}

/** 状态过滤在前端做：列表本身不区分状态参数，避免后端再开口径 */
const rowsFiltered = computed(() => {
  if (!q.status) return rows.value
  return rows.value.filter(r => statKey(r) === q.status)
})

// 模板里统一用 filteredRows；这里把 rows 的渲染改为 filteredRows 的等价实现
const rowsView = computed(() => rowsFiltered.value)

function summaryOf(r) {
  return summaryMap.value[r.id] || { pf0: null, pf1: null, pfTrend: [], dpMin: null, filled: null, total: 37 }
}

const patientCount = computed(() => new Set(rowsView.value.map(r => r.inHospitalNo).filter(Boolean)).size)

/** 单条记录当前已趴分钟数：已结束用 durationMin，进行中实时算 now - startTime */
function recordElapsedMin(r) {
  if (r.durationMin != null && r.durationMin > 0) return r.durationMin
  if (r.startTime && !r.endTime) {
    const t = new Date(String(r.startTime).replace(' ', 'T')).getTime()
    if (!isNaN(t)) {
      const diff = Math.floor((Date.now() - t) / 60000)
      if (diff > 0) return diff
    }
  }
  return 0
}

const stats = computed(() => {
  const list = rowsView.value
  const times = list.length
  const patients = patientCount.value
  const durList = list.map(r => r.durationMin).filter(v => v != null && v > 0)
  const avgMin = durList.length ? durList.reduce((a, b) => a + b, 0) / durList.length : 0
  let improved = 0
  let pfCount = 0
  let compCount = 0
  let totalMin = 0
  let doneCount = 0
  list.forEach(r => {
    const s = summaryOf(r)
    if (s.pf0 != null && s.pf1 != null) {
      pfCount += 1
      if (Number(s.pf1) - Number(s.pf0) >= 20) improved += 1
    }
    if (compList(r).length) compCount += 1
    totalMin += recordElapsedMin(r)
    if (r.endTime) doneCount += 1
  })
  return {
    times,
    patients,
    avgPerPatient: patients ? (times / patients).toFixed(1) : '0.0',
    avgDuration: (avgMin / 60).toFixed(1),
    improveRate: pfCount ? ((improved * 100) / pfCount).toFixed(1) : '0.0',
    compRate: times ? ((compCount * 100) / times).toFixed(1) : '0.0',
    compCount,
    totalMin: (totalMin / 60).toFixed(1),
    doneCount
  }
})

// ---------------------------------------------------------------- 行渲染

/** 日期展示统一到分钟：yyyy-MM-dd HH:mm（后端 recordDate 亦按此格式存储） */
function fmtDate(v) {
  if (!v) return '—'
  const s = String(v)
  return s.length > 16 ? s.slice(0, 16) : s
}
function fmtTime(v) {
  if (!v) return '—'
  return String(v).slice(5, 16)
}
/**
 * 「最后更新」的操作人。
 * unknown 是服务端解析不到身份时写的占位值，不是人名：这里显示成「未知」并挂排查提示，
 * 免得看着像记录被某个叫 unknown 的人改过，而真正的问题（外链没带身份）被一直忽略。
 */
function updBy(r) {
  return operatorLabel(r.updateBy) || '未知'
}
function updByTitle(r) {
  return operatorLabel(r.updateBy) ? '' : OPERATOR_MISSING_HINT
}
function durationText(r) {
  if (!r.durationMin && r.durationMin !== 0) return '—'
  const h = Math.floor(r.durationMin / 60)
  const m = r.durationMin % 60
  return h > 0 ? `${h}h${m ? ' ' + m + 'min' : ''}` : `${m}min`
}
function compList(r) {
  if (!r.complicationJson) return []
  try {
    const arr = JSON.parse(r.complicationJson)
    return Array.isArray(arr) ? arr.filter(x => x && x !== '无并发症') : []
  } catch (e) {
    return r.complicationJson ? [r.complicationJson] : []
  }
}
function compText(r) {
  const c = compList(r)
  return c.length ? c.join(' + ') : '无'
}
function signText(r) {
  const s = [r.doctorSign, r.nurseSign, r.seniorSign].filter(Boolean)
  return s.length ? s.join(' / ') : '未签名'
}
function statKey(r) {
  if (r.stopType === 'emergency') return 'stop'
  if (r.recordStatus === 'submitted') return 'submitted'
  if (r.startTime && !r.endTime) return 'ing'
  return 'draft'
}
function statCell(r) {
  const map = {
    ing: '<span class="tag blue">进行中</span>',
    submitted: '<span class="tag green">已提交</span>',
    stop: '<span class="tag red">紧急终止</span>',
    draft: '<span class="tag gray">草稿</span>'
  }
  return map[statKey(r)]
}
function pfCell(r) {
  const s = summaryOf(r)
  if (s.pf0 == null) return '<span class="muted">—</span>'
  const d = Number(s.pf1) - Number(s.pf0)
  const cls = d >= 20 ? 'up' : (d < 0 ? 'down' : '')
  return `${s.pf0} → ${s.pf1} <span class="${cls}">(${d >= 0 ? '+' : ''}${d})</span>`
}
function compCell(r) {
  const c = compList(r).length
  if (!c) return '<span class="tag green">无</span>'
  return c === 1 ? '<span class="tag orange">1 项</span>' : `<span class="tag red">${c} 项</span>`
}
function arcCell(r) {
  if (r.archiveStatus === 1) {
    return `<span class="arc ok">已归档${r.archiveDocNo ? ' · ' + r.archiveDocNo : ''}</span>`
  }
  return '<span class="arc none">未回传</span>'
}
function sparkClass(v) {
  if (v == null) return ''
  return v < 100 ? 'lo' : (v >= 150 ? 'hi' : '')
}
function sparkHeight(v) {
  if (v == null) return 3
  return Math.max(4, Math.min(22, Math.round(v / 8)))
}

// ---------------------------------------------------------------- 操作

function toggleExpand(id) {
  expanded.value = expanded.value === id ? null : id
}

function onEdit(r) {
  router.push({ path: '/page/ards-prone-record', query: { id: r.id } })
}
function onPrint(r) {
  router.push({ path: '/page/ards-prone-record', query: { id: r.id, scr: 'print' } })
}

function onNew() {
  Object.assign(newForm, {
    inHospitalNo: q.inHospitalNo || '',
    patientId: '',
    startTime: nowText(),
    patientName: '', bedCode: '', departCode: '', admitDate: '', inDepartTime: '', diagnosis: ''
  })
  lookupMsg.value = ''
  showNew.value = true
  // 住院号已知（含外链带入）：自动解析一次患者，带出患者 ID / 诊断 / 入院日期
  if (newForm.inHospitalNo) {
    onLookup(true)
  }
}

/**
 * 患者解析：按住院号 + 外链参数（住院流水号 / 入科时间）查 patient_info，
 * 命中则自动填「患者 ID」，并带出姓名、床号、诊断、入院日期（入科时间）。
 */
async function onLookup(silent) {
  const no = (newForm.inHospitalNo || '').trim()
  if (!no) {
    if (!silent) ElMessage.warning('请先填写住院号')
    return
  }
  try {
    const res = await lookupArdsPronePatient(no, ext.inHospitalSerialNo || null, ext.inDepartTime || null)
    if (res && res.found) {
      newForm.patientId = res.patientId || ''
      newForm.patientName = res.patientName || ''
      newForm.bedCode = res.bedCode || ''
      newForm.departCode = res.departCode || ''
      newForm.inDepartTime = res.inDepartTime || ext.inDepartTime || ''
      newForm.admitDate = res.admitDate || res.inDepartTime || ext.inDepartTime || ''
      newForm.diagnosis = res.diagnosis || ''
      lookupMsg.value = `已匹配患者：${res.patientName || '—'}（患者 ID ${res.patientId || '—'}）；诊断与入院日期将带入记录`
    } else {
      newForm.patientId = ''
      newForm.patientName = ''
      newForm.bedCode = ''
      newForm.departCode = ''
      newForm.inDepartTime = ext.inDepartTime || ''
      newForm.admitDate = ext.inDepartTime || ''
      newForm.diagnosis = ''
      lookupMsg.value = (res && res.message) || '未匹配到患者信息：患者 ID 可留空，后台仍会按住院号尝试匹配'
    }
  } catch (e) {
    lookupMsg.value = '患者查询失败：' + (e.message || '请稍后重试')
  }
}

function nowText() {
  const d = new Date()
  const p = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:00`
}

async function submitNew() {
  if (!newForm.inHospitalNo) {
    ElMessage.warning('请填写住院号')
    return
  }
  try {
    const rec = await createArdsProneRecord(
      newForm.inHospitalNo.trim(), newForm.patientId || null, newForm.startTime || null,
      ext.inHospitalSerialNo || null, ext.inDepartTime || null
    )
    showNew.value = false
    ElMessage.success('记录已创建')
    router.push({ path: '/page/ards-prone-record', query: { id: rec.id } })
  } catch (e) {
    ElMessage.error('创建失败：' + (e.message || '请稍后重试'))
  }
}

async function onCopy(r) {
  try {
    const rec = await createArdsProneRecord(r.inHospitalNo, r.patientId, null, null, null)
    ElMessage.success('已复制新建，时点数据为空待填')
    router.push({ path: '/page/ards-prone-record', query: { id: rec.id } })
  } catch (e) {
    ElMessage.error('复制新建失败：' + (e.message || '请稍后重试'))
  }
}

async function onDelete(r) {
  try {
    await ElMessageBox.confirm('确定删除该记录？删除后不再出现在列表中（历史数据保留可追溯）。', '删除记录', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning', customClass: 'ards-overlay'
    })
  } catch (e) {
    return
  }
  try {
    await deleteArdsProneRecord(r.id)
    ElMessage.success('已删除')
    rows.value = rows.value.filter(x => x.id !== r.id)
  } catch (e) {
    ElMessage.error('删除失败：' + (e.message || '请稍后重试'))
  }
}

async function onArchive(r) {
  try {
    const res = await pushArdsProneArchive(r.id)
    if (res && res.idempotent) {
      ElMessage.info('该记录已归档，未重复推送')
    } else {
      ElMessage.success('归档回传成功' + (res && res.docNo ? '，文档号 ' + res.docNo : ''))
    }
    load()
  } catch (e) {
    ElMessage.error('归档回传失败：' + (e.message || '请稍后重试'))
  }
}
</script>

<style scoped>
.ards-page {
  max-width: 1600px; margin: 0 auto; padding: 16px 20px 60px;
  color: var(--el-text-color-primary);
}
* { box-sizing: border-box; }

/* 顶部条 */
.topbar {
  display: flex; align-items: center; gap: 14px; background: #fff;
  border: 1px solid var(--el-border-color); border-radius: var(--ards-card-radius);
  padding: 16px 20px; margin-bottom: 14px; box-shadow: var(--ards-card-shadow);
}
.topbar h1 { font-size: 20px; font-weight: 600; margin: 0; color: var(--ards-title-color); }
.topbar .sub { font-size: 12px; color: var(--el-text-color-placeholder); margin-top: 4px; }
.spacer { flex: 1; }

/* 按钮 */
.btn {
  border: 1px solid var(--el-border-color); background: #fff; color: var(--el-text-color-regular);
  border-radius: var(--ards-ctl-radius); padding: 7px 14px; font-size: 13px; font-weight: 500;
  cursor: pointer; transition: background .15s ease, border-color .15s ease, color .15s ease;
}
.btn:hover {
  color: var(--el-color-primary); border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}
.btn.primary { background: var(--el-color-primary); border-color: var(--el-color-primary); color: #fff; }
.btn.primary:hover {
  background: var(--el-color-primary-dark-2); border-color: var(--el-color-primary-dark-2); color: #fff;
}

/* 新建记录：ARDS 主题 teal 实色，和统计数字同色系但实色填充，一眼可辨 */
.btn.btn-new {
  background: var(--ards-accent-dark);
  border-color: var(--ards-accent-dark);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 2px 6px rgba(15, 118, 110, 0.25);
}
.btn.btn-new:hover {
  background: var(--ards-accent);
  border-color: var(--ards-accent);
  color: #fff;
  box-shadow: 0 3px 10px rgba(13, 148, 136, 0.35);
}

/* 筛选 */
.filter {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap; background: #fff;
  border: 1px solid var(--el-border-color); border-radius: var(--ards-card-radius);
  padding: 12px 14px; margin-bottom: 14px; box-shadow: var(--ards-card-shadow);
}
.filter .ipt, .ipt {
  border: 1px solid var(--el-border-color); border-radius: var(--ards-ctl-radius);
  padding: 6px 10px; min-width: 160px; font-size: 13px; color: var(--el-text-color-primary);
  background: #fff; height: 34px; outline: none;
  transition: border-color .15s ease, box-shadow .15s ease;
}
.ipt:focus {
  border-color: var(--el-color-primary); box-shadow: 0 0 0 3px var(--el-color-primary-light-8);
}
.ipt::placeholder { color: var(--el-text-color-placeholder); }
.ipt.block { width: 100%; margin-top: 4px; }
.w120 { min-width: 120px; }
.w110 { min-width: 110px; }
.filter .cnt { font-size: 12px; color: var(--el-text-color-placeholder); }

/* 统计卡：数值用 ARDS 识别色（青蓝），与暖橙的操作语义区分 */
.stat-row { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 12px; margin-bottom: 14px; }
.stat {
  background: #fff; border: 1px solid var(--el-border-color);
  border-radius: var(--ards-card-radius); padding: 14px 16px; box-shadow: var(--ards-card-shadow);
}
.stat .k { font-size: 12px; color: var(--el-text-color-secondary); }
.stat .v {
  font-size: 26px; font-weight: 600; margin-top: 6px; line-height: 1.1;
  color: var(--ards-accent-dark);
}
.stat .v small { font-size: 12px; font-weight: 400; color: var(--el-text-color-placeholder); margin-left: 3px; }
.stat .s { font-size: 12px; color: var(--el-text-color-placeholder); margin-top: 4px; }

/* 卡片 */
.card {
  background: #fff; border: 1px solid var(--el-border-color);
  border-radius: var(--ards-card-radius); overflow: hidden; box-shadow: var(--ards-card-shadow);
}
.card > .hd {
  display: flex; align-items: center; gap: 12px; padding: 14px 18px;
  border-bottom: 1px solid var(--el-border-color-lighter); background: #fff;
}
.card > .hd h2 {
  font-size: 15px; font-weight: 600; margin: 0; padding-left: 10px; color: var(--ards-title-color);
  border-left: 3px solid var(--el-color-primary); line-height: 16px;
}
.card > .hd .hint { font-size: 12px; color: var(--el-text-color-placeholder); }

.loading { padding: 30px; text-align: center; color: var(--el-text-color-placeholder); font-size: 13px; }

/* 列表表格 */
table.lst { width: 100%; border-collapse: collapse; font-size: 13px; }
table.lst th {
  background: var(--el-table-header-bg-color); border-bottom: 1px solid var(--el-border-color);
  padding: 10px 12px; text-align: left; font-weight: 600; font-size: 12px;
  color: var(--el-table-header-text-color); white-space: nowrap;
}
table.lst td {
  border-bottom: 1px solid var(--el-border-color-lighter); padding: 10px 12px;
  color: var(--el-text-color-regular); white-space: nowrap;
}
table.lst tbody tr.row:hover td { background: var(--el-table-row-hover-bg-color); cursor: pointer; }
table.lst .nm { font-weight: 600; color: var(--el-text-color-primary); }
table.lst .sub { color: var(--el-text-color-placeholder); font-size: 12px; }
table.lst .up { color: var(--el-color-success); font-weight: 600; }
table.lst .down { color: var(--el-color-danger); font-weight: 600; }
table.lst .muted { color: var(--el-text-color-disabled); }
table.lst .fill { color: var(--ards-accent-dark); font-size: 12px; margin-left: 4px; }
table.lst .upd { color: var(--el-text-color-placeholder); font-size: 12px; }
table.lst td.ops { color: var(--el-color-primary); }
table.lst td.ops span { cursor: pointer; margin-right: 10px; }
table.lst td.ops span:hover { color: var(--el-color-primary-dark-2); }
table.lst td.ops span.del { color: var(--el-color-danger); }
table.lst td.empty { text-align: center; color: var(--el-text-color-disabled); padding: 40px 0; }

/* 标签：胶囊 + 柔和底色 */
.tag {
  display: inline-block; padding: 2px 10px; border-radius: 9999px; font-size: 12px;
  line-height: 18px; border: 1px solid transparent; font-weight: 500;
}
.tag.blue { background: var(--ards-accent-wash); color: var(--ards-accent-dark); border-color: var(--ards-accent-soft); }
.tag.green { background: #dcfce7; color: #16a34a; border-color: #bbf7d0; }
.tag.orange { background: #ffedd5; color: #c2410c; border-color: #fed7aa; }
.tag.red { background: #fee2e2; color: #dc2626; border-color: #fecaca; }
.tag.gray { background: #f5f5f4; color: #78716c; border-color: #e7e5e4; }

.arc { font-size: 12px; }
.arc.ok { color: var(--el-color-success); }
.arc.fail { color: var(--el-color-danger); }
.arc.none { color: var(--el-text-color-disabled); }

/* 展开区 */
.exp-row td { background: var(--el-fill-color-light); }
.exp {
  border: 1px solid var(--el-border-color); border-radius: var(--ards-card-radius);
  padding: 12px 14px; background: #fff;
}
.exp .row { display: flex; gap: 20px; align-items: center; font-size: 12px; color: var(--el-text-color-regular); }
.exp .row .k { color: var(--el-text-color-placeholder); }
.exp .row b { color: var(--ards-accent-dark); font-weight: 600; }
.spark { display: flex; align-items: flex-end; gap: 2px; height: 22px; }
.spark i { display: block; width: 6px; background: var(--ards-accent-soft); border-radius: 2px; }
.spark i.hi { background: var(--el-color-success-light-3); }
.spark i.lo { background: var(--el-color-danger-light-5); }

/* 弹窗 */
.mask { position: fixed; inset: 0; background: var(--el-mask-color); z-index: 60; }
.dialog {
  position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%); width: 460px;
  max-width: 92vw; background: #fff; border-radius: var(--ards-card-radius); z-index: 61;
  overflow: hidden; box-shadow: 0 12px 32px rgba(28, 25, 23, .18);
}
.dialog .dh { padding: 16px 20px; border-bottom: 1px solid var(--el-border-color-extra-light); }
.dialog .dh h3 {
  margin: 0; font-size: 15px; font-weight: 600; color: var(--ards-title-color);
  padding-left: 10px; border-left: 3px solid var(--el-color-primary); line-height: 16px;
}
.dialog .db { padding: 18px 20px; }
.dialog .df {
  padding: 12px 20px; border-top: 1px solid var(--el-border-color-extra-light);
  display: flex; gap: 10px; align-items: center; background: var(--el-fill-color-light);
}
.f { display: flex; flex-direction: column; gap: 6px; margin-bottom: 14px; }
.f > label { font-size: 12px; color: var(--el-text-color-secondary); }
.req { color: var(--el-color-danger); }
.inline { display: flex; gap: 8px; align-items: center; }
.grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }
.f > .v {
  border: 1px solid var(--el-border-color); border-radius: var(--ards-ctl-radius);
  padding: 6px 10px; min-height: 34px; display: flex; align-items: center;
  background: var(--el-fill-color-light); font-size: 13px; color: var(--el-text-color-regular);
}
.f > .v.multi { align-items: flex-start; line-height: 1.6; max-height: 96px; overflow: auto; }
.w-full { width: 100%; }
.tip {
  font-size: 12px; color: var(--el-text-color-regular); background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter); border-radius: var(--ards-ctl-radius);
  padding: 10px 12px; line-height: 1.7;
}
.tip.warn { color: #92400e; background: #fffbeb; border-color: #fde68a; }
</style>
