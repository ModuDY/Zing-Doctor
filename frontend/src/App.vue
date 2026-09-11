<template>
  <MainLayout v-if="!externalMode">
    <router-view />
  </MainLayout>
  <router-view v-else />
</template>

<script>
import MainLayout from './layouts/MainLayout.vue'
import { isExternalMode } from './utils/external'

export default {
  name: 'App',
  components: { MainLayout },
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
