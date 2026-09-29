<template>
  <div class="sofa-cfg">
    <div class="head">
      <h2>SOFA 配置管理</h2>
      <div class="filters">
        <select v-model="configType" class="tb-input" @change="load">
          <option value="">全部类型</option>
          <option v-for="t in typeOptions" :key="t.value" :value="t.value">{{ t.label }}</option>
        </select>
        <button class="btn btn-primary" @click="openDialog(null)">新增配置</button>
        <button class="btn" @click="load">刷新</button>
      </div>
    </div>

    <div class="tip">
      配置表为空时系统回退代码内置默认值；停用（status=0）的项**不参与取数**。同类型同键视为同一条配置。
    </div>

    <div class="panel">
      <table class="tbl">
        <thead>
          <tr>
            <th style="width:130px">类型</th>
            <th style="width:150px">配置键</th>
            <th>配置值</th>
            <th style="width:150px">项目名称</th>
            <th>备注</th>
            <th style="width:60px">排序</th>
            <th style="width:70px">状态</th>
            <th style="width:170px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="c in list" :key="c.id" :class="{ disabled: c.status !== 1 }">
            <td><span class="type-tag">{{ typeLabel(c.configType) }}</span></td>
            <td><code>{{ c.configKey }}</code></td>
            <td class="val">{{ c.configValue }}</td>
            <td>{{ c.itemName || '—' }}</td>
            <td class="remark">{{ c.remark || '—' }}</td>
            <td>{{ c.sortNo }}</td>
            <td>
              <span :class="['status-tag', c.status === 1 ? 'on' : 'off']">
                {{ c.status === 1 ? '启用' : '停用' }}
              </span>
            </td>
            <td>
              <button class="btn btn-text" @click="openDialog(c)">编辑</button>
              <button class="btn btn-text" @click="toggle(c)">{{ c.status === 1 ? '停用' : '启用' }}</button>
              <button class="btn btn-text danger" @click="remove(c)">删除</button>
            </td>
          </tr>
          <tr v-if="!list.length">
            <td colspan="8" class="empty">暂无配置项</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 编辑弹窗 -->
    <div v-if="showDialog" class="modal-mask" @click.self="showDialog = false">
      <div class="modal">
        <div class="modal-head">
          <h3>{{ form.id ? '编辑配置' : '新增配置' }}</h3>
          <button class="modal-close" @click="showDialog = false">×</button>
        </div>
        <div class="modal-body">
          <div class="field">
            <label>配置类型 <span class="req">*</span></label>
            <select v-model="form.configType" class="tb-input full">
              <option v-for="t in typeOptions" :key="t.value" :value="t.value">{{ t.label }}（{{ t.value }}）</option>
            </select>
          </div>
          <div class="field">
            <label>配置键 <span class="req">*</span></label>
            <input v-model.trim="form.configKey" class="tb-input full" placeholder="如 platelet / map / norepinephrine / M_18_39" />
          </div>
          <div class="field">
            <label>配置值</label>
            <textarea v-model.trim="form.configValue" class="tb-input full" rows="2"
                      placeholder="如 200050 / 100010,100020,100030 / 去甲肾上腺素,0.1"></textarea>
          </div>
          <div class="field">
            <label>项目名称</label>
            <input v-model.trim="form.itemName" class="tb-input full" placeholder="展示用，如 血小板" />
          </div>
          <div class="field">
            <label>备注</label>
            <input v-model.trim="form.remark" class="tb-input full" placeholder="说明该项含义 / 注意事项" />
          </div>
          <div class="field">
            <label>排序</label>
            <input v-model.number="form.sortNo" class="tb-input full" type="number" />
          </div>
        </div>
        <div class="modal-foot">
          <button class="btn" @click="showDialog = false">取消</button>
          <button class="btn btn-primary" :disabled="saving" @click="submit">{{ saving ? '保存中…' : '保存' }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchSofaConfig, saveSofaConfig, deleteSofaConfig, toggleSofaConfig } from '../api/sofa'

const typeOptions = [
  { value: 'lis_item', label: '检验项' },
  { value: 'observe_item', label: '监护项' },
  { value: 'io_item', label: '出入量项' },
  { value: 'vasopressor', label: '血管活性药' },
  { value: 'conversion', label: '换算系数' },
  { value: 'default_weight', label: '默认体重' }
]

const configType = ref('')
const list = ref([])
const showDialog = ref(false)
const saving = ref(false)
const form = reactive({
  id: null, configType: 'lis_item', configKey: '', configValue: '',
  itemName: '', remark: '', sortNo: 1
})

onMounted(() => load())

function typeLabel(v) {
  const t = typeOptions.find(x => x.value === v)
  return t ? t.label : (v || '—')
}

async function load() {
  try {
    const res = await fetchSofaConfig(configType.value)
    list.value = res || []
  } catch (e) {
    console.warn('配置加载失败：', e.message || e)
  }
}

function openDialog(row) {
  if (row) {
    Object.assign(form, {
      id: row.id,
      configType: row.configType,
      configKey: row.configKey,
      configValue: row.configValue || '',
      itemName: row.itemName || '',
      remark: row.remark || '',
      sortNo: row.sortNo == null ? 1 : row.sortNo
    })
  } else {
    Object.assign(form, {
      id: null,
      configType: configType.value || 'lis_item',
      configKey: '', configValue: '', itemName: '', remark: '', sortNo: 1
    })
  }
  showDialog.value = true
}

async function submit() {
  if (!form.configKey) {
    ElMessage.warning('配置键不能为空')
    return
  }
  saving.value = true
  try {
    await saveSofaConfig({ ...form })
    ElMessage.success('已保存')
    showDialog.value = false
    await load()
  } catch (e) {
    console.warn('保存失败：', e.message || e)
  } finally {
    saving.value = false
  }
}

async function toggle(row) {
  try {
    await toggleSofaConfig(row.id, row.status === 1 ? 0 : 1)
    ElMessage.success(row.status === 1 ? '已停用' : '已启用')
    await load()
  } catch (e) {
    console.warn('操作失败：', e.message || e)
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除配置「${row.configKey}」？`, '删除确认', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await deleteSofaConfig(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    console.warn('删除失败：', e.message || e)
  }
}
</script>

<style scoped>
.sofa-cfg { padding: 16px 20px 40px; background: #f5f7fa; min-height: 100vh; }
.head { display: flex; align-items: center; gap: 16px; margin-bottom: 12px; }
.head h2 { margin: 0; font-size: 18px; color: #303133; }
.filters { display: flex; align-items: center; gap: 8px; margin-left: auto; }
.tip { background: #fdf6ec; color: #b88230; font-size: 13px; padding: 10px 14px; border-radius: 6px; margin-bottom: 14px; }
.panel { background: #fff; border-radius: 8px; padding: 14px 16px; }
.tb-input { height: 30px; padding: 0 8px; border: 1px solid #dcdfe6; border-radius: 4px; font-size: 13px; }
.tb-input.full { width: 100%; box-sizing: border-box; }
textarea.tb-input { height: auto; padding: 6px 8px; font-family: inherit; }
.tbl { width: 100%; border-collapse: collapse; font-size: 13px; }
.tbl th, .tbl td { padding: 8px 10px; border-bottom: 1px solid #ebeef5; text-align: left; vertical-align: top; }
.tbl th { background: #f5f7fa; color: #606266; font-weight: 500; }
.tbl tr.disabled { opacity: .55; }
.tbl td.val { color: #409eff; font-weight: 600; word-break: break-all; }
.tbl td.remark { color: #909399; }
.empty { text-align: center; color: #c0c4cc; padding: 24px 0; }
.type-tag { background: #ecf5ff; color: #409eff; padding: 1px 8px; border-radius: 10px; font-size: 12px; }
.status-tag { padding: 1px 8px; border-radius: 10px; font-size: 12px; }
.status-tag.on { background: #f0f9eb; color: #67c23a; }
.status-tag.off { background: #f4f4f5; color: #909399; }
.btn { height: 30px; padding: 0 12px; border: 1px solid #dcdfe6; background: #fff; border-radius: 4px; font-size: 13px; cursor: pointer; }
.btn-primary { background: #409eff; border-color: #409eff; color: #fff; }
.btn-primary:disabled { opacity: .6; cursor: not-allowed; }
.btn-text { border: none; background: transparent; color: #409eff; padding: 0 6px; }
.btn-text.danger { color: #f56c6c; }
.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,.4); display: flex; align-items: center; justify-content: center; z-index: 2000; }
.modal { width: 560px; max-height: 88vh; overflow: auto; background: #fff; border-radius: 8px; }
.modal-head { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid #ebeef5; }
.modal-head h3 { margin: 0; font-size: 15px; }
.modal-close { border: none; background: transparent; font-size: 20px; cursor: pointer; color: #909399; }
.modal-body { padding: 16px; }
.modal-foot { padding: 12px 16px; border-top: 1px solid #ebeef5; text-align: right; }
.field { margin-bottom: 12px; }
.field label { display: block; font-size: 13px; color: #606266; margin-bottom: 6px; }
.req { color: #f56c6c; }
.modal-foot .btn { margin-left: 8px; }
</style>
