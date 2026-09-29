<template>
  <div :class="['ards-page ards-theme', { embedded }]">
    <div class="topbar">
      <div>
        <h1>ARDS 俯卧位数据映射配置</h1>
        <div class="sub">
          维护「参数项 ← 监护 / LIS 项目」的映射；未配置或未命中的参数自动回退内置关键字，配置保存后取数立即生效
        </div>
      </div>
      <div class="spacer"></div>
      <button class="btn" @click="onSeed">一键从内置生成</button>
      <button class="btn" @click="togglePreview">{{ previewOpen ? '收起试采' : '试采核对' }}</button>
      <button class="btn primary" @click="openEdit(null)">新增映射</button>
    </div>

    <!-- 试采（dry-run） -->
    <div v-if="previewOpen" class="card">
      <div class="hd">
        <h2>试采核对</h2>
        <span class="hint">与正式采集同一取数与匹配口径，仅预览不写库；用来确认「哪个项目映射到哪个值」</span>
      </div>
      <div class="bd">
        <div class="bar">
          <input v-model="pv.keyword" class="ipt" placeholder="住院号 / 患者姓名" @keyup.enter="loadRecords" />
          <button class="btn" @click="loadRecords">查询记录</button>
          <select v-model="pv.recordId" class="ipt w300" @change="onRecordChange">
            <option :value="null">请选择记录</option>
            <option v-for="r in pvRecords" :key="r.id" :value="r.id">
              {{ r.recordNo || '#' + r.id }} · {{ r.patientName || r.inHospitalNo }} · {{ fmtTime(r.startTime) }}
            </option>
          </select>
          <select v-model="pv.tpIndex" class="ipt w150">
            <option :value="null">请选择时点</option>
            <option v-for="t in pvTps" :key="t.tpIndex" :value="t.tpIndex">{{ t.tpLabel }}</option>
          </select>
          <button class="btn primary" :disabled="!pv.recordId || pv.tpIndex === null || pv.running" @click="runPreview">
            开始试采
          </button>
        </div>

        <div v-if="pvResult" class="sum">
          计划时间 <b>{{ pvResult.planTime }}</b> · 命中 <b>{{ pvResult.filled }}</b> 项 ·
          窗口内无数据 <b>{{ pvResult.pending }}</b> 项 ·
          已有值保留 <b>{{ pvResult.kept }}</b> 项 ·
          取到监护记录 {{ pvResult.observeCount }} 条 / 检验记录 {{ pvResult.labCount }} 条
          <div class="hint">字段说明：来源为「规则」表示走配置，为「内置」表示回退字典关键字；无数据项需现场手工录入</div>
        </div>

        <table v-if="pvResult" class="lst">
          <thead>
            <tr>
              <th>分组</th>
              <th>参数项</th>
              <th>取到的值</th>
              <th>来源</th>
              <th>命中方式</th>
              <th>来源项目</th>
              <th>项目编码</th>
              <th>源时间</th>
              <th>窗口</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(it, i) in pvItems" :key="i" :class="{ miss: it.from === 'miss', keep: it.from === 'keep' }">
              <td>{{ it.group }}</td>
              <td>{{ it.paramName }}</td>
              <td class="val">{{ it.value || '—' }}</td>
              <td><span class="tag" :class="fromClass(it)">{{ fromText(it) }}</span></td>
              <td>{{ matchText(it) }}</td>
              <td>{{ it.itemName || '—' }}</td>
              <td class="mono">{{ it.itemCode || '—' }}</td>
              <td>{{ it.itemTime || '—' }}</td>
              <td>{{ it.windowMin ? '±' + it.windowMin + ' min' : '—' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 规则列表 -->
    <div class="card">
      <div class="hd">
        <h2>映射规则</h2>
        <span class="hint">同参数可多行：优先级数字小的先试，同优先级先监护后退检验；全部未命中回退内置关键字</span>
        <div class="spacer"></div>
        <select v-model="filter.configType" class="ipt w150" @change="loadList">
          <option value="">全部通道</option>
          <option value="observe_item">监护 / 呼吸机</option>
          <option value="lis_item">检验 / 血气</option>
        </select>
        <select v-model="filter.configKey" class="ipt w240" @change="loadList">
          <option value="">全部参数项</option>
          <option v-for="p in dict" :key="p.key" :value="p.key">{{ p.group }} · {{ p.name }}</option>
        </select>
        <button class="btn" @click="loadList">刷新</button>
      </div>
      <div class="bd" style="padding: 0">
        <div v-if="loading" class="loading">加载中…</div>
        <table v-else class="lst">
          <thead>
            <tr>
              <th>参数项</th>
              <th>通道</th>
              <th>匹配方式</th>
              <th>匹配值</th>
              <th>项目名称</th>
              <th>优先级</th>
              <th>窗口</th>
              <th>换算</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="!list.length">
              <td colspan="10" class="empty">
                暂无映射规则。点右上角「一键从内置生成」，把现有内置关键字变成可视化配置（可重复执行，不会重复生成）
              </td>
            </tr>
            <tr v-for="c in list" :key="c.id" :class="{ off: c.status !== 1 }">
              <td>
                <span class="nm">{{ paramName(c.configKey) }}</span>
                <div class="sub mono">{{ c.configKey }}</div>
              </td>
              <td>{{ channelText(c.configType) }}</td>
              <td>{{ c.matchType === 'code' ? '编码精确' : '名称包含' }}</td>
              <td class="val">{{ c.configValue }}</td>
              <td>{{ c.itemName || '—' }}</td>
              <td>{{ c.priority }}</td>
              <td>{{ c.windowMin ? '±' + c.windowMin + ' min' : '默认' }}</td>
              <td>{{ scaleText(c) }}</td>
              <td>
                <span class="tag" :class="c.status === 1 ? 'green' : 'gray'">{{ c.status === 1 ? '启用' : '停用' }}</span>
              </td>
              <td class="ops">
                <span @click="openEdit(c)">编辑</span>
                <span @click="onToggle(c)">{{ c.status === 1 ? '停用' : '启用' }}</span>
                <span class="del" @click="onDelete(c)">删除</span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>

    <!-- 新增 / 编辑 -->
    <div v-if="dlg.show" class="mask" @click.self="dlg.show = false">
      <div class="dialog wide">
        <div class="dh"><h3>{{ dlg.form.id ? '编辑映射规则' : '新增映射规则' }}</h3></div>
        <div class="db">
          <div class="f2">
            <label>参数项 <b class="req">*</b></label>
            <select v-model="dlg.form.configKey" class="ipt block">
              <optgroup v-for="g in dictGroups" :key="g" :label="g">
                <option v-for="p in dictOf(g)" :key="p.key" :value="p.key">{{ p.name }}（{{ p.key }}）</option>
              </optgroup>
            </select>
          </div>
          <div class="f2">
            <label>数据源通道 <b class="req">*</b></label>
            <select v-model="dlg.form.configType" class="ipt block">
              <option value="observe_item">监护 / 呼吸机（观察项）</option>
              <option value="lis_item">检验 / 血气（LIS）</option>
            </select>
          </div>
          <div class="f2">
            <label>匹配方式</label>
            <select v-model="dlg.form.matchType" class="ipt block" @change="candidates = []">
              <option value="name">名称包含（模糊，兼容现有内置关键字）</option>
              <option value="code">item_code 精确（推荐，稳定）</option>
            </select>
          </div>
          <div class="f2">
            <label>优先级（数字小优先）</label>
            <input v-model="dlg.form.priority" class="ipt block" placeholder="监护 10 / 检验 20" />
          </div>
          <div class="f2 full">
            <label>匹配值 <b class="req">*</b>（多个用逗号分隔，任一命中即可）</label>
            <textarea
              v-model="dlg.form.configValue"
              class="bx"
              rows="3"
              :placeholder="dlg.form.matchType === 'code' ? '如 hr_pl,hr_art / 200050' : '如 心率,脉搏,HR'"
            ></textarea>
            <div class="cand">
              <button class="btn" @click="loadCandidates">选择候选</button>
              <input v-model="candKeyword" class="ipt" placeholder="按编码 / 名称过滤后回车" @keyup.enter="loadCandidates" />
              <span class="hint">{{ candidates.length ? candidates.length + ' 项候选，点击追加' : '候选来自 ICU 观察项字典 / 近 7 天 LIS 项目' }}</span>
            </div>
            <div v-if="candidates.length" class="cand-list">
              <span v-for="cd in candidates" :key="cd.item_code" class="cand-item" @click="pickCandidate(cd)">
                {{ cd.item_name }}<i class="mono">{{ cd.item_code }}</i>
              </span>
            </div>
          </div>
          <div class="f2">
            <label>项目名称（展示用）</label>
            <input v-model="dlg.form.itemName" class="ipt block" placeholder="如 心率（监护）" />
          </div>
          <div class="f2">
            <label>采集窗口覆盖（分钟，空=默认 ±15 / ±60）</label>
            <input v-model="dlg.form.windowMin" class="ipt block" placeholder="15 / 60" />
          </div>
          <div class="f2">
            <label>单位换算系数（值 × 系数 + 偏移）</label>
            <input v-model="dlg.form.unitScale" class="ipt block" placeholder="如 FiO₂ 0.4→40 填 100" />
          </div>
          <div class="f2">
            <label>单位换算偏移</label>
            <input v-model="dlg.form.unitOffset" class="ipt block" placeholder="一般留空" />
          </div>
          <div class="f2 full">
            <label>备注</label>
            <input v-model="dlg.form.remark" class="ipt block" placeholder="说明该项口径 / 注意事项" />
          </div>
          <div class="tip">
            提示：名称包含匹配存在歧义风险（如「二氧化碳分压」会同时命中 PaCO₂ 与 PvCO₂），
            现场确认到具体数据元后建议改用 item_code 精确匹配；同参数配多行时用优先级表达「监护优先 / 检验兜底」。
          </div>
        </div>
        <div class="df">
          <span class="spacer"></span>
          <button class="btn" @click="dlg.show = false">取消</button>
          <button class="btn primary" :disabled="dlg.saving" @click="onSave">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import '../styles/ards-theme.css'
import {
  fetchArdsProneDict, fetchArdsProneList, fetchArdsProneRecord,
  fetchArdsProneMap, saveArdsProneMap, deleteArdsProneMap, toggleArdsProneMap,
  seedArdsProneMap, fetchArdsProneMapCandidates, previewArdsProneCollect
} from '../api/ardsProne'

/** embedded=true：嵌入「参数设置」页签时去掉外层留白（独立页面访问时为 false） */
defineProps({
  embedded: { type: Boolean, default: false }
})

const dict = ref([])
const list = ref([])
const loading = ref(false)
const filter = reactive({ configType: '', configKey: '' })

const dlg = reactive({
  show: false,
  saving: false,
  form: emptyForm()
})
const candidates = ref([])
const candKeyword = ref('')

const previewOpen = ref(false)
const previewReady = ref(false)
const pv = reactive({ keyword: '', loading: false, recordId: null, tpIndex: null, running: false })
const records = ref([])
const tps = ref([])
const pvResult = ref(null)

function emptyForm() {
  return {
    id: null, configKey: '', configType: 'observe_item', matchType: 'name',
    configValue: '', itemName: '', priority: 10, windowMin: '', unitScale: '', unitOffset: '',
    remark: '', status: 1, sortNo: 1
  }
}

onMounted(() => {
  loadDict()
  loadList()
})

// ---------------------------------------------------------------- 基础数据

async function loadDict() {
  try {
    const res = await fetchArdsProneDict()
    dict.value = Array.isArray(res) ? res : []
  } catch (e) {
    ElMessage.error('参数字典加载失败：' + (e.message || '请稍后重试'))
  }
}

const dictGroups = computed(() => {
  const out = []
  dict.value.forEach(p => { if (p.group && !out.includes(p.group)) out.push(p.group) })
  return out
})

function dictOf(group) {
  return dict.value.filter(p => p.group === group)
}

function paramName(key) {
  const p = dict.value.find(x => x.key === key)
  return p ? p.name : key
}

async function loadList() {
  loading.value = true
  try {
    const res = await fetchArdsProneMap(filter.configType || null, filter.configKey || null)
    list.value = Array.isArray(res) ? res : []
  } catch (e) {
    ElMessage.error('映射配置加载失败：' + (e.message || '请稍后重试'))
  } finally {
    loading.value = false
  }
}

// ---------------------------------------------------------------- 文案

function channelText(t) {
  return t === 'lis_item' ? '检验 / 血气' : '监护 / 呼吸机'
}

function scaleText(c) {
  if (c.unitScale == null && c.unitOffset == null) return '—'
  const s = c.unitScale == null ? '1' : c.unitScale
  const o = c.unitOffset == null ? '0' : c.unitOffset
  return `×${s} + ${o}`
}

function fromText(it) {
  if (it.from === 'rule') return '规则'
  if (it.from === 'builtin') return '内置'
  if (it.from === 'keep') return '已填保留'
  return '无数据'
}

function fromClass(it) {
  if (it.from === 'rule') return 'green'
  if (it.from === 'builtin') return 'blue'
  if (it.from === 'keep') return 'gray'
  return 'orange'
}

function matchText(it) {
  if (!it.matchType) return '—'
  const base = it.matchType === 'code' ? '编码' : '名称'
  return it.from === 'builtin' ? base + '（内置）' : base
}

function fmtTime(v) {
  return v ? String(v).slice(5, 16) : '—'
}

// ---------------------------------------------------------------- 编辑

function openEdit(c) {
  candidates.value = []
  candKeyword.value = ''
  if (c) {
    dlg.form = {
      id: c.id, configKey: c.configKey, configType: c.configType,
      matchType: c.matchType || 'name', configValue: c.configValue || '',
      itemName: c.itemName || '', priority: c.priority == null ? 10 : c.priority,
      windowMin: c.windowMin == null ? '' : c.windowMin,
      unitScale: c.unitScale == null ? '' : c.unitScale,
      unitOffset: c.unitOffset == null ? '' : c.unitOffset,
      remark: c.remark || '', status: c.status == null ? 1 : c.status, sortNo: c.sortNo == null ? 1 : c.sortNo
    }
  } else {
    dlg.form = emptyForm()
  }
  dlg.show = true
}

async function onSave() {
  if (!dlg.form.configKey) { ElMessage.warning('请选择参数项'); return }
  if (!dlg.form.configValue || !dlg.form.configValue.trim()) { ElMessage.warning('请填写匹配值'); return }
  dlg.saving = true
  try {
    await saveArdsProneMap({ ...dlg.form })
    ElMessage.success('已保存，取数立即生效')
    dlg.show = false
    loadList()
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || '请稍后重试'))
  } finally {
    dlg.saving = false
  }
}

async function onDelete(c) {
  try {
    await ElMessageBox.confirm(
      `确定删除该映射规则？\n${paramName(c.configKey)} · ${channelText(c.configType)} · ${c.configValue}`,
      '删除映射规则',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning', customClass: 'ards-overlay' }
    )
  } catch (e) { return }
  try {
    await deleteArdsProneMap(c.id)
    ElMessage.success('已删除')
    loadList()
  } catch (e) {
    ElMessage.error('删除失败：' + (e.message || '请稍后重试'))
  }
}

async function onToggle(c) {
  try {
    await toggleArdsProneMap(c.id, c.status === 1 ? 0 : 1)
    ElMessage.success(c.status === 1 ? '已停用（该参数回退内置关键字）' : '已启用')
    loadList()
  } catch (e) {
    ElMessage.error('操作失败：' + (e.message || '请稍后重试'))
  }
}

async function onSeed() {
  try {
    await ElMessageBox.confirm(
      '将按当前内置关键字生成映射规则（已存在的同参数 + 同通道 + 同匹配值会跳过，可重复执行）。确定继续？',
      '一键从内置生成',
      { confirmButtonText: '生成', cancelButtonText: '取消', type: 'info', customClass: 'ards-overlay' }
    )
  } catch (e) { return }
  try {
    const n = await seedArdsProneMap()
    ElMessage.success(n > 0 ? `已新增 ${n} 条映射规则` : '已是最新，无新增规则')
    loadList()
  } catch (e) {
    ElMessage.error('生成失败：' + (e.message || '请稍后重试'))
  }
}

// ---------------------------------------------------------------- 候选数据元

async function loadCandidates() {
  try {
    const type = dlg.form.configType === 'lis_item' ? 'lis' : 'observe'
    const res = await fetchArdsProneMapCandidates(type, candKeyword.value || null, 200)
    candidates.value = Array.isArray(res) ? res.filter(x => x.item_code || x.item_name) : []
    if (!candidates.value.length) ElMessage.info('未查询到候选数据元，可直接手工填写')
  } catch (e) {
    ElMessage.error('候选查询失败：' + (e.message || '请稍后重试'))
  }
}

function pickCandidate(cd) {
  const val = dlg.form.matchType === 'code' ? cd.item_code : cd.item_name
  if (!val) return
  const parts = String(dlg.form.configValue || '')
    .split(/[,，]/).map(s => s.trim()).filter(Boolean)
  if (parts.includes(val.trim())) { ElMessage.info('该值已在匹配值中'); return }
  parts.push(val.trim())
  dlg.form.configValue = parts.join(',')
  if (!dlg.form.itemName) dlg.form.itemName = cd.item_name || ''
}

// ---------------------------------------------------------------- 试采

function togglePreview() {
  previewOpen.value = !previewOpen.value
  if (previewOpen.value && !previewReady.value) {
    previewReady.value = true
    loadRecords()
  }
}

async function loadRecords() {
  pv.loading = true
  try {
    const res = await fetchArdsProneList(pv.keyword || null, null)
    records.value = (Array.isArray(res) ? res : []).slice(0, 100)
    if (!records.value.length) ElMessage.info('未查询到记录，可先到列表页新建一条')
  } catch (e) {
    ElMessage.error('记录查询失败：' + (e.message || '请稍后重试'))
  } finally {
    pv.loading = false
  }
}

async function onRecordChange() {
  pv.tpIndex = null
  pvResult.value = null
  tps.value = []
  if (!pv.recordId) return
  try {
    const view = await fetchArdsProneRecord(pv.recordId)
    tps.value = view && Array.isArray(view.timepoints) ? view.timepoints : []
  } catch (e) {
    ElMessage.error('时点加载失败：' + (e.message || '请稍后重试'))
  }
}

async function runPreview() {
  if (!pv.recordId || pv.tpIndex === null) return
  pv.running = true
  try {
    const res = await previewArdsProneCollect(pv.recordId, pv.tpIndex)
    pvResult.value = res || null
  } catch (e) {
    ElMessage.error('试采失败：' + (e.message || '请稍后重试'))
  } finally {
    pv.running = false
  }
}

const pvRecords = computed(() => records.value)
const pvTps = computed(() => tps.value)
const pvItems = computed(() => (pvResult.value && Array.isArray(pvResult.value.items) ? pvResult.value.items : []))
</script>

<style scoped>
.ards-page { max-width: 1600px; margin: 0 auto; padding: 16px 20px 60px; color: var(--el-text-color-primary); }
/* 嵌入「参数设置」页签时去掉外层留白与最大宽度 */
.ards-page.embedded { max-width: none; margin: 0; padding: 0; }
* { box-sizing: border-box; }
.spacer { flex: 1; }
.hint { font-size: 12px; color: var(--el-text-color-placeholder); }
.mono { font-family: Consolas, Monaco, monospace; font-size: 12px; }

.topbar {
  display: flex; align-items: center; gap: 14px; background: #fff;
  border: 1px solid var(--el-border-color); border-radius: var(--ards-card-radius);
  padding: 16px 20px; margin-bottom: 14px; box-shadow: var(--ards-card-shadow);
}
.topbar h1 { font-size: 20px; font-weight: 600; margin: 0; color: var(--ards-title-color); }
.topbar .sub { font-size: 12px; color: var(--el-text-color-placeholder); margin-top: 4px; }

.btn {
  border: 1px solid var(--el-border-color); background: #fff; color: var(--el-text-color-regular);
  border-radius: var(--ards-ctl-radius); padding: 7px 14px; font-size: 13px; font-weight: 500;
  cursor: pointer; transition: background .15s ease, border-color .15s ease, color .15s ease;
}
.btn:hover { color: var(--el-color-primary); border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); }
.btn:disabled { opacity: .55; cursor: not-allowed; }
.btn.primary { background: var(--el-color-primary); border-color: var(--el-color-primary); color: #fff; }
.btn.primary:hover { background: var(--el-color-primary-dark-2); border-color: var(--el-color-primary-dark-2); color: #fff; }

.ipt {
  border: 1px solid var(--el-border-color); border-radius: var(--ards-ctl-radius);
  padding: 6px 10px; font-size: 13px; color: var(--el-text-color-primary);
  background: #fff; height: 34px; outline: none; min-width: 160px;
  transition: border-color .15s ease, box-shadow .15s ease;
}
.ipt:focus { border-color: var(--el-color-primary); box-shadow: 0 0 0 3px var(--el-color-primary-light-8); }
.ipt.block { width: 100%; min-width: 0; }
.w150 { min-width: 150px; }
.w240 { min-width: 240px; }
.w300 { min-width: 300px; }

.card {
  background: #fff; border: 1px solid var(--el-border-color); border-radius: var(--ards-card-radius);
  overflow: hidden; margin-bottom: 14px; box-shadow: var(--ards-card-shadow);
}
.card > .hd {
  display: flex; align-items: center; gap: 12px; padding: 14px 18px; flex-wrap: wrap;
  border-bottom: 1px solid var(--el-border-color-lighter); background: #fff;
}
.card > .hd h2 {
  font-size: 15px; font-weight: 600; margin: 0; padding-left: 10px; color: var(--ards-title-color);
  border-left: 3px solid var(--el-color-primary); line-height: 16px;
}
.card > .bd { padding: 14px 18px; }

.bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-bottom: 12px; }
.sum {
  font-size: 12px; color: var(--el-text-color-regular); background: var(--ards-accent-bg);
  border: 1px solid var(--ards-accent-wash); border-radius: var(--ards-ctl-radius);
  padding: 10px 12px; margin-bottom: 12px; line-height: 1.8;
}
.sum b { color: var(--ards-accent-dark); }

.loading { padding: 30px; text-align: center; color: var(--el-text-color-placeholder); font-size: 13px; }

table.lst { width: 100%; border-collapse: collapse; font-size: 13px; }
table.lst th {
  background: var(--el-table-header-bg-color); border-bottom: 1px solid var(--el-border-color);
  padding: 10px 12px; text-align: left; font-weight: 600; font-size: 12px;
  color: var(--el-table-header-text-color); white-space: nowrap;
}
table.lst td {
  border-bottom: 1px solid var(--el-border-color-lighter); padding: 10px 12px;
  color: var(--el-text-color-regular); vertical-align: top;
}
table.lst tbody tr:hover td { background: var(--el-table-row-hover-bg-color); }
table.lst tr.off td { opacity: .55; }
table.lst tr.miss td { background: #fff7ed; }
table.lst tr.keep td { color: var(--el-text-color-placeholder); }
table.lst .nm { font-weight: 600; color: var(--el-text-color-primary); }
table.lst .sub { color: var(--el-text-color-placeholder); font-size: 12px; }
table.lst .val { font-family: Consolas, Monaco, monospace; color: var(--ards-accent-dark); }
table.lst td.empty { text-align: center; color: var(--el-text-color-disabled); padding: 40px 0; }
table.lst td.ops { color: var(--el-color-primary); white-space: nowrap; }
table.lst td.ops span { cursor: pointer; margin-right: 10px; }
table.lst td.ops span:hover { color: var(--el-color-primary-dark-2); }
table.lst td.ops span.del { color: var(--el-color-danger); }

.tag {
  display: inline-block; padding: 2px 10px; border-radius: 9999px; font-size: 12px;
  line-height: 18px; border: 1px solid transparent; font-weight: 500; white-space: nowrap;
}
.tag.blue { background: var(--ards-accent-wash); color: var(--ards-accent-dark); border-color: var(--ards-accent-soft); }
.tag.green { background: #dcfce7; color: #16a34a; border-color: #bbf7d0; }
.tag.orange { background: #ffedd5; color: #c2410c; border-color: #fed7aa; }
.tag.gray { background: #f5f5f4; color: #78716c; border-color: #e7e5e4; }

.mask { position: fixed; inset: 0; background: var(--el-mask-color); z-index: 60; }
.dialog {
  position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%); width: 480px;
  max-width: 92vw; background: #fff; border-radius: var(--ards-card-radius); z-index: 61;
  overflow: hidden; box-shadow: 0 12px 32px rgba(28, 25, 23, .18);
}
.dialog.wide { width: 860px; }
.dialog .dh { padding: 16px 20px; border-bottom: 1px solid var(--el-border-color-extra-light); }
.dialog .dh h3 {
  margin: 0; font-size: 15px; font-weight: 600; color: var(--ards-title-color);
  padding-left: 10px; border-left: 3px solid var(--el-color-primary); line-height: 16px;
}
.dialog .db { padding: 18px 20px; max-height: 66vh; overflow: auto; display: grid; grid-template-columns: 1fr 1fr; gap: 14px 16px; }
.dialog .df {
  padding: 12px 20px; border-top: 1px solid var(--el-border-color-extra-light);
  display: flex; gap: 10px; align-items: center; background: var(--el-fill-color-light);
}
.f2 { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.f2.full { grid-column: span 2; }
.f2 > label { font-size: 12px; color: var(--el-text-color-secondary); }
.req { color: var(--el-color-danger); }
.bx {
  width: 100%; min-height: 62px; border: 1px solid var(--el-border-color);
  border-radius: var(--ards-ctl-radius); padding: 8px 10px; font: 13px/1.6 inherit;
  color: var(--el-text-color-regular); background: #fff; resize: vertical; outline: none;
}
.bx:focus { border-color: var(--el-color-primary); box-shadow: 0 0 0 3px var(--el-color-primary-light-8); }
.cand { display: flex; align-items: center; gap: 10px; margin-top: 8px; flex-wrap: wrap; }
.cand-list {
  margin-top: 8px; max-height: 140px; overflow: auto; border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--ards-ctl-radius); padding: 8px; display: flex; flex-wrap: wrap; gap: 6px;
  background: var(--el-fill-color-light);
}
.cand-item {
  font-size: 12px; background: #fff; border: 1px solid var(--el-border-color); border-radius: 9999px;
  padding: 3px 10px; cursor: pointer; color: var(--el-text-color-regular);
}
.cand-item:hover { color: var(--el-color-primary); border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); }
.cand-item i { margin-left: 6px; color: var(--el-text-color-placeholder); font-style: normal; }
.tip {
  grid-column: span 2; font-size: 12px; color: var(--el-text-color-regular);
  background: var(--el-fill-color-light); border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--ards-ctl-radius); padding: 10px 12px; line-height: 1.7;
}
</style>
