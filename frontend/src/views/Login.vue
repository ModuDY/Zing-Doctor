<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <div class="brand-logo">抗</div>
        <div class="brand-text">
          <div class="brand-title">医生决策系统</div>
          <div class="brand-sub">ICU 抗生素分析</div>
        </div>
      </div>

      <div class="login-title">账号登录</div>
      <div class="login-desc">请使用院内分配的账号登录，第三方系统通过外链访问无需登录。</div>

      <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent>
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="账号"
            :prefix-icon="User"
            autocomplete="username"
            @keyup.enter="submit" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
            autocomplete="current-password"
            @keyup.enter="submit" />
        </el-form-item>

        <el-button class="submit-btn" type="primary" size="large" :loading="loading" @click="submit">
          登 录
        </el-button>
      </el-form>

      <div class="login-tip">初始管理员账号 admin / zing@123，首次登录后请尽快修改。</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login as loginApi } from '../api/auth'
import { setToken, setUser } from '../utils/auth'

const route = useRoute()
const router = useRouter()

const formRef = ref(null)
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

onMounted(() => {
  // 外链访问（第三方系统带凭证进入）不需要登录，进来时直接放行到目标页
  if (route.query.redirect) return
})

async function submit() {
  if (loading.value) return
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  loading.value = true
  try {
    const data = await loginApi(form.username.trim(), form.password)
    setToken(data.token)
    setUser({ username: data.username, realName: data.realName })
    ElMessage.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.replace(redirect)
  } catch (e) {
    // 后端对「账号不存在 / 密码错误 / 已停用」统一返回 401 + 明确文案
    ElMessage.error(e?.message || '登录失败，请检查账号和密码')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 与 MainLayout 外壳同一套暖石色，避免登录后视觉跳变 */
  background: #fafaf9;
  padding: 24px;
}

.login-card {
  width: 100%;
  max-width: 400px;
  background: #fff;
  border: 1px solid #e7e5e4;
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(41, 37, 36, 0.06);
  padding: 36px 32px 28px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 28px;
}

.brand-logo {
  width: 40px;
  height: 40px;
  background: linear-gradient(135deg, #57534e, #292524);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 20px;
  font-weight: 600;
}

.brand-title {
  font-size: 16px;
  font-weight: 600;
  color: #292524;
  line-height: 1.3;
}

.brand-sub {
  font-size: 12px;
  color: #a8a29e;
  line-height: 1.3;
}

.login-title {
  font-size: 20px;
  font-weight: 600;
  color: #292524;
  margin-bottom: 6px;
}

.login-desc {
  font-size: 13px;
  color: #a8a29e;
  margin-bottom: 22px;
  line-height: 1.6;
}

.submit-btn {
  width: 100%;
  margin-top: 4px;
  /* 中性暖灰主色：与侧边栏同一套语言，不与抗菌药（青）/质控（橙）主题打架 */
  background: #292524;
  border-color: #292524;
}

.submit-btn:hover,
.submit-btn:focus {
  background: #44403c;
  border-color: #44403c;
}

.login-tip {
  margin-top: 18px;
  font-size: 12px;
  color: #a8a29e;
  line-height: 1.6;
  border-top: 1px solid #f5f5f4;
  padding-top: 14px;
}
</style>
