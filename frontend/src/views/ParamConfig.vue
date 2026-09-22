<template>
  <div class="param-config">
    <div class="pc-tabs">
      <button :class="['pc-tab', { on: tab === 'param' }]" @click="tab = 'param'">系统参数</button>
      <button :class="['pc-tab', { on: tab === 'map' }]" @click="tab = 'map'">ARDS 数据映射</button>
      <button :class="['pc-tab', { on: tab === 'link' }]" @click="tab = 'link'">外链页面</button>
    </div>

    <div v-show="tab === 'param'" class="pc-layout">
      <!-- 左：分组导航（数据来自库表，页面可维护，新增功能模块不必改代码） -->
      <aside class="pc-side">
        <div class="side-head">
          <span class="side-title">参数分组</span>
          <el-button link type="primary" size="small" @click="openGroupManager">管理</el-button>
        </div>
        <ul class="group-list">
          <li :class="['group-item', { active: currentGroup === '' }]" @click="currentGroup = ''">
            <span class="g-name">全部参数</span>
            <span class="g-count">{{ list.length }}</span>
          </li>
          <li
            v-for="g in groups"
            :key="g.id"
            :class="['group-item', { active: currentGroup === g.groupCode }]"
            @click="currentGroup = g.groupCode"
          >
            <span class="g-name" :title="g.remark">{{ g.groupName }}</span>
            <span class="g-count">{{ countOf(g.groupCode) }}</span>
          </li>
        </ul>
        <div v-if="!groups.length" class="side-empty">暂无分组，点「管理」新增</div>
      </aside>

      <!-- 右：参数列表 -->
      <section class="pc-main">
        <div class="main-head">
          <div class="mh-left">
            <h2>{{ currentGroupName }}</h2>
            <p class="g-desc">{{ currentGroupDesc || '维护该分组下的参数，修改后立即生效' }}</p>
          </div>
          <div class="main-actions">
            <el-input v-model="keyword" placeholder="搜索名称 / 参数键 / 备注" clearable class="kw" />
            <el-button type="primary" @click="openAdd">新增参数</el-button>
            <el-button :loading="loading" @click="loadAll">刷新</el-button>
          </div>
        </div>

        <div v-loading="loading" class="param-list">
          <div v-for="p in filteredList" :key="p.id" class="param-card">
            <div class="pc-left">
              <div class="pc-title">
                <span class="p-name">{{ p.paramName }}</span>
                <el-tag size="small" type="info" effect="plain">{{ p.paramKey }}</el-tag>
                <el-tag v-if="p.required === 1" size="small" type="danger" effect="plain">必填</el-tag>
                <el-tag v-if="p.status === 0" size="small" type="info">已停用</el-tag>
              </div>
              <div class="pc-remark">{{ p.remark || '—' }}</div>
            </div>

            <div class="pc-right">
              <!-- 开关型：就地切换，不用进弹窗 -->
              <el-switch
                v-if="p.paramType === 'switch'"
                :model-value="p.paramValue === '1'"
                active-text="开" inactive-text="关"
                @change="(v) => onSwitchChange(p, v)"
              />

              <!-- 下拉型：就地选择 -->
              <el-select
                v-else-if="p.paramType === 'select'"
                :model-value="p.paramValue"
                size="default"
                class="p-select"
                @change="(v) => onSelectChange(p, v)"
              >
                <el-option
                  v-for="opt in optionsOf(p)"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>

              <!-- 其余类型只读展示，点编辑修改 -->
              <span v-else class="p-value" :class="{ empty: !p.paramValue }">
                {{ shortValue(p) }}
              </span>

              <el-button link type="primary" @click="openEdit(p)">编辑</el-button>
              <el-button link type="danger" @click="onDelete(p)">删除</el-button>
            </div>
          </div>

          <el-empty v-if="!filteredList.length && !loading" description="该分组下暂无参数" />
        </div>
      </section>
    </div>

    <!-- ARDS 采集映射规则：数据存 config_prone_item 表（规则表，与上面的键值型参数分开维护） -->
    <div v-show="tab === 'map'">
      <ArdsProneConfig embedded />
    </div>

    <!-- 外链页面一览：静态内置清单，便于 ICU 集成方直接取用完整 URL -->
    <div v-show="tab === 'link'" class="link-wrap">
      <div class="link-intro">
        <h3>系统外链页面一览</h3>
        <p>外部系统（如 ICU 信息系统）通过 <code>/entry/{pageCode}</code> 免登录打开本系统功能页。两种鉴权方式任选其一：</p>
        <ul>
          <li><b>ICU 固定令牌（推荐）</b>：URL 带 <code>extToken=已分配的固定令牌</code>，最简单，ICU 外链模板直接写死即可。</li>
          <li><b>签名校验</b>：URL 带 <code>expire=过期时间戳&amp;sign=按密钥生成的签名</code>，安全性高，由后端程序实时生成。</li>
        </ul>
        <p>通用业务参数（建议带）：<code>realname=医生姓名</code> —— 会记录为操作人，页面「评分医生」也据此显示。把示例中的 <code>{host}</code>、<code>{token}</code> 替换为实际部署地址与令牌即可使用。</p>
        <div class="link-actions">
          <el-button type="primary" size="small" @click="onGenToken">随机生成新令牌</el-button>
          <span class="link-token-tip">当前令牌：<b>{{ maskedToken }}</b>&emsp;对外地址：<b>{{ currentBase() || '{host}' }}</b></span>
        </div>
      </div>

      <div class="link-toolbar">
        <el-input v-model="linkKw" placeholder="搜索页面名称 / pageCode" clearable class="link-kw" />
        <span class="link-count">共 {{ filteredLinks.length }} 个页面</span>
      </div>

      <div class="link-list">
        <div v-for="pg in filteredLinks" :key="pg.code" class="link-card">
          <div class="lc-head">
            <span class="lc-name">{{ pg.name }}</span>
            <el-tag size="small" type="primary" effect="plain" class="lc-code">{{ pg.code }}</el-tag>
          </div>
          <div class="lc-desc">{{ pg.desc }}</div>
          <div v-if="pg.params && pg.params.length" class="lc-params">
            <span class="lc-p-label">业务参数：</span>
            <el-tag v-for="pa in pg.params" :key="pa.k" size="small" :type="pa.req ? 'danger' : 'info'" effect="plain">
              {{ pa.k }}{{ pa.req ? '（必填）' : '' }}
            </el-tag>
          </div>
          <div class="lc-url">
            <code class="lc-url-text">{{ buildExample(pg) }}</code>
            <el-button link type="primary" size="small" @click="copyLink(pg)">复制</el-button>
          </div>
        </div>
        <el-empty v-if="!filteredLinks.length && !loading" description="没有匹配的页面" />
      </div>
    </div>
    <!-- 参数新增 / 编辑 -->
    <el-dialog
      v-model="dialogVisible"
      :title="form.id ? '编辑参数' : '新增参数'"
      width="620px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-width="100px">
        <el-form-item label="参数名称" required>
          <el-input v-model="form.paramName" placeholder="如：文书归档接口地址" />
        </el-form-item>
        <el-form-item label="参数键" required>
          <el-input v-model="form.paramKey" :disabled="!!form.id" placeholder="如：ARCHIVE_API_URL" />
          <div class="form-tip">程序读取用的唯一标识，新增后不可修改</div>
        </el-form-item>
        <el-form-item label="参数类型" required>
          <el-select v-model="form.paramType" class="w-full" @change="onTypeChange">
            <el-option label="单行文本" value="text" />
            <el-option label="多行文本" value="textarea" />
            <el-option label="数字" value="number" />
            <el-option label="开关" value="switch" />
            <el-option label="下拉选择" value="select" />
          </el-select>
          <div class="form-tip">决定参数在列表页上的控件形态：开关与下拉可就地修改</div>
        </el-form-item>

        <el-form-item v-if="form.paramType === 'switch'" label="参数值">
          <el-switch v-model="switchValue" active-text="开" inactive-text="关" />
        </el-form-item>
        <el-form-item v-else-if="form.paramType === 'select'" label="参数值">
          <el-select v-model="form.paramValue" class="w-full">
            <el-option v-for="opt in formOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-else-if="form.paramType === 'number'" label="参数值">
          <el-input v-model="form.paramValue" type="number" placeholder="数字" />
        </el-form-item>
        <el-form-item v-else label="参数值">
          <el-input
            v-model="form.paramValue"
            :type="form.paramType === 'textarea' ? 'textarea' : 'text'"
            :rows="3"
            placeholder="参数值"
          />
        </el-form-item>

        <el-form-item v-if="form.paramType === 'select'" label="选项">
          <el-input
            v-model="form.options"
            type="textarea"
            :rows="3"
            placeholder='[{"label":"口令同工号","value":"WORK_NO"},{"label":"固定初始口令","value":"FIXED"}]'
          />
          <div class="form-tip">JSON 数组，label 为显示名、value 为存储值</div>
        </el-form-item>

        <el-form-item label="所属分组" required>
          <el-select v-model="form.paramGroup" class="w-full">
            <el-option v-for="g in groups" :key="g.id" :label="g.groupName" :value="g.groupCode" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="form.sortNo" :min="0" :step="1" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0"
                     active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="参数用途说明，会显示在列表上" />
        </el-form-item>

        <el-collapse class="adv">
          <el-collapse-item title="高级（默认值 / 校验）" name="adv">
            <el-form-item label="默认值">
              <el-input v-model="form.defaultValue" placeholder="参数值留空时取用" />
              <div class="form-tip">列表页把参数值清空即恢复为默认值</div>
            </el-form-item>
            <el-form-item label="必填">
              <el-switch v-model="form.required" :active-value="1" :inactive-value="0"
                         active-text="必填" inactive-text="选填" />
            </el-form-item>
            <el-form-item label="校验正则">
              <el-input v-model="form.regex" placeholder="如：^https?://.+" />
              <div class="form-tip">保存时校验参数值，不匹配则拒绝保存</div>
            </el-form-item>
          </el-collapse-item>
        </el-collapse>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分组管理 -->
    <el-dialog v-model="groupDialogVisible" title="参数分组管理" width="700px" :close-on-click-modal="false">
      <div class="gm-tip">
        分组决定参数设置页左侧的导航与参数归类。新增功能模块时先在这里加分组，再把参数挂上去即可，无需改代码。
      </div>
      <el-table :data="groups" border size="small">
        <el-table-column prop="groupName" label="分组名称" min-width="130" />
        <el-table-column prop="groupCode" label="分组编码" width="160" />
        <el-table-column prop="sortNo" label="排序" width="70" align="center" />
        <el-table-column label="参数数" width="80" align="center">
          <template #default="{ row }">{{ countOf(row.groupCode) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openGroupEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="onGroupDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="gm-add">
        <el-button type="primary" size="small" @click="openGroupAdd">新增分组</el-button>
      </div>
    </el-dialog>

    <el-dialog
      v-model="groupFormVisible"
      :title="groupForm.id ? '编辑分组' : '新增分组'"
      width="520px"
      :close-on-click-modal="false"
      append-to-body
    >
      <el-form :model="groupForm" label-width="88px">
        <el-form-item label="分组名称" required>
          <el-input v-model="groupForm.groupName" placeholder="如：外链集成" />
        </el-form-item>
        <el-form-item label="分组编码" required>
          <el-input v-model="groupForm.groupCode" :disabled="!!groupForm.id" placeholder="如：external" />
          <div class="form-tip">参数的所属分组按此编码匹配，新增后不可修改</div>
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="groupForm.sortNo" :min="0" :step="1" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="groupForm.status" :active-value="1" :inactive-value="0"
                     active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="groupForm.remark" type="textarea" :rows="2" placeholder="分组用途说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupFormVisible = false">取消</el-button>
        <el-button type="primary" :loading="groupSaving" @click="onGroupSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import request from '../api/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import ArdsProneConfig from './ArdsProneConfig.vue'

/** 顶部页签：param=系统参数（键值型），map=ARDS 采集映射（规则表型） */
const tab = ref('param')

/* ---------- 外链页面一览（与 sys_page_config 已注册页面对应） ---------- */
const linkKw = ref('')
const LINK_BASE = 'https://{host}'
const LINK_TOKEN = '{token}'
const LINK_PAGES = [
  { code: 'abx-patient-list', name: '疑似感染患者列表', desc: '第一维度：疑似感染/脓毒症患者总览，按科室过滤', params: [
    { k: 'departCode', req: false, sample: 'ICU01' },
    { k: 'departName', req: false, sample: '综合ICU' } ] },
  { code: 'abx-decision', name: '经验性抗感染治疗决策', desc: '单患者经验性抗感染方案决策', params: [
    { k: 'patientId', req: true, sample: '10001' },
    { k: 'inHospitalNo', req: true, sample: '201152869' } ] },
  { code: 'abx-pkpd', name: 'PK/PD 抗菌药物剂量优化', desc: '基于肾功能/体重/低蛋白/CRRT 的抗菌药物剂量个体化', params: [
    { k: 'patientId', req: true, sample: '10001' },
    { k: 'inHospitalNo', req: true, sample: '201152869' } ] },
  { code: 'abx-ddd', name: '抗菌药物使用强度分析', desc: '全院/科室抗菌药物使用率、DDDs、药品排名、趋势', params: [
    { k: 'departCode', req: false, sample: 'ICU01' } ] },
  { code: 'abx-ddd-config', name: 'DDD 值配置管理', desc: '抗菌药物 DDD 值知识库后台配置', params: [] },
  { code: 'abx-mdro', name: '细菌培养检出监测', desc: '菌株排名、标本分布、趋势、高风险菌预警', params: [] },
  { code: 'abx-mdro-config', name: '细菌分类配置管理', desc: '革兰阳性/阴性/真菌、高风险菌列表后台配置', params: [] },
  { code: 'sepsis-bundle', name: '脓毒症休克集束化治疗', desc: '1H/3H/6H 集束化治疗完成情况自动判断与记录', params: [
    { k: 'inHospitalNo', req: true, sample: '201152869' } ] },
  { code: 'abx-word-config', name: '抗菌药物识别词库配置', desc: '广谱抗菌药白名单 / 非抗菌药黑名单后台配置', params: [] },
  { code: 'handover-board', name: '医生交班览表', desc: '按上一全天班次汇总在科患者病情/生命体征/器官支持', params: [] },
  { code: 'discharge-stats', name: '患者出科统计', desc: '按出科时间范围和科室查询已出科患者，支持导出', params: [] },
  { code: 'ards-monitor', name: 'ARDS 监测', desc: 'ARDS 患者识别、柏林定义分级、肺保护通气依从性', params: [
    { k: 'departCode', req: false, sample: 'ICU01' } ] },
  { code: 'apache2-overview', name: 'APACHE II 评分总览', desc: '主任视角：科室评分统计、分布、时机对比、患者列表', params: [
    { k: 'departCode', req: false, sample: 'ICU01' },
    { k: 'departName', req: false, sample: '综合ICU' } ] },
  { code: 'apache2-score', name: 'APACHE II 评分评估', desc: '单患者 APACHE II 评分录入与保存', params: [
    { k: 'inHospitalNo', req: true, sample: '201152869' },
    { k: 'patientName', req: false, sample: '陈丽珍' },
    { k: 'departCode', req: false, sample: 'ICU01' },
    { k: 'realname', req: false, sample: '张医生' } ] },
  { code: 'sofa-score', name: 'SOFA 评分', desc: 'SOFA 6 器官评分 + 总分 + 趋势', params: [
    { k: 'inHospitalNo', req: true, sample: '201152869' },
    { k: 'patientId', req: false, sample: '10001' },
    { k: 'realname', req: false, sample: '张医生' } ] },
  { code: 'sofa-overview', name: 'SOFA 评分总览', desc: '科室评分分布、ΔSOFA 恶化预警、患者列表', params: [
    { k: 'departCode', req: false, sample: 'ICU01' } ] },
  { code: 'sofa-config', name: 'SOFA 配置管理', desc: 'SOFA 取数项映射、血管活性药阈值、换算系数、默认体重', params: [] },
  { code: 'ards-prone-list', name: 'ARDS 俯卧位通气记录', desc: '按住院号查看俯卧位历史疗程与归档状态', params: [
    { k: 'inHospitalNo', req: true, sample: '201152869' },
    { k: 'departCode', req: false, sample: 'ICU01' },
    { k: 'inHospitalSerialNo', req: false, sample: '20260904001' },
    { k: 'inDepartTime', req: false, sample: '2026-09-04 08:00' } ] },
  { code: 'ards-prone-record', name: 'ARDS 俯卧位记录填写', desc: '37 项参数 × 时点矩阵、打印预览与 PDF 导出', params: [
    { k: 'id', req: true, sample: '1024' },
    { k: 'scr', req: false, sample: 'print' } ] },
  { code: 'ards-prone-config', name: 'ARDS 俯卧位数据映射配置', desc: '采集项 → 监护/LIS 项目映射、候选值、试采核对', params: [] },
  { code: 'quality-board', name: '质控指标看板', desc: '127 条指标按域分组、周期切换、点数字下钻', params: [
    { k: 'departCode', req: false, sample: 'ICU01' } ] },
  { code: 'quality-monthly', name: '质控月度汇总', desc: '1-12 月横排对比，同比/环比，支持 xlsx 导出', params: [
    { k: 'departCode', req: false, sample: 'ICU01' } ] },
  { code: 'quality-config', name: '质控指标配置', desc: '指标口径编辑、事实层配置、变更历史与回滚', params: [] },
  { code: 'param-config', name: '参数设置', desc: '系统参数、ARDS 采集映射、外链页面一览', params: [] }
]
const filteredLinks = computed(() => {
  const kw = linkKw.value.trim().toLowerCase()
  if (!kw) return LINK_PAGES
  return LINK_PAGES.filter((p) =>
    p.name.toLowerCase().includes(kw) || p.code.toLowerCase().includes(kw) || (p.desc || '').toLowerCase().includes(kw)
  )
})
function buildExample(pg) {
  const base = (currentBase() || LINK_BASE).replace(/\/+$/, '')
  const tok = currentToken() || LINK_TOKEN
  let url = `${base}/entry/${pg.code}?extToken=${tok}&realname=张医生`
  ;(pg.params || []).forEach((pa) => { url += `&${pa.k}=${pa.sample}` })
  return url
}
function currentBase() {
  const it = list.value.find((p) => p.paramKey === 'EXTERNAL_LINK_BASE_URL')
  return it && it.paramValue ? it.paramValue : ''
}
function currentToken() {
  const it = list.value.find((p) => p.paramKey === 'EXTERNAL_LINK_ICU_TOKEN')
  return it && it.paramValue ? it.paramValue : ''
}
const maskedToken = computed(() => {
  const t = currentToken()
  if (!t) return '未生成'
  return t.length <= 8 ? t[0] + '****' : t.slice(0, 4) + '****' + t.slice(-4)
})
function randomToken() {
  const arr = new Uint8Array(24)
  window.crypto.getRandomValues(arr)
  return btoa(String.fromCharCode.apply(null, Array.from(arr))).replace(/[+/=]/g, '').slice(0, 32)
}
async function onGenToken() {
  try {
    await ElMessageBox.confirm('重新生成后，ICU 侧写死的旧 extToken 将立即失效，需同步更新 ICU 外链模板。确认生成？', '重新生成令牌', {
      type: 'warning', confirmButtonText: '生成', cancelButtonText: '取消'
    })
  } catch { return }
  const tok = randomToken()
  const exist = list.value.find((p) => p.paramKey === 'EXTERNAL_LINK_ICU_TOKEN')
  const body = exist ? { ...exist, paramValue: tok } : {
    paramName: 'ICU 外链固定令牌', paramKey: 'EXTERNAL_LINK_ICU_TOKEN', paramValue: tok,
    paramGroup: 'external', paramType: 'text', options: '', defaultValue: '',
    required: 0, regex: '', sortNo: 2, status: 1, remark: 'ICU 外链固定令牌，随机生成'
  }
  try {
    await request.post('/sys-param/save', body)
    ElMessage.success('新令牌已生成并保存，请同步到 ICU 外链模板')
    await loadAll()
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || e))
  }
}
async function copyLink(pg) {
  const url = buildExample(pg)
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('已复制：' + pg.name)
  } catch (e) {
    ElMessage.warning('复制失败，请手动选择：' + url)
  }
}

const list = ref([])
const groups = ref([])
const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const currentGroup = ref('')

const dialogVisible = ref(false)
const switchValue = ref(false)
const formOptions = ref([])

const groupDialogVisible = ref(false)
const groupFormVisible = ref(false)
const groupSaving = ref(false)

const emptyForm = () => ({
  id: null,
  paramName: '',
  paramKey: '',
  paramValue: '',
  paramGroup: '',
  paramType: 'text',
  options: '',
  defaultValue: '',
  required: 0,
  regex: '',
  sortNo: 0,
  status: 1,
  remark: ''
})
const form = reactive(emptyForm())

const emptyGroupForm = () => ({
  id: null,
  groupName: '',
  groupCode: '',
  sortNo: 10,
  status: 1,
  remark: ''
})
const groupForm = reactive(emptyGroupForm())

const currentGroupName = computed(() => {
  if (!currentGroup.value) return '全部参数'
  const g = groups.value.find((x) => x.groupCode === currentGroup.value)
  return g ? g.groupName : currentGroup.value
})

const currentGroupDesc = computed(() => {
  if (!currentGroup.value) return '按分组维护系统参数，开关与下拉型参数可就地修改'
  const g = groups.value.find((x) => x.groupCode === currentGroup.value)
  return g ? g.remark : ''
})

const filteredList = computed(() => {
  let arr = list.value
  if (currentGroup.value) {
    arr = arr.filter((p) => p.paramGroup === currentGroup.value)
  }
  // 搜索跨当前分组外的全部参数，避免切组才能找到东西
  const kw = keyword.value.trim().toLowerCase()
  if (kw) {
    arr = list.value.filter((p) => {
      if (currentGroup.value && p.paramGroup !== currentGroup.value) return false
      return (
        (p.paramName || '').toLowerCase().includes(kw) ||
        (p.paramKey || '').toLowerCase().includes(kw) ||
        (p.remark || '').toLowerCase().includes(kw)
      )
    })
  }
  return arr.slice().sort((a, b) => (a.sortNo || 0) - (b.sortNo || 0) || (a.id || 0) - (b.id || 0))
})

function countOf(code) {
  return list.value.filter((p) => p.paramGroup === code).length
}

function optionsOf(p) {
  if (!p.options) return []
  try {
    const arr = JSON.parse(p.options)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

function shortValue(p) {
  if (!p.paramValue) return '未配置'
  const v = String(p.paramValue)
  return v.length > 60 ? v.substring(0, 60) + '…' : v
}

async function loadAll() {
  loading.value = true
  try {
    const [params, gs] = await Promise.all([
      request.get('/sys-param/list'),
      request.get('/param-group/list?onlyEnabled=false')
    ])
    list.value = params || []
    groups.value = gs || []
  } catch (e) {
    console.error('参数数据加载失败', e)
    ElMessage.error('参数数据加载失败')
  } finally {
    loading.value = false
  }
}

function openAdd() {
  Object.assign(form, emptyForm(), { paramGroup: currentGroup.value || (groups.value[0]?.groupCode || '') })
  formOptions.value = []
  switchValue.value = false
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, emptyForm(), {
    id: row.id,
    paramName: row.paramName,
    paramKey: row.paramKey,
    paramValue: row.paramValue || '',
    paramGroup: row.paramGroup || '',
    paramType: row.paramType || 'text',
    options: row.options || '',
    defaultValue: row.defaultValue || '',
    required: row.required || 0,
    regex: row.regex || '',
    sortNo: row.sortNo || 0,
    status: row.status === 0 ? 0 : 1,
    remark: row.remark || ''
  })
  formOptions.value = optionsOf(row)
  switchValue.value = row.paramValue === '1'
  dialogVisible.value = true
}

function onTypeChange(type) {
  if (type === 'switch' && form.paramValue !== '1' && form.paramValue !== '0') {
    form.paramValue = '0'
    switchValue.value = false
  }
}

async function buildAndSave() {
  if (!form.paramName || !form.paramKey || !form.paramGroup) {
    ElMessage.warning('参数名称、参数键、所属分组不能为空')
    return false
  }
  if (form.paramType === 'switch') {
    form.paramValue = switchValue.value ? '1' : '0'
  }
  if (form.paramType === 'select') {
    try {
      const arr = JSON.parse(form.options)
      if (!Array.isArray(arr) || !arr.length) {
        ElMessage.warning('下拉型参数必须配置至少一个选项')
        return false
      }
    } catch {
      ElMessage.warning('选项不是合法 JSON 数组')
      return false
    }
  }
  await request.post('/sys-param/save', { ...form })
  return true
}

async function onSave() {
  saving.value = true
  try {
    if (!(await buildAndSave())) return
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadAll()
  } catch (e) {
    console.error('参数保存失败', e)
    ElMessage.error('保存失败：' + (e.message || e))
  } finally {
    saving.value = false
  }
}

/** 开关就地保存 */
async function onSwitchChange(p, v) {
  const origin = p.paramValue
  p.paramValue = v ? '1' : '0'
  try {
    await request.post('/sys-param/save', { ...p })
    ElMessage.success('已保存')
  } catch (e) {
    p.paramValue = origin
    ElMessage.error('保存失败：' + (e.message || e))
  }
}

/** 下拉就地保存 */
async function onSelectChange(p, v) {
  const origin = p.paramValue
  p.paramValue = v
  try {
    await request.post('/sys-param/save', { ...p })
    ElMessage.success('已保存')
  } catch (e) {
    p.paramValue = origin
    ElMessage.error('保存失败：' + (e.message || e))
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除参数「${row.paramName}」？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await request.post('/sys-param/delete', null, { params: { id: row.id } })
    ElMessage.success('已删除')
    await loadAll()
  } catch (e) {
    console.error('参数删除失败', e)
    ElMessage.error('删除失败：' + (e.message || e))
  }
}

/* ---------------- 分组管理 ---------------- */

function openGroupManager() {
  groupDialogVisible.value = true
}

function openGroupAdd() {
  Object.assign(groupForm, emptyGroupForm())
  groupFormVisible.value = true
}

function openGroupEdit(row) {
  Object.assign(groupForm, emptyGroupForm(), {
    id: row.id,
    groupName: row.groupName,
    groupCode: row.groupCode,
    sortNo: row.sortNo || 0,
    status: row.status === 0 ? 0 : 1,
    remark: row.remark || ''
  })
  groupFormVisible.value = true
}

async function onGroupSave() {
  if (!groupForm.groupName || !groupForm.groupCode) {
    ElMessage.warning('分组名称与分组编码不能为空')
    return
  }
  groupSaving.value = true
  try {
    await request.post('/param-group/save', { ...groupForm })
    ElMessage.success('保存成功')
    groupFormVisible.value = false
    await loadAll()
  } catch (e) {
    console.error('分组保存失败', e)
    ElMessage.error('保存失败：' + (e.message || e))
  } finally {
    groupSaving.value = false
  }
}

async function onGroupDelete(row) {
  const used = countOf(row.groupCode)
  if (used > 0) {
    ElMessage.warning(`该分组下还有 ${used} 个参数，请先移走或删除这些参数`)
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除分组「${row.groupName}」？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await request.post('/param-group/delete', null, { params: { id: row.id } })
    ElMessage.success('已删除')
    await loadAll()
  } catch (e) {
    console.error('分组删除失败', e)
    ElMessage.error('删除失败：' + (e.message || e))
  }
}

onMounted(loadAll)
</script>

<style scoped>
.param-config { padding: 16px 20px 24px; background: #f5f7fa; min-height: 100%; box-sizing: border-box; }

/* 顶部页签 */
.pc-tabs { display: flex; gap: 6px; margin-bottom: 12px; }
.pc-tab {
  border: 1px solid #e4e7ed; background: #fff; color: #4b5563;
  border-radius: 6px; padding: 7px 18px; font-size: 13px; cursor: pointer;
}
.pc-tab:hover { color: #0f766e; border-color: #99f6e4; }
.pc-tab.on { background: #0f766e; border-color: #0f766e; color: #fff; font-weight: 600; }

.pc-layout { display: flex; gap: 12px; align-items: flex-start; }

/* 左侧分组导航 */
.pc-side {
  width: 200px; flex-shrink: 0; background: #fff; border: 1px solid #ebeef5;
  border-radius: 6px; padding: 10px 8px 14px;
}
.side-head {
  display: flex; align-items: center; justify-content: space-between;
  padding: 2px 6px 8px; border-bottom: 1px solid #f0f0f0; margin-bottom: 6px;
}
.side-title { font-size: 13px; font-weight: 700; color: #303133; }
.group-list { list-style: none; margin: 0; padding: 0; }
.group-item {
  display: flex; align-items: center; justify-content: space-between; gap: 8px;
  padding: 8px 10px; border-radius: 4px; cursor: pointer; font-size: 13px; color: #4b5563;
}
.group-item:hover { background: #f5f7fa; }
.group-item.active { background: #ecfdf5; color: #0f766e; font-weight: 600; }
.g-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.g-count {
  flex-shrink: 0; min-width: 20px; text-align: center; font-size: 12px;
  color: #a8a29e; background: #f4f4f5; border-radius: 8px; padding: 0 6px;
}
.group-item.active .g-count { color: #0f766e; background: #d1fae5; }
.side-empty { font-size: 12px; color: #c0c4cc; padding: 10px; text-align: center; }

/* 右侧主区 */
.pc-main { flex: 1; min-width: 0; }
.main-head {
  display: flex; align-items: flex-start; justify-content: space-between; gap: 16px;
  background: #fff; border: 1px solid #ebeef5; border-radius: 6px;
  padding: 14px 18px; margin-bottom: 12px;
}
.mh-left h2 { margin: 0 0 4px; font-size: 18px; font-weight: 700; color: #292524; }
.g-desc { margin: 0; font-size: 13px; color: #78716c; line-height: 1.5; }
.main-actions { display: flex; gap: 8px; flex-shrink: 0; align-items: center; }
.kw { width: 220px; }

.param-list { display: flex; flex-direction: column; gap: 8px; }
.param-card {
  display: flex; align-items: center; justify-content: space-between; gap: 16px;
  background: #fff; border: 1px solid #ebeef5; border-radius: 6px; padding: 12px 16px;
}
.param-card:hover { border-color: #99f6e4; }
.pc-left { min-width: 0; flex: 1; }
.pc-title { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.p-name { font-weight: 600; color: #303133; font-size: 14px; }
.pc-remark { margin-top: 4px; font-size: 12px; color: #a8a29e; line-height: 1.5; }
.pc-right { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
.p-value { color: #0f766e; font-size: 12px; max-width: 420px; word-break: break-all; }
.p-value.empty { color: #c0c4cc; }
.p-select { width: 180px; }

.form-tip { font-size: 11px; color: #a8a29e; line-height: 1.5; margin-top: 2px; }
.w-full { width: 100%; }

/* 外链页面一览 */
.link-wrap { max-width: 1100px; }
.link-intro {
  background: #fff; border: 1px solid #ebeef5; border-radius: 6px;
  padding: 14px 18px; margin-bottom: 12px; font-size: 13px; color: #4b5563; line-height: 1.7;
}
.link-intro h3 { margin: 0 0 6px; font-size: 16px; color: #292524; }
.link-intro p { margin: 6px 0; }
.link-intro ul { margin: 6px 0; padding-left: 20px; }
.link-intro code, .lc-url-text {
  background: #f1f5f9; color: #0f766e; padding: 1px 6px; border-radius: 3px;
  font-family: Consolas, Monaco, monospace; font-size: 12px;
}
.link-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.link-kw { width: 280px; }
.link-count { font-size: 12px; color: #78716c; }
.link-actions { margin-top: 10px; display: flex; align-items: center; gap: 10px; }
.link-token-tip { font-size: 12px; color: #78716c; }
.link-token-tip b { color: #0f766e; font-family: Consolas, Monaco, monospace; }
.link-list { display: flex; flex-direction: column; gap: 8px; }
.link-card {
  background: #fff; border: 1px solid #ebeef5; border-radius: 6px; padding: 12px 16px;
}
.link-card:hover { border-color: #99f6e4; }
.lc-head { display: flex; align-items: center; gap: 8px; }
.lc-name { font-weight: 600; color: #303133; font-size: 14px; }
.lc-code { font-family: Consolas, Monaco, monospace; }
.lc-desc { margin: 6px 0; font-size: 12px; color: #78716c; }
.lc-params { margin-bottom: 8px; display: flex; align-items: center; flex-wrap: wrap; gap: 4px; }
.lc-p-label { font-size: 12px; color: #a8a29e; margin-right: 2px; }
.lc-url {
  display: flex; align-items: center; gap: 8px; background: #f8fafc;
  border: 1px dashed #e2e8f0; border-radius: 4px; padding: 6px 10px;
}
.lc-url-text { flex: 1; background: transparent; word-break: break-all; color: #334155; font-size: 12px; }
</style>
