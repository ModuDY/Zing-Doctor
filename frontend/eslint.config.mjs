// ESLint 扁平配置（ESLint 10 只支持 flat config，故用 .mjs）
//
// 目标：能拦住真问题（未定义变量、未使用变量、模板结构错误、props 误改），
// 但不因为历史遗留的巨型组件风格问题把 CI 卡死 —— 因此把若干风格类规则降为 warn，
// error 级只保留会导致运行时报错或难以排查的规则。
import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import prettier from 'eslint-config-prettier'

export default [
  {
    // 构建产物与依赖目录不参与检查
    ignores: ['dist/**', 'node_modules/**', 'public/**', '*.min.js']
  },
  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  // 关闭所有与 Prettier 冲突的格式类规则（格式统一交给 Prettier）
  prettier,
  {
    // Node 环境脚本（构建配置、依赖审计门禁脚本）：跑在 Node 里，
    // 需要用 process 等 Node 全局，不能套浏览器 globals 的那套限制。
    files: ['scripts/**/*.{js,mjs}', '*.mjs', 'vite.config.mjs', 'eslint.config.mjs'],
    languageOptions: {
      globals: {
        process: 'readonly',
        console: 'readonly',
        URL: 'readonly',
        Buffer: 'readonly',
        __dirname: 'readonly',
        __filename: 'readonly',
        require: 'readonly',
        module: 'readonly',
        exports: 'readonly'
      }
    }
  },
  {
    files: ['**/*.{js,mjs,vue}'],
    languageOptions: {
      ecmaVersion: 2023,
      sourceType: 'module',
      globals: {
        // 浏览器全局（项目未引入 eslint globals 包，按实际用到的显式声明）
        window: 'readonly',
        document: 'readonly',
        navigator: 'readonly',
        location: 'readonly',
        history: 'readonly',
        console: 'readonly',
        sessionStorage: 'readonly',
        localStorage: 'readonly',
        setTimeout: 'readonly',
        clearTimeout: 'readonly',
        setInterval: 'readonly',
        clearInterval: 'readonly',
        requestAnimationFrame: 'readonly',
        getComputedStyle: 'readonly',
        fetch: 'readonly',
        URL: 'readonly',
        URLSearchParams: 'readonly',
        Blob: 'readonly',
        File: 'readonly',
        FileReader: 'readonly',
        FormData: 'readonly',
        Image: 'readonly',
        Event: 'readonly',
        CustomEvent: 'readonly',
        HTMLElement: 'readonly',
        MutationObserver: 'readonly',
        ResizeObserver: 'readonly',
        atob: 'readonly',
        btoa: 'readonly'
      }
    },
    rules: {
      // ---- error：会导致运行时报错或难以定位的问题 ----
      'no-undef': 'error',
      'no-dupe-keys': 'error',
      'no-unreachable': 'error',
      'vue/no-mutating-props': 'error',
      'vue/no-parsing-error': 'error',
      'vue/no-side-effects-in-computed-properties': 'error',
      'vue/no-reserved-component-names': 'error',
      'vue/valid-v-for': 'error',
      'vue/require-v-for-key': 'error',
      'vue/no-use-v-if-with-v-for': 'error',

      // ---- warn：历史遗留规模较大，先暴露不阻塞 ----
      // 巨型组件里存在大量历史未使用变量，逐批清理
      'no-unused-vars': ['warn', { args: 'none', caughtErrors: 'none', varsIgnorePattern: '^_' }],
      // 评分/质控代码里有若干「防御式初始化」（let x = null 后立即在各分支赋值），
      // 属风格问题、无运行风险；为避免为过 lint 改动医疗计算代码，降为 warn 观察。
      'no-useless-assignment': 'warn',
      'no-empty': ['warn', { allowEmptyCatch: true }],
      'no-useless-escape': 'warn',
      'no-irregular-whitespace': 'warn',
      'vue/no-unused-components': 'warn',
      'vue/no-unused-vars': 'warn',
      'vue/no-v-html': 'warn',

      // ---- off：纯风格偏好，交给 Prettier 或维持现状 ----
      'vue/multi-word-component-names': 'off',
      'vue/require-default-prop': 'off',
      'vue/attribute-hyphenation': 'off',
      'vue/v-on-event-hyphenation': 'off',
      'vue/html-self-closing': 'off',
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/html-indent': 'off',
      'vue/html-closing-bracket-newline': 'off',
      'vue/first-attribute-linebreak': 'off'
    }
  }
]
