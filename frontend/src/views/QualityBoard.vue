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
      计算异常提示条（放在总览最上方）。
      ERROR 此前与「待接源表」混在同一个统计里，页面上无从分辨：源表结构变了、口径引用
      了不存在的列，都会让指标沉默地缺数，一直拖到上报时才发现。这里直接点名出错的指标。
    -->
    <el-alert
      v-if="ruleSummary.error > 0"
      type="error"
      show-icon
      :closable="false"
      class="qb-error-bar"
    >
      <template #title>
        本期有 {{ ruleSummary.error }} 条指标计算失败，数值为空：{{ errorMetricNames }}
      </template>
      <div class="qb-error-hint">
        常见原因：源表结构变更、口径引用了不存在的列。可在「质控配置 → 计算批次」查看该批次的报错详情。
      </div>
    </el-alert>

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
          <!--
            达标汇总只在「配了阈值且本期有值」的指标上统计。
            分母若用全部指标，会得到一个把未配阈值的指标也算作不达标的假比例。
          -->
          <span v-if="targetSummary.judged > 0" class="ov-tag strong">
            达标 {{ targetSummary.ok }}/{{ targetSummary.judged }} 条<template
              v-if="targetSummary.fail > 0"> · 未达标 {{ targetSummary.fail }}</template>
          </span>
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
              description="这些指标的分子/分母含语义存疑的原子项，值按公式照常给出，但可能有数量级偏差；其余指标不受影响。"
            />

            <div class="block-title">
              <span class="dot"></span>
              指标 = 分子 ÷ 分母 × 系数
              <el-tag size="small" type="info" effect="plain">
                共 {{ rules.length }} 条 · 已出数 {{ rulesOkCount }}
              </el-tag>
              <!--
                源端 is_show_page=0 的规则（如「ICU实际病死率」）默认也列出来：
                它们是同步进来了的，只因源端不让它上板就被藏掉，页面上跟「没同步」没区别。
              -->
              <el-checkbox
                v-model="showHiddenRules"
                size="small"
                style="margin-left: auto"
                @change="loadRules"
              >
                显示源端不上板的指标
              </el-checkbox>
            </div>

            <el-table :data="rules" stripe border size="small" max-height="620">
              <!-- 序号随当前列表顺序编号，对着清单核对条数时用 -->
              <el-table-column type="index" label="序号" width="62" align="center" />
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
                  <!--
                    is_show_page=0：源端把它设成不进看板（如「ICU实际病死率」）。
                    值照样算，只是提醒一句它不在 ICU 的看板口径内，别当成漏同步。
                  -->
                  <el-tag
                    v-if="row.isShowPage === 0"
                    size="small"
                    type="info"
                    effect="plain"
                    class="pending-tag"
                  >
                    源端不上板
                  </el-tag>
                </template>
              </el-table-column>

              <!--
                主行显示原子项**名称 + 值**，点名称看口径血缘（回答「这个率怎么来的」）。
                quality_403 这类编号不再占用列宽：看板是给护士长/主任看的，code 对他们只是噪声；
                真要排查口径，点开血缘弹窗里就有。
              -->
              <el-table-column label="分子" min-width="190">
                <template #default="{ row }">
                  <div class="atom-cell">
                    <a
                      class="atom-title"
                      :title="row.numeratorName || row.numeratorCode"
                      @click="openAtom(row.numeratorCode, { tab: 'trace', from: 'numerator' })"
                    >
                      {{ row.numeratorName || row.numeratorCode }}
                    </a>
                    <span class="atom-val">
                      {{ isBlank(row.numeratorValue) ? '—' : num(row.numeratorValue) }}
                    </span>
                    <!--
                      人工录入项的真值由人填、引擎不重算，入口必须落在这一格上：
                      只给操作列的规则级「录入」按钮，使用者看到的是整条规则，
                      并不知道缺的是分子还是分母。
                    -->
                    <el-button
                      v-if="row.numeratorImplStatus === 'MANUAL'"
                      link
                      type="primary"
                      size="small"
                      @click="openManual({ code: row.numeratorCode, name: row.numeratorName, value: row.numeratorValue })"
                    >
                      录入
                    </el-button>
                  </div>
                  <!-- 只在原子项出数异常时才占一行，正常情况不显示 -->
                  <div v-if="row.numeratorStatus && row.numeratorStatus !== 'OK'" class="atom-name">
                    <el-tag
                      size="small"
                      effect="plain"
                      :type="calcTag(row.numeratorStatus).type"
                      class="pending-tag"
                    >
                      {{ calcTag(row.numeratorStatus).label }}
                    </el-tag>
                  </div>
                </template>
              </el-table-column>

              <el-table-column label="分母" min-width="190">
                <template #default="{ row }">
                  <div class="atom-cell">
                    <a
                      class="atom-title"
                      :title="row.denominatorName || row.denominatorCode"
                      @click="openAtom(row.denominatorCode, { tab: 'trace', from: 'denominator' })"
                    >
                      {{ row.denominatorName || row.denominatorCode }}
                    </a>
                    <span class="atom-val">
                      {{ isBlank(row.denominatorValue) ? '—' : num(row.denominatorValue) }}
                    </span>
                    <!-- 同分子列：人工录入项在哪一侧，录入入口就落在哪一侧 -->
                    <el-button
                      v-if="row.denominatorImplStatus === 'MANUAL'"
                      link
                      type="primary"
                      size="small"
                      @click="openManual({ code: row.denominatorCode, name: row.denominatorName, value: row.denominatorValue })"
                    >
                      录入
                    </el-button>
                  </div>
                  <div v-if="row.denominatorStatus && row.denominatorStatus !== 'OK'" class="atom-name">
                    <el-tag
                      size="small"
                      effect="plain"
                      :type="calcTag(row.denominatorStatus).type"
                      class="pending-tag"
                    >
                      {{ calcTag(row.denominatorStatus).label }}
                    </el-tag>
                  </div>
                </template>
              </el-table-column>

              <el-table-column label="单位" width="56" align="center">
                <template #default="{ row }">{{ row.displayUnit }}</template>
              </el-table-column>

              <el-table-column label="本期值" width="100" align="right">
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
              <!--
                达标标记：结论由后端给（judge），前端只负责上色。
                方向不能在页面推断 —— 「越高越好」与「越低越好」两类指标上，
                同一个数值的结论正好相反，前端猜方向必然错一半。
              -->
              <el-table-column label="达标" width="76" align="center">
                <template #default="{ row }">
                  <el-tag v-if="judgeTag(row.judge)" :type="judgeTag(row.judge).type" size="small" effect="light">
                    {{ judgeTag(row.judge).label }}
                  </el-tag>
                  <span v-else class="empty-value">—</span>
                </template>
              </el-table-column>

              <el-table-column label="目标值" width="88" align="right">
                <template #default="{ row }">
                  <a
                    class="target-link"
                    :title="`点击配置目标值／预警值（当前方向：${row.targetDirection === 'DOWN' ? '越低越好' : '越高越好'}）`"
                    @click="openTarget(row)"
                  >
                    <span v-if="isBlank(row.targetValue)" class="empty-value">未配置</span>
                    <span v-else>{{ targetText(row) }}</span>
                  </a>
                </template>
              </el-table-column>

              <el-table-column label="预警值" width="84" align="right">
                <template #default="{ row }">
                  <a class="target-link" title="点击配置预警值" @click="openTarget(row)">
                    <span v-if="isBlank(row.warningValue)" class="empty-value">未配置</span>
                    <span v-else>{{ num(row.warningValue) }}</span>
                  </a>
                </template>
              </el-table-column>

              <el-table-column label="状态" width="94" align="center">
                <template #default="{ row }">
                  <el-tag :type="calcTag(row.calcStatus).type" size="small" effect="light">
                    {{ calcTag(row.calcStatus).label }}
                  </el-tag>
                </template>
              </el-table-column>

              <!--
                「计算」只重算这一条指标用到的分子、分母两个原子项（秒级），
                与顶部「触发计算」（127 条全跑，分钟级）是两个量级的操作。
                「配置」是本院自管的目标值 / 预警值入口，源端同步不会覆盖。
              -->
              <el-table-column label="操作" width="150" align="center" fixed="right">
                <template #default="{ row }">
                  <el-button
                    link
                    type="primary"
                    size="small"
                    :loading="ruleCalcId === row.ruleId"
                    @click="calcRule(row)"
                  >
                    计算
                  </el-button>
                  <el-button link type="primary" size="small" @click="openTarget(row)">配置</el-button>
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
                    <!--
                      点指标名 = 「这条指标本期覆盖了谁」，所以落在全量明细（含未纳入）。
                      与点数字是两个不同的问题：这里回答构成，那里回答「这 n 个人是谁」。
                    -->
                    <a
                      class="value-link"
                      @click="openMetric(row, { tab: 'patients', view: 'all' })"
                    >{{ row.name }}</a>
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
                <el-table-column label="分子 / 分母" width="142" align="right">
                  <template #default="{ row }">
                    <span v-if="isBlank(row.numerator) && isBlank(row.denominator)" class="empty-value">—</span>
                    <template v-else>
                      <!--
                        分子、分母各自可点，且带出各自的视角：点分子进来必然只看得到已达标的人，
                        点分母进来看到全部纳入统计的人。明细的行数因此与所点的那个数字对得上 ——
                        这正是旧的「进来再挑筛选」做不到的，也是它让人困惑的根源。
                      -->
                      <a
                        v-if="!isBlank(row.numerator)"
                        class="value-link"
                        @click="openMetric(row, { tab: 'patients', view: 'inNumerator' })"
                      >{{ num(row.numerator) }}</a>
                      <span v-else class="empty-value">—</span>
                      <span class="ratio-sep">/</span>
                      <a
                        v-if="!isBlank(row.denominator)"
                        class="value-link"
                        @click="openMetric(row, { tab: 'patients', view: 'inDenominator' })"
                      >{{ num(row.denominator) }}</a>
                      <span v-else class="empty-value">—</span>
                    </template>
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
              <!--
                维度列落库的是科室编码（'ALL' 表示全院）。看这条血缘的是护士长/主任，
                quality_403 这种编号是噪声，这里映射成科室名称；映射不到（多维拼接维度）保留原值，不吞信息。
              -->
              <el-table-column label="维度" width="120">
                <template #default="{ row }">
                  <span :title="row.dimKey">{{ deptName(row.dimKey) }}</span>
                </template>
              </el-table-column>
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

          <el-tab-pane :label="`患者明细（${visiblePatients.length}）`" name="patients">
            <!--
              明细生成失败的原因必须露出来：患者明细字段里配了与默认列同名的列（如 patient_id）时，
              明细 SQL 会报「列名不明确」而整份返回空。页面若只说「无明细」，
              使用者只会当成本期没数据，实际是配置写错了 —— 后端把原因放在 patientsMessage 里带到前端。
            -->
            <el-alert
              v-if="detail.patientsMessage"
              type="warning"
              :closable="false"
              show-icon
              class="qb-detail-alert"
              :title="detail.patientsMessage"
            />
            <!--
              纳入视角。四个互斥档（已达标 / 未达标 / 未纳入 / 口径异常）两两不重叠、并集为全部，
              因此「各档人数相加 = 总数」，可以直接对账 —— 旧的「仅分子 / 仅分母」做不到：
              分子通常也落在分母里，被数了两遍。
              「进分子」「进分母」是两个总览档，各自对应被点的那个数字：
              点指标名落在「全部」，点分子落在「进分子」，点分母落在「进分母」。
              所有数据在一次请求里取回，切档只是本地过滤。
            -->
            <!--
              工具条：左说明当前这一档是什么，右导出。导出原先挂在 el-tabs 的 #extra 插槽上，
              实际渲染不出来（按钮凭空消失），放在表格正上方既稳定、又紧挨着它要导出的名单。
            -->
            <!--
              只有分子入口才有筛选：分母入口进来时工具条上只剩导出，
              此时靠右排（is-export-only）—— 否则 space-between 会把唯一的按钮推到左边。
            -->
            <div class="qb-detail-toolbar" :class="{ 'is-export-only': !detailFilterable }">
              <!--
                四档筛选：全部 / 分子 / 分母 / 未达标。
                人数恒取后端 viewCounts（按未过滤的全量统计）—— 若按当前列表算，
                每切一档其他档的数字就跟着变，而这正是用来对账的数（各档相加 = 总数）。
              -->
              <el-radio-group v-if="detailFilterable" v-model="patientView" size="small">
                <el-radio-button label="all">全部（{{ viewCount('all') }}）</el-radio-button>
                <el-radio-button label="inNumerator">分子（{{ viewCount('inNumerator') }}）</el-radio-button>
                <el-radio-button label="inDenominator">分母（{{ viewCount('inDenominator') }}）</el-radio-button>
                <el-radio-button label="missed">未达标（{{ viewCount('missed') }}）</el-radio-button>
              </el-radio-group>
              <el-button size="small" :loading="patientExporting" @click="doExportPatients">
                <el-icon><Download /></el-icon>
                <span style="margin-left: 4px">导出名单</span>
              </el-button>
            </div>
            <el-table :data="visiblePatients" border size="small" max-height="420">
              <!-- 序号：名单常被逐行指认（「第 3 行这个人对不上」），没有序号只能靠数 -->
              <el-table-column type="index" label="序号" width="58" align="center" />
              <!--
                整表出列统一由接口的 patientFields 驱动：默认列（姓名/住院号/入分子/入分母…）
                与指标自配的补充列共用同一个有序清单 —— 只有这样「调到前面/不要这列」才能统一生效。
                类型也由后端给（bool 渲染成是/否、depart 映射科室名），前端不靠猜。
              -->
              <el-table-column
                v-for="f in detail.patientFields || []"
                :key="f.key"
                :prop="f.key"
                :label="f.label"
                :width="f.width || 120"
                :align="f.type === 'bool' || f.type === 'time' ? 'center' : undefined"
                show-overflow-tooltip
              >
                <template v-if="f.type === 'bool'" #default="{ row }">
                  <el-tag :type="row[f.key] === 1 ? 'success' : 'info'" size="small" effect="plain">
                    {{ row[f.key] === 1 ? '是' : '否' }}
                  </el-tag>
                </template>
                <template v-else-if="f.type === 'depart'" #default="{ row }">
                  {{ deptName(row[f.key]) }}
                </template>
                <template v-else #default="{ row }">
                  {{ formatCellValue(row[f.key]) }}
                </template>
              </el-table-column>
            </el-table>
            <el-empty
              v-if="visiblePatients.length === 0"
              :description="patientsEmptyHint"
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
      <!--
        录入依据：这个数从哪来（科室报送台账、纸质报表…）。质控上报被追问时
        「为什么是这个数」是必答题，而录入人 / 时间由服务端自动留痕。
      -->
      <div class="manual-row">
        <span class="manual-label">录入依据</span>
        <el-input
          v-model="manualForm.note"
          maxlength="200"
          show-word-limit
          placeholder="如：2026-08 科室质控报表（可留空）"
          style="width: 260px"
        />
      </div>
      <el-alert
        type="info"
        show-icon
        :closable="false"
        style="margin-top: 8px"
        title="人工录入的值不会被重算覆盖"
        description="录入后该值标记为人工来源，后续无论整批重算还是单指标重算都会跳过它；录入人与时间会自动记入结果表，供上报追溯。"
      />
      <template #footer>
        <el-button @click="manualVisible = false">取消</el-button>
        <el-button type="primary" :loading="manualSaving" @click="doManual">保存</el-button>
      </template>
    </el-dialog>

    <!--
      目标值 / 预警值配置：这两列是本院自管字段（ICU 侧原表有此四列但全 NULL），
      质控最关心的「达标与否」必须有个地方存。留空即清空。
    -->
    <el-dialog v-model="targetVisible" title="配置目标值 / 预警值" width="440px">
      <template v-if="targetRow">
        <div class="manual-row">
          <span class="manual-label">指标</span>
          <span>{{ targetRow.countName }}</span>
        </div>
        <div class="manual-row">
          <span class="manual-label">单位</span>
          <span>{{ targetRow.displayUnit || '—' }}</span>
        </div>
        <div class="manual-row">
          <span class="manual-label">本期值</span>
          <span>{{ isBlank(targetRow.value) ? '—' : num(targetRow.value) }}</span>
        </div>
        <div class="manual-row">
          <span class="manual-label">达标方向</span>
          <el-radio-group v-model="targetForm.targetDirection" size="small">
            <el-radio-button label="UP">越高越好</el-radio-button>
            <el-radio-button label="DOWN">越低越好</el-radio-button>
          </el-radio-group>
        </div>
        <div class="manual-row">
          <span class="manual-label">目标值</span>
          <el-input-number
            v-model="targetForm.targetValue"
            :precision="4"
            :controls="false"
            placeholder="留空为清空"
            style="width: 180px"
          />
        </div>
        <div class="manual-row">
          <span class="manual-label">预警值</span>
          <el-input-number
            v-model="targetForm.warningValue"
            :precision="4"
            :controls="false"
            placeholder="留空为清空"
            style="width: 180px"
          />
        </div>
        <el-alert
          type="info"
          show-icon
          :closable="false"
          style="margin-top: 10px"
          title="本院自管配置"
          description="只改达标方向 / 目标值 / 预警值，不动口径与计算方式；源端同步也不会覆盖它们。方向必须与实际业务一致：依从率、完成率、送检率类选「越高越好」；发病率、病死率、重返率类选「越低越好」。方向选反会把不达标显示成达标，比不判定更危险。"
        />
      </template>
      <template #footer>
        <el-button @click="targetVisible = false">取消</el-button>
        <el-button type="primary" :loading="targetSaving" @click="doSaveTarget">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Cpu, Upload, Download, Grid, CircleCheck, WarningFilled, Hide } from '@element-plus/icons-vue'
import { externalParam } from '../utils/external'
import '../styles/quality-theme.css'
import {
  fetchQualityOverview,
  fetchQualityRules,
  syncQualityRules,
  saveQualityRuleTarget,
  calcQualityRule,
  fetchQualityMetric,
  exportQualityMetricPatients,
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

/** 计算失败的指标名（最多点 6 条，其余只报数量），供顶部提示条直接点名。 */
const errorMetricNames = computed(() => {
  const names = rules.value
    .filter((r) => r.calcStatus === 'ERROR')
    .map((r) => r.countName || r.ruleId)
  return names.slice(0, 6).join('、') + (names.length > 6 ? ` 等 ${names.length} 条` : '')
})

/**
 * 达标汇总。
 *
 * 分母是「配了阈值且本期出数」的指标，不是全部指标 —— 未配阈值的指标本就无从判定，
 * 算进去会得出一个看着很低、实则虚的达标率，反而误导判断。
 */
const targetSummary = computed(() => {
  const judged = rules.value.filter((r) => r.judge && r.judge !== 'NONE')
  return {
    judged: judged.length,
    ok: judged.filter((r) => r.judge === 'OK').length,
    warn: judged.filter((r) => r.judge === 'WARN').length,
    fail: judged.filter((r) => r.judge === 'FAIL').length
  }
})

/** 达标标记的呈现属性；未判定返回 null（页面显示「—」而不是硬套一个颜色）。 */
function judgeTag(judge) {
  if (judge === 'OK') return { label: '达标', type: 'success' }
  if (judge === 'WARN') return { label: '预警', type: 'warning' }
  if (judge === 'FAIL') return { label: '未达标', type: 'danger' }
  return null
}

/** 目标值带方向符号展示：「≥90」与「≤5」不会读反。 */
function targetText(row) {
  if (isBlank(row.targetValue)) return ''
  return (row.targetDirection === 'DOWN' ? '≤' : '≥') + num(row.targetValue)
}

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
  // 计算异常单独计数。此前它被并进「未出数（含空壳）」，与「待接源表」在这张卡片上
  // 完全同形 —— 源表改结构导致指标连续几个月算不出来，从总览上根本看不出来。
  const error = rules.value.filter((r) => r.calcStatus === 'ERROR').length
  const pending = total - ok - noData - error
  const pct = (n) => (total ? ((Number(n) / total) * 100).toFixed(1) : '0.0')
  return {
    total,
    ok,
    noData,
    error,
    pending,
    okRate: pct(ok),
    noDataRate: pct(noData),
    errorRate: pct(error),
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

/**
 * 是否列出源端 is_show_page=0 的规则（如「ICU实际病死率」「感染性休克1h集束化治疗完成率」）。
 *
 * 默认 true：这类规则其实已经同步进库，只是源端不让它上板。以前一律过滤掉，
 * 页面表现跟「压根没同步过来」完全一样 —— 看着像同步漏了指标，实则只是没显示。
 * 现在默认列出并打「源端不上板」标签，想回到只看上板的视图，勾掉这个开关即可。
 */
const showHiddenRules = ref(true)

const rulesOkCount = computed(() => rules.value.filter((r) => r.calcStatus === 'OK').length)
/** 口径待确认的条数（后端目前无待确认项；保留以免将来新增存疑项时页面漏提示） */
const rulesPendingCount = computed(() => rules.value.filter((r) => r.pendingConfirm).length)

async function loadRules() {
  rulesLoading.value = true
  try {
    const res = await fetchQualityRules({
      periodType: periodType.value,
      periodStart: periodStart.value,
      departCode: departCode.value || '',
      includeHidden: showHiddenRules.value
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
    const res = (await syncQualityRules()) || {}
    const added = res.added != null ? res.added : 0
    const updated = res.updated != null ? res.updated : 0
    const hidden = res.hiddenCount != null ? res.hiddenCount : 0
    // 明确说出「不上板」那几条：它们同步进来了，只是不进看板，
    // 不说清楚就会被当成同步漏了指标。
    const tail =
      hidden > 0 ? `；其中 ${hidden} 条源端设为不上板（如 ICU 实际病死率），已在列表中标注` : ''
    ElMessage.success(
      added > 0 || updated > 0
        ? `同步完成：新增 ${added} 条，刷新 ${updated} 条${tail}`
        : `指标规则已是最新（本地共 ${res.localTotal != null ? res.localTotal : rules.value.length} 条）${tail}`
    )
    await loadRules()
  } catch (e) {
    ElMessage.error(
      '同步指标规则失败：' + (e.message || e) + '（确认已执行 sql/11_quality_count_rule.sql 且库账号可跨 schema 读 ICU）'
    )
  } finally {
    rulesSyncing.value = false
  }
}

/**
 * 打开分子 / 分母原子项的口径血缘 —— 回答「这个率是怎么来的」。
 *
 * @param opts.view 入口视角：分子原子项 vs 分母原子项必须区分开，
 *                  否则点分母原子项名称进去，明细里也会错误地出现筛选器。
 */
function openAtom(code, opts) {
  if (!code) return
  // opts 必须作为第二个参数透传给 openMetric：此前误写成 { code, ...(opts || {}) }，
  // view 被并进 row 导致 openMetric 拿不到入口视角，分母入口的四档筛选隐藏逻辑永远不生效。
  //
  // 原子项没有真正的「分母」：它的值就是满足条件的人数，点名称进来统一按 inNumerator
  // 看「满足条件的人」，否则按 inDenominator 会把整个事实层的人都列出来。
  // 是否给四档筛选由调用处的 from 字段决定：分子列点进来给，分母列点进来不给。
  openMetric({ code }, { ...(opts || {}), view: 'inNumerator', isAtom: true })
}

// ---------------- 单指标计算 ----------------
/** 正在计算的规则：只让这一行的按钮转圈，不整表 loading */
const ruleCalcId = ref('')

/**
 * 计算单条指标：重算它依赖的分子、分母两个原子项，再除出指标值。
 *
 * 与顶部「触发计算」不是一个量级：那条跑全部 127 条（分钟级），
 * 这条只跑两个原子项（秒级）。算完用后端回吐的行就地替换 ——
 * 整表重拉既慢，还会把滚动位置和刚看的那行顶走。
 */
async function calcRule(row) {
  if (!row || !row.ruleId) return
  ruleCalcId.value = row.ruleId
  try {
    const res = await calcQualityRule({
      ruleId: row.ruleId,
      periodType: periodType.value,
      periodStart: periodStart.value,
      departCode: departCode.value || ''
    })
    replaceRow(row.ruleId, res)
    const steps = (res && res.steps) || {}
    const msg = [stepText(steps.numerator), stepText(steps.denominator)].filter(Boolean).join('；')
    if (isBlank(res && res.value)) {
      ElMessage.warning(`「${row.countName}」未算出值 —— ${msg || '请查看分子/分母状态'}`)
    } else {
      ElMessage.success(`「${row.countName}」= ${num(res.value)}${res.displayUnit || ''}（${msg}）`)
    }
  } catch (e) {
    ElMessage.error('计算失败：' + (e.message || e))
  } finally {
    ruleCalcId.value = ''
  }
}

/** 把「分子算成功、分母被跳过」这种半成功说明白，否则用户只看到「—」无从下手。 */
function stepText(s) {
  if (!s) return ''
  const name = s.name || s.code || '原子项'
  if (s.busy) return `${name}：计算正忙，稍后重试`
  if (s.calcStatus === 'ERROR') return `${name}：${s.reason || '计算异常'}`
  if (s.done) return `${name}：已重算`
  return `${name}：${s.reason || '未重算'}`
}

// ---------------- 目标值 / 预警值（本院自管） ----------------
const targetVisible = ref(false)
const targetSaving = ref(false)
const targetRow = ref(null)
const targetForm = reactive({ targetValue: null, warningValue: null, targetDirection: 'UP' })

function openTarget(row) {
  if (!row || !row.ruleId) return
  targetRow.value = row
  targetForm.targetValue = isBlank(row.targetValue) ? null : Number(row.targetValue)
  targetForm.warningValue = isBlank(row.warningValue) ? null : Number(row.warningValue)
  // 未配过的按 UP 预选：多数质控指标是「越高越好」，默认值只是省一次点击，
  // 保存时使用者仍能看清当前选的是哪个方向
  targetForm.targetDirection = row.targetDirection === 'DOWN' ? 'DOWN' : 'UP'
  targetVisible.value = true
}

/**
 * 保存目标值 / 预警值。
 *
 * 传 null（输入框清空）时 axios 会省略该参数，后端按 null 处理即清空。
 * 只写这两列：源端同步不覆盖、也不动口径与计算方式。
 */
async function doSaveTarget() {
  const row = targetRow.value
  if (!row) return
  targetSaving.value = true
  try {
    const res = await saveQualityRuleTarget({
      ruleId: row.ruleId,
      targetValue: targetForm.targetValue,
      warningValue: targetForm.warningValue,
      targetDirection: targetForm.targetDirection,
      periodType: periodType.value,
      periodStart: periodStart.value,
      departCode: departCode.value || ''
    })
    replaceRow(row.ruleId, res)
    ElMessage.success(`已保存「${row.countName}」的目标值 / 预警值`)
    targetVisible.value = false
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || e))
  } finally {
    targetSaving.value = false
  }
}

/** 用后端回吐的那一行就地替换，避免整表重拉。 */
function replaceRow(ruleId, row) {
  if (!row) return
  const i = rules.value.findIndex((r) => r.ruleId === ruleId)
  if (i >= 0) {
    rules.value.splice(i, 1, row)
  }
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

/**
 * 患者明细的纳入视角。
 *
 * 入口只决定「默认落在哪一档」，进来之后仍可在工具条上切这四档：
 *   全部   → all            本期该事实层扫到的所有人
 *   分子   → inNumerator    已达标 + 口径异常，行数必然等于分子数
 *   分母   → inDenominator  已达标 + 未达标
 *   未达标 → missed         进分母却没进分子，即「该做未做」的那批人
 *
 * 反过来讲：凡从数字进入的，列表行数必须与那个数字对得上 ——
 * 对不上就说明入口没把话说完。
 *
 * 四个互斥档（achieved / missed / excluded / abnormal）两两不重叠、并集为全部，
 * 因此各档人数可以直接相加对账。
 */
const patientView = ref('all')
const patientExporting = ref(false)

/**
 * 本次是不是从「分子」进来的 —— 决定明细里给不给四档筛选。
 *
 * 分母入口的口径本身就是「纳入统计的都有哪些人」，是一个已经说清楚的集合，
 * 再摆一排筛选反而让人怀疑当前看的究竟是哪一档，所以分母入口维持原样（只给导出）。
 *
 * 用入口判断，而不是用 patientView 判断：后者会让用户从「分子」档切到「分母」档时
 * 筛选器当场消失、再也切不回来 —— 那是把一次正常操作变成死路。
 */
const detailFilterable = ref(false)

/**
 * 与后端 inclusionState 同源的判定，作兜底用。
 *
 * 后端已算好随行返回，这里再留一份是为了兼容旧响应（页面缓存、未升级的后端）：
 * 缺了它，所有页签人数会变成 0，比不显示更误导。
 */
function stateOf(row) {
  const n = row.inNumerator === 1
  const d = row.inDenominator === 1
  if (n && d) return 'achieved'
  if (!n && d) return 'missed'
  if (!n && !d) return 'excluded'
  // 进了分子却没进分母：分子与分母是两个独立条件，这种组合客观存在，
  // 通常意味着 YAML 里条件写反了；单列一档，不并进「已达标」蒙混过去
  return 'abnormal'
}

/**
 * 各档人数，供工具条上的四个选项显示。
 *
 * 优先用后端 viewCounts —— 它统计的是「未过滤的全量」；前端这份只在后端没给时兜底
 * （页面缓存、未升级的后端）。差别在于：若按当前列表算，一切档其他档的数字就跟着
 * 列表变了，而这个数字正是拿来对账的，一变就废。
 */
const viewCounts = computed(() => {
  const fromServer = detail.viewCounts
  if (fromServer) return fromServer
  const list = Array.isArray(detail.patients) ? detail.patients : []
  const c = {
    all: list.length,
    inNumerator: 0,
    inDenominator: 0,
    achieved: 0,
    missed: 0,
    excluded: 0,
    abnormal: 0
  }
  for (const row of list) {
    const s = stateOf(row)
    if (s === 'achieved' || s === 'missed') c.inDenominator++
    if (s === 'achieved' || s === 'abnormal') c.inNumerator++
    c[s]++
  }
  return c
})

/** 取某一档人数；拿不到时给 0，绝不让页面上出现 undefined。 */
function viewCount(k) {
  const v = viewCounts.value
  return v && v[k] != null ? v[k] : 0
}

const visiblePatients = computed(() => {
  const list = Array.isArray(detail.patients) ? detail.patients : []
  const v = patientView.value
  if (v === 'all') return list
  if (v === 'inDenominator') {
    return list.filter((r) => {
      const s = stateOf(r)
      return s === 'achieved' || s === 'missed'
    })
  }
  // 进分子 = 已达标 + 口径异常：漏掉 abnormal 这一半，点分子进来的行数就与分子值对不上
  if (v === 'inNumerator') {
    return list.filter((r) => {
      const s = stateOf(r)
      return s === 'achieved' || s === 'abnormal'
    })
  }
  return list.filter((r) => stateOf(r) === v)
})

/**
 * 导出患者名单。
 *
 * 导出跟随当前视角：屏幕上是「未达标 8 人」，导出的就应该是同样这 8 人。
 * 过滤统一交给后端的 view 参数做 —— 前端筛一遍、后端再筛一遍，
 * 两份判定迟早会漂移，而症状是「导出的人与屏幕上看到的不一致」，极难解释。
 */
// 导出刻意走裸 axios + responseType:'blob'（成功时是二进制流，不能过统一解包），
// 代价是后端失败时返回的 JSON 错误体也变成了 Blob：不拦一道就照常触发下载，
// 用户会拿到一个 .xlsx 后缀、内容却是 {"code":500,...} 的文件，Excel 只报「格式损坏」，
// 真正的失败原因被封在文件里看不见，而页面已经弹过「导出成功」。
function isExportErrorPayload(resp) {
  const d = resp && resp.data
  if (!d) return true
  const blobType = typeof d === 'string' ? '' : String(d.type || '')
  if (blobType.indexOf('json') >= 0) return true
  const ct = String((resp.headers && resp.headers['content-type']) || '')
  return ct.indexOf('json') >= 0
}

// 从错误体里取可读原因；取不到也要给一句能指导下一步动作的话，而不是 undefined
async function exportErrorText(resp) {
  const d = resp.data
  try {
    const text = typeof d === 'string' ? d : await d.text()
    const o = JSON.parse(text)
    return (o && (o.message || o.msg)) || text
  } catch (e) {
    return '服务端未返回文件内容，导出未完成'
  }
}

async function doExportPatients() {
  if (!detail.code) return
  patientExporting.value = true
  try {
    const resp = await exportQualityMetricPatients(detail.code, {
      periodStart: periodStart.value,
      departCode: departCode.value || '',
      // includeExcluded 恒为 true：明细已一次取全，视角统一由 view 表达。
      // 再留一个开关，只会让「导出的行」与「屏幕的行」出现第二种解释。
      includeExcluded: true,
      view: patientView.value
    })
    if (isExportErrorPayload(resp)) {
      ElMessage.error('导出失败：' + (await exportErrorText(resp)))
      return
    }
    const blob = new Blob([resp.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `质控患者明细_${detail.code}_${periodStart.value || ''}.xlsx`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    setTimeout(() => URL.revokeObjectURL(url), 60000)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error('导出失败：' + (e.message || e))
  } finally {
    patientExporting.value = false
  }
}

/** 空表提示要区分「没人」「生成失败」「没查到」——三者的下一步动作完全不同。 */
/**
 * 单元格值兜底格式化。
 *
 * 时间值后端已统一转成「yyyy-MM-dd HH:mm:ss」，这里只兜两类漏网：
 * 时间列被配成默认列，或某条指标的事实层返回了非标准类型（带 T 的 ISO 串 / 毫秒时间戳）。
 * 数字时间戳只在合理区间内才认 —— 否则住院号这类大编号会被误读成时间。
 * 空值统一显示破折号，避免满屏空白分不清「没值」和「没查出来」。
 */
function formatCellValue(v) {
  if (v === null || v === undefined || v === '') return '—'
  if (typeof v === 'string') {
    const m = v.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})(:\d{2})?/)
    return m ? `${m[1]} ${m[2]}${m[3] || ''}` : v
  }
  if (typeof v === 'number' && v > 1000000000000 && v < 4000000000000) {
    const d = new Date(v)
    if (!Number.isNaN(d.getTime())) {
      const p = (n) => String(n).padStart(2, '0')
      return (
        `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ` +
        `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
      )
    }
  }
  return v
}

const patientsEmptyHint = computed(() => {
  if (detail.patientsMessage) return '明细生成失败，原因见上方提示'
  const v = patientView.value
  if (v === 'inNumerator') return '本档没有患者：本期没有人命中分子条件'
  if (v === 'achieved') return '本档没有患者：本期进分子的为 0'
  if (v === 'missed') return '本档没有患者：纳入统计的人全部进了分子（没有该做未做的）'
  if (v === 'inDenominator') return '本档没有患者：本期没有人被纳入统计（分母为 0）'
  if (v === 'excluded') return '没有未纳入的患者：本期该事实层的人全部已计入分子或分母'
  if (v === 'abnormal') return '没有口径异常的患者：分子与分母条件未见冲突'
  return '无患者级命中明细（下钻按需生成，指标未出数时为空）'
})

/**
 * 打开指标详情抽屉。
 *
 * @param opts.tab  初始页签：'trace' 口径血缘（点本期值 / 口径血缘）/ 'patients' 患者明细
 * @param opts.view 初始纳入视角，由入口决定：
 *                  点分子数字 → inNumerator，点分母数字 → inDenominator，
 *                  点指标名称 → all（看板显式传 'all'，回答「这条指标覆盖了谁」），
 *                  点本期值 / 口径血缘 → 不传，落 inNumerator（点进来就想看「这 N 个人是谁」）。
 *                  只有落在 inNumerator 的入口才带四档筛选，见 detailFilterable。
 */
async function openMetric(row, opts) {
  const opt = opts || {}
  detailVisible.value = true
  detailLoading.value = true
  detailTab.value = opt.tab || 'trace'
  // 视角跟着本次入口走，不复用上一次的选择：留档会出现「点了分子却落在未纳入」
  // 这种入口与内容对不上的情况，而那正是这次要根治的问题。
  // 默认进分子：指标卡片上的大数字通常就是分子，进来先让用户对得上这个数。
  const entryView = opt.view || 'inNumerator'
  patientView.value = entryView
  // 是否给四档筛选：
  // - 规则入口：分子视角（inNumerator）给，分母视角（inDenominator）不给；
  // - 原子项入口：分子列点进来给，分母列点进来不给。
  //   原子项统一按 inNumerator 看「满足条件的人」，因为它的值就是这个数。
  detailFilterable.value = opt.isAtom
      ? opt.from === 'numerator'
      : entryView === 'inNumerator'
  // 先清空，避免快速连点时短暂显示上一条指标的数据
  Object.keys(detail).forEach((k) => delete detail[k])
  try {
    const res = await fetchQualityMetric(row.code, {
      periodStart: periodStart.value,
      departCode: departCode.value || ''
    })
    Object.assign(detail, res || {})
    // 表格只吃数组：后端若把信封对象（如 { patients: [...] }）整包塞回来，
    // el-table 迭代对象会抛 "is not iterable" 并把整页打挂，这里兜一层。
    detail.traces = Array.isArray(detail.traces) ? detail.traces : []
    const p = detail.patients
    detail.patients = Array.isArray(p) ? p : Array.isArray(p?.patients) ? p.patients : []
    detail.patientFields = Array.isArray(detail.patientFields) ? detail.patientFields : []
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
      `将重算 ${fmtPeriod(overview.periodStart || periodStart.value)} ~ ${fmtPeriodEnd(overview.periodEnd)} 的指标结果`
        + `${departCode.value ? `（仅科室「${deptName(departCode.value)}」）` : '（全院 + 各科室）'}：`
        + `同周期同科室幂等覆盖${departCode.value ? '，全院汇总行不受本次计算影响（它由全院计算更新）' : ''}，是否继续？`,
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
const manualForm = reactive({ code: '', name: '', value: null, note: '' })

function openManual(row) {
  manualForm.code = row.code
  manualForm.name = row.name
  manualForm.value = row.value === null || row.value === undefined ? null : Number(row.value)
  // 备注每次从空开始：沿用上一条的备注会被误读成本次录入依据
  manualForm.note = ''
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
      note: manualForm.note || '',
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

/* 明细生成失败的原因提示：要显眼但不刺眼 —— 它多半是配置写错了，不是系统故障 */
.qb-detail-alert {
  margin-bottom: 10px;
}

/* 明细工具条：左侧四档筛选、右侧导出；窄屏换行，避免两者互相挤没 */
.qb-detail-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

/* 没有筛选器时（分母入口）只剩导出：靠右，与筛选上线之前的观感一致 */
.qb-detail-toolbar.is-export-only {
  justify-content: flex-end;
}

/* 分子 / 分母之间的斜杠：两侧数字各自可点，靠它拉开间距免得挤成一团 */
.ratio-sep {
  margin: 0 4px;
  color: #a8a29e;
}

/* 计算异常提示条：常驻在总览最上方，异常未清零前一直可见 */
.qb-error-bar {
  margin-bottom: 12px;
}

.qb-error-hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.6;
  opacity: 0.85;
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

/* 主行显示原子项名称（可点开血缘），code 退到次行 */
.atom-title {
  font-size: 13px;
  color: #292524;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.atom-title:hover {
  color: #ea580c;
}

/* 目标值 / 预警值：点单元格即可配置，比去操作列找「配置」更直觉 */
.target-link {
  display: inline-block;
  width: 100%;
  cursor: pointer;
  border-bottom: 1px dashed #d6d3d1;
}

.target-link:hover {
  border-bottom-color: #ea580c;
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
