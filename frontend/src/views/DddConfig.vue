<template>
  <div class="ddd-config abx-theme">
    <div class="filter-bar">
      <div class="filter-left">
        <el-button @click="goBack">
          <el-icon><ArrowLeft /></el-icon> 返回总览
        </el-button>
        <el-select v-model="filterClass" placeholder="按分类筛选" clearable style="width: 160px;" popper-class="abx-popper" @change="loadList">
          <el-option v-for="c in allClasses" :key="c" :label="c" :value="c" />
        </el-select>
        <el-select v-model="filterLevel" placeholder="按管理级别筛选" clearable style="width: 140px;" popper-class="abx-popper" @change="loadList">
          <el-option label="非限制" value="非限制" />
          <el-option label="限制" value="限制" />
          <el-option label="特殊" value="特殊" />
        </el-select>
        <el-input v-model="searchKeyword" placeholder="搜索药品名称/关键词" clearable style="width: 200px;" @input="filterList" />
      </div>
      <div class="filter-right">
        <el-button type="primary" @click="openAddDialog">
          <el-icon><Plus /></el-icon> 新增配置
        </el-button>
        <el-button @click="loadList" :loading="loading">
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
      <div class="summary-item">
        <span class="label">非限制级</span>
        <span class="value success">{{ countByLevel['非限制'] || 0 }}</span>
      </div>
      <div class="summary-item">
        <span class="label">限制级</span>
        <span class="value warning">{{ countByLevel['限制'] || 0 }}</span>
      </div>
      <div class="summary-item">
        <span class="label">特殊使用级</span>
        <span class="value danger">{{ countByLevel['特殊'] || 0 }}</span>
      </div>
    </div>

    <!-- 配置列表 -->
    <div class="config-table">
      <el-table :data="filteredList" stripe size="default" max-height="600">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="drugName" label="药品通用名" min-width="160">
          <template #default="{ row }">
            <span class="drug-name">{{ row.drugName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="atcCode" label="ATC编码" width="110" />
        <el-table-column prop="dddValue" label="DDD值" width="90" align="right">
          <template #default="{ row }">
            <span class="highlight">{{ row.dddValue }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="dddUnit" label="单位" width="70" align="center" />
        <el-table-column prop="route" label="给药途径" width="80" align="center" />
        <el-table-column prop="manageLevel" label="管理级别" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.manageLevel === '特殊' ? 'danger' : row.manageLevel === '限制' ? 'warning' : 'success'" size="small">
              {{ row.manageLevel }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="drugClass" label="药物分类" width="120" />
        <el-table-column prop="keywords" label="匹配关键词" min-width="180" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="toggleStatus(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openEditDialog(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑DDD配置' : '新增DDD配置'" width="600px" class="abx-overlay" @close="resetForm">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="药品通用名" prop="drugName">
          <el-input v-model="form.drugName" placeholder="如：美罗培南" />
        </el-form-item>
        <el-form-item label="ATC编码" prop="atcCode">
          <el-input v-model="form.atcCode" placeholder="如：J01DH02" />
        </el-form-item>
        <el-form-item label="DDD值" prop="dddValue">
          <el-input-number v-model="form.dddValue" :precision="4" :step="0.1" :min="0" style="width: 200px;" />
        </el-form-item>
        <el-form-item label="DDD单位" prop="dddUnit">
          <el-select v-model="form.dddUnit" style="width: 150px;" popper-class="abx-popper">
            <el-option label="g" value="g" />
            <el-option label="mg" value="mg" />
            <el-option label="MU" value="MU" />
            <el-option label="万单位" value="万单位" />
          </el-select>
        </el-form-item>
        <el-form-item label="给药途径" prop="route">
          <el-select v-model="form.route" style="width: 150px;" popper-class="abx-popper">
            <el-option label="注射" value="注射" />
            <el-option label="口服" value="口服" />
          </el-select>
        </el-form-item>
        <el-form-item label="管理级别" prop="manageLevel">
          <el-select v-model="form.manageLevel" style="width: 150px;" popper-class="abx-popper">
            <el-option label="非限制" value="非限制" />
            <el-option label="限制" value="限制" />
            <el-option label="特殊" value="特殊" />
          </el-select>
        </el-form-item>
        <el-form-item label="药物分类" prop="drugClass">
          <el-select v-model="form.drugClass" filterable allow-create style="width: 200px;" popper-class="abx-popper" placeholder="选择或输入分类">
            <el-option v-for="c in allClasses" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="匹配关键词" prop="keywords">
          <el-input v-model="form.keywords" type="textarea" :rows="2" placeholder="多个关键词用逗号分隔，如：美罗培南,美平,罗南,倍能" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting">确定</el-button>
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
const allClasses = ref([])
const searchKeyword = ref('')
const filterClass = ref('')
const filterLevel = ref('')

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

const form = reactive({
  id: null,
  drugName: '',
  atcCode: '',
  dddValue: 1,
  dddUnit: 'g',
  route: '注射',
  manageLevel: '非限制',
  drugClass: '',
  keywords: '',
  remark: '',
  status: 1
})

const rules = {
  drugName: [{ required: true, message: '请输入药品通用名', trigger: 'blur' }],
  dddValue: [{ required: true, message: '请输入DDD值', trigger: 'blur' }],
  manageLevel: [{ required: true, message: '请选择管理级别', trigger: 'change' }]
}

const filteredList = computed(() => {
  let list = configList.value
  if (filterClass.value) {
    list = list.filter(c => c.drugClass === filterClass.value)
  }
  if (filterLevel.value) {
    list = list.filter(c => c.manageLevel === filterLevel.value)
  }
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    list = list.filter(c =>
      (c.drugName || '').toLowerCase().includes(kw) ||
      (c.keywords || '').toLowerCase().includes(kw)
    )
  }
  return list
})

const countByLevel = computed(() => {
  const count = {}
  configList.value.forEach(c => {
    count[c.manageLevel] = (count[c.manageLevel] || 0) + 1
  })
  return count
})

async function loadList() {
  loading.value = true
  try {
    const [listRes, classesRes] = await Promise.all([
      request.get('/antibiotic/ddd/config/list'),
      request.get('/antibiotic/ddd/config/classes')
    ])
    configList.value = listRes || []
    allClasses.value = classesRes || []
  } catch (e) {
    console.warn('加载失败: ', e.message || e)
  } finally {
    loading.value = false
  }
}

function filterList() {
  // computed 自动处理
}

function openAddDialog() {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(row) {
  isEdit.value = true
  Object.assign(form, row)
  dialogVisible.value = true
}

function resetForm() {
  Object.assign(form, {
    id: null,
    drugName: '',
    atcCode: '',
    dddValue: 1,
    dddUnit: 'g',
    route: '注射',
    manageLevel: '非限制',
    drugClass: '',
    keywords: '',
    remark: '',
    status: 1
  })
  formRef.value?.clearValidate()
}

async function submitForm() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    if (isEdit.value) {
      await request.post('/antibiotic/ddd/config/update', form)
      ElMessage.success('修改成功')
    } else {
      await request.post('/antibiotic/ddd/config/add', form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    loadList()
  } catch (e) {
    console.warn('操作失败: ', e.message || e)
  } finally {
    submitting.value = false
  }
}

async function toggleStatus(row) {
  const newStatus = row.status === 1 ? 0 : 1
  try {
    await ElMessageBox.confirm(
      `确定要${newStatus === 1 ? '启用' : '停用'}「${row.drugName}」吗？`,
      '提示',
      { type: 'warning', customClass: 'abx-overlay' }
    )
    await request.post('/antibiotic/ddd/config/toggle', null, {
      params: { id: row.id, status: newStatus }
    })
    row.status = newStatus
    ElMessage.success('操作成功')
  } catch {
    // 用户取消
  }
}

function goBack() {
  router.push('/page/abx-ddd')
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.ddd-config {
  padding: 24px;
  background: #fafaf9;
  min-height: 100vh;
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(28,25,23,0.04);
}

.filter-left, .filter-right {
  display: flex;
  gap: 12px;
  align-items: center;
}

.summary-bar {
  display: flex;
  gap: 32px;
  padding: 16px 20px;
  background: #fff;
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  margin-bottom: 16px;
  box-shadow: 0 1px 2px rgba(28,25,23,0.04);
}

.summary-item {
  display: flex;
  align-items: baseline;
  gap: 8px;
}

.summary-item .label {
  font-size: 13px;
  color: #78716c;
}

.summary-item .value {
  font-size: 22px;
  font-weight: 700;
  color: #292524;
}

.summary-item .value.success { color: #16a34a; }
.summary-item .value.warning { color: #d97706; }
.summary-item .value.danger { color: #dc2626; }

.config-table {
  background: #fff;
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 1px 2px rgba(28,25,23,0.04);
}

.drug-name {
  font-weight: 600;
  color: #292524;
}

.highlight {
  color: #0d9488;
  font-weight: 600;
}
</style>
