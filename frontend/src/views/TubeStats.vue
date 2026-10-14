<template>
  <div class="tube-stats">
    <div class="filter-bar">
      <el-date-picker
        v-model="month"
        type="month"
        placeholder="选择月份"
        value-format="YYYY-MM"
        :clearable="false"
        popper-class="qb-popper"
        @change="load"
      />
      <span class="hint">置管/拔管/带出按事件发生月份统计；留置日未拔者算到患者出科时间</span>
    </div>

    <el-alert
      v-if="data.infectionNote"
      :title="data.infectionNote"
      type="info"
      :closable="false"
      style="margin-bottom: 12px"
    />

    <!-- 三管汇总：做成「一个模块三列」，而不是三张互不相干的浮动卡片 -->
    <section class="tube-summary" aria-label="三管事件汇总">
      <div class="summary-head">
        <h2>三管事件汇总</h2>
      </div>
      <div class="summary-grid">
        <article v-for="g in data.groups" :key="g.group" class="tube-col" :class="groupClass(g.group)">
          <div class="tube-head">
            <i class="tube-dot"></i>
            <span class="tube-name">{{ groupLabel(g.group) }}</span>
          </div>

          <!-- 主指标：本期置管强度。来源构成放进副标题，不另占一行数字 -->
          <div class="tube-primary">
            <b>{{ g.placed }}</b>
            <span>置管</span>
            <em>本科 {{ g.selfPlaced }} · 带入 {{ g.broughtIn }}</em>
          </div>

          <!-- 结局指标：次要层级，等宽数字保证跨列对齐 -->
          <dl class="tube-facts">
            <div><dt>拔管</dt><dd>{{ g.removed }}</dd></div>
            <div><dt>转出带出</dt><dd>{{ g.takenAway }}</dd></div>
            <div><dt>留置日合计</dt><dd>{{ g.totalDays }}</dd></div>
            <div><dt>48h再插管</dt><dd>{{ g.reintubation24h }}</dd></div>
          </dl>

          <!-- 风险指标单独成条：与中性数字在视觉上分开，命中时整条转红 -->
          <div class="tube-risk" :class="{ hit: g.unplannedRemoval > 0 }">
            <span>非计划拔管</span>
            <b>{{ g.unplannedRemoval }}</b>
          </div>
        </article>
      </div>
    </section>

    <!-- 明细 -->
    <el-table :data="data.rows" size="small" border stripe style="margin-top:16px">
      <el-table-column prop="tubeName" label="导管类型" min-width="140" />
      <el-table-column label="归类" width="90">
        <template #default="{ row }">
          <span class="g-tag" :class="groupClass(row.group)">{{ groupLabel(row.group) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="placed" label="置管" width="70" />
      <el-table-column prop="broughtIn" label="带入" width="70" />
      <el-table-column prop="selfPlaced" label="本科置管" width="80" />
      <el-table-column prop="removed" label="拔管" width="70" />
      <el-table-column prop="takenAway" label="转出带出" width="90" />
      <el-table-column label="非计划拔管" width="90">
        <template #default="{ row }">
          <span :style="{ color: row.unplannedRemoval > 0 ? '#dc2626' : 'inherit', fontWeight: row.unplannedRemoval > 0 ? 700 : 400 }">{{ row.unplannedRemoval }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="reintubation24h" label="48h再插管" width="90" />
      <el-table-column label="平均留置日" width="90">
        <template #default="{ row }">
          {{ row.avgDays == null ? '—' : row.avgDays + '天' }}
        </template>
      </el-table-column>
      <el-table-column label="留置覆盖率" width="100">
        <template #default="{ row }">
          <span :title="'有明确拔管/出科上界的比例'">{{ row.daysCoverage }}%</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import request from '../api/request'

const month = ref(new Date().toISOString().slice(0, 7))
const data = reactive({ groups: [], rows: [], infectionNote: '' })

function groupLabel(g) {
  return { CVC: '中心静脉导管(CVC)', 导尿管: '导尿管', 气道导管: '气道导管(插管/气切)', 其他: '其他导管' }[g] || g
}
function groupClass(g) {
  return { CVC: 'cvc', 导尿管: 'urine', 气道导管: 'airway' }[g] || ''
}

async function load() {
  try {
    const res = await request.get('/tube/monthly', { params: { yearMonth: month.value } })
    // 兼容两种信封：统一 request 已解包（直接是业务对象），但若上层改动或
    // 换用裸 axios 会拿到 {code,message,data}。整包 Object.assign 的后果是
    // data.groups 永远为空 —— 页面「暂无数据」而接口明明返回了 4 组 30 行。
    Object.assign(data, res && res.groups ? res : (res && res.data) || {})
  } catch (e) {
    console.error(e)
  }
}
onMounted(load)
</script>

<style scoped>
.tube-stats { padding: 16px; }
.filter-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 14px; }
.hint { color: #78716c; font-size: 12px; }
/* 三管汇总：一个模块三列，发丝线分隔。
   旧版是三张浅灰浮卡 + 3px 彩色顶边 —— 那是后台模板套出来的观感，而且八项
   数字等权平铺，看不出哪个才是要盯的。现在改为：统一白底模块、色点做分组
   标识、30px 主指标（置管）立第一层级、次级事实降为 15px、风险项单独成条
   并与中性数字在视觉上分离（命中时整条转红）。 */
/* 列分隔不用 border-left，而是「容器铺发丝色 + 1px 间距 + 每列白底」。
   原因：后端返回的导管组是 3~4 个（含「其他导管」）—— 线上实测就是 4 组。
   用 border-left 时第 4 列换到第二行仍会带一条多余的竖线；用 1px gap 则是
   「成对生成」分隔线，一行几列、是否换行都自动正确。列数交给 auto-fit，
   宽屏一行排满、窄屏自动 2 列 / 1 列，不需要额外写断点。 */
.tube-summary { overflow: hidden; border: 1px solid #e7e5e4; border-radius: 12px; background: #fff; box-shadow: 0 1px 2px rgba(28, 25, 23, .04); }
.summary-head { display: flex; align-items: baseline; gap: 10px; padding: 13px 18px 11px; border-bottom: 1px solid #f0efee; }
.summary-head h2 { margin: 0; font-size: 14px; font-weight: 700; color: #1c1917; letter-spacing: .2px; }
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 1px; background: #f0efee; }
.tube-col { position: relative; padding: 14px 18px 15px; background: #fff; }
.tube-head { display: flex; align-items: center; gap: 7px; }
.tube-dot { width: 7px; height: 7px; border-radius: 50%; background: #78716c; }
.tube-col.cvc .tube-dot { background: #2563eb; }
.tube-col.urine .tube-dot { background: #059669; }
.tube-col.airway .tube-dot { background: #dc2626; }
.tube-name { color: #44403c; font-size: 13px; font-weight: 600; }
.tube-primary { display: flex; align-items: baseline; gap: 7px; margin: 11px 0 0; }
.tube-primary b { color: #1c1917; font-size: 30px; font-weight: 700; line-height: 1; font-variant-numeric: tabular-nums; }
.tube-primary span { color: #78716c; font-size: 12px; }
.tube-primary em { color: #a8a29e; font-size: 11px; font-style: normal; }
.tube-facts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 7px 14px; margin: 12px 0 0; padding: 11px 0 0; border-top: 1px solid #f5f5f4; }
.tube-facts > div { display: flex; align-items: baseline; justify-content: space-between; gap: 6px; }
.tube-facts dt { color: #78716c; font-size: 11px; }
.tube-facts dd { margin: 0; color: #292524; font-size: 15px; font-weight: 600; font-variant-numeric: tabular-nums; }
.tube-risk { display: flex; align-items: center; justify-content: space-between; margin-top: 10px; padding: 6px 9px; border-radius: 7px; background: #fafaf9; }
.tube-risk span { color: #78716c; font-size: 11px; }
.tube-risk b { color: #57534e; font-size: 15px; font-weight: 700; font-variant-numeric: tabular-nums; }
.tube-risk.hit { background: #fef2f2; }
.tube-risk.hit span { color: #b91c1c; }
.tube-risk.hit b { color: #dc2626; }
.g-tag { font-size: 11px; padding: 1px 6px; border-radius: 4px; background: #f1f5f9; color: #475569; }
/* 列数由 auto-fit 自适应（minmax 210px），窄屏自动降到 2 列 / 1 列，无需断点 */
</style>
