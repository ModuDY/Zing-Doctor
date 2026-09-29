<template>
  <div class="login-page">
    <div class="login-shell">
      <!-- 左侧：品牌文字（院名 / 系统名 / 描述） -->
      <aside class="brand-panel">
        <div class="brand-text">
          <h1 class="bt-hospital">{{ hospitalNames[0] }}</h1>
          <h2 class="bt-system">重症医生决策系统</h2>
          <p class="bt-desc">
            重症临床决策与质控平台，评分、抗感染、质控一站式完成。
          </p>
        </div>
      </aside>

      <!-- 右侧：登录卡片（顶部院徽 + 表单） -->
      <main class="form-panel">
        <div class="form-side">
          <div class="form-wrap">
            <img class="card-logo" :src="logo" alt="院徽" />

            <el-form
              ref="formRef"
              :model="form"
              :rules="rules"
              label-position="top"
              size="large"
              @submit.prevent>
              <el-form-item label="账号" prop="username">
                <el-input
                  v-model="form.username"
                  placeholder="请输入账号"
                  :prefix-icon="User"
                  autocomplete="username"
                  @keyup.enter="submit" />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input
                  v-model="form.password"
                  type="password"
                  placeholder="请输入密码"
                  :prefix-icon="Lock"
                  show-password
                  autocomplete="current-password"
                  @keyup.enter="submit" />
              </el-form-item>

              <el-button
                class="submit-btn"
                type="primary"
                size="large"
                :loading="loading"
                @click="submit">
                登 录
              </el-button>
            </el-form>

            <div class="fp-note">
              <el-icon class="note-ic"><Link /></el-icon>
              第三方系统通过外链访问，无需登录
            </div>
          </div>
        </div>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock, Link } from '@element-plus/icons-vue'
import { login as loginApi } from '../api/auth'
import { setToken, setUser } from '../utils/auth'
import { useDocHeader } from '../utils/useDocHeader'

const route = useRoute()
const router = useRouter()

const { logo, hospitalNames, load: loadDocHeader } = useDocHeader()

const formRef = ref(null)
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

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
  min-height: 100dvh;
  /* 完整插画铺整页背景，内容浮于背景上 */
  background:
    linear-gradient(rgba(245, 245, 244, 0.18), rgba(245, 245, 244, 0.18)),
    url('/login-bg.jpg') left center/cover no-repeat fixed;
}

.login-shell {
  display: grid;
  grid-template-columns: 1fr 1fr;
  min-height: 100vh;
  min-height: 100dvh;
}

/* ---------------- 左侧：品牌文字（右对齐，与人物叠加） ---------------- */
.brand-panel {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 40px 8% 40px 9%;
  background: transparent;
}
.brand-text {
  max-width: 480px;
  text-align: left;
}
.bt-hospital {
  font-size: 38px;
  font-weight: 800;
  letter-spacing: 1px;
  color: #1c1917;
  margin: 0;
}
.bt-system {
  font-size: 38px;
  font-weight: 400;
  letter-spacing: 1px;
  color: #292524;
  margin: 10px 0 0;
}
.bt-desc {
  font-size: 13.5px;
  line-height: 1.7;
  color: #57534e;
  margin: 16px 0 0;
  white-space: nowrap;
}

/* ---------------- 右侧：登录卡片 ---------------- */
.form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 9% 40px 8%;
  background: transparent;
}
.form-side {
  width: 100%;
  max-width: 400px;
}

.form-wrap {
  background: rgba(255, 255, 255, 0.60);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: 16px;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.7), 0 14px 44px rgba(41, 37, 36, 0.10);
  padding: 34px 32px 26px;
}

.card-logo {
  display: block;
  width: 76px;
  height: 76px;
  object-fit: contain;
  margin: 0 auto 24px;
}

/* label 在输入框上方，不拿 placeholder 当标签 */
.form-wrap :deep(.el-form-item__label) {
  font-weight: 600;
  color: #44403c;
  padding-bottom: 6px;
}
.form-wrap :deep(.el-input__wrapper) {
  border-radius: 12px;
  box-shadow: 0 0 0 1px #e7e5e4 inset;
  background: #fff;
}
.form-wrap :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #ea580c inset;
}
.form-wrap :deep(.el-input__inner) {
  height: 42px;
}

.submit-btn {
  width: 100%;
  height: 46px;
  margin-top: 6px;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 4px;
  border-radius: 12px;
  background: #ea580c;
  border-color: #ea580c;
  transition: background 0.18s ease, transform 0.08s ease;
}
.submit-btn:hover,
.submit-btn:focus {
  background: #c2410c;
  border-color: #c2410c;
}
.submit-btn:active {
  transform: scale(0.985);
}

.fp-note {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-top: 20px;
  font-size: 12.5px;
  color: #a8a29e;
}
.note-ic {
  font-size: 14px;
}

/* ---------------- 响应式 ---------------- */
@media (max-width: 900px) {
  .login-shell {
    grid-template-columns: 1fr;
  }
  .brand-panel {
    justify-content: center;
    text-align: center;
    padding: 36px 24px 8px;
  }
  .brand-text {
    max-width: none;
  }
  .bt-desc {
    margin-left: auto;
    margin-right: auto;
  }
  .form-panel {
    padding: 20px 24px 36px;
  }
}
</style>
