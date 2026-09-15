<template>
  <div class="quality-board qb-theme">
    <!-- 筛选栏 -->
    <div class="filter-bar qb-card">
      <div class="filter-left">
        <el-select
          v-model="periodType"
          style="width: 100px"
          popper-class="qb-popper"
          @change="onPeriodTypeChange"
        >
          <el-option label="按月" value="MONTH" />
          <el-option label="按季" value="QUARTER" />
          <el-option label="按年" value="YEAR" />
        </el-select>

        <el-date-picker
          v-if="periodType === 'MONTH'"
          v-model="monthValue"
          type="month"
          placeholder="选择月份"
          value-format="YYYY-MM"
          :clearable="false"
          popper-class="qb-popper"
          @change="loadOverview"
        />
        <el-date-picker
          v-else-if="periodType === 'YEAR'"
          v-model="yearValue"
          type="year"
          placeholder="选择年份"
          value-format="YYYY"
          :clearable="false"
          popper-class="qb-popper"
          @change="loadOverview"
        />
        <template v-else>
          <el-date-picker
            v-model="quarterYear"
            type="year"
            placeholder="年份"
            value-format="YYYY"
            :clearable="false"
            style="width: 110px"
            popper-class="qb-popper"
            @change="loadOverview"
          />
          <el-select
            v-model="quarter"
            style="width: 84px"
            popper-class="qb-popper"
            @change="loadOverview"
          >
            <el-option v-for="q in 4" :key="q" :label="`Q${q}`" :value="q" />
          </el-select>
        </template>

        <el-select
          v-model="departCode"
          placeholder="全院"
          style="width: 170px"
          clearable
          popper-class="qb-popper"
          @change="loadOverview"
        >
          <el-option
            v-for="dept in departments"
            :key="dept.org_code"
            :label="dept.depart_name"
            :value="dept.org_code"
          />
        </el-select>

        <el-button type="primary" :loading="loading || rulesLoading" @click="loadOverview">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>

      <div class="filter-right">
        <el-button :loading="rulesSyncing" @click="doSyncRules">
          <el-icon><Upload /></el-icon> 同步指标规则
        </el-button>
        <el-button :loading="recalcing" @click="doRecalc">
          <el-icon><Cpu /></el-icon> 触发计算
        </el-button>
        <el-button :loading="syncing" @click="doSync">
          <el-icon><Upload /></el-icon> 同步字典
        </el-button>
      </div>
    </div>

    <!--
      总览（按设计稿）：常驻在 Tab 上方，统计口径是 quality_count_rule 的质控指标（约 60 条），
      数字由前端按 rules 的 calcStatus 聚合，无需后端新增接口。
    -->
    <section class="qb-card overview">
      <div class="ov-head">
        <h2 class="ov-title">
          质控指标总览
          <span class="ov-period">{{ ovPeriod.start }} ~ {{ ovPeriod.end }} · 科室 {{ ovPeriod.dept }}</span>
        </h2>
        <div class="ov-tags">
          <span class="ov-tag"><i class="tag-dot info"></i>来源 quality_count_rule</span>
          <span class="ov-tag strong">质控指标 {{ ruleSummary.total }} 条 · 出数率 {{ ruleSummary.okRate }}%</span>
        </div>
      </div>

      <div class="stat-grid">
        <div class="stat-card">
          <div class="stat-head">
            <span class="stat-label">指标总数</span>
            <span class="stat-ico blue"><el-icon><Grid /></el-icon></span>
          </div>
          <div class="stat-value">{{ ruleSummary.total }}</div>
          <div class="stat-foot">来源 quality_count_rule</div>
        </div>

        <div class="stat-card">
          <div class="stat-head">
            <span class="stat-label">本期已出数</span>
            <span class="stat-ico green"><el-icon><CircleCheck /></el-icon></span>
          </div>
          <div class="stat-value green">{{ ruleSummary.ok }}</div>
          <div class="stat-bar"><i class="fill green" :style="{ width: ruleSummary.okRate + '%' }"></i></div>
          <div class="stat-foot">占指标总数 {{ ruleSummary.okRate }}%</div>
        </div>

        <div class="stat-card">
          <div class="stat-head">
            <span class="stat-label">本期无数据</span>
            <span class="stat-ico orange"><el-icon><WarningFilled /></el-icon></span>
          </div>
          <div class="stat-value orange">{{ ruleSummary.noData }}</div>
          <div class="stat-bar"><i class="fill orange" :style="{ width: ruleSummary.noDataRate + '%' }"></i></div>
          <div class="stat-foot">本期计算结果为空</div>
        </div>

        <div class="stat-card">
          <div class="stat-head">
            <span class="stat-label">未出数（含空壳）</span>
            <span class="stat-ico gray"><el-icon><Hide /></el-icon></span>
          </div>
          <div class="stat-value gray">{{ ruleSummary.pending }}</div>
          <div class="stat-bar"><i class="fill gray" :style="{ width: ruleSummary.pendingRate + '%' }"></i></div>
          <div class="stat-foot">待接源 / 口径待定 / 人工录入</div>
        </div>
      </div>
    </section>

    <el-tabs v-model="activeTab" class="board-tabs" @tab-change="onTabChange">
      <!-- ---------------- 质控指标（真指标，主视图） ---------------- -->
      <el-tab-pane label="质控指标" name="rules">
        <div v-loading="rulesLoading" class="tab-body">
          <el-alert
            v-if="!rulesLoading && rules.length === 0"
            type="warning"
            show-icon
            :closable="false"
            title="还没有指标规则"
            description="点击右上角「同步指标规则」，从 ICU 侧 quality_count_rule 拉取。首次使用同步一次即可，之后 ICU 侧增减规则再同步。"
          />

          <template v-else>
            <el-alert
              v-if="rulesPendingCount > 0"
              type="warning"
              show-icon
              :closable="false"
              style="margin-bottom: 12px"
              :title="rulesPendingCount + ' 条指标口径待确认'"
              description="涉及 quality_15 / quality_30 / quality_31 —— 这三个原子项自带百分比语义，源表存的究竟是「率」还是「率之和」尚未与 ICU 侧确认。值按公式照常给出，但可能有数量级偏差；其余指标不受影响。"
            />

            <div class="block-title">
              <span class="dot"></span>
              指标 = 分子 ÷ 分母 × 系数
              <el-tag size="small" type="info" effect="plain">
                共 {{ rules.length }} 条 · 已出数 {{ rulesOkCount }}
              </el-tag>
            </div>

            <el-table :data="rules" stripe border size="small" max-height="620">
              <el-table-column label="指标名称" min-width="300">
                <template #default="{ row }">
                  <span>{{ row.countName }}</span>
                  <el-tooltip v-if="row.remark" placement="top" :content="row.remark">
                    <span class="remark-dot">?</span>
                  </el-tooltip>
                  <el-tag
                    v-if="row.pendingConfirm"
                    size="small"
                    type="warning"
                    effect="plain"
                    class="pending-tag"
                  >
                    口径待确认
                  </el-tag>
                </template>
              </el-table-column>

              <el-table-column label="分子" min-width="190">
                <template #default="{ row }">
                  <div class="atom-cell">
                    <a class="code-link" @click="openAtom(row.numeratorCode)">
                      {{ row.numeratorCode }}
                    </a>
                    <span class="atom-val">
                      {{ isBlank(row.numeratorValue) ? '—' : num(row.numeratorValue) }}
                    </span>
                  </div>
                  <div class="atom-name">{{ row.numeratorName || '' }}</div>
                </template>
              </el-table-column>

              <el-table-column label="分母" min-width="190">
                <template #default="{ row }">
                  <div class="atom-cell">
                    <a class="code-link" @click="openAtom(row.denominatorCode)">
                      {{ row.denominatorCode }}
                    </a>
                    <span class="atom-val">
                      {{ isBlank(row.denominatorValue) ? '—' : num(row.denominatorValue) }}
                    </span>
                  </div>
                  <div class="atom-name">{{ row.denominatorName || '' }}</div>
                </template>
              </el-table-column>

              <el-table-column label="单位" width="66" align="center">
                <template #default="{ row }">{{ row.displayUnit }}</template>
              </el-table-column>

              <el-table-column label="本期值" width="124" align="right">
                <template #default="{ row }">
                  <span v-if="isBlank(row.value)" class="empty-value">—</span>
                  <span v-else class="rule-value" :class="{ 'is-pending': row.pendingConfirm }">
                    {{ num(row.value) }}<em>{{ row.displayUnit }}</em>
                  </span>
                </template>
              </el-table-column>

              <!--
                目标值 / 预警值：ICU 侧这四列目前全是 NULL，所以这里多半显示「未配置」。
                仍保留这两列 —— 质控最关心的「达标与否」必须有地方看，
                等本院配好值就能直接判定，不必再改页面。
              -->
              <el-table-column label="目标值" width="92" align="right">
                <template #default="{ row }">
                  <span v-if="isBlank(row.targetValue)" class="empty-value">未配置</span>
                  <span v-else>{{ num(row.targetValue) }}</span>
                </template>
              </el-table-column>

              <el-table-column label="预警值" width="92" align="right">
                <template #default="{ row }">
                  <span v-if="isBlank(row.warningValue)" class="empty-value">未配置</span>
                  <span v-else>{{ num(row.warningValue) }}</span>
                </template>
              </el-table-column>

              <el-table-column label="状态" width="94" align="center">
                <template #default="{ row }">
                  <el-tag :type="calcTag(row.calcStatus).type" size="small" effect="light">
                    {{ calcTag(row.calcStatus).label }}
                  </el-tag>
                </template>
              </el-table-column>

              <el-table-column label="血缘" width="132" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" @click="openAtom(row.numeratorCode)">
                    分子
                  </el-button>
                  <el-button link type="primary" size="small" @click="openAtom(row.denominatorCode)">
                    分母
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>
        </div>
      </el-tab-pane>

      <!-- ---------------- 原子项（原「指标看板」，退为明细层） ---------------- -->
      <el-tab-pane label="原子项明细" name="board">
        <div v-loading="loading" class="tab-body">
          <el-alert
            v-if="!loading && domainPanels.length === 0"
            type="warning"
            show-icon
            :closable="false"
            title="指标字典为空"
            description="请先点击右上角「同步字典」，从 classpath:quality/metrics/*.yaml 同步指标定义。"
          />
          <el-alert
            v-if="domainPanels.length > 0"
            type="info"
            show-icon
            :closable="false"
            style="margin-bottom: 12px"
            title="这一页是原子项，不是质控指标"
            description="quality_xxx 只是「多少人 / 多少天」的原子量，自身成不了率。真正的指标 = 分子 ÷ 分母 × 系数，在「质控指标」页。本页用于排查某个率的分母为什么是 0、以及查看暂未被任何指标引用的原子项。"
          />
          <el-collapse v-else v-model="activeDomains" class="qb-collapse">
            <el-collapse-item
              v-for="group in domainPanels"
              :key="group.domain"
              :name="group.domain"
            >
              <template #title>
                <span class="domain-title">{{ group.domain }}</span>
                <span class="domain-count">
                  {{ group.metrics.length }} 条 · 已出数 {{ group.okCount }}
                </span>
              </template>
              <el-table :data="group.metrics" stripe border size="small">
                <el-table-column prop="code" label="指标编号" width="118" />
                <el-table-column label="指标名称" min-width="280">
                  <template #default="{ row }">
                    <span>{{ row.name }}</span>
                    <el-tooltip v-if="row.remark" placement="top" :content="row.remark">
                      <span class="remark-dot">?</span>
                    </el-tooltip>
                  </template>
                </el-table-column>
                <el-table-column prop="unit" label="单位" width="66" align="center" />
                <el-table-column label="本期值" width="110" align="right">
                  <template #default="{ row }">
                    <span v-if="isBlank(row.value)" class="empty-value">—</span>
                    <a v-else class="value-link" @click="openMetric(row)">{{ num(row.value) }}</a>
                  </template>
                </el-table-column>
                <el-table-column label="分子 / 分母" width="128" align="right">
                  <template #default="{ row }">
                    <span v-if="isBlank(row.numerator) && isBlank(row.denominator)" class="empty-value">—</span>
                    <span v-else>{{ num(row.numerator) }} / {{ num(row.denominator) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="计算状态" width="98" align="center">
                  <template #default="{ row }">
                    <el-tag :type="calcTag(row.calcStatus).type" size="small" effect="light">
                      {{ calcTag(row.calcStatus).label }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="实现状态" width="112" align="center">
                  <template #default="{ row }">
                    <el-tag :type="implTag(row.implStatus).type" size="small" effect="plain">
                      {{ implTag(row.implStatus).label }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="215" align="center" fixed="right">
                  <template #default="{ row }">
                    <el-button
                      link
                      type="success"
                      size="small"
                      :loading="rowCalcCode === row.code"
                      :disabled="!!rowCalcCode && rowCalcCode !== row.code"
                      @click="calcOne(row)"
                    >
                      {{ rowCalcCode === row.code ? '计算中' : '计算' }}
                    </el-button>
                    <el-button link type="primary" size="small" @click="openMetric(row)">
                      口径血缘
                    </el-button>
                    <el-button
                      v-if="row.implStatus === 'MANUAL'"
                      link
                      type="warning"
                      size="small"
                      @click="openManual(row)"
                    >
                      录入
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-collapse-item>
          </el-collapse>
        </div>
      </el-tab-pane>

      <!-- ---------------- 覆盖率报告 ---------------- -->
      <el-tab-pane label="覆盖率报告" name="coverage">
        <div v-loading="coverageLoading" class="tab-body">
          <div class="block-title">
            <span class="dot"></span>
            <span>按域承诺清单（配置层对 127 条的「可算 / 空壳 / 待接数据源」承诺）</span>
          </div>
          <el-table :data="coverage.summary || []" border size="small" row-key="domain">
            <el-table-column prop="domain" label="所属域" min-width="160" />
            <el-table-column prop="total" label="指标数" width="90" align="right" />
            <el-table-column prop="impl" label="可算" width="90" align="right" />
            <el-table-column prop="placeholder" label="空壳" width="90" align="right" />
            <el-table-column prop="pending" label="待接数据源" width="110" align="right" />
          </el-table>

          <div class="block-title" style="margin-top: 18px">
            <span class="dot"></span>
            <span>逐条明细</span>
          </div>
          <el-table :data="coverage.metrics || []" border size="small" max-height="520">
            <el-table-column prop="code" label="指标编号" width="118" />
            <el-table-column prop="name" label="指标名称" min-width="240" />
            <el-table-column prop="domain" label="所属域" width="130" />
            <el-table-column prop="fact" label="事实层" width="150" />
            <el-table-column prop="factStatus" label="事实层状态" width="120" align="center">
              <template #default="{ row }">
                <el-tag
                  :type="row.factStatus === 'ACTIVE' ? 'success' : row.factStatus ? 'warning' : 'info'"
                  size="small"
                  effect="plain"
                >
                  {{ row.factStatus || '—' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="有效状态" width="112" align="center">
              <template #default="{ row }">
                <el-tag :type="implTag(row.implStatus).type" size="small">
                  {{ implTag(row.implStatus).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="valueType" label="值类型" width="86" align="center" />
            <el-table-column prop="expressionVersion" label="口径版本" width="90" align="center">
              <template #default="{ row }">v{{ row.expressionVersion ?? '—' }}</template>
            </el-table-column>
            <el-table-column prop="remark" label="说明" min-width="260" show-overflow-tooltip />
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ---------------- 事实层 ---------------- -->
      <el-tab-pane label="事实层" name="facts">
        <div v-loading="factsLoading" class="tab-body">
          <el-alert
            type="info"
            show-icon
            :closable="false"
            title="改 YAML 前先在页面上确认口径"
            description="事实层（DWD）由 DSL 声明、引擎编译成 SQL 并物化。下面展示的是按当前周期注入 :periodStart / :periodEnd 后编译出的实际 SQL。"
            style="margin-bottom: 14px"
          />
          <el-table :data="facts || []" border size="small">
            <el-table-column type="expand">
              <template #default="{ row }">
                <pre class="sql-box">{{ factSql(row.sourceTables) }}</pre>
              </template>
            </el-table-column>
            <el-table-column prop="fact" label="事实层" width="170" />
            <el-table-column prop="domain" label="所属域" width="140" />
            <el-table-column prop="status" label="状态" width="130" align="center">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'warning'" size="small" effect="plain">
                  {{ row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="source" label="源表" width="160" />
            <el-table-column prop="note" label="说明" min-width="300" show-overflow-tooltip />
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ---------------- 计算批次 ---------------- -->
      <el-tab-pane label="计算批次" name="runs">
        <div v-loading="runsLoading" class="tab-body">
          <el-table :data="runs || []" border size="small" max-height="560">
            <el-table-column prop="runId" label="批次号" width="220" />
            <el-table-column prop="periodType" label="周期类型" width="96" align="center" />
            <el-table-column prop="periodStart" label="周期开始" width="160"
                             :formatter="(row) => fmtPeriod(row.periodStart)" />
            <el-table-column prop="periodEnd" label="周期结束" width="160"
                             :formatter="(row) => fmtPeriod(row.periodEnd)" />
            <el-table-column prop="departCode" label="科室" width="140"
                             :formatter="(row) => deptName(row.departCode)" />
            <el-table-column prop="triggerType" label="触发" width="96" align="center" />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag :type="runTag(row.status).type" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="metricTotal" label="总数" width="76" align="right" />
            <el-table-column prop="metricOk" label="成功" width="76" align="right" />
            <el-table-column prop="metricFail" label="失败" width="76" align="right" />
            <el-table-column prop="metricPlaceholder" label="占位" width="76" align="right" />
            <el-table-column prop="durationMs" label="耗时(ms)" width="100" align="right" />
            <el-table-column prop="operator" label="触发人" width="100" />
            <el-table-column prop="createTime" label="创建时间" width="160" />
            <el-table-column prop="message" label="信息" min-width="200" show-overflow-tooltip />
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- ---------------- 计算进度（异步批次） ---------------- -->
    <el-dialog
      v-model="calcVisible"
      title="指标计算"
      width="460px"
      class="qb-overlay"
      :close-on-click-modal="false"
      :close-on-press-escape="!calcRunning"
      :show-close="!calcRunning"
    >
      <div class="calc-body">
        <el-progress
          :percentage="calcPercent"
          :stroke-width="14"
          :status="calcDone
            ? (calcDone.status === 'SUCCESS' ? 'success' : calcDone.status === 'PARTIAL' ? 'warning' : 'exception')
            : ''"
        />
        <div class="calc-line">
          <span>已完成 {{ calcProgress.done }} / {{ calcProgress.total }} 条</span>
          <span class="calc-elapsed">已用时 {{ calcElapsed }}</span>
        </div>
        <div class="calc-stat">
          <span class="s-ok">成功 {{ calcProgress.ok }}</span>
          <span class="s-fail">失败 {{ calcProgress.fail }}</span>
          <span class="s-hold">占位 {{ calcProgress.placeholder }}</span>
        </div>
        <div v-if="calcRunning" class="calc-tip">
          计算在服务端进行，可点「后台运行」关闭本窗口；完成后会自动刷新看板。
        </div>
        <el-alert
          v-else-if="calcDone && calcDone.message"
          :type="calcDone.status === 'SUCCESS' ? 'success' : 'error'"
          :closable="false"
          :title="calcDone.message"
        />
      </div>
      <template #footer>
        <el-button v-if="calcRunning" @click="calcVisible = false">后台运行</el-button>
        <el-button v-else type="primary" @click="calcVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ---------------- 指标详情抽屉 ---------------- -->
    <el-drawer
      v-model="detailVisible"
      :title="detail.name || detail.code || '指标详情'"
      size="62%"
      class="qb-overlay"
    >
      <div v-loading="detailLoading" class="detail-body">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="指标编号">{{ detail.code || '—' }}</el-descriptions-item>
          <el-descriptions-item label="所属域">{{ detail.domain || '—' }}</el-descriptions-item>
          <el-descriptions-item label="单位">{{ detail.unit || '—' }}</el-descriptions-item>
          <el-descriptions-item label="事实层">{{ detail.factName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="实现状态">
            <el-tag :type="implTag(detail.implStatus).type" size="small">
              {{ implTag(detail.implStatus).label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="口径版本">v{{ detail.expressionVersion ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="统计周期" :span="2">
            {{ fmtPeriod(detail.periodStart) }} ~ {{ fmtPeriodEnd(detail.periodEnd) }} · 科室 {{ deptName(detail.departCode) }}
          </el-descriptions-item>
          <el-descriptions-item label="口径说明" :span="2">
            {{ detail.remark || '—' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="!detail.result"
          type="info"
          show-icon
          :closable="false"
          style="margin-top: 14px"
          title="本期无计算结果"
          :description="noResultReason(detail.implStatus)"
        />

        <div v-else class="result-cards">
          <div class="result-card">
            <div class="result-label">指标值</div>
            <div class="result-value">{{ num(detail.result.metricValue, 4) }}</div>
            <div class="result-unit">{{ detail.result.unit || detail.unit || '' }}</div>
          </div>
          <div class="result-card">
            <div class="result-label">分子 / 分母</div>
            <div class="result-value small">
              {{ num(detail.result.numerator, 4) }} / {{ num(detail.result.denominator, 4) }}
            </div>
          </div>
          <div class="result-card">
            <div class="result-label">计算状态</div>
            <div class="result-value small">{{ detail.result.calcStatus || '—' }}</div>
          </div>
          <div class="result-card">
            <div class="result-label">数据快照批次</div>
            <div class="result-value small mono">{{ detail.result.runId || '—' }}</div>
          </div>
        </div>

        <el-tabs v-model="detailTab" style="margin-top: 16px">
          <el-tab-pane label="血缘与 SQL" name="trace">
            <el-table :data="detail.traces || []" border size="small" max-height="420">
              <el-table-column type="expand">
                <template #default="{ row }">
                  <pre class="sql-box">{{ row.sql || '（未记录 SQL 原文）' }}</pre>
                </template>
              </el-table-column>
              <el-table-column prop="dimKey" label="维度" width="120" />
              <el-table-column prop="factName" label="事实层" width="150" />
              <el-table-column prop="operators" label="算子链" min-width="200" show-overflow-tooltip />
              <el-table-column prop="scannedRows" label="扫描行数" width="110" align="right" />
              <el-table-column prop="numRows" label="分子行" width="90" align="right" />
              <el-table-column prop="denRows" label="分母行" width="90" align="right" />
              <el-table-column prop="durationMs" label="耗时(ms)" width="100" align="right" />
              <el-table-column prop="expressionVersion" label="版本" width="70" align="center">
                <template #default="{ row }">v{{ row.expressionVersion ?? '—' }}</template>
              </el-table-column>
            </el-table>
            <el-empty
              v-if="(detail.traces || []).length === 0"
              description="本周期无血缘记录（指标未出数或未预落库）"
              :image-size="70"
            />
          </el-tab-pane>

          <el-tab-pane :label="`患者明细（${(detail.patients || []).length}）`" name="patients">
            <el-table :data="detail.patients || []" border size="small" max-height="420">
              <el-table-column prop="patientName" label="姓名" width="100" />
              <el-table-column prop="inHospitalNo" label="住院号" width="150" />
              <el-table-column prop="patientId" label="患者ID" width="130" />
              <el-table-column prop="departCode" label="科室" width="140"
                               :formatter="(row) => deptName(row.departCode)" />
              <el-table-column label="入分子" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.inNumerator === 1 ? 'success' : 'info'" size="small" effect="plain">
                    {{ row.inNumerator === 1 ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="入分母" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.inDenominator === 1 ? 'success' : 'info'" size="small" effect="plain">
                    {{ row.inDenominator === 1 ? '是' : '否' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
            <el-empty
              v-if="(detail.patients || []).length === 0"
              description="无患者级命中明细（下钻按需生成，指标未出数时为空）"
              :image-size="70"
            />
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <!-- ---------------- 人工录入弹窗 ---------------- -->
    <el-dialog v-model="manualVisible" title="人工录入指标值" width="440px" class="qb-overlay">
      <div class="manual-row">
        <span class="manual-label">指标</span>
        <span>{{ manualForm.name }}</span>
      </div>
      <div class="manual-row">
        <span class="manual-label">编号</span>
        <span class="mono">{{ manualForm.code }}</span>
      </div>
      <div class="manual-row">
        <span class="manual-label">周期</span>
        <span>{{ overview.periodStart }} ~ {{ overview.periodEnd }}</span>
      </div>
      <div class="manual-row">
        <span class="manual-label">值</span>
        <el-input-number
          v-model="manualForm.value"
          :precision="4"
          :controls="false"
          style="width: 170px"
        />
      </div>
      <template #footer>
        <el-button @click="manualVisible = false">取消</el-button>
        <el-button type="primary" :loading="manualSaving" @click="doManual">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Cpu, Upload, Grid, CircleCheck, WarningFilled, Hide } from '@element-plus/icons-vue'
import { externalParam } from '../utils/external'
import '../styles/quality-theme.css'
import {
  fetchQualityOverview,
  fetchQualityRules,
  syncQualityRules,
  fetchQualityMetric,
  fetchQualityCoverage,
  fetchQualityFacts,
  fetchQualityRuns,
  fetchQualityDepartments,
  recalcQuality,
  recalcQualityMetric,
  fetchQualityRun,
  saveQualityManual,
  syncQualityIndex
} from '../api/quality'

// ---------------- 状态字典 ----------------
const CALC_STATUS = {
  OK: { label: '已出数', type: 'success' },
  NO_DATA: { label: '无数据', type: 'warning' },
  NOT_CALC: { label: '未计算', type: 'info' },
  PLACEHOLDER: { label: '空壳', type: 'info' },
  PENDING_SOURCE: { label: '待接源', type: 'warning' },
  MANUAL: { label: '人工录入', type: 'primary' },
  ERROR: { label: '计算异常', type: 'danger' }
}
const IMPL_STATUS = {
  IMPL: { label: '已实现', type: 'success' },
  PENDING_SOURCE: { label: '待接数据源', type: 'warning' },
  PLACEHOLDER: { label: '口径待定', type: 'info' },
  MANUAL: { label: '人工录入', type: 'primary' }
}
const RUN_STATUS = {
  RUNNING: { type: 'primary' },
  SUCCESS: { type: 'success' },
  PARTIAL: { type: 'warning' },
  FAILED: { type: 'danger' }
}

function calcTag(code) {
  return CALC_STATUS[code] || { label: code || '—', type: 'info' }
}
function implTag(code) {
  return IMPL_STATUS[code] || { label: code || '—', type: 'info' }
}
function runTag(code) {
  return RUN_STATUS[code] || { type: 'info' }
}

// ---------------- 筛选条件 ----------------
const periodType = ref('MONTH')
const quarter = ref(1)

/** 默认取「上个月」，与后端 PeriodRange 缺省口径保持一致 */
function lastMonth() {
  const d = new Date()
  d.setDate(1)
  d.setMonth(d.getMonth() - 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}
function thisYear() {
  return String(new Date().getFullYear())
}

/** 把后端周期时间解析成 Date；无法解析（如 yyyy-MM、空值）返回 null，由调用方原样显示。 */
function toDate(v) {
  if (v === null || v === undefined || v === '') return null
  // Jackson 被配成时间戳时输出数组 [2026,9,1,0,0]
  if (Array.isArray(v)) {
    const [y, m, d = 1, hh = 0, mi = 0, ss = 0] = v
    return y ? new Date(y, m - 1, d, hh, mi, ss) : null
  }
  const s = String(v).trim()
  if (!s) return null
  // LocalDateTime 默认序列化成 ISO「2026-09-01T00:00:00」，中间的 T 要去掉
  const iso = s.match(/^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})(?::(\d{2}))?/)
  if (iso) return new Date(+iso[1], +iso[2] - 1, +iso[3], +iso[4], +iso[5], +(iso[6] || 0))
  if (/^\d{4}-\d{2}$/.test(s)) {
    const [y, m] = s.split('-').map(Number)
    return new Date(y, m - 1, 1)
  }
  if (/^\d+$/.test(s)) {
    const d = new Date(Number(s))
    return isNaN(d.getTime()) ? null : d
  }
  return null
}

function fmtDate(d) {
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} `
    + `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/** 周期时间显示格式化：yyyy-MM-dd HH:mm:ss（空值显示 —）。 */
function fmtPeriod(v) {
  if (v === null || v === undefined || v === '') return '—'
  const d = toDate(v)
  return d ? fmtDate(d) : String(v)
}

/**
 * 周期结束显示：回退 1 秒，按闭区间呈现。
 *
 * 后端 PeriodRange 是左闭右开，存的是「下月 1 日 00:00:00」（SQL 用 >= start AND < end，
 * 这样才能完整覆盖 9/30 23:59:59.999 的数据），但业务看的是「9 月 = 9/30 23:59:59」。
 * 这里只在显示层回退 1 秒；提交给后端的 periodStart、以及引擎的区间判定一律用原值，
 * 不能把开区间改成闭区间，否则跨周期的边界数据会被前后两期各计一次。
 */
function fmtPeriodEnd(v) {
  if (v === null || v === undefined || v === '') return '—'
  const d = toDate(v)
  return d ? fmtDate(new Date(d.getTime() - 1000)) : String(v)
}

const monthValue = ref(lastMonth())
const yearValue = ref(thisYear())
const quarterYear = ref(thisYear())
// 外链进入时 ICU 会把科室编码带在 departCode 上，默认就按该科室查：
// 否则一进页面就是全院全量计算（127 个指标 × 全院数据），既慢也不是使用者想看的。
// 下拉仍是 clearable，用户可随时清空回到全院。
const departCode = ref(externalParam('departCode'))
const departments = ref([])

/** 周期起始（后端 PeriodRange.of 支持 yyyy-MM / yyyy） */
const periodStart = computed(() => {
  if (periodType.value === 'MONTH') return monthValue.value
  if (periodType.value === 'YEAR') return yearValue.value
  const m = (Number(quarter.value) - 1) * 3 + 1
  return `${quarterYear.value}-${String(m).padStart(2, '0')}`
})

function onPeriodTypeChange() {
  // 切周期类型时清掉另一类型的空值，避免把空串传给后端（会被当作「缺省=上个月」）
  if (periodType.value === 'MONTH' && !monthValue.value) monthValue.value = lastMonth()
  if (periodType.value === 'YEAR' && !yearValue.value) yearValue.value = thisYear()
  if (periodType.value === 'QUARTER' && !quarterYear.value) quarterYear.value = thisYear()
  loadOverview()
}

// ---------------- 看板数据 ----------------
const loading = ref(false)
// 默认落在「质控指标」：这里列的才是质控要管的率，原子项是排查时才看的下一层
const activeTab = ref('rules')
const overview = reactive({})
const activeDomains = ref([])

const domainPanels = computed(() =>
  (overview.groups || []).map((g) => ({
    domain: g.domain,
    metrics: g.metrics || [],
    okCount: (g.metrics || []).filter((m) => m.calcStatus === 'OK').length
  }))
)

/**
 * 总览卡口径（按设计图）：统计 quality_count_rule 同步来的质控指标（rules，约 60 条），
 * 而不是原子项明细的 127 条。按 calcStatus 聚合：OK=已出数，NO_DATA=无数据，其余全部
 * 计入「未出数（含空壳）」（未计算 / 空壳 / 待接源 / 人工录入 / 异常）。
 * 百分比保留一位小数，与设计稿「出数率 0.0%」一致。
 */
const ruleSummary = computed(() => {
  const total = rules.value.length
  const ok = rules.value.filter((r) => r.calcStatus === 'OK').length
  const noData = rules.value.filter((r) => r.calcStatus === 'NO_DATA').length
  const pending = total - ok - noData
  const pct = (n) => (total ? ((Number(n) / total) * 100).toFixed(1) : '0.0')
  return {
    total,
    ok,
    noData,
    pending,
    okRate: pct(ok),
    noDataRate: pct(noData),
    pendingRate: pct(pending)
  }
})

/**
 * 总览周期文案：直接按当前筛选条件本地拼出闭区间日期（与提交给后端的 PeriodRange 同源），
 * 这样在「质控指标」Tab（不加载 overview）下总览也始终能说清是哪段时间、哪个科室。
 */
const ovPeriod = computed(() => {
  const now = new Date()
  let year = now.getFullYear()
  let startMonth = 0
  let spanMonths = 1
  if (periodType.value === 'MONTH') {
    const [y, m] = String(monthValue.value || '').split('-').map(Number)
    if (y) year = y
    startMonth = (m || 1) - 1
  } else if (periodType.value === 'YEAR') {
    year = Number(yearValue.value) || year
    startMonth = 0
    spanMonths = 12
  } else {
    year = Number(quarterYear.value) || year
    startMonth = (Number(quarter.value) - 1) * 3
    spanMonths = 3
  }
  const day = (d) => fmtDate(d).slice(0, 10)
  const start = new Date(year, startMonth, 1)
  const end = new Date(year, startMonth + spanMonths, 1, 0, 0, -1)
  return { start: day(start), end: day(end), dept: deptName(departCode.value) }
})

/**
 * 刷新入口：按当前 Tab 决定刷哪个视图。
 *
 * 页面有两个数据视图 —— 「质控指标」（真指标，主视图）与「指标看板」（原子项）。
 * 顶部周期/科室筛选与刷新按钮是两者共用的，若这里固定刷 overview，
 * 切到「质控指标」后点刷新就会毫无反应（看着像坏了）。
 */
function loadOverview() {
  return activeTab.value === 'rules' ? loadRules() : loadOverviewData()
}

async function loadOverviewData() {
  loading.value = true
  try {
    const res = await fetchQualityOverview({
      periodType: periodType.value,
      periodStart: periodStart.value,
      departCode: departCode.value || ''
    })
    Object.assign(overview, res || {})
    activeDomains.value = (res && res.groups ? res.groups : []).map((g) => g.domain)
  } catch (e) {
    console.warn('质控看板加载失败:', e.message || e)
  } finally {
    loading.value = false
  }
}

async function loadDepartments() {
  try {
    const res = await fetchQualityDepartments()
    departments.value = Array.isArray(res) ? res : []
  } catch (e) {
    // 科室下拉是锦上添花，取不到不影响看板
    console.warn('科室列表加载失败:', e.message || e)
  }
}

/**
 * 科室编码 → 名称。
 *
 * 筛选与落库一律用 org_code（它是结果表的主键维度），但页面展示必须用人看得懂的
 * depart_name —— 临床看「20070131」没有任何意义。
 * 字典没加载出来时退回编码：宁可显示编码，也不要显示空白让使用者以为查错了科室。
 */
function deptName(code) {
  const c = String(code == null ? '' : code).trim()
  if (!c || c === 'ALL') return '全院'
  const hit = departments.value.find((d) => String(d.org_code) === c)
  return hit ? hit.depart_name : c
}

// ---------------- 质控指标（真指标 = 分子 ÷ 分母 × 系数） ----------------
//
// 与下面「指标看板」的关系必须说清楚：那边列的是**原子项**（quality_xxx，
// 一个「多少人 / 多少天」的量，自身成不了率），这边列的才是质控真正要管的指标。
// 例：ICU镇痛评估率 = quality_306（做了镇痛评估的人数）÷ quality_403（同期患者总数）× 100。
// 值不重算 SQL —— 分子分母已由引擎落库，后端只做一次除法。
const rules = ref([])
const rulesLoading = ref(false)
const rulesSyncing = ref(false)

const rulesOkCount = computed(() => rules.value.filter((r) => r.calcStatus === 'OK').length)
/** 口径待确认（涉及 quality_15/30/31）的条数，用于顶部降级提示 */
const rulesPendingCount = computed(() => rules.value.filter((r) => r.pendingConfirm).length)

async function loadRules() {
  rulesLoading.value = true
  try {
    const res = await fetchQualityRules({
      periodType: periodType.value,
      periodStart: periodStart.value,
      departCode: departCode.value || '',
      includeHidden: false
    })
    rules.value = Array.isArray(res) ? res : []
  } catch (e) {
    console.warn('指标规则加载失败:', e.message || e)
  } finally {
    rulesLoading.value = false
  }
}

/**
 * 从 ICU 侧同步指标规则（幂等）。
 *
 * 同步不覆盖本院已配置的目标值 / 预警值，因此可以放心重复点。
 * 失败多半是没有跨 schema 读权限或未执行 11_quality_count_rule.sql，提示里点明排查方向。
 */
async function doSyncRules() {
  rulesSyncing.value = true
  try {
    const res = await syncQualityRules()
    const added = res && res.added != null ? res.added : 0
    ElMessage.success(added > 0 ? `已同步 ${added} 条指标规则` : '指标规则已是最新，无需新增')
    await loadRules()
  } catch (e) {
    ElMessage.error(
      '同步指标规则失败：' + (e.message || e) + '（确认已执行 sql/11_quality_count_rule.sql 且库账号可跨 schema 读 ICU）'
    )
  } finally {
    rulesSyncing.value = false
  }
}

/** 打开分子 / 分母原子项的口径血缘 —— 回答「这个率是怎么来的」。 */
function openAtom(code) {
  if (!code) return
  openMetric({ code })
}

// ---------------- 覆盖率 / 事实层 / 批次（按 Tab 懒加载） ----------------
const coverage = reactive({})
const coverageLoading = ref(false)
const facts = ref([])
const factsLoading = ref(false)
const runs = ref([])
const runsLoading = ref(false)

async function loadCoverage() {
  coverageLoading.value = true
  try {
    Object.assign(coverage, (await fetchQualityCoverage()) || {})
  } catch (e) {
    console.warn('覆盖率报告加载失败:', e.message || e)
  } finally {
    coverageLoading.value = false
  }
}

async function loadFacts() {
  factsLoading.value = true
  try {
    facts.value = (await fetchQualityFacts(periodStart.value)) || []
  } catch (e) {
    console.warn('事实层加载失败:', e.message || e)
  } finally {
    factsLoading.value = false
  }
}

async function loadRuns() {
  runsLoading.value = true
  try {
    runs.value = (await fetchQualityRuns()) || []
  } catch (e) {
    console.warn('批次加载失败:', e.message || e)
  } finally {
    runsLoading.value = false
  }
}

function onTabChange(name) {
  if (name === 'rules' && rules.value.length === 0) loadRules()
  if (name === 'board' && !overview.groups) loadOverviewData()
  if (name === 'coverage' && !coverage.metrics) loadCoverage()
  if (name === 'facts' && facts.value.length === 0) loadFacts()
  if (name === 'runs' && runs.value.length === 0) loadRuns()
}

// ---------------- 指标详情抽屉 ----------------
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailTab = ref('trace')
const detail = reactive({})

async function openMetric(row) {
  detailVisible.value = true
  detailLoading.value = true
  detailTab.value = 'trace'
  // 先清空，避免快速连点时短暂显示上一条指标的数据
  Object.keys(detail).forEach((k) => delete detail[k])
  try {
    const res = await fetchQualityMetric(row.code, {
      periodStart: periodStart.value,
      departCode: departCode.value || ''
    })
    Object.assign(detail, res || {})
  } catch (e) {
    ElMessage.error('指标详情加载失败：' + (e.message || e))
  } finally {
    detailLoading.value = false
  }
}

function noResultReason(implStatus) {
  if (implStatus === 'PENDING_SOURCE') return '该指标的事实层尚未接入数据源，引擎不给出假 0，接入后即生效。'
  if (implStatus === 'PLACEHOLDER') return '该指标口径待业务确认（如判定标准未定），当前为空壳占位。'
  if (implStatus === 'MANUAL') return '该指标为人工录入类，请在指标行点击「录入」填写。'
  return '该指标在本周期尚未计算，可点击右上角「触发计算」。'
}

// ---------------- 触发计算 / 同步字典 ----------------
const recalcing = ref(false)
const syncing = ref(false)

// 计算进度（异步批次）
// 批算要重建全部事实层，生产上常以分钟计，远超前端 60s 超时。
// 旧实现是同步等：超时后前端报「失败」，后端其实还在写库，用户往往以为没跑成又点一次。
// 改为「提交即返回 + 轮询进度」：既不会假失败，也能看到跑到哪一条。
const calcVisible = ref(false)
const calcRunning = ref(false)
const calcElapsed = ref('0s')
const calcProgress = reactive({ runId: '', total: 0, done: 0, ok: 0, fail: 0, placeholder: 0 })
const calcDone = ref(null)
let calcClock = null
let calcAbort = false

const calcPercent = computed(() => {
  if (calcDone.value) return 100
  const t = calcProgress.total || 0
  return t ? Math.min(99, Math.round((calcProgress.done / t) * 100)) : 0
})

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function doRecalc() {
  if (calcRunning.value) {
    ElMessage.warning('已有计算任务在执行，请等它跑完再试')
    return
  }
  try {
    await ElMessageBox.confirm(
      `将重算 ${fmtPeriod(overview.periodStart || periodStart.value)} ~ ${fmtPeriodEnd(overview.periodEnd)} 的指标结果（同周期同科室幂等覆盖），是否继续？`,
      '触发计算',
      { type: 'warning', confirmButtonText: '开始计算', cancelButtonText: '取消', customClass: 'qb-overlay' }
    )
  } catch (e) {
    return
  }
  recalcing.value = true
  try {
    const res = await recalcQuality({
      periodType: periodType.value,
      periodStart: periodStart.value,
      departCode: departCode.value || '',
      async: true
    })
    if (!res || !res.runId) throw new Error('未返回批次号')
    // 兼容尚未升级的后端：它不认 async 参数，会同步跑完直接返回终态，此时不必轮询
    if (['SUCCESS', 'PARTIAL', 'FAILED'].includes(res.status)) {
      Object.assign(calcProgress, {
        runId: res.runId,
        total: res.metricTotal || 0,
        done: res.metricTotal || 0,
        ok: res.metricOk || 0,
        fail: res.metricFail || 0,
        placeholder: res.metricPlaceholder || 0
      })
      calcVisible.value = true
      finishCalc(res)
      return
    }
    startCalcProgress(res.runId, res.metricTotal || 0)
  } catch (e) {
    recalcing.value = false
    ElMessage.error('计算失败：' + (e.message || e))
  }
}

/** 打开进度框并开始轮询（只提交、不等结果，因此不会撞上超时） */
function startCalcProgress(runId, total) {
  Object.assign(calcProgress, { runId, total, done: 0, ok: 0, fail: 0, placeholder: 0 })
  calcDone.value = null
  calcVisible.value = true
  calcRunning.value = true
  calcAbort = false
  const t0 = Date.now()
  calcElapsed.value = '0s'
  clearInterval(calcClock)
  calcClock = setInterval(() => {
    calcElapsed.value = Math.round((Date.now() - t0) / 1000) + 's'
  }, 1000)
  pollCalc(runId, t0)
}

async function pollCalc(runId, t0) {
  const MAX_WAIT = 30 * 60 * 1000
  let miss = 0
  while (!calcAbort) {
    await sleep(2000)
    if (Date.now() - t0 > MAX_WAIT) {
      stopCalc()
      recalcing.value = false
      ElMessage.warning('等待超时（30 分钟），后端仍在计算，请稍后刷新或到「计算批次」查看')
      return
    }
    try {
      const r = await fetchQualityRun(runId)
      miss = 0
      if (!r || !r.exists) throw new Error('批次不存在')
      Object.assign(calcProgress, {
        total: r.metricTotal || 0,
        done: r.done || 0,
        ok: r.metricOk || 0,
        fail: r.metricFail || 0,
        placeholder: r.metricPlaceholder || 0
      })
      if (!r.running) {
        finishCalc(r)
        return
      }
    } catch (e) {
      // 轮询偶发失败不中断（后端重启、网络抖动都可能），连续 5 次失败才算真失败
      if (++miss >= 5) {
        stopCalc()
        recalcing.value = false
        ElMessage.error('计算进度获取失败：' + (e.message || e))
        return
      }
    }
  }
}

function finishCalc(run) {
  stopCalc()
  recalcing.value = false
  calcDone.value = run
  const ok = run.metricOk || 0
  const fail = run.metricFail || 0
  const sec = ((run.durationMs || 0) / 1000).toFixed(1)
  if (run.status === 'SUCCESS') {
    ElMessage.success(`计算完成：成功 ${ok} 条，耗时 ${sec}s`)
  } else if (run.status === 'PARTIAL') {
    ElMessage.warning(`计算完成但有失败：成功 ${ok} 条 / 失败 ${fail} 条，耗时 ${sec}s`)
  } else {
    ElMessage.error('计算失败：' + (run.message || '请到「计算批次」页查看批次信息'))
  }
  loadOverview()
  if (runs.value.length) loadRuns()
}

function stopCalc() {
  calcRunning.value = false
  calcAbort = true
  clearInterval(calcClock)
  calcClock = null
}

onUnmounted(() => {
  // 离开页面即停止轮询，避免在后台空转
  calcAbort = true
  clearInterval(calcClock)
})

// ---------------- 单指标计算 ----------------
// 场景：只改了一条指标的口径，只想重算它 —— 不必等 127 条全跑完。
const rowCalcCode = ref('')

async function calcOne(row) {
  if (rowCalcCode.value) return
  if (row.implStatus === 'MANUAL') {
    ElMessage.info('该指标为人工录入类，请点「录入」填写数值')
    return
  }
  rowCalcCode.value = row.code
  const t0 = Date.now()
  try {
    const res = await recalcQualityMetric(row.code, {
      periodType: periodType.value,
      periodStart: periodStart.value,
      departCode: departCode.value || ''
    })
    if (res && res.calcStatus === 'BUSY') {
      ElMessage.warning(res.errorMsg || '已有计算任务在执行，请稍候再试')
      return
    }
    const r = res && res.result
    // 就地更新这一行：127 行整页重查反而比单条计算本身还慢
    if (r) {
      row.value = r.value
      row.numerator = r.numerator
      row.denominator = r.denominator
      row.calcStatus = r.calcStatus
      row.errorMsg = r.errorMsg || ''
    }
    const sec = ((Date.now() - t0) / 1000).toFixed(1)
    if (r && r.calcStatus === 'ERROR') {
      ElMessage.error(`${row.code} 计算失败：${r.errorMsg || '未知错误'}`)
    } else {
      ElMessage.success(`${row.code} 计算完成，耗时 ${sec}s`)
    }
  } catch (e) {
    ElMessage.error('计算失败：' + (e.message || e))
  } finally {
    rowCalcCode.value = ''
  }
}

async function doSync() {
  syncing.value = true
  try {
    const count = await syncQualityIndex()
    ElMessage.success(`配置已重载，指标字典同步 ${count ?? 0} 条`)
    await loadOverview()
  } catch (e) {
    ElMessage.error('同步失败：' + (e.message || e))
  } finally {
    syncing.value = false
  }
}

// ---------------- 人工录入 ----------------
const manualVisible = ref(false)
const manualSaving = ref(false)
const manualForm = reactive({ code: '', name: '', value: null })

function openManual(row) {
  manualForm.code = row.code
  manualForm.name = row.name
  manualForm.value = row.value === null || row.value === undefined ? null : Number(row.value)
  manualVisible.value = true
}

async function doManual() {
  if (manualForm.value === null || manualForm.value === undefined || Number.isNaN(manualForm.value)) {
    ElMessage.warning('请填写指标值')
    return
  }
  manualSaving.value = true
  try {
    await saveQualityManual({
      code: manualForm.code,
      value: manualForm.value,
      periodType: periodType.value,
      periodStart: periodStart.value,
      departCode: departCode.value || ''
    })
    ElMessage.success('录入成功')
    manualVisible.value = false
    await loadOverview()
  } catch (e) {
    ElMessage.error('录入失败：' + (e.message || e))
  } finally {
    manualSaving.value = false
  }
}

// ---------------- 展示工具 ----------------
function isBlank(v) {
  return v === null || v === undefined || v === ''
}

/** 数值展示：整数不带小数，否则保留 digits 位并去掉尾随 0 */
function num(v, digits = 2) {
  if (isBlank(v)) return '—'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  if (Number.isInteger(n)) return String(n)
  return n.toFixed(digits).replace(/0+$/, '').replace(/\.$/, '')
}

/** facts 接口的 sourceTables 可能是数组或字符串，统一成可读文本 */
function factSql(sourceTables) {
  if (!sourceTables) return '（暂无编译 SQL）'
  return Array.isArray(sourceTables) ? sourceTables.join('\n') : String(sourceTables)
}

onMounted(() => {
  loadDepartments()
  loadOverview()
  loadRules()
})
</script>

<style scoped>
/* ===================================================================
   质控看板 · 暖橙主题（设计稿 quality-board.html 适配）
   组件级变量级覆盖在 styles/quality-theme.css，这里只管本页布局与卡片。
   =================================================================== */
.quality-board {
  padding: 24px;
  background: #fafaf9;
  min-height: 100vh;
}

/* 设计稿通用白卡：暖灰描边 + 12px 圆角 + 极轻阴影 */
.qb-card {
  background: #ffffff;
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(28, 25, 23, 0.04);
}

.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
  padding: 14px 18px;
}

.filter-left,
.filter-right {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

/* ---------------- 总览面板（常驻，60 条质控指标口径） ---------------- */
.overview {
  padding: 20px 22px;
  margin-bottom: 20px;
}

.ov-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.ov-title {
  font-size: 18px;
  font-weight: 700;
  color: #1c1917;
  margin: 0;
}

.ov-period {
  font-size: 13px;
  font-weight: 400;
  color: #78716c;
  margin-left: 12px;
  font-variant-numeric: tabular-nums;
}

.ov-tags {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.ov-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #57534e;
  background: #f5f5f4;
  border-radius: 9999px;
  padding: 4px 12px;
}

.ov-tag.strong {
  color: #ea580c;
  font-weight: 600;
}

.tag-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
}
.tag-dot.info {
  background: #0891b2;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-top: 18px;
}

.stat-card {
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  padding: 16px 18px 14px;
  background: linear-gradient(180deg, #ffffff 0%, #fafaf9 100%);
  transition: box-shadow 0.2s, transform 0.2s, border-color 0.2s;
}
.stat-card:hover {
  border-color: #d6d3d1;
  box-shadow: 0 4px 14px rgba(28, 25, 23, 0.06);
  transform: translateY(-1px);
}

.stat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat-label {
  font-size: 13px;
  font-weight: 500;
  color: #78716c;
}

.stat-ico {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
}
.stat-ico.blue {
  color: #0891b2;
  background: #cffafe;
}
.stat-ico.green {
  color: #16a34a;
  background: #dcfce7;
}
.stat-ico.orange {
  color: #d97706;
  background: #fef3c7;
}
.stat-ico.gray {
  color: #78716c;
  background: #f5f5f4;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #1c1917;
  line-height: 1.25;
  margin-top: 8px;
  font-variant-numeric: tabular-nums;
}
.stat-value.green {
  color: #16a34a;
}
.stat-value.orange {
  color: #d97706;
}
.stat-value.gray {
  color: #a8a29e;
}

.stat-bar {
  height: 5px;
  border-radius: 3px;
  background: #f5f5f4;
  margin-top: 12px;
  overflow: hidden;
}
.stat-bar .fill {
  display: block;
  height: 100%;
  border-radius: 3px;
  transition: width 0.4s ease;
}
.fill.green {
  background: #16a34a;
}
.fill.orange {
  background: #d97706;
}
.fill.gray {
  background: #d6d3d1;
}

.stat-foot {
  font-size: 12px;
  color: #a8a29e;
  margin-top: 8px;
}

/* ---------------- Tab 区整体作为一张白卡 ---------------- */
.board-tabs {
  background: #ffffff;
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(28, 25, 23, 0.04);
}
.board-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 16px;
}
.board-tabs :deep(.el-tabs__content) {
  padding: 18px 16px;
}

/* ---------------- 计算进度（异步批次） ---------------- */
.calc-body {
  padding: 4px 2px 0;
}
.calc-line {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #57534e;
  margin-top: 10px;
  font-variant-numeric: tabular-nums;
}
.calc-elapsed {
  color: #a8a29e;
}
.calc-stat {
  display: flex;
  gap: 16px;
  margin-top: 8px;
  font-size: 13px;
}
.calc-stat .s-ok {
  color: #16a34a;
}
.calc-stat .s-fail {
  color: #dc2626;
}
.calc-stat .s-hold {
  color: #78716c;
}
.calc-tip {
  margin-top: 12px;
  font-size: 12px;
  color: #78716c;
  background: #fafaf9;
  border: 1px solid #f5f5f4;
  border-radius: 8px;
  padding: 8px 10px;
}

.tab-body {
  min-height: 200px;
}

.domain-title {
  font-weight: 600;
  color: #44403c;
  margin-right: 10px;
}

.domain-count {
  font-size: 12px;
  color: #78716c;
}

/* 口径待确认提示点：暖橙软底 */
.remark-dot {
  display: inline-block;
  width: 16px;
  height: 16px;
  line-height: 16px;
  text-align: center;
  margin-left: 6px;
  border-radius: 50%;
  background: #ffedd5;
  color: #c2410c;
  font-size: 11px;
  font-weight: 700;
  cursor: help;
}

.empty-value {
  color: #a8a29e;
}

.value-link {
  color: #ea580c;
  font-weight: 600;
  cursor: pointer;
}

.value-link:hover {
  color: #c2410c;
  text-decoration: underline;
}

/* ---- 质控指标（真指标）表：分子/分母单元 ---- */
.atom-cell {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

/* 原子项编号用等宽字体：quality_403 与 quality_4031 肉眼极易看混 */
.code-link {
  font-family: 'Cascadia Mono', Consolas, 'Courier New', monospace;
  font-size: 12px;
  color: #ea580c;
  cursor: pointer;
}

.code-link:hover {
  color: #c2410c;
  text-decoration: underline;
}

.atom-val {
  font-weight: 600;
  color: #292524;
}

.atom-name {
  font-size: 12px;
  color: #78716c;
  margin-top: 2px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pending-tag {
  margin-left: 6px;
  transform: scale(0.92);
}

.rule-value {
  font-weight: 700;
  color: #292524;
}

.rule-value em {
  font-style: normal;
  font-size: 12px;
  font-weight: 400;
  color: #78716c;
  margin-left: 2px;
}

/* 口径待确认的值：给足警示，但仍显示 —— 完全藏起来会让使用者以为没算 */
.rule-value.is-pending {
  color: #d97706;
}

.block-title {
  display: flex;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
  color: #44403c;
  margin-bottom: 12px;
}

.dot {
  width: 4px;
  height: 16px;
  background: linear-gradient(180deg, #f97316, #fb923c);
  border-radius: 2px;
  margin-right: 8px;
  flex-shrink: 0;
}

/* 设计稿代码块：近黑底 + 暖灰字（替代旧的深色主题配色） */
.sql-box {
  margin: 0;
  padding: 14px 16px;
  background: #1c1917;
  color: #e7e5e4;
  border: 1px solid #292524;
  border-radius: 8px;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 320px;
  overflow: auto;
}

.detail-body {
  padding: 0 4px 20px;
}

.result-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-top: 14px;
}

.result-card {
  background: #fafaf9;
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  padding: 14px 16px;
}

.result-label {
  font-size: 12px;
  color: #78716c;
  margin-bottom: 6px;
}

.result-value {
  font-size: 24px;
  font-weight: 700;
  color: #1c1917;
  font-variant-numeric: tabular-nums;
}

.result-value.small {
  font-size: 16px;
  font-weight: 600;
}

.result-unit {
  font-size: 12px;
  color: #78716c;
  margin-top: 4px;
}

.mono {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 12px;
}

.manual-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.manual-label {
  width: 48px;
  color: #78716c;
  font-size: 13px;
  flex-shrink: 0;
}

@media (max-width: 1200px) {
  .stat-grid,
  .result-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .quality-board {
    padding: 12px;
  }
  .stat-grid,
  .result-cards {
    grid-template-columns: 1fr;
  }
}
</style>
