<template>
  <div class="page">
    <!-- 左侧边栏 -->
    <aside class="side">
      <div class="side-head">
        <span>评分记录</span>
        <span class="count">{{ records.length }}</span>
      </div>
      <div style="padding:10px;border-bottom:1px solid #ebeef5">
        <button class="add-record-btn" @click="addRecord">
          <span style="font-size:18px;line-height:1">＋</span>
          新增评分
        </button>
      </div>
      <div class="record-list">
        <div
          v-for="rec in records"
          :key="rec.id"
          :class="['record-item', { active: currentRecord && currentRecord.id === rec.id }]"
          @click="selectRecord(rec)"
        >
          <div class="record-top">
            <span class="record-time">{{ formatDisplayTime(rec.scoreTime) }}</span>
            <span :class="['record-score', getScoreClass(rec.totalScore)]">{{ rec.totalScore }}</span>
          </div>
          <div class="record-meta">
            <span>{{ operatorLabel(rec.createBy) || '—' }}</span>
            <span :class="['record-tag', recTagClass(rec)]">{{ scoreTypeLabel(rec) }}</span>
            <span v-if="rec.hasPdf === 1" class="record-tag pdf-tag" @click.stop="viewSavedPdf(rec)">PDF文书</span>
            <!-- 归档口径：自动初评（auto/daily）只是系统内部评估草稿，未经医生确认，不作为文书归档；
                 医生打开复核并保存后来源转为「手工评分」，才可归档。
                 已确认但还没文书时显示「无文书」；有文书时：待归档→推送院方接口→已归档，
                 已归档再点只撤销标记（不调接口） -->
            <span v-if="isAutoRecord(rec)" class="record-tag archive-tag none"
                  title="系统自动初评属于内部评估草稿，需医生打开复核并保存后才能归档">草稿不归档</span>
            <span v-else-if="rec.hasPdf === 1"
                  :class="['record-tag', 'archive-tag', rec.archiveStatus === 1 ? 'done' : 'todo']"
                  @click.stop="toggleArchive(rec)">{{ rec.archiveStatus === 1 ? '已归档' : '待归档' }}</span>
            <span v-else class="record-tag archive-tag none"
                  title="该记录尚未生成评分文书，打开后保存一次即可归档">无文书</span>
            <!-- 删除按钮与 SOFA 一致，直接放在记录条里；@click.stop 防止连带触发 selectRecord -->
            <span class="record-tag del-tag" @click.stop="deleteRecord(rec)">删除</span>
          </div>
        </div>
        <div v-if="records.length === 0" class="record-empty">暂无评分记录</div>
      </div>
    </aside>

    <!-- 主区域 -->
    <main class="main">
      <!-- 患者信息栏 -->
      <!-- 外链访问时外层重症系统已展示患者信息，隐藏本条避免重复；非外链（系统内直接打开）仍显示 -->
      <div v-if="!isExternal" class="patient-bar">
        <div class="patient-name">
          <span class="bed-tag">{{ patientInfo.bedCode || '—' }}</span>
          {{ patientInfo.name || '—' }}
        </div>
        <div class="patient-info">
          <div class="patient-info-item"><span class="label">性别：</span><span class="value">{{ patientInfo.gender || '—' }}</span></div>
          <div class="patient-info-item"><span class="label">年龄：</span><span class="value">{{ patientInfo.age || '—' }}岁</span></div>
          <div class="patient-info-item"><span class="label">住院号：</span><span class="value">{{ patientInfo.inHospitalNo || '—' }}</span></div>
          <div class="patient-info-item"><span class="label">入科时间：</span><span class="value">{{ formatDisplayTime(patientInfo.inDepartTime) }}</span></div>
        </div>
        <div class="patient-bar-right">
          <button class="btn">评分历史趋势</button>
        </div>
      </div>

      <!-- 评分汇总卡 -->
      <div class="summary-bar">
        <div class="total-score-card">
          <div class="label">APACHE II 总分</div>
          <div class="value">{{ scoreResult.totalScore || 0 }}</div>
          <div class="mortality">预计死亡率 <b style="font-size:14px">{{ fmt2(scoreResult.mortalityRate) }}%</b></div>
        </div>
        <div class="score-cards">
          <div class="score-card">
            <div class="code">A</div>
            <div class="info"><div class="name">年龄评分</div><div class="val">{{ scoreResult.ageScore || 0 }}</div></div>
          </div>
          <div class="score-card">
            <div class="code">B</div>
            <div class="info"><div class="name">慢性健康状况</div><div class="val">{{ scoreResult.chronicScore || 0 }}</div></div>
          </div>
          <div class="score-card">
            <div class="code">C</div>
            <div class="info"><div class="name">GCS评分</div><div class="val">{{ scoreResult.gcsScore || 0 }}</div></div>
          </div>
          <div class="score-card">
            <div class="code">D</div>
            <div class="info"><div class="name">急性生理评分</div><div class="val">{{ scoreResult.physiologyScore || 0 }}</div></div>
          </div>
        </div>
      </div>

      <!-- 工具栏 -->
      <div class="toolbar">
        <span class="toolbar-label">取数时间范围：</span>
        <input v-model="fetchStartTime" type="datetime-local" class="dt-input"
               :class="{ 'dt-custom': fetchPreset === 'custom' }" @change="onFetchTimeChange">
        <span style="color:#c0c4cc">至</span>
        <input v-model="fetchEndTime" type="datetime-local" class="dt-input"
               :class="{ 'dt-custom': fetchPreset === 'custom' }" @change="onFetchTimeChange">
        <span class="range-presets">
          <button :class="['btn', { active: fetchPreset === '24h' }]" @click="setRangePreset('24h')">24小时</button>
          <button :class="['btn', { active: fetchPreset === '48h' }]" @click="setRangePreset('48h')">48小时</button>
          <button :class="['btn', { active: fetchPreset === 'admission_after' }]" @click="setRangePreset('admission_after')">入科后24h</button>
          <button :class="['btn', { active: fetchPreset === 'admission_before' }]" @click="setRangePreset('admission_before')">入科前24h</button>
        </span>
        <button class="btn btn-primary" @click="autoFetchAndCalc">自动获取并计算</button>
        <span style="margin-left:auto;color:#909399;font-size:12px">取数逻辑：范围内最差值（偏离正常最远）</span>
      </div>

      <!-- 内容区 -->
      <div class="content">
        <div class="content-row">
          <!-- 左侧：ABC基础评分 -->
          <div class="panel">
            <div class="panel-title">基础评分（A + B + C）</div>
            <div class="panel-body">
              <!-- A 年龄 -->
              <div class="abc-row">
                <span class="abc-code">A</span>
                <div class="abc-main">
                  <div class="abc-head"><strong>年龄评分</strong><em>{{ form.age }}岁 → {{ scoreResult.ageScore || 0 }}分</em></div>
                  <input v-model.number="form.age" type="number" min="0" max="120" @input="calculateScore">
                </div>
                <div class="abc-result"><span>得分</span><b>{{ scoreResult.ageScore || 0 }}</b></div>
              </div>

              <!-- B 慢性健康 -->
              <div class="abc-row">
                <span class="abc-code">B</span>
                <div class="abc-main">
                  <div class="abc-head"><strong>慢性健康状况评分</strong><em>{{ getChronicText() }}</em></div>
                  <select v-model="form.chronicHealth" @change="calculateScore">
                    <option value="none">无上述情况（0分）</option>
                    <option value="nonoperative">非手术或急诊手术后，有严重器官功能不全（5分）</option>
                    <option value="elective">择期手术后，有严重器官功能不全（2分）</option>
                  </select>
                </div>
                <div class="abc-result"><span>得分</span><b>{{ scoreResult.chronicScore || 0 }}</b></div>
              </div>

              <!-- C GCS -->
              <div class="abc-row gcs">
                <span class="abc-code">C</span>
                <div class="abc-main">
                  <div class="abc-head"><strong>GCS评分</strong><em>E{{ gcsDimText(form.gcsEye) }} / V{{ gcsDimText(form.gcsVerbal) }} / M{{ gcsDimText(form.gcsMotor) }} = {{ gcsTotalText }}</em></div>
                  <button class="gcs-trigger" @click="openGcsModal">
                    <div class="gcs-info">
                      <span>选择GCS评分（支持从系统同步或手动录入）</span>
                      <em v-if="gcsComplete">C项得分 = 15 - GCS = {{ scoreResult.gcsScore || 0 }}分</em>
                      <em v-else style="color:#c2410c;">睁眼/言语/运动三项均需各选一档，未评全前C项不计分</em>
                    </div>
                    <div class="gcs-val">{{ gcsTotalText }}</div>
                  </button>
                </div>
                <div class="abc-result"><span>得分</span><b>{{ gcsComplete ? (scoreResult.gcsScore || 0) : '—' }}</b></div>
              </div>

              <!-- 评分时间/医师 -->
              <div class="meta-row">
                <div class="meta-item">
                  <label>评分时间</label>
                  <input v-model="form.scoreTime" type="datetime-local">
                </div>
                <div class="meta-item">
                  <label>评分医师</label>
                  <input v-model="form.doctor" type="text" placeholder="请输入评分医师">
                </div>
              </div>
            </div>
          </div>

          <!-- 右侧：D急性生理评分 -->
          <div class="panel">
            <div class="panel-title" style="flex-wrap:wrap;gap:8px">
              <span>D 急性生理评分（12项）</span>
              <div style="margin-left:auto;display:flex;align-items:center;gap:12px;flex-wrap:wrap">
                <span style="font-size:12px;color:#606266;display:flex;align-items:center;gap:6px">
                  急性肾衰（肌酐加倍）
                  <span :class="['switch', { on: form.acuteRenalFailure }]" @click="form.acuteRenalFailure = !form.acuteRenalFailure; calculateScore()"></span>
                </span>
                <button class="btn btn-text" @click="autoFetchData">自动获取</button>
              </div>
            </div>
            <div style="overflow-x:auto">
              <table class="d-table">
                <thead>
                  <tr>
                    <th style="width:120px">参数</th>
                    <th>当前值（可手动修改）</th>
                    <th style="width:130px">命中区间</th>
                    <th style="width:60px">分值</th>
                    <th style="width:80px">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in apsItems" :key="item.key">
                    <td>
                      <div class="metric-name">{{ item.label }}</div>
                      <div class="metric-unit">{{ item.unit }}</div>
                    </td>
                    <td>
                      <input
                        v-if="item.key !== 'oxygen'"
                        v-model.number="form[item.key]"
                        type="number"
                        :step="item.step || 1"
                        @input="calculateScore"
                      >
                      <div v-else class="oxygen-fields">
                        <div class="oxygen-field"><span>FiO2(%)</span><input v-model.number="form.fio2" type="number" @input="calculateScore"></div>
                        <div class="oxygen-field"><span>A-aDO2</span><input v-model.number="form.aado2" type="number" @input="calculateScore"></div>
                        <div class="oxygen-field"><span>PaO2</span><input v-model.number="form.pao2" type="number" @input="calculateScore"></div>
                      </div>
                    </td>
                    <td><span class="range-hit">{{ item.key === 'oxygen' ? getOxygenRange() : getHitRange(item.key, form[item.key]) }}</span></td>
                    <td><span :class="['score-badge', getBadgeClass(scoreResult.apsScores && scoreResult.apsScores[item.key])]">{{ (scoreResult.apsScores && scoreResult.apsScores[item.key]) || 0 }}</span></td>
                    <td><div class="metric-actions"><button class="btn" style="min-width:50px" @click="openSourceModal(item.label)">来源</button></div></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>

        <!-- 死亡率预测区 -->
        <div class="mortality-panel">
          <div class="mortality-head">
            <div class="mortality-result">
              <div class="label">预测院内死亡率</div>
              <div class="value">{{ fmt2(scoreResult.mortalityRate) }}%</div>
              <div class="note">基于APACHE II总分 + 诊断权重</div>
            </div>
            <div class="equation-box">
              <span class="eq-label">计算公式：</span>
              <span class="eq-total">{{ scoreResult.totalScore || 0 }}</span>
              <div class="equation-chips">
                <span class="eq-chip"><mark>A</mark> 年龄 <strong>{{ scoreResult.ageScore || 0 }}</strong></span>
                <span class="eq-plus">+</span>
                <span class="eq-chip"><mark>B</mark> 慢性 <strong>{{ scoreResult.chronicScore || 0 }}</strong></span>
                <span class="eq-plus">+</span>
                <span class="eq-chip"><mark>C</mark> GCS <strong>{{ scoreResult.gcsScore || 0 }}</strong></span>
                <span class="eq-plus">+</span>
                <span class="eq-chip"><mark>D</mark> 生理 <strong>{{ scoreResult.physiologyScore || 0 }}</strong></span>
                <span class="eq-plus">+</span>
                <span class="eq-chip">诊断权重 <strong>{{ diagnosisWeight }}</strong></span>
              </div>
            </div>
          </div>
          <div class="mortality-controls">
            <div class="control-group">
              <span class="ctl-label">疾病分类：</span>
              <button :class="['seg-btn', { active: form.diagnosisType === 'nonoperative' }]" @click="form.diagnosisType = 'nonoperative'">非手术类</button>
              <button :class="['seg-btn', { active: form.diagnosisType === 'operative' }]" @click="form.diagnosisType = 'operative'">手术类</button>
              <button :class="['seg-btn', { active: form.diagnosisType === 'none' }]" @click="form.diagnosisType = 'none'">以上都不是</button>
            </div>
            <div v-if="form.diagnosisType === 'operative'" class="control-group">
              <span class="ctl-label">急诊手术：</span>
              <span :class="['switch', { on: form.emergencySurgery }]" @click="form.emergencySurgery = !form.emergencySurgery"></span>
              <span style="font-size:12px;color:#606266">{{ form.emergencySurgery ? '是' : '否' }}</span>
            </div>
            <button class="btn btn-text" @click="openWeightTable">权重对照表</button>
          </div>
          <div v-if="form.diagnosisType !== 'none'" class="factor-grid">
            <div v-if="form.diagnosisType === 'nonoperative'" class="factor-card">
              <div class="factor-card-title">
                <span>非手术类诊断权重</span>
                <b>已选</b>
              </div>
              <div class="factor-options">
                <label v-for="opt in nonoperativeFactors" :key="opt.name" :class="{ checked: form.selectedNonopFactor === opt.name }">
                  <input v-model="form.selectedNonopFactor" type="radio" name="nonop" :value="opt.name">
                  {{ opt.name }} ({{ opt.weight }})
                </label>
              </div>
            </div>
            <div v-if="form.diagnosisType === 'operative'" class="factor-card">
              <div class="factor-card-title">
                <span>手术类诊断权重</span>
                <b>已选</b>
              </div>
              <div class="factor-options">
                <label v-for="opt in operativeFactors" :key="opt.name" :class="{ checked: form.selectedOpFactor === opt.name }">
                  <input v-model="form.selectedOpFactor" type="radio" name="op" :value="opt.name">
                  {{ opt.name }} ({{ opt.weight }})
                </label>
              </div>
            </div>
          </div>
          <div v-else style="padding:20px;text-align:center;color:#909399;font-size:13px">
            选择"以上都不是"时，诊断权重为0
          </div>
          <div class="bottom-row">
            <div class="note-card">
              <div class="note-title">临床建议</div>
              <div class="note-body">
                <ul>
                  <li>总分 0-4：低危，常规监护</li>
                  <li>总分 5-14：中危，密切观察病情变化</li>
                  <li>总分 15-24：高危，建议转入ICU加强监护</li>
                  <li>总分 ≥25：极高危，死亡率显著升高，需积极干预</li>
                </ul>
              </div>
            </div>
            <div class="note-card">
              <div class="note-title">计算说明</div>
              <div class="note-body">
                <ul>
                  <li>A 年龄：≤44岁0分，45-54岁2分，55-64岁3分，65-74岁5分，≥75岁6分</li>
                  <li>B 慢性健康：择期手术后2分，非手术/急诊手术后5分</li>
                  <li>C GCS：得分 = 15 - GCS总分（GCS总分 = 睁眼E + 言语V + 运动M，范围3~15）</li>
                  <li>D 急性生理：12项指标取入科后24小时内最差值</li>
                  <li>急性肾衰时肌酐分值加倍</li>
                  <li><b>总分公式：APACHE II 总分 = A + B + C + D（范围 0~71 分）</b></li>
                  <li><b>风险系数：R = -3.5174 + D×0.1467 + 0.6031（非手术或急诊手术时）+ 诊断权重</b></li>
                  <li><b>预计院内死亡率 = 1 / (1 + e<sup style="font-size:9px;">-R</sup>) × 100%</b></li>
                </ul>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 底部操作栏 -->
      <div class="footer-bar">
        <textarea v-model="form.remark" placeholder="备注（可选）"></textarea>
        <div class="footer-total">总分：{{ scoreResult.totalScore || 0 }} 分</div>
        <button class="btn" :disabled="reportGenerating" @click="openReport">预览文书</button>
        <button class="btn btn-success" :disabled="saving" @click="saveRecord">{{ saving ? '保存中…' : '保存评分' }}</button>
      </div>
    </main>

    <!-- GCS弹窗 -->
    <div v-if="showGcsModal" class="modal-mask" @click.self="showGcsModal = false">
      <div class="modal gcs-modal">
        <div class="modal-head">
          <h3>C 项：GCS 评分（APACHE II）</h3>
          <button class="modal-close" @click="showGcsModal = false">×</button>
        </div>
        <el-tabs v-model="gcsTab" class="gcs-tabs">
          <!-- 页签一：选择重症系统已有记录 -->
          <el-tab-pane label="选择已有记录" name="sys">
            <div class="gcs-sys-bar">
              <div class="gcs-sys-tip">GCS 评估记录来自重症系统，选择一条后点「确定选择」带入 APACHE II 的 C 项</div>
              <button class="btn gcs-mini-primary" :disabled="gcsSyncLoading" @click="syncLatestGcs">
                {{ gcsSyncLoading ? '同步中…' : '自动同步最新记录' }}
              </button>
            </div>
            <div class="gcs-sys-table">
              <table>
                <thead>
                  <tr>
                    <th>评分时间</th><th>记录者</th><th>GCS</th><th>睁眼</th><th>言语</th><th>运动</th><th>选择</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-if="gcsSyncLoading">
                    <td colspan="7" class="gcs-empty">正在拉取重症系统记录…</td>
                  </tr>
                  <tr v-else-if="gcsSyncLoaded && !systemGcsList.length">
                    <td colspan="7" class="gcs-empty">重症系统暂无该患者的 GCS 评估记录，请切换到「新建 GCS 评估」手工录入</td>
                  </tr>
                  <tr v-else-if="!gcsSyncLoaded">
                    <td colspan="7" class="gcs-empty"><a class="pick-link" @click="loadSystemGcs(false)">点此拉取重症系统 GCS 记录</a></td>
                  </tr>
                  <tr v-for="(rec, i) in systemGcsList" :key="i"
                      :class="{ selected: selectedSysIndex === i }" @click="selectedSysIndex = i">
                    <td>{{ formatDisplayTime(rec.recordTime) }}</td>
                    <td>{{ rec.recordStaffName || '—' }}</td>
                    <td>{{ gcsRowTotal(rec) }}</td>
                    <td>{{ rec.eye == null ? '—' : rec.eye }}</td>
                    <td>
                      <span v-if="rec.intubated" class="et-tag">ET 插管</span>
                      <span v-else>{{ rec.verbal == null ? '—' : rec.verbal }}</span>
                    </td>
                    <td>{{ rec.motor == null ? '—' : rec.motor }}</td>
                    <td>
                      <a class="pick-link" @click.stop="selectedSysIndex = i">{{ selectedSysIndex === i ? '已选' : '选择' }}</a>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </el-tab-pane>

          <!-- 页签二：手工新建评估 -->
          <el-tab-pane label="新建 GCS 评估" name="manual">
            <div class="gcs-total-bar">
              <span>GCS 总分</span>
              <strong>{{ gcsTotalText }}</strong>
              <em v-if="gcsComplete">（C项得分 = 15 - {{ gcsTotal }} = {{ gcsScorePreview }}）</em>
              <em v-else style="color:#c2410c;">（请在睁眼、言语、运动三项中各选一档，评全后自动计分）</em>
            </div>
            <div class="gcs-row">
              <div class="gcs-row-title">睁眼反应 (E)</div>
              <div class="gcs-options">
                <button v-for="opt in gcsEyeOptions" :key="opt.value" :class="{ active: form.gcsEye === opt.value }" @click="form.gcsEye = opt.value">
                  {{ opt.label }} ({{ opt.value }})
                </button>
              </div>
            </div>
            <div class="gcs-row">
              <div class="gcs-row-title">言语反应 (V)</div>
              <div class="gcs-options">
                <button v-for="opt in gcsVerbalOptions" :key="opt.value" :class="{ active: form.gcsVerbal === opt.value }" @click="form.gcsVerbal = opt.value">
                  {{ opt.label }} ({{ opt.value }})
                </button>
              </div>
            </div>
            <div class="gcs-row">
              <div class="gcs-row-title">运动反应 (M)</div>
              <div class="gcs-options">
                <button v-for="opt in gcsMotorOptions" :key="opt.value" :class="{ active: form.gcsMotor === opt.value }" @click="form.gcsMotor = opt.value">
                  {{ opt.label }} ({{ opt.value }})
                </button>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
        <div class="modal-foot">
          <button class="btn" @click="showGcsModal = false">取消</button>
          <button v-if="gcsTab === 'sys'" class="btn btn-primary" @click="confirmPickSystemGcs">确定选择</button>
          <button v-else class="btn btn-primary" @click="confirmGcs">确认评估</button>
        </div>
      </div>
    </div>

    <!-- 数据来源弹窗 -->
    <div v-if="showSourceModal" class="modal-mask" @click.self="showSourceModal = false">
      <div class="modal">
        <div class="modal-head">
          <h3>{{ sourceMetric }} - 数据来源</h3>
          <button class="modal-close" @click="showSourceModal = false">×</button>
        </div>
        <div class="modal-body">
          <!-- 氧合为派生项：展示 FiO2 / PaO2 / A-aDO2 三要素及各自区间，得分仍为一项 -->
          <div v-if="isOxygenSource" style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:12px;margin-bottom:12px">
            <div style="padding:12px;background:#f5f7fa;border-radius:8px">
              <div style="font-size:12px;color:#999">FiO2（决定评分分支）</div>
              <div style="font-size:22px;font-weight:700;color:#409eff">{{ srcOxygen.fio2 === null ? '—' : srcOxygen.fio2 + ' %' }}</div>
              <div style="font-size:12px;color:#909399">{{ srcOxygen.branchText }}</div>
            </div>
            <div style="padding:12px;background:#f5f7fa;border-radius:8px">
              <div style="font-size:12px;color:#999">PaO2（mmHg）</div>
              <div style="font-size:22px;font-weight:700;color:#409eff">{{ srcOxygen.pao2 === null ? '—' : srcOxygen.pao2 }}</div>
              <div style="font-size:12px;color:#909399">区间 {{ srcOxygen.pao2Range }}</div>
            </div>
            <div style="padding:12px;background:#f5f7fa;border-radius:8px">
              <div style="font-size:12px;color:#999">A-aDO2</div>
              <div style="font-size:22px;font-weight:700;color:#409eff">{{ srcOxygen.aado2 === null ? '—' : Math.round(srcOxygen.aado2 * 10) / 10 }}</div>
              <div style="font-size:12px;color:#909399">区间 {{ srcOxygen.aado2Range }}</div>
            </div>
          </div>
          <div v-if="isOxygenSource" style="display:flex;align-items:center;gap:16px;margin-bottom:16px">
            <div style="padding:10px 16px;background:#ecf5ff;border-radius:8px">
              <span style="font-size:12px;color:#999">氧合得分</span>
              <b style="font-size:22px;color:#409eff;margin-left:8px">{{ getSourceScore() }} 分</b>
            </div>
            <div style="font-size:12px;color:#909399">实际命中：{{ getSourceRange() }}</div>
          </div>

          <div v-else style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:12px;margin-bottom:16px">
            <div style="padding:14px;background:#f5f7fa;border-radius:8px">
              <div style="font-size:12px;color:#999">当前值</div>
              <div style="font-size:24px;font-weight:700;color:#409eff">{{ getSourceValue() }}</div>
            </div>
            <div style="padding:14px;background:#f5f7fa;border-radius:8px">
              <div style="font-size:12px;color:#999">命中区间</div>
              <div style="font-size:20px;font-weight:700;color:#409eff">{{ getSourceRange() }}</div>
            </div>
            <div style="padding:14px;background:#f5f7fa;border-radius:8px">
              <div style="font-size:12px;color:#999">得分</div>
              <div style="font-size:24px;font-weight:700;color:#409eff">{{ getSourceScore() }} 分</div>
            </div>
          </div>
          <div style="border-left:3px solid #409eff;padding-left:10px;margin-bottom:8px"><b>数据趋势</b></div>
          <div ref="sourceTrendChartRef" style="width:100%;height:240px;margin-bottom:16px"></div>
          <div style="border-left:3px solid #409eff;padding-left:10px;margin-bottom:8px"><b>数据来源说明</b></div>
          <div style="font-size:13px;color:#666;line-height:1.8">
            <template v-if="isOxygenSource">
              <div><b>数据表：</b>patient_observe_module_item_record（FiO2） / patient_info_lis_item（血气：氧分压、二氧化碳分压）</div>
              <div><b>取数逻辑：</b>逐管血气按采集时刻就近匹配 FiO2 → 判分支（FiO2≥50% 用 A-aDO2，否则用 PaO2）→ <b>取氧合得分最差的一管</b>作为评分取值；趋势图中已高亮该管时间点</div>
              <div><b>A-aDO2 公式：</b>713 × FiO2(%) − 1.25 × PaCO2 − PaO2（FiO2 取该管血气采集时刻最近的监护记录）</div>
            </template>
            <template v-else>
              <div><b>数据表：</b>patient_observe_module_item_record / patient_info_lis_item</div>
              <div><b>取数逻辑：</b>取取数时间范围内的最差值（偏离正常区间最远）</div>
              <div><b>单位转换：</b>血清肌酐数据库存μmol/L，评分标准用mg/dL（1mg/dL=88.4μmol/L）</div>
            </template>
          </div>
        </div>
        <div class="modal-foot">
          <button class="btn btn-primary" @click="showSourceModal = false">确认</button>
        </div>
      </div>
    </div>

    <!-- 权重对照表弹窗 -->
    <div v-if="showWeightTable" class="modal-mask" @click.self="showWeightTable = false">
      <div class="modal">
        <div class="modal-head">
          <h3>诊断权重对照表</h3>
          <button class="modal-close" @click="showWeightTable = false">×</button>
        </div>
        <div class="modal-body">
          <div class="gcs-tabs">
            <button :class="{ active: weightTab === 'nonoperative' }" @click="weightTab = 'nonoperative'">非手术类</button>
            <button :class="{ active: weightTab === 'operative' }" @click="weightTab = 'operative'">手术类</button>
          </div>
          <table v-if="weightTab === 'nonoperative'" class="gcs-record-table">
            <thead><tr><th>疾病分类</th><th>权重值</th><th>说明</th></tr></thead>
            <tbody>
              <tr v-for="opt in nonoperativeFactors" :key="opt.name">
                <td>{{ opt.name }}</td>
                <td><strong>{{ opt.weight }}</strong></td>
                <td>{{ opt.desc }}</td>
              </tr>
            </tbody>
          </table>
          <table v-else class="gcs-record-table">
            <thead><tr><th>疾病分类</th><th>权重值</th><th>说明</th></tr></thead>
            <tbody>
              <tr v-for="opt in operativeFactors" :key="opt.name">
                <td>{{ opt.name }}</td>
                <td><strong>{{ opt.weight }}</strong></td>
                <td>{{ opt.desc }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="modal-foot">
          <button class="btn btn-primary" @click="showWeightTable = false">关闭</button>
        </div>
      </div>
    </div>

    <!-- 评分文书预览弹窗 -->
    <div v-if="showReportModal" class="modal-mask report-modal-mask" @click.self="showReportModal = false">
      <div class="modal report-modal">
        <div class="modal-head">
          <h3>APACHE II 评分文书预览</h3>
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

    <!-- 离屏文书渲染源（始终渲染，供 html2canvas / 打印 / 入库使用；Teleport 到 body 并移出视口） -->
    <teleport to="body">
    <div class="report-offscreen" aria-hidden="true">
      <div ref="reportRef" class="report-page" style="width:794px;background:#ffffff;padding:30px 36px 34px;color:#000000;font-family:'SimSun','宋体',serif;font-size:12px;line-height:1.6;box-sizing:border-box;">
        <!-- 医院抬头：logo + 三行院名（与 SOFA 评分文书保持一致） -->
        <div style="display:flex;align-items:center;justify-content:center;margin-bottom:8px;">
          <img :src="HOSPITAL_LOGO" style="width:60px;height:60px;margin-right:16px;" />
          <div style="text-align:center;">
            <div style="font-family:'SimHei','黑体',sans-serif;font-size:20px;font-weight:700;letter-spacing:2px;">福州市第二总医院</div>
            <div style="font-family:'SimHei','黑体',sans-serif;font-size:18px;font-weight:700;letter-spacing:2px;">福州市第二医院</div>
            <div style="font-family:'SimHei','黑体',sans-serif;font-size:16px;font-weight:700;letter-spacing:2px;">福建省福州中西医结合医院</div>
          </div>
        </div>
        <!-- 大标题 -->
        <div style="text-align:center;margin-bottom:12px;">
          <div style="font-family:'SimHei','黑体',sans-serif;font-size:23px;font-weight:700;letter-spacing:2px;">危重患者 APACHE II 评分表</div>
        </div>
        <div style="font-size:12px;margin-bottom:3px;line-height:1.8;">
          姓名：{{ patientInfo.name || '—' }}&emsp;&emsp;性别：{{ patientInfo.gender || '—' }}&emsp;&emsp;年龄：{{ patientInfo.age || '—' }}岁&emsp;&emsp;床号：{{ patientInfo.bedCode || '—' }}&emsp;&emsp;住院号：{{ patientInfo.inHospitalNo || inHospitalNo || '—' }}
        </div>
        <div style="font-size:12px;margin-bottom:6px;">诊断：{{ selectedDiagnosisName !== '—' ? selectedDiagnosisName : (patientInfo.diagnosis || '—') }}</div>

        <!-- A 年龄 / B 慢性健康（统一12列固定网格：label15% + 9档各8% + 计分名7% + 分值6%） -->
        <table style="width:100%;table-layout:fixed;border-collapse:collapse;margin:0;font-size:11.5px;text-align:center;">
          <colgroup>
            <col style="width:15%;"><col style="width:8%;"><col style="width:8%;"><col style="width:8%;"><col style="width:8%;"><col style="width:8%;"><col style="width:8%;"><col style="width:8%;"><col style="width:8%;"><col style="width:8%;"><col style="width:7%;"><col style="width:6%;">
          </colgroup>
          <tr>
            <td style="border:1px solid #000;padding:5px 6px;font-weight:600;vertical-align:middle;">A.年龄</td>
            <td colspan="9" style="border:1px solid #000;padding:5px 8px;text-align:left;">
              <span v-for="o in ageOptions" :key="o.label" style="margin-right:18px;white-space:nowrap;">{{ o.label }}&emsp;{{ o.checked ? '☑' : '□' }}&emsp;{{ o.score }}</span>
            </td>
            <td style="border:1px solid #000;padding:5px 4px;">A 计分</td>
            <td style="border:1px solid #000;padding:5px 4px;">{{ scoreResult.ageScore || 0 }}</td>
          </tr>
          <tr>
            <td style="border:1px solid #000;padding:5px 6px;font-weight:600;vertical-align:middle;text-align:left;">B. 有严重器官系统功能不全或免疫损害</td>
            <td colspan="9" style="border:1px solid #000;padding:6px 8px;text-align:left;line-height:1.65;">
              <div style="font-size:10.5px;margin-bottom:5px;">【严重器官功能不全者：①心：心功能IV级；②肺：慢性缺氧、阻塞性或限制性通气障碍、运动耐力差；③肾：慢性透析者；④肝：肝硬化、门脉高压、有上消化道出血史、肝昏迷、肝功能衰竭史。免疫损害：如接受放疗、化疗、长期或大量激素治疗，有白血病、淋巴瘤、艾滋病等。】</div>
              <span style="margin-right:20px;white-space:nowrap;">非手术或择期手术后&emsp;{{ form.chronicHealth==='elective' ? '☑' : '□' }}&emsp;2</span>
              <span style="margin-right:20px;white-space:nowrap;">不能手术或急症手术后&emsp;{{ form.chronicHealth==='nonoperative' ? '☑' : '□' }}&emsp;5</span>
              <span style="white-space:nowrap;">无上述情况&emsp;{{ form.chronicHealth==='none' ? '☑' : '□' }}&emsp;0</span>
            </td>
            <td style="border:1px solid #000;padding:5px 4px;">B 计分</td>
            <td style="border:1px solid #000;padding:5px 4px;">{{ scoreResult.chronicScore || 0 }}</td>
          </tr>
        </table>

        <!-- GCS（8列固定网格：label15% + 6档各12% + 末列13%，左右外边界与上下表对齐），与上表无缝拼接 -->
        <table style="width:100%;table-layout:fixed;border-collapse:collapse;margin:-1px 0 0;font-size:11px;text-align:center;">
          <colgroup>
            <col style="width:15%;"><col style="width:12%;"><col style="width:12%;"><col style="width:12%;"><col style="width:12%;"><col style="width:12%;"><col style="width:12%;"><col style="width:13%;">
          </colgroup>
          <tr style="background:#f5f5f5;font-weight:600;">
            <td style="border:1px solid #000;padding:4px 6px;">GCS评分</td>
            <td v-for="h in GCS_HEADS" :key="h" style="border:1px solid #000;padding:4px 4px;">{{ h }}</td>
            <td style="border:1px solid #000;padding:4px 4px;"></td>
          </tr>
          <tr v-for="g in gcsGrid" :key="g.name">
            <td style="border:1px solid #000;padding:4px 6px;text-align:left;">{{ g.name }}</td>
            <td v-for="(c,i) in g.cells" :key="i" style="border:1px solid #000;padding:3px 4px;line-height:1.4;">{{ c.text ? (c.checked ? '☑ ' : '□ ') + c.text : '' }}</td>
            <td style="border:1px solid #000;padding:3px 4px;"></td>
          </tr>
          <tr>
            <td colspan="2" style="border:1px solid #000;padding:5px 8px;text-align:left;">GCS记分=1+2+3</td>
            <td style="border:1px solid #000;padding:5px 4px;">{{ gcsTotalText }}</td>
            <td colspan="4" style="border:1px solid #000;padding:5px 8px;text-align:right;">C.积分 = 15-GCS</td>
            <td style="border:1px solid #000;padding:5px 4px;">{{ gcsComplete ? (scoreResult.gcsScore || 0) : '—' }}</td>
          </tr>
        </table>

        <!-- D 生理指标（统一12列固定网格：label15% + 9档各8% + 记分名7% + 分值6%，与A/B表竖线严格对齐），无缝拼接 -->
        <table style="width:100%;table-layout:fixed;border-collapse:collapse;margin:-1px 0 0;font-size:10.5px;text-align:center;word-break:break-word;">
          <colgroup>
            <col style="width:15%;"><col style="width:8%;"><col style="width:8%;"><col style="width:8%;"><col style="width:8%;"><col style="width:8%;"><col style="width:8%;"><col style="width:8%;"><col style="width:8%;"><col style="width:8%;"><col style="width:7%;"><col style="width:6%;">
          </colgroup>
          <tr style="background:#f5f5f5;font-weight:600;">
            <td style="border:1px solid #000;padding:5px 4px;">D.生理指标</td>
            <td colspan="9" style="border:1px solid #000;padding:3px 4px;"></td>
            <td style="border:1px solid #000;padding:5px 2px;">D 记分</td>
            <td style="border:1px solid #000;padding:5px 2px;">分值</td>
          </tr>
          <tr style="background:#f5f5f5;font-weight:600;">
            <td style="border:1px solid #000;"></td>
            <td v-for="(c,i) in D_SCORE_COLS" :key="i" style="border:1px solid #000;padding:4px 1px;">{{ c }}</td>
            <td style="border:1px solid #000;"></td>
            <td style="border:1px solid #000;"></td>
          </tr>
          <template v-for="row in dRows" :key="row.key">
            <tr v-if="!row.dual">
              <td style="border:1px solid #000;padding:3px 5px;text-align:left;line-height:1.4;">{{ row.label }}<div v-if="row.sub" style="font-size:9.5px;margin-top:1px;">{{ row.sub }}</div></td>
              <td v-for="(cell,ci) in row.cells" :key="ci" style="border:1px solid #000;padding:2px 2px;line-height:1.35;">{{ cell ? box(row.hit, ci) + ' ' + cell : '' }}</td>
              <td style="border:1px solid #000;"></td>
              <td style="border:1px solid #000;padding:3px 2px;font-weight:600;">{{ row.score }}</td>
            </tr>
            <tr v-else>
              <td style="border:1px solid #000;padding:3px 5px;text-align:left;line-height:1.5;">
                <div>{{ row.labelTop }}</div><div style="font-size:9.5px;">{{ row.labelSub }}</div><div style="margin-top:1px;">{{ row.labelTop2 }}</div>
              </td>
              <td v-for="(cell,ci) in row.paCells" :key="ci" style="border:1px solid #000;padding:2px 2px;line-height:1.5;">
                <div>{{ cell ? box(row.paHit, ci) + ' ' + cell : '\u00A0' }}</div>
                <div>{{ row.aaCells[ci] ? box(row.aaHit, ci) + ' ' + row.aaCells[ci] : '\u00A0' }}</div>
              </td>
              <td style="border:1px solid #000;"></td>
              <td style="border:1px solid #000;padding:3px 2px;font-weight:600;">{{ row.score }}</td>
            </tr>
          </template>
          <tr>
            <td colspan="11" style="border:1px solid #000;padding:5px 8px;text-align:left;">D 积分</td>
            <td style="border:1px solid #000;padding:5px 2px;font-weight:600;">{{ scoreResult.physiologyScore || 0 }}</td>
          </tr>
          <tr>
            <td colspan="11" style="border:1px solid #000;padding:6px 8px;text-align:left;">APACHE II 总积分=A+B+C+D</td>
            <td style="border:1px solid #000;padding:6px 2px;font-weight:700;font-size:13px;">{{ scoreResult.totalScore || 0 }}</td>
          </tr>
        </table>

        <!-- 表底注释 -->
        <div style="font-size:10.5px;line-height:1.7;margin-top:9px;">
          注：1. 数据采集应为病人入ICU或抢救开始后24小时内最差值<br>
          2. B项中“不能手术”应理解为由于病人病情危重不能接受手术治疗者<br>
          3. 严重器官功能不全者：①心：心功能IV级；②肺：慢性缺氧、阻塞性或限制性通气障碍、运动耐力差；③肾：慢性透析者；④肝：肝硬化、门脉高压、有上消化道出血史、肝昏迷、肝功能衰竭史。免疫损害：如接受放疗、化疗、长期或大量激素治疗，有白血病、淋巴瘤、艾滋病等。<br>
          4. D项中的血压值应为平均动脉压=（收缩压+2*舒张压）/3，若有直接动脉压检测则记直接动脉压<br>
          6. 呼吸频率应记录为病人的自主呼吸频率<br>
          7. 如果病人急性肾功能衰竭，则血清肌酐一项分值应在原基础上加倍（*2）
        </div>

        <!-- 评分医师（含电子签名）/ 评分时间：右下角一行（导出 PDF 可见） -->
        <div style="display:flex;justify-content:flex-end;align-items:center;font-size:12px;margin-top:16px;">
          <div style="margin-right:36px;display:flex;align-items:center;">
            <span>评分医师：</span>
            <img v-if="doctorSignature" :src="doctorSignature" alt="电子签名" style="height:38px;" />
            <span v-else>{{ realname || username || '—' }}</span>
          </div>
          <div>评分时间：{{ formatDisplayTime(form.scoreTime) }}</div>
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
import * as echarts from '../utils/echarts'
import { jsPDF } from 'jspdf'
import html2canvas from 'html2canvas'
import axios from 'axios'
import request from '../api/request'
import { getExternalHeaders, isExternalMode } from '../utils/external'
import { getAuthHeaders, getUser } from '../utils/auth'
import { useStaffSignature } from '../utils/staffSignature'
import { operatorLabel } from '../utils/operator'

const route = useRoute()
const inHospitalNo = ref(route.query.inHospitalNo || '')
const patientName = ref(route.query.patientName || '')
const departCode = ref(route.query.departCode || '')
// 身份三级回退，与 MainLayout.userName / SofaScore 同一口径：
//   外链 URL 参数 → 外链会话缓存（站内跳转后 URL 不再带 realname）→ 直连登录用户。
// 缺了登录这一级，站内登录进来 realname/username 全空，「评分医师」显示「—」且不会被自动带出。
const loginUser = getUser()
const extOperator = sessionStorage.getItem('extOperator')
const username = ref(route.query.username || (loginUser && loginUser.username) || '')
const realname = ref(route.query.realname || extOperator
  || (loginUser && (loginUser.realName || loginUser.username)) || '')

// 是否外链访问：外链下隐藏系统内重复的患者信息条（外层重症系统已展示）
const isExternal = isExternalMode()

/** 院徽静态资源：frontend/public/logo.png，构建后随 dist 输出（文书抬头用，与 SOFA 一致） */
const HOSPITAL_LOGO = '/logo.png'

const patientInfo = reactive({
  patientId: '',
  name: '',
  gender: '',
  age: '',
  bedCode: '',
  inHospitalNo: '',
  inDepartTime: '',
  // 入科诊断（patient_info.diagnosis_content），文书预览/导出展示用
  diagnosis: ''
})

const records = ref([])
const currentRecord = ref(null)

const form = reactive({
  age: null,
  chronicHealth: 'none',
  gcsEye: null,
  gcsVerbal: null,
  gcsMotor: null,
  temperature: null,
  map: null,
  heartRate: null,
  respiratoryRate: null,
  fio2: null,
  aado2: null,
  pao2: null,
  ph: null,
  sodium: null,
  potassium: null,
  creatinine: null,
  hct: null,
  wbc: null,
  acuteRenalFailure: false,
  diagnosisType: 'none',
  emergencySurgery: false,
  selectedNonopFactor: '',
  selectedOpFactor: '',
  scoreTime: '',
  doctor: '',
  remark: ''
})

const scoreResult = reactive({
  ageScore: 0,
  chronicScore: 0,
  gcsScore: 0,
  physiologyScore: 0,
  totalScore: 0,
  mortalityRate: 0,
  apsScores: {}
})

const fetchStartTime = ref('')
const fetchEndTime = ref('')
const fetchPreset = ref('admission_after')
/** 手改时间后尚未重新取数的标记（仅作提示，不阻断操作） */
const rangeDirty = ref(false)

// 文书预览/导出
const showReportModal = ref(false)
const reportRef = ref(null)
const reportViewRef = ref(null)
const reportGenerating = ref(false)
const saving = ref(false)

// 评分医师电子签名（按工号 username 反查 ICU CA 库；取不到则为空，文书不显示签名）
const { signatureSrc: doctorSignature, load: loadDoctorSignature } = useStaffSignature()

const apsItems = [
  { key: 'temperature', label: '体温', unit: '℃', step: 0.1, itemCode: 'oi_tiwen', dataType: 'observe' },
  { key: 'map', label: '平均动脉压', unit: 'mmHg', step: 1, itemCode: 'oi_map', dataType: 'observe' },
  { key: 'heartRate', label: '心率', unit: '次/分', step: 1, itemCode: 'oi_hr', dataType: 'observe' },
  { key: 'respiratoryRate', label: '呼吸频率', unit: '次/分', step: 1, itemCode: 'oi_hxpl', dataType: 'observe' },
  { key: 'oxygen', label: '氧合', unit: 'FiO2≥0.5用A-aDO2', step: 1, itemCode: '', dataType: 'calc' },
  { key: 'ph', label: '动脉血pH', unit: '', step: 0.01, itemCode: 'PH值', dataType: 'lab' },
  { key: 'sodium', label: '血清钠', unit: 'mmol/L', step: 1, itemCode: '100150,5039', dataType: 'lab' },
  { key: 'potassium', label: '血清钾', unit: 'mmol/L', step: 0.1, itemCode: '100140,5040', dataType: 'lab' },
  { key: 'creatinine', label: '血清肌酐', unit: 'μmol/L', step: 1, itemCode: '100210', dataType: 'lab' },
  { key: 'hct', label: '血细胞比容', unit: '%', step: 1, itemCode: '200060', dataType: 'lab' },
  { key: 'wbc', label: '白细胞', unit: '×10⁹/L', step: 0.1, itemCode: '200010', dataType: 'lab' }
]

const gcsEyeOptions = [
  { value: 4, label: '自发睁眼' },
  { value: 3, label: '语言命令睁眼' },
  { value: 2, label: '疼痛刺激睁眼' },
  { value: 1, label: '无反应' }
]
const gcsVerbalOptions = [
  { value: 5, label: '定向力正常' },
  { value: 4, label: '意识模糊' },
  { value: 3, label: '言语不当' },
  { value: 2, label: '难以理解' },
  { value: 1, label: '无反应' }
]
const gcsMotorOptions = [
  { value: 6, label: '遵嘱活动' },
  { value: 5, label: '定位疼痛' },
  { value: 4, label: '躲避疼痛' },
  { value: 3, label: '异常屈曲' },
  { value: 2, label: '异常伸展' },
  { value: 1, label: '无反应' }
]

const nonoperativeFactors = [
  { name: '严重疾病', weight: -1.150, desc: '呼吸衰竭、心衰、肝硬化等' },
  { name: '中度疾病', weight: -0.705, desc: '肺炎、肾盂肾炎等' },
  { name: '轻度疾病', weight: -0.501, desc: '轻度感染等' },
  { name: '药物/酒精中毒', weight: -2.108, desc: '急性中毒' },
  { name: '心律失常', weight: -1.798, desc: '严重心律失常' },
  { name: '脑血管意外', weight: -0.937, desc: '脑卒中' }
]
const operativeFactors = [
  { name: '术后严重疾病', weight: -1.376, desc: '大手术后并发症' },
  { name: '术后中度疾病', weight: -0.587, desc: '术后感染' },
  { name: '术后轻度疾病', weight: -0.280, desc: '术后恢复' },
  { name: '心脏手术', weight: -1.261, desc: '心脏直视手术后' },
  { name: '神经外科手术', weight: -0.816, desc: '颅脑手术后' },
  { name: '腹部手术', weight: -0.465, desc: '腹部大手术后' }
]

// GCS 标准规则：E睁眼1-4、V言语1-5、M运动1-6，三项必须各选一档，总分严格 3-15。
// 任一缺评或越权一律视为“未评全”(null)，不允许只选一两项就拼出分数；Number 强转避免字符串拼接。
const gcsInRange = (v, lo, hi) => { const n = Number(v); return Number.isInteger(n) && n >= lo && n <= hi }
const gcsComplete = computed(() => gcsInRange(form.gcsEye, 1, 4) && gcsInRange(form.gcsVerbal, 1, 5) && gcsInRange(form.gcsMotor, 1, 6))
const gcsTotal = computed(() => gcsComplete.value ? Number(form.gcsEye) + Number(form.gcsVerbal) + Number(form.gcsMotor) : null)
// C 项预览 = 15 - GCS（评全才有值，未评全为 null 不计分）
const gcsScorePreview = computed(() => gcsComplete.value ? 15 - gcsTotal.value : null)
const gcsTotalText = computed(() => gcsComplete.value ? gcsTotal.value : '—')
const gcsDimText = (v) => (v === null || v === undefined || v === '' ? '—' : v)
const diagnosisWeight = computed(() => {
  if (form.diagnosisType === 'nonoperative' && form.selectedNonopFactor) {
    const f = nonoperativeFactors.find(o => o.name === form.selectedNonopFactor)
    return f ? f.weight : 0
  }
  if (form.diagnosisType === 'operative' && form.selectedOpFactor) {
    const f = operativeFactors.find(o => o.name === form.selectedOpFactor)
    return f ? f.weight : 0
  }
  return 0
})

// ============ 评分文书 ============
function num(v) {
  if (v === null || v === undefined || v === '') return null
  const n = Number(v)
  return Number.isNaN(n) ? null : n
}
// 勾选框：命中输出 ☑，否则 □
function box(hit, col) { return hit === col ? '☑' : '□' }
// D 表分值列头（左到右：+4 +3 +2 +1 0 +1 +2 +3 +4）
const D_SCORE_COLS = ['+4', '+3', '+2', '+1', '0', '+1', '+2', '+3', '+4']

// A 年龄选项（label, 命中判定）
const ageOptions = computed(() => {
  const a = num(form.age)
  const hit = a === null ? -1 : a <= 44 ? 0 : a <= 54 ? 1 : a <= 64 ? 2 : a <= 74 ? 3 : 4
  return [
    { label: '≤44', score: 0 }, { label: '45-54', score: 2 }, { label: '55-64', score: 3 },
    { label: '65-74', score: 5 }, { label: '≥75', score: 6 }
  ].map((o, i) => ({ ...o, checked: i === hit }))
})
// D 生理指标表：每项 9 列区间文本 + 命中列（列号 0-8，-1 表示无值）
const dRows = computed(() => {
  const sc = scoreResult.apsScores || {}
  const t = num(form.temperature), mp = num(form.map), hr = num(form.heartRate), rr = num(form.respiratoryRate)
  const ph = num(form.ph), na = num(form.sodium), k = num(form.potassium)
  const cr = num(form.creatinine), hct = num(form.hct), wbc = num(form.wbc)
  const fio2 = num(form.fio2), pao2 = num(form.pao2), aado2 = num(form.aado2)
  const hit = (v, fn) => (v === null ? -1 : fn(v))
  // 氧合：FiO2≥50 走 A-aDO2（上排按参考图为PaO2、下排A-aDO2），否则走 PaO2
  // PaO2 列头为 >70 / 61-70 / 55-60 / <55（对应列索引 4 / 5 / 7 / 8），
  // 阈值必须与后端 calcOxygenScore 完全一致，否则文书打勾位置与实际分值不符
  const paHit = (fio2 === null || fio2 >= 50) ? -1 : (pao2 === null ? -1 : pao2 < 55 ? 8 : pao2 <= 60 ? 7 : pao2 <= 70 ? 5 : 4)
  const aaHit = (fio2 === null || fio2 < 50) ? -1 : (aado2 === null ? -1 : aado2 >= 500 ? 0 : aado2 >= 350 ? 1 : aado2 >= 200 ? 2 : 4)
  return [
    { key: 'temperature', label: '1.体温（腋下℃）',
      cells: ['≥41', '39-40.9', '', '38.5-38.9', '36-38.4', '34-35.9', '32-33.9', '30-31.9', '≤29.9'],
      hit: hit(t, v => v >= 41 ? 0 : v >= 39 ? 1 : v >= 38.5 ? 3 : v >= 36 ? 4 : v >= 34 ? 5 : v >= 32 ? 6 : v >= 30 ? 7 : 8), score: sc.temperature || 0 },
    { key: 'map', label: '2.平均血压（mmHg）',
      cells: ['≥160', '130-159', '110-129', '', '70-109', '', '50-69', '', '≤49'],
      hit: hit(mp, v => v >= 160 ? 0 : v >= 130 ? 1 : v >= 110 ? 2 : v >= 70 ? 4 : v >= 50 ? 6 : 8), score: sc.map || 0 },
    { key: 'heartRate', label: '3.心率（次/分）',
      cells: ['≥180', '140-179', '110-139', '', '70-109', '', '55-69', '40-54', '≤39'],
      hit: hit(hr, v => v >= 180 ? 0 : v >= 140 ? 1 : v >= 110 ? 2 : v >= 70 ? 4 : v >= 55 ? 6 : v >= 40 ? 7 : 8), score: sc.heartRate || 0 },
    { key: 'respiratoryRate', label: '4.呼吸频率（次/分）',
      cells: ['≥50', '35-49', '', '25-34', '12-24', '10-11', '55-69', '', '≤5'],
      hit: hit(rr, v => v >= 50 ? 0 : v >= 35 ? 1 : v >= 25 ? 3 : v >= 12 ? 4 : v >= 10 ? 5 : v >= 6 ? 6 : 8), score: sc.respiratoryRate || 0 },
    // 5 氧合：双行（上 PaO2 / 下 A-aDO2），文字照纸质表
    { key: 'oxygen', dual: true,
      labelTop: '5.PaO2(mmHg)', labelSub: '（FiO2≤50%）', labelTop2: 'A-aDO2(FiO2≥50%)',
      paCells: ['', '', '', '', '>70', '61-70', '', '55-60', '<55'], paHit,
      aaCells: ['≥500', '350-499', '200-349', '', '<200', '', '', '', ''], aaHit,
      score: sc.oxygen || 0 },
    // 6 动脉血PH / 血清HCO3（无血气时用）：双行，系统按 PH 评分，HCO3 行仅展示区间
    { key: 'ph', dual: true,
      labelTop: '6.动脉血 PH', labelSub: '血清 HCO3（mmol/L）', labelTop2: '（无血气时用）',
      paCells: ['≥7.7', '7.6-7.69', '', '7.5-7.59', '7.33-7.49', '', '7.25-7.32', '7.15-7.24', '<7.15'],
      paHit: hit(ph, v => v >= 7.7 ? 0 : v >= 7.6 ? 1 : v >= 7.5 ? 3 : v >= 7.33 ? 4 : v >= 7.25 ? 6 : v >= 7.15 ? 7 : 8),
      aaCells: ['≥52', '41-51.9', '', '32-40.9', '23-31.9', '', '18-21.9', '15-17.9', '<15'], aaHit: -1,
      score: sc.ph || 0 },
    { key: 'sodium', label: '7.血清 Na（mmol/L）',
      cells: ['≥180', '160-179', '155-159', '150-154', '130-149', '', '120-129', '111-119', '≤110'],
      hit: hit(na, v => v >= 180 ? 0 : v >= 160 ? 1 : v >= 155 ? 2 : v >= 150 ? 3 : v >= 130 ? 4 : v >= 120 ? 6 : v >= 111 ? 7 : 8), score: sc.sodium || 0 },
    { key: 'potassium', label: '8.血清 K（mmol/L）',
      cells: ['≥7', '6-6.9', '', '5.5-5.9', '3.5-5.4', '3-3.4', '2.5-2.9', '', '<2.5'],
      hit: hit(k, v => v >= 7 ? 0 : v >= 6 ? 1 : v >= 5.5 ? 3 : v >= 3.5 ? 4 : v >= 3 ? 5 : v >= 2.5 ? 6 : 8), score: sc.potassium || 0 },
    { key: 'creatinine', label: '9.血清肌酐（umol/L）', sub: (form.acuteRenalFailure ? '☑' : '□') + ' 急性肾功能衰竭',
      cells: ['≥305', '172-304', '128-171', '', '53-127', '', '<53', '', ''],
      hit: hit(cr, v => v >= 309 ? 0 : v >= 177 ? 1 : v >= 133 ? 2 : v >= 53 ? 4 : 6), score: sc.creatinine || 0 },
    { key: 'hct', label: '10.血球压积（%）',
      cells: ['≥60', '', '50-59.9', '46-49.9', '30-45.9', '', '20-29.9', '', '<20'],
      hit: hit(hct, v => v >= 60 ? 0 : v >= 50 ? 2 : v >= 46 ? 3 : v >= 30 ? 4 : v >= 20 ? 6 : 8), score: sc.hct || 0 },
    { key: 'wbc', label: '11.WBC（*1000）',
      cells: ['≥40', '', '20-39.9', '15-19.9', '3-14.9', '', '1-2.9', '', '<1'],
      hit: hit(wbc, v => v >= 40 ? 0 : v >= 20 ? 2 : v >= 15 ? 3 : v >= 3 ? 4 : v >= 1 ? 6 : 8), score: sc.wbc || 0 }
  ]
})
// GCS 勾选网格：列头 6→1，三行（睁眼/语言/运动），每格 {text,checked}；文案对齐纸质评分表
const GCS_HEADS = [6, 5, 4, 3, 2, 1]
const GCS_PAPER = {
  eye: { 4: '自动睁眼', 3: '呼唤睁眼', 2: '刺痛睁眼', 1: '不能睁眼' },
  verbal: { 5: '回答切题', 4: '回答不切题', 3: '答非所问', 2: '只能发音', 1: '不能言语' },
  motor: { 6: '按吩咐动作', 5: '刺痛能定位', 4: '刺痛能躲避', 3: '刺痛肢体屈曲', 2: '刺痛肢体伸展', 1: '不能活动' }
}
const gcsGrid = computed(() => {
  const build = (map, cur) => GCS_HEADS.map(h => {
    const text = map[h] || ''
    return { text, checked: text !== '' && Number(cur) === h }
  })
  return [
    { name: '1. 睁眼反应', cells: build(GCS_PAPER.eye, form.gcsEye) },
    { name: '2. 语言反应', cells: build(GCS_PAPER.verbal, form.gcsVerbal) },
    { name: '3. 运动反应', cells: build(GCS_PAPER.motor, form.gcsMotor) }
  ]
})
const selectedDiagnosisName = computed(() => {
  if (form.diagnosisType === 'nonoperative') return form.selectedNonopFactor || '—'
  if (form.diagnosisType === 'operative') return form.selectedOpFactor || '—'
  return '—'
})
/** 数值统一保留两位小数（空值/非数字按 0.00 显示） */
function fmt2(v) {
  const n = Number(v)
  return isNaN(n) ? '0.00' : n.toFixed(2)
}

const showGcsModal = ref(false)
// 从重症系统同步 GCS（Z_ICU_GCS）
const systemGcsList = ref([])
const gcsSyncLoading = ref(false)
const gcsSyncLoaded = ref(false)
const gcsTab = ref('sys')              // sys=选择已有记录 / manual=手工新建
const selectedSysIndex = ref(-1)       // 系统记录表格当前选中行
const showSourceModal = ref(false)
const showWeightTable = ref(false)
const weightTab = ref('nonoperative')
const sourceMetric = ref('')
const sourceTrendChartRef = ref(null)
let sourceTrendChart = null

// 氧合是派生项（FiO2 决定评分分支，PaO2 或 A-aDO2 出分），
// 来源弹窗需展示三要素而非单一“当前值”，故单独计算。
const isOxygenSource = computed(() => sourceMetric.value === '氧合')
const srcOxygen = computed(() => {
  const fio2 = num(form.fio2), pao2 = num(form.pao2), aado2 = num(form.aado2)
  return {
    fio2, pao2, aado2,
    branchText: fio2 === null ? '未取到 FiO2' : (fio2 >= 50 ? 'FiO2≥50%：按 A-aDO2 评分' : 'FiO2<50%：按 PaO2 评分'),
    // 区间阈值与后端 calcOxygenScore 保持一致
    pao2Range: pao2 === null ? '—' : (pao2 < 55 ? '<55' : pao2 <= 60 ? '55-60' : pao2 <= 70 ? '61-70' : '>70'),
    aado2Range: aado2 === null ? '—' : (aado2 >= 500 ? '≥500' : aado2 >= 350 ? '350-499' : aado2 >= 200 ? '200-349' : '<200')
  }
})

onMounted(async () => {
  patientInfo.name = patientName.value
  patientInfo.inHospitalNo = inHospitalNo.value
  const now = new Date()
  form.scoreTime = formatLocalDateTime(now)
  form.doctor = realname.value || username.value || ''
  // 获取患者详细信息
  await loadPatientInfo()
  // 应用默认取数范围（入科后24小时）
  applyFetchPreset()
  await loadRecords()
  calculateScore()
  // 电子签名独立于评分流程，放最后加载，不阻塞上面的取数
  await loadDoctorSignature(username.value)
})

async function loadPatientInfo() {
  if (!inHospitalNo.value) return
  try {
    const res = await request.get(`/apache2/patient-info/${inHospitalNo.value}`)
    if (res) {
      const data = res
      patientInfo.patientId = data.patientId || ''
      patientInfo.name = data.name || patientInfo.name
      patientInfo.gender = data.gender || ''
      patientInfo.age = data.age || ''
      patientInfo.bedCode = data.bedCode || ''
      patientInfo.inDepartTime = data.inDepartTime || ''
      patientInfo.diagnosis = data.diagnosis || ''
      if (data.age) form.age = parseInt(data.age)
    }
  } catch (e) {
    console.error('获取患者信息失败', e)
  }
}

async function loadRecords() {
  if (!inHospitalNo.value) return
  try {
    const res = await request.get(`/apache2/patient/${inHospitalNo.value}/records`)
    if (res) {
      records.value = res || []
      if (records.value.length > 0 && !currentRecord.value) {
        selectRecord(records.value[0])
      }
    }
  } catch (e) {
    console.error('加载评分记录失败', e)
  }
}

function selectRecord(rec) {
  currentRecord.value = rec
  if (rec.apsData) {
    try {
      const aps = JSON.parse(rec.apsData)
      Object.keys(aps).forEach(k => {
        if (Object.prototype.hasOwnProperty.call(form, k)) form[k] = aps[k]
      })
    } catch (e) {}
  }
  form.age = rec.age || form.age
  form.chronicHealth = rec.chronicHealth || 'none'
  form.diagnosisType = rec.diagnosisType || 'none'
  form.emergencySurgery = rec.emergencySurgery === 1
  form.acuteRenalFailure = form.acuteRenalFailure === true
  form.remark = rec.remark || ''
  form.scoreTime = rec.scoreTime ? toLocalInput(rec.scoreTime) : form.scoreTime
  // createBy 可能是服务端占位值 unknown（未解析到身份），不能当名字回填到「评分医师」
  form.doctor = operatorLabel(rec.createBy) || form.doctor || ''
  fetchStartTime.value = rec.dataStartTime ? toLocalInput(rec.dataStartTime) : fetchStartTime.value
  fetchEndTime.value = rec.dataEndTime ? toLocalInput(rec.dataEndTime) : fetchEndTime.value
  calculateScore()
}

// ---- 来源两态（自动评分 / 手工评分）：显示口径与 SOFA 完全一致 ----
// 库里只有 auto（系统自动初评）与 custom（医生保存，含对自动初评的复核确认）等取值；
// 历史设计里的 reviewed 从未落过库，相关分支已清除，不要再加回来。

/** 自动类来源：自动初评 auto、每日定时 daily */
function isAutoRecord(r) {
  return !!r && (r.scoreType === 'auto' || r.scoreType === 'daily')
}

function scoreTypeLabel(r) {
  if (!r) return ''
  return isAutoRecord(r) ? '自动评分' : '手工评分'
}

function recTagClass(r) {
  if (!r) return 'manual'
  return isAutoRecord(r) ? 'auto' : 'manual'
}

function addRecord() {
  currentRecord.value = null
  form.age = patientInfo.age ? parseInt(patientInfo.age) : null
  form.chronicHealth = 'none'
  form.gcsEye = null
  form.gcsVerbal = null
  form.gcsMotor = null
  form.temperature = null
  form.map = null
  form.heartRate = null
  form.respiratoryRate = null
  form.fio2 = null
  form.aado2 = null
  form.pao2 = null
  form.ph = null
  form.sodium = null
  form.potassium = null
  form.creatinine = null
  form.hct = null
  form.wbc = null
  form.acuteRenalFailure = false
  form.diagnosisType = 'none'
  form.emergencySurgery = false
  form.selectedNonopFactor = ''
  form.selectedOpFactor = ''
  form.remark = ''
  form.doctor = realname.value || username.value || ''
  const now = new Date()
  form.scoreTime = formatLocalDateTime(now)
  // 清空评分结果（等待自动取数或手动填写）
  Object.assign(scoreResult, { ageScore: 0, chronicScore: 0, gcsScore: 0, physiologyScore: 0, totalScore: 0, mortalityRate: 0, apsScores: {} })
  ElMessage.success('已创建新评分，请点"自动获取并计算"或手动填写')
}

function quickRange(hours) {
  const end = new Date()
  const start = new Date(end.getTime() - hours * 3600 * 1000)
  fetchStartTime.value = formatLocalDateTime(start)
  fetchEndTime.value = formatLocalDateTime(end)
}

function formatLocalDateTime(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const h = String(date.getHours()).padStart(2, '0')
  const min = String(date.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${d}T${h}:${min}`
}

// 任意时间值 → datetime-local 输入框需要的 'YYYY-MM-DDTHH:mm'（带T、到分），保证回填时控件能正确回显
function toLocalInput(v) {
  if (!v) return ''
  const s = String(v).replace('T', ' ').trim()
  return s.slice(0, 16).replace(' ', 'T')
}
// 任意时间值 → 后端 LocalDateTime 需要的 'YYYY-MM-DD HH:mm:ss'：到分补秒、已到秒不再重复补（修复二次保存拼出 :ss:00 报错）
function toBackendDateTime(v) {
  if (!v) return null
  const s = String(v).replace('T', ' ').trim()
  if (s.length >= 19) return s.slice(0, 19)
  if (s.length === 16) return s + ':00'
  return s
}

// 格式化显示时间（处理后端返回的ISO格式，如2026-09-01T08:00:00.000+00:00）
function formatDisplayTime(timeStr) {
  if (!timeStr) return '—'
  try {
    // 如果已经是 yyyy-MM-dd HH:mm:ss 格式，直接返回
    if (/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}/.test(timeStr)) {
      return timeStr.slice(0, 16)
    }
    const d = new Date(timeStr)
    if (isNaN(d.getTime())) return timeStr
    const y = d.getFullYear()
    const m = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    const h = String(d.getHours()).padStart(2, '0')
    const min = String(d.getMinutes()).padStart(2, '0')
    return `${y}-${m}-${day} ${h}:${min}`
  } catch (e) {
    return timeStr
  }
}

function setAdmissionTime(after = true) {
  if (!patientInfo.inDepartTime) {
    ElMessage.warning('未获取到入科时间')
    return
  }
  const start = new Date(patientInfo.inDepartTime)
  if (after) {
    // 入科后24小时
    fetchStartTime.value = formatLocalDateTime(start)
    fetchEndTime.value = formatLocalDateTime(new Date(start.getTime() + 24 * 3600 * 1000))
  } else {
    // 入科前24小时
    fetchStartTime.value = formatLocalDateTime(new Date(start.getTime() - 24 * 3600 * 1000))
    fetchEndTime.value = formatLocalDateTime(start)
  }
}

function applyFetchPreset() {
  switch (fetchPreset.value) {
    case '24h':
      quickRange(24)
      break
    case '48h':
      quickRange(48)
      break
    case 'admission_after':
      setAdmissionTime(true)
      break
    case 'admission_before':
      setAdmissionTime(false)
      break
    case 'custom':
      break
  }
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
  const s = parseLocalInput(fetchStartTime.value)
  const e = parseLocalInput(fetchEndTime.value)
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

/** 快捷区间按钮：设置时间范围后立即重新取数（与 SOFA 统一），并高亮当前选中项 */
async function setRangePreset(key) {
  fetchPreset.value = key
  applyFetchPreset()
  await autoFetchAndCalc()
}

/** 手动修改时间：切到「自定义」态并标记待取数（不自动请求，避免输入过程中反复打后端） */
function onFetchTimeChange() {
  fetchPreset.value = 'custom'
  rangeDirty.value = true
}

async function autoFetchData() {
  if (!patientInfo.patientId) {
    ElMessage.warning('未获取到患者ID，请刷新页面重试')
    return
  }
  ElMessage.info('正在自动获取数据...')
  try {
    const res = await request.get(`/apache2/auto-fetch/${patientInfo.patientId}`, {
      params: { startTime: toBackendDateTime(fetchStartTime.value), endTime: toBackendDateTime(fetchEndTime.value) }
    })
    if (res) {
      const data = res
      // 监护数据
      if (data.temperature !== null && data.temperature !== undefined) form.temperature = data.temperature
      if (data.heartRate !== null && data.heartRate !== undefined) form.heartRate = data.heartRate
      if (data.respiratoryRate !== null && data.respiratoryRate !== undefined) form.respiratoryRate = data.respiratoryRate
      if (data.map !== null && data.map !== undefined) form.map = data.map
      if (data.fio2 !== null && data.fio2 !== undefined) form.fio2 = data.fio2
      if (data.aado2 !== null && data.aado2 !== undefined) form.aado2 = Math.round(data.aado2 * 10) / 10
      // 检验数据
      if (data.sodium !== null && data.sodium !== undefined) form.sodium = data.sodium
      if (data.potassium !== null && data.potassium !== undefined) form.potassium = data.potassium
      if (data.creatinine !== null && data.creatinine !== undefined) form.creatinine = data.creatinine
      if (data.hct !== null && data.hct !== undefined) form.hct = data.hct
      if (data.wbc !== null && data.wbc !== undefined) form.wbc = data.wbc
      // 血气分析
      if (data.ph !== null && data.ph !== undefined) form.ph = data.ph
      if (data.pao2 !== null && data.pao2 !== undefined) form.pao2 = data.pao2
      // 患者信息
      if (data.age) form.age = data.age
      // GCS：取数范围内最新一条评全（非插管）的系统 GCS，同步到睁眼/言语/运动三项
      const gcsSynced = data.gcsEye != null && data.gcsVerbal != null && data.gcsMotor != null
      if (gcsSynced) {
        form.gcsEye = data.gcsEye
        form.gcsVerbal = data.gcsVerbal
        form.gcsMotor = data.gcsMotor
      }
      ElMessage.success(gcsSynced
        ? `自动取数完成，已同步系统 GCS（E${data.gcsEye}V${data.gcsVerbal}M${data.gcsMotor}${data.gcsRecordTime ? '，' + formatDisplayTime(data.gcsRecordTime) : ''}）`
        : '自动取数完成；取数范围内无「评全且非插管」的系统 GCS，C 项请手工评定')
      calculateScore()
    } else {
      ElMessage.warning('自动取数返回空，请手动填写')
    }
  } catch (e) {
    ElMessage.warning('自动取数失败，请手动填写: ' + e.message)
  }
}

async function autoFetchAndCalc() {
  // 统一入口校验：快捷区间 / 手动点按钮都走这里
  if (!(await validateRange())) return
  rangeDirty.value = false
  await autoFetchData()
}

async function calculateScore() {
  // 获取选中的诊断权重
  let diagnosisWeight = 0
  if (form.diagnosisType === 'nonoperative' && form.selectedNonopFactor) {
    const opt = nonoperativeFactors.find(o => o.name === form.selectedNonopFactor)
    if (opt) diagnosisWeight = opt.weight
  } else if (form.diagnosisType === 'operative' && form.selectedOpFactor) {
    const opt = operativeFactors.find(o => o.name === form.selectedOpFactor)
    if (opt) diagnosisWeight = opt.weight
  }
  const params = {
    age: form.age,
    chronicHealth: form.chronicHealth,
    gcsTotal: gcsTotal.value,
    temperature: form.temperature,
    map: form.map,
    heartRate: form.heartRate,
    respiratoryRate: form.respiratoryRate,
    fio2: form.fio2,
    pao2: form.pao2,
    aado2: form.aado2,
    ph: form.ph,
    sodium: form.sodium,
    potassium: form.potassium,
    creatinine: form.creatinine,
    hct: form.hct,
    wbc: form.wbc,
    acuteRenalFailure: form.acuteRenalFailure,
    diagnosisType: form.diagnosisType,
    emergencySurgery: form.emergencySurgery,
    diagnosisWeight: diagnosisWeight
  }
  try {
    const res = await request.post('/apache2/calculate', params)
    if (res) {
      const data = res
      scoreResult.ageScore = data.ageScore
      scoreResult.chronicScore = data.chronicScore
      scoreResult.gcsScore = data.gcsScore
      scoreResult.physiologyScore = data.physiologyScore
      scoreResult.totalScore = data.totalScore
      scoreResult.mortalityRate = data.mortalityRate
      scoreResult.apsScores = data.apsScores || {}
    }
  } catch (e) {
    console.error('计算评分失败', e)
  }
}

async function saveRecord() {
  if (saving.value) return
  // GCS 三项未评全时 C 项按0计（等同于假设 GCS=15 清醒），会低估病情严重度，需医生明确知情后再保存
  if (!gcsComplete.value) {
    try {
      await ElMessageBox.confirm(
        'GCS 的睁眼/言语/运动三项尚未全部评定，C 项将按 0 分计入（可能低估病情严重度）。是否仍要保存？',
        'GCS 未评全',
        { confirmButtonText: '仍要保存', cancelButtonText: '返回补评', type: 'warning' }
      )
    } catch (e) {
      return
    }
  }
  // 点击立即置灰，避免生成PDF阶段无反馈；整个保存过程都在 try/finally 内，任何异常都会复位
  saving.value = true
  // 保存前先刷新评分，保证分数与当前数据一致（计算失败不阻断保存）
  try {
    try { await calculateScore() } catch (e) { console.error('保存前刷新评分失败', e) }
    await nextTick()
    // 【PDF 与主保存并行】此刻表单/离屏文书仍是当前数据，先同步启动文书渲染（html2canvas 在微任务内克隆DOM定格），
    // 不 await 它，避免重位图生成阻塞“评分落库”这条关键路径；失败兜底为 null，后面再补传
    const pdfPromise = buildPdfBase64().catch(e => { console.error('生成文书PDF失败', e); return null })

    // 阶段1：评分主体（轻量JSON，不含约1MB的PDF大字段）优先落库——保证“评分一定先存得上”
    const record = {
      id: currentRecord.value ? currentRecord.value.id : null,
      patientId: patientInfo.patientId || '',
      inHospitalNo: inHospitalNo.value,
      patientName: patientInfo.name,
      departCode: departCode.value,
      scoreTime: toBackendDateTime(form.scoreTime) || new Date().toISOString().slice(0, 19).replace('T', ' '),
      // 统一落库为「手工评分」：选中自动评分记录后再保存即视为医生已确认（与 SOFA 一致）
      scoreType: 'custom',
      ageScore: scoreResult.ageScore,
      chronicScore: scoreResult.chronicScore,
      gcsScore: scoreResult.gcsScore,
      physiologyScore: scoreResult.physiologyScore,
      totalScore: scoreResult.totalScore,
      mortalityRate: scoreResult.mortalityRate,
      apsData: JSON.stringify({
        temperature: form.temperature, map: form.map, heartRate: form.heartRate,
        respiratoryRate: form.respiratoryRate, fio2: form.fio2, pao2: form.pao2,
        aado2: form.aado2, ph: form.ph, sodium: form.sodium, potassium: form.potassium,
        creatinine: form.creatinine, hct: form.hct, wbc: form.wbc,
        gcsEye: form.gcsEye, gcsVerbal: form.gcsVerbal, gcsMotor: form.gcsMotor,
        age: form.age,
        acuteRenalFailure: form.acuteRenalFailure,
        selectedNonopFactor: form.selectedNonopFactor,
        selectedOpFactor: form.selectedOpFactor,
        doctor: form.doctor
      }),
      diagnosisType: form.diagnosisType,
      chronicHealth: form.chronicHealth,
      diagnosisWeight: diagnosisWeight.value,
      emergencySurgery: form.emergencySurgery ? 1 : 0,
      dataStartTime: toBackendDateTime(fetchStartTime.value),
      dataEndTime: toBackendDateTime(fetchEndTime.value),
      gcsDetail: `E${form.gcsEye}V${form.gcsVerbal}M${form.gcsMotor}`,
      remark: form.remark,
      // createBy 不传：由后端按服务端解析出的操作人覆盖，避免前端把它改成别人
      // 阶段1不带PDF大字段，文书走阶段2单独补传
      pdfData: null,
      pdfName: null
    }
    // silentError：失败提示由下面的 catch 统一给出“保存失败：xxx”，避免弹两条
    const saved = await request.post('/apache2/save', record, { silentError: true })
    if (!saved) {
      ElMessage.error('保存失败')
      return
    }
    const savedId = saved.id
    // 评分已落库：立即刷新左侧列表、复位按钮，用户无需等待PDF位图生成与大字段上传
    ElMessage.success('评分已保存，评分文书正在后台归档…')
    currentRecord.value = null
    await loadRecords()
    // 选中最新的一条记录
    if (records.value.length > 0) {
      selectRecord(records.value[0])
    }
    saving.value = false

    // 阶段2：后台补传PDF（与主保存解耦，再慢/失败都不影响已落库的评分）
    try {
      const pdfBase64 = await pdfPromise
      if (pdfBase64 && savedId) {
        await request.post(`/apache2/record/${savedId}/pdf`,
          { pdfData: pdfBase64, pdfName: reportFileName() },
          { timeout: 120000 })
        ElMessage.success('评分文书PDF已归档')
        await loadRecords() // 刷新该条“PDF文书”标记
      } else {
        ElMessage.warning('评分已保存，但文书PDF生成失败，可重新编辑该记录后再次保存')
      }
    } catch (e) {
      console.error('文书PDF补传失败', e)
      ElMessage.warning('评分已保存，但文书PDF归档失败，可重新编辑该记录后再次保存')
    }
  } catch (e) {
    ElMessage.error('保存失败：' + (e && e.message ? e.message : e))
  } finally {
    saving.value = false
  }
}

/** 删除指定记录（左侧记录条内的「删除」，与 SOFA 一致） */
/**
 * 文书归档：
 *   待归档 → 调院方归档接口推送该条文书 → 成功后标记「已归档」；
 *   已是「已归档」时再点只撤销标记，不调用院方接口（只改本地状态）。
 */
async function toggleArchive(rec) {
  if (!rec || !rec.id) return
  // 同 SOFA：自动初评草稿业务上不属于可归档文书，后端同样会拒绝，提前给出可读提示
  if (isAutoRecord(rec)) {
    ElMessage.warning('自动初评属于内部评估草稿，打开复核并保存后才能归档')
    return
  }
  // 归档前提是已有文书 PDF，无文书时后端会拒绝，这里提前给出可读提示
  if (rec.hasPdf !== 1) {
    ElMessage.warning('该记录尚未生成评分文书，打开后保存一次即可归档')
    return
  }
  try {
    if (rec.archiveStatus === 1) {
      await request.post('/archive/unmark', null, { params: { biz: 'APACHE2', id: rec.id } })
      rec.archiveStatus = 0
      ElMessage.success('已撤销归档标记')
    } else {
      await request.post('/archive/push', null, { params: { biz: 'APACHE2', id: rec.id } })
      rec.archiveStatus = 1
      ElMessage.success('归档成功')
    }
  } catch (e) {
    console.error('归档失败', e)
    ElMessage.error(e?.response?.data?.message || e?.message || '归档失败')
  }
}

async function deleteRecord(rec) {
  if (!rec || !rec.id) return
  try {
    await ElMessageBox.confirm(`确定删除 ${formatDisplayTime(rec.scoreTime)} 的评分记录吗？`, '确认删除', { type: 'warning' })
    // 不再传 operator：操作人由服务端从登录态/外链身份解析，前端传什么都不采信
    const res = await request.delete(`/apache2/record/${rec.id}`, { silentError: true })
    if (res) {
      ElMessage.success('删除成功')
      // 删掉的正是当前打开的那条时清空选中，避免之后保存误更新到已删记录
      if (currentRecord.value && currentRecord.value.id === rec.id) {
        currentRecord.value = null
      }
      loadRecords()
    }
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败：' + (e.message || e))
  }
}

function openGcsModal() {
  showGcsModal.value = true
  // 每次打开回到“选择已有记录”页签并清空选中，再自动静默拉取
  gcsTab.value = 'sys'
  selectedSysIndex.value = -1
  loadSystemGcs(true)
}

// 拉取重症系统 Z_ICU_GCS 评估记录；silent=true 时无记录不弹提示（打开弹窗自动调用）
async function loadSystemGcs(silent) {
  if (!patientInfo.patientId) {
    if (!silent) ElMessage.warning('未获取到患者信息，无法拉取系统记录')
    return
  }
  gcsSyncLoading.value = true
  try {
    const res = await request.get(`/apache2/patient/${patientInfo.patientId}/gcs-records`)
    systemGcsList.value = Array.isArray(res) ? res : []
    gcsSyncLoaded.value = true
    // 默认选中最新一条（列表已按评估时间倒序）
    selectedSysIndex.value = systemGcsList.value.length ? 0 : -1
    if (!silent && !systemGcsList.value.length) {
      ElMessage.info('重症系统暂无该患者的 GCS 评估记录')
    }
  } catch (e) {
    if (!silent) console.warn('拉取重症系统GCS记录失败：', e.message || '')
  } finally {
    gcsSyncLoading.value = false
  }
}

// 表格 GCS 列：非插管三项齐全显示合计，插管显示原始 totalText（如 2+ET+2）
function gcsRowTotal(rec) {
  if (rec.intubated) return rec.totalText || 'ET'
  if (rec.eye != null && rec.verbal != null && rec.motor != null) return rec.eye + rec.verbal + rec.motor
  return '—'
}

// 仅回填 E/V/M 并重算，不弹提示（提示由“确定选择/自动同步”统一给出）
function applySystemGcs(rec) {
  if (!rec) return
  form.gcsEye = rec.eye != null ? Number(rec.eye) : null
  form.gcsMotor = rec.motor != null ? Number(rec.motor) : null
  form.gcsVerbal = (!rec.intubated && rec.verbal != null) ? Number(rec.verbal) : null
  calculateScore()
}

// “确定选择”：回填选中行；三项完整直接带入并关闭，插管/言语缺失则跳到手工页补评
function confirmPickSystemGcs() {
  const rec = systemGcsList.value[selectedSysIndex.value]
  if (!rec) {
    ElMessage.warning('请先在列表中选择一条 GCS 记录')
    return
  }
  applySystemGcs(rec)
  if (rec.intubated) {
    ElMessage.warning('该患者气管插管/气切(ET)，已同步睁眼(E)、运动(M)，言语(V)请在「新建 GCS 评估」中人工评定')
    gcsTab.value = 'manual'
  } else if (rec.verbal == null) {
    ElMessage.warning('该记录言语项缺失，已同步 E/M，请在「新建 GCS 评估」补选言语(V)')
    gcsTab.value = 'manual'
  } else {
    ElMessage.success('已从重症系统同步 E / V / M')
    showGcsModal.value = false
  }
}

// “自动同步最新记录”：拉取后直接带入最新一条（插管/缺V时跳手工页补评）
async function syncLatestGcs() {
  await loadSystemGcs(false)
  if (systemGcsList.value.length) {
    selectedSysIndex.value = 0
    confirmPickSystemGcs()
  } else {
    ElMessage.info('重症系统暂无 GCS 记录，请切换到「新建 GCS 评估」手工录入')
  }
}

function confirmGcs() {
  showGcsModal.value = false
  calculateScore()
}

function openSourceModal(metric) {
  sourceMetric.value = metric
  showSourceModal.value = true
  // 延迟渲染趋势图，确保DOM准备好（最多重试3次）
  let retryCount = 0
  function tryRender() {
    if (sourceTrendChartRef.value) {
      renderSourceTrendChart(metric)
    } else if (retryCount < 3) {
      retryCount++
      setTimeout(tryRender, 100)
    }
  }
  setTimeout(tryRender, 100)
}

async function renderSourceTrendChart(metric) {
  if (!sourceTrendChartRef.value) return
  // 每次都重新初始化，避免弹窗关闭后DOM销毁导致旧实例失效
  if (sourceTrendChart) {
    sourceTrendChart.dispose()
    sourceTrendChart = null
  }
  sourceTrendChart = echarts.init(sourceTrendChartRef.value)
  const item = apsItems.find(i => i.label === metric)
  const unit = item ? item.unit : ''
  const metricKey = item ? item.key : ''

  // 氧合是派生项（FiO2 决定分支 + PaO2/A-aDO2 出分），单独走双 Y 轴多序列渲染
  if (metricKey === 'oxygen') {
    await renderOxygenTrendChart(metric)
    return
  }

  // 显示加载状态
  sourceTrendChart.showLoading({ text: '加载中...', color: '#409eff', textColor: '#999', maskColor: 'rgba(255,255,255,0.8)' })

  let times = []
  let values = []

  try {
    // 从后端接口获取真实趋势数据
    if (patientInfo.patientId && metricKey) {
      const res = await request.get(`/apache2/metric-trend/${patientInfo.patientId}`, {
        params: {
          metricKey: metricKey,
          startTime: toBackendDateTime(fetchStartTime.value),
          endTime: toBackendDateTime(fetchEndTime.value)
        }
      })
      if (res && Array.isArray(res) && res.length > 0) {
        for (const point of res) {
          // 格式化时间：去掉T和秒
          let timeStr = point.time || ''
          if (timeStr.includes('T')) {
            timeStr = timeStr.replace('T', ' ').slice(0, 16)
          } else if (timeStr.length > 16) {
            timeStr = timeStr.slice(0, 16)
          }
          times.push(timeStr)
          values.push(Number(point.value))
        }
      }
    }
  } catch (e) {
    console.warn('获取趋势数据失败，使用空数据:', e)
  }

  sourceTrendChart.hideLoading()

  // 如果没有数据，显示空状态
  if (times.length === 0) {
    sourceTrendChart.setOption({
      title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: '#999', fontSize: 14, fontWeight: 'normal' } },
      xAxis: { show: false },
      yAxis: { show: false },
      series: []
    }, true)
    return
  }

  sourceTrendChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: function(params) {
        return params[0].name + '<br/>' + metric + ': ' + params[0].value + ' ' + unit
      }
    },
    grid: { left: 50, right: 20, top: 20, bottom: 30 },
    xAxis: {
      type: 'category',
      data: times,
      axisLabel: { fontSize: 10, color: '#999', interval: Math.floor(times.length / 6) || 0, rotate: times.length > 10 ? 30 : 0 }
    },
    yAxis: {
      type: 'value',
      name: unit,
      nameTextStyle: { fontSize: 10, color: '#999' },
      axisLabel: { fontSize: 10, color: '#999' }
    },
    dataZoom: times.length > 20 ? [{ type: 'inside', start: 0, end: 100 }] : [],
    series: [{
      name: metric,
      type: 'line',
      data: values,
      smooth: true,
      symbol: 'circle',
      symbolSize: 4,
      lineStyle: { color: '#409eff', width: 2 },
      itemStyle: { color: '#409eff' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(64,158,255,0.3)' },
          { offset: 1, color: 'rgba(64,158,255,0.05)' }
        ])
      },
      markPoint: {
        data: [
          { type: 'max', name: '最大值' },
          { type: 'min', name: '最小值' }
        ],
        symbolSize: 40,
        label: { fontSize: 10 }
      }
    }]
  }, true)
  sourceTrendChart.resize()
}

// 趋势时间文本：去掉 T、截到分钟（与单指标趋势图保持一致）
function fmtTrendTime(t) {
  let s = t || ''
  if (s.includes('T')) s = s.replace('T', ' ').slice(0, 16)
  else if (s.length > 16) s = s.slice(0, 16)
  return s
}

/**
 * 氧合趋势图：FiO2（右轴 %）+ PaO2 / A-aDO2（左轴 mmHg）三条序列。
 * 三类数据采样不同频（FiO2 监护、PaO2/A-aDO2 来自血气），故用 time 轴各自按真实时间点绘制；
 * FiO2 为呼吸机“设置值”，用阶梯线；并高亮评分实际选中的那一管血气（worstTime）。
 */
async function renderOxygenTrendChart(metric) {
  if (!sourceTrendChart) return
  sourceTrendChart.showLoading({ text: '加载中...', color: '#409eff', textColor: '#999', maskColor: 'rgba(255,255,255,0.8)' })

  let data = null
  try {
    if (patientInfo.patientId) {
      data = await request.get(`/apache2/oxygen-trend/${patientInfo.patientId}`, {
        params: {
          startTime: toBackendDateTime(fetchStartTime.value),
          endTime: toBackendDateTime(fetchEndTime.value)
        }
      })
    }
  } catch (e) {
    console.warn('获取氧合趋势失败:', e)
  }

  sourceTrendChart.hideLoading()

  const toSeries = (arr) => (Array.isArray(arr) ? arr : [])
    .filter(p => p && p.time && p.value !== null && p.value !== undefined)
    .map(p => ({ name: fmtTrendTime(p.time), value: Number(p.value) }))
  const fio2Series = toSeries(data && data.fio2)
  const pao2Series = toSeries(data && data.pao2)
  const aado2Series = toSeries(data && data.aado2)

  if (fio2Series.length + pao2Series.length + aado2Series.length === 0) {
    sourceTrendChart.setOption({
      title: { text: '暂无数据', left: 'center', top: 'center', textStyle: { color: '#999', fontSize: 14, fontWeight: 'normal' } },
      xAxis: { show: false },
      yAxis: { show: false },
      series: []
    }, true)
    return
  }

  const worstTime = (data && data.worstTime) ? fmtTrendTime(data.worstTime) : ''
  // 标记“评分选中的那一管”：在该时间点对应的曲线上打点
  const markWorst = (series) => {
    if (!worstTime) return undefined
    const hit = series.find(p => p.name === worstTime)
    if (!hit) return undefined
    return {
      symbol: 'pin',
      symbolSize: 44,
      itemStyle: { color: '#f56c6c' },
      label: { fontSize: 10, color: '#fff', formatter: '评分取值' },
      data: [{ name: '评分取值', coord: [hit.name, hit.value] }]
    }
  }

  sourceTrendChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: function (params) {
        if (!params || !params.length) return ''
        let s = params[0].axisValue
        for (const p of params) {
          const v = Array.isArray(p.value) ? p.value[1] : p.value
          if (v === undefined || v === null) continue
          s += '<br/>' + p.marker + p.seriesName + ': ' + v + (p.seriesName === 'FiO2' ? ' %' : ' mmHg')
        }
        return s
      }
    },
    legend: { data: ['PaO2', 'A-aDO2', 'FiO2'], top: 0, textStyle: { fontSize: 11 } },
    grid: { left: 52, right: 56, top: 34, bottom: 30 },
    // 三种来源采样时间不同频，用 time 轴各自按真实时间点绘制（不做类目对齐）
    xAxis: { type: 'time', axisLabel: { fontSize: 10, color: '#999' } },
    yAxis: [
      { type: 'value', name: 'mmHg', nameTextStyle: { fontSize: 10, color: '#999' }, axisLabel: { fontSize: 10, color: '#999' } },
      { type: 'value', name: 'FiO2 %', nameTextStyle: { fontSize: 10, color: '#999' }, axisLabel: { fontSize: 10, color: '#999' }, min: 21, max: 100 }
    ],
    series: [
      { name: 'PaO2', type: 'line', yAxisIndex: 0,
        data: pao2Series.map(p => [p.name, p.value]),
        symbol: 'circle', symbolSize: 5, lineStyle: { color: '#409eff', width: 2 }, itemStyle: { color: '#409eff' },
        markPoint: markWorst(pao2Series) },
      { name: 'A-aDO2', type: 'line', yAxisIndex: 0,
        data: aado2Series.map(p => [p.name, p.value]),
        symbol: 'circle', symbolSize: 5, lineStyle: { color: '#e6a23c', width: 2 }, itemStyle: { color: '#e6a23c' },
        markPoint: markWorst(aado2Series) },
      // FiO2 为设置值，阶梯变化更真实；单位 % 走右轴
      { name: 'FiO2', type: 'line', yAxisIndex: 1, step: 'end',
        data: fio2Series.map(p => [p.name, p.value]),
        symbol: 'rect', symbolSize: 5, lineStyle: { color: '#67c23a', width: 2, type: 'dashed' }, itemStyle: { color: '#67c23a' } }
    ]
  }, true)
  sourceTrendChart.resize()
}

function getSourceValue() {
  const item = apsItems.find(i => i.label === sourceMetric.value)
  if (!item) return '—'
  // 氧合是派生项，form 里没有 oxygen 字段，拼出三要素作为“当前值”
  if (item.key === 'oxygen') {
    const o = srcOxygen.value
    const parts = []
    if (o.fio2 !== null) parts.push('FiO2 ' + o.fio2 + '%')
    if (o.pao2 !== null) parts.push('PaO2 ' + o.pao2)
    if (o.aado2 !== null) parts.push('A-aDO2 ' + (Math.round(o.aado2 * 10) / 10))
    return parts.length ? parts.join(' / ') : '—'
  }
  return form[item.key]
}
function getSourceRange() {
  const item = apsItems.find(i => i.label === sourceMetric.value)
  if (!item) return '—'
  // 氧合按 FiO2 分支给出实际参与评分的那个区间（与后端 calcOxygenScore 一致）
  if (item.key === 'oxygen') return getOxygenRange()
  return getHitRange(item.key, form[item.key])
}
function getSourceScore() {
  const item = apsItems.find(i => i.label === sourceMetric.value)
  return item && scoreResult.apsScores ? (scoreResult.apsScores[item.key] || 0) : 0
}

function openWeightTable() { showWeightTable.value = true }

function getChronicText() {
  const map = { none: '无上述情况', nonoperative: '非手术/急诊手术后', elective: '择期手术后' }
  return map[form.chronicHealth] || '—'
}

function getScoreClass(score) {
  if (score >= 20) return 'score-high'
  if (score >= 10) return 'score-mid'
  return 'score-low'
}

function getBadgeClass(score) {
  if (score >= 3) return 'score-3'
  if (score >= 1) return 'score-1'
  return 'score-0'
}

function getHitRange(key, value) {
  if (value === null || value === undefined || value === '') return '—'
  const ranges = {
    temperature: v => v >= 41 ? '≥41' : v >= 39 ? '39-40.9' : v >= 38.5 ? '38.5-38.9' : v >= 36 ? '36-38.4' : v >= 34 ? '34-35.9' : v >= 32 ? '32-33.9' : '<32',
    map: v => v >= 160 ? '≥160' : v >= 130 ? '130-159' : v >= 110 ? '110-129' : v >= 70 ? '70-109' : v >= 50 ? '50-69' : '<50',
    heartRate: v => v >= 180 ? '≥180' : v >= 140 ? '140-179' : v >= 110 ? '110-139' : v >= 70 ? '70-109' : v >= 55 ? '55-69' : v >= 40 ? '40-54' : '<40',
    respiratoryRate: v => v >= 50 ? '≥50' : v >= 35 ? '35-49' : v >= 25 ? '25-34' : v >= 12 ? '12-24' : v >= 10 ? '10-11' : v >= 6 ? '6-9' : '<6',
    // 氧合的区间展示由 getOxygenRange() 按 FiO2 分支负责，此处不再保留已失效的 oxygen / pao2 条目
    ph: v => v >= 7.7 ? '≥7.7' : v >= 7.6 ? '7.6-7.69' : v >= 7.5 ? '7.5-7.59' : v >= 7.33 ? '7.33-7.49' : v >= 7.25 ? '7.25-7.32' : v >= 7.15 ? '7.15-7.24' : '<7.15',
    sodium: v => v >= 180 ? '≥180' : v >= 160 ? '160-179' : v >= 155 ? '155-159' : v >= 150 ? '150-154' : v >= 130 ? '130-149' : v >= 120 ? '120-129' : v >= 111 ? '111-119' : '<111',
    potassium: v => v >= 7 ? '≥7' : v >= 6 ? '6-6.9' : v >= 5.5 ? '5.5-5.9' : v >= 3.5 ? '3.5-5.4' : v >= 3 ? '3-3.4' : v >= 2.5 ? '2.5-2.9' : '<2.5',
    creatinine: v => v >= 309 ? '≥3.5mg/dL' : v >= 177 ? '2-3.4mg/dL' : v >= 133 ? '1.5-1.9mg/dL' : v >= 53 ? '0.6-1.4mg/dL' : '<0.6mg/dL',
    hct: v => v >= 60 ? '≥60' : v >= 50 ? '50-59.9' : v >= 46 ? '46-49.9' : v >= 30 ? '30-45.9' : v >= 20 ? '20-29.9' : '<20',
    wbc: v => v >= 40 ? '≥40' : v >= 20 ? '20-39.9' : v >= 15 ? '15-19.9' : v >= 3 ? '3-14.9' : v >= 1 ? '1-2.9' : '<1'
  }
  return ranges[key] ? ranges[key](value) : '—'
}

// 氧合命中区间：FiO2≥50%用A-aDO2，否则用PaO2
function getOxygenRange() {
  const fio2 = form.fio2
  if (fio2 === null || fio2 === undefined || fio2 === '') return '—'
  if (fio2 >= 50) {
    const v = form.aado2
    if (v === null || v === undefined || v === '') return '—'
    return v >= 500 ? '≥500' : v >= 350 ? '350-499' : v >= 200 ? '200-349' : '<200'
  } else {
    const v = form.pao2
    if (v === null || v === undefined || v === '') return '—'
    // 与后端 calcOxygenScore 的 PaO2 阈值一致：<55 / 55-60 / 61-70 / >70
    return v < 55 ? '<55' : v <= 60 ? '55-60' : v <= 70 ? '61-70' : '>70'
  }
}

// ============ 文书预览 / 打印 / PDF ============
function reportFileName() {
  const t = (form.scoreTime || '').replace(/[:\s]/g, '').slice(0, 12)
  return `APACHE2评分_${patientInfo.name || '患者'}_${t || Date.now()}.pdf`
}

async function openReport() {
  reportGenerating.value = true
  try {
    await calculateScore() // 打开前保证分数为最新
    showReportModal.value = true
    await nextTick()
    // 离屏文书为唯一渲染源，快照到弹窗显示区。
    // 必须克隆 outerHTML：.report-page 外壳带固定 794px(A4) 宽度/内边距/宋体样式，
    // 若只克隆 innerHTML，表格会落进 flex 的 .report-view-host 被压成竖排、左侧大片空白。
    if (reportRef.value && reportViewRef.value) {
      reportViewRef.value.innerHTML = reportRef.value.outerHTML
    }
  } finally {
    reportGenerating.value = false
  }
}

// 将文书DOM渲染为A4多页 jsPDF
async function buildPdf() {
  await nextTick()
  const el = reportRef.value
  if (!el) throw new Error('文书未渲染')
  // scale 1.5（原2）：A4宽约1191px≈144dpi，屏幕/打印清晰，像素量比2倍降约44%，明显降低医生工作站CPU占用与出图耗时
  const canvas = await html2canvas(el, { scale: 1.5, useCORS: true, backgroundColor: '#ffffff', logging: false })
  const pdf = new jsPDF('p', 'mm', 'a4')
  const pageW = 210
  const pageH = 297
  const margin = 4
  const maxW = pageW - margin * 2
  const maxH = pageH - margin * 2
  // 整张评分表等比缩放到“一页”A4内：先按可打印宽度铺满，若超高则改为以页高为准等比缩，
  // 水平居中、留页边距。这样完整一张表只出一页、绝不把D表拦腰切到第二页（与“预览文书”连续整表一致）
  let imgW = maxW
  let imgH = canvas.height * imgW / canvas.width
  if (imgH > maxH) {
    imgH = maxH
    imgW = canvas.width * imgH / canvas.height
  }
  const imgX = (pageW - imgW) / 2
  const imgY = margin
  // 质量0.85（原0.92）：JPEG体积近半，肉眼几乎无差，减小上传大字段与达梦写入压力
  const imgData = canvas.toDataURL('image/jpeg', 0.85)
  pdf.addImage(imgData, 'JPEG', imgX, imgY, imgW, imgH)
  return pdf
}

async function downloadReportPdf() {
  try {
    const pdf = await buildPdf()
    pdf.save(reportFileName())
  } catch (e) {
    console.error(e)
    console.warn('导出PDF失败: ', e.message)
  }
}

// 浏览器打印（新窗口写入文书HTML，矢量清晰）
function printReport() {
  const el = reportRef.value
  if (!el) return
  const w = window.open('', '_blank')
  if (!w) { ElMessage.warning('浏览器拦截了打印窗口，请允许弹窗'); return }
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

// 生成PDF Base64（不含 data:application/pdf;base64, 前缀），供保存入库
// 加 15s 超时兜底：html2canvas 克隆文档/字体加载在极端情况下可能不返回，不能让它无限挂起拖死保存
async function buildPdfBase64() {
  const pdf = await Promise.race([
    buildPdf(),
    new Promise((_, reject) => setTimeout(() => reject(new Error('文书渲染超时(15s)')), 15000))
  ])
  const uri = pdf.output('datauristring')
  return uri.includes(',') ? uri.split(',')[1] : uri
}

// 在线查看已保存记录的PDF（用 axios 携带外链鉴权头拉取 blob，再新窗打开）
async function viewSavedPdf(rec) {
  if (!rec || !rec.id) return
  try {
    const resp = await axios.get(`/api/apache2/record/${rec.id}/pdf`, {
      params: { disposition: 'inline', t: Date.now() },
      headers: { ...getExternalHeaders(), ...getAuthHeaders() },
      responseType: 'blob',
      timeout: 60000
    })
    const blob = new Blob([resp.data], { type: 'application/pdf' })
    const url = URL.createObjectURL(blob)
    window.open(url, '_blank')
    setTimeout(() => URL.revokeObjectURL(url), 60000)
  } catch (e) {
    ElMessage.error('打开评分文书PDF失败')
  }
}
</script>

<style scoped>
* { margin: 0; padding: 0; box-sizing: border-box; }
.page { display: flex; min-height: 100vh; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif; background: #f0f2f5; color: #303133; font-size: 14px; }

/* 左侧边栏 */
.side { width: 280px; min-width: 280px; display: flex; flex-direction: column; background: #fff; border-right: 1px solid #e4e7ed; position: sticky; top: 0; height: 100vh; }
.side-head { height: 52px; padding: 0 16px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #ebeef5; font-weight: 600; font-size: 15px; }
.side-head .count { background: #ecf5ff; color: #409eff; padding: 2px 8px; border-radius: 10px; font-size: 12px; font-weight: 600; }
.record-list { flex: 1; overflow-y: auto; padding: 8px; }
.record-item { padding: 12px; margin-bottom: 8px; border: 1px solid #ebeef5; border-radius: 6px; cursor: pointer; transition: all 0.2s; background: #fff; }
.record-item:hover { border-color: #c6e2ff; background: #f5f9ff; }
.record-item.active { border-color: #409eff; background: #ecf5ff; }
.record-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.record-time { font-size: 13px; color: #606266; font-weight: 500; }
.record-score { font-size: 20px; font-weight: 700; padding: 2px 10px; border-radius: 4px; }
.score-low { background: #f0f9eb; color: #67c23a; }
.score-mid { background: #fdf6ec; color: #e6a23c; }
.score-high { background: #fef0f0; color: #f56c6c; }
.record-meta { font-size: 12px; color: #909399; }
.record-meta span { margin-right: 8px; }
.record-tag { display: inline-block; padding: 1px 6px; border-radius: 3px; font-size: 11px; background: #f4f4f5; color: #909399; }
.record-tag.auto { background: #ecf5ff; color: #409eff; }
.record-tag.manual { background: #fdf6ec; color: #e6a23c; }
.record-empty { text-align: center; color: #c0c4cc; font-size: 13px; padding: 40px 0; }
.add-record-btn { width: 100%; height: 36px; background: linear-gradient(135deg, #409eff, #66b1ff); color: #fff; border: none; border-radius: 6px; font-size: 14px; font-weight: 600; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 6px; box-shadow: 0 2px 6px rgba(64,158,255,0.3); }
.add-record-btn:hover { background: linear-gradient(135deg, #66b1ff, #409eff); }

/* 主区域 */
.main { flex: 1; min-width: 0; display: flex; flex-direction: column; }

/* 患者信息栏 */
.patient-bar { background: #fff; padding: 12px 20px; border-bottom: 1px solid #ebeef5; display: flex; align-items: center; gap: 24px; flex-wrap: wrap; }
.patient-name { font-size: 18px; font-weight: 700; color: #303133; display: flex; align-items: center; gap: 10px; }
.patient-name .bed-tag { background: #409eff; color: #fff; padding: 2px 10px; border-radius: 4px; font-size: 13px; font-weight: 500; }
.patient-info { display: flex; gap: 20px; flex-wrap: wrap; }
.patient-info-item { display: flex; align-items: center; gap: 4px; font-size: 13px; }
.patient-info-item .label { color: #909399; }
.patient-info-item .value { color: #303133; font-weight: 500; }
.patient-bar-right { margin-left: auto; display: flex; gap: 8px; }

/* 评分汇总卡 */
.summary-bar { background: #fff; padding: 14px 20px; border-bottom: 1px solid #ebeef5; display: flex; align-items: center; gap: 16px; }
.total-score-card { min-width: 140px; padding: 12px 20px; background: linear-gradient(135deg, #409eff, #66b1ff); border-radius: 8px; color: #fff; text-align: center; }
.total-score-card .label { font-size: 12px; opacity: 0.9; }
.total-score-card .value { font-size: 36px; font-weight: 700; line-height: 1.2; }
.total-score-card .mortality { font-size: 12px; opacity: 0.9; margin-top: 2px; background: rgba(255,255,255,0.2); border-radius: 10px; padding: 2px 8px; display: inline-block; }
.score-cards { display: flex; gap: 10px; flex: 1; }
.score-card { flex: 1; min-width: 0; padding: 10px 14px; border: 1px solid #ebeef5; border-radius: 6px; background: #fafafa; display: flex; align-items: center; gap: 10px; }
.score-card .code { width: 32px; height: 32px; border-radius: 7px; background: #ecf5ff; color: #409eff; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 16px; flex-shrink: 0; }
.score-card .info { min-width: 0; flex: 1; }
.score-card .name { font-size: 12px; color: #909399; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.score-card .val { font-size: 22px; font-weight: 700; color: #303133; line-height: 1.2; }

/* 工具栏 */
.toolbar { background: #fff; padding: 10px 20px; border-bottom: 1px solid #ebeef5; display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.toolbar-label { color: #606266; font-size: 13px; white-space: nowrap; }
.toolbar input { height: 32px; border: 1px solid #dcdfe6; border-radius: 4px; padding: 0 10px; font-size: 13px; outline: none; background: #fff; }
.toolbar input:focus { border-color: #409eff; }
.btn { height: 32px; padding: 0 14px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; color: #606266; font-size: 13px; cursor: pointer; display: inline-flex; align-items: center; gap: 5px; transition: all 0.2s; }
.btn:hover { color: #409eff; border-color: #c6e2ff; background: #ecf5ff; }
.btn-primary { background: #409eff; color: #fff; border-color: #409eff; }
.btn-primary:hover { background: #66b1ff; color: #fff; border-color: #66b1ff; }
.btn-success { background: #67c23a; color: #fff; border-color: #67c23a; }
.btn-success:hover { background: #85ce61; color: #fff; border-color: #85ce61; }
.btn-danger { background: #f56c6c; color: #fff; border-color: #f56c6c; }
.btn-danger:hover { background: #f78989; color: #fff; border-color: #f78989; }
.btn-text { background: none; border: none; color: #409eff; padding: 0 4px; }
.btn-text:hover { color: #66b1ff; background: none; }

/* 内容区 */
.content { padding: 10px 16px; display: flex; flex-direction: column; gap: 10px; }
.content-row { display: grid; grid-template-columns: minmax(320px, 0.7fr) minmax(500px, 1.3fr); gap: 12px; align-items: start; }
.panel { background: #fff; border: 1px solid #ebeef5; border-radius: 6px; overflow: hidden; }
.panel-title { padding: 10px 16px; border-bottom: 1px solid #ebeef5; font-weight: 600; font-size: 14px; display: flex; align-items: center; gap: 8px; }
.panel-title::before { content: ''; width: 3px; height: 14px; background: #409eff; border-radius: 2px; }
.panel-body { padding: 10px 14px; }

/* ABC评分表 */
.abc-row { display: flex; align-items: center; gap: 12px; padding: 12px; margin-bottom: 10px; border: 1px solid #ebeef5; border-radius: 6px; background: #fafafa; }
.abc-row:last-child { margin-bottom: 0; }
.abc-row.gcs { background: #faf8ff; border-color: #e4d9f7; }
.abc-code { width: 30px; height: 30px; border-radius: 6px; background: #ecf5ff; color: #409eff; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 15px; flex-shrink: 0; }
.abc-row.gcs .abc-code { background: #f3efff; color: #7c3aed; }
.abc-main { flex: 1; min-width: 0; }
.abc-head { display: flex; justify-content: space-between; align-items: baseline; gap: 8px; margin-bottom: 6px; }
.abc-head strong { font-size: 14px; color: #303133; }
.abc-head em { font-size: 12px; color: #909399; font-style: normal; }
.abc-main input, .abc-main select { width: 100%; height: 32px; border: 1px solid #dcdfe6; border-radius: 4px; padding: 0 10px; font-size: 13px; outline: none; }
.abc-main input:focus, .abc-main select:focus { border-color: #409eff; }
.gcs-trigger { width: 100%; min-height: 36px; border: 1px solid #d9c8ff; border-radius: 6px; background: #faf8ff; color: #59349d; padding: 6px 12px; cursor: pointer; display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.gcs-trigger:hover { border-color: #9c72e8; background: #f5f0ff; }
.gcs-trigger .gcs-info { text-align: left; }
.gcs-trigger .gcs-info span { font-size: 12px; font-weight: 600; display: block; }
.gcs-trigger .gcs-info em { font-size: 11px; color: #7c5aba; font-style: normal; }
.gcs-trigger .gcs-val { font-size: 24px; font-weight: 700; padding-left: 10px; border-left: 1px solid #dfd0fc; }
.abc-result { width: 56px; flex-shrink: 0; text-align: center; padding: 8px 0; background: #f0f6ff; border-radius: 6px; }
.abc-result span { font-size: 11px; color: #909399; display: block; }
.abc-result b { font-size: 20px; color: #409eff; font-weight: 700; }
.meta-row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 12px; padding-top: 12px; border-top: 1px dashed #ebeef5; }
.meta-item label { display: block; font-size: 12px; color: #909399; margin-bottom: 4px; }
.meta-item input, .meta-item select { width: 100%; height: 32px; border: 1px solid #dcdfe6; border-radius: 4px; padding: 0 10px; font-size: 13px; outline: none; }

/* D 急性生理评分表 */
.d-table { width: 100%; border-collapse: collapse; }
.d-table th { background: #f5f7fa; color: #606266; font-weight: 500; font-size: 12px; padding: 8px 10px; text-align: left; border-bottom: 1px solid #ebeef5; white-space: nowrap; }
.d-table td { padding: 8px 10px; border-bottom: 1px solid #f0f0f0; font-size: 13px; }
.d-table tr:last-child td { border-bottom: none; }
.d-table tr:hover { background: #fafafa; }
.metric-name { font-weight: 600; color: #303133; }
.metric-unit { font-size: 11px; color: #909399; margin-top: 2px; }
.d-table input[type="number"] { width: 90px; height: 30px; border: 1px solid #dcdfe6; border-radius: 4px; padding: 0 8px; font-size: 13px; outline: none; }
.d-table input[type="number"]:focus { border-color: #409eff; }
.oxygen-fields { display: flex; gap: 6px; flex-wrap: wrap; }
.oxygen-field { display: flex; flex-direction: column; gap: 2px; }
.oxygen-field span { font-size: 11px; color: #909399; }
.oxygen-field input { width: 70px; height: 28px; border: 1px solid #dcdfe6; border-radius: 4px; padding: 0 6px; font-size: 12px; }
.range-hit { display: inline-block; padding: 2px 8px; background: #f5f7fa; color: #606266; border-radius: 3px; font-size: 12px; }
.score-badge { display: inline-flex; align-items: center; justify-content: center; min-width: 28px; height: 26px; padding: 0 6px; border-radius: 4px; font-weight: 700; font-size: 15px; }
.score-0 { background: #f0f9eb; color: #67c23a; }
.score-1, .score-2 { background: #fdf6ec; color: #e6a23c; }
.score-3, .score-4 { background: #fef0f0; color: #f56c6c; }
.metric-actions { display: flex; gap: 4px; }
.metric-actions .btn { height: 26px; padding: 0 8px; font-size: 12px; min-width: 40px; }
.switch { width: 38px; height: 20px; border-radius: 10px; background: #c0c4cc; cursor: pointer; position: relative; transition: background 0.2s; }
.switch::after { content: ''; position: absolute; width: 16px; height: 16px; border-radius: 50%; background: #fff; top: 2px; left: 2px; transition: left 0.2s; }
.switch.on { background: #409eff; }
.switch.on::after { left: 20px; }

/* 死亡率预测区 */
.mortality-panel { background: #fff; border: 1px solid #ebeef5; border-radius: 6px; overflow: hidden; }
.mortality-head { padding: 14px 20px; display: flex; align-items: center; gap: 20px; background: linear-gradient(135deg, #f7fbff, #fff); border-bottom: 1px solid #ebeef5; }
.mortality-result { min-width: 180px; }
.mortality-result .label { font-size: 12px; color: #909399; }
.mortality-result .value { font-size: 32px; font-weight: 700; color: #f56c6c; line-height: 1.2; }
.mortality-result .note { font-size: 12px; color: #c0c4cc; margin-top: 2px; }
.equation-box { flex: 1; padding: 10px 16px; border: 1px solid #e0e9f6; border-radius: 6px; background: #fff; display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.equation-box .eq-label { font-size: 12px; color: #909399; white-space: nowrap; }
.equation-box .eq-total { font-size: 28px; font-weight: 700; color: #409eff; }
.equation-chips { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.eq-chip { display: inline-flex; align-items: center; gap: 5px; padding: 3px 10px; border: 1px solid #e1e9f5; border-radius: 12px; background: #f8fbff; font-size: 12px; color: #40546c; }
.eq-chip mark { width: 18px; height: 18px; border-radius: 50%; background: #409eff; color: #fff; display: inline-flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700; }
.eq-chip strong { color: #409eff; font-size: 14px; }
.eq-plus { color: #c0c4cc; font-weight: 700; }
.mortality-controls { padding: 12px 20px; display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; border-bottom: 1px solid #f0f0f0; }
.control-group { display: flex; align-items: center; gap: 8px; }
.control-group .ctl-label { font-size: 13px; color: #606266; white-space: nowrap; }
.seg-btn { height: 30px; padding: 0 12px; border: 1px solid #dcdfe6; border-radius: 4px; background: #fff; color: #606266; font-size: 13px; cursor: pointer; }
.seg-btn.active { background: #409eff; color: #fff; border-color: #409eff; }
.factor-grid { padding: 12px 20px; display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.factor-card { border: 1px solid #ebeef5; border-radius: 6px; overflow: hidden; }
.factor-card-title { padding: 8px 12px; background: #f7fbff; border-bottom: 1px solid #edf2f8; font-size: 13px; font-weight: 600; color: #303133; display: flex; align-items: center; justify-content: space-between; }
.factor-card-title::before { content: ''; width: 3px; height: 12px; background: #409eff; border-radius: 2px; margin-right: 8px; }
.factor-card-title b { background: #eef6ff; color: #409eff; padding: 1px 8px; border-radius: 10px; font-size: 11px; }
.factor-options { padding: 10px; display: grid; grid-template-columns: repeat(auto-fit, minmax(140px, 1fr)); gap: 6px; }
.factor-options label { display: flex; align-items: center; gap: 6px; padding: 6px 8px; border: 1px solid #e6edf6; border-radius: 5px; background: #fbfdff; font-size: 12px; color: #26364d; cursor: pointer; transition: all 0.15s; }
.factor-options label:hover { border-color: #91caff; background: #f0f7ff; }
.factor-options label.checked { border-color: #8cc8ff; background: #eef7ff; color: #0b5cad; font-weight: 600; }
.factor-options input { width: 14px; height: 14px; accent-color: #409eff; }
.bottom-row { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; padding: 0 20px 12px; }
.note-card { border: 1px solid #ebeef5; border-radius: 6px; overflow: hidden; }
.note-card .note-title { padding: 8px 12px; background: #f7fbff; border-bottom: 1px solid #edf2f8; font-size: 13px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.note-card .note-title::before { content: ''; width: 3px; height: 12px; background: #409eff; border-radius: 2px; }
.note-card .note-body { padding: 10px 12px; font-size: 13px; color: #40546c; line-height: 1.6; }
.note-card ul { margin: 0; padding-left: 20px; }
.note-card li { margin-bottom: 4px; font-size: 12px; color: #5d6f86; }

/* 底部操作栏 */
.footer-bar { background: #fff; padding: 10px 20px; border-top: 1px solid #ebeef5; display: flex; align-items: center; gap: 10px; }
.footer-bar textarea { flex: 1; height: 36px; min-height: 36px; border: 1px solid #dcdfe6; border-radius: 4px; padding: 8px 10px; font-size: 13px; resize: none; outline: none; }
.footer-bar textarea:focus { border-color: #409eff; }
.footer-total { padding: 6px 14px; background: #ecf5ff; border-radius: 4px; color: #409eff; font-weight: 700; font-size: 15px; }

/* 弹窗 */
.modal-mask { position: fixed; inset: 0; z-index: 100; display: flex; align-items: center; justify-content: center; background: rgba(0,0,0,0.4); }
.modal { width: min(720px, calc(100vw - 40px)); max-height: calc(100vh - 60px); display: flex; flex-direction: column; background: #fff; border-radius: 8px; overflow: hidden; }
.modal-head { padding: 14px 20px; border-bottom: 1px solid #ebeef5; display: flex; align-items: center; justify-content: space-between; }
.modal-head h3 { font-size: 16px; font-weight: 600; }
.modal-close { width: 28px; height: 28px; border: none; background: none; font-size: 20px; color: #909399; cursor: pointer; }
.modal-body { flex: 1; overflow-y: auto; padding: 16px 20px; }
.modal-foot { padding: 12px 20px; border-top: 1px solid #ebeef5; display: flex; justify-content: flex-end; gap: 10px; }
.gcs-modal { width: min(800px, calc(100vw - 40px)); }
.gcs-tabs { flex: 1; overflow-y: auto; padding: 0 20px; }
.gcs-tabs :deep(.el-tabs__header) { margin: 0 -20px 14px; padding: 0 20px; }
.gcs-tabs :deep(.el-tabs__item) { font-size: 14px; font-weight: 600; color: #728096; height: 44px; line-height: 44px; }
.gcs-tabs :deep(.el-tabs__item.is-active) { color: #59349d; }
.gcs-tabs :deep(.el-tabs__active-bar) { background-color: #7c3aed; }
.gcs-sys-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 10px; }
.gcs-sys-tip { font-size: 12px; color: #728096; line-height: 1.5; }
.gcs-mini-primary { flex: 0 0 auto; height: 30px; padding: 0 14px; font-size: 12px; border: none; border-radius: 6px; background: #3b82f6; color: #fff; cursor: pointer; white-space: nowrap; }
.gcs-mini-primary:disabled { opacity: .6; cursor: not-allowed; }
.gcs-sys-table { max-height: 360px; overflow-y: auto; border: 1px solid #e8edf4; border-radius: 8px; }
.gcs-sys-table table { width: 100%; border-collapse: collapse; }
.gcs-sys-table th, .gcs-sys-table td { padding: 9px 8px; text-align: center; font-size: 12.5px; color: #40546c; border-bottom: 1px solid #eef2f7; white-space: nowrap; }
.gcs-sys-table th { position: sticky; top: 0; background: #f5f8fc; color: #728096; font-weight: 600; z-index: 1; }
.gcs-sys-table tbody tr { cursor: pointer; }
.gcs-sys-table tbody tr:hover { background: #f6f1ff; }
.gcs-sys-table tbody tr.selected { background: #efe9fc; }
.gcs-sys-table td.gcs-empty { text-align: center; color: #94a3b8; padding: 24px 8px; cursor: default; white-space: normal; }
.gcs-sys-table .pick-link { color: #2563eb; font-weight: 600; cursor: pointer; }
.gcs-sys-table tr.selected .pick-link { color: #7c3aed; }
.et-tag { display: inline-block; padding: 1px 7px; border-radius: 4px; background: #fff1ea; color: #c2410c; font-size: 11px; font-weight: 600; }
.gcs-total-bar { padding: 14px; background: #f6f1ff; border-radius: 8px; text-align: center; margin-bottom: 14px; }
.gcs-total-bar span { font-size: 13px; color: #59349d; font-weight: 600; }
.gcs-total-bar strong { font-size: 32px; color: #59349d; margin: 0 8px; }
.gcs-total-bar em { font-size: 13px; color: #7c5aba; font-style: normal; }
.gcs-row { margin-bottom: 14px; }
.gcs-row-title { font-size: 14px; font-weight: 600; color: #34445b; margin-bottom: 8px; }
.gcs-options { display: flex; flex-wrap: wrap; gap: 8px; }
.gcs-options button { min-width: 140px; height: 36px; padding: 0 14px; border: 1px solid #d9e2ef; border-radius: 6px; background: #fff; color: #334155; font-size: 13px; cursor: pointer; text-align: left; }
.gcs-options button.active { border-color: #7c3aed; background: #7c3aed; color: #fff; }
.gcs-record-table { width: 100%; border-collapse: collapse; }
.gcs-record-table th, .gcs-record-table td { padding: 8px; text-align: center; border-bottom: 1px solid #edf2f7; font-size: 12px; color: #40546c; }
.gcs-record-table th { background: #f5f8fc; color: #728096; font-weight: 600; }
.gcs-record-table strong { color: #59349d; font-size: 15px; }

/* 历史记录 PDF 标签 */
.record-tag.pdf-tag { background: #e1f3d8; color: #389e0d; cursor: pointer; }
.record-tag.pdf-tag:hover { background: #d3f0c0; }
/* 记录条内删除按钮（与 SOFA 记录条一致） */
.record-tag.del-tag { cursor: pointer; }
.record-tag.del-tag:hover { background: #fef0f0; color: #f56c6c; }
/* 归档状态标签：待归档（橙，可点击推送）/ 已归档（绿，点击撤销标记） */
.record-tag.archive-tag { cursor: pointer; }
.record-tag.archive-tag.todo { background: #fdf6ec; color: #e6a23c; }
.record-tag.archive-tag.todo:hover { background: #fbe9d0; }
.record-tag.archive-tag.done { background: #e1f3d8; color: #389e0d; }
.record-tag.archive-tag.done:hover { background: #d3f0c0; }
/* 无文书：不可点击，仅说明这条记录还不能归档（自动初评记录常见） */
.record-tag.archive-tag.none { background: #f4f4f5; color: #c0c4cc; cursor: default; }

/* 离屏文书渲染源：移出视口但保留真实尺寸供 html2canvas 渲染 */
.report-offscreen { position: absolute; left: -9999px; top: 0; width: 794px; pointer-events: none; }

/* 文书预览弹窗 */
.report-modal { width: min(880px, calc(100vw - 40px)); }
.report-scroll { background: #e9edf2; padding: 18px; }
.report-view-host { display: flex; justify-content: center; }
.report-view-host .report-page { flex: 0 0 auto; background: #ffffff; box-shadow: 0 2px 12px rgba(0,0,0,0.12); }

@media (max-width: 1200px) {
  .content-row { grid-template-columns: 1fr; }
  .factor-grid { grid-template-columns: 1fr; }
  .bottom-row { grid-template-columns: 1fr; }
}

/* ===== 取数时间范围：快捷按钮组 / 自定义态 / 区间回显 ===== */
.range-presets { display: inline-flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.toolbar .dt-input { height: 32px; border: 1px solid #dcdfe6; border-radius: 4px; padding: 0 10px; font-size: 13px; outline: none; background: #fff; }
.toolbar .dt-input.dt-custom { border-color: #409eff; background: #f2f8ff; }
.btn.active { border-color: #409eff; background: #ecf5ff; color: #409eff; font-weight: 600; }
.range-chip { height: 32px; display: inline-flex; align-items: center; padding: 0 12px; border: 1px dashed #dcdfe6; border-radius: 4px; color: #a8abb2; font-size: 13px; cursor: default; }
.range-chip.active { border-style: solid; border-color: #409eff; background: #ecf5ff; color: #409eff; font-weight: 600; }
.range-echo { display: flex; align-items: center; gap: 8px; padding: 6px 20px; background: #f7fbff; border-bottom: 1px solid #edf2f8; font-size: 12.5px; color: #40546c; }
.range-echo .echo-lbl { color: #909399; }
.range-echo b { font-weight: 600; color: #303133; }
.range-echo .echo-tag { padding: 1px 8px; border-radius: 10px; background: #ecf5ff; color: #409eff; font-size: 11.5px; }
.range-echo .echo-tip { margin-left: auto; color: #e6a23c; }
</style>
