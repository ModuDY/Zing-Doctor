<template>
  <div class="dim-page">
    <header class="page-head">
      <div>
        <h1>数据项映射配置</h1>
        <p class="sub">
          各院 ICU 的观察项编码、LIS 项目名不一样，这里统一维护「程序内部键 → 本院实际编码」。
          所有取数页面共用这一套配置，改一次全部生效；未配置的项自动回退内置常量。
        </p>
      </div>
      <div class="head-actions">
        <button class="btn" :disabled="loading" @click="load">刷新</button>
        <button class="btn primary" @click="openEdit(null)">新增映射</button>
      </div>
    </header>

    <div class="tabs">
      <button
        v-for="d in domains"
        :key="d.domain"
        class="tab"
        :class="{ on: d.domain === domain }"
        @click="switchDomain(d.domain)"
      >
        {{ d.name }}
        <span class="tag" :class="d.wired ? 'ok' : 'pending'">{{ d.wired ? '已接入' : '待接入' }}</span>
      </button>
    </div>

    <div class="note">
      {{ currentDomain.note || '' }}
      <span v-if="currentDomain && !currentDomain.wired" class="note-warn">
        本组配置已预置，取数接入在下一批；现在改动不会影响页面数据。
      </span>
    </div>

    <section class="card">
      <div class="card-head">
        <input
          v-model="keyword"
          class="ipt w320"
          placeholder="过滤：内部键 / 显示名 / 匹配值"
        />
        <span class="spacer"></span>
        <span class="cnt">共 {{ filtered.length }} 条 · 启用 {{ enabledCount }} 条</span>
      </div>

      <div v-if="loading" class="state">加载中…</div>
      <div v-else-if="!filtered.length" class="state">
        暂无配置。若这是首次部署，请先执行 <code>sql/66_config_data_item.sql</code> 写入初始化数据。
      </div>
      <table v-else class="lst">
        <thead>
          <tr>
            <th class="w110">内部键</th>
            <th class="w180">显示名</th>
            <th v-if="isLab" class="w120">分组</th>
            <th class="w80">单位</th>
            <th class="w90">来源</th>
            <th class="w110">匹配方式</th>
            <th>匹配值</th>
            <th v-if="isLab" class="w160">排除词</th>
            <th class="w70">状态</th>
            <th class="w150">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in filtered" :key="r.id" :class="{ off: r.status === 0 }">
            <td class="mono">{{ r.itemKey }}</td>
            <td>{{ r.itemName }}</td>
            <td v-if="isLab" class="dim">{{ r.groupName || r.groupCode || '—' }}</td>
            <td>{{ r.unit || '—' }}</td>
            <td>{{ r.source || '—' }}</td>
            <td>{{ matchText(r.matchType) }}</td>
            <td class="mono">{{ r.matchValue }}</td>
            <td v-if="isLab" class="dim small">{{ r.excludeValue || '—' }}</td>
            <td>
              <span class="tag" :class="r.status === 1 ? 'ok' : 'pending'">
                {{ r.status === 1 ? '启用' : '停用' }}
              </span>
            </td>
            <td class="ops">
              <button class="link" @click="openEdit(r)">编辑</button>
              <button class="link" @click="toggleStatus(r)">{{ r.status === 1 ? '停用' : '启用' }}</button>
              <button class="link danger" @click="remove(r)">删除</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <!-- 编辑弹窗 -->
    <div v-if="edit.open" class="mask" @click.self="closeEdit">
      <div class="dlg">
        <div class="dlg-head">
          <h2>{{ edit.id ? '编辑映射' : '新增映射' }}</h2>
          <button class="x" @click="closeEdit">×</button>
        </div>

        <div class="dlg-body">
          <div class="grid2">
            <label>
              <span>业务域</span>
              <input v-model="form.domain" class="ipt" readonly />
            </label>
            <label>
              <span>内部键 *<i class="tip">程序按它取值，如 hr / peep / alt，不要改</i></span>
              <input v-model="form.itemKey" class="ipt mono" placeholder="hr" />
            </label>
            <label>
              <span>显示名 *</span>
              <input v-model="form.itemName" class="ipt" placeholder="心率" />
            </label>
            <label>
              <span>单位</span>
              <input v-model="form.unit" class="ipt" placeholder="次/分" />
            </label>
            <label v-if="!isLab">
              <span>来源标注</span>
              <input v-model="form.source" class="ipt" placeholder="无创 / 有创 / 未标明" />
            </label>
            <label>
              <span>排序号</span>
              <input v-model.number="form.sortNo" class="ipt" type="number" />
            </label>
            <template v-if="isLab">
              <label>
                <span>分组编码</span>
                <select v-model="form.groupCode" class="ipt">
                  <option value="">（不分组）</option>
                  <option value="LIVER">LIVER 肝功能</option>
                  <option value="COAG">COAG 凝血</option>
                  <option value="RENAL">RENAL 肾功能与电解质</option>
                  <option value="INFECTION">INFECTION 感染指标</option>
                </select>
              </label>
              <label>
                <span>分组显示名</span>
                <input v-model="form.groupName" class="ipt" placeholder="肾小球滤过功能" />
              </label>
            </template>
          </div>

          <div class="row">
            <label class="grow">
              <span>匹配方式 *</span>
              <select v-model="form.matchType" class="ipt">
                <option value="CODE">CODE 编码精确</option>
                <option value="NAME_LIKE">NAME_LIKE 名称包含</option>
                <option value="NAME_EXACT">NAME_EXACT 名称相等</option>
              </select>
            </label>
            <label class="grow">
              <span>匹配值 *<i class="tip">多个用逗号分隔</i></span>
              <input v-model="form.matchValue" class="ipt mono" placeholder="oi_hr" />
            </label>
          </div>

          <div class="row">
            <label class="grow">
              <span>排除词<i class="tip">仅名称匹配用，多个用竖线分隔</i></span>
              <input v-model="form.excludeValue" class="ipt mono" placeholder="尿|透析液" />
            </label>
            <label class="grow">
              <span>备注</span>
              <input v-model="form.remark" class="ipt" />
            </label>
          </div>

          <div class="assist">
            <button class="btn small" :disabled="cand.loading" @click="openCandidates">
              {{ cand.open ? '收起候选' : '从源库选（候选值）' }}
            </button>
            <button class="btn small" :disabled="probe.loading" @click="runProbe">
              {{ probe.loading ? '试跑中…' : '试跑命中' }}
            </button>
            <span class="assist-hint">改完不必猜：先试跑，能看到近 7 天命中多少条</span>
          </div>

          <!-- 候选值 -->
          <div v-if="cand.open" class="cand">
            <div class="cand-head">
              <input v-model="cand.keyword" class="ipt" placeholder="按编码或名称搜索源库项目" @keyup.enter="openCandidates" />
              <button class="btn small" :disabled="cand.loading" @click="openCandidates">搜索</button>
              <span class="spacer"></span>
              <span class="cnt">{{ cand.list.length }} 条</span>
            </div>
            <div v-if="cand.loading" class="state small">读取源库…</div>
            <div v-else-if="cand.error" class="state small err">{{ cand.error }}</div>
            <div v-else class="cand-list">
              <button
                v-for="(c, i) in cand.list"
                :key="i"
                class="cand-item"
                :class="{ hit: c.code === form.matchValue || c.name === form.matchValue }"
                @click="pickCandidate(c)"
              >
                <b class="mono">{{ c.code || c.name }}</b>
                <span v-if="c.code && c.name && c.name !== c.code" class="dim">{{ c.name }}</span>
                <span class="cnt">{{ c.cnt || 0 }}</span>
              </button>
            </div>
          </div>

          <!-- 试跑结果 -->
          <div v-if="probe.result || probe.error" class="probe">
            <div v-if="probe.error" class="err">试跑失败：{{ probe.error }}</div>
            <template v-else>
              <div class="probe-sum">
                近 <b>{{ probe.result.days }}</b> 天命中 <b>{{ probe.result.count }}</b> {{ probe.result.unit || '条' }}
                <span v-if="probe.result.count === 0" class="err">　→ 这个值取不到数据，检查编码/项目名是否正确</span>
              </div>
              <table v-if="probe.result.samples && probe.result.samples.length" class="lst small">
                <thead>
                  <tr><th>时间</th><th>项目</th><th>值</th></tr>
                </thead>
                <tbody>
                  <tr v-for="(s, i) in probe.result.samples" :key="i">
                    <td>{{ s.time || '—' }}</td>
                    <td>{{ s.name || s.code || '—' }}</td>
                    <td class="mono">{{ s.value == null ? '—' : s.value }}</td>
                  </tr>
                </tbody>
              </table>
            </template>
          </div>
        </div>

        <div class="dlg-foot">
          <span v-if="edit.error" class="err">{{ edit.error }}</span>
          <span class="spacer"></span>
          <button class="btn" @click="closeEdit">取消</button>
          <button class="btn primary" :disabled="edit.saving" @click="submit">
            {{ edit.saving ? '保存中…' : '保存并生效' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchDataItemDomains,
  fetchDataItems,
  saveDataItem,
  setDataItemStatus,
  deleteDataItem,
  fetchDataItemCandidates,
  probeDataItem
} from '../api/dataItem'

const domains = ref([])
const domain = ref('VITAL')
const rows = ref([])
const keyword = ref('')
const loading = ref(false)

const edit = reactive({ open: false, id: null, saving: false, error: '' })
const form = reactive(emptyForm())

const cand = reactive({ open: false, loading: false, list: [], keyword: '', error: '' })
const probe = reactive({ loading: false, result: null, error: '' })

const currentDomain = computed(() => domains.value.find(d => d.domain === domain.value) || {})
const isLab = computed(() => domain.value === 'LAB')
const enabledCount = computed(() => rows.value.filter(r => r.status === 1).length)

const filtered = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return rows.value
  return rows.value.filter(r =>
    [r.itemKey, r.itemName, r.matchValue, r.groupName, r.groupCode, r.remark]
      .filter(Boolean)
      .some(v => String(v).toLowerCase().includes(kw))
  )
})

function emptyForm() {
  return {
    id: null,
    domain: 'VITAL',
    groupCode: '',
    groupName: '',
    itemKey: '',
    itemName: '',
    unit: '',
    source: '',
    matchType: 'CODE',
    matchValue: '',
    excludeValue: '',
    sortNo: 0,
    status: 1,
    remark: ''
  }
}

function matchText(t) {
  if (t === 'NAME_LIKE') return '名称包含'
  if (t === 'NAME_EXACT') return '名称相等'
  return '编码精确'
}

async function loadDomains() {
  try {
    const list = await fetchDataItemDomains()
    domains.value = Array.isArray(list) ? list : []
    if (domains.value.length && !domains.value.some(d => d.domain === domain.value)) {
      domain.value = domains.value[0].domain
    }
  } catch (e) {
    domains.value = [
      { domain: 'VITAL', name: '生命体征', wired: true, note: '' },
      { domain: 'LAB', name: '检验项目', wired: false, note: '' }
    ]
  }
}

async function load() {
  loading.value = true
  try {
    const list = await fetchDataItems(domain.value)
    rows.value = Array.isArray(list) ? list : []
  } catch (e) {
    rows.value = []
    ElMessage.error('读取配置失败：' + (e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

function switchDomain(d) {
  if (domain.value === d) return
  domain.value = d
  keyword.value = ''
  closeEdit()
  load()
}

function openEdit(row) {
  Object.assign(form, emptyForm())
  form.domain = domain.value
  if (row) {
    Object.assign(form, {
      id: row.id,
      domain: row.domain,
      groupCode: row.groupCode || '',
      groupName: row.groupName || '',
      itemKey: row.itemKey,
      itemName: row.itemName,
      unit: row.unit || '',
      source: row.source || '',
      matchType: row.matchType || 'CODE',
      matchValue: row.matchValue,
      excludeValue: row.excludeValue || '',
      sortNo: row.sortNo || 0,
      status: row.status,
      remark: row.remark || ''
    })
  } else {
    form.matchType = domain.value === 'LAB' ? 'NAME_LIKE' : 'CODE'
  }
  edit.open = true
  edit.error = ''
  cand.open = false
  cand.list = []
  probe.result = null
  probe.error = ''
}

function closeEdit() {
  edit.open = false
  edit.error = ''
  cand.open = false
  probe.result = null
  probe.error = ''
}

async function submit() {
  if (!form.itemKey.trim() || !form.itemName.trim() || !form.matchValue.trim()) {
    edit.error = '内部键、显示名、匹配值都必须填写'
    return
  }
  edit.saving = true
  edit.error = ''
  try {
    await saveDataItem({ ...form })
    edit.open = false
    ElMessage.success('已保存，取数立即生效')
    await load()
  } catch (e) {
    edit.error = e.message || '保存失败'
  } finally {
    edit.saving = false
  }
}

async function toggleStatus(row) {
  try {
    await setDataItemStatus(row.id, row.status === 1 ? 0 : 1)
    ElMessage.success(row.status === 1 ? '已停用' : '已启用')
    await load()
  } catch (e) {
    ElMessage.error('操作失败：' + (e.message || '未知错误'))
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `删除后「${row.itemName}」将不再参与取数；若同域还有别行配置它，那些行不受影响。`,
      '删除映射',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  try {
    await deleteDataItem(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error('删除失败：' + (e.message || '未知错误'))
  }
}

async function openCandidates() {
  if (cand.open && cand.loading) return
  cand.open = true
  cand.loading = true
  cand.error = ''
  try {
    const list = await fetchDataItemCandidates(domain.value, form.itemKey.trim(), cand.keyword.trim())
    cand.list = Array.isArray(list) ? list : []
  } catch (e) {
    cand.list = []
    cand.error = '候选值读取失败：' + (e.message || '未知错误')
  } finally {
    cand.loading = false
  }
}

function pickCandidate(c) {
  form.matchValue = c.code || c.name || ''
  if (!form.itemName.trim()) {
    form.itemName = c.name || c.code || ''
  }
}

async function runProbe() {
  const v = form.matchValue.trim()
  if (!v) {
    probe.error = '请先填写匹配值'
    probe.result = null
    return
  }
  probe.loading = true
  probe.error = ''
  probe.result = null
  try {
    probe.result = await probeDataItem(domain.value, form.itemKey.trim(), v, 7)
  } catch (e) {
    probe.error = e.message || '未知错误'
  } finally {
    probe.loading = false
  }
}

onMounted(async () => {
  await loadDomains()
  await load()
})
</script>

<style scoped>
.dim-page { padding: 18px 22px 40px; }

.page-head { display: flex; align-items: flex-start; gap: 16px; margin-bottom: 14px; }
.page-head h1 { font-size: 20px; font-weight: 700; color: #1c1917; margin: 0 0 6px; }
.page-head .sub { font-size: 12.5px; line-height: 1.7; color: #78716c; max-width: 900px; margin: 0; }
.head-actions { margin-left: auto; display: flex; gap: 8px; }

.tabs { display: flex; gap: 6px; border-bottom: 1px solid #e7e5e4; margin-bottom: 10px; }
.tab {
  border: none; background: none; cursor: pointer; padding: 9px 14px;
  font-size: 13.5px; color: #57534e; border-bottom: 2px solid transparent;
  display: flex; align-items: center; gap: 6px;
}
.tab.on { color: #b45309; border-bottom-color: #b45309; font-weight: 600; }

.note { font-size: 12px; color: #78716c; margin-bottom: 12px; line-height: 1.7; }
.note-warn { color: #b45309; margin-left: 6px; }

.card { background: #fff; border: 1px solid #e7e5e4; border-radius: 10px; overflow: hidden; }
.card-head { display: flex; align-items: center; gap: 8px; padding: 10px 12px; border-bottom: 1px solid #f0efed; }
.spacer { flex: 1; }
.cnt { font-size: 12px; color: #a8a29e; }

.ipt {
  height: 30px; padding: 0 9px; font-size: 13px; color: #292524;
  border: 1px solid #d6d3d1; border-radius: 6px; background: #fff; outline: none;
}
.ipt:focus { border-color: #f59e0b; }
.w320 { width: 320px; }

.btn {
  height: 30px; padding: 0 12px; font-size: 13px; cursor: pointer;
  border: 1px solid #d6d3d1; border-radius: 6px; background: #fff; color: #44403c;
}
.btn:hover { border-color: #f59e0b; color: #b45309; }
.btn.primary { background: #b45309; border-color: #b45309; color: #fff; }
.btn.primary:hover { background: #92400e; }
.btn.small { height: 27px; padding: 0 10px; font-size: 12px; }
.btn:disabled { opacity: .55; cursor: not-allowed; }

.state { padding: 26px 14px; text-align: center; color: #a8a29e; font-size: 13px; line-height: 1.8; }
.state.small { padding: 14px; font-size: 12px; }
.state.err { color: #b91c1c; }
code { background: #f5f5f4; padding: 1px 5px; border-radius: 4px; font-size: 12px; }

.lst { width: 100%; border-collapse: collapse; font-size: 13px; }
.lst th, .lst td { padding: 8px 10px; text-align: left; border-bottom: 1px solid #f5f5f4; vertical-align: middle; }
.lst th { background: #fafaf9; font-weight: 600; color: #57534e; font-size: 12px; white-space: nowrap; }
.lst tbody tr:hover { background: #fffbf5; }
.lst tr.off { color: #a8a29e; }
.lst.small { font-size: 12px; }
.mono { font-family: ui-monospace, Menlo, Consolas, monospace; font-size: 12px; }
.dim { color: #78716c; }
.small { font-size: 12px; }
.w70 { width: 70px; } .w80 { width: 80px; } .w90 { width: 90px; }
.w110 { width: 110px; } .w120 { width: 120px; } .w150 { width: 150px; }
.w160 { width: 160px; } .w180 { width: 180px; }
.ops { white-space: nowrap; }
.link { border: none; background: none; cursor: pointer; color: #b45309; font-size: 12.5px; padding: 0 6px 0 0; }
.link:hover { text-decoration: underline; }
.link.danger { color: #b91c1c; }

.tag { font-size: 11px; padding: 1px 6px; border-radius: 9px; white-space: nowrap; }
.tag.ok { background: #ecfdf5; color: #047857; }
.tag.pending { background: #f5f5f4; color: #a8a29e; }

.mask {
  position: fixed; inset: 0; background: rgba(28, 25, 23, .38);
  display: flex; align-items: flex-start; justify-content: center; z-index: 60; padding-top: 5vh;
}
.dlg { width: 780px; max-width: 94vw; max-height: 88vh; display: flex; flex-direction: column;
  background: #fff; border-radius: 12px; box-shadow: 0 20px 50px rgba(0,0,0,.18); }
.dlg-head { display: flex; align-items: center; padding: 14px 18px; border-bottom: 1px solid #f0efed; }
.dlg-head h2 { font-size: 15px; margin: 0; color: #1c1917; }
.dlg-head .x { margin-left: auto; border: none; background: none; font-size: 20px; color: #a8a29e; cursor: pointer; }
.dlg-body { padding: 16px 18px; overflow: auto; }
.dlg-foot { display: flex; align-items: center; gap: 8px; padding: 12px 18px; border-top: 1px solid #f0efed; }

.grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 10px 14px; }
.row { display: flex; gap: 14px; margin-top: 10px; }
.grow { flex: 1; }
label { display: flex; flex-direction: column; gap: 5px; font-size: 12px; color: #57534e; }
label > span { display: flex; align-items: baseline; gap: 6px; }
.tip { font-style: normal; color: #a8a29e; font-size: 11px; }

.assist { display: flex; align-items: center; gap: 8px; margin-top: 14px; }
.assist-hint { font-size: 11.5px; color: #a8a29e; }

.cand { margin-top: 10px; border: 1px solid #e7e5e4; border-radius: 8px; overflow: hidden; }
.cand-head { display: flex; align-items: center; gap: 8px; padding: 8px 10px; background: #fafaf9; border-bottom: 1px solid #f0efed; }
.cand-list { max-height: 220px; overflow: auto; }
.cand-item {
  width: 100%; display: flex; align-items: center; gap: 10px; padding: 6px 10px;
  border: none; background: none; cursor: pointer; text-align: left; font-size: 12.5px; color: #292524;
  border-bottom: 1px solid #fafaf9;
}
.cand-item:hover { background: #fffbf5; }
.cand-item.hit { background: #fef3c7; }
.cand-item .dim { color: #78716c; }
.cand-item .cnt { margin-left: auto; color: #a8a29e; }

.probe { margin-top: 10px; border: 1px solid #e7e5e4; border-radius: 8px; padding: 10px 12px; }
.probe-sum { font-size: 12.5px; color: #44403c; margin-bottom: 8px; }
.err { color: #b91c1c; font-size: 12.5px; }
</style>
