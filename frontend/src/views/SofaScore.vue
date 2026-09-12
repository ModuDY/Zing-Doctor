<template>
  <div class="sofa-page">
    <div class="app">
      <!-- ============ 左侧：评分记录 ============ -->
      <aside class="side">
        <div class="side-head">
          <span>评分记录</span>
          <span class="count">{{ sortedRecords.length }}</span>
        </div>
        <div class="side-add">
          <button class="add-record-btn" @click="addRecord">
            <span class="plus">＋</span>
            新增评分
          </button>
        </div>
        <div class="record-list">
          <div v-for="r in sortedRecords" :key="r.id"
               :class="['record-item', { active: currentRecordId === r.id }]"
               @click="selectRecord(r)">
            <div class="record-top">
              <span class="record-time">{{ fmtTime(r.scoreTime) }}</span>
              <span :class="['record-score', scoreBadgeClass(r.totalScore)]">{{ r.totalScore }}</span>
            </div>
            <div class="record-meta">
              <span>{{ r.createBy || '—' }}</span>
              <span :class="['record-tag', recTagClass(r)]">{{ scoreTypeLabel(r) }}</span>
              <span v-if="r.hasPdf === 1" class="record-tag pdf-tag" @click.stop="viewPdf(r)">PDF文书</span>
              <span v-if="isAutoRecord(r)" class="record-tag review-tag" @click.stop="reviewRecord(r)">复核</span>
              <span class="record-tag del-tag" @click.stop="removeRecord(r)">删除</span>
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
          <div v-for="it in items" :key="it.key" class="ov-card">
            <span class="ov-pic" :class="boxClass(scoreOf(it))" v-html="ORGAN_SVG[it.key] || ''"></span>
            <span class="ov-name">{{ it.label }}</span>
            <div class="ov-foot">
              <span :class="['ov-num', boxClass(scoreOf(it))]">{{ scoreOf(it) }}</span>
              <span class="ov-unit">分</span>
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
        <button class="rbtn solid" :disabled="loading" @click="loadAssessment">{{ loading ? '取数中…' : '自动获取并计算' }}</button>
        <span class="range-logic">取数逻辑：范围内最差值（偏离正常最远）</span>
      </div>

      <!-- ===== 器官功能评分表 ===== -->
      <div class="table-panel">
        <div class="table-title">器官功能评分表</div>
        <table class="score">
          <colgroup>
            <col style="width:9%"><col style="width:16%"><col style="width:15%"><col style="width:8%">
            <col style="width:10.4%"><col style="width:10.4%"><col style="width:10.4%"><col style="width:10.4%"><col style="width:10.4%">
          </colgroup>
          <thead>
            <tr><th>器官系统</th><th>评估指标</th><th>输入值</th><th>操作</th>
              <th>0</th><th>1</th><th>2</th><th>3</th><th>4</th></tr>
          </thead>
          <tbody>
            <!-- 呼吸 -->
            <tr>
              <td class="sys" rowspan="2">呼吸系统</td>
              <td class="ind">呼吸机支持</td>
              <td><div class="inp drop">{{ respiratorySupport === 1 ? '是' : '否' }}<span class="caret">▾</span></div></td>
              <td class="act" rowspan="2"><button class="btn-src" @click="openSource(itemOf('resp'))">来源</button></td>
              <td class="dim">—</td><td class="dim">—</td><td class="dim">—</td><td></td><td></td>
            </tr>
            <tr>
              <td class="ind">PaO₂/F.iO₂ (mmHg)</td>
              <td><div class="inp">{{ num(rawOf('resp').oxygenationIndex, 1) }}</div></td>
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
              <td class="act"><button class="btn-src" @click="openSource(itemOf('coag'))">来源</button></td>
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
              <td class="act"><button class="btn-src" @click="openSource(itemOf('liver'))">来源</button></td>
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
              <td class="act" rowspan="5"><button class="btn-src" @click="openSource(itemOf('cardio'))">来源</button></td>
              <td :class="cellCls(hitRaw('cardio', 'mapScore', 0))">≥70</td>
              <td :class="cellCls(hitRaw('cardio', 'mapScore', 1))">&lt;70</td>
              <td class="dim">—</td><td class="dim">—</td><td class="dim">—</td>
            </tr>
            <tr>
              <td class="ind">多巴胺 (μg·kg⁻¹·min⁻¹)</td>
              <td><div class="inp">{{ num(vasoOf('多巴胺'), 3) }}</div></td>
              <td class="dim">—</td><td class="dim">—</td>
              <td :class="cellCls(vasoHit('多巴胺', 2))">≤5</td>
              <td :class="cellCls(vasoHit('多巴胺', 3))">5~15</td>
              <td :class="cellCls(vasoHit('多巴胺', 4))">&gt;15</td>
            </tr>
            <tr>
              <td class="ind">肾上腺素 (μg·kg⁻¹·min⁻¹)</td>
              <td><div class="inp">{{ num(vasoOf('肾上腺素'), 3) }}</div></td>
              <td class="dim">—</td><td class="dim">—</td><td class="dim">—</td>
              <td :class="cellCls(vasoHit('肾上腺素', 3))">≤0.1</td>
              <td :class="cellCls(vasoHit('肾上腺素', 4))">&gt;0.1</td>
            </tr>
            <tr>
              <td class="ind">去甲肾上腺素 (μg·kg⁻¹·min⁻¹)</td>
              <td><div class="inp">{{ num(vasoOf('去甲肾上腺素'), 3) }}</div></td>
              <td class="dim">—</td><td class="dim">—</td><td class="dim">—</td>
              <td :class="cellCls(vasoHit('去甲肾上腺素', 3))">≤0.1</td>
              <td :class="cellCls(vasoHit('去甲肾上腺素', 4))">&gt;0.1</td>
            </tr>
            <tr>
              <td class="ind">多巴酚丁胺 (μg·kg⁻¹·min⁻¹)</td>
              <td><div class="inp">{{ num(vasoOf('多巴酚丁胺'), 3) }}</div></td>
              <td class="dim">—</td>
              <td :class="cellCls(vasoHit('多巴酚丁胺', 2))">任何剂量</td>
              <td class="dim">—</td><td class="dim">—</td>
            </tr>
            <!-- 神经 -->
            <tr>
              <td class="sys">神经系统</td>
              <td class="ind">GCS 评分</td>
              <td><div class="inp">{{ gcsInputText }}</div></td>
              <td class="act"><button class="btn-src" @click="openSource(itemOf('neuro'))">来源</button></td>
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
              <td class="act" rowspan="2"><button class="btn-src" @click="openSource(itemOf('renal'))">来源</button></td>
              <td :class="cellCls(hitRaw('renal', 'creatinineScore', 0))">&lt;106</td>
              <td :class="cellCls(hitRaw('renal', 'creatinineScore', 1))">≤176</td>
              <td :class="cellCls(hitRaw('renal', 'creatinineScore', 2))">≤308</td>
              <td :class="cellCls(hitRaw('renal', 'creatinineScore', 3))">≤442</td>
              <td :class="cellCls(hitRaw('renal', 'creatinineScore', 4))">&gt;442</td>
            </tr>
            <tr>
              <td class="ind">24h 尿量 (ml)</td>
              <td><div class="inp">{{ urineInputText }}</div></td>
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
      <div class="footer-bar">
        <div class="footer-info">
          评分医师：{{ realname || username || '—' }}<br>
          创建时间：{{ fmtTimeNow() }}
        </div>
        <input class="footer-note" v-model="doctorRemark" placeholder="备注（可选）" />
        <button class="btn" @click="loadAssessment">重置</button>
        <button class="btn" :disabled="saving || pdfGenerating" @click="saveWithPdf">
          {{ pdfGenerating ? '生成文书…' : '保存并生成文书' }}
        </button>
        <button class="btn btn-success" :disabled="saving" @click="save">
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
    console.warn('SOFA 取数失败：', e.message || e)
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
    console.warn('保存失败：', e.message || e)
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
    console.warn('删除失败：', e.message || e)
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

/** 按 key 定位器官项（表格「操作」列的来源弹窗入口） */
function itemOf(key) {
  return items.value.find(x => x.key === key) || null
}

/** 器官图形（内联 SVG，随分值着色，仅用于只读展示） */
const ORGAN_SVG = {
  // 肺：气管 + 支气管分叉 + 左右肺叶（含叶间裂）
  resp: '<svg viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' +
    '<path d="M24 5.5V13"/><path d="M24 13c0 1.9-1.4 3-3.2 3.6"/><path d="M24 13c0 1.9 1.4 3 3.2 3.6"/>' +
    '<path d="M20.8 16.6c-4.5 1.1-7.5 4.8-8.3 10.1-.8 5.3.5 10.4 3.1 13.5 1.3 1.6 4.3 1.5 5.4-.2.8-1.2.7-2.7 1.6-3.9.9-1.2 2.8-1.3 3.8-.2.8.9.9 2 1.6 2.8 1.2 1 3.1.7 3.8-.6.6-1.1.3-2.3.3-3.5 0-1.8-.2-3.7-.2-5.5 0-5.7 2.1-9.6 6.3-10.8" fill="currentColor" fill-opacity=".15"/>' +
    '<path d="M13.6 24.8c2.5.6 5 .7 7.5.3" stroke-width="1.2" opacity=".6"/>' +
    '<path d="M12.9 32.4c2.7 1 5.5 1.4 8.4 1.1" stroke-width="1.2" opacity=".6"/>' +
    '<path d="M27.2 16.6c4.5 1.1 7.5 4.8 8.3 10.1.8 5.3-.5 10.4-3.1 13.5-1.3 1.6-4.3 1.5-5.4-.2-.8-1.2-.7-2.7-1.6-3.9-.9-1.2-2.8-1.3-3.8-.2-.8.9-.9 2-1.6 2.8-1.2 1-3.1.7-3.8-.6-.6-1.1-.3-2.3-.3-3.5 0-1.8.2-3.7.2-5.5 0-5.7-2.1-9.6-6.3-10.8" fill="currentColor" fill-opacity=".15"/>' +
    '<path d="M34.4 24.8c-2.5.6-5 .7-7.5.3" stroke-width="1.2" opacity=".6"/>' +
    '<path d="M35.1 32.4c-2.7 1-5.5 1.4-8.4 1.1" stroke-width="1.2" opacity=".6"/></svg>',
  // 凝血：血滴 + 高光 + 卫星小滴
  coag: '<svg viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' +
    '<path d="M24 6c7.7 9.5 12.6 16 12.6 21.7 0 7-5.7 12.7-12.6 12.7S11.4 34.7 11.4 27.7C11.4 22 16.3 15.5 24 6z" fill="currentColor" fill-opacity=".15"/>' +
    '<path d="M17.2 29c.7 3.6 3.5 6.3 7.2 6.7" stroke-width="1.4" opacity=".7"/>' +
    '<circle cx="37" cy="35.6" r="2.9" fill="currentColor" fill-opacity=".22" stroke-width="1.5"/>' +
    '<circle cx="11" cy="37.6" r="2" fill="currentColor" fill-opacity=".18" stroke-width="1.3"/></svg>',
  // 肝：膈面圆隆 + 右叶宽大下缘波状 + 镰状韧带 + 胆囊
  liver: '<svg viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' +
    '<path d="M6 16.4c0-.7.3-1.4.9-1.8C9.4 12.8 14 11.9 18.9 12.4c8.7.9 15.7 6.3 17.8 13.4.6 2-.3 4.3-2.2 5.3-2.4 1.3-5.3.8-7.9-.4-2.6-1.2-5.5-1.8-8.4-1.8h-3.7C9.4 28.9 6 25.5 6 20.4v-4z" fill="currentColor" fill-opacity=".15"/>' +
    '<path d="M26.2 14.3c1.1 7.2 1.2 14.5.3 21.7" stroke-width="1.3" opacity=".55"/>' +
    '<path d="M10.5 20.5c4.6 1.2 9.4 1.3 14.2.4" stroke-width="1.2" opacity=".5"/>' +
    '<path d="M34.8 32.2c1.9-.7 4-.3 5.5 1.1" stroke-width="1.4" opacity=".8"/>' +
    '<path d="M36.3 33.1c1.4-1.2 3.5-1 4.7.4.7.9.9 2.1.4 3.1-1.3.7-2.8.5-3.9-.4-.7-.6-1-1.9-1.2-3.1z" fill="currentColor" fill-opacity=".25" stroke-width="1.2" opacity=".85"/></svg>',
  // 循环：解剖心脏 + 心底三血管（腔静脉/主动脉弓/肺动脉）+ 室间沟 + 冠状动脉
  cardio: '<svg viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' +
    '<path d="M17.5 12V6.5"/>' +
    '<path d="M25 11.5c0-3 1.5-5.6 4-6.9"/>' +
    '<path d="M31 11.8c.3-2.4 1.6-4.4 3.6-5.4"/>' +
    '<path d="M15.5 9.8c-4.3 1.6-7 5.6-6.6 10 .3 3.7 2.5 6.4 4.6 9.3 2.4 3.3 5.2 6.9 8.9 9.4 2.5 1.7 5.9 1.3 8-.9 3-3.2 6-7.9 7.6-12.5 1.5-4.3 1.1-9-1.7-12.3-2.7-3.2-7-4.2-10.7-2.6-1.6.7-3.4.7-5 0-.6-.3-1.2-.5-1.9-.4z" fill="currentColor" fill-opacity=".15"/>' +
    '<path d="M22.5 13.5c1.5 5.5.8 11.5-2 17.3" stroke-width="1.3" opacity=".6"/>' +
    '<path d="M21.8 19.5c-2.2.4-4 1.9-4.9 4" stroke-width="1.2" opacity=".55"/></svg>',
  // 神经：脑双半球（俯视）+ 纵裂 + 脑沟
  neuro: '<svg viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' +
    '<path d="M24 6.5c-5.5 0-10 3.7-11.2 8.8-.3 1.3-.2 2.5.2 3.6.7 2 0 4.3-1.5 5.9-1.6 1.8-2.2 4.4-1.6 6.8.7 3.1 3.3 5.4 6.5 5.9 2.4.4 4.9-.3 6.9-1.9.7-.5 1.5-.8 2.4-.8h.6c.9 0 1.7.3 2.4.8 2 1.6 4.5 2.3 6.9 1.9 3.2-.5 5.8-2.8 6.5-5.9.6-2.4 0-5-1.6-6.8-1.5-1.6-2.2-3.9-1.5-5.9.4-1.1.5-2.3.2-3.6C34 10.2 29.5 6.5 24 6.5z" fill="currentColor" fill-opacity=".15"/>' +
    '<path d="M24 6.8v30.4" stroke-width="1.3" opacity=".6"/>' +
    '<path d="M18.4 11.6c-1.7 2.1-1.6 4.8.3 6.7" stroke-width="1.2" opacity=".6"/>' +
    '<path d="M14.8 22c-.6 3.1.3 6.2 2.5 8.5" stroke-width="1.2" opacity=".6"/>' +
    '<path d="M29.6 11.6c1.7 2.1 1.6 4.8-.3 6.7" stroke-width="1.2" opacity=".6"/>' +
    '<path d="M33.2 22c.6 3.1-.3 6.2-2.5 8.5" stroke-width="1.2" opacity=".6"/></svg>',
  // 肾：豆形肾体 + 肾门动静脉 + 输尿管
  renal: '<svg viewBox="0 0 48 48" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' +
    '<path d="M19 19A 12.6 12.6 0 0 1 39.6 23A 12.6 12.6 0 0 1 24 35A 8.5 8.5 0 0 1 19 19Z" fill="currentColor" fill-opacity=".15"/>' +
    '<path d="M20.4 24.4h-5.6" stroke-width="1.5"/>' +
    '<path d="M14.8 24.4c-2.2 0-3.7-1.4-3.7-3.2" stroke-width="1.4"/>' +
    '<path d="M20.6 27.8h-4.4" stroke-width="1.5"/>' +
    '<path d="M16.2 27.8c-2 .5-3.2 2-3.1 3.9" stroke-width="1.4"/>' +
    '<path d="M21 30c-.8 4.3-2 7.5-4.2 10.1" stroke-width="1.5"/></svg>'
}

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
    console.warn('文书打开失败：', e.message || e)
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

/* ===== 全局基调（与 APACHE II 评分页统一：背景/文字/主色） ===== */
.sofa-page {
  min-height: 100vh;
  background: #f0f2f5;
  font-family: "Microsoft YaHei", "PingFang SC", "Segoe UI", Arial, sans-serif;
  color: #303133;
  font-size: 14px;
  -webkit-font-smoothing: antialiased;
}

/* ===== 页面骨架：左侧记录栏 + 右侧主区 ===== */
.app { display: flex; min-height: 100vh; align-items: stretch; }
.side { width: 280px; min-width: 280px; display: flex; flex-direction: column; background: #fff; border-right: 1px solid #e4e7ed; height: 100vh; position: sticky; top: 0; }
.side-head { height: 52px; padding: 0 16px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #ebeef5; font-weight: 600; font-size: 15px; }
.side-head .count { background: #ecf5ff; color: #409eff; padding: 2px 8px; border-radius: 10px; font-size: 12px; font-weight: 600; }
.side-add { padding: 10px; border-bottom: 1px solid #ebeef5; }
.add-record-btn { width: 100%; height: 36px; background: linear-gradient(135deg, #409eff, #66b1ff); color: #fff; border: none; border-radius: 6px; font-size: 14px; font-weight: 600; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 6px; box-shadow: 0 2px 6px rgba(64,158,255,0.3); }
.add-record-btn:hover { background: linear-gradient(135deg, #66b1ff, #409eff); }
.add-record-btn .plus { font-size: 18px; line-height: 1; }
.record-list { flex: 1; overflow-y: auto; padding: 8px; }
.record-item { padding: 12px; margin-bottom: 8px; border: 1px solid #ebeef5; border-radius: 6px; cursor: pointer; transition: all .2s; background: #fff; }
.record-item:hover { border-color: #c6e2ff; background: #f5f9ff; }
.record-item.active { border-color: #409eff; background: #ecf5ff; }
.record-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.record-time { font-size: 13px; color: #606266; font-weight: 500; }
.record-score { font-size: 20px; font-weight: 700; padding: 2px 10px; border-radius: 4px; }
.record-score.green { background: #f0f9eb; color: #67c23a; }
.record-score.orange { background: #fdf6ec; color: #e6a23c; }
.record-score.red { background: #fef0f0; color: #f56c6c; }
.record-meta { font-size: 12px; color: #909399; }
.record-meta span { margin-right: 8px; }
.record-tag { display: inline-block; padding: 1px 6px; border-radius: 3px; font-size: 11px; background: #f4f4f5; color: #909399; }
.record-tag.auto { background: #ecf5ff; color: #409eff; }
.record-tag.manual { background: #fdf6ec; color: #e6a23c; }
.record-tag.reviewed { background: #e1f3d8; color: #389e0d; }
.record-tag.pdf-tag { background: #e1f3d8; color: #389e0d; cursor: pointer; }
.record-tag.pdf-tag:hover { background: #d3f0c0; }
.record-tag.review-tag { background: #ecf5ff; color: #409eff; cursor: pointer; }
.record-tag.review-tag:hover { background: #d9ecff; }
.record-tag.del-tag { cursor: pointer; }
.record-tag.del-tag:hover { background: #fef0f0; color: #f56c6c; }
.record-empty { text-align: center; color: #c0c4cc; font-size: 13px; padding: 40px 0; }

.main { flex: 1; min-width: 0; padding: 12px 18px 24px; }

/* ===== 患者信息行 ===== */
.patient-row { display: flex; align-items: center; gap: 16px; margin-bottom: 11px; flex-wrap: wrap; }
.patient-row .bed-tag { background: #409eff; color: #fff; font-size: 13px; font-weight: 500; padding: 2px 10px; border-radius: 4px; }
.patient-row .patient-name { font-size: 18px; font-weight: 700; color: #303133; }
.patient-meta { font-size: 13px; color: #606266; }
.patient-meta b { color: #303133; font-weight: 600; }
.patient-row .resp-flag { font-size: 13px; padding: 3px 11px; border-radius: 14px; background: #f4f4f5; color: #909399; }
.patient-row .resp-flag.on { background: #ecf5ff; color: #409eff; }
.btn-trend { margin-left: auto; height: 32px; border: 1px solid #dcdfe6; background: #fff; color: #606266; font-size: 13px; padding: 0 14px; border-radius: 4px; cursor: pointer; transition: all .2s; }
.btn-trend:hover { color: #409eff; border-color: #c6e2ff; background: #ecf5ff; }

/* ===== 总览条：总分卡 + 6 器官小卡 ===== */
.overview { display: flex; gap: 12px; background: #fafafa; border: 1px solid #ebeef5; border-radius: 6px; padding: 12px; margin-top: 12px; margin-bottom: 12px; }
.ov-total { width: 200px; flex-shrink: 0; border-radius: 8px; color: #fff; padding: 12px 14px; background: linear-gradient(135deg,#409eff,#66b1ff); box-shadow: 0 2px 6px rgba(64,158,255,.3); display: flex; flex-direction: column; box-sizing: border-box; }
.ov-total .t-label { font-size: 12px; opacity: .92; }
.ov-total .t-num { font-size: 36px; font-weight: 700; line-height: 1.2; margin: 0 0 6px; }
.ov-total .t-pill { align-self: flex-start; font-size: 12px; color: #fff; background: rgba(255,255,255,.20); border: 1px solid rgba(255,255,255,.28); padding: 3px 11px; border-radius: 16px; }
.ov-total .t-delta { margin-top: 7px; font-size: 12px; opacity: .95; }
.ov-total .t-delta b { color: #FFE7A8; }
.ov-total .t-foot { margin-top: auto; padding-top: 6px; font-size: 12px; opacity: .82; }
.ov-organs { flex: 1; display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; min-width: 0; }
.ov-card { background: #fff; border: 1px solid #ebeef5; border-radius: 6px; padding: 12px 8px 0; display: flex; flex-direction: column; align-items: center; gap: 6px; min-width: 0; box-sizing: border-box; }
.ov-pic { width: 46px; height: 46px; display: inline-flex; align-items: center; justify-content: center; }
.ov-pic svg { width: 100%; height: 100%; display: block; }
.ov-pic.green { color: #67c23a; }
.ov-pic.blue { color: #409eff; }
.ov-pic.orange { color: #e6a23c; }
.ov-pic.red { color: #f56c6c; }
.ov-name { font-size: 13px; font-weight: 500; color: #606266; max-width: 100%; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.ov-foot { margin-top: auto; width: 100%; display: flex; align-items: baseline; justify-content: center; gap: 2px; padding: 7px 0 9px; border-top: 1px dashed #eef1f6; }
.ov-unit { font-size: 11px; color: #909399; }
.ov-num { font-size: 22px; font-weight: 700; line-height: 1; color: #303133; }
.ov-num.green { color: #67c23a; }
.ov-num.blue { color: #409eff; }
.ov-num.orange { color: #e6a23c; }
.ov-num.red { color: #f56c6c; }

/* ===== 取数时间范围 ===== */
.range-row { display: flex; align-items: center; gap: 10px; margin: 12px 0 11px; flex-wrap: wrap; }
.range-row .lbl { font-size: 14px; color: #606266; font-weight: 600; }
.range-row .sel { height: 32px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; padding: 0 10px; font-size: 13px; color: #303133; outline: none; }
.range-row .sel:focus { border-color: #409eff; }
.range-row .tilde { color: #c0c4cc; }
.rbtn { height: 32px; border-radius: 4px; padding: 0 14px; font-size: 13px; cursor: pointer; background: #fff; color: #606266; border: 1px solid #dcdfe6; transition: all .2s; }
.rbtn:hover { color: #409eff; border-color: #c6e2ff; background: #ecf5ff; }
.rbtn.active, .rbtn.solid { background: #409eff; color: #fff; border-color: #409eff; font-weight: 600; }
.rbtn.active:hover, .rbtn.solid:hover { background: #66b1ff; border-color: #66b1ff; color: #fff; }
.range-logic { margin-left: auto; font-size: 13px; color: #909399; }

/* ===== 器官功能评分表 ===== */
.table-panel { background: #fff; border-radius: 8px; border: 1px solid #ebeef5; padding: 11px 14px 4px; }
.table-title { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 9px; }
.table-title::before { content: ''; width: 3px; height: 15px; border-radius: 2px; background: #409eff; }
table.score { width: 100%; border-collapse: collapse; table-layout: fixed; }
table.score th, table.score td { border: 1px solid #ebeef5; text-align: center; padding: 0; height: 38px; font-size: 13px; vertical-align: middle; color: #606266; }
table.score thead th { background: #f5f8fc; color: #728096; font-weight: 600; height: 36px; }
table.score td.sys { font-weight: 600; color: #303133; background: #fafafa; font-size: 13px; }
table.score td.ind { text-align: left; padding-left: 13px; color: #303133; background: #fff; }
table.score td.dim { color: #c0c4cc; }
table.score td.cell-hit { background: #ecf5ff; color: #409eff; font-weight: 600; }
table.score .inp { display: flex; align-items: center; justify-content: space-between; height: 30px; margin: 0 auto; width: 88%; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; padding: 0 10px; color: #303133; font-size: 13px; font-weight: 500; box-sizing: border-box; overflow: hidden; white-space: nowrap; }
table.score .inp.drop { background: #fafafa; border-color: #dcdfe6; }
table.score .inp .caret { color: #c0c4cc; font-size: 12px; flex-shrink: 0; }
table.score td.act { padding: 4px 6px; height: auto; }
.btn-src { min-width: 50px; height: 26px; padding: 0 8px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; color: #606266; font-size: 12px; cursor: pointer; transition: all .2s; }
.btn-src:hover { color: #409eff; border-color: #c6e2ff; background: #ecf5ff; }
.score-tag { display: inline-flex; align-items: center; justify-content: center; min-width: 46px; height: 30px; padding: 0 12px; border-radius: 4px; background: #ecf5ff; color: #409eff; font-weight: 600; font-size: 13px; box-sizing: border-box; }

.tip { display: flex; align-items: center; gap: 8px; margin: 10px 0 8px; padding: 9px 13px; background: #f7fbff; border: 1px solid #edf2f8; border-radius: 6px; color: #40546c; font-size: 13px; line-height: 1.6; }
.tip .ico { flex: 0 0 18px; width: 18px; height: 18px; border-radius: 50%; background: #409eff; color: #fff; display: inline-flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; flex-shrink: 0; box-sizing: border-box; }
.tip b { color: #303133; }

/* ===== 底部操作行 ===== */
.footer-bar { display: flex; align-items: center; gap: 10px; margin-top: 8px; flex-wrap: wrap; }
.footer-info { font-size: 13px; color: #909399; line-height: 1.7; flex: 0 0 auto; }
.footer-note { flex: 1; min-width: 200px; height: 32px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; padding: 0 10px; font-size: 13px; color: #303133; outline: none; }
.footer-note:focus { border-color: #409eff; }

.btn { height: 32px; padding: 0 14px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; color: #606266; font-size: 13px; cursor: pointer; display: inline-flex; align-items: center; gap: 5px; transition: all .2s; }
.btn:hover { color: #409eff; border-color: #c6e2ff; background: #ecf5ff; }
.btn-primary { background: #409eff; color: #fff; border-color: #409eff; font-weight: 600; }
.btn-primary:hover { background: #66b1ff; color: #fff; border-color: #66b1ff; }
.btn-success { background: #67c23a; color: #fff; border-color: #67c23a; font-weight: 600; }
.btn-success:hover { background: #85ce61; color: #fff; border-color: #85ce61; }
.btn:disabled { opacity: .6; cursor: not-allowed; }
.btn-primary:disabled { opacity: .6; cursor: not-allowed; }
.btn-text { border: none; background: transparent; color: #409eff; padding: 0 6px; cursor: pointer; }
.btn-text.danger { color: #f56c6c; }

.modal-mask { position: fixed; inset: 0; background: rgba(0,0,0,.4); display: flex; align-items: center; justify-content: center; z-index: 100; }
.modal { width: min(760px, calc(100vw - 40px)); max-height: calc(100vh - 60px); overflow: auto; background: #fff; border-radius: 8px; }
.modal-head { display: flex; align-items: center; justify-content: space-between; padding: 14px 20px; border-bottom: 1px solid #ebeef5; }
.modal-head h3 { margin: 0; font-size: 16px; font-weight: 600; color: #303133; }
.modal-close { border: none; background: transparent; font-size: 20px; cursor: pointer; color: #909399; }
.modal-body { padding: 16px 20px; }
.modal-foot { padding: 12px 20px; border-top: 1px solid #ebeef5; text-align: right; }
.src-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 14px; }
.src-card { padding: 12px; background: #f5f7fa; border: 1px solid #ebeef5; border-radius: 8px; }
.src-label { font-size: 12px; color: #909399; }
.src-value { font-size: 20px; font-weight: 700; color: #409eff; margin-top: 4px; }
.src-section { font-size: 13px; color: #606266; margin-bottom: 6px; }
.src-title { margin: 12px 0 6px; font-weight: 600; font-size: 13px; color: #303133; border-left: 3px solid #409eff; padding-left: 8px; }
.src-json { background: #f5f7fa; border: 1px solid #ebeef5; border-radius: 6px; padding: 10px; font-size: 12px; color: #606266; max-height: 220px; overflow: auto; }
.src-desc { font-size: 13px; color: #606266; line-height: 1.8; }
</style>
