<template>
  <div class="sofa-page">
    <div class="app">
      <!-- ============ 左侧：评分记录 ============ -->
      <aside class="sidebar">
        <div class="side-title">评分记录 <span class="count">{{ sortedRecords.length }}</span></div>
        <button class="btn-add" @click="addRecord">＋ 新增评分</button>
        <div class="record-list">
          <div v-for="r in sortedRecords" :key="r.id"
               :class="['rec-card', { active: currentRecordId === r.id }]"
               @click="selectRecord(r)">
            <div class="rec-row">
              <span class="rec-time">{{ fmtTime(r.scoreTime) }}</span>
              <span :class="['score-badge', scoreBadgeClass(r.totalScore)]">{{ r.totalScore }}</span>
            </div>
            <div class="tag-row">
              <span class="tag gray">{{ r.createBy || '—' }}</span>
              <span :class="['tag', recTagClass(r)]">{{ scoreTypeLabel(r) }}</span>
              <span v-if="r.hasPdf === 1" class="tag orange" @click.stop="viewPdf(r)">PDF文书</span>
              <span v-if="isAutoRecord(r)" class="tag blue" @click.stop="reviewRecord(r)">复核</span>
              <span class="tag gray" @click.stop="removeRecord(r)">删除</span>
            </div>
          </div>
          <div v-if="!sortedRecords.length" class="record-empty">暂无评分记录</div>
        </div>
      </aside>

      <!-- ============ 右侧主区 ============ -->
      <main class="main">
    <!-- 患者信息行（外链访问时外层已展示，故隐藏） -->
    <div class="patient-row" v-if="!isExternal">
      <span class="bed-tag">{{ patient.bedCode || '—' }}</span>
      <span class="patient-name">{{ patient.name || '—' }}</span>
      <span class="patient-meta"><b>{{ patient.gender || '—' }}</b> / {{ patient.age || '—' }}{{ patient.ageUnit || '岁' }}</span>
      <span class="patient-meta">住院号：<b>{{ patient.inHospitalNo || inHospitalNo || '—' }}</b></span>
      <span class="patient-meta">入科时间：<b>{{ fmtTime(patient.inDepartTime) }}</b></span>
      <span :class="['resp-flag', { on: respiratorySupport === 1 }]">{{ respiratorySupport === 1 ? '有呼吸支持' : '无呼吸支持' }}</span>
      <button class="btn-trend" @click="openTotalTrend">评分历史趋势</button>
    </div>
      <!-- ===== 总览条：SOFA 总分 + 6 器官当前分值 ===== -->
      <div class="overview">
        <div class="ov-total">
          <div class="t-label">SOFA 总分</div>
          <div class="t-num">{{ totalScore }}</div>
          <div class="t-pill">6 项合计 (0~24)</div>
          <div class="t-delta" v-if="deltaSofa !== null && deltaSofa !== undefined">
            较上次 <b>{{ deltaSofa > 0 ? '+' : '' }}{{ deltaSofa }}</b>（上次 {{ lastScore }} 分）
          </div>
          <div class="t-foot">体重 {{ weightUsed || '—' }} kg</div>
        </div>
        <div class="ov-organs">
          <div v-for="it in items" :key="it.key" :class="['ov-card', { overridden: isOverridden(it) }]">
            <div class="ov-line">
              <span :class="['ov-box', boxClass(scoreOf(it))]">{{ scoreOf(it) }}</span>
              <span class="ov-name">{{ it.label }}</span>
              <button class="ov-src" @click="openSource(it)" title="查看数据来源">来源</button>
            </div>
            <div class="ov-line">
              <span class="ov-plain" :title="it.valueText || ''">{{ it.valueText || '未取到' }}</span>
              <select class="ov-sel" v-model.number="scoreOverrides[it.key]" @change="recalcTotal"
                      :title="isOverridden(it) ? '已手工修正（自动 ' + (it.score || 0) + ' 分）' : '手工修正分值'">
                <option v-for="n in SCORE_OPTIONS" :key="n" :value="n">{{ n }}</option>
              </select>
            </div>
            <div class="ov-sub">
              <span :class="['ov-chip', { missing: it.missing }]">{{ it.missing ? '未取到' : (it.rangeText || '—') }}</span>
              <span class="ov-time" v-if="it.dataTime">{{ fmtTime(it.dataTime) }}</span>
              <button v-if="isOverridden(it)" class="ov-reset" @click="resetOverride(it.key)">恢复</button>
            </div>
          </div>
        </div>
      </div>

      <!-- ===== 取数时间范围 ===== -->
      <div class="range-row">
        <span class="lbl">取数时间范围：</span>
        <input class="sel" type="datetime-local" v-model="rangeStart" @change="onRangeInput">
        <span class="tilde">至</span>
        <input class="sel" type="datetime-local" v-model="rangeEnd" @change="onRangeInput">
        <button :class="['rbtn', { active: activeRange === 24 }]" @click="quickRange(24)">24小时</button>
        <button :class="['rbtn', { active: activeRange === 48 }]" @click="quickRange(48)">48小时</button>
        <button :class="['rbtn', { active: activeRange === 72 }]" @click="quickRange(72)">72小时</button>
        <button class="rbtn solid" @click="loadAssessment">自动获取并计算</button>
        <span class="range-logic">取数逻辑：范围内最差值（偏离正常最远）</span>
      </div>

      <!-- ===== 器官功能评分表 ===== -->
      <div class="table-panel">
        <div class="table-title">器官功能评分表</div>
        <table class="score">
          <colgroup>
            <col style="width:9%"><col style="width:16%"><col style="width:15%"><col style="width:9%">
            <col style="width:10.2%"><col style="width:10.2%"><col style="width:10.2%"><col style="width:10.2%"><col style="width:10.2%">
          </colgroup>
          <thead>
            <tr><th>器官系统</th><th>评估指标</th><th>输入值</th><th>近五次</th>
              <th>0</th><th>1</th><th>2</th><th>3</th><th>4</th></tr>
          </thead>
          <tbody>
            <!-- 呼吸 -->
            <tr>
              <td class="sys" rowspan="2">呼吸系统</td>
              <td class="ind">呼吸机支持</td>
              <td><div class="inp drop">{{ respiratorySupport === 1 ? '是' : '否' }}<span class="caret">▾</span></div></td>
              <td class="near" v-html="nearFive('resp')"></td>
              <td class="dim">—</td><td class="dim">—</td><td class="dim">—</td><td></td><td></td>
            </tr>
            <tr>
              <td class="ind">PaO₂/F.iO₂ (mmHg)</td>
              <td><div class="inp">{{ num(rawOf('resp').oxygenationIndex, 1) }}</div></td>
              <td class="near" v-html="nearFive('resp')"></td>
              <td :class="cellCls(hitCol('resp', 0))">≥400</td>
              <td :class="cellCls(hitCol('resp', 1))">&lt;400</td>
              <td :class="cellCls(hitCol('resp', 2))">&lt;300</td>
              <td :class="cellCls(hitCol('resp', 3))">&lt;200</td>
              <td :class="cellCls(hitCol('resp', 4))">&lt;100</td>
            </tr>
            <!-- 凝血 -->
            <tr>
              <td class="sys">血液系统</td>
              <td class="ind">血小板 (10⁹/L)</td>
              <td><div class="inp">{{ num(rawOf('coag').platelet, 0) }}</div></td>
              <td class="near" v-html="nearFive('coag')"></td>
              <td :class="cellCls(hitCol('coag', 0))">≥150</td>
              <td :class="cellCls(hitCol('coag', 1))">&lt;150</td>
              <td :class="cellCls(hitCol('coag', 2))">&lt;100</td>
              <td :class="cellCls(hitCol('coag', 3))">&lt;50</td>
              <td :class="cellCls(hitCol('coag', 4))">&lt;20</td>
            </tr>
            <!-- 肝 -->
            <tr>
              <td class="sys">肝脏</td>
              <td class="ind">胆红素 (μmol/L)</td>
              <td><div class="inp">{{ num(rawOf('liver').totalBilirubinUmol, 1) }}</div></td>
              <td class="near" v-html="nearFive('liver')"></td>
              <td :class="cellCls(hitCol('liver', 0))">&lt;20.5</td>
              <td :class="cellCls(hitCol('liver', 1))">≤34.1</td>
              <td :class="cellCls(hitCol('liver', 2))">≤102.5</td>
              <td :class="cellCls(hitCol('liver', 3))">≤205.1</td>
              <td :class="cellCls(hitCol('liver', 4))">&gt;205.1</td>
            </tr>
            <!-- 循环 -->
            <tr>
              <td class="sys" rowspan="5">循环系统</td>
              <td class="ind">平均动脉压 (mmHg)</td>
              <td><div class="inp">{{ num(rawOf('cardio').map, 0) }}</div></td>
              <td class="near" v-html="nearFive('cardio')"></td>
              <td :class="cellCls(hitRaw('cardio', 'mapScore', 0))">≥70</td>
              <td :class="cellCls(hitRaw('cardio', 'mapScore', 1))">&lt;70</td>
              <td class="dim">—</td><td class="dim">—</td><td class="dim">—</td>
            </tr>
            <tr>
              <td class="ind">多巴胺 (μg·kg⁻¹·min⁻¹)</td>
              <td><div class="inp">{{ num(vasoOf('多巴胺'), 3) }}</div></td>
              <td class="near">—</td>
              <td class="dim">—</td><td class="dim">—</td>
              <td :class="cellCls(vasoHit('多巴胺', 2))">≤5</td>
              <td :class="cellCls(vasoHit('多巴胺', 3))">5~15</td>
              <td :class="cellCls(vasoHit('多巴胺', 4))">&gt;15</td>
            </tr>
            <tr>
              <td class="ind">肾上腺素 (μg·kg⁻¹·min⁻¹)</td>
              <td><div class="inp">{{ num(vasoOf('肾上腺素'), 3) }}</div></td>
              <td class="near">—</td>
              <td class="dim">—</td><td class="dim">—</td><td class="dim">—</td>
              <td :class="cellCls(vasoHit('肾上腺素', 3))">≤0.1</td>
              <td :class="cellCls(vasoHit('肾上腺素', 4))">&gt;0.1</td>
            </tr>
            <tr>
              <td class="ind">去甲肾上腺素 (μg·kg⁻¹·min⁻¹)</td>
              <td><div class="inp">{{ num(vasoOf('去甲肾上腺素'), 3) }}</div></td>
              <td class="near">—</td>
              <td class="dim">—</td><td class="dim">—</td><td class="dim">—</td>
              <td :class="cellCls(vasoHit('去甲肾上腺素', 3))">≤0.1</td>
              <td :class="cellCls(vasoHit('去甲肾上腺素', 4))">&gt;0.1</td>
            </tr>
            <tr>
              <td class="ind">多巴酚丁胺 (μg·kg⁻¹·min⁻¹)</td>
              <td><div class="inp">{{ num(vasoOf('多巴酚丁胺'), 3) }}</div></td>
              <td class="near">—</td>
              <td class="dim">—</td>
              <td :class="cellCls(vasoHit('多巴酚丁胺', 2))">任何剂量</td>
              <td class="dim">—</td><td class="dim">—</td>
            </tr>
            <!-- 神经 -->
            <tr>
              <td class="sys">神经系统</td>
              <td class="ind">GCS 评分</td>
              <td><div class="inp">{{ gcsInputText }}</div></td>
              <td class="near" v-html="nearFive('neuro')"></td>
              <td :class="cellCls(hitCol('neuro', 0))">15</td>
              <td :class="cellCls(hitCol('neuro', 1))">13~14</td>
              <td :class="cellCls(hitCol('neuro', 2))">10~12</td>
              <td :class="cellCls(hitCol('neuro', 3))">6~9</td>
              <td :class="cellCls(hitCol('neuro', 4))">&lt;6</td>
            </tr>
            <!-- 肾 -->
            <tr>
              <td class="sys" rowspan="2">肾脏</td>
              <td class="ind">肌酐 (μmol/L)</td>
              <td><div class="inp">{{ num(rawOf('renal').creatinineUmol, 1) }}</div></td>
              <td class="near" v-html="nearFive('renal')"></td>
              <td :class="cellCls(hitRaw('renal', 'creatinineScore', 0))">&lt;106</td>
              <td :class="cellCls(hitRaw('renal', 'creatinineScore', 1))">≤176</td>
              <td :class="cellCls(hitRaw('renal', 'creatinineScore', 2))">≤308</td>
              <td :class="cellCls(hitRaw('renal', 'creatinineScore', 3))">≤442</td>
              <td :class="cellCls(hitRaw('renal', 'creatinineScore', 4))">&gt;442</td>
            </tr>
            <tr>
              <td class="ind">24h 尿量 (ml)</td>
              <td><div class="inp">{{ urineInputText }}</div></td>
              <td class="near">—</td>
              <td class="dim">—</td><td class="dim">—</td><td class="dim">—</td>
              <td :class="cellCls(hitRaw('renal', 'urineScore', 3))">≤500</td>
              <td :class="cellCls(hitRaw('renal', 'urineScore', 4))">≤200</td>
            </tr>
          </tbody>
        </table>

        <!-- 提示 -->
        <div class="tip" v-if="remark">
          <span class="ico">!</span>
          <span><b>提示：</b>{{ remark }}</span>
        </div>
        <div class="tip" v-if="reviewingId" style="background:#EAF1FC;border-color:#C9DCF6;color:#2560C8;">
          <span class="ico" style="background:#4485DB">i</span>
          <span><b>复核中：</b>已载入 {{ fmtTime(reviewingScoreTime) }} 的自动初评（记录 #{{ reviewingId }}），
            保存后将覆盖该条记录并标记「已复核」。
            <button class="btn btn-text" @click="cancelReview">取消复核</button>
          </span>
        </div>
      </div>

      <!-- ===== 底部操作行 ===== -->
      <div class="foot">
        <div class="foot-info">
          评分医师：{{ realname || username || '—' }}<br>
          创建时间：{{ fmtTimeNow() }}
        </div>
        <input class="foot-note" v-model="doctorRemark" placeholder="备注（可选）" />
        <button class="fbtn ghost" @click="loadAssessment">重置</button>
        <button class="fbtn ghost" :disabled="saving || pdfGenerating" @click="saveWithPdf">
          {{ pdfGenerating ? '生成文书…' : '保存并生成文书' }}
        </button>
        <button class="fbtn solid" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : (reviewingId ? '保存复核' : '保存评分') }}
        </button>
      </div>
      </main>
    </div>

    <!-- 来源弹窗 -->
    <div class="modal-mask" v-if="showSource" @click.self="showSource = false">
      <div class="modal">
        <div class="modal-head">
          <h3>{{ sourceItem ? sourceItem.label : '' }} - 数据来源</h3>
          <button class="modal-close" @click="showSource = false">×</button>
        </div>
        <div class="modal-body">
          <div class="src-grid">
            <div class="src-card">
              <div class="src-label">当前值</div>
              <div class="src-value">{{ sourceItem && sourceItem.valueText || '—' }}</div>
            </div>
            <div class="src-card">
              <div class="src-label">命中区间</div>
              <div class="src-value">{{ sourceItem && sourceItem.rangeText || '—' }}</div>
            </div>
            <div class="src-card">
              <div class="src-label">得分</div>
              <div class="src-value">{{ sourceItem && sourceItem.score || 0 }} 分</div>
            </div>
          </div>
          <div class="src-section"><b>取值时间：</b>{{ sourceItem && sourceItem.dataTime ? fmtTime(sourceItem.dataTime) : '—' }}</div>
          <div class="src-section" v-if="sourceItem && sourceItem.note"><b>提示：</b>{{ sourceItem.note }}</div>
          <div class="src-title">数据趋势（当前取数范围）</div>
          <div ref="trendChartRef" style="width:100%;height:200px;margin-bottom:12px;"></div>
          <div class="src-title">原始数据（落库 JSON）</div>
          <pre class="src-json">{{ prettyJson(sourceItem && sourceItem.rawJson) }}</pre>
          <div class="src-title">取数说明</div>
          <div class="src-desc">
            取数窗口内<b>最差值</b>：呼吸/凝血/MAP 取最低，肝/肌酐取最高；循环（MAP 与血管活性药）与肾（肌酐与尿量）<b>二选一取高分</b>。
            血管活性药剂量 = （药物总量 ÷ 总液量）× 泵速 ÷ 60 ÷ 体重，单位 µg/kg/min。
          </div>
        </div>
        <div class="modal-foot">
          <button class="btn btn-primary" @click="showSource = false">确认</button>
        </div>
      </div>
    </div>

    <!-- 总分趋势弹窗 -->
    <div class="modal-mask" v-if="showTrend" @click.self="closeTotalTrend">
      <div class="modal">
        <div class="modal-head">
          <h3>SOFA 评分历史趋势</h3>
          <button class="modal-close" @click="closeTotalTrend">×</button>
        </div>
        <div class="modal-body">
          <div ref="totalTrendRef" style="width:100%;height:280px;"></div>
        </div>
      </div>
    </div>

    <!-- 离屏文书渲染源：移出视口但保留真实尺寸，供 html2canvas 生成 PDF -->
    <teleport to="body">
      <div class="report-offscreen" aria-hidden="true">
        <div ref="reportRef" class="report-page"
             style="width:794px;background:#ffffff;padding:24px 32px 28px;color:#000000;font-family:'SimSun','宋体',serif;font-size:12px;line-height:1.5;box-sizing:border-box;">
          <!-- 医院抬头：logo + 三行院名 -->
          <div style="display:flex;align-items:center;justify-content:center;margin-bottom:10px;">
            <img :src="HOSPITAL_LOGO" style="width:60px;height:60px;margin-right:16px;" />
            <div style="text-align:center;">
              <div style="font-family:'SimHei','黑体',sans-serif;font-size:20px;font-weight:700;letter-spacing:2px;">福州市第二总医院</div>
              <div style="font-family:'SimHei','黑体',sans-serif;font-size:18px;font-weight:700;letter-spacing:2px;">福州市第二医院</div>
              <div style="font-family:'SimHei','黑体',sans-serif;font-size:16px;font-weight:700;letter-spacing:2px;">福建省福州中西医结合医院</div>
            </div>
          </div>
          <!-- 大标题 -->
          <div style="text-align:center;font-family:'SimHei','黑体',sans-serif;font-size:22px;font-weight:700;letter-spacing:2px;margin-bottom:12px;">序贯器官衰竭评分（SOFA评分）</div>
          <!-- 患者信息行 -->
          <div style="display:flex;flex-wrap:wrap;font-size:12px;line-height:2;margin-bottom:6px;">
            <span style="margin-right:14px;">姓名：{{ patient.name || '—' }}</span>
            <span style="margin-right:14px;">性别：{{ patient.gender || '—' }}</span>
            <span style="margin-right:14px;">年龄：{{ patient.age || '—' }}{{ patient.ageUnit || '岁' }}</span>
            <span style="margin-right:14px;">科别：{{ patient.departName || '—' }}</span>
            <span style="margin-right:14px;">床号：{{ patient.bedCode || '—' }}</span>
            <span>住院号：{{ patient.inHospitalNo || inHospitalNo || '—' }}</span>
          </div>
          <!-- 8列评分表：系统 | 检测项目 | 0分 | 1分 | 2分 | 3分 | 4分 | 得分 -->
          <table style="width:100%;border-collapse:collapse;font-size:11px;">
            <thead>
              <tr style="background:#f5f5f5;">
                <th style="border:1px solid #000;padding:4px;width:48px;">系统</th>
                <th style="border:1px solid #000;padding:4px;width:165px;">检测项目</th>
                <th style="border:1px solid #000;padding:4px;width:52px;">0分</th>
                <th style="border:1px solid #000;padding:4px;width:52px;">1分</th>
                <th style="border:1px solid #000;padding:4px;width:52px;">2分</th>
                <th style="border:1px solid #000;padding:4px;width:52px;">3分</th>
                <th style="border:1px solid #000;padding:4px;width:52px;">4分</th>
                <th style="border:1px solid #000;padding:4px;width:44px;">得分</th>
              </tr>
            </thead>
            <tbody>
              <!-- 呼吸 2行 -->
              <tr>
                <td rowspan="2" style="border:1px solid #000;padding:4px;text-align:center;">呼吸</td>
                <td style="border:1px solid #000;padding:4px;">PaO2/FiO2(mmHg)</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">≥400</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">&lt;400</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">&lt;300</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">&lt;200</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">&lt;100</td>
                <td rowspan="2" style="border:1px solid #000;padding:4px;text-align:center;font-weight:700;font-size:14px;">{{ scoreOf(items[0]) }}</td>
              </tr>
              <tr>
                <td style="border:1px solid #000;padding:4px;">呼吸支持(是/否)</td>
                <td style="border:1px solid #000;padding:4px;"></td>
                <td style="border:1px solid #000;padding:4px;"></td>
                <td style="border:1px solid #000;padding:4px;"></td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">{{ respiratorySupport === 1 ? '是' : '' }}</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">{{ respiratorySupport === 1 ? '是' : '' }}</td>
              </tr>
              <!-- 凝血 1行 -->
              <tr>
                <td style="border:1px solid #000;padding:4px;text-align:center;">凝血</td>
                <td style="border:1px solid #000;padding:4px;">血小板（10^9/L）</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">&gt;150</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">≤150</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">≤100</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">≤50</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">≤20</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;font-weight:700;font-size:14px;">{{ scoreOf(items[1]) }}</td>
              </tr>
              <!-- 肝 1行 -->
              <tr>
                <td style="border:1px solid #000;padding:4px;text-align:center;">肝</td>
                <td style="border:1px solid #000;padding:4px;">胆红素（μmol/L）</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">&lt;20.5</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">≤34.1</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">≤102.5</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">≤205.1</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">&gt;205.1</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;font-weight:700;font-size:14px;">{{ scoreOf(items[2]) }}</td>
              </tr>
              <!-- 循环 5行 -->
              <tr>
                <td rowspan="5" style="border:1px solid #000;padding:4px;text-align:center;">循环</td>
                <td style="border:1px solid #000;padding:4px;">平均动脉压（mmHg）</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">≥70</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">&lt;70</td>
                <td style="border:1px solid #000;padding:4px;"></td>
                <td style="border:1px solid #000;padding:4px;"></td>
                <td style="border:1px solid #000;padding:4px;"></td>
                <td rowspan="5" style="border:1px solid #000;padding:4px;text-align:center;font-weight:700;font-size:14px;">{{ scoreOf(items[3]) }}</td>
              </tr>
              <tr>
                <td style="border:1px solid #000;padding:4px;">多巴胺（μg·kg⁻¹·min⁻¹）</td>
                <td style="border:1px solid #000;padding:4px;"></td>
                <td style="border:1px solid #000;padding:4px;"></td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">≤5</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">&gt;5</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">&gt;15</td>
              </tr>
              <tr>
                <td style="border:1px solid #000;padding:4px;">肾上腺素（μg·kg⁻¹·min⁻¹）</td>
                <td style="border:1px solid #000;padding:4px;"></td>
                <td style="border:1px solid #000;padding:4px;"></td>
                <td style="border:1px solid #000;padding:4px;"></td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">≤0.1</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">&gt;0.1</td>
              </tr>
              <tr>
                <td style="border:1px solid #000;padding:4px;">去甲肾上腺素剂量（μg·kg⁻¹·min⁻¹）</td>
                <td style="border:1px solid #000;padding:4px;"></td>
                <td style="border:1px solid #000;padding:4px;"></td>
                <td style="border:1px solid #000;padding:4px;"></td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">≤0.1</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">&gt;0.1</td>
              </tr>
              <tr>
                <td style="border:1px solid #000;padding:4px;">多巴酚丁胺剂量（μg·kg⁻¹·min⁻¹）</td>
                <td style="border:1px solid #000;padding:4px;"></td>
                <td style="border:1px solid #000;padding:4px;"></td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">任何剂量</td>
                <td style="border:1px solid #000;padding:4px;"></td>
                <td style="border:1px solid #000;padding:4px;"></td>
              </tr>
              <!-- 神经 1行 -->
              <tr>
                <td style="border:1px solid #000;padding:4px;text-align:center;">神经</td>
                <td style="border:1px solid #000;padding:4px;">GCS评分</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">15</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">13~14</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">10~12</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">6~9</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">&lt;6</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;font-weight:700;font-size:14px;">{{ scoreOf(items[4]) }}</td>
              </tr>
              <!-- 肾脏 2行 -->
              <tr>
                <td rowspan="2" style="border:1px solid #000;padding:4px;text-align:center;">肾脏</td>
                <td style="border:1px solid #000;padding:4px;">肌酐（μmol/L）</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">&lt;106</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">≤176</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">≤308</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">≤442</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">&gt;442</td>
                <td rowspan="2" style="border:1px solid #000;padding:4px;text-align:center;font-weight:700;font-size:14px;">{{ scoreOf(items[5]) }}</td>
              </tr>
              <tr>
                <td style="border:1px solid #000;padding:4px;">24小时尿量（ml/24h）</td>
                <td style="border:1px solid #000;padding:4px;"></td>
                <td style="border:1px solid #000;padding:4px;"></td>
                <td style="border:1px solid #000;padding:4px;"></td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">≤500</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">≤200</td>
              </tr>
              <!-- 总分行 -->
              <tr>
                <td colspan="7" style="border:1px solid #000;padding:6px;text-align:right;font-weight:700;">总分</td>
                <td style="border:1px solid #000;padding:6px;text-align:center;font-weight:700;font-size:16px;">{{ totalScore }}</td>
              </tr>
            </tbody>
          </table>
          <!-- 备注 -->
          <div style="margin-top:8px;font-size:11px;line-height:1.8;">
            备注：1.每日评估时应采取每日最差值；2.分数越高，预后越差。
          </div>
          <!-- 记录时间（白底，去掉参考图的紫色高亮） -->
          <div style="margin-top:12px;font-size:12px;">
            记录时间：{{ reportTime || fmtTimeNow() }}
          </div>
        </div>
      </div>
    </teleport>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as echarts from 'echarts'
import { jsPDF } from 'jspdf'
import html2canvas from 'html2canvas'
import {
  fetchSofaAssessment, fetchSofaAssessmentByNo, saveSofaRecord, fetchSofaRecords, deleteSofaRecord,
  fetchSofaTrend, fetchSofaRecordPdf, attachSofaRecordPdf
} from '../api/sofa'
import { isExternalMode } from '../utils/external'

const HOSPITAL_LOGO = '/logo.png'  /* 院徽静态资源：frontend/public/logo.png，构建后随 dist 输出 */

const route = useRoute()
const isExternal = isExternalMode()

const inHospitalNo = ref(route.query.inHospitalNo || '')
const patientId = ref(route.query.patientId || '')
const username = ref(route.query.username || '')
const realname = ref(route.query.realname || '')

// 文书 PDF / 来源趋势图
const reportRef = ref(null)
const reportTime = ref('')
const pdfGenerating = ref(false)
const trendChartRef = ref(null)
let trendChart = null
// 文书用到的汇总值（GCS / 尿量），随评估结果回填
const gcsTotal = ref(null)
const gcsDetail = ref('')
const urineMl = ref(null)

const patient = reactive({ patientId: '', inHospitalNo: '', name: '', bedCode: '', age: '', ageUnit: '岁', gender: '', departCode: '', departName: '', inDepartTime: '' })
const items = ref([])
const totalScore = ref(0)
const lastScore = ref(null)
const lastScoreTime = ref(null)
const deltaSofa = ref(null)
const weightUsed = ref(null)
const weightNote = ref('')
const weightSource = ref('')
const respiratorySupport = ref(0)
const remark = ref('')
const records = ref([])
const doctorRemark = ref('')
const saving = ref(false)
const loading = ref(false)

const rangeStart = ref('')
const rangeEnd = ref('')
/** 当前选中的快捷区间（24/48/72 小时，手动改时间后清空） */
const activeRange = ref(null)
const showSource = ref(false)
const sourceItem = ref(null)

onMounted(async () => {
  initRange()
  await loadAssessment()
  await loadRecords()
})

function initRange() {
  const now = new Date()
  const start = new Date(now.getTime() - 24 * 3600 * 1000)
  rangeEnd.value = toLocalInput(now)
  rangeStart.value = toLocalInput(start)
}

function toLocalInput(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
}

/** datetime-local → 后端 yyyy-MM-dd HH:mm:ss */
function toBackend(v) {
  if (!v) return ''
  return v.replace('T', ' ') + (v.length === 16 ? ':00' : '')
}

function quickRange(hours) {
  const now = new Date()
  rangeEnd.value = toLocalInput(now)
  rangeStart.value = toLocalInput(new Date(now.getTime() - hours * 3600 * 1000))
  activeRange.value = hours
  loadAssessment()
}

/** 手动修改时间输入后清除快捷区间高亮 */
function onRangeInput() {
  activeRange.value = null
  loadAssessment()
}

function fmtTime(t) {
  if (!t) return '—'
  let s = String(t)
  if (s.includes('T')) s = s.replace('T', ' ')
  return s.length > 16 ? s.slice(0, 16) : s
}

function scoreClass(s) {
  if (s === null || s === undefined) return ''
  if (s >= 10) return 'high'
  if (s >= 6) return 'mid'
  return 'low'
}

function prettyJson(s) {
  if (!s) return '—'
  try { return JSON.stringify(JSON.parse(s), null, 2) } catch (e) { return s }
}

async function loadAssessment() {
  const startTime = toBackend(rangeStart.value)
  const endTime = toBackend(rangeEnd.value)
  loading.value = true
  try {
    const res = inHospitalNo.value
      ? await fetchSofaAssessmentByNo(inHospitalNo.value, startTime, endTime)
      : await fetchSofaAssessment(patientId.value, startTime, endTime)
    if (!res) return
    Object.assign(patient, res.patient || {})
    items.value = res.items || []
    // 用自动分初始化各器官项的手工修正值，并按六项当前生效分值之和重算总分
    resetOverridesFromItems()
    lastScore.value = res.lastScore
    lastScoreTime.value = res.lastScoreTime
    deltaSofa.value = res.deltaSofa
    weightUsed.value = res.weightUsed
    weightNote.value = res.weightNote || ''
    weightSource.value = res.weightSource || ''
    respiratorySupport.value = res.respiratorySupport || 0
    remark.value = res.remark || ''
    // 文书用汇总值
    gcsTotal.value = res.gcsTotal
    gcsDetail.value = res.gcsDetail || ''
    urineMl.value = res.urineMl
    if (res.patient && res.patient.inHospitalNo) inHospitalNo.value = res.patient.inHospitalNo
  } catch (e) {
    ElMessage.error('SOFA 取数失败：' + (e.message || e))
  } finally {
    loading.value = false
  }
}

async function loadRecords() {
  if (!inHospitalNo.value) return
  try {
    const res = await fetchSofaRecords(inHospitalNo.value)
    records.value = res || []
  } catch (e) {
    console.warn('历史记录加载失败', e)
  }
}

function openSource(it) {
  sourceItem.value = it
  showSource.value = true
  // 弹窗渲染后再初始化图表（此时容器才有尺寸）
  nextTick(() => renderSourceTrend(it))
}

/** 来源弹窗趋势图：按当前器官项对应的 metricKey 拉取 {time,value} 序列 */
async function renderSourceTrend(it) {
  const el = trendChartRef.value
  if (!el || !it) return
  if (trendChart) {
    trendChart.dispose()
    trendChart = null
  }
  trendChart = echarts.init(el)
  trendChart.showLoading({ text: '加载中...', color: '#409eff', textColor: '#999', maskColor: 'rgba(255,255,255,0.8)' })

  let data = []
  try {
    const pid = patient.patientId || patientId.value
    if (pid) {
      data = await fetchSofaTrend(pid, it.key, toBackend(rangeStart.value), toBackend(rangeEnd.value))
    }
  } catch (e) {
    console.warn('趋势加载失败', e)
  }
  trendChart.hideLoading()

  const points = (Array.isArray(data) ? data : [])
    .filter(p => p && p.time && p.value !== null && p.value !== undefined)
    .map(p => [fmtTime(p.time), Number(p.value)])

  if (!points.length) {
    trendChart.setOption({
      title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: '#999', fontSize: 14, fontWeight: 'normal' } },
      xAxis: { show: false },
      yAxis: { show: false },
      series: []
    }, true)
    return
  }

  const unit = it.unit && it.unit !== 'GCS' ? it.unit : ''
  trendChart.setOption({
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v) => (v === null || v === undefined ? '—' : v + (unit ? ' ' + unit : ''))
    },
    grid: { left: 50, right: 22, top: 26, bottom: 40 },
    xAxis: { type: 'category', data: points.map(p => p[0]), axisLabel: { fontSize: 10, color: '#999', rotate: 30 } },
    yAxis: { type: 'value', name: unit, nameTextStyle: { fontSize: 10, color: '#999' }, axisLabel: { fontSize: 10, color: '#999' }, scale: true },
    series: [{
      type: 'line',
      data: points.map(p => p[1]),
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { color: '#409eff', width: 2 },
      itemStyle: { color: '#409eff' },
      // 高亮评分实际取用的那个点（窗口内最差值）
      markPoint: (it.value === null || it.value === undefined) ? undefined : {
        symbol: 'pin',
        symbolSize: 40,
        itemStyle: { color: '#f56c6c' },
        label: { fontSize: 10, color: '#fff', formatter: '评分取值' },
        data: points.filter(p => Math.abs(Number(p[1]) - Number(it.value)) < 0.001)
      }
    }]
  }, true)
  trendChart.resize()
}

async function save() {
  return doSave(false)
}

/** 「保存并生成文书」入口 */
function saveWithPdf() {
  return doSave(true)
}

/**
 * 保存评分；withPdf=true 时在主体落库后补传文书 PDF。
 * 与 APACHE II 同策略：主体（轻量 JSON）优先落库，PDF 大字段单独补传，
 * 文书生成失败/超时都不影响已保存的评分。
 */
async function doSave(withPdf) {
  if (!inHospitalNo.value) {
    ElMessage.warning('缺少住院号，无法保存')
    return
  }
  const rec = buildRecord()
  saving.value = true
  let pdfPromise = null
  try {
    if (withPdf) {
      pdfGenerating.value = true
      reportTime.value = fmtTimeNow()
      await nextTick()
      // 并行启动文书渲染，不阻塞评分落库这条关键路径；失败兜底 null，稍后再提示
      pdfPromise = buildSofaPdfBase64().catch(e => { console.error('生成文书PDF失败', e); return null })
    }
    const isReview = reviewingId.value !== null
    const saved = await saveSofaRecord(rec, toBackend(rangeStart.value), toBackend(rangeEnd.value))
    ElMessage.success(isReview ? '复核已保存' : 'SOFA 评分已保存')
    doctorRemark.value = ''
    reviewingId.value = null
    reviewingScoreTime.value = null
    const savedId = saved && saved.id ? saved.id : null
    await loadAssessment()
    await loadRecords()

    if (withPdf && savedId) {
      const pdfBase64 = await pdfPromise
      if (pdfBase64) {
        await attachSofaRecordPdf(savedId, pdfBase64, reportFileName())
        ElMessage.success('评分文书 PDF 已归档')
        await loadRecords()
      } else {
        ElMessage.warning('评分已保存，但文书 PDF 生成失败，可稍后重试')
      }
    }
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || e))
  } finally {
    saving.value = false
    pdfGenerating.value = false
  }
}

/** 前端按当前评估结果组装记录（后端若发现分值为空会自行重算兜底） */
function buildRecord() {
  const byKey = {}
  for (const it of items.value) byKey[it.key] = it
  // 分值取“当前生效值”（含医生手工修正），而非纯自动值
  const s = (k) => (byKey[k] ? scoreOf(byKey[k]) : (scoreOverrides[k] || 0))
  const adjusted = adjustmentsText()
  const baseRemark = doctorRemark.value || remark.value
  const isReview = reviewingId.value !== null
  return {
    // 复核：覆盖原自动记录并把来源改为 reviewed；否则按新手工评分插入
    id: isReview ? reviewingId.value : null,
    patientId: patient.patientId || patientId.value,
    inHospitalNo: inHospitalNo.value,
    patientName: patient.name,
    departCode: patient.departCode,
    scoreType: isReview ? 'reviewed' : 'custom',
    respScore: s('resp'),
    coagScore: s('coag'),
    liverScore: s('liver'),
    cardioScore: s('cardio'),
    neuroScore: s('neuro'),
    renalScore: s('renal'),
    totalScore: totalScore.value,
    // 手工修正明细自动记入备注，便于事后追溯
    remark: [adjusted, baseRemark].filter(Boolean).join('；'),
    createBy: realname.value || username.value || 'doctor'
  }
}

async function removeRecord(r) {
  try {
    await ElMessageBox.confirm(`确认删除 ${fmtTime(r.scoreTime)} 的评分记录？`, '删除确认', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
  } catch (e) {
    return
  }
  try {
    await deleteSofaRecord(r.id)
    ElMessage.success('已删除')
    await loadAssessment()
    await loadRecords()
  } catch (e) {
    ElMessage.error('删除失败：' + (e.message || e))
  }
}

// ---------------- 手工修正 / 复核 ----------------

/** SOFA 每项分值范围 0~4 */
const SCORE_OPTIONS = [0, 1, 2, 3, 4]

/** 各器官项“当前生效分值”：载入评估时用自动分初始化，医生可直接改 */
const scoreOverrides = reactive({})

/** 正在复核的自动记录（保存时覆盖该条并标记 reviewed） */
const reviewingId = ref(null)
const reviewingScoreTime = ref(null)

/** 某项当前生效分值（默认自动值） */
function scoreOf(it) {
  if (!it) return 0
  const ov = scoreOverrides[it.key]
  return (ov === null || ov === undefined || ov === '') ? (it.score || 0) : Number(ov)
}

/** 是否被手工改过（与自动值不一致） */
function isOverridden(it) {
  if (!it) return false
  const ov = scoreOverrides[it.key]
  if (ov === null || ov === undefined || ov === '') return false
  return Number(ov) !== (it.score || 0)
}

function scoreColor(s) {
  if (s >= 4) return '#f56c6c'
  if (s >= 3) return '#e6a23c'
  if (s >= 1) return '#409eff'
  return '#67c23a'
}

/** 器官小卡左侧标识方块：底色/文字随分值风险等级（对齐 APACHE II 分值徽章） */
function codeStyle(s) {
  const m = {
    0: ['#f0f9eb', '#529b2e'],
    1: ['#f0f9eb', '#529b2e'],
    2: ['#fdf6ec', '#b88230'],
    3: ['#fdf6ec', '#b88230'],
    4: ['#fef0f0', '#c45656']
  }
  const [background, color] = m[s] || ['#ecf5ff', '#409eff']
  return { background, color }
}

/** 恢复某项为自动取值 */
function resetOverride(key) {
  const it = items.value.find(x => x.key === key)
  if (it) scoreOverrides[key] = it.score || 0
  recalcTotal()
}

/** 总分 = 六项当前生效分值之和（含手工修正） */
function recalcTotal() {
  totalScore.value = items.value.reduce((sum, it) => sum + scoreOf(it), 0)
}

/** 载入评估结果后用自动分初始化各项，并清空复核态 */
function resetOverridesFromItems() {
  for (const k of Object.keys(scoreOverrides)) delete scoreOverrides[k]
  for (const it of items.value) scoreOverrides[it.key] = it.score || 0
  recalcTotal()
}

/** 手工修正说明，写入备注便于事后追溯（谁把哪项从几分改到几分） */
function adjustmentsText() {
  const parts = []
  for (const it of items.value) {
    if (!isOverridden(it)) continue
    parts.push(`${it.label} ${it.score || 0}→${scoreOf(it)}`)
  }
  return parts.length ? `手工修正：${parts.join('、')}` : ''
}

// ---------------- 左侧评分记录栏 ----------------

/** 记录按评分时间倒序（最新在上） */
const sortedRecords = computed(() => {
  return [...records.value].sort((a, b) => String(b.scoreTime || '').localeCompare(String(a.scoreTime || '')))
})

/** 当前选中的记录（仅用于高亮） */
const currentRecordId = ref(null)

function selectRecord(r) {
  if (!r) return
  currentRecordId.value = r.id
  if (isAutoRecord(r)) ElMessage.info('已选中该条自动初评，点侧栏「复核」可载入修改')
}

/** 新增评分：清空复核态并按当前取数范围重新取数 */
function addRecord() {
  currentRecordId.value = null
  if (reviewingId.value) {
    reviewingId.value = null
    reviewingScoreTime.value = null
  }
  doctorRemark.value = ''
  ElMessage.info('已重新取数，可开始新的评分')
  loadAssessment()
}

/** 侧栏总分徽章配色 */
function scoreBadgeClass(s) {
  const n = Number(s || 0)
  if (n >= 10) return 'red'
  if (n >= 6) return 'orange'
  return 'green'
}

// ---------------- 评分表：取值与命中列 ----------------

/** 解析某项的原始指标 JSON */
function rawOf(key) {
  const it = items.value.find(x => x.key === key)
  if (!it || !it.rawJson) return {}
  try { return JSON.parse(it.rawJson) || {} } catch (e) { return {} }
}

/** 数字展示（null/空 → —） */
function num(v, digits) {
  if (v === null || v === undefined || v === '') return '—'
  const n = Number(v)
  if (isNaN(n)) return '—'
  return digits === undefined ? String(n) : n.toFixed(digits)
}

/** 血管活性药：按药品名精确匹配（避免「肾上腺素」命中「去甲肾上腺素」） */
function vasoDose(name) {
  const list = rawOf('cardio').vasopressors || []
  return list.find(d => {
    const n = String(d.drugName || '')
    if (name === '肾上腺素') return n.includes('肾上腺素') && !n.includes('去甲肾上腺素')
    return n.includes(name)
  }) || null
}

function vasoOf(name) {
  const d = vasoDose(name)
  return d ? d.doseUgKgMin : null
}

function vasoHit(name, col) {
  const d = vasoDose(name)
  return d ? Number(d.score || 0) === col : false
}

/** 单项指标命中列（该器官自动分 === col） */
function hitCol(key, col) {
  const it = items.value.find(x => x.key === key)
  return it ? Number(it.score || 0) === col : false
}

/** rawJson 子项命中列（mapScore / creatinineScore / urineScore） */
function hitRaw(key, field, col) {
  const v = rawOf(key)[field]
  if (v === null || v === undefined) return false
  return Number(v) === col
}

function cellCls(hit) {
  return hit ? 'cell-hit' : ''
}

/** GCS 输入值 */
const gcsInputText = computed(() => {
  const r = rawOf('neuro')
  if (r.gcsTotal === null || r.gcsTotal === undefined) return '—'
  return `${r.gcsTotal} 分${r.gcsDetail ? ' (' + r.gcsDetail + ')' : ''}`
})

/** 24h 尿量输入值 */
const urineInputText = computed(() => {
  const u = rawOf('renal').urineMl
  return (u === null || u === undefined) ? '窗口<24h' : num(u, 0)
})

/** 近五次该器官分值（最新在前），无记录显示 — */
function nearFive(key) {
  const field = {
    resp: 'respScore', coag: 'coagScore', liver: 'liverScore',
    cardio: 'cardioScore', neuro: 'neuroScore', renal: 'renalScore'
  }[key]
  const list = sortedRecords.value.slice(0, 5)
    .map(r => r[field])
    .filter(v => v !== null && v !== undefined)
  if (!list.length) return '<span class="nf-none">—</span>'
  return list.map(v => {
    const n = Number(v) || 0
    return `<span class="nf nf-${n}">${n}</span>`
  }).join('')
}

/** 器官分值方块配色 */
function boxClass(s) {
  const n = Number(s || 0)
  if (n >= 4) return 'red'
  if (n >= 2) return 'orange'
  if (n >= 1) return 'blue'
  return 'green'
}

// ---------------- 总分趋势弹窗 ----------------

const showTrend = ref(false)
const totalTrendRef = ref(null)
let totalTrend = null

async function openTotalTrend() {
  showTrend.value = true
  await nextTick()
  const el = totalTrendRef.value
  if (!el) return
  if (totalTrend) { totalTrend.dispose(); totalTrend = null }
  totalTrend = echarts.init(el)
  totalTrend.showLoading({ text: '加载中…', color: '#409eff', textColor: '#999', maskColor: 'rgba(255,255,255,0.8)' })

  let data = []
  try {
    const pid = patient.patientId || patientId.value
    if (pid) data = await fetchSofaTrend(pid, 'total', toBackend(rangeStart.value), toBackend(rangeEnd.value))
  } catch (e) {
    console.warn('总分趋势加载失败', e)
  }
  totalTrend.hideLoading()

  const points = (Array.isArray(data) ? data : [])
    .filter(p => p && p.time && p.value !== null && p.value !== undefined)
    .map(p => [String(p.time), Number(p.value)])
    .sort((a, b) => a[0].localeCompare(b[0]))

  if (!points.length) {
    totalTrend.setOption({
      title: { text: '暂无趋势数据', left: 'center', top: 'middle', textStyle: { color: '#909399', fontSize: 13, fontWeight: 'normal' } },
      xAxis: { show: false },
      yAxis: { show: false },
      series: []
    }, true)
    return
  }

  totalTrend.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 50, right: 24, top: 30, bottom: 52 },
    xAxis: { type: 'category', data: points.map(p => fmtTime(p[0])), axisLabel: { fontSize: 10, color: '#909399', rotate: 30 } },
    yAxis: { name: 'SOFA', type: 'value', min: 0, nameTextStyle: { fontSize: 10, color: '#909399' }, axisLabel: { fontSize: 10, color: '#909399' } },
    series: [{
      type: 'line',
      data: points.map(p => p[1]),
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { color: '#409eff', width: 2 },
      itemStyle: { color: '#409eff' },
      areaStyle: { color: 'rgba(64,158,255,0.12)' }
    }]
  }, true)
  totalTrend.resize()
}

function closeTotalTrend() {
  showTrend.value = false
  if (totalTrend) { totalTrend.dispose(); totalTrend = null }
}

// ---- 来源三态（自动初评 / 已复核 / 手工评分） ----

/** 自动类来源：定时任务 daily、自动取数 auto */
function isAutoRecord(r) {
  return !!r && (r.scoreType === 'daily' || r.scoreType === 'auto')
}

function scoreTypeLabel(r) {
  if (!r) return ''
  if (r.scoreType === 'reviewed') return '已复核'
  if (isAutoRecord(r)) return '自动初评'
  return '手工评分'
}

function recTagClass(r) {
  if (!r) return 'manual'
  if (r.scoreType === 'reviewed') return 'reviewed'
  return isAutoRecord(r) ? 'auto' : 'manual'
}

/** 载入某条自动初评进行复核：分值作为初始生效值，保存时覆盖原记录并标记“已复核” */
function reviewRecord(r) {
  if (!r) return
  for (const k of Object.keys(scoreOverrides)) delete scoreOverrides[k]
  scoreOverrides.resp = r.respScore || 0
  scoreOverrides.coag = r.coagScore || 0
  scoreOverrides.liver = r.liverScore || 0
  scoreOverrides.cardio = r.cardioScore || 0
  scoreOverrides.neuro = r.neuroScore || 0
  scoreOverrides.renal = r.renalScore || 0
  reviewingId.value = r.id
  reviewingScoreTime.value = r.scoreTime
  doctorRemark.value = r.remark || ''
  recalcTotal()
  ElMessage.info('已载入该条自动初评，调整后点“保存复核”覆盖')
}

function cancelReview() {
  reviewingId.value = null
  reviewingScoreTime.value = null
  resetOverridesFromItems()
  ElMessage.info('已取消复核')
}

// ---------------- 文书 PDF ----------------

function fmtTimeNow() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function reportFileName() {
  const name = patient.name || inHospitalNo.value || 'patient'
  return `SOFA评分表_${name}_${fmtTimeNow().replace(/[: ]/g, '')}.pdf`
}

/** 将离屏文书 DOM 渲染为单页 A4 的 jsPDF（整表等比缩放，绝不跨页截断） */
async function buildSofaPdf() {
  await nextTick()
  const el = reportRef.value
  if (!el) throw new Error('文书未渲染')
  const canvas = await html2canvas(el, { scale: 1.5, useCORS: true, backgroundColor: '#ffffff', logging: false })
  const pdf = new jsPDF('p', 'mm', 'a4')
  const pageW = 210
  const pageH = 297
  const margin = 6
  const maxW = pageW - margin * 2
  const maxH = pageH - margin * 2
  let imgW = maxW
  let imgH = canvas.height * imgW / canvas.width
  if (imgH > maxH) {
    imgH = maxH
    imgW = canvas.width * imgH / canvas.height
  }
  const imgData = canvas.toDataURL('image/jpeg', 0.85)
  pdf.addImage(imgData, 'JPEG', (pageW - imgW) / 2, margin, imgW, imgH)
  return pdf
}

/** 生成 PDF Base64（不含 data: 前缀）；15s 超时兜底，避免渲染异常拖死保存流程 */
async function buildSofaPdfBase64() {
  const pdf = await Promise.race([
    buildSofaPdf(),
    new Promise((_, reject) => setTimeout(() => reject(new Error('文书渲染超时(15s)')), 15000))
  ])
  const uri = pdf.output('datauristring')
  return uri.includes(',') ? uri.split(',')[1] : uri
}

/** 在线查看已归档的文书 PDF */
async function viewPdf(r) {
  try {
    const data = await fetchSofaRecordPdf(r.id)
    if (!data || !data.pdfData) {
      ElMessage.warning('该记录暂无文书')
      return
    }
    const blob = base64ToBlob(data.pdfData, 'application/pdf')
    const url = URL.createObjectURL(blob)
    window.open(url, '_blank')
    setTimeout(() => URL.revokeObjectURL(url), 60000)
  } catch (e) {
    ElMessage.error('文书打开失败：' + (e.message || e))
  }
}

function base64ToBlob(base64, type) {
  const bin = atob(base64)
  const len = bin.length
  const bytes = new Uint8Array(len)
  for (let i = 0; i < len; i++) bytes[i] = bin.charCodeAt(i)
  return new Blob([bytes], { type })
}
</script>

<style scoped>
/* 离屏文书渲染源：移出视口但保留真实尺寸供 html2canvas 渲染（不可用 display:none，否则无法出图） */
.report-offscreen { position: absolute; left: -9999px; top: 0; width: 794px; pointer-events: none; }

/* ===== 全局基调（参考高保真稿） ===== */
.sofa-page {
  min-height: 100vh;
  background: #EFF1F4;
  font-family: "Microsoft YaHei", "PingFang SC", "Segoe UI", Arial, sans-serif;
  color: #28303E;
  font-size: 14px;
  -webkit-font-smoothing: antialiased;
}

/* ===== 页面骨架：左侧记录栏 + 右侧主区 ===== */
.app { display: flex; min-height: 100vh; align-items: stretch; }
.sidebar { width: 288px; flex: 0 0 288px; background: #fff; border-right: 1px solid #E8EAEE; padding: 14px 16px 20px; overflow-y: auto; }
.side-title { display: flex; align-items: center; gap: 8px; font-size: 21px; font-weight: 700; color: #1F2733; margin-bottom: 13px; }
.side-title .count { display: inline-flex; align-items: center; justify-content: center; min-width: 26px; height: 26px; padding: 0 7px; border-radius: 8px; background: #EAF1FC; color: #4485DB; font-size: 15px; font-weight: 700; box-sizing: border-box; }
.btn-add { width: 100%; height: 48px; border: none; border-radius: 11px; cursor: pointer; background: linear-gradient(180deg,#4A94E3 0%,#1E6BD6 55%,#1459C4 100%); color: #fff; font-size: 18px; font-weight: 700; letter-spacing: 2px; box-shadow: 0 3px 8px rgba(30,107,214,.25); margin-bottom: 14px; }
.btn-add:hover { filter: brightness(1.06); }
.record-list { display: flex; flex-direction: column; gap: 11px; }
.rec-card { background: #fff; border: 1.5px solid #E5E7EB; border-radius: 12px; padding: 11px 13px 10px; margin-bottom: 0; cursor: pointer; transition: all .15s; box-sizing: border-box; }
.rec-card:hover { border-color: #C9DCF6; }
.rec-card.active { border: 2px solid #4485DB; padding: 10px 12px 9px; box-shadow: 0 2px 10px rgba(68,133,219,.12); }
.rec-row { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 9px; }
.rec-time { font-size: 17px; font-weight: 600; color: #1F2733; }
.score-badge { display: inline-flex; align-items: center; justify-content: center; min-width: 33px; height: 33px; padding: 0 6px; border-radius: 9px; font-size: 18px; font-weight: 700; box-sizing: border-box; }
.score-badge.green { background: #E9F7EA; color: #52A457; border: 1px solid #C6E6C9; }
.score-badge.orange { background: #FCEBDC; color: #F08A2E; border: 1px solid #F6D2AE; }
.score-badge.red { background: #FBE3E3; color: #D9534F; border: 1px solid #F2BDBD; }
.tag-row { display: flex; gap: 8px; flex-wrap: wrap; }
.tag { font-size: 13px; padding: 3px 10px; border-radius: 7px; line-height: 1.5; background: #F0F1F3; color: #8A919E; }
.tag.gray { background: #F0F1F3; color: #8A919E; }
.tag.blue { background: #EAF1FC; color: #4485DB; cursor: pointer; }
.tag.green { background: #E9F7EA; color: #52A457; }
.tag.orange { background: #FCEBDC; color: #E6883F; cursor: pointer; }
.tag.auto { background: #EAF1FC; color: #4485DB; }
.tag.manual { background: #FCEBDC; color: #E6883F; }
.tag.reviewed { background: #E9F7EA; color: #52A457; }
.record-empty { text-align: center; color: #A6ADB8; font-size: 14px; padding: 24px 0; }

.main { flex: 1; min-width: 0; padding: 12px 18px 24px; }

/* ===== 患者信息行 ===== */
.patient-row { display: flex; align-items: center; gap: 16px; margin-bottom: 11px; flex-wrap: wrap; }
.patient-row .bed-tag { background: #4485DB; color: #fff; font-size: 15px; font-weight: 700; padding: 4px 11px; border-radius: 8px; }
.patient-row .patient-name { font-size: 24px; font-weight: 700; color: #1F2733; }
.patient-meta { font-size: 14px; color: #5A6270; }
.patient-meta b { color: #28303E; font-weight: 600; }
.patient-row .resp-flag { font-size: 13px; padding: 3px 11px; border-radius: 14px; background: #F0F1F3; color: #8A919E; }
.patient-row .resp-flag.on { background: #EAF1FC; color: #4485DB; }
.btn-trend { margin-left: auto; border: 1.5px solid #4485DB; background: #fff; color: #3A78D0; font-size: 14px; padding: 6px 15px; border-radius: 8px; cursor: pointer; }
.btn-trend:hover { background: #EAF1FC; }

/* ===== 总览条：总分卡 + 6 器官小卡 ===== */
.overview { display: flex; gap: 13px; background: #E8EAEE; border-radius: 13px; padding: 12px; margin-top: 12px; margin-bottom: 12px; }
.ov-total { width: 224px; flex-shrink: 0; border-radius: 11px; color: #fff; padding: 13px 16px; background: linear-gradient(128deg,#5FA3E3 0%,#2E7ED9 42%,#0E54C4 100%); box-shadow: 0 3px 10px rgba(16,84,196,.22); display: flex; flex-direction: column; box-sizing: border-box; }
.ov-total .t-label { font-size: 14px; opacity: .92; }
.ov-total .t-num { font-size: 50px; font-weight: 700; line-height: 1.05; margin: 1px 0 7px; }
.ov-total .t-pill { align-self: flex-start; font-size: 13px; color: #fff; background: rgba(255,255,255,.20); border: 1px solid rgba(255,255,255,.28); padding: 3px 11px; border-radius: 16px; }
.ov-total .t-delta { margin-top: 7px; font-size: 12px; opacity: .95; }
.ov-total .t-delta b { color: #FFE7A8; }
.ov-total .t-foot { margin-top: auto; padding-top: 6px; font-size: 12px; opacity: .82; }
.ov-organs { flex: 1; display: grid; grid-template-columns: repeat(6, 1fr); gap: 13px; min-width: 0; }
.ov-card { background: linear-gradient(135deg,#F5F7FA 0%,#EDF0F4 100%); border: 1px solid #E2E5EA; border-radius: 11px; padding: 11px 12px; display: flex; flex-direction: column; gap: 9px; justify-content: center; min-width: 0; box-sizing: border-box; }
.ov-card.overridden { box-shadow: inset 0 0 0 2px #F08A2E; }
.ov-line { display: flex; align-items: center; gap: 9px; min-width: 0; }
.ov-box { width: 30px; height: 28px; flex: 0 0 30px; border-radius: 8px; display: inline-flex; align-items: center; justify-content: center; font-size: 17px; font-weight: 700; box-sizing: border-box; }
.ov-box.green { background: #E4ECDC; color: #4E713D; border: 1px solid #CFDCC2; }
.ov-box.blue { background: linear-gradient(180deg,#4E8FE3,#3A78D0); color: #fff; box-shadow: 0 1px 3px rgba(58,120,208,.3); }
.ov-box.orange { background: #FCEBDC; color: #E6883F; border: 1px solid #F6D2AE; }
.ov-box.red { background: #FBE3E3; color: #D9534F; border: 1px solid #F2BDBD; }
.ov-name { font-size: 19px; font-weight: 700; color: #1F2733; flex: 1; min-width: 0; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.ov-src { flex: 0 0 auto; border: none; background: transparent; color: #4485DB; font-size: 12px; cursor: pointer; padding: 0; }
.ov-src:hover { text-decoration: underline; }
.ov-plain { flex: 1; min-width: 0; font-size: 17px; font-weight: 500; color: #1F2733; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.ov-sel { flex: 0 0 auto; width: 46px; height: 26px; border: 1px solid #C9D4E6; border-radius: 7px; background: #fff; font-size: 14px; font-weight: 700; color: #28303E; padding: 0 2px; text-align: center; cursor: pointer; }
.ov-sub { display: flex; align-items: center; gap: 6px; min-width: 0; }
.ov-chip { font-size: 11px; color: #6B7280; background: #fff; border: 1px solid #E2E5EA; border-radius: 5px; padding: 1px 6px; white-space: nowrap; }
.ov-chip.missing { background: #FBE3E3; color: #D9534F; border-color: #F2BDBD; }
.ov-time { font-size: 11px; color: #8A919E; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.ov-reset { margin-left: auto; border: none; background: transparent; color: #4485DB; font-size: 11px; cursor: pointer; padding: 0; }

/* ===== 取数时间范围 ===== */
.range-row { display: flex; align-items: center; gap: 10px; margin: 12px 0 11px; flex-wrap: wrap; }
.range-row .lbl { font-size: 14px; color: #28303E; font-weight: 600; }
.range-row .sel { height: 34px; border: 1px solid #D3D9E2; border-radius: 7px; background: #fff; padding: 0 10px; font-size: 13.5px; color: #28303E; }
.range-row .tilde { color: #8A919E; }
.rbtn { height: 34px; border-radius: 7px; padding: 0 15px; font-size: 13.5px; cursor: pointer; background: #fff; color: #3A78D0; border: 1.5px solid #8FB2E4; }
.rbtn:hover { background: #EAF1FC; }
.rbtn.active, .rbtn.solid { background: linear-gradient(180deg,#3E84E6,#1E6BD6); color: #fff; border-color: transparent; font-weight: 600; }
.rbtn.active:hover, .rbtn.solid:hover { filter: brightness(1.06); }
.range-logic { margin-left: auto; font-size: 13px; color: #7A828F; }

/* ===== 器官功能评分表 ===== */
.table-panel { background: #fff; border-radius: 10px; border: 1px solid #E6E9EF; padding: 11px 14px 4px; }
.table-title { display: flex; align-items: center; gap: 9px; font-size: 18px; font-weight: 700; color: #1F2733; margin-bottom: 9px; }
.table-title::before { content: ''; width: 5px; height: 19px; border-radius: 3px; background: linear-gradient(180deg,#3E84E6,#1E6BD6); }
table.score { width: 100%; border-collapse: collapse; table-layout: fixed; }
table.score th, table.score td { border: 1px solid #E4E7ED; text-align: center; padding: 0; height: 39px; font-size: 13.5px; vertical-align: middle; color: #333B48; }
table.score thead th { background: #E6EAF1; color: #2B57A8; font-weight: 700; height: 36px; }
table.score td.sys { font-weight: 700; color: #1F2733; background: #FBFCFD; font-size: 14.5px; }
table.score td.ind { text-align: left; padding-left: 13px; color: #333B48; background: #fff; }
table.score td.dim { color: #A6ADB8; }
table.score td.cell-hit { background: #DEE5EF; color: #2B57A8; font-weight: 600; }
table.score .inp { display: flex; align-items: center; justify-content: space-between; height: 30px; margin: 0 auto; width: 88%; border: 1px solid #D3D9E2; border-radius: 7px; background: #fff; padding: 0 10px; color: #28303E; font-size: 13.5px; font-weight: 500; box-sizing: border-box; overflow: hidden; white-space: nowrap; }
table.score .inp.drop { background: #F6F8FB; border-color: #C9D4E6; }
table.score .inp .caret { color: #9AA3B0; font-size: 12px; flex-shrink: 0; }
table.score td.near { padding: 4px 6px; height: auto; }
.nf { display: inline-block; min-width: 20px; height: 20px; line-height: 20px; border-radius: 5px; font-size: 12px; font-weight: 700; margin: 0 1px; box-sizing: border-box; }
.nf-0 { background: #E9F7EA; color: #52A457; }
.nf-1 { background: #EAF1FC; color: #4485DB; }
.nf-2 { background: #FCEBDC; color: #E6883F; }
.nf-3 { background: #FCEBDC; color: #E6883F; }
.nf-4 { background: #FBE3E3; color: #D9534F; }
.nf-none { color: #A6ADB8; }
.score-tag { display: inline-flex; align-items: center; justify-content: center; min-width: 46px; height: 30px; padding: 0 12px; border-radius: 8px; background: #D7E2F6; color: #2560C8; font-weight: 700; font-size: 15px; box-sizing: border-box; }

.tip { display: flex; align-items: center; gap: 8px; margin: 10px 0 8px; padding: 9px 13px; background: #FCF2D9; border: 1px solid #F3E0AC; border-radius: 8px; color: #9C7A3C; font-size: 13px; line-height: 1.6; }
.tip .ico { flex: 0 0 18px; width: 18px; height: 18px; border-radius: 50%; background: #E6A23C; color: #fff; display: inline-flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; flex-shrink: 0; box-sizing: border-box; }
.tip b { color: #C2700F; }

/* ===== 底部操作行 ===== */
.foot { display: flex; align-items: center; gap: 16px; margin-top: 8px; flex-wrap: wrap; }
.foot-info { font-size: 13px; color: #7A828F; line-height: 1.7; flex: 0 0 auto; }
.foot-note { flex: 1; min-width: 200px; height: 38px; border: 1px solid #D3D9E2; border-radius: 8px; background: #fff; padding: 0 13px; font-size: 13.5px; color: #28303E; outline: none; }
.foot-note:focus { border-color: #4485DB; }
.fbtn { height: 38px; border-radius: 8px; padding: 0 20px; font-size: 14.5px; cursor: pointer; }
.fbtn.ghost { background: #fff; border: 1.5px solid #8FB2E4; color: #3A78D0; }
.fbtn.ghost:hover { background: #EAF1FC; }
.fbtn.solid { background: linear-gradient(180deg,#3E84E6,#1E6BD6); color: #fff; border: none; font-weight: 600; box-shadow: 0 2px 6px rgba(30,107,214,.25); }
.fbtn.solid:hover { filter: brightness(1.06); }
.fbtn:disabled { opacity: .6; cursor: not-allowed; }

.btn { height: 32px; padding: 0 14px; border: 1.5px solid #8FB2E4; background: #fff; border-radius: 7px; font-size: 13px; color: #3A78D0; cursor: pointer; }
.btn:hover { background: #EAF1FC; }
.btn-primary { background: linear-gradient(180deg,#3E84E6,#1E6BD6); border: none; color: #fff; font-weight: 600; }
.btn-primary:hover { filter: brightness(1.06); }
.btn-primary:disabled { opacity: .6; cursor: not-allowed; }
.btn-text { border: none; background: transparent; color: #4485DB; padding: 0 6px; cursor: pointer; }
.btn-text.danger { color: #D9534F; }

.modal-mask { position: fixed; inset: 0; background: rgba(31,39,51,.45); display: flex; align-items: center; justify-content: center; z-index: 2000; }
.modal { width: 760px; max-width: 92vw; max-height: 86vh; overflow: auto; background: #fff; border-radius: 12px; }
.modal-head { display: flex; align-items: center; justify-content: space-between; padding: 14px 18px; border-bottom: 1px solid #E8EAEE; }
.modal-head h3 { margin: 0; font-size: 17px; color: #1F2733; }
.modal-close { border: none; background: transparent; font-size: 22px; cursor: pointer; color: #8A919E; }
.modal-body { padding: 16px 18px; }
.modal-foot { padding: 12px 18px; border-top: 1px solid #E8EAEE; text-align: right; }
.src-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 14px; }
.src-card { padding: 12px; background: #F5F7FA; border: 1px solid #E2E5EA; border-radius: 10px; }
.src-label { font-size: 12px; color: #8A919E; }
.src-value { font-size: 20px; font-weight: 700; color: #2560C8; margin-top: 4px; }
.src-section { font-size: 13px; color: #5A6270; margin-bottom: 6px; }
.src-title { margin: 12px 0 6px; font-weight: 700; font-size: 14px; color: #1F2733; border-left: 4px solid #3E84E6; padding-left: 8px; }
.src-json { background: #F5F7FA; border: 1px solid #E2E5EA; border-radius: 8px; padding: 10px; font-size: 12px; color: #5A6270; max-height: 220px; overflow: auto; }
.src-desc { font-size: 13px; color: #5A6270; line-height: 1.8; }
</style>
