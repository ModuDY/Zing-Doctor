<template>
  <div class="stat-card" :class="[toneClass, { 'is-pending': isPending, 'is-unknown': isUnknown }]">
    <div class="card-label">{{ label }}</div>

    <!-- 待接数据源：明确说没接，而不是显示一个假的 0 -->
    <div v-if="isPending" class="card-value pending">
      <span class="pending-icon">○</span>
      <span class="pending-text">待接数据</span>
    </div>
    <!-- 查询失败：与「没有数据」严格区分 -->
    <div v-else-if="isUnknown" class="card-value unknown">暂不可用</div>
    <!-- 无数据（EMPTY）：区间内确实没有 -->
    <div v-else-if="isEmpty" class="card-value empty">暂无</div>
    <div v-else class="card-value">
      <span class="num">{{ displayValue }}</span>
      <span v-if="unit" class="unit">{{ unit }}</span>
    </div>

    <div v-if="sub" class="card-sub" :class="{ warn: subWarn }">{{ sub }}</div>
    <div v-if="note" class="card-note">{{ note }}</div>
  </div>
</template>

<script>
/**
 * 驾驶舱指标卡。
 *
 * 四种状态严格分开，这是本组件的核心：PENDING_SOURCE（数据源没接）/
 * UNKNOWN（查询失败）/ EMPTY（区间内确实没有）/ OK（有值）。
 * 把前三者显示成 0 是最容易犯的错 —— 医生会以为"今天很安全"。
 */
export default {
  name: 'StatCard',
  props: {
    label: { type: String, required: true },
    /** 后端返回的区块对象 { value, status, note } */
    block: { type: Object, default: null },
    unit: { type: String, default: '' },
    /** 直接展示的文本（用于「待接数据」这类） */
    valueText: { type: String, default: '' },
    sub: { type: String, default: '' },
    subWarn: { type: Boolean, default: false },
    /** up / down 仅用于今日入出科的色彩暗示 */
    tone: { type: String, default: '' }
  },
  computed: {
    status() {
      return (this.block && this.block.status) || ''
    },
    isPending() {
      return this.status === 'PENDING_SOURCE'
    },
    isUnknown() {
      return this.status === 'UNKNOWN'
    },
    isEmpty() {
      return this.status === 'EMPTY'
    },
    note() {
      return this.block && this.block.note ? this.block.note : ''
    },
    displayValue() {
      if (this.valueText) return this.valueText
      const v = this.block && this.block.value
      return v == null ? '—' : v
    },
    toneClass() {
      return this.tone ? `tone-${this.tone}` : ''
    }
  }
}
</script>

<style scoped>
.stat-card {
  background: #fff;
  border: 1px solid #f0efec;
  border-radius: 10px;
  padding: 12px 14px;
}
.stat-card.is-pending {
  background: #fafaf9;
  border-style: dashed;
}
.card-label {
  font-size: 11px;
  color: #78716c;
}
.card-value {
  margin-top: 6px;
  display: flex;
  align-items: baseline;
  gap: 3px;
}
.num {
  font-size: 24px;
  font-weight: 700;
  color: #1c1917;
  font-variant-numeric: tabular-nums;
}
.unit {
  font-size: 11px;
  color: #a8a29e;
}
.card-value.pending .pending-icon {
  font-size: 18px;
  color: #d6d3d1;
}
.card-value.pending .pending-text {
  font-size: 13px;
  color: #a8a29e;
  margin-left: 4px;
}
.card-value.unknown {
  font-size: 13px;
  color: #b91c1c;
}
.card-value.empty {
  font-size: 13px;
  color: #a8a29e;
}
.tone-up .num {
  color: #b45309;
}
.tone-down .num {
  color: #0e7490;
}
.card-sub {
  margin-top: 4px;
  font-size: 11px;
  color: #a8a29e;
}
.card-sub.warn {
  color: #b45309;
}
.card-note {
  margin-top: 4px;
  font-size: 10px;
  color: #a8a29e;
  line-height: 1.5;
}
</style>
