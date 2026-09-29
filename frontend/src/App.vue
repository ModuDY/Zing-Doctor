<template>
  <!-- Element Plus 按需引入后不能再 app.use(ElementPlus, { locale }) 全局注册，
       语言包改由 config-provider 提供（该组件由 unplugin-vue-components 自动引入） -->
  <el-config-provider :locale="zhCn">
    <!-- 外链没带身份时的显式提示：不这么做的话，所有审计字段都记成 unknown，
         页面上只剩一个「未知」，现场分不清是系统坏了还是链接没配 -->
    <div v-if="showIdentityWarn" class="ext-identity-warn">
      <span class="ew-badge">!</span>
      <span class="ew-text">
        本次为 ICU 外链访问，<b>链接没有携带操作人身份</b>（模板里的 ${realname} / ${username} 未替换成实际医生姓名）：
        本次保存的记录、触发的计算批次，<b>操作人会记成「未知」</b>。请联系 ICU 侧把链接模板中这两个变量改成真值，重新进入即可生效。
      </span>
      <span class="ew-close" title="关闭（本次会话不再提示）" @click="dismissIdentityWarn">×</span>
    </div>
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
import { isExternalMode, hasExternalOperator } from './utils/external'
import { isLoggedIn } from './utils/auth'

/** 关闭提示的标记：按标签页隔离即可（重新进入时应再提醒一次） */
const KEY_WARN_DISMISSED = 'extIdentityWarnDismissed'

export default {
  name: 'App',
  components: { MainLayout, PageBoundary },
  data() {
    return {
      zhCn,
      externalMode: isExternalMode(),
      identityWarnDismissed: sessionStorage.getItem(KEY_WARN_DISMISSED) === '1'
    }
  },
  computed: {
    // 外链模式下第三方系统只需要功能页；登录页是独立整屏页面，两种模式都不套侧边栏
    useLayout() {
      return !this.externalMode && this.$route.name !== 'login'
    },
    /**
     * 是否需要提示「外链未携带身份」。
     *
     * 三个前提缺一不可：处于外链模式、不是直连登录（登录通道的姓名由服务端令牌反解，
     * 与外链参数无关）、本次会话确实没有可用身份。ICU 侧改好模板重新进入后，
     * 新会话会带上身份，提示自然不再出现。
     */
    showIdentityWarn() {
      return this.externalMode && !isLoggedIn() && !hasExternalOperator() && !this.identityWarnDismissed
    }
  },
  methods: {
    dismissIdentityWarn() {
      sessionStorage.setItem(KEY_WARN_DISMISSED, '1')
      this.identityWarnDismissed = true
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

/* 外链身份缺失提示条：贴顶常驻（滚动时仍在），层级低于 Element Plus 弹层 */
.ext-identity-warn {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 14px;
  background: #fdf6ec;
  border-bottom: 1px solid #f5dab1;
  color: #8a5b16;
  font-size: 13px;
  line-height: 1.6;
}

.ext-identity-warn .ew-badge {
  flex: none;
  width: 16px;
  height: 16px;
  margin-top: 2px;
  border-radius: 50%;
  background: #e6a23c;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  text-align: center;
  line-height: 16px;
}

.ext-identity-warn .ew-text b {
  color: #b35b12;
}

.ext-identity-warn .ew-close {
  flex: none;
  margin-left: auto;
  padding: 0 4px;
  color: #c0a06a;
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
}

.ext-identity-warn .ew-close:hover {
  color: #8a5b16;
}
</style>
