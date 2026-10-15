<template>
  <div class="log-viewer">
    <div class="filter-bar">
      <div class="bar-title">
        <span class="title-icon">▤</span>
        <span>后端日志</span>
      </div>
      <el-select v-model="pageSize" class="size-select" @change="reloadLatest">
        <el-option label="50 行 / 页" :value="50" />
        <el-option label="200 行 / 页" :value="200" />
        <el-option label="500 行 / 页" :value="500" />
        <el-option label="2000 行 / 页" :value="2000" />
      </el-select>
      <el-button :loading="loading" @click="reloadLatest">
        <el-icon><Refresh /></el-icon>刷新
      </el-button>
      <span v-if="meta.file" class="file-meta" :title="meta.file">
        {{ meta.file }} · {{ fmtSize(meta.size) }} · 更新于 {{ meta.modified }}
      </span>
      <span class="hint">仅系统管理员可查看；日志含 SQL 与患者数据，请勿外传</span>
    </div>

    <el-alert v-if="loadError" :type="loadErrorType" :closable="false" show-icon :title="loadError" />

    <div v-else class="log-panel">
      <div ref="scrollEl" class="log-scroll">
        <div class="log-toolbar">
          <el-button
            size="small"
            :disabled="atHead || loading"
            :loading="loadingMore"
            @click="loadOlder"
          >
            加载更早…
          </el-button>
          <span v-if="atHead && lines.length" class="head-tip">已到日志开头</span>
          <span v-else-if="lines.length" class="head-tip">还有更早的日志，点「加载更早」继续</span>
        </div>
        <div v-if="!lines.length && !loading" class="log-empty">
          {{ emptyText }}
        </div>
        <div v-else class="log-lines">
          <div v-for="l in lines" :key="l.uid" class="log-line" :class="['lv-' + (levelOf(l) || 'OTHER')]">
            <span class="ln">{{ pad(l.no) }}</span>
            <span class="txt">{{ l.text }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { Refresh } from '@element-plus/icons-vue'
import request from '../api/request'

/**
 * 系统设置 → 日志查看：直接读后端 nohup 日志（logs/backend.log）的「最新一页」，
 * 支持向更早翻页（后端 64KB 分块反向读取，不整文件加载）。
 *
 * 权限：仅直连登录的管理员（外链/普通账号由后端 403）。
 * 数据模型：lines 时间正序（早→晚），最新在末尾；「加载更早」把上一页 unshift 到头部。
 */
export default {
  name: 'LogViewer',
  components: { Refresh },
  data() {
    return {
      loading: false,
      loadingMore: false,
      pageSize: 200,
      lines: [],
      meta: {},
      nextOffset: 0,
      atHead: false,
      loadError: '',
      loadErrorType: 'error',
      // 每行的跨页唯一键：后端每页的行号都从 1 重新开始，
      // 直接拿它当 v-for key，在「加载更早」把新页接到旧页前面时会撞键
      uidSeq: 0
    }
  },
  computed: {
    emptyText() {
      return this.loading ? '读取日志中…' : '日志为空'
    }
  },
  created() {
    // 列表是「早 → 晚」，首屏要停在最新的几行（与点「刷新」后的行为一致）
    this.loadLatest().then(() => this.scrollToBottom())
  },
  methods: {
    async loadLatest() {
      this.loading = true
      this.loadError = ''
      try {
        const res = await request.get('/system/logs/tail', { params: { lines: this.pageSize, offset: 0 } })
        this.applyPage(res, { prepend: false })
      } catch (e) {
        this.loadError = e.message || '日志读取失败'
        this.loadErrorType = /管理员|登录/.test(this.loadError) ? 'warning' : 'error'
      } finally {
        this.loading = false
      }
    },
    async reloadLatest() {
      this.lines = []
      await this.loadLatest()
      this.scrollToBottom()
    },
    async loadOlder() {
      if (this.atHead || !this.nextOffset) return
      this.loadingMore = true
      try {
        const res = await request.get('/system/logs/tail', { params: { lines: this.pageSize, offset: this.nextOffset } })
        this.applyPage(res, { prepend: true })
        this.scrollToTop()
      } catch (e) {
        this.loadError = e.message || '日志读取失败'
        this.loadErrorType = 'error'
      } finally {
        this.loadingMore = false
      }
    },
    applyPage(res, { prepend }) {
      if (!res || !res.lines) {
        this.loadError = '日志接口返回异常'
        this.loadErrorType = 'error'
        return
      }
      this.loadError = ''
      this.meta = { file: res.file, size: res.size, modified: res.modified }
      this.atHead = !!res.atHead
      this.nextOffset = res.nextOffset || 0
      // 逐行补一个跨页唯一的 uid 作为 v-for key（后端行号按页重新编号，不能直接当 key）
      const rows = res.lines.map((l) => ({ ...l, uid: ++this.uidSeq }))
      this.lines = prepend ? rows.concat(this.lines) : rows
    },
    levelOf(l) {
      return (l && l.level) || 'OTHER'
    },
    pad(no) {
      return String(no).padStart(4, '0')
    },
    fmtSize(bytes) {
      if (bytes == null) return '—'
      if (bytes < 1024) return bytes + ' B'
      if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
      return (bytes / 1024 / 1024).toFixed(1) + ' MB'
    },
    scrollToBottom() {
      this.$nextTick(() => {
        const el = this.$refs.scrollEl
        if (el) el.scrollTop = el.scrollHeight
      })
    },
    scrollToTop() {
      this.$nextTick(() => {
        const el = this.$refs.scrollEl
        if (el) el.scrollTop = 0
      })
    }
  }
}
</script>

<style scoped>
.log-viewer {
  padding: 16px 20px 24px;
  max-width: 1400px;
  margin: 0 auto;
}
.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  background: #fff;
  border: 1px solid #e6e2da;
  border-radius: 10px;
  padding: 10px 14px;
  margin-bottom: 12px;
}
.bar-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: #3d3a34;
}
.title-icon {
  color: #b3543a;
}
.size-select {
  width: 132px;
}
.file-meta {
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 12px;
  color: #8a857a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 480px;
}
.hint {
  font-size: 12px;
  color: #a09a8e;
  margin-left: auto;
}
.log-panel {
  background: #1e1e1e;
  border: 1px solid #333;
  border-radius: 10px;
  overflow: hidden;
}
.log-scroll {
  height: calc(100vh - 240px);
  min-height: 360px;
  overflow-y: auto;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 12.5px;
  line-height: 1.65;
}
.log-toolbar {
  padding: 8px 12px;
  border-bottom: 1px solid #2c2c2c;
  background: #262626;
  display: flex;
  align-items: center;
  gap: 10px;
}
.head-tip {
  color: #7a7a7a;
  font-size: 12px;
}
.log-lines {
  padding: 6px 0;
}
.log-line {
  display: flex;
  padding: 0 12px;
  white-space: pre-wrap;
  word-break: break-all;
  color: #d4d4d4;
}
.log-line:hover {
  background: rgba(255, 255, 255, 0.04);
}
.ln {
  flex: 0 0 56px;
  color: #666;
  user-select: none;
  padding-right: 12px;
  text-align: right;
}
.txt {
  flex: 1;
}
.log-line.lv-ERROR {
  color: #ff7b72;
}
.log-line.lv-ERROR .ln {
  color: #b3554f;
}
.log-line.lv-WARN {
  color: #e3b341;
}
.log-line.lv-INFO {
  color: #d4d4d4;
}
.log-line.lv-DEBUG,
.log-line.lv-TRACE {
  color: #9a9a9a;
}
.log-empty {
  color: #9a9a9a;
  padding: 48px 16px;
  text-align: center;
}
</style>
