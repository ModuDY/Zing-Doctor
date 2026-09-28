<template>
  <div class="pkpd-config abx-theme">
    <div class="filter-bar">
      <div class="filter-left">
        <el-input v-model="keyword" clearable placeholder="搜索药品名称" style="width: 220px" @input="filterList" :prefix-icon="Search" />
        <el-select v-model="filterType" placeholder="PK/PD类型" clearable style="width: 170px" popper-class="abx-popper" @change="filterList">
          <el-option label="时间依赖性" value="TIME_DEPENDENT" />
          <el-option label="浓度依赖性" value="CONCENTRATION_DEPENDENT" />
          <el-option label="时间依赖性+长PAE" value="TIME_DEPENDENT_LONG_PAE" />
        </el-select>
      </div>
      <div class="filter-right">
        <el-button type="primary" @click="openAdd"><el-icon><Plus /></el-icon> 新增药物</el-button>
        <el-button :loading="loading" @click="loadList"><el-icon><Refresh /></el-icon> 刷新</el-button>
      </div>
    </div>

    <div class="summary-bar">
      <div class="summary-item"><span class="label">药物总数</span><span class="value">{{ list.length }}</span></div>
      <div class="summary-item"><span class="label">时间依赖性</span><span class="value success">{{ countByType.TIME_DEPENDENT || 0 }}</span></div>
      <div class="summary-item"><span class="label">浓度依赖性</span><span class="value warning">{{ countByType.CONCENTRATION_DEPENDENT || 0 }}</span></div>
      <div class="summary-item"><span class="label">需TDM</span><span class="value danger">{{ tdmCount }}</span></div>
    </div>

    <div class="config-table">
      <el-table :data="filteredList" stripe size="default" max-height="620">
        <el-table-column prop="drugName" label="药品通用名" min-width="130">
          <template #default="{ row }"><span class="drug-name">{{ row.drugName }}</span></template>
        </el-table-column>
        <el-table-column prop="drugFullName" label="药品全称" min-width="160" show-overflow-tooltip />
        <el-table-column label="PK/PD类型" width="130" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.pkpdType)" size="small">{{ typeText(row.pkpdType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="目标" width="130" align="center">
          <template #default="{ row }">{{ row.targetParam }} {{ row.targetValue }}</template>
        </el-table-column>
        <el-table-column prop="proteinBinding" label="蛋白结合率" width="100" align="center">
          <template #default="{ row }">{{ row.proteinBinding }}%</template>
        </el-table-column>
        <el-table-column label="清除途径" width="90" align="center">
          <template #default="{ row }">{{ routeText(row.clearanceRoute) }}</template>
        </el-table-column>
        <el-table-column prop="usualDose" label="常用剂量" min-width="150" show-overflow-tooltip />
        <el-table-column label="TDM" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.tdmRequired === 1" type="danger" size="small">需</el-tag>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="70" align="center">
          <template #default="{ row }">
            <el-switch :model-value="row.status === 1" @change="toggleStatus(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editing ? '编辑药物' : '新增药物'" width="640px" :close-on-click-modal="false">
      <el-form :model="form" label-width="110px" label-position="right">
        <el-form-item label="药品通用名" required>
          <el-input v-model="form.drugName" placeholder="如：头孢呋辛（匹配关键词）" />
        </el-form-item>
        <el-form-item label="药品全称">
          <el-input v-model="form.drugFullName" placeholder="如：注射用头孢呋辛钠（展示用）" />
        </el-form-item>
        <el-form-item label="PK/PD类型" required>
          <el-select v-model="form.pkpdType" style="width: 100%">
            <el-option label="时间依赖性（β-内酰胺类）" value="TIME_DEPENDENT" />
            <el-option label="浓度依赖性（氨基糖苷/喹诺酮）" value="CONCENTRATION_DEPENDENT" />
            <el-option label="时间依赖性+长PAE（万古/利奈唑胺）" value="TIME_DEPENDENT_LONG_PAE" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标参数">
          <el-select v-model="form.targetParam" style="width: 100%">
            <el-option label="%T>MIC" value="%T>MIC" />
            <el-option label="Cmax/MIC" value="Cmax/MIC" />
            <el-option label="AUC/MIC" value="AUC/MIC" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标值">
          <el-input v-model="form.targetValue" placeholder="如：≥50-70%" />
        </el-form-item>
        <el-form-item label="蛋白结合率(%)">
          <el-input-number v-model="form.proteinBinding" :min="0" :max="100" style="width: 100%" />
        </el-form-item>
        <el-form-item label="清除途径">
          <el-select v-model="form.clearanceRoute" style="width: 100%">
            <el-option label="肾脏清除" value="renal" />
            <el-option label="肝脏清除" value="hepatic" />
            <el-option label="肝肾双通道" value="dual" />
          </el-select>
        </el-form-item>
        <el-form-item label="常用剂量">
          <el-input v-model="form.usualDose" placeholder="如：0.75-1.5g q8h" />
        </el-form-item>
        <el-form-item label="剂量调整">
          <el-input v-model="form.doseAdjust" type="textarea" :rows="2" placeholder="如：CrCl<20 调整间隔" />
        </el-form-item>
        <el-form-item label="高蛋白结合率">
          <el-switch v-model="form.highProteinBinding" :active-value="1" :inactive-value="0" />
          <span class="form-hint">≥80%，低蛋白血症时需提醒</span>
        </el-form-item>
        <el-form-item label="需要TDM">
          <el-switch v-model="form.tdmRequired" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import axios from 'axios'
import { Search, Plus, Refresh } from '@element-plus/icons-vue'

export default {
  name: 'PkpdKnowledgeConfig',
  components: { Search, Plus, Refresh },
  data() {
    return {
      loading: false,
      saving: false,
      list: [],
      keyword: '',
      filterType: '',
      dialogVisible: false,
      editing: false,
      form: this.emptyForm()
    }
  },
  computed: {
    filteredList() {
      const kw = this.keyword.trim().toLowerCase()
      return this.list.filter(r => {
        if (this.filterType && r.pkpdType !== this.filterType) return false
        if (!kw) return true
        return (r.drugName || '').toLowerCase().includes(kw) ||
               (r.drugFullName || '').toLowerCase().includes(kw)
      })
    },
    countByType() {
      const m = {}
      this.list.forEach(r => { m[r.pkpdType] = (m[r.pkpdType] || 0) + 1 })
      return m
    },
    tdmCount() { return this.list.filter(r => r.tdmRequired === 1).length }
  },
  created() { this.loadList() },
  methods: {
    emptyForm() {
      return {
        id: null, drugName: '', drugFullName: '', pkpdType: 'TIME_DEPENDENT',
        targetParam: '%T>MIC', targetValue: '', proteinBinding: 0,
        clearanceRoute: 'renal', usualDose: '', doseAdjust: '',
        highProteinBinding: 0, tdmRequired: 0, remark: '', status: 1
      }
    },
    async loadList() {
      this.loading = true
      try {
        const { data } = await axios.get('/api/antibiotic/pkpd-knowledge/list')
        this.list = data.data || []
      } catch (e) {
        this.$message.error('加载失败: ' + (e.response?.data?.message || e.message))
      } finally { this.loading = false }
    },
    filterList() {},
    typeText(t) {
      return { TIME_DEPENDENT: '时间依赖性', CONCENTRATION_DEPENDENT: '浓度依赖性', TIME_DEPENDENT_LONG_PAE: '时间依赖+长PAE' }[t] || t
    },
    typeTag(t) {
      return { TIME_DEPENDENT: 'success', CONCENTRATION_DEPENDENT: 'warning', TIME_DEPENDENT_LONG_PAE: 'info' }[t] || 'info'
    },
    routeText(r) {
      return { renal: '肾脏', hepatic: '肝脏', dual: '肝肾双' }[r] || r
    },
    openAdd() { this.form = this.emptyForm(); this.editing = false; this.dialogVisible = true },
    openEdit(row) { this.form = { ...row }; this.editing = true; this.dialogVisible = true },
    async save() {
      if (!this.form.drugName || !this.form.drugName.trim()) { this.$message.warning('药品通用名不能为空'); return }
      if (!this.form.pkpdType) { this.$message.warning('PK/PD类型不能为空'); return }
      this.saving = true
      try {
        if (this.editing) {
          await axios.put('/api/antibiotic/pkpd-knowledge', this.form)
          this.$message.success('更新成功')
        } else {
          await axios.post('/api/antibiotic/pkpd-knowledge', this.form)
          this.$message.success('新增成功')
        }
        this.dialogVisible = false
        this.loadList()
      } catch (e) {
        this.$message.error('保存失败: ' + (e.response?.data?.message || e.message))
      } finally { this.saving = false }
    },
    async toggleStatus(row) {
      row.status = row.status === 1 ? 0 : 1
      try { await axios.put('/api/antibiotic/pkpd-knowledge', row) }
      catch (e) { row.status = row.status === 1 ? 0 : 1; this.$message.error('操作失败') }
    },
    async remove(row) {
      try {
        await this.$confirm(`确认删除「${row.drugName}」？`, '删除确认', { type: 'warning' })
        await axios.delete(`/api/antibiotic/pkpd-knowledge/${row.id}`)
        this.$message.success('删除成功')
        this.loadList()
      } catch (e) { if (e !== 'cancel') this.$message.error('删除失败') }
    }
  }
}
</script>

<style scoped>
.pkpd-config { padding: 16px; }
.filter-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; gap: 12px; flex-wrap: wrap; }
.filter-left { display: flex; gap: 10px; align-items: center; }
.filter-right { display: flex; gap: 8px; }
.summary-bar { display: flex; gap: 16px; margin-bottom: 14px; padding: 12px 16px; background: #fff; border-radius: 8px; border: 1px solid #e8e8e8; }
.summary-item { display: flex; flex-direction: column; gap: 2px; }
.summary-item .label { font-size: 12px; color: #909399; }
.summary-item .value { font-size: 20px; font-weight: 700; color: #303133; }
.summary-item .value.success { color: #67c23a; }
.summary-item .value.warning { color: #e6a23c; }
.summary-item .value.danger { color: #f56c6c; }
.config-table { background: #fff; border-radius: 8px; border: 1px solid #e8e8e8; padding: 12px; }
.drug-name { font-weight: 600; color: #303133; }
.muted { color: #c0c4cc; }
.form-hint { margin-left: 10px; font-size: 12px; color: #909399; }
</style>
