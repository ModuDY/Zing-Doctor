<template>
  <!-- Element Plus 按需引入后不能再 app.use(ElementPlus, { locale }) 全局注册，
       语言包改由 config-provider 提供（该组件由 unplugin-vue-components 自动引入） -->
  <el-config-provider :locale="zhCn">
    <MainLayout v-if="useLayout">
      <PageBoundary>
        <router-view />
      </PageBoundary>
    </MainLayout>
    <PageBoundary v-else>
      <router-view />
    </PageBoundary>
  </el-config-provider>
</template>

<script>
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import MainLayout from './layouts/MainLayout.vue'
import PageBoundary from './layouts/PageBoundary.vue'
import { isExternalMode } from './utils/external'

export default {
  name: 'App',
  components: { MainLayout, PageBoundary },
  data() {
    return {
      zhCn,
      externalMode: isExternalMode()
    }
  },
  computed: {
    // 外链模式下第三方系统只需要功能页；登录页是独立整屏页面，两种模式都不套侧边栏
    useLayout() {
      return !this.externalMode && this.$route.name !== 'login'
    }
  }
}
</script>

<style>
html, body {
  margin: 0;
  padding: 0;
  background: #f5f7fa;
  font-family: 'PingFang SC', 'Microsoft YaHei', 'Segoe UI', Arial, sans-serif;
}
</style>
