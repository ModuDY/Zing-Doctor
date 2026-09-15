<template>
  <div class="word-config abx-theme">
    <div class="filter-bar">
      <div class="filter-left">
        <el-button @click="goBack">
          <el-icon><ArrowLeft /></el-icon> 返回集束化治疗
        </el-button>
        <el-select v-model="filterType" placeholder="按词类型筛选" clearable style="width: 170px;" popper-class="abx-popper" @change="filterList">
          <el-option label="广谱抗菌药（白名单）" value="broad_spectrum" />
          <el-option label="非抗菌药（黑名单）" value="non_antibiotic" />
        </el-select>
        <el-select v-model="filterCategory" placeholder="按分组筛选" clearable style="width: 160px;" popper-class="abx-popper" @change="filterList">
          <el-option v-for="c in allCategories" :key="c" :label="c" :value="c" />
        </el-select>
        <el-input v-model="searchKeyword" placeholder="搜索关键词" clearable style="width: 200px;" @input="filterList" />
      </div>
      <div class="filter-right">
        <el-button type="primary" @click="openAddDialog">
          <el-icon><Plus /></el-icon> 新增词条
        </el-button>
        <el-button @click="loadList" :loading="loading">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>
    </div>

    <!-- 统计摘要 -->
    <div class="summary-bar">
      <div class="summary-item">
        <span class="label">总词条数</span>
        <span class="value">{{ configList.length }}</span>
      </div>
      <div class="summary-item">
        <span class="label">广谱抗菌药</span>
        <span class="value success">{{ countByType['broad_spectrum'] || 0 }}</span>
      </div>
      <div class="summary-item">
        <span class="label">非抗菌药</span>
        <span class="value warning">{{ countByType['non_antibiotic'] || 0 }}</span>
      </div>
      <div class="summary-item">
        <span class="label">分组数</span>
        <span class="value">{{ allCategories.length }}</span>
      </div>
    </div>

    <!-- 词条列表 -->
    <div class="config-table">
      <el-table :data="filteredList" stripe size="default" max-height="600">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column prop="wordType" label="词类型" width="170" align="center">
          <template #default="{ row }">
            <el-tag :type="row.wordType === 'broad_spectrum' ? 'success' : 'warning'" size="small">
              {{ row.wordType === 'broad_spectrum' ? '广谱抗菌药' : '非抗菌药' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="keyword" label="关键词" min-width="140">
          <template #default="{ row }">
            <span class="keyword">{{ row.keyword }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="category" label="分组" width="150">
          <template #default="{ row }">
            {{ row.category || '—' }}
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.remark || '—' }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="toggleStatus(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" link @click="openEditDialog(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑词条' : '新增词条'" width="560px" class="abx-overlay" @close="resetForm">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <el-form-item label="关键词" prop="keyword">
          <el-input v-model="form.keyword" placeholder="如：美罗培南 / 西替利嗪" />
        </el-form-item>
        <el-form-item label="词类型" prop="wordType">
          <el-select v-model="form.wordType" style="width: 220px;" popper-class="abx-popper">
            <el-option label="广谱抗菌药（白名单）" value="broad_spectrum" />
            <el-option label="非抗菌药（黑名单）" value="non_antibiotic" />
          </el-select>
        </el-form-item>
        <el-form-item label="分组" prop="category">
          <el-select v-model="form.category" filterable allow-create style="width: 220px;" popper-class="abx-popper" placeholder="选择或输入分组">
            <el-option v-for="c in allCategories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选，如：抗菌药误判，2026-09 补充" />
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
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus, Refresh } from '@element-plus/icons-vue'
import request from '../api/request'
import '../styles/abx-theme.css'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const configList = ref([])
const allCategories = ref([])
const searchKeyword = ref('')
const filterType = ref('')
const filterCategory = ref('')

const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)

const form = reactive({
  id: null,
  wordType: 'non_antibiotic',
  keyword: '',
  category: '',
  remark: '',
  status: 1
})

const rules = {
  keyword: [{ required: true, message: '请输入关键词', trigger: 'blur' }],
  wordType: [{ required: true, message: '请选择词类型', trigger: 'change' }]
}

const filteredList = computed(() => {
  let list = configList.value
  if (filterType.value) {
    list = list.filter(c => c.wordType === filterType.value)
  }
  if (filterCategory.value) {
    list = list.filter(c => c.category === filterCategory.value)
  }
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    list = list.filter(c =>
      (c.keyword || '').toLowerCase().includes(kw) ||
      (c.category || '').toLowerCase().includes(kw)
    )
  }
  return list
})

const countByType = computed(() => {
  const count = {}
  configList.value.forEach(c => {
    count[c.wordType] = (count[c.wordType] || 0) + 1
  })
  return count
})

async function loadList() {
  loading.value = true
  try {
    const [listRes, catsRes] = await Promise.all([
      request.get('/antibiotic/word-config/list'),
      request.get('/antibiotic/word-config/categories')
    ])
    configList.value = listRes || []
    allCategories.value = catsRes || []
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
    wordType: 'non_antibiotic',
    keyword: '',
    category: '',
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
      await request.post('/antibiotic/word-config/update', form)
      ElMessage.success('修改成功')
    } else {
      await request.post('/antibiotic/word-config/add', form)
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
      `确定要${newStatus === 1 ? '启用' : '停用'}「${row.keyword}」吗？`,
      '提示',
      { type: 'warning', customClass: 'abx-overlay' }
    )
    await request.post('/antibiotic/word-config/toggle', null, {
      params: { id: row.id, status: newStatus }
    })
    row.status = newStatus
    ElMessage.success('操作成功')
  } catch {
    // 用户取消
  }
}

function goBack() {
  router.push({ path: '/page/sepsis-bundle', query: { inHospitalNo: route.query.inHospitalNo } })
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.word-config {
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

.config-table {
  background: #fff;
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 1px 2px rgba(28,25,23,0.04);
}

.keyword {
  font-weight: 600;
  color: #292524;
}
</style>
