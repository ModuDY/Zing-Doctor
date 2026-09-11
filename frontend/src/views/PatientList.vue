<template>
  <div style="padding: 20px; max-width: 1200px; margin: 0 auto;">
    <el-page-header @back="goHome" content="疑似感染患者列表" style="margin-bottom: 16px" />

    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="外链集成提示"
      description="本页面已注册为可外链页面（pageCode: abx-patient-list）。ICU 系统可通过外链 URL 免登录直接打开，也可在本页直接浏览。" />
    <div style="height: 16px" />

    <el-card shadow="never">
      <el-table v-loading="loading" :data="patients" stripe style="width: 100%">
        <el-table-column prop="patientNo" label="住院号" width="140" />
        <el-table-column prop="name" label="姓名" width="90" />
        <el-table-column prop="age" label="年龄" width="60" />
        <el-table-column prop="gender" label="性别" width="60" />
        <el-table-column prop="bedNo" label="床位" width="70" />
        <el-table-column prop="infectionType" label="疑似感染类型" min-width="140" />
        <el-table-column label="脓毒性休克" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.septicShock ? 'danger' : 'info'" size="small">
              {{ row.septicShock ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="MRSA" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.mrsaRisk" type="warning" size="small">高</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="MDR" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.mdrRisk" type="warning" size="small">高</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="真菌" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.fungalRisk" type="warning" size="small">高</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column prop="pct" label="PCT" width="80" />
        <el-table-column prop="temperature" label="体温" width="80" />
        <el-table-column label="风险" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="riskType(row.riskLevel)" size="small">{{ row.riskLevel }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="goDecision(row)">进入决策</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script>
import { fetchPatients } from '../api/antibiotic'

export default {
  name: 'PatientList',
  data() {
    return {
      loading: false,
      patients: []
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        this.patients = await fetchPatients()
      } finally {
        this.loading = false
      }
    },
    goDecision(row) {
      // 内部跳转携带 patientId；外链签名上下文已存于 sessionStorage，自动随 API 请求带上
      this.$router.push(`/page/abx-decision?patientId=${row.patientId}`)
    },
    goHome() {
      this.$router.push('/page/abx-patient-list')
    },
    riskType(level) {
      if (level === '高风险') return 'danger'
      if (level === '中风险') return 'warning'
      return 'info'
    }
  }
}
</script>
