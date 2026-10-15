<template>
  <div class="page">
    <div class="page-header">
      <div>
        <h2>导管相关感染确诊</h2>
        <p class="sub">VAP / CRBSI / CAUTI 医生确诊录入 · 确诊后质控「导管管路院感」域指标自动更新（每日批算，或手动触发重算）</p>
      </div>
      <div class="patient-chip" v-if="ctx.patientId">
        <span class="chip-name">{{ ctx.patientName || '当前患者' }}</span>
        <span class="chip-no" v-if="ctx.inHospitalNo">{{ ctx.inHospitalNo }}</span>
      </div>
    </div>

    <el-alert v-if="!ctx.patientId" type="warning" :closable="false"
              title="尚未选中患者" description="请先在「患者工作台」选择患者，或从侧栏带患者上下文进入本页。" />

    <template v-else>
      <div class="toolbar">
        <el-radio-group v-model="typeFilter" @change="load">
          <el-radio-button label="">全部</el-radio-button>
          <el-radio-button v-for="t in TYPES" :key="t.value" :label="t.value">{{ t.value }}</el-radio-button>
        </el-radio-group>
        <el-button type="primary" @click="openCreate">新增确诊记录</el-button>
      </div>

      <el-table :data="records" v-loading="loading" border stripe size="small">
        <el-table-column prop="confirmTime" label="确诊时间" width="160" />
        <el-table-column prop="infectionType" label="感染类型" width="110" />
        <el-table-column prop="diagnosisBasis" label="诊断依据" width="110" />
        <el-table-column prop="clinicalEvidence" label="临床依据" min-width="220" show-overflow-tooltip />
        <el-table-column prop="pathogen" label="病原学" min-width="180" show-overflow-tooltip />
        <el-table-column prop="relatedTubeName" label="关联导管" width="130" show-overflow-tooltip />
        <el-table-column prop="doctorName" label="确诊医师" width="100" />
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && records.length === 0" description="暂无确诊记录" />
    </template>

    <el-dialog v-model="dialog.visible" :title="dialog.editing ? '编辑确诊记录' : '新增确诊记录'" width="560px">
      <el-form :model="form" label-width="96px">
        <el-form-item label="感染类型" required>
          <el-radio-group v-model="form.infectionType">
            <el-radio-button v-for="t in TYPES" :key="t.value" :label="t.value">{{ t.value }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="确诊时间" required>
          <el-date-picker v-model="form.confirmTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss"
                          placeholder="默认当前时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="诊断依据">
          <el-radio-group v-model="form.diagnosisBasis">
            <el-radio label="临床诊断">临床诊断</el-radio>
            <el-radio label="病原学确诊">病原学确诊</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="临床依据">
          <el-input v-model="form.clinicalEvidence" type="textarea" :rows="3"
                    placeholder="发热、脓性痰、白细胞变化、影像新发浸润影等（医院感染诊断标准要素）" />
        </el-form-item>
        <el-form-item label="病原学">
          <el-input v-model="form.pathogen" type="textarea" :rows="2"
                    placeholder="培养/涂片/分子诊断结果，如：铜绿假单胞菌（BALF）" />
        </el-form-item>
        <el-form-item label="关联导管">
          <el-input v-model="form.relatedTubeName" placeholder="导管名称（可选，如 气管插管 / CVC / 导尿管）" />
        </el-form-item>
        <el-form-item label="确诊医师">
          <el-input v-model="form.doctorName" placeholder="医师姓名" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchDeviceInfections, saveDeviceInfection, deleteDeviceInfection } from '../api/deviceInfection'

const route = useRoute()

const TYPES = [
  { value: 'VAP', label: 'VAP（呼吸机相关肺炎）' },
  { value: 'CRBSI', label: 'CRBSI（导管相关血流感染）' },
  { value: 'CAUTI', label: 'CAUTI（导尿管相关尿路感染）' }
]

const ctx = reactive({
  patientId: route.query.patientId || '',
  patientName: route.query.patientName || '',
  inHospitalNo: route.query.inHospitalNo || ''
})

const records = ref([])
const loading = ref(false)
const typeFilter = ref('')
const dialog = reactive({ visible: false, saving: false, editing: null })
const emptyForm = () => ({
  infectionType: 'VAP',
  confirmTime: '',
  diagnosisBasis: '临床诊断',
  clinicalEvidence: '',
  pathogen: '',
  relatedTubeName: '',
  doctorName: '',
  remark: ''
})
const form = reactive(emptyForm())

async function load() {
  if (!ctx.patientId) return
  loading.value = true
  try {
    records.value = await fetchDeviceInfections(ctx.patientId, typeFilter.value || undefined) || []
  } catch (e) {
    // 静默错误：列表为空即可
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, emptyForm())
  dialog.editing = null
  dialog.visible = true
}

function openEdit(row) {
  Object.assign(form, {
    infectionType: row.infectionType,
    confirmTime: row.confirmTime,
    diagnosisBasis: row.diagnosisBasis || '临床诊断',
    clinicalEvidence: row.clinicalEvidence || '',
    pathogen: row.pathogen || '',
    relatedTubeName: row.relatedTubeName || '',
    doctorName: row.doctorName || '',
    remark: row.remark || ''
  })
  dialog.editing = row
  dialog.visible = true
}

async function submit() {
  if (!form.infectionType) {
    ElMessage.warning('请选择感染类型')
    return
  }
  dialog.saving = true
  try {
    const payload = { ...form, patientId: ctx.patientId }
    if (dialog.editing) {
      // 后端按（患者+类型+确诊时间）覆盖保存：确诊时间未改即更新原记录；
      // 改了时间会产生一条新记录，这里删掉旧记录避免重复计数
      const old = dialog.editing
      if (old.confirmTime !== form.confirmTime || old.infectionType !== form.infectionType) {
        try { await deleteDeviceInfection(old.id, ctx.patientId) } catch (e) { /* 旧记录可能已删 */ }
      }
    }
    await saveDeviceInfection(payload)
    ElMessage.success('已保存')
    dialog.visible = false
    load()
  } catch (e) {
    // 错误已由全局拦截器提示
  } finally {
    dialog.saving = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm('确认删除这条确诊记录？删除后质控指标在下次重算时同步。', '删除确认', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await deleteDeviceInfection(row.id, ctx.patientId)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    // 错误已由全局拦截器提示
  }
}

load()
</script>

<style scoped>
.page { padding: 16px 20px; }
.page-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 14px; }
.page-header h2 { margin: 0 0 4px; font-size: 18px; }
.sub { margin: 0; color: #909399; font-size: 12px; }
.patient-chip { text-align: right; }
.chip-name { font-size: 15px; font-weight: 600; margin-right: 8px; }
.chip-no { color: #909399; font-size: 12px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
</style>
