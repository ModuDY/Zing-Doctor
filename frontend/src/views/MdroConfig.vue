<template>
  <div class="mdro-config abx-theme">
    <div class="filter-bar">
      <div class="filter-left">
        <el-button @click="goBack">
          <el-icon><ArrowLeft /></el-icon> 返回总览
        </el-button>
        <el-select v-model="filterConfigType" placeholder="按配置类型筛选" clearable style="width: 160px;" popper-class="abx-popper" @change="loadList">
          <el-option label="细菌分类" value="bacteria_class" />
          <el-option label="高风险细菌" value="high_risk" />
        </el-select>
        <el-select v-model="filterBacteriaClass" placeholder="按细菌分类筛选" clearable style="width: 140px;" popper-class="abx-popper" @change="filterList">
          <el-option label="革兰阳性菌" value="gram_positive" />
          <el-option label="革兰阴性菌" value="gram_negative" />
          <el-option label="真菌" value="fungi" />
          <el-option label="其他" value="other" />
        </el-select>
        <el-input v-model="searchKeyword" placeholder="搜索细菌名称/关键词" clearable style="width: 200px;" @input="filterList" />
      </div>
      <div class="filter-right">
        <el-button type="primary" @click="openAddDialog">
          <el-icon><Plus /></el-icon> 新增配置
        </el-button>
        <el-button :loading="loading" @click="loadList">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </div>

    <!-- 统计摘要 -->
    <div class="summary-bar">
      <div class="summary-item">
        <span class="label">总配置数</span>
        <span class="value">{{ configList.length }}</span>
      </div>
      <div class="summary-item success">
        <span class="label">革兰阳性菌</span>
        <span class="value">{{ countByClass['gram_positive'] || 0 }}</span>
      </div>
      <div class="summary-item primary">
        <span class="label">革兰阴性菌</span>
        <span class="value">{{ countByClass['gram_negative'] || 0 }}</span>
      </div>
      <div class="summary-item warning">
        <span class="label">真菌</span>
        <span class="value">{{ countByClass['fungi'] || 0 }}</span>
      </div>
      <div class="summary-item danger">
        <span class="label">高风险细菌</span>
        <span class="value">{{ highRiskCount }}</span>
      </div>
    </div>

    <!-- 配置列表 -->
    <div class="config-table">
      <el-table :data="filteredList" stripe size="default" max-height="600">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="configType" label="配置类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.configType === 'high_risk' ? 'danger' : 'primary'" size="small">
              {{ row.configType === 'high_risk' ? '高风险细菌' : '细菌分类' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bacteriaName" label="细菌名称" min-width="160">
          <template #default="{ row }">
            <span class="bacteria-name">{{ row.bacteriaName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="bacteriaClass" label="细菌分类" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getClassTagType(row.bacteriaClass)" size="small">
              {{ getClassName(row.bacteriaClass) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isHighRisk" label="高风险" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isHighRisk === 1" type="danger" size="small" effect="dark">是</el-tag>
            <el-tag v-else type="info" size="small">否</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="keywords" label="匹配关键词" min-width="180" show-overflow-tooltip />
        <el-table-column prop="remark" label="备注" min-width="150" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="toggleStatus(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button type="danger" link size="small" @click="toggleStatus(row, row.status === 1 ? 0 : 1)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑配置' : '新增配置'" width="560px" class="abx-overlay" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="配置类型" prop="configType">
          <el-select v-model="form.configType" style="width: 100%;" popper-class="abx-popper">
            <el-option label="细菌分类" value="bacteria_class" />
            <el-option label="高风险细菌" value="high_risk" />
          </el-select>
        </el-form-item>
        <el-form-item label="细菌名称" prop="bacteriaName">
          <el-input v-model="form.bacteriaName" placeholder="请输入细菌名称，如：鲍曼不动杆菌" />
        </el-form-item>
        <el-form-item label="细菌分类" prop="bacteriaClass">
          <el-select v-model="form.bacteriaClass" style="width: 100%;" popper-class="abx-popper">
            <el-option label="革兰阳性菌" value="gram_positive" />
            <el-option label="革兰阴性菌" value="gram_negative" />
            <el-option label="真菌" value="fungi" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="是否高风险">
          <el-switch v-model="form.isHighRisk" :active-value="1" :inactive-value="0" />
          <span style="margin-left: 8px; color: #78716c; font-size: 12px;">ICU常见MDRO风险菌标记为高风险</span>
        </el-form-item>
        <el-form-item label="匹配关键词">
          <el-input v-model="form.keywords" type="textarea" :rows="2" placeholder="多个关键词用逗号分隔，如：鲍曼不动杆菌,不动杆菌" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus, Refresh } from '@element-plus/icons-vue'
import request from '../api/request'
import '../styles/abx-theme.css'

const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const configList = ref([])
const searchKeyword = ref('')
const filterConfigType = ref('')
const filterBacteriaClass = ref('')
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

const form = reactive({
  id: null,
  configType: 'bacteria_class',
  bacteriaName: '',
  bacteriaClass: 'gram_negative',
  isHighRisk: 0,
  keywords: '',
  remark: '',
  status: 1
})

const rules = {
  configType: [{ required: true, message: '请选择配置类型', trigger: 'change' }],
  bacteriaName: [{ required: true, message: '请输入细菌名称', trigger: 'blur' }],
  bacteriaClass: [{ required: true, message: '请选择细菌分类', trigger: 'change' }]
}

const filteredList = computed(() => {
  let list = configList.value
  if (filterConfigType.value) {
    list = list.filter(c => c.configType === filterConfigType.value)
  }
  if (filterBacteriaClass.value) {
    list = list.filter(c => c.bacteriaClass === filterBacteriaClass.value)
  }
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    list = list.filter(c =>
      (c.bacteriaName && c.bacteriaName.toLowerCase().includes(kw)) ||
      (c.keywords && c.keywords.toLowerCase().includes(kw))
    )
  }
  return list
})

const countByClass = computed(() => {
  const map = {}
  configList.value.forEach(c => {
    if (c.configType === 'bacteria_class') {
      map[c.bacteriaClass] = (map[c.bacteriaClass] || 0) + 1
    }
  })
  return map
})

const highRiskCount = computed(() => {
  return configList.value.filter(c => c.configType === 'high_risk' || c.isHighRisk === 1).length
})

async function loadList() {
  loading.value = true
  try {
    const res = await request.get('/antibiotic/mdro/config/list')
    configList.value = res || []
  } catch (e) {
    console.warn('加载配置列表失败: ', e.message || e)
  } finally {
    loading.value = false
  }
}

function filterList() {
  // computed 自动处理
}

function getClassName(bacteriaClass) {
  if (bacteriaClass === 'gram_positive') return '革兰阳性菌'
  if (bacteriaClass === 'gram_negative') return '革兰阴性菌'
  if (bacteriaClass === 'fungi') return '真菌'
  return '其他'
}

function getClassTagType(bacteriaClass) {
  if (bacteriaClass === 'gram_positive') return 'success'
  if (bacteriaClass === 'gram_negative') return 'primary'
  if (bacteriaClass === 'fungi') return 'warning'
  return 'info'
}

async function toggleStatus(row, status) {
  const newStatus = status !== undefined ? status : (row.status === 1 ? 0 : 1)
  try {
    await ElMessageBox.confirm(
      `确定要${newStatus === 1 ? '启用' : '停用'}「${row.bacteriaName}」吗？`,
      '提示',
      { type: 'warning', customClass: 'abx-overlay' }
    )
    await request.post('/antibiotic/mdro/config/toggle', null, {
      params: { id: row.id, status: newStatus }
    })
    ElMessage.success('操作成功')
    loadList()
  } catch (e) {
    if (e !== 'cancel') {
      console.warn('操作失败: ', e.message || e)
    }
  }
}

function openAddDialog() {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(row) {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    configType: row.configType,
    bacteriaName: row.bacteriaName,
    bacteriaClass: row.bacteriaClass,
    isHighRisk: row.isHighRisk,
    keywords: row.keywords,
    remark: row.remark,
    status: row.status
  })
  dialogVisible.value = true
}

function resetForm() {
  Object.assign(form, {
    id: null,
    configType: 'bacteria_class',
    bacteriaName: '',
    bacteriaClass: 'gram_negative',
    isHighRisk: 0,
    keywords: '',
    remark: '',
    status: 1
  })
  formRef.value && formRef.value.clearValidate()
}

async function submitForm() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    submitting.value = true
    if (isEdit.value) {
      await request.post('/antibiotic/mdro/config/update', form)
      ElMessage.success('修改成功')
    } else {
      await request.post('/antibiotic/mdro/config/add', form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadList()
  } catch (e) {
    if (e !== false) {
      console.warn('提交失败: ', e.message || e)
    }
  } finally {
    submitting.value = false
  }
}

function goBack() {
  router.push('/page/abx-mdro')
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.mdro-config {
  padding: 24px;
  background: #fafaf9;
  min-height: 100vh;
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;
}

.filter-left, .filter-right {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.summary-bar {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.summary-item {
  background: #fff;
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  padding: 12px 20px;
  display: flex;
  align-items: baseline;
  gap: 8px;
  box-shadow: 0 1px 2px rgba(28,25,23,0.04);
  border-left: 4px solid #0d9488;
}

.summary-item.success { border-left-color: #16a34a; }
.summary-item.primary { border-left-color: #0d9488; }
.summary-item.warning { border-left-color: #d97706; }
.summary-item.danger { border-left-color: #dc2626; }

.summary-item .label {
  font-size: 13px;
  color: #78716c;
}

.summary-item .value {
  font-size: 22px;
  font-weight: 600;
  color: #292524;
}

.summary-item .value.success { color: #16a34a; }
.summary-item .value.primary { color: #0d9488; }
.summary-item .value.warning { color: #d97706; }
.summary-item .value.danger { color: #dc2626; }

.config-table {
  background: #fff;
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 1px 2px rgba(28,25,23,0.04);
}

</style>
