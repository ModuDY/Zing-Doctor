<template>
  <div style="padding: 40px; text-align: center">
    <el-result icon="warning" title="页面不存在" sub-title="请确认外链页面编码已注册，或从 ICU 系统重新进入。">
      <template #extra>
        <el-button type="primary" @click="$router.push('/page/abx-patient-list')">返回患者列表</el-button>
      </template>
    </el-result>
  </div>
</template>

<script>
export default {
  name: 'NotFound'
}
</script>
