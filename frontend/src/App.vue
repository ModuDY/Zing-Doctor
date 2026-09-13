<template>
  <MainLayout v-if="!externalMode">
    <PageBoundary>
      <router-view />
    </PageBoundary>
  </MainLayout>
  <PageBoundary v-else>
    <router-view />
  </PageBoundary>
</template>

<script>
import MainLayout from './layouts/MainLayout.vue'
import PageBoundary from './layouts/PageBoundary.vue'
import { isExternalMode } from './utils/external'

export default {
  name: 'App',
  components: { MainLayout, PageBoundary },
  data() {
    return {
      externalMode: isExternalMode()
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
