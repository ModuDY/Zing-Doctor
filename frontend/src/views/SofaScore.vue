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
              <!-- 归档：待归档→点击推送到院方归档接口→已归档；已归档再点只撤销标记（不调接口） -->
              <span :class="['record-tag', 'archive-tag', r.archiveStatus === 1 ? 'done' : 'todo']"
                    @click.stop="toggleArchive(r)">{{ r.archiveStatus === 1 ? '已归档' : '待归档' }}</span>
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
      <span class="bed-tag">{{ patient.departCode || patient.bedCode || '—' }}</span>
      <span class="patient-name">{{ patient.name || '—' }}</span>
      <span class="patient-meta"><b>{{ patient.gender || '—' }}</b> / {{ patient.age || '—' }}{{ patient.ageUnit || '岁' }}</span>
      <span class="patient-meta">住院号：<b>{{ patient.inHospitalNo || inHospitalNo || '—' }}</b></span>
      <span class="patient-meta">入科时间：<b>{{ fmtTime(patient.inDepartTime) }}</b></span>
      <span :class="['resp-flag', { on: inputs.respSupport === 1 }]">{{ inputs.respSupport === 1 ? '有创呼吸支持' : '无呼吸支持' }}</span>
    </div>
      <!-- ===== 总览条：SOFA 总分 + 6 器官当前分值 ===== -->
      <div class="overview">
        <div class="ov-total">
          <div class="t-label">SOFA 总分</div>
          <div class="t-num">{{ totalScore }}</div>
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
        <input class="sel" :class="{ custom: activeRange === 'custom' }" type="datetime-local" v-model="rangeStart" @change="onRangeInput">
        <span class="tilde">至</span>
        <input class="sel" :class="{ custom: activeRange === 'custom' }" type="datetime-local" v-model="rangeEnd" @change="onRangeInput">
        <span class="range-presets">
          <button :class="['rbtn', { active: activeRange === 24 }]" @click="quickRange(24)">24小时</button>
          <button :class="['rbtn', { active: activeRange === 48 }]" @click="quickRange(48)">48小时</button>
          <button :class="['rbtn', { active: activeRange === 'admission_after' }]" @click="setAdmissionRange(true)">入科后24h</button>
          <button :class="['rbtn', { active: activeRange === 'admission_before' }]" @click="setAdmissionRange(false)">入科前24h</button>
        </span>
        <button class="rbtn solid" :disabled="loading" @click="loadAssessment">{{ loading ? '取数中…' : '自动获取并计算' }}</button>
        <span class="range-logic">取数逻辑：范围内最差值（偏离正常最远）</span>
      </div>

      <!-- ===== 器官功能评分表 ===== -->
      <div class="table-panel">
        <div class="table-title">
          器官功能评分表
          <span class="tt-hint">输入值可直接修改，改后自动重算该器官分值与总分</span>
          <button v-if="manualEditCount > 0" class="btn-mini" @click="resetOverridesFromItems">恢复自动值</button>
        </div>
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
              <td>
                <select class="inp" :class="{ dirty: touched('respSupport') }" :value="inputs.respSupport"
                        :title="inputHint('resp', '呼吸机支持')" @change="onSupportChange">
                  <option :value="0">否</option>
                  <option :value="1">是</option>
                </select>
              </td>
              <td class="act" rowspan="2"><button class="btn-src" @click="openSource(itemOf('resp'))">来源</button></td>
              <td class="dim">—</td><td class="dim">—</td><td class="dim">—</td>
              <td>{{ inputs.respSupport === 1 ? '是' : '' }}</td>
              <td>{{ inputs.respSupport === 1 ? '是' : '' }}</td>
            </tr>
            <tr>
              <td class="ind">PaO₂/F.iO₂ (mmHg)</td>
              <td>
                <input class="inp" :class="{ dirty: touched('pf') }" type="number" step="0.1" min="0" placeholder="无数据"
                       :title="inputHint('resp', 'PaO₂/F.iO₂')" :value="inputs.pf" @input="onNumInput('pf', $event)">
              </td>
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
              <td>
                <input class="inp" :class="{ dirty: touched('plt') }" type="number" step="1" min="0" placeholder="无数据"
                       :title="inputHint('coag', '血小板 (10⁹/L)')" :value="inputs.plt" @input="onNumInput('plt', $event)">
              </td>
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
              <td>
                <input class="inp" :class="{ dirty: touched('bili') }" type="number" step="0.1" min="0" placeholder="无数据"
                       :title="inputHint('liver', '总胆红素 (μmol/L)')" :value="inputs.bili" @input="onNumInput('bili', $event)">
              </td>
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
              <td>
                <input class="inp" :class="{ dirty: touched('map') }" type="number" step="1" min="0" placeholder="无数据"
                       :title="inputHint('cardio', '平均动脉压 (mmHg)')" :value="inputs.map" @input="onNumInput('map', $event)">
              </td>
              <td class="act" rowspan="5"><button class="btn-src" @click="openSource(itemOf('cardio'))">来源</button></td>
              <td :class="cellCls(hitRaw('cardio', 'mapScore', 0))">≥70</td>
              <td :class="cellCls(hitRaw('cardio', 'mapScore', 1))">&lt;70</td>
              <td class="dim">—</td><td class="dim">—</td><td class="dim">—</td>
            </tr>
            <tr>
              <td class="ind">多巴胺 (μg·kg⁻¹·min⁻¹)</td>
              <td>
                <input class="inp" :class="{ dirty: touched('dopamine') }" type="number" step="0.01" min="0" placeholder="未使用"
                       :title="drugHint('多巴胺')" :value="inputs.dopamine" @input="onNumInput('dopamine', $event)">
              </td>
              <td class="dim">—</td><td class="dim">—</td>
              <td :class="cellCls(vasoHit('多巴胺', 2))">≤5</td>
              <td :class="cellCls(vasoHit('多巴胺', 3))">5~15</td>
              <td :class="cellCls(vasoHit('多巴胺', 4))">&gt;15</td>
            </tr>
            <tr>
              <td class="ind">肾上腺素 (μg·kg⁻¹·min⁻¹)</td>
              <td>
                <input class="inp" :class="{ dirty: touched('epinephrine') }" type="number" step="0.01" min="0" placeholder="未使用"
                       :title="drugHint('肾上腺素')" :value="inputs.epinephrine" @input="onNumInput('epinephrine', $event)">
              </td>
              <td class="dim">—</td><td class="dim">—</td><td class="dim">—</td>
              <td :class="cellCls(vasoHit('肾上腺素', 3))">≤0.1</td>
              <td :class="cellCls(vasoHit('肾上腺素', 4))">&gt;0.1</td>
            </tr>
            <tr>
              <td class="ind">去甲肾上腺素 (μg·kg⁻¹·min⁻¹)</td>
              <td>
                <input class="inp" :class="{ dirty: touched('norepinephrine') }" type="number" step="0.01" min="0" placeholder="未使用"
                       :title="drugHint('去甲肾上腺素')" :value="inputs.norepinephrine" @input="onNumInput('norepinephrine', $event)">
              </td>
              <td class="dim">—</td><td class="dim">—</td><td class="dim">—</td>
              <td :class="cellCls(vasoHit('去甲肾上腺素', 3))">≤0.1</td>
              <td :class="cellCls(vasoHit('去甲肾上腺素', 4))">&gt;0.1</td>
            </tr>
            <tr>
              <td class="ind">多巴酚丁胺 (μg·kg⁻¹·min⁻¹)</td>
              <td>
                <input class="inp" :class="{ dirty: touched('dobutamine') }" type="number" step="0.01" min="0" placeholder="未使用"
                       :title="drugHint('多巴酚丁胺')" :value="inputs.dobutamine" @input="onNumInput('dobutamine', $event)">
              </td>
              <td class="dim">—</td>
              <td :class="cellCls(vasoHit('多巴酚丁胺', 2))">任何剂量</td>
              <td class="dim">—</td><td class="dim">—</td>
            </tr>
            <!-- 神经 -->
            <tr>
              <td class="sys">神经系统</td>
              <td class="ind">GCS 评分</td>
              <td>
                <input class="inp" :class="{ dirty: touched('gcs') }" type="number" step="1" min="3" max="15" placeholder="无数据"
                       :title="gcsHint" :value="inputs.gcs" @input="onNumInput('gcs', $event)">
                <button class="gcs-open" @click="openGcsModal">同步/手录 GCS</button>
              </td>
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
              <td>
                <input class="inp" :class="{ dirty: touched('creatinine') }" type="number" step="0.1" min="0" placeholder="无数据"
                       :title="inputHint('renal', '肌酐 (μmol/L)')" :value="inputs.creatinine" @input="onNumInput('creatinine', $event)">
              </td>
              <td class="act" rowspan="2"><button class="btn-src" @click="openSource(itemOf('renal'))">来源</button></td>
              <td :class="cellCls(hitRaw('renal', 'creatinineScore', 0))">&lt;106</td>
              <td :class="cellCls(hitRaw('renal', 'creatinineScore', 1))">≤176</td>
              <td :class="cellCls(hitRaw('renal', 'creatinineScore', 2))">≤308</td>
              <td :class="cellCls(hitRaw('renal', 'creatinineScore', 3))">≤442</td>
              <td :class="cellCls(hitRaw('renal', 'creatinineScore', 4))">&gt;442</td>
            </tr>
            <tr>
              <td class="ind">24h 尿量 (ml)</td>
              <td>
                <input class="inp" :class="{ dirty: touched('urine') }" type="number" step="10" min="0" :placeholder="urinePh"
                       :title="inputHint('renal', '24h 尿量 (ml)')" :value="inputs.urine" @input="onNumInput('urine', $event)">
              </td>
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
        <!-- 手工修正提示：让医生一眼看到哪些项被改过，以及可一键回退 -->
        <div class="tip tip-manual" v-if="manualEditCount > 0">
          <span class="ico" style="background:#e6a23c">✎</span>
          <span>
            <b>手工修正：</b>已修改 {{ manualEditCount }} 项输入值（{{ manualLabels.join('、') }}），相关器官分值与总分已按 SOFA 标准重算；该内容会随记录一并留痕。
            <button class="btn-mini" style="margin-left:8px" @click="resetOverridesFromItems">恢复自动值</button>
          </span>
        </div>
      </div>

      <!-- ===== 底部操作行（与 APACHE II 一致：预览文书 / 删除 / 保存评分） ===== -->
      <div class="footer-bar">
        <div class="footer-info">
          评分医师：{{ realname || username || '—' }}<br>
          创建时间：{{ fmtTimeNow() }}
        </div>
        <input class="footer-note" v-model="doctorRemark" placeholder="备注（可选）" />
        <button class="btn" :disabled="reportGenerating" @click="openReport">
          {{ reportGenerating ? '生成中…' : '预览文书' }}
        </button>
        <button v-if="currentRecordId" class="btn btn-danger" @click="deleteCurrentRecord">删除</button>
        <button class="btn btn-success" :disabled="saving" @click="saveRecord">
          {{ saving ? '保存中…' : '保存评分' }}
        </button>
      </div>
      </main>
    </div>

    <!-- GCS 弹窗（与 APACHE II 同款：自动同步最新 / 手动选择系统记录 / 手工新建评估） -->
    <div class="modal-mask" v-if="showGcsModal" @click.self="showGcsModal = false">
      <div class="modal gcs-modal">
        <div class="modal-head">
          <h3>GCS 评分（神经系统）</h3>
          <button class="modal-close" @click="showGcsModal = false">×</button>
        </div>
        <div class="modal-body">
          <div class="gcs-tabs">
            <button :class="['gcs-tab', { active: gcsTab === 'sys' }]" @click="gcsTab = 'sys'">选择系统已有记录</button>
            <button :class="['gcs-tab', { active: gcsTab === 'manual' }]" @click="gcsTab = 'manual'">手工新建评估</button>
          </div>

          <template v-if="gcsTab === 'sys'">
            <div class="gcs-sys-bar">
              <div class="gcs-sys-tip">记录来自重症系统 GCS 评估文书（Z_ICU_GCS）。选定一条后，其睁眼 / 言语 / 运动三项合计将填入 SOFA 神经系统评分。</div>
              <button class="gcs-mini-primary" :disabled="gcsSyncLoading" @click="syncLatestGcs">
                {{ gcsSyncLoading ? '同步中…' : '自动同步最新记录' }}
              </button>
            </div>
            <div class="gcs-sys-table">
              <table>
                <thead>
                  <tr><th>评分时间</th><th>记录者</th><th>GCS</th><th>睁眼(E)</th><th>言语(V)</th><th>运动(M)</th><th>选择</th></tr>
                </thead>
                <tbody>
                  <tr v-if="gcsSyncLoading"><td colspan="7" class="gcs-empty">正在拉取重症系统记录…</td></tr>
                  <tr v-else-if="!systemGcsList.length"><td colspan="7" class="gcs-empty">重症系统暂无该患者的 GCS 评估记录，可切换到「手工新建评估」录入</td></tr>
                  <template v-else>
                    <tr v-for="(rec, i) in systemGcsList" :key="i"
                        :class="{ selected: selectedSysIndex === i }" @click="selectedSysIndex = i">
                      <td>{{ fmtTime(rec.recordTime) }}</td>
                      <td>{{ rec.recordStaffName || '—' }}</td>
                      <td>{{ gcsRowTotal(rec) }}</td>
                      <td>{{ rec.eye === null || rec.eye === undefined ? '—' : rec.eye }}</td>
                      <td>
                        <span v-if="rec.intubated" class="et-tag">ET 插管</span>
                        <span v-else>{{ rec.verbal === null || rec.verbal === undefined ? '—' : rec.verbal }}</span>
                      </td>
                      <td>{{ rec.motor === null || rec.motor === undefined ? '—' : rec.motor }}</td>
                      <td><a class="pick-link" @click.stop="selectedSysIndex = i">{{ selectedSysIndex === i ? '已选' : '选择' }}</a></td>
                    </tr>
                  </template>
                </tbody>
              </table>
            </div>
          </template>

          <template v-else>
            <div class="gcs-total-bar">
              <span>GCS 总分</span><strong>{{ gcsModalTotalText }}</strong>
              <em v-if="gcsModalComplete">神经系统得分 {{ gcsModalScore }} 分</em>
              <em v-else class="gcs-warn">请在睁眼 / 言语 / 运动三项中各选一档，评全后自动计分</em>
            </div>
            <div class="gcs-row">
              <div class="gcs-row-title">睁眼反应（E）</div>
              <div class="gcs-options">
                <button v-for="opt in GCS_EYE_OPTIONS" :key="opt.value" :class="{ active: gcsForm.eye === opt.value }"
                        @click="gcsForm.eye = opt.value">{{ opt.value }} {{ opt.label }}</button>
              </div>
            </div>
            <div class="gcs-row">
              <div class="gcs-row-title">言语反应（V）</div>
              <div class="gcs-options">
                <button v-for="opt in GCS_VERBAL_OPTIONS" :key="opt.value" :class="{ active: gcsForm.verbal === opt.value }"
                        @click="gcsForm.verbal = opt.value">{{ opt.value }} {{ opt.label }}</button>
              </div>
            </div>
            <div class="gcs-row">
              <div class="gcs-row-title">运动反应（M）</div>
              <div class="gcs-options">
                <button v-for="opt in GCS_MOTOR_OPTIONS" :key="opt.value" :class="{ active: gcsForm.motor === opt.value }"
                        @click="gcsForm.motor = opt.value">{{ opt.value }} {{ opt.label }}</button>
              </div>
            </div>
          </template>
        </div>
        <div class="modal-foot">
          <button class="btn" @click="showGcsModal = false">取消</button>
          <button v-if="gcsTab === 'sys'" class="btn btn-primary" :disabled="selectedSysIndex < 0" @click="confirmPickSystemGcs">确定选择</button>
          <button v-else class="btn btn-primary" @click="confirmGcs">确认评估</button>
        </div>
      </div>
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
          <div class="src-title">数据趋势（当前取数范围）<span class="src-count" v-if="trendCount > 0">共 {{ trendCount }} 个点</span></div>
          <div ref="trendChartRef" style="width:100%;height:200px;margin-bottom:6px;"></div>
          <div class="src-note src-note-warn" v-if="trendFallback">{{ trendFallback }}</div>
          <div class="src-note" v-if="sourceItem && (sourceItem.key === 'liver' || sourceItem.key === 'renal')">
            注：趋势图按 mg/dL 展示（与评分取值口径一致），表格「输入值」为 μmol/L。
          </div>
          <div class="src-note" v-if="sourceItem && sourceItem.key === 'cardio'">
            注：循环趋势仅展示 MAP 序列；血管活性药剂量见上方「当前值」。
          </div>
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

    <!-- 评分文书预览弹窗（与 APACHE II 一致：打印 / 导出 PDF / 关闭） -->
    <div class="modal-mask" v-if="showReportModal" @click.self="showReportModal = false">
      <div class="modal report-modal">
        <div class="modal-head">
          <h3>SOFA 评分文书预览</h3>
          <button class="modal-close" @click="showReportModal = false">×</button>
        </div>
        <div class="modal-body report-scroll">
          <div ref="reportViewRef" class="report-view-host"></div>
        </div>
        <div class="modal-foot">
          <button class="btn" @click="printReport">打印</button>
          <button class="btn btn-primary" @click="downloadReportPdf">导出 PDF</button>
          <button class="btn" @click="showReportModal = false">关闭</button>
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
                <td style="border:1px solid #000;padding:4px;text-align:center;">{{ inputs.respSupport === 1 ? '是' : '' }}</td>
                <td style="border:1px solid #000;padding:4px;text-align:center;">{{ inputs.respSupport === 1 ? '是' : '' }}</td>
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
          <!-- 评分医师（含电子签名）/ 评分时间：右下角一行（导出 PDF 可见） -->
          <div style="display:flex;justify-content:flex-end;align-items:center;font-size:12px;margin-top:12px;">
            <div style="margin-right:36px;display:flex;align-items:center;">
              <span>评分医师：</span>
              <img v-if="doctorSignature" :src="doctorSignature" alt="电子签名" style="height:38px;" />
              <span v-else>{{ realname || username || '—' }}</span>
            </div>
            <div>评分时间：{{ reportTime || fmtTimeNow() }}</div>
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
  fetchSofaTrend, fetchSofaRecordPdf, attachSofaRecordPdf, fetchSofaGcsRecords,
  pushSofaArchive, unmarkSofaArchive
} from '../api/sofa'
import { isExternalMode } from '../utils/external'
import { useStaffSignature } from '../utils/staffSignature'

const HOSPITAL_LOGO = '/logo.png'  /* 院徽静态资源：frontend/public/logo.png，构建后随 dist 输出 */

const route = useRoute()
const isExternal = isExternalMode()

const inHospitalNo = ref(route.query.inHospitalNo || '')
const patientId = ref(route.query.patientId || '')
const username = ref(route.query.username || '')
const realname = ref(route.query.realname || '')

// 文书 PDF / 文书预览弹窗 / 来源趋势图
const reportRef = ref(null)
const reportViewRef = ref(null)
const reportTime = ref('')
const showReportModal = ref(false)
const reportGenerating = ref(false)
const trendChartRef = ref(null)
let trendChart = null

// 评分医师电子签名（按工号 username 反查 ICU CA 库；取不到则为空，文书不显示签名）
const { signatureSrc: doctorSignature, load: loadDoctorSignature } = useStaffSignature()
// 文书用到的汇总值（GCS / 尿量），随评估结果回填
const gcsTotal = ref(null)
const gcsDetail = ref('')
const urineMl = ref(null)

// GCS 弹窗（与 APACHE II 同款：自动同步最新 / 手动选择系统记录 / 手工新建评估）
const showGcsModal = ref(false)
const gcsTab = ref('sys')
const systemGcsList = ref([])
const gcsSyncLoading = ref(false)
const selectedSysIndex = ref(-1)
/** 手工新建评估的临时选择（确认后才写入 inputs.gcs，取消不影响已填值） */
const gcsForm = reactive({ eye: null, verbal: null, motor: null })
/** 本次由弹窗带入的 E/V/M 文案（与总分一致时才作为落库明细） */
const gcsLocalDetail = ref('')

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
/** 当前选中的快捷区间（24/48 小时 或 入科后/入科前 24h，手动改时间后为 custom） */
const activeRange = ref(null)
/** 手改时间后尚未重新取数的标记（仅作提示，不阻断操作） */
const rangeDirty = ref(false)
const showSource = ref(false)
const sourceItem = ref(null)
/** 来源弹窗趋势图点数（>0 时在标题上提示） */
const trendCount = ref(0)
/** 趋势范围内无记录、回退展示「本次评分取值点」时的提示文案 */
const trendFallback = ref('')

// =============== 手工修正：输入值可编辑 + 前端按 SOFA 标准重算 ===============

/**
 * 可手工编辑的输入值，初始值 = 自动取数结果。
 * 医生改动任意一项后，相关器官分值 / 命中列 / 总分立即按 SOFA 标准重算，
 * 保存时以重算结果落库；改回自动值则自动恢复自动分（不留手工痕迹）。
 *
 * ⚠ 计分规则必须与后端 SofaServiceImpl 保持一致，改一处必须同步改两处。
 */
const inputs = reactive({
  respSupport: 0,   // 呼吸机支持 0/1
  pf: null,         // PaO2/FiO2 mmHg
  plt: null,        // 血小板 10⁹/L
  bili: null,       // 总胆红素 μmol/L
  map: null,        // 平均动脉压 mmHg
  dopamine: null,   // 多巴胺 μg·kg⁻¹·min⁻¹
  epinephrine: null,      // 肾上腺素
  norepinephrine: null,   // 去甲肾上腺素
  dobutamine: null,       // 多巴酚丁胺
  gcs: null,        // GCS 总分
  creatinine: null, // 肌酐 μmol/L
  urine: null       // 24h 尿量 ml
})

/** 自动取数结果快照（JSON），用于判断哪些项被手工改过 */
const autoSnapshot = ref('{}')
/** 子项分（MAP/肌酐/尿量）与逐支血管活性药分值，驱动命中列高亮 */
const subScores = reactive({ mapScore: null, vasoScore: null, creatinineScore: null, urineScore: null })
const DRUG_NAMES = ['多巴胺', '肾上腺素', '去甲肾上腺素', '多巴酚丁胺']
const DRUG_FIELD = { 多巴胺: 'dopamine', 肾上腺素: 'epinephrine', 去甲肾上腺素: 'norepinephrine', 多巴酚丁胺: 'dobutamine' }
const drugScores = reactive({ 多巴胺: null, 肾上腺素: null, 去甲肾上腺素: null, 多巴酚丁胺: null })
/** 器官 → 输入项映射（用于手工修正提示与留痕） */
const ORGAN_INPUTS = [
  { label: '呼吸', fields: ['respSupport', 'pf'] },
  { label: '凝血', fields: ['plt'] },
  { label: '肝', fields: ['bili'] },
  { label: '循环', fields: ['map', 'dopamine', 'epinephrine', 'norepinephrine', 'dobutamine'] },
  { label: '神经', fields: ['gcs'] },
  { label: '肾', fields: ['creatinine', 'urine'] }
]

function isEmptyNum(v) {
  return v === null || v === undefined || v === '' || isNaN(Number(v))
}

function numOrNull(v) {
  return isEmptyNum(v) ? null : Number(v)
}

/** 与后端 round(v, 2)（BigDecimal HALF_UP）同口径 */
function round2(v) {
  return Math.round((Number(v) + Number.EPSILON) * 100) / 100
}

/** 归一化后比较，避免 ""/null/数字字符串被判成「改过」 */
function normVal(v) {
  return isEmptyNum(v) ? '' : String(Number(v))
}

function snapshotInputs() {
  const snap = {}
  for (const k of Object.keys(inputs)) snap[k] = inputs[k]
  autoSnapshot.value = JSON.stringify(snap)
}

/** 该项是否被手工改过（与自动取数结果不同即为改过） */
function touched(field) {
  let snap = {}
  try {
    snap = JSON.parse(autoSnapshot.value || '{}')
  } catch (e) {
    snap = {}
  }
  return normVal(snap[field]) !== normVal(inputs[field])
}

const manualEditCount = computed(() => Object.keys(inputs).filter(f => touched(f)).length)
const manualLabels = computed(() => ORGAN_INPUTS.filter(o => o.fields.some(f => touched(f))).map(o => o.label))

// ---------------- SOFA 计分规则（与后端同口径） ----------------

function scoreResp(pf, support) {
  if (isEmptyNum(pf)) return 0
  const v = Number(pf)
  if (v >= 400) return 0
  if (v >= 300) return 1
  if (v >= 200) return 2
  if (v >= 100) return support ? 3 : 2
  return support ? 4 : 2
}

function scoreCoag(plt) {
  if (isEmptyNum(plt)) return 0
  const v = Number(plt)
  if (v >= 150) return 0
  if (v >= 100) return 1
  if (v >= 50) return 2
  if (v >= 20) return 3
  return 4
}

/** 胆红素：入参 μmol/L，按后端 mg/dL 阈值判分（÷17.1 后 <1.2 / <2.0 / <6.0 / <12.0） */
function scoreLiverUmol(umol) {
  if (isEmptyNum(umol)) return 0
  const mgdl = round2(Number(umol) / 17.1)
  if (mgdl < 1.2) return 0
  if (mgdl < 2.0) return 1
  if (mgdl < 6.0) return 2
  if (mgdl < 12.0) return 3
  return 4
}

function scoreCardioMap(map) {
  if (isEmptyNum(map)) return 0
  return Number(map) >= 70 ? 0 : 1
}

/** 单支血管活性药分值；返回 null 表示该药未使用 */
function scoreVasoDose(field, dose) {
  if (isEmptyNum(dose)) return null
  const v = Number(dose)
  if (field === 'dobutamine') return 2
  if (field === 'dopamine') return v <= 5 ? 2 : (v <= 15 ? 3 : 4)
  return v <= 0.1 ? 3 : 4
}

function scoreNeuro(gcs) {
  if (isEmptyNum(gcs)) return 0
  const v = Number(gcs)
  if (v >= 15) return 0
  if (v >= 13) return 1
  if (v >= 10) return 2
  if (v >= 6) return 3
  return 4
}

/** 肌酐：入参 μmol/L，按后端 mg/dL 阈值判分（÷88.4 后 <1.2 / <2.0 / <3.5 / <5.0） */
function scoreRenalCrUmol(umol) {
  if (isEmptyNum(umol)) return 0
  const mgdl = round2(Number(umol) / 88.4)
  if (mgdl < 1.2) return 0
  if (mgdl < 2.0) return 1
  if (mgdl < 3.5) return 2
  if (mgdl < 5.0) return 3
  return 4
}

function scoreRenalUrine(ml) {
  if (isEmptyNum(ml)) return 0
  const v = Number(ml)
  if (v < 200) return 4
  if (v < 500) return 3
  return 0
}

// ---------------- 输入值 ↔ 分值 ----------------

/** 用自动取数结果初始化输入值（载入评估、恢复自动值时调用） */
function initInputsFromItems() {
  const resp = rawOf('resp')
  const coag = rawOf('coag')
  const liver = rawOf('liver')
  const cardio = rawOf('cardio')
  const neuro = rawOf('neuro')
  const renal = rawOf('renal')

  inputs.respSupport = (resp.respiratorySupport === true || resp.respiratorySupport === 1 || respiratorySupport.value === 1) ? 1 : 0
  inputs.pf = numOrNull(resp.oxygenationIndex)
  inputs.plt = numOrNull(coag.platelet)
  inputs.bili = numOrNull(liver.totalBilirubinUmol)
  inputs.map = numOrNull(cardio.map)
  inputs.gcs = numOrNull(neuro.gcsTotal)
  inputs.creatinine = numOrNull(renal.creatinineUmol)
  inputs.urine = numOrNull(renal.urineMl)
  for (const name of DRUG_NAMES) inputs[DRUG_FIELD[name]] = numOrNull(vasoOf(name))
  snapshotInputs()
}

/** 命中列回到「自动结果」：未改动或已改回自动值时使用，保证与后端分完全一致 */
function restoreAutoScores() {
  for (const k of Object.keys(scoreOverrides)) delete scoreOverrides[k]
  for (const it of items.value) scoreOverrides[it.key] = it.score || 0
  const cardio = rawOf('cardio')
  const renal = rawOf('renal')
  subScores.mapScore = numOrNull(cardio.mapScore)
  subScores.vasoScore = numOrNull(cardio.vasoScore)
  subScores.creatinineScore = numOrNull(renal.creatinineScore)
  subScores.urineScore = numOrNull(renal.urineScore)
  for (const name of DRUG_NAMES) {
    const d = vasoDose(name)
    drugScores[name] = d ? Number(d.score || 0) : null
  }
  recalcTotal()
}

/** 按当前输入值重算六项分值、子项命中列与总分 */
function recalcFromInputs() {
  const mapScore = scoreCardioMap(inputs.map)
  let vasoScore = 0
  for (const name of DRUG_NAMES) {
    const s = scoreVasoDose(DRUG_FIELD[name], inputs[DRUG_FIELD[name]])
    drugScores[name] = s
    if (s !== null && s > vasoScore) vasoScore = s
  }
  const crScore = scoreRenalCrUmol(inputs.creatinine)
  const urineScore = scoreRenalUrine(inputs.urine)
  subScores.mapScore = mapScore
  subScores.vasoScore = vasoScore
  subScores.creatinineScore = crScore
  subScores.urineScore = urineScore

  scoreOverrides.resp = scoreResp(inputs.pf, Number(inputs.respSupport) === 1)
  scoreOverrides.coag = scoreCoag(inputs.plt)
  scoreOverrides.liver = scoreLiverUmol(inputs.bili)
  scoreOverrides.cardio = Math.max(mapScore, vasoScore)
  scoreOverrides.neuro = scoreNeuro(inputs.gcs)
  scoreOverrides.renal = Math.max(crScore, urineScore)
  recalcTotal()
}

/** 输入变化后：真改过 → 按输入重算；改回自动值 → 恢复自动分 */
function applyInputChange() {
  if (manualEditCount.value > 0) recalcFromInputs()
  else restoreAutoScores()
}

function onNumInput(field, ev) {
  const raw = ev && ev.target ? ev.target.value : ''
  inputs[field] = isEmptyNum(raw) ? null : Number(raw)
  // 手工直接改总分：弹窗带入的 E/V/M 明细不再对应，清空避免误导
  if (field === 'gcs') gcsLocalDetail.value = ''
  applyInputChange()
}

function onSupportChange(ev) {
  inputs.respSupport = ev && ev.target && Number(ev.target.value) === 1 ? 1 : 0
  applyInputChange()
}

// ---------------- 输入框悬浮提示：自动取值 + 取值时间 ----------------

/** @param {string} key 器官 key；@param {string} label 指标名；@param {string} [autoText] 覆盖自动值文案 */
function inputHint(key, label, autoText) {
  const it = itemOf(key)
  const auto = autoText !== undefined ? autoText : (it && it.valueText ? it.valueText : '无数据')
  const t = it && it.dataTime ? `，取值时间 ${fmtTime(it.dataTime)}` : ''
  return `${label}：自动取值 ${auto}${t}。可直接修改，改后自动重算分值（清空表示无数据）`
}

function drugHint(name) {
  const v = vasoOf(name)
  const auto = (v === null || v === undefined) ? '未使用' : `${num(v, 3)} μg·kg⁻¹·min⁻¹`
  return `${name}：自动取值 ${auto}。填写剂量即表示使用（清空表示未使用），改后自动重算分值`
}

const gcsHint = computed(() => {
  const r = rawOf('neuro')
  const auto = (r.gcsTotal === null || r.gcsTotal === undefined)
    ? '无数据'
    : `${r.gcsTotal} 分${r.gcsDetail ? '（' + r.gcsDetail + '）' : ''}`
  const t = r.checkTime ? `，取值时间 ${fmtTime(r.checkTime)}` : ''
  return `GCS 评分：自动取值 ${auto}${t}。可修改总分（3~15），改后自动重算分值`
})

const urinePh = computed(() => ((rawOf('renal').urineMl === null || rawOf('renal').urineMl === undefined) ? '窗口<24h' : '无数据'))

// ---------------- GCS 弹窗（自动同步最新 / 手动选择 / 手工新建，与 APACHE II 同口径） ----------------

const GCS_EYE_OPTIONS = [
  { value: 4, label: '自发睁眼' },
  { value: 3, label: '语言命令睁眼' },
  { value: 2, label: '疼痛刺激睁眼' },
  { value: 1, label: '无反应' }
]
const GCS_VERBAL_OPTIONS = [
  { value: 5, label: '定向力正常' },
  { value: 4, label: '意识模糊' },
  { value: 3, label: '言语不当' },
  { value: 2, label: '难以理解' },
  { value: 1, label: '无反应' }
]
const GCS_MOTOR_OPTIONS = [
  { value: 6, label: '遵嘱活动' },
  { value: 5, label: '定位疼痛' },
  { value: 4, label: '躲避疼痛' },
  { value: 3, label: '异常屈曲' },
  { value: 2, label: '异常伸展' },
  { value: 1, label: '无反应' }
]

const gcsIn = (v, lo, hi) => { const n = Number(v); return Number.isInteger(n) && n >= lo && n <= hi }
const gcsModalComplete = computed(() => gcsIn(gcsForm.eye, 1, 4) && gcsIn(gcsForm.verbal, 1, 5) && gcsIn(gcsForm.motor, 1, 6))
const gcsModalTotal = computed(() => gcsModalComplete.value
  ? Number(gcsForm.eye) + Number(gcsForm.verbal) + Number(gcsForm.motor) : null)
const gcsModalTotalText = computed(() => (gcsModalComplete.value ? gcsModalTotal.value : '—'))
const gcsModalScore = computed(() => (gcsModalComplete.value ? scoreNeuro(gcsModalTotal.value) : null))

/** 当前生效的 GCS 明细文案：总分与带入值一致时才展示 E/V/M，否则视为手工录入 */
const gcsDetailText = computed(() => {
  if (isEmptyNum(inputs.gcs)) return ''
  const t = Number(inputs.gcs)
  if (gcsLocalDetail.value && Number(gcsTotal.value) === t) return gcsLocalDetail.value
  if (gcsDetail.value && Number(gcsTotal.value) === t) return gcsDetail.value
  return `手工录入 ${t} 分`
})

/** 从 "E4V5M6" 解析三项（用于打开弹窗时预填手工页） */
function parseGcsDetail(text) {
  const m = /E(\d+)V(\d+)M(\d+)/.exec(String(text || ''))
  if (!m) return null
  return { eye: Number(m[1]), verbal: Number(m[2]), motor: Number(m[3]) }
}

function openGcsModal() {
  showGcsModal.value = true
  gcsTab.value = 'sys'
  selectedSysIndex.value = -1
  // 用当前生效值预填手工页，便于在自动/已填总分基础上微调
  const d = parseGcsDetail(gcsLocalDetail.value || gcsDetail.value)
  gcsForm.eye = d ? d.eye : null
  gcsForm.verbal = d ? d.verbal : null
  gcsForm.motor = d ? d.motor : null
  loadSystemGcs(true)
}

/** 拉取重症系统 Z_ICU_GCS 评估记录；silent=true 时无记录不弹提示（打开弹窗自动调用） */
async function loadSystemGcs(silent) {
  const pid = patient.patientId || patientId.value
  if (!pid) {
    if (!silent) ElMessage.warning('未获取到患者信息，无法拉取系统记录')
    return
  }
  gcsSyncLoading.value = true
  try {
    const res = await fetchSofaGcsRecords(pid)
    systemGcsList.value = Array.isArray(res) ? res : []
    // 默认选中最新一条（后端已按评估时间倒序）
    selectedSysIndex.value = systemGcsList.value.length ? 0 : -1
    if (!silent && !systemGcsList.value.length) ElMessage.info('重症系统暂无该患者的 GCS 评估记录')
  } catch (e) {
    if (!silent) console.warn('拉取重症系统GCS记录失败：', e.message || '')
  } finally {
    gcsSyncLoading.value = false
  }
}

/** 表格 GCS 列：非插管三项齐全显示合计，插管显示原始 totalText（如 2+ET+2） */
function gcsRowTotal(rec) {
  if (rec.intubated) return rec.totalText || 'ET'
  if (rec.eye !== null && rec.eye !== undefined && rec.verbal !== null && rec.verbal !== undefined
    && rec.motor !== null && rec.motor !== undefined) {
    return Number(rec.eye) + Number(rec.verbal) + Number(rec.motor)
  }
  return '—'
}

/** 把弹窗选定的 E/V/M 合计写入可编辑的 GCS 输入值（自动重算神经项分值与总分） */
function applyGcsTotal(total, detail) {
  inputs.gcs = total
  gcsLocalDetail.value = detail || ''
  gcsTotal.value = total
  gcsDetail.value = detail || ''
  applyInputChange()
}

/** 「确定选择」：三项齐全直接带入并关闭；插管/缺言语则带入可用项并跳手工页补评 */
function confirmPickSystemGcs() {
  const rec = systemGcsList.value[selectedSysIndex.value]
  if (!rec) {
    ElMessage.warning('请先在列表中选择一条 GCS 记录')
    return
  }
  const e = (rec.eye === null || rec.eye === undefined) ? null : Number(rec.eye)
  const v = (!rec.intubated && rec.verbal !== null && rec.verbal !== undefined) ? Number(rec.verbal) : null
  const m = (rec.motor === null || rec.motor === undefined) ? null : Number(rec.motor)
  if (e !== null && v !== null && m !== null) {
    applyGcsTotal(e + v + m, `E${e}V${v}M${m}`)
    ElMessage.success(`已从重症系统同步 GCS ${e + v + m} 分（E${e}V${v}M${m}）`)
    showGcsModal.value = false
    return
  }
  // 插管/缺项：带入可用项，转到手工页补评
  gcsForm.eye = e
  gcsForm.verbal = v
  gcsForm.motor = m
  gcsTab.value = 'manual'
  ElMessage.warning(rec.intubated
    ? '该患者气管插管/气切（ET），言语(V) 无法从系统获取，已带入睁眼(E)、运动(M)，请人工评定言语'
    : '该记录存在缺项，已带入可用项，请在「手工新建评估」中补选')
}

/** 「自动同步最新记录」：拉取后直接带入最新一条（插管/缺 V 时跳手工页补评） */
async function syncLatestGcs() {
  await loadSystemGcs(false)
  if (systemGcsList.value.length) {
    selectedSysIndex.value = 0
    confirmPickSystemGcs()
  } else {
    ElMessage.info('重症系统暂无 GCS 记录，请切换到「手工新建评估」录入')
  }
}

/** 手工新建评估：三项齐全后写入 GCS 总分 */
function confirmGcs() {
  if (!gcsModalComplete.value) {
    ElMessage.warning('请在睁眼、言语、运动三项中各选一档')
    return
  }
  const e = Number(gcsForm.eye)
  const v = Number(gcsForm.verbal)
  const m = Number(gcsForm.motor)
  applyGcsTotal(e + v + m, `E${e}V${v}M${m}`)
  ElMessage.success(`已填入 GCS ${e + v + m} 分（E${e}V${v}M${m}）`)
  showGcsModal.value = false
}

/** 趋势图高亮值：肝/肾趋势按 mg/dL（与后端同口径），其余取输入值 */
function chartMarkValue(it) {
  if (!it) return null
  if (it.key === 'liver') return isEmptyNum(inputs.bili) ? null : round2(Number(inputs.bili) / 17.1)
  if (it.key === 'renal') return isEmptyNum(inputs.creatinine) ? null : round2(Number(inputs.creatinine) / 88.4)
  if (it.key === 'resp') return numOrNull(inputs.pf)
  if (it.key === 'coag') return numOrNull(inputs.plt)
  if (it.key === 'cardio') return numOrNull(inputs.map)
  if (it.key === 'neuro') return numOrNull(inputs.gcs)
  return it.value
}

onMounted(async () => {
  initRange()
  await loadAssessment()
  await loadRecords()
  // 电子签名独立于评分流程，放最后加载，不阻塞上面的取数
  await loadDoctorSignature(username.value)
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

/** 当前区间回显（MM-dd HH:mm ~ MM-dd HH:mm） */
const rangeText = computed(() => {
  const s = fmtRangeInput(rangeStart.value)
  const e = fmtRangeInput(rangeEnd.value)
  return (s && e) ? `${s} ~ ${e}` : '—'
})

/** 快捷区间标签：让医生一眼看到当前区间是怎么来的 */
const rangeTag = computed(() => {
  if (activeRange.value === 24) return '最近24小时'
  if (activeRange.value === 48) return '最近48小时'
  if (activeRange.value === 'admission_after') return '入科后24h'
  if (activeRange.value === 'admission_before') return '入科前24h'
  if (activeRange.value === 'custom') return '自定义'
  return ''
})

function fmtRangeInput(v) {
  const s = String(v || '')
  return s.length >= 16 ? `${s.slice(5, 10)} ${s.slice(11, 16)}` : s
}

function parseLocalInput(v) {
  if (!v) return null
  const d = new Date(String(v).replace(' ', 'T'))
  return isNaN(d.getTime()) ? null : d
}

/**
 * 取数窗口校验：起止必须完整且 start < end；跨度 > 7 天二次确认（避免误选后长时间取数）。
 * @return {Promise<boolean>} 通过校验为 true
 */
async function validateRange() {
  const s = parseLocalInput(rangeStart.value)
  const e = parseLocalInput(rangeEnd.value)
  if (!s || !e) {
    ElMessage.warning('请先选择完整的取数起止时间')
    return false
  }
  if (s.getTime() >= e.getTime()) {
    ElMessage.warning('取数开始时间必须早于结束时间，请重新选择')
    return false
  }
  const days = (e.getTime() - s.getTime()) / 86400000
  if (days > 7) {
    try {
      await ElMessageBox.confirm(`当前取数跨度约 ${days.toFixed(1)} 天，数据量大时取数会明显变慢，确认继续？`,
        '取数范围偏大', { confirmButtonText: '继续取数', cancelButtonText: '重新选择', type: 'warning' })
    } catch (err) {
      return false
    }
  }
  return true
}

/** 快捷区间：改时间后立即重新取数（无需再点按钮），并标记选中态 */
function quickRange(hours) {
  const now = new Date()
  rangeEnd.value = toLocalInput(now)
  rangeStart.value = toLocalInput(new Date(now.getTime() - hours * 3600 * 1000))
  activeRange.value = hours
  rangeDirty.value = false
  loadAssessment()
}

/**
 * 按入科时间取 24h 窗口（after=true 为「入科后24h」，false 为「入科前24h」），与 APACHE II 同口径。
 * 入科时间缺失或格式异常时提示并保持当前范围不变。
 */
function setAdmissionRange(after) {
  const t = patient.inDepartTime
  if (!t) {
    ElMessage.warning('未获取到入科时间，无法按入科时间取数')
    return
  }
  const base = new Date(String(t).replace(' ', 'T'))
  if (isNaN(base.getTime())) {
    ElMessage.warning('入科时间格式异常，无法按入科时间取数')
    return
  }
  if (after) {
    rangeStart.value = toLocalInput(base)
    rangeEnd.value = toLocalInput(new Date(base.getTime() + 24 * 3600 * 1000))
    activeRange.value = 'admission_after'
  } else {
    rangeStart.value = toLocalInput(new Date(base.getTime() - 24 * 3600 * 1000))
    rangeEnd.value = toLocalInput(base)
    activeRange.value = 'admission_before'
  }
  rangeDirty.value = false
  loadAssessment()
}

/** 手动修改时间：切到「自定义」态并标记待取数（不自动请求，避免输入过程中反复打后端） */
function onRangeInput() {
  activeRange.value = 'custom'
  rangeDirty.value = true
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

async function loadAssessment() {
  // 统一入口校验：所有取数（快捷区间 / 按入科时间 / 手动点按钮）都走这里
  if (!(await validateRange())) return
  rangeDirty.value = false
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
    // 呼吸支持状态先落地：可编辑输入值要用它初始化
    respiratorySupport.value = res.respiratorySupport || 0
    // 用自动取数结果初始化可编辑的输入值与命中列，并按六项当前生效分值之和重算总分
    resetOverridesFromItems()
    lastScore.value = res.lastScore
    lastScoreTime.value = res.lastScoreTime
    deltaSofa.value = res.deltaSofa
    weightUsed.value = res.weightUsed
    weightNote.value = res.weightNote || ''
    weightSource.value = res.weightSource || ''
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
  trendFallback.value = ''
  // 弹窗渲染后再初始化图表（此时容器才有尺寸）
  nextTick(() => renderSourceTrend(it))
}

/**
 * 趋势回退点：范围内无序列时，用本次评分实际取用的值 + 取值时间构造单点。
 * 单位与趋势口径一致（肝/肾转 mg/dL，循环取 MAP），仅供核对。
 * @returns {[string, number]|null} 无取值时返回 null
 */
function fallbackTrendPoint(it) {
  if (!it) return null
  let v = null
  if (it.key === 'liver') v = isEmptyNum(inputs.bili) ? null : round2(Number(inputs.bili) / 17.1)
  else if (it.key === 'renal') v = isEmptyNum(inputs.creatinine) ? null : round2(Number(inputs.creatinine) / 88.4)
  else if (it.key === 'cardio') v = numOrNull(inputs.map)
  else if (it.key === 'neuro') v = numOrNull(inputs.gcs)
  else if (it.key === 'coag') v = numOrNull(inputs.plt)
  else v = numOrNull(inputs.pf)
  if (v === null || v === undefined) return null
  return [it.dataTime ? fmtTime(it.dataTime) : '本次取值', Number(v)]
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

  const startTime = toBackend(rangeStart.value)
  const endTime = toBackend(rangeEnd.value)
  let data = []
  let errMsg = ''
  try {
    const pid = patient.patientId || patientId.value
    if (!pid) {
      // 明确区分「没发请求」和「确实没数据」：外链入口必须带 patientId / 住院号
      errMsg = '缺少患者标识（patientId / 住院号），未发起趋势查询'
    } else {
      data = await fetchSofaTrend(pid, it.key, startTime, endTime)
    }
  } catch (e) {
    console.warn('趋势加载失败', e)
    errMsg = '趋势加载失败：' + (e.message || e)
  }
  trendChart.hideLoading()

  const points = (Array.isArray(data) ? data : [])
    .filter(p => p && p.time && p.value !== null && p.value !== undefined)
    .map(p => [fmtTime(p.time), Number(p.value)])

  // 范围内查不到趋势点、但本次评分确实取到了值：回退展示「本次评分取值点」，
  // 保证「来源」始终能核对到评分依据，而不是一片空白。
  if (!errMsg && !points.length) {
    const fb = fallbackTrendPoint(it)
    if (fb) {
      points.push(fb)
      trendFallback.value = `当前取数范围（${fmtTime(startTime)} ~ ${fmtTime(endTime)}）内无 ${it.label || it.key} 趋势记录，图中仅显示本次评分取值点，供核对（可调整上方时间范围后重试）。`
    }
  }
  trendCount.value = points.length

  if (errMsg || !points.length) {
    trendChart.setOption({
      title: {
        text: errMsg ? '趋势加载失败' : '暂无数据',
        subtext: errMsg || `取数范围（${fmtTime(startTime)} ~ ${fmtTime(endTime)}）内无「${it.label || it.key}」记录，可核对下方原始数据或调整时间范围后重试`,
        left: 'center',
        top: '35%',
        textStyle: { color: errMsg ? '#f56c6c' : '#909399', fontSize: 13, fontWeight: 'normal' },
        subtextStyle: { color: '#a8abb2', fontSize: 11 }
      },
      xAxis: { show: false },
      yAxis: { show: false },
      series: []
    }, true)
    return
  }

  const unit = it.unit && it.unit !== 'GCS' ? it.unit : ''
  // 高亮「本次评分实际取用」的点：手工改过输入值后以输入值为准
  const markValue = chartMarkValue(it)
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
      markPoint: (markValue === null || markValue === undefined) ? undefined : {
        symbol: 'pin',
        symbolSize: 40,
        itemStyle: { color: '#f56c6c' },
        label: { fontSize: 10, color: '#fff', formatter: '评分取值' },
        // ⚠️ data 项必须是对象，不能写成 [时间, 数值] 数组：
        // ECharts 的 MarkerModel 会对数组项执行 fillLabel(item[0]) / fillLabel(item[1])，
        // 即给数组元素本身写 label 属性，字符串元素会抛
        // "Cannot create property 'label' on string '...'" 并中断整个 setOption（趋势图空白）。
        data: points
          .filter(p => Math.abs(Number(p[1]) - Number(markValue)) < 0.001)
          .map(p => ({ name: '评分取值', coord: [p[0], Number(p[1])], value: Number(p[1]) }))
      }
    }]
  }, true)
  trendChart.resize()
}

/**
 * 保存评分，并在后台自动生成文书 PDF 归档（与 APACHE II 一致：只保留一个保存按钮）。
 * 主体（轻量 JSON）优先落库，PDF 大字段单独补传，文书生成失败/超时都不影响已保存的评分。
 */
async function saveRecord() {
  if (!inHospitalNo.value) {
    ElMessage.warning('缺少住院号，无法保存')
    return
  }
  const cur = currentRecordOf()
  const rec = buildRecord()
  saving.value = true
  let pdfPromise = null
  try {
    // 文书里的「记录时间」：覆盖已有记录时与其评分时间一致，新增时为当前时间
    refreshReportTime()
    await nextTick()
    // 并行启动文书渲染，不阻塞评分落库这条关键路径；失败兜底 null，稍后再提示
    pdfPromise = buildSofaPdfBase64().catch(e => { console.error('生成文书PDF失败', e); return null })

    const saved = await saveSofaRecord(rec, toBackend(rangeStart.value), toBackend(rangeEnd.value))
    const savedId = saved && saved.id ? saved.id : null
    ElMessage.success(cur ? '评分已更新，评分文书正在后台归档…' : '评分已保存，评分文书正在后台归档…')
    doctorRemark.value = ''
    // 高亮刚落库的记录；不重新载入其分值（当前分值就是刚落库的内容）
    currentRecordId.value = savedId
    await loadRecords()
    saving.value = false

    // 阶段2：后台补传 PDF（与主保存解耦，再慢/失败都不影响已落库的评分）
    try {
      const pdfBase64 = await pdfPromise
      if (pdfBase64 && savedId) {
        await attachSofaRecordPdf(savedId, pdfBase64, reportFileName())
        ElMessage.success('评分文书 PDF 已归档')
        await loadRecords() // 刷新该条“PDF文书”标记
      } else {
        ElMessage.warning('评分已保存，但文书 PDF 生成失败，可重新编辑该记录后再次保存')
      }
    } catch (e) {
      console.error('文书PDF补传失败', e)
      ElMessage.warning('评分已保存，但文书 PDF 归档失败，可重新编辑该记录后再次保存')
    }
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || e))
  } finally {
    saving.value = false
  }
}

/** 前端按当前评估结果组装记录（后端若发现分值为空会自行重算兜底） */
function buildRecord() {
  const byKey = {}
  for (const it of items.value) byKey[it.key] = it
  // 分值取“当前生效值”（含医生手工修正），而非纯自动值
  const s = (k) => (byKey[k] ? scoreOf(byKey[k]) : (scoreOverrides[k] || 0))
  const cur = currentRecordOf()
  // 手工修正留痕：复核者能看到哪些项被改过；覆盖已有记录时保留其原备注，避免被当前自动提示改写
  const manualTail = manualEditCount.value > 0 ? `【手工修正】${manualLabels.value.join('、')}` : ''
  const baseRemark = cur
    ? [cur.remark, doctorRemark.value, manualTail].filter(Boolean).join('；')
    : [doctorRemark.value || remark.value, manualTail].filter(Boolean).join('；')
  return {
    // 选中左侧记录后再保存 = 覆盖该条；点「新增评分」清空选中后再保存 = 插入新记录
    id: cur ? cur.id : null,
    // 覆盖时沿用原评分时间，避免把历史记录的时间改写成当前时间
    scoreTime: cur ? cur.scoreTime : null,
    patientId: patient.patientId || patientId.value,
    inHospitalNo: inHospitalNo.value,
    patientName: patient.name,
    departCode: patient.departCode,
    // 统一落库为「手工评分」：覆盖自动初评时同样表示已由医生确认
    scoreType: 'custom',
    respScore: s('resp'),
    coagScore: s('coag'),
    liverScore: s('liver'),
    cardioScore: s('cardio'),
    neuroScore: s('neuro'),
    renalScore: s('renal'),
    totalScore: totalScore.value,
    // GCS 汇总：医生弹窗同步/手工录入后随记录落库，供文书与历史复核（后端仅在缺分值时兜底重算）
    gcsTotal: isEmptyNum(inputs.gcs) ? null : Number(inputs.gcs),
    gcsDetail: gcsDetailText.value || null,
    remark: baseRemark
    // createBy 不传：由后端按服务端解析出的操作人覆盖，避免前端把它改成别人
  }
}

/**
 * 文书归档：
 *   待归档 → 调院方归档接口推送该条文书 → 成功后标记「已归档」；
 *   已是「已归档」时再点只撤销标记，不调用院方接口（只改本地状态）。
 */
async function toggleArchive(r) {
  if (!r || !r.id) return
  try {
    if (r.archiveStatus === 1) {
      await unmarkSofaArchive(r.id)
      r.archiveStatus = 0
      ElMessage.success('已撤销归档标记')
    } else {
      await pushSofaArchive(r.id)
      r.archiveStatus = 1
      ElMessage.success('归档成功')
    }
  } catch (e) {
    console.error('归档失败', e)
    ElMessage.error(e?.response?.data?.message || e?.message || '归档失败')
  }
}

async function removeRecord(r) {
  if (!r) return
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
    // 被删的是当前选中记录时，清空选中并把分值恢复为当前自动取数
    if (currentRecordId.value === r.id) {
      currentRecordId.value = null
      resetOverridesFromItems()
    }
    await loadRecords()
  } catch (e) {
    ElMessage.error('删除失败：' + (e.message || e))
  }
}

/** 底部「删除」：删除当前选中的记录（与 APACHE II 一致） */
async function deleteCurrentRecord() {
  await removeRecord(currentRecordOf())
}

// ---------------- 分值状态 ----------------

/**
 * 各器官项「当前生效分值」：
 *  - 载入评估结果时初始化为自动分（resetOverridesFromItems）
 *  - 医生手工修改输入值后由前端按 SOFA 标准重算（recalcFromInputs）
 *  - 选中历史记录时填入该记录的分值（selectRecord），保存即覆盖该条
 * 器官卡片为只读展示，手工修正入口在评分表的「输入值」列。
 */
const scoreOverrides = reactive({})

/** 某项当前生效分值（默认自动值） */
function scoreOf(it) {
  if (!it) return 0
  const ov = scoreOverrides[it.key]
  return (ov === null || ov === undefined || ov === '') ? (it.score || 0) : Number(ov)
}

/** 总分 = 六项当前生效分值之和 */
function recalcTotal() {
  totalScore.value = items.value.reduce((sum, it) => sum + scoreOf(it), 0)
}

/** 载入评估结果后用自动分初始化各项，并重置可编辑输入值 */
function resetOverridesFromItems() {
  initInputsFromItems()
  restoreAutoScores()
  // 重新取数后旧的弹窗明细失效，改由后端返回的 gcsDetail 决定展示
  gcsLocalDetail.value = ''
}

// ---------------- 左侧评分记录栏 ----------------

/** 记录按评分时间倒序（最新在上） */
const sortedRecords = computed(() => {
  return [...records.value].sort((a, b) => String(b.scoreTime || '').localeCompare(String(a.scoreTime || '')))
})

/** 当前选中的记录：高亮该条，且「保存评分」会覆盖该条 */
const currentRecordId = ref(null)

/**
 * 选中某条记录：载入其六项分值作为当前生效值。
 * 之后点「保存评分」即覆盖该条（与 APACHE II 一致）；点「新增评分」清空选中后再保存则新增一条。
 */
function selectRecord(r) {
  if (!r) return
  currentRecordId.value = r.id
  scoreOverrides.resp = r.respScore || 0
  scoreOverrides.coag = r.coagScore || 0
  scoreOverrides.liver = r.liverScore || 0
  scoreOverrides.cardio = r.cardioScore || 0
  scoreOverrides.neuro = r.neuroScore || 0
  scoreOverrides.renal = r.renalScore || 0
  recalcTotal()
}

/** 新增评分：清空选中并按当前取数范围重新取数 */
function addRecord() {
  currentRecordId.value = null
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

/** 血管活性药命中列：以当前生效分（含手工修正）为准 */
function vasoHit(name, col) {
  const s = drugScores[name]
  return s !== null && s !== undefined && Number(s) === col
}

/** 单项指标命中列（该器官「当前生效分」=== col） */
function hitCol(key, col) {
  const it = items.value.find(x => x.key === key)
  return it ? Number(scoreOf(it)) === col : false
}

/** 子项命中列（mapScore / creatinineScore / urineScore），随手工修正重算 */
function hitRaw(key, field, col) {
  const v = subScores[field]
  if (v === null || v === undefined) return false
  return Number(v) === col
}

function cellCls(hit) {
  return hit ? 'cell-hit' : ''
}

/** 器官分值方块配色 */
function boxClass(s) {
  const n = Number(s || 0)
  if (n >= 4) return 'red'
  if (n >= 2) return 'orange'
  if (n >= 1) return 'blue'
  return 'green'
}

// ---- 来源三态（自动初评 / 已复核 / 手工评分） ----

/** 自动类来源：定时任务 daily、自动取数 auto */
function isAutoRecord(r) {
  return !!r && (r.scoreType === 'daily' || r.scoreType === 'auto')
}

function scoreTypeLabel(r) {
  if (!r) return ''
  if (r.scoreType === 'reviewed') return '已复核'
  if (isAutoRecord(r)) return '自动评分'
  return '手工评分'
}

function recTagClass(r) {
  if (!r) return 'manual'
  if (r.scoreType === 'reviewed') return 'reviewed'
  return isAutoRecord(r) ? 'auto' : 'manual'
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

/** 当前选中的记录对象（来自左侧记录列表） */
function currentRecordOf() {
  return records.value.find(x => x.id === currentRecordId.value) || null
}

/** 文书里的「记录时间」：覆盖已有记录时与其评分时间一致，新增时取当前时间 */
function refreshReportTime() {
  const cur = currentRecordOf()
  const t = cur && cur.scoreTime ? String(cur.scoreTime).replace('T', ' ') : ''
  reportTime.value = t ? t.slice(0, 19) : fmtTimeNow()
}

/** 预览文书：把离屏文书快照到弹窗（与 APACHE II 一致，只预览不保存） */
async function openReport() {
  reportGenerating.value = true
  try {
    refreshReportTime()
    await nextTick()
    showReportModal.value = true
    await nextTick()
    // 必须克隆 outerHTML：.report-page 外壳带 A4 固定宽度/内边距/宋体样式，
    // 只取 innerHTML 会让表格落进弹窗容器被压变形、左侧留大片空白
    if (reportRef.value && reportViewRef.value) {
      reportViewRef.value.innerHTML = reportRef.value.outerHTML
    }
  } finally {
    reportGenerating.value = false
  }
}

/** 浏览器打印（新窗口写入文书 HTML，矢量清晰） */
function printReport() {
  const el = reportRef.value
  if (!el) return
  const w = window.open('', '_blank')
  if (!w) {
    ElMessage.warning('浏览器拦截了打印窗口，请允许弹窗')
    return
  }
  w.document.write(`<!DOCTYPE html><html><head><meta charset="utf-8"><title>${reportFileName()}</title>
  <style>
    body{margin:0;background:#fff;font-family:'Microsoft YaHei','SimSun',sans-serif;color:#000}
    .report-page{width:190mm;margin:0 auto;padding:4mm 0}
    @page{size:A4;margin:10mm}
    table{border-collapse:collapse;width:100%}
  </style></head><body>${el.innerHTML}</body></html>`)
  w.document.close()
  w.focus()
  setTimeout(() => { w.print() }, 350)
}

/** 导出 PDF：与保存归档使用的是同一份文书渲染逻辑 */
async function downloadReportPdf() {
  try {
    const pdf = await buildSofaPdf()
    pdf.save(reportFileName())
  } catch (e) {
    console.error(e)
    ElMessage.warning('导出PDF失败：' + (e.message || e))
  }
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
.side-head .count { background: #ecf5ff; color: #409eff; min-width: 22px; height: 22px; padding: 0 6px; border-radius: 11px; font-size: 12px; font-weight: 600; display: inline-flex; align-items: center; justify-content: center; }
.side-add { padding: 10px; border-bottom: 1px solid #ebeef5; }
.add-record-btn { width: 100%; height: 36px; background: linear-gradient(135deg, #409eff, #66b1ff); color: #fff; border: none; border-radius: 6px; font-size: 14px; font-weight: 600; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 6px; box-shadow: 0 2px 6px rgba(64,158,255,0.3); }
.add-record-btn:hover { background: linear-gradient(135deg, #66b1ff, #409eff); }
.add-record-btn .plus { font-size: 18px; line-height: 1; }
.record-list { flex: 1; overflow-y: auto; padding: 8px; }
.record-item { padding: 12px 14px; margin-bottom: 8px; border: 1px solid #ebeef5; border-radius: 8px; cursor: pointer; transition: all .2s; background: #fff; }
.record-item:hover { border-color: #c6e2ff; background: #f5f9ff; }
.record-item.active { border-color: #409eff; background: #ecf5ff; }
.record-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.record-time { font-size: 13px; color: #606266; font-weight: 500; }
.record-score { width: 34px; height: 34px; display: inline-flex; align-items: center; justify-content: center; font-size: 18px; font-weight: 700; border-radius: 50%; }
.record-score.green { background: #f0f9eb; color: #67c23a; }
.record-score.orange { background: #fdf6ec; color: #e6a23c; }
.record-score.red { background: #fef0f0; color: #f56c6c; }
.record-meta { font-size: 12px; color: #909399; line-height: 1.8; }
.record-meta span { margin-right: 6px; }
.record-tag { display: inline-flex; align-items: center; padding: 1px 8px; border-radius: 10px; font-size: 11px; background: #f4f4f5; color: #909399; }
.record-tag.auto { background: #ecf5ff; color: #409eff; }
.record-tag.manual { background: #fdf6ec; color: #e6a23c; }
.record-tag.reviewed { background: #e1f3d8; color: #389e0d; }
.record-tag.pdf-tag { background: #e1f3d8; color: #389e0d; cursor: pointer; }
.record-tag.pdf-tag:hover { background: #d3f0c0; }
.record-tag.del-tag { cursor: pointer; }
.record-tag.del-tag:hover { background: #fef0f0; color: #f56c6c; }
/* 归档状态标签：待归档（橙，可点击推送）/ 已归档（绿，点击撤销标记） */
.record-tag.archive-tag { cursor: pointer; }
.record-tag.archive-tag.todo { background: #fdf6ec; color: #e6a23c; }
.record-tag.archive-tag.todo:hover { background: #fbe9d0; }
.record-tag.archive-tag.done { background: #e1f3d8; color: #389e0d; }
.record-tag.archive-tag.done:hover { background: #d3f0c0; }
.record-empty { text-align: center; color: #c0c4cc; font-size: 13px; padding: 40px 0; }

.main { flex: 1; min-width: 0; padding: 12px 18px 24px; }

/* ===== 患者信息行 ===== */
.patient-row { display: flex; align-items: center; gap: 16px; margin-bottom: 11px; flex-wrap: wrap; background: #fff; border: 1px solid #ebeef5; border-radius: 6px; padding: 10px 16px; }
.patient-row .bed-tag { background: #409eff; color: #fff; font-size: 13px; font-weight: 500; padding: 3px 12px; border-radius: 14px; }
.patient-row .patient-name { font-size: 18px; font-weight: 700; color: #303133; }
.patient-meta { font-size: 13px; color: #606266; }
.patient-meta b { color: #303133; font-weight: 600; }
.patient-row .resp-flag { font-size: 13px; padding: 3px 12px; border-radius: 14px; background: #f4f4f5; color: #909399; }
.patient-row .resp-flag.on { background: #e1f3d8; color: #389e0d; }

/* ===== 总览条：总分卡 + 6 器官小卡 ===== */
.overview { display: flex; gap: 10px; background: #fafafa; border: 1px solid #ebeef5; border-radius: 6px; padding: 10px; margin-top: 12px; margin-bottom: 12px; }
/* 总分卡：内容像 APACHE II 那样卡内居中（label / 分值 / 底部说明整体垂直居中） */
.ov-total { width: 190px; flex-shrink: 0; border-radius: 8px; color: #fff; padding: 10px 14px; background: linear-gradient(135deg,#409eff,#66b1ff); box-shadow: 0 2px 6px rgba(64,158,255,.3); display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; box-sizing: border-box; }
.ov-total .t-label { font-size: 12px; opacity: .92; }
.ov-total .t-num { font-size: 34px; font-weight: 700; line-height: 1.15; }
.ov-total .t-pill { margin-top: 4px; font-size: 12px; color: #fff; background: rgba(255,255,255,.20); border: 1px solid rgba(255,255,255,.28); padding: 3px 11px; border-radius: 16px; }
.ov-total .t-delta { margin-top: 5px; font-size: 12px; opacity: .95; }
.ov-total .t-delta b { color: #FFE7A8; }
.ov-total .t-foot { margin-top: 5px; font-size: 12px; opacity: .92; background: rgba(255,255,255,.2); border-radius: 10px; padding: 2px 8px; }
.ov-organs { flex: 1; display: grid; grid-template-columns: repeat(6, 1fr); gap: 10px; min-width: 0; }
.ov-card { background: #fff; border: 1px solid #ebeef5; border-radius: 8px; padding: 10px 8px 0; display: flex; flex-direction: column; align-items: center; gap: 6px; min-width: 0; box-sizing: border-box; }
.ov-pic { width: 46px; height: 46px; border-radius: 50%; display: inline-flex; align-items: center; justify-content: center; background: rgba(0,0,0,0.04); }
.ov-pic svg { width: 28px; height: 28px; display: block; }
.ov-pic.green { color: #67c23a; background: rgba(103,194,58,0.12); }
.ov-pic.blue { color: #409eff; background: rgba(64,158,255,0.12); }
.ov-pic.orange { color: #e6a23c; background: rgba(230,162,60,0.12); }
.ov-pic.red { color: #f56c6c; background: rgba(245,108,108,0.12); }
.ov-name { font-size: 13px; font-weight: 500; color: #606266; max-width: 100%; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.ov-foot { margin-top: auto; width: 100%; display: flex; align-items: baseline; justify-content: center; gap: 2px; padding: 5px 0 7px; border-top: 1px dashed #eef1f6; }
.ov-unit { font-size: 11px; color: #909399; }
.ov-num { font-size: 20px; font-weight: 700; line-height: 1; color: #303133; }
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
.table-panel { background: #fff; border-radius: 8px; border: 1px solid #ebeef5; padding: 14px 16px 8px; }
.table-title { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 600; color: #303133; margin-bottom: 10px; }
.table-title::before { content: ''; width: 3px; height: 15px; border-radius: 2px; background: #409eff; }
.table-title .tt-hint { font-size: 12px; font-weight: 400; color: #909399; }
.btn-mini { height: 24px; padding: 0 10px; border: 1px solid #f0c78a; border-radius: 4px; background: #fdf6ec; color: #d98b0b; font-size: 12px; cursor: pointer; transition: all .2s; }
.btn-mini:hover { background: #fbe9d2; }
table.score { width: 100%; border-collapse: collapse; table-layout: fixed; }
table.score th, table.score td { border: 1px solid #ebeef5; text-align: center; padding: 0; height: 40px; font-size: 13px; vertical-align: middle; color: #606266; }
table.score thead th { background: #f5f8fc; color: #728096; font-weight: 600; height: 38px; }
table.score td.sys { font-weight: 600; color: #303133; background: #fafafa; font-size: 13px; }
table.score td.ind { text-align: left; padding-left: 13px; color: #303133; background: #fff; }
table.score td.dim { color: #c0c4cc; }
table.score td.cell-hit { background: #dcecfc; color: #2b7de1; font-weight: 600; }
/* 输入值列：可直接编辑（手工修正入口），呼吸机支持为下拉 */
table.score .inp { display: block; height: 30px; margin: 0 auto; width: 88%; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; padding: 0 8px; color: #303133; font-size: 13px; font-weight: 600; box-sizing: border-box; text-align: center; outline: none; transition: border-color .2s, box-shadow .2s, background .2s; }
table.score .inp::placeholder { color: #c0c4cc; font-weight: 400; }
table.score .inp:hover { border-color: #c6e2ff; }
table.score .inp:focus { border-color: #409eff; box-shadow: 0 0 0 2px rgba(64, 158, 255, .12); }
/* 手工改过的输入项：橙色高亮，改回自动值即恢复 */
table.score .inp.dirty { border-color: #e6a23c; background: #fdf6ec; color: #d98b0b; }
/* 数字输入框隐藏原生步进箭头，避免挤占本就很窄的单元格 */
table.score .inp[type="number"] { -moz-appearance: textfield; appearance: textfield; }
table.score .inp[type="number"]::-webkit-outer-spin-button,
table.score .inp[type="number"]::-webkit-inner-spin-button { -webkit-appearance: none; margin: 0; }
table.score td.act { padding: 4px 6px; height: auto; }
.btn-src { min-width: 50px; height: 26px; padding: 0 8px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; color: #606266; font-size: 12px; cursor: pointer; transition: all .2s; }
.btn-src:hover { color: #409eff; border-color: #c6e2ff; background: #ecf5ff; }
.score-tag { display: inline-flex; align-items: center; justify-content: center; min-width: 46px; height: 30px; padding: 0 12px; border-radius: 4px; background: #ecf5ff; color: #409eff; font-weight: 600; font-size: 13px; box-sizing: border-box; }

.tip { display: flex; align-items: center; gap: 8px; margin: 10px 0 8px; padding: 9px 13px; background: #f7fbff; border: 1px solid #edf2f8; border-radius: 6px; color: #40546c; font-size: 13px; line-height: 1.6; }
.tip .ico { flex: 0 0 18px; width: 18px; height: 18px; border-radius: 50%; background: #409eff; color: #fff; display: inline-flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; flex-shrink: 0; box-sizing: border-box; }
.tip b { color: #303133; }
.tip-manual { background: #fdf6ec; border-color: #f5dab1; color: #8a5a00; }

/* ===== 底部操作行 ===== */
.footer-bar { display: flex; align-items: center; gap: 10px; margin-top: 8px; flex-wrap: wrap; }
.footer-info { font-size: 13px; color: #303133; line-height: 1.7; flex: 0 0 auto; }
.footer-note { flex: 1; min-width: 200px; height: 32px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; padding: 0 10px; font-size: 13px; color: #303133; outline: none; }
.footer-note:focus { border-color: #409eff; }

.btn { height: 32px; padding: 0 14px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; color: #606266; font-size: 13px; cursor: pointer; display: inline-flex; align-items: center; gap: 5px; transition: all .2s; }
.btn:hover { color: #409eff; border-color: #c6e2ff; background: #ecf5ff; }
.btn-primary { background: #409eff; color: #fff; border-color: #409eff; font-weight: 600; }
.btn-primary:hover { background: #66b1ff; color: #fff; border-color: #66b1ff; }
.btn-success { background: #67c23a; color: #fff; border-color: #67c23a; font-weight: 600; }
.btn-success:hover { background: #85ce61; color: #fff; border-color: #85ce61; }
.btn-danger { background: #f56c6c; color: #fff; border-color: #f56c6c; font-weight: 600; }
.btn-danger:hover { background: #f78989; color: #fff; border-color: #f78989; }
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
/* ===== 文书预览弹窗：A4 原尺寸等比展示，弹窗体内部滚动 ===== */
.report-modal { width: min(880px, calc(100vw - 40px)); display: flex; flex-direction: column; overflow: hidden; }
.report-modal .modal-body.report-scroll { flex: 1; overflow: auto; background: #e9edf2; padding: 18px; max-height: calc(100vh - 220px); }
.report-view-host { display: flex; justify-content: center; }
.report-view-host .report-page { flex: 0 0 auto; box-shadow: 0 2px 12px rgba(0,0,0,.16); }
.src-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 14px; }
.src-card { padding: 12px; background: #f5f7fa; border: 1px solid #ebeef5; border-radius: 8px; }
.src-label { font-size: 12px; color: #909399; }
.src-value { font-size: 20px; font-weight: 700; color: #409eff; margin-top: 4px; }
.src-section { font-size: 13px; color: #606266; margin-bottom: 6px; }
.src-title { margin: 12px 0 6px; font-weight: 600; font-size: 13px; color: #303133; border-left: 3px solid #409eff; padding-left: 8px; }
.src-desc { font-size: 13px; color: #606266; line-height: 1.8; }
.src-count { margin-left: 6px; font-weight: 400; color: #909399; }
.src-note { margin: 0 0 10px; font-size: 12px; color: #909399; line-height: 1.6; }
.src-note-warn { color: #e6a23c; }

/* ===== 取数时间范围：快捷按钮组 / 自定义态 / 区间回显 ===== */
.range-presets { display: inline-flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.range-row .sel.custom { border-color: #409eff; background: #f2f8ff; }
.range-chip { height: 32px; display: inline-flex; align-items: center; padding: 0 12px; border: 1px dashed #dcdfe6; border-radius: 4px; color: #a8abb2; font-size: 13px; cursor: default; }
.range-chip.active { border-style: solid; border-color: #409eff; background: #ecf5ff; color: #409eff; font-weight: 600; }
.range-echo { display: flex; align-items: center; gap: 8px; margin: 6px 0 10px; padding: 6px 12px; background: #f7fbff; border: 1px solid #edf2f8; border-radius: 6px; font-size: 12.5px; color: #40546c; }
.range-echo .echo-lbl { color: #909399; }
.range-echo b { font-weight: 600; color: #303133; }
.range-echo .echo-tag { padding: 1px 8px; border-radius: 10px; background: #ecf5ff; color: #409eff; font-size: 11.5px; }
.range-echo .echo-tip { margin-left: auto; color: #e6a23c; }

/* ===== GCS 行弹窗入口 ===== */
.score .gcs-open { display: block; margin: 4px auto 0; padding: 0 6px; height: 22px; line-height: 20px; border: 1px solid #c6e2ff; border-radius: 4px; background: #f2f8ff; color: #409eff; font-size: 11.5px; cursor: pointer; white-space: nowrap; }
.score .gcs-open:hover { background: #ecf5ff; border-color: #409eff; }

/* ===== GCS 弹窗（自动同步最新 / 手动选择 / 手工新建） ===== */
.gcs-modal { width: min(780px, calc(100vw - 40px)); max-height: 86vh; display: flex; flex-direction: column; overflow: hidden; }
.gcs-modal .modal-body { flex: 1; overflow-y: auto; }
.gcs-tabs { display: flex; gap: 8px; margin-bottom: 14px; }
.gcs-tab { height: 34px; padding: 0 16px; border: 1px solid #dcdfe6; border-radius: 6px; background: #fff; color: #606266; font-size: 13px; cursor: pointer; }
.gcs-tab.active { border-color: #409eff; background: #ecf5ff; color: #409eff; font-weight: 600; }
.gcs-sys-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 10px; }
.gcs-sys-tip { font-size: 12px; color: #728096; line-height: 1.5; }
.gcs-mini-primary { flex: 0 0 auto; height: 30px; padding: 0 14px; border: none; border-radius: 6px; background: #409eff; color: #fff; font-size: 12px; cursor: pointer; white-space: nowrap; }
.gcs-mini-primary:disabled { opacity: .6; cursor: not-allowed; }
.gcs-sys-table { max-height: 340px; overflow-y: auto; border: 1px solid #e8edf4; border-radius: 8px; }
.gcs-sys-table table { width: 100%; border-collapse: collapse; }
.gcs-sys-table th, .gcs-sys-table td { padding: 9px 8px; text-align: center; font-size: 12.5px; color: #40546c; border-bottom: 1px solid #eef2f7; white-space: nowrap; }
.gcs-sys-table th { position: sticky; top: 0; background: #f5f8fc; color: #728096; font-weight: 600; z-index: 1; }
.gcs-sys-table tbody tr { cursor: pointer; }
.gcs-sys-table tbody tr:hover { background: #f2f8ff; }
.gcs-sys-table tbody tr.selected { background: #e8f3ff; }
.gcs-sys-table td.gcs-empty { text-align: center; color: #94a3b8; padding: 24px 8px; cursor: default; white-space: normal; }
.gcs-sys-table .pick-link { color: #409eff; font-weight: 600; cursor: pointer; }
.et-tag { display: inline-block; padding: 1px 7px; border-radius: 4px; background: #fff1ea; color: #c2410c; font-size: 11px; font-weight: 600; }
.gcs-total-bar { padding: 14px; background: #f2f8ff; border-radius: 8px; text-align: center; margin-bottom: 14px; }
.gcs-total-bar span { font-size: 13px; color: #409eff; font-weight: 600; }
.gcs-total-bar strong { font-size: 32px; color: #409eff; margin: 0 8px; }
.gcs-total-bar em { font-size: 13px; color: #337ecc; font-style: normal; }
.gcs-total-bar em.gcs-warn { color: #c2410c; }
.gcs-row { margin-bottom: 14px; }
.gcs-row-title { font-size: 14px; font-weight: 600; color: #34445b; margin-bottom: 8px; }
.gcs-options { display: flex; flex-wrap: wrap; gap: 8px; }
.gcs-options button { min-width: 150px; height: 36px; padding: 0 14px; border: 1px solid #d9e2ef; border-radius: 6px; background: #fff; color: #334155; font-size: 13px; cursor: pointer; text-align: left; }
.gcs-options button.active { border-color: #409eff; background: #409eff; color: #fff; }
@media (max-width: 900px) {
  .gcs-options button { min-width: 100%; }
}
</style>
