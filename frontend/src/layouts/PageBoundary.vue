<template>
  <slot v-if="!error" />
  <div v-else class="page-boundary">
    <div class="page-boundary-card">
      <div class="page-boundary-title">页面渲染失败</div>
      <div class="page-boundary-msg">{{ error.message }}</div>
      <div class="page-boundary-tip">
        这是前端运行时异常（通常是参数缺失、数据格式不符或某个页面组件自身报错）。
        请把上面这行信息连同科室/时间范围一起反馈给开发；也可切到其他页面或刷新继续使用。
      </div>
      <div class="page-boundary-actions">
        <el-button size="small" @click="reset">重试渲染</el-button>
        <el-button size="small" type="primary" @click="reload">刷新页面</el-button>
      </div>
    </div>
  </div>
</template>

<script>
/**
 * 页面级错误边界。
 *
 * 没有它时：任何一个视图组件在渲染期抛错（例如 ReferenceError / 数据字段缺失），
 * Vue 会直接卸载整棵组件树，用户看到的就是「整页白屏、什么都没有」，
 * 现场又拿不到控制台，排查成本极高。
 * 有了它：错误被就地捕获并原样展示，同时把「组件名 + 出错阶段」打到控制台。
 */
export default {
  name: 'PageBoundary',
  data() {
    return { error: null }
  },
  watch: {
    // 切换路由时清掉上一次的错误，避免错误态“粘住”后续页面
    '$route.fullPath'() {
      this.error = null
    }
  },
  errorCaptured(err, instance, info) {
    const name =
      (instance && instance.$options && instance.$options.name) ||
      (instance && instance.type && instance.type.__name) ||
      'unknown'
    console.error(
      `[frontend-error] ${(err && (err.message || err)) || ''} | component=${name} | phase=${info || 'render'}`
    )
    this.error = { message: (err && err.message) || String(err || '未知错误') }
    // 就地兜底，不再向上冒泡，避免整个应用被卸载成白屏
    return false
  },
  methods: {
    reset() {
      this.error = null
    },
    reload() {
      window.location.reload()
    }
  }
}
</script>

<style scoped>
.page-boundary {
  padding: 48px 24px;
  display: flex;
  justify-content: center;
}
.page-boundary-card {
  max-width: 720px;
  width: 100%;
  background: #fff;
  border: 1px solid #f0d9d9;
  border-left: 4px solid #f56c6c;
  border-radius: 6px;
  padding: 20px 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
}
.page-boundary-title {
  font-size: 16px;
  font-weight: 600;
  color: #f56c6c;
  margin-bottom: 10px;
}
.page-boundary-msg {
  font-family: Consolas, Monaco, monospace;
  font-size: 13px;
  color: #303133;
  background: #f7f8fa;
  padding: 10px 12px;
  border-radius: 4px;
  word-break: break-all;
  margin-bottom: 12px;
}
.page-boundary-tip {
  font-size: 13px;
  color: #909399;
  line-height: 1.7;
  margin-bottom: 16px;
}
</style>
