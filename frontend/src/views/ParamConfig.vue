<template>
  <div class="param-config">
    <div class="page-head">
      <div class="head-left">
        <h2>参数设置</h2>
        <p class="page-desc">
          维护系统级参数。当前「文书归档接口地址」与「归档目录」供 SOFA、APACHE II 评分记录条上的
          <b>归档</b> 按钮调用，两个评分共用同一套配置。<br />
          归档目录支持占位符：<code>#in_hospital_no#</code> 住院号、<code>#doc_code#</code> 文书编码
          （sofa / apache2）、<code>#score_date#</code> 评分日期，也支持
          <code>#patient_id#</code>、<code>#patient_name#</code>。
        </p>
      </div>
      <div class="head-right">
        <el-button type="primary" @click="openAdd">新增参数</el-button>
        <el-button @click="loadList" :loading="loading">刷新</el-button>
      </div>
    </div>

    <el-table :data="list" v-loading="loading" stripe border size="default">
      <el-table-column prop="paramName" label="参数名称" min-width="150">
        <template #default="{ row }">
          <span class="p-name">{{ row.paramName }}</span>
          <el-tag v-if="row.paramKey === 'ARCHIVE_API_URL' || row.paramKey === 'ARCHIVE_DIR'" type="warning" size="small" class="p-flag">归档</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="paramKey" label="参数键" width="180" />
      <el-table-column prop="paramValue" label="参数值" min-width="300" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.paramValue" class="p-value">{{ row.paramValue }}</span>
          <span v-else class="p-value empty">未配置</span>
        </template>
      </el-table-column>
      <el-table-column prop="paramGroup" label="分组" width="100" align="center" />
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip />
      <el-table-column prop="updateTime" label="更新时间" width="170" align="center">
        <template #default="{ row }">{{ fmtTime(row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <span class="tbl-empty">暂无参数配置</span>
      </template>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑参数' : '新增参数'" width="600px" :close-on-click-modal="false">
      <el-form :model="form" label-width="96px">
        <el-form-item label="参数名称" required>
          <el-input v-model="form.paramName" placeholder="如：文书归档接口地址" />
        </el-form-item>
        <el-form-item label="参数键" required>
          <el-input v-model="form.paramKey" :disabled="!!form.id" placeholder="如：ARCHIVE_API_URL" />
          <div class="form-tip">参数键为程序读取用的唯一标识，新增后不可修改</div>
        </el-form-item>
        <el-form-item label="参数值">
          <el-input v-model="form.paramValue" type="textarea" :rows="3"
                    placeholder="如：http://100.120.1.102:8080/zing-api-server/rest/execute/文书归档" />
        </el-form-item>
        <el-form-item label="分组">
          <el-input v-model="form.paramGroup" placeholder="archive" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="form.sortNo" :min="0" :step="1" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0"
                     active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSave" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import request from '../api/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const list = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)

const emptyForm = () => ({
  id: null,
  paramName: '',
  paramKey: '',
  paramValue: '',
  paramGroup: 'archive',
  sortNo: 0,
  status: 1,
  remark: ''
})
const form = reactive(emptyForm())

async function loadList() {
  loading.value = true
  try {
    list.value = await request.get('/sys-param/list') || []
  } catch (e) {
    console.error('参数列表加载失败', e)
    ElMessage.error('参数列表加载失败')
  } finally {
    loading.value = false
  }
}

function openAdd() {
  Object.assign(form, emptyForm())
  dialogVisible.value = true
}

function openEdit(row) {
  Object.assign(form, emptyForm(), {
    id: row.id,
    paramName: row.paramName,
    paramKey: row.paramKey,
    paramValue: row.paramValue || '',
    paramGroup: row.paramGroup || 'archive',
    sortNo: row.sortNo || 0,
    status: row.status === 0 ? 0 : 1,
    remark: row.remark || ''
  })
  dialogVisible.value = true
}

async function onSave() {
  if (!form.paramName || !form.paramKey) {
    ElMessage.warning('参数名称与参数键不能为空')
    return
  }
  saving.value = true
  try {
    await request.post('/sys-param/save', { ...form })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadList()
  } catch (e) {
    console.error('参数保存失败', e)
    ElMessage.error('保存失败：' + (e.message || e))
  } finally {
    saving.value = false
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
    return // 取消
  }
  try {
    await request.post('/sys-param/delete', null, { params: { id: row.id } })
    ElMessage.success('已删除')
    await loadList()
  } catch (e) {
    console.error('参数删除失败', e)
    ElMessage.error('删除失败：' + (e.message || e))
  }
}

function fmtTime(t) {
  if (!t) return '—'
  return String(t).replace('T', ' ').substring(0, 19)
}

onMounted(loadList)
</script>

<style scoped>
.param-config { padding: 16px 20px 24px; background: #f5f7fa; min-height: 100%; box-sizing: border-box; }

.page-head {
  display: flex; align-items: flex-start; justify-content: space-between;
  gap: 16px; background: #fff; border: 1px solid #ebeef5; border-radius: 6px;
  padding: 14px 18px; margin-bottom: 12px;
}
.page-head h2 { margin: 0 0 6px; font-size: 18px; font-weight: 700; color: #292524; }
.page-desc { margin: 0; font-size: 13px; color: #78716c; line-height: 1.6; }
.page-desc code { background: #f4f4f5; padding: 1px 5px; border-radius: 3px; color: #0f766e; font-size: 12px; }
.head-right { display: flex; gap: 8px; flex-shrink: 0; }

.p-name { font-weight: 600; color: #303133; }
.p-flag { margin-left: 6px; }
.p-value { color: #0f766e; font-size: 12px; word-break: break-all; }
.p-value.empty { color: #c0c4cc; }
.tbl-empty { color: #c0c4cc; font-size: 13px; }
.form-tip { font-size: 11px; color: #a8a29e; line-height: 1.5; margin-top: 2px; }
</style>
