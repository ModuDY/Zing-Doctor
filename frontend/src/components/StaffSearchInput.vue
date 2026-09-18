<template>
  <div class="ss-wrap" ref="wrapRef">
    <input
      class="ss-ipt"
      autocomplete="off"
      :value="modelValue"
      :placeholder="placeholder"
      :title="tipText"
      @input="onInput"
      @focus="onFocus"
      @blur="close"
      @keydown.down.prevent="move(1)"
      @keydown.up.prevent="move(-1)"
      @keydown.enter.prevent="pick(activeIndex)"
      @keydown.esc.stop="close"
    />
    <span v-if="picked || workNo" class="ss-badge">职工库 ✓</span>
    <div v-if="open" class="ss-pop" :style="popStyle">
      <template v-if="options.length">
        <div
          v-for="(s, i) in options"
          :key="(s.id || s.workNo || i) + '_' + i"
          class="ss-opt"
          :class="{ on: i === activeIndex }"
          @mousedown.prevent="pick(i)"
          @mouseenter="activeIndex = i">
          <b>{{ s.name }}</b>
          <span class="ss-meta">{{ [s.workNo, s.depart, s.pinyin].filter(Boolean).join(' · ') }}</span>
        </div>
      </template>
      <div v-else class="ss-empty">{{ loading ? '检索中…' : '未匹配到职工，可直接手工输入姓名' }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onBeforeUnmount, nextTick } from 'vue'
import { searchStaff } from '../api/staff'

/**
 * 职工检索输入：输入即搜（姓名 / 拼音首字母 / 工号），点选即填入姓名 + 工号。
 *
 * 刻意保留自由输入：外院会诊、进修人员不一定在 ICU 职工库里，签名字段不能被检索卡死。
 * 从列表选中后右上方显示「职工库 ✓」，一眼区分「库里选的」与「手敲的」。
 *
 * 工号（v-model:work-no）是文书电子签名图的主键：签名图只认工号，姓名重名 / 改过名
 * 就可能取错人的章。因此手工改动姓名时工号必须立即作废（否则文书盖的是上一任的签名），
 * 而只填姓名、没有工号是允许的 —— 此时文书自动退化为打印姓名。
 *
 * 回填历史记录时用 v-model:work-no 把库里的工号传回来：不改动姓名就不会丢，
 * 徽标也照常亮着，看得出这条签名来自职工库。
 *
 * 下拉面板用 position: fixed + 动态定位，避免被外层容器的 overflow 裁剪。
 */
const props = defineProps({
  modelValue: { type: String, default: '' },
  /** 已落库的工号（可能来自历史记录回填，而非本次点选） */
  workNo: { type: String, default: '' },
  placeholder: { type: String, default: '输入姓名 / 拼音首字母 / 工号检索' }
})
const emit = defineEmits(['update:modelValue', 'update:workNo', 'select'])

const wrapRef = ref(null)
const open = ref(false)
const loading = ref(false)
const options = ref([])
const activeIndex = ref(0)
/** 当前值是否来自职工库选中（用于显示徽标）；历史记录回填的姓名不显示 */
const picked = ref(null)
/** null = 从未检索过，聚焦时先拉一批；'' = 已检索过全量 */
let lastQuery = null
let timer = null

/** 下拉面板的 fixed 定位样式（top / left / width），打开时动态计算 */
const popStyle = ref({})

function updatePopPosition() {
  if (!wrapRef.value) return
  const rect = wrapRef.value.getBoundingClientRect()
  popStyle.value = {
    top: rect.bottom + 4 + 'px',
    left: rect.left + 'px',
    width: rect.width + 'px'
  }
}

/** 悬停提示：本次点选的（含科室）优先，其次用回填的工号 —— 让人能核对该签名对应哪个工号 */
const tipText = computed(() => {
  if (picked.value) {
    return picked.value.name + '（工号 ' + picked.value.workNo
      + (picked.value.depart ? ' · ' + picked.value.depart : '') + '）'
  }
  return props.workNo ? props.modelValue + '（工号 ' + props.workNo + '）' : ''
})

function onInput(e) {
  emit('update:modelValue', e.target.value)
  // 姓名被手改，旧工号立刻作废：留着会让文书盖上「同名不同人」的签名图
  emit('update:workNo', '')
  picked.value = null
  emit('select', null)
  open.value = true
  activeIndex.value = 0
  nextTick(updatePopPosition)
  schedule(e.target.value)
}

function onFocus() {
  open.value = true
  nextTick(updatePopPosition)
  if (lastQuery === null) schedule(props.modelValue || '')
}

function schedule(kw) {
  clearTimeout(timer)
  timer = setTimeout(() => run(kw), 220)
}

async function run(kw) {
  lastQuery = kw
  loading.value = true
  try {
    const list = await searchStaff(kw)
    options.value = (list || [])
      .map(s => ({
        id: s.user_id,
        name: s.realname || '',
        workNo: s.work_no || '',
        pinyin: s.pinyin || '',
        depart: s.depart_name || ''
      }))
      .filter(s => s.name)
  } catch (e) {
    options.value = []
  } finally {
    loading.value = false
    nextTick(updatePopPosition)
  }
}

function move(d) {
  if (!options.value.length) return
  const n = options.value.length
  activeIndex.value = (activeIndex.value + d + n) % n
}

function pick(i) {
  const s = options.value[i]
  if (!s) return
  emit('update:modelValue', s.name)
  emit('update:workNo', s.workNo || '')
  emit('select', s)
  picked.value = s
  close()
}

function close() {
  open.value = false
}

/** 窗口滚动 / 尺寸变化时重新定位下拉面板 */
function onScrollOrResize() {
  if (open.value) updatePopPosition()
}
if (typeof window !== 'undefined') {
  window.addEventListener('scroll', onScrollOrResize, true)
  window.addEventListener('resize', onScrollOrResize)
}

onBeforeUnmount(() => {
  clearTimeout(timer)
  if (typeof window !== 'undefined') {
    window.removeEventListener('scroll', onScrollOrResize, true)
    window.removeEventListener('resize', onScrollOrResize)
  }
})
</script>

<style scoped>
.ss-wrap { position: relative; width: 100%; }
.ss-ipt {
  width: 100%; height: 34px; padding: 6px 66px 6px 10px; box-sizing: border-box;
  border: 1px solid var(--el-border-color); border-radius: var(--ards-ctl-radius, 6px);
  background: #fff; font: 13px/1.5 inherit; color: var(--el-text-color-primary);
  outline: none; transition: border-color .15s ease, box-shadow .15s ease;
}
.ss-ipt:focus { border-color: var(--el-color-primary); box-shadow: 0 0 0 3px var(--el-color-primary-light-8); }
.ss-badge {
  position: absolute; right: 8px; top: 50%; transform: translateY(-50%);
  font-size: 11px; color: var(--el-color-success); pointer-events: none; white-space: nowrap;
}
.ss-pop {
  position: fixed; z-index: 3000;
  max-height: 240px; overflow: auto; background: #fff; padding: 4px;
  border: 1px solid var(--el-border-color-light); border-radius: 6px;
  box-shadow: 0 6px 16px rgba(0, 0, 0, .12);
}
.ss-opt {
  display: flex; justify-content: space-between; align-items: center; gap: 10px;
  padding: 6px 8px; border-radius: 4px; cursor: pointer; font-size: 13px;
}
.ss-opt.on { background: var(--el-color-primary-light-9); }
.ss-meta { color: var(--el-text-color-secondary); font-size: 12px; }
.ss-empty { padding: 8px; font-size: 12px; color: var(--el-text-color-secondary); text-align: center; }
</style>
