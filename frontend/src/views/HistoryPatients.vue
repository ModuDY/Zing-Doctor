<template>
  <div class="history-page">
    <div class="page-head">
      <button class="back-btn" @click="goBack">← 返回工作台</button>
      <div class="head-main">
        <h2 class="page-title">历史患者</h2>
        <p class="page-sub">
          已出科患者。用于出院后复盘、死亡病例讨论 —— 摘要、监测、时间线、查房记录均可查看，
          但<span class="ro-note">只读</span>：不能补录或修改已出科患者的查房记录。
        </p>
      </div>
      <div class="head-tools">
        <el-input
          v-model="keyword"
          placeholder="姓名 / 住院号"
          clearable
          class="kw-input"
          @keyup.enter="load"
          @clear="load"
        />
        <el-button type="primary" :icon="SearchIcon" :loading="loading" @click="load">查询</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="stat-line">
      <span class="stat-item">已出科 <b>{{ list.length }}</b> 人</span>
      <span class="stat-sep"></span>
      <span class="stat-item">{{ scopeText }}</span>
      <span class="stat-sep"></span>
      <span class="stat-item muted">{{ updatedAt ? '更新于 ' + updatedAt : '' }}</span>
    </div>

    <div v-if="error" class="state-box error">
      <el-icon><Warning /></el-icon>
      <div>
        <div class="state-title">{{ error }}</div>
        <el-button size="small" :loading="loading" @click="load">重试</el-button>
      </div>
    </div>

    <div v-else-if="!loading && !list.length" class="state-box">
      <div class="state-title">{{ keyword ? '没有匹配的历史患者' : '暂无已出科患者' }}</div>
      <div class="state-sub">按出科时间倒序，最多显示 300 人；可用姓名或住院号缩小范围</div>
    </div>

    <el-table
      v-else
      :data="list"
      v-loading="loading"
      class="history-table"
      size="small"
      @row-click="openDetail"
      row-class-name="clickable-row"
    >
      <el-table-column label="患者" min-width="150">
        <template #default="{ row }">
          <div class="cell-name">{{ row.name }}</div>
          <div class="cell-sub">{{ row.inHospitalNo }}</div>
        </template>
      </el-table-column>
      <el-table-column label="性别/年龄" width="92">
        <template #default="{ row }">{{ row.gender }} · {{ row.age }}岁</template>
      </el-table-column>
      <el-table-column label="科室" min-width="150">
        <template #default="{ row }">
          <div>{{ row.departName || '—' }}</div>
          <div class="cell-sub">{{ row.wardName || '—' }} {{ row.bedNo || '' }}</div>
        </template>
      </el-table-column>
      <el-table-column label="主管医生" width="100">
        <template #default="{ row }">{{ row.attendingDoctor || '—' }}</template>
      </el-table-column>
      <el-table-column label="住科天数" width="88" align="right">
        <template #default="{ row }">{{ row.icuDays != null ? row.icuDays + ' 天' : '—' }}</template>
      </el-table-column>
      <el-table-column label="入科 → 出科" min-width="230">
        <template #default="{ row }">
          <span class="t-range">{{ shortTime(row.inDepartmentTime) }}</span>
          <span class="t-arrow">→</span>
          <span class="t-range">{{ shortTime(row.outDepartmentTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="" width="86" align="right">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click.stop="openDetail(row)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script>
import { ElMessage } from 'element-plus'
import { Refresh, Search as SearchIcon, Warning } from '@element-plus/icons-vue'
import { fetchDischargedPatients } from '../api/workbench'
import { currentDepart } from '../utils/departContext'

export default {
  name: 'HistoryPatients',
  components: { Refresh, SearchIcon, Warning },
  data() {
    return {
      keyword: '',
      list: [],
      loading: false,
      error: '',
      updatedAt: ''
    }
  },
  computed: {
    // 科室取全局上下文：历史患者同样受科室权限边界约束，不因为是历史数据就放开
    departCode() {
      return currentDepart.departCode || ''
    },
    scopeText() {
      if (!this.departCode) return '全院（未限定科室）'
      const names = [...new Set(this.list.map((p) => p.departName).filter(Boolean))]
      return names.length ? `科室：${names.join('、')}` : '已限定科室'
    }
  },
  watch: {
    // 左侧栏切换科室后自动重查
    departCode() {
      this.load()
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      this.error = ''
      try {
        const rows = await fetchDischargedPatients(this.departCode, this.keyword.trim())
        this.list = Array.isArray(rows) ? rows : []
        this.updatedAt = new Date().toLocaleString('zh-CN', { hour12: false })
      } catch (e) {
        this.list = []
        // 无科室权限时后端会明确报错，这里原样透出，避免医生以为是「没有历史患者」
        const msg = (e && (e.message || e.msg)) || ''
        this.error = msg.includes('权限')
          ? `当前账号无权查询该科室的历史患者：${msg}`
          : `历史患者加载失败：${msg || '请稍后重试'}`
      } finally {
        this.loading = false
      }
    },
    openDetail(row) {
      if (!row || !row.patientId) return
      this.$router.push({
        path: '/page/patient-summary',
        query: {
          patientId: row.patientId,
          inHospitalNo: row.inHospitalNo || '',
          patientName: row.name || ''
        }
      })
    },
    shortTime(t) {
      if (!t) return '—'
      return String(t).replace('T', ' ').slice(0, 16)
    },
    goBack() {
      this.$router.push('/page/patient-workbench')
    }
  }
}
</script>

<style scoped>
.history-page {
  padding: 16px 20px 24px;
}
.page-head {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  flex-wrap: wrap;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0efec;
}
.back-btn {
  border: none;
  background: none;
  color: #b45309;
  cursor: pointer;
  font-size: 13px;
  padding: 2px 0;
  white-space: nowrap;
}
.back-btn:hover {
  color: #92400e;
  text-decoration: underline;
}
.head-main {
  flex: 1;
  min-width: 260px;
}
.page-title {
  margin: 0;
  font-size: 18px;
  color: #1c1917;
}
.page-sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: #78716c;
  line-height: 1.6;
}
.ro-note {
  color: #b45309;
  font-weight: 600;
}
.head-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}
.kw-input {
  width: 190px;
}
.stat-line {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 2px;
  font-size: 12px;
  color: #57534e;
}
.stat-item b {
  color: #b45309;
  font-size: 14px;
  margin: 0 2px;
}
.stat-item.muted {
  color: #a8a29e;
}
.stat-sep {
  width: 1px;
  height: 12px;
  background: #e7e5e4;
}
.state-box {
  padding: 40px 20px;
  text-align: center;
  color: #a8a29e;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.state-box.error {
  color: #b91c1c;
  flex-direction: row;
  justify-content: center;
  gap: 10px;
  text-align: left;
}
.state-title {
  font-size: 13px;
  color: #57534e;
}
.state-box.error .state-title {
  color: #b91c1c;
  margin-bottom: 4px;
}
.state-sub {
  font-size: 12px;
}
.history-table {
  width: 100%;
}
.cell-name {
  font-weight: 600;
  color: #1c1917;
}
.cell-sub {
  font-size: 11px;
  color: #a8a29e;
}
.t-range {
  font-size: 12px;
  color: #57534e;
}
.t-arrow {
  color: #d6d3d1;
  margin: 0 4px;
}
:deep(.clickable-row) {
  cursor: pointer;
}
:deep(.clickable-row:hover > td.el-table__cell) {
  background: #fffaf3 !important;
}
</style>