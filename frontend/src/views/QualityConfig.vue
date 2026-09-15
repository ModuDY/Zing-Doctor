<template>
  <div class="qc qb-theme">
    <div class="head">
      <div class="title">
        <h2>质控指标配置</h2>
        <p class="sub">改口径不改代码：保存后热生效，每次保存留快照可回滚</p>
      </div>
      <div class="filters">
        <button class="btn" @click="reloadAll">刷新</button>
        <button class="btn" :disabled="busy" @click="doReload">重载配置</button>
      </div>
    </div>

    <!--
      真源 + 写权限提示：先说清楚「能不能改、为什么不能改」，而不是等点了保存才拒绝。
      两种只读原因要分开说：真源没切到库、和「有库但当前没写权限」，处理方式完全不同。
    -->
    <div class="banner" :class="bannerClass">
      <span class="dot"></span>
      <span>{{ bannerText }}</span>
    </div>

    <div class="tabs">
      <button v-for="t in tabs" :key="t.key" class="tab"
              :class="{ active: tab === t.key }" @click="tab = t.key">
        {{ t.label }}<em v-if="t.count != null">{{ t.count }}</em>
      </button>
    </div>

    <!-- ==================== 指标配置 ==================== -->
    <div v-show="tab === 'metric'" class="panel">
      <div class="toolbar">
        <select class="tb-input" v-model="filterDomain">
          <option value="">全部域</option>
          <option v-for="d in domains" :key="d" :value="d">{{ d }}</option>
        </select>
        <select class="tb-input" v-model="filterStatus">
          <option value="">全部状态</option>
          <option value="IMPL">已实现</option>
          <option value="PLACEHOLDER">空壳（待补口径）</option>
          <option value="PENDING_SOURCE">待接数据源</option>
          <option value="MANUAL">人工录入</option>
        </select>
        <input class="tb-input search" v-model.trim="keyword"
               placeholder="搜索编号 / 名称 / 事实层" />
        <button class="btn" @click="doExport">导出配置</button>
        <button class="btn" :disabled="!canEdit" @click="openImport">导入配置</button>
        <span class="count">共 {{ filteredMetrics.length }} 条</span>
      </div>

      <table class="tbl">
        <thead>
          <tr>
            <th style="width:110px">编号</th>
            <th>指标名称</th>
            <th style="width:130px">所属域</th>
            <th style="width:100px">事实层</th>
            <th style="width:70px">单位</th>
            <th style="width:90px">计算方式</th>
            <th style="width:90px">状态</th>
            <th style="width:150px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in filteredMetrics" :key="m.indexCode" :class="{ off: m.status !== 1 }">
            <td><code>{{ m.indexCode }}</code></td>
            <td>{{ m.indexName }}</td>
            <td>{{ m.domainCode || '—' }}</td>
            <td><code class="small">{{ m.factName || '—' }}</code></td>
            <td>{{ m.unit || '—' }}</td>
            <td>{{ valueTypeLabel(m.valueType, m.agg) }}</td>
            <td>
              <span :class="['tag', m.status === 1 ? 'on' : 'off']">
                {{ m.status === 1 ? '启用' : '停用' }}
              </span>
            </td>
            <td>
              <button class="btn btn-text" :disabled="!canEdit" @click="openMetric(m.indexCode)">编辑</button>
              <button class="btn btn-text" :disabled="!canEdit" @click="openHistory(m.indexCode)">历史</button>
              <button v-if="m.status === 1" class="btn btn-text danger" :disabled="!canEdit"
                      @click="doDisable(m)">停用</button>
            </td>
          </tr>
          <tr v-if="!filteredMetrics.length">
            <td colspan="8" class="empty">没有匹配的指标</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- ==================== 事实层 ==================== -->
    <div v-show="tab === 'fact'" class="panel">
      <div class="tip">
        事实层是「原始表 → 可算列」的加工声明，多个指标共享同一事实层。
        <b>改它之前先看影响面</b>：一个域几十条指标可能都挂在这一层上。
      </div>
      <table class="tbl">
        <thead>
          <tr>
            <th style="width:170px">事实层</th>
            <th style="width:120px">所属域</th>
            <th style="width:150px">来源</th>
            <th style="width:90px">状态</th>
            <th style="width:80px">投影列</th>
            <th>说明</th>
            <th style="width:130px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="f in facts" :key="f.fact">
            <td><code>{{ f.fact }}</code></td>
            <td>{{ f.domain || '—' }}</td>
            <td><code class="small">{{ f.source }}</code></td>
            <td><span class="tag" :class="f.status === 'ACTIVE' ? 'on' : 'pend'">{{ f.status }}</span></td>
            <td>{{ (f.select || []).length + (f.derive || []).length }}</td>
            <td class="remark">{{ f.note || '—' }}</td>
            <td>
              <button class="btn btn-text" @click="openFact(f.fact)">查看</button>
              <button class="btn btn-text" :disabled="!canEdit" @click="editFact(f.fact)">编辑</button>
            </td>
          </tr>
          <tr v-if="!facts.length">
            <td colspan="7" class="empty">暂无事实层</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- ==================== 变更历史 ==================== -->
    <div v-show="tab === 'history'" class="panel">
      <div class="toolbar">
        <select class="tb-input" v-model="historyType">
          <option value="">全部类型</option>
          <option value="METRIC">指标</option>
          <option value="FACT">事实层</option>
        </select>
        <input class="tb-input search" v-model.trim="historyKey" placeholder="编号 / 事实层名（留空看全部）" />
        <button class="btn" @click="loadHistory">查询</button>
      </div>
      <table class="tbl">
        <thead>
          <tr>
            <th style="width:80px">类型</th>
            <th style="width:180px">对象</th>
            <th style="width:80px">版本</th>
            <th style="width:100px">变更</th>
            <th style="width:120px">操作人</th>
            <th style="width:170px">时间</th>
            <th style="width:200px">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="h in historyList" :key="h.id">
            <td>{{ h.defType === 'METRIC' ? '指标' : '事实层' }}</td>
            <td><code class="small">{{ h.defKey }}</code></td>
            <td>v{{ h.exprVersion }}</td>
            <td><span class="tag">{{ h.changeType }}</span></td>
            <td>{{ h.operator || '—' }}</td>
            <td>{{ h.createTime }}</td>
            <td>
              <button class="btn btn-text" @click="showSnapshot(h)">看快照</button>
              <button class="btn btn-text danger" :disabled="!canEdit" @click="doRollback(h)">回滚到此版</button>
            </td>
          </tr>
          <tr v-if="!historyList.length">
            <td colspan="7" class="empty">暂无变更记录</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- ==================== 指标编辑弹窗 ==================== -->
    <div class="modal-mask" v-if="metricDialog" @click.self="metricDialog = false">
      <div class="modal wide">
        <div class="modal-head">
          <h3>{{ metricForm.isNew ? '新增指标' : '编辑指标 · ' + metricForm.code }}</h3>
          <button class="modal-close" @click="metricDialog = false">×</button>
        </div>

        <div class="modal-body">
          <!-- 基本信息 -->
          <div class="grid">
            <div class="field">
              <label>指标编号 <span class="req">*</span></label>
              <input class="tb-input full" v-model.trim="metricForm.code" :disabled="!metricForm.isNew"
                     placeholder="quality_xxx" />
            </div>
            <div class="field">
              <label>指标名称 <span class="req">*</span></label>
              <input class="tb-input full" v-model.trim="metricForm.name" />
            </div>
            <div class="field">
              <label>所属域</label>
              <input class="tb-input full" v-model.trim="metricForm.domain" placeholder="如 抗菌药物 / ICU 质量" />
            </div>
            <div class="field">
              <label>单位</label>
              <input class="tb-input full" v-model.trim="metricForm.unit" placeholder="% / 例 / 天 / 人" />
            </div>
            <div class="field">
              <label>绑定事实层 <span class="req">*</span></label>
              <select class="tb-input full" v-model="metricForm.fact" @change="onFactChange">
                <option value="">（空壳指标，暂不绑定）</option>
                <option v-for="f in facts" :key="f.fact" :value="f.fact">{{ f.fact }}</option>
              </select>
            </div>
            <div class="field">
              <label>实现状态</label>
              <select class="tb-input full" v-model="metricForm.implStatus">
                <option value="IMPL">已实现</option>
                <option value="PLACEHOLDER">空壳（口径未定，看板占位）</option>
                <option value="PENDING_SOURCE">待接数据源</option>
                <option value="MANUAL">人工录入</option>
              </select>
            </div>
          </div>

          <div class="mode-bar">
            <div class="modes">
              <button class="mode" :class="{ active: mode === 'simple' }" @click="switchMode('simple')">简单模式</button>
              <button class="mode" :class="{ active: mode === 'advanced' }" @click="switchMode('advanced')">高级模式</button>
            </div>
            <p class="mode-hint">
              <template v-if="mode === 'simple'">
                只做「选列 + 选运算符 + 填值」，不会写 SQL 也能改口径。
              </template>
              <template v-else>
                直接写表达式，支持函数与括号。保存前仍会做安全校验与试算。
              </template>
            </p>
          </div>

          <!-- 计算方式 -->
          <div class="grid">
            <div class="field">
              <label>计数方式 <span class="req">*</span></label>
              <select class="tb-input full" v-model="metricForm.agg">
                <option value="PT_COUNT">去重患者数（人数 / 例数）</option>
                <option value="SUM">求和（床日 / 天数合计）</option>
                <option value="AVG">均值（平均天数）</option>
              </select>
            </div>
            <div class="field">
              <label>值类型</label>
              <select class="tb-input full" v-model="metricForm.valueType">
                <option value="COUNT">COUNT 数</option>
                <option value="RATE">RATE 率</option>
                <option value="SUM">SUM 求和</option>
                <option value="AVG">AVG 均值</option>
              </select>
            </div>
            <div class="field">
              <label>放大系数</label>
              <input class="tb-input full" type="number" v-model.number="metricForm.scale" placeholder="率类默认 100" />
            </div>
            <div class="field">
              <label>排序号</label>
              <input class="tb-input full" type="number" v-model.number="metricForm.sortNo" />
            </div>
          </div>

          <!-- ---------- 简单模式 ---------- -->
          <template v-if="mode === 'simple'">
            <div class="sect">
              <div class="sect-head">
                <h4>{{ isSumOrAvg ? '① 统计范围（筛选出要算的记录）' : '① 分子筛选条件' }}</h4>
                <div class="sect-acts">
                  <button class="btn btn-text" @click="addCond(simple.whereRows)">+ 加一行</button>
                  <button class="btn btn-text" @click="addGroup(simple.whereRows)">+ 加一组（或）</button>
                </div>
              </div>
              <p class="sect-hint">
                <b>同一组</b>内的多行是「并且」，<b>不同组</b>之间是「或者」。
                例：「A 并且 B」或者「C」= 第 1 组填 A、B，第 2 组填 C。不设置则全部记录都算作分子。
              </p>
              <div class="cond" v-for="(r, i) in simple.whereRows" :key="'w' + i">
                <select class="tb-input grp" v-model.number="r.group" title="同组内是「并且」，不同组之间是「或者」">
                  <option v-for="g in groupOptions(simple.whereRows, r.group)" :key="g" :value="g">组{{ g }}</option>
                </select>
                <select class="tb-input field-sel" v-model="r.field">
                  <option value="">选择字段…</option>
                  <option v-for="c in fieldOptions" :key="c" :value="c">{{ c }}</option>
                </select>
                <select class="tb-input op" v-model="r.op">
                  <option v-for="o in OPS" :key="o" :value="o">{{ o }}</option>
                </select>
                <input class="tb-input grow" v-model.trim="r.value" placeholder="值，如 1 / 28 / 'ICU'" />
                <button class="btn btn-text danger" @click="simple.whereRows.splice(i, 1)">删</button>
              </div>
              <p v-if="!simple.whereRows.length" class="sect-empty">未设置：全部记录都算作分子。</p>
              <p v-else-if="previewWhere" class="expr-preview">
                实际口径：<code>{{ previewWhere }}</code>
                <em v-if="hasOrGroup(simple.whereRows)">已按「或」分组自动加括号</em>
              </p>
            </div>

            <div class="sect">
              <div class="sect-head">
                <h4>{{ isSumOrAvg ? '② 要统计的数值列' : '② 额外条件（一般留空）' }}</h4>
                <div class="sect-acts" v-if="!isSumOrAvg">
                  <button class="btn btn-text" @click="addCond(simple.numRows)">+ 加一行</button>
                  <button class="btn btn-text" @click="addGroup(simple.numRows)">+ 加一组（或）</button>
                </div>
              </div>
              <template v-if="isSumOrAvg">
                <p class="sect-hint">对满足①的每条记录，取这一列的数值累加 / 求平均。</p>
                <select class="tb-input full" v-model="simple.numField">
                  <option value="">选择数值列…</option>
                  <option v-for="c in fieldOptions" :key="c" :value="c">{{ c }}</option>
                  <option v-for="c in dimColumns" :key="'d' + c" :value="c">{{ c }}</option>
                </select>
              </template>
              <template v-else>
                <p class="sect-hint">
                  去重患者数已按①筛选，这里通常不需要再填。仅在「同一患者还需满足另一列条件」时使用；
                  分组规则同①（同组「并且」、异组「或者」）。
                </p>
                <div class="cond" v-for="(r, i) in simple.numRows" :key="'n' + i">
                  <select class="tb-input grp" v-model.number="r.group" title="同组内是「并且」，不同组之间是「或者」">
                    <option v-for="g in groupOptions(simple.numRows, r.group)" :key="g" :value="g">组{{ g }}</option>
                  </select>
                  <select class="tb-input field-sel" v-model="r.field">
                    <option value="">选择字段…</option>
                    <option v-for="c in fieldOptions" :key="c" :value="c">{{ c }}</option>
                  </select>
                  <select class="tb-input op" v-model="r.op">
                    <option v-for="o in OPS" :key="o" :value="o">{{ o }}</option>
                  </select>
                  <input class="tb-input grow" v-model.trim="r.value" placeholder="值" />
                  <button class="btn btn-text danger" @click="simple.numRows.splice(i, 1)">删</button>
                </div>
                <p v-if="!simple.numRows.length" class="sect-empty">未设置。</p>
                <p v-else-if="previewNum" class="expr-preview">
                  实际口径：<code>{{ previewNum }}</code>
                  <em v-if="hasOrGroup(simple.numRows)">已按「或」分组自动加括号</em>
                </p>
              </template>
            </div>

            <div class="sect">
              <div class="sect-head">
                <h4>③ 分母筛选条件</h4>
                <div class="sect-acts">
                  <button class="btn btn-text" @click="addCond(simple.denRows)">+ 加一行</button>
                  <button class="btn btn-text" @click="addGroup(simple.denRows)">+ 加一组（或）</button>
                </div>
              </div>
              <p class="sect-hint">留空 = 同期全部患者。率类指标（如使用率）必须设置。分组规则同①。</p>
              <div class="cond" v-for="(r, i) in simple.denRows" :key="'d' + i">
                <select class="tb-input grp" v-model.number="r.group" title="同组内是「并且」，不同组之间是「或者」">
                  <option v-for="g in groupOptions(simple.denRows, r.group)" :key="g" :value="g">组{{ g }}</option>
                </select>
                <select class="tb-input field-sel" v-model="r.field">
                  <option value="">选择字段…</option>
                  <option v-for="c in fieldOptions" :key="c" :value="c">{{ c }}</option>
                </select>
                <select class="tb-input op" v-model="r.op">
                  <option v-for="o in OPS" :key="o" :value="o">{{ o }}</option>
                </select>
                <input class="tb-input grow" v-model.trim="r.value" placeholder="值" />
                <button class="btn btn-text danger" @click="simple.denRows.splice(i, 1)">删</button>
              </div>
              <p v-if="!simple.denRows.length" class="sect-empty">未设置：分母为同期全部患者。</p>
              <p v-else-if="previewDen" class="expr-preview">
                实际口径：<code>{{ previewDen }}</code>
                <em v-if="hasOrGroup(simple.denRows)">已按「或」分组自动加括号</em>
              </p>
            </div>

            <div class="sect">
              <div class="sect-head"><h4>④ 分组维度</h4></div>
              <p class="sect-hint">留空 = 只出「全院」一行。按科室统计请选 depart_code。</p>
              <div class="chips">
                <label v-for="c in dimColumns" :key="c" class="chip" :class="{ on: simple.dims.includes(c) }">
                  <input type="checkbox" :value="c" v-model="simple.dims" />
                  {{ c }}
                </label>
                <span v-if="!dimColumns.length" class="sect-empty">未取到字段清单，请先选择事实层。</span>
              </div>
            </div>
          </template>

          <!-- ---------- 高级模式 ---------- -->
          <template v-else>
            <div class="field">
              <label>分子筛选条件（where）</label>
              <textarea class="tb-input full code" rows="3" v-model="metricForm.where"
                        placeholder="如 t.age &gt;= 18 AND t.icu_days &gt; 2" />
            </div>
            <div class="field">
              <label>{{ isSumOrAvg ? '数值表达式（numerator）' : '分子附加条件（numerator）' }}</label>
              <textarea class="tb-input full code" rows="2" v-model="metricForm.numerator"
                        :placeholder="isSumOrAvg ? '如 t.icu_days' : '默认 1，可不填'" />
            </div>
            <div class="field">
              <label>分母筛选条件（denominatorWhere）</label>
              <textarea class="tb-input full code" rows="2" v-model="metricForm.denominatorWhere"
                        placeholder="留空 = 同期全部患者" />
            </div>
            <div class="field">
              <label>分组维度（多个用英文逗号分隔）</label>
              <input class="tb-input full" v-model="dimsText" placeholder="depart_code" />
            </div>
          </template>

          <div class="field">
            <label>备注</label>
            <input class="tb-input full" v-model.trim="metricForm.remark" placeholder="口径说明 / 注意事项，会显示在看板上" />
          </div>

          <!-- 校验结果 -->
          <div class="result" v-if="checkResult">
            <div class="result-head">
              <span class="tag" :class="checkResult.ok ? 'on' : 'off'">
                {{ checkResult.ok ? '校验通过' : '校验未通过' }}
              </span>
              <span class="dim" v-if="checkResult.durationMs">耗时 {{ checkResult.durationMs }} ms</span>
            </div>
            <ul class="issues" v-if="checkResult.errors && checkResult.errors.length">
              <li v-for="(e, i) in checkResult.errors" :key="'e' + i" class="err">{{ e }}</li>
            </ul>
            <ul class="issues" v-if="checkResult.warnings && checkResult.warnings.length">
              <li v-for="(w, i) in checkResult.warnings" :key="'w' + i" class="warn">{{ w }}</li>
            </ul>
            <div class="preview" v-if="checkResult.preview && checkResult.preview.length">
              <table class="mini">
                <thead>
                  <tr><th>分组</th><th>分子</th><th>分母</th><th>指标值</th></tr>
                </thead>
                <tbody>
                  <tr v-for="(p, i) in checkResult.preview" :key="i">
                    <td>{{ p.departCode }}</td>
                    <td>{{ p.numerator }}</td>
                    <td>{{ p.denominator }}</td>
                    <td class="strong">{{ fmt(p.value) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <details class="sql-box" v-if="checkResult.sql">
              <summary>编译后的 SQL（点开核对取数口径）</summary>
              <div class="sql-tools">
                <button type="button" class="btn-copy" :class="{ done: sqlCopied }"
                        @click="copySql(checkResult.sql)">
                  {{ sqlCopied ? '已复制' : '复制 SQL' }}
                </button>
              </div>
              <pre>{{ checkResult.sql }}</pre>
            </details>
          </div>
        </div>

        <div class="modal-foot">
          <button class="btn" @click="metricDialog = false">取消</button>
          <button class="btn" :disabled="busy" @click="doCheckMetric">校验并试算</button>
          <button class="btn btn-primary" :disabled="busy" @click="doSaveMetric">
            {{ busy ? '处理中…' : '保存并生效' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 事实层详情 / 编辑弹窗 ==================== -->
    <div class="modal-mask" v-if="factDialog" @click.self="closeFact">
      <div class="modal wide">
        <div class="modal-head">
          <h3>{{ factEditable ? '编辑事实层 · ' : '事实层 · ' }}{{ factForm.fact }}</h3>
          <button class="modal-close" @click="closeFact">×</button>
        </div>
        <div class="modal-body fact-body">
          <div class="fact-main">
            <div class="grid">
              <div class="field">
                <label>事实层名</label>
                <input class="tb-input full" v-model.trim="factForm.fact" :disabled="!factEditable" />
              </div>
              <div class="field">
                <label>所属域</label>
                <input class="tb-input full" v-model.trim="factForm.domain" :disabled="!factEditable" />
              </div>
              <div class="field">
                <label>来源逻辑表</label>
                <input class="tb-input full" v-model.trim="factForm.source" :disabled="!factEditable" />
              </div>
              <div class="field">
                <label>状态</label>
                <select class="tb-input full" v-model="factForm.status" :disabled="!factEditable">
                  <option value="ACTIVE">ACTIVE 可计算</option>
                  <option value="PENDING_SOURCE">PENDING_SOURCE 待接数据源</option>
                  <option value="PLACEHOLDER">PLACEHOLDER 空壳</option>
                </select>
              </div>
              <div class="field">
                <label>患者主键列</label>
                <input class="tb-input full" v-model.trim="factForm.patientKey" :disabled="!factEditable" />
              </div>
              <div class="field">
                <label>科室列</label>
                <input class="tb-input full" v-model.trim="factForm.departKey" :disabled="!factEditable" />
              </div>
            </div>

            <div class="field">
              <label>选列（每行一条，形如 <code>t.adm_time AS adm_time</code>）</label>
              <textarea class="tb-input full code" rows="5" v-model="factForm.selectText"
                        :disabled="!factEditable" />
            </div>
            <div class="field">
              <label>派生列（每行一条，可写 CASE WHEN … END AS xxx）</label>
              <textarea class="tb-input full code" rows="5" v-model="factForm.deriveText"
                        :disabled="!factEditable" />
            </div>
            <div class="field">
              <label>过滤条件（每行一条，逐条 AND）</label>
              <textarea class="tb-input full code" rows="4" v-model="factForm.whereText"
                        :disabled="!factEditable" />
            </div>
            <div class="field">
              <label>分组列（每行一条，留空 = 患者级不聚合）</label>
              <textarea class="tb-input full code" rows="2" v-model="factForm.groupText"
                        :disabled="!factEditable" />
            </div>
            <div class="field">
              <label>说明</label>
              <input class="tb-input full" v-model.trim="factForm.note" :disabled="!factEditable" />
            </div>

            <div class="result" v-if="factCheck">
              <div class="result-head">
                <span class="tag" :class="factCheck.ok ? 'on' : 'off'">
                  {{ factCheck.ok ? '校验通过' : '校验未通过' }}
                </span>
                <span class="dim" v-if="factCheck.factRows">试跑产出 {{ factCheck.factRows }} 行</span>
                <span class="dim" v-if="factCheck.durationMs">耗时 {{ factCheck.durationMs }} ms</span>
              </div>
              <ul class="issues" v-if="factCheck.errors && factCheck.errors.length">
                <li v-for="(e, i) in factCheck.errors" :key="'fe' + i" class="err">{{ e }}</li>
              </ul>
              <ul class="issues" v-if="factCheck.warnings && factCheck.warnings.length">
                <li v-for="(w, i) in factCheck.warnings" :key="'fw' + i" class="warn">{{ w }}</li>
              </ul>
            </div>
          </div>

          <aside class="fact-side">
            <div class="side-block">
              <h5>影响面 · {{ impact.length }} 条指标</h5>
              <p class="side-hint">以下指标引用了本事实层，改动会同时影响它们。</p>
              <ul class="impact">
                <li v-for="m in impact" :key="m.code">
                  <code class="small">{{ m.code }}</code> {{ m.name }}
                </li>
                <li v-if="!impact.length" class="dim">暂无指标引用</li>
              </ul>
            </div>
            <div class="side-block">
              <h5>可引用列 · {{ fieldOptions.length }}</h5>
              <div class="cols">
                <code v-for="c in fieldOptions" :key="c" class="col">{{ c }}</code>
                <span v-if="!fieldOptions.length" class="dim">未取到</span>
              </div>
            </div>
            <div class="side-block">
              <h5>当前生效 SQL</h5>
              <pre class="sql">{{ currentSql || '（未取到，可能事实层未绑定数据源）' }}</pre>
            </div>
          </aside>
        </div>
        <div class="modal-foot">
          <button class="btn" @click="closeFact">关闭</button>
          <template v-if="factEditable">
            <button class="btn" :disabled="busy" @click="doCheckFact">校验并试跑</button>
            <button class="btn btn-primary" :disabled="busy" @click="doSaveFact">
              {{ busy ? '处理中…' : '保存并生效' }}
            </button>
          </template>
        </div>
      </div>
    </div>

    <!-- ==================== 批量导入 ==================== -->
    <div class="modal-mask" v-if="importDialog" @click.self="importDialog = false">
      <div class="modal">
        <div class="modal-head">
          <h3>批量导入指标口径</h3>
          <button class="modal-close" @click="importDialog = false">×</button>
        </div>
        <div class="modal-body">
          <div class="tip">
            用本页「导出配置」产出的 JSON 回灌即可：先在测试环境调好口径，再一次性导到生产。
            导入是<b>逐条校验、逐条落库</b>——好的一条进、坏的一条带着原因返回，
            不会因为个别错误把整批回滚掉（上百条里有两三条写错是常态）。
          </div>

          <div class="field">
            <label>配置文件</label>
            <div class="cond">
              <button class="btn" @click="pickImportFile">选择 JSON 文件…</button>
              <span class="grow import-file">{{ importFileName || '未选择文件' }}</span>
              <input ref="importInput" type="file" accept=".json,application/json"
                     style="display: none" @change="onImportFile" />
            </div>
            <p v-if="importMetrics.length" class="sect-hint">已解析 {{ importMetrics.length }} 条指标定义。</p>
          </div>

          <div class="field">
            <label>遇到同编号指标时</label>
            <div class="chips">
              <label class="chip" :class="{ on: importMode === 'skip' }">
                <input type="radio" value="skip" v-model="importMode" /> 跳过（只新增，不动线上口径）
              </label>
              <label class="chip" :class="{ on: importMode === 'overwrite' }">
                <input type="radio" value="overwrite" v-model="importMode" /> 覆盖（用文件内容替换）
              </label>
            </div>
            <p class="sect-hint">
              默认「跳过」：一份来路不明的文件不应有机会整体覆盖线上口径，确认无误后再显式选「覆盖」。
            </p>
          </div>

          <div class="result" v-if="importResult">
            <div class="result-head">
              <span class="tag" :class="importResult.failed ? 'pend' : 'on'">{{ importResult.message }}</span>
            </div>
            <div class="preview" v-if="importResult.items && importResult.items.length">
              <table class="mini">
                <thead>
                  <tr><th style="width:190px">编号</th><th style="width:80px">结果</th><th>说明</th></tr>
                </thead>
                <tbody>
                  <tr v-for="(it, i) in importResult.items" :key="i">
                    <td><code class="small">{{ it.code || '—' }}</code></td>
                    <td><span class="tag" :class="importTagClass(it.status)">{{ importStatusLabel(it.status) }}</span></td>
                    <td>{{ it.message }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
        <div class="modal-foot">
          <button class="btn" @click="importDialog = false">关闭</button>
          <button class="btn btn-primary" :disabled="busy || !importMetrics.length" @click="doImport">
            {{ busy ? '导入中…' : '开始导入' }}
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 快照 ==================== -->
    <div class="modal-mask" v-if="snapshot" @click.self="snapshot = null">
      <div class="modal">
        <div class="modal-head">
          <h3>快照 · {{ snapshot.defKey }} v{{ snapshot.exprVersion }}</h3>
          <button class="modal-close" @click="snapshot = null">×</button>
        </div>
        <div class="modal-body">
          <pre class="sql">{{ pretty(snapshot.snapshot) }}</pre>
        </div>
        <div class="modal-foot">
          <button class="btn" @click="snapshot = null">关闭</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import '../styles/quality-theme.css'
import {
  fetchQualityConfigStatus,
  fetchConfigMetrics,
  fetchConfigMetric,
  validateConfigMetric,
  saveConfigMetric,
  disableConfigMetric,
  fetchConfigFacts,
  fetchConfigFact,
  validateConfigFact,
  saveConfigFact,
  fetchFactFields,
  fetchFactImpact,
  fetchConfigFactSql,
  fetchQualityConfigHistory,
  rollbackQualityConfig,
  reloadQualityConfig,
  exportQualityMetricsConfig,
  importQualityMetricsConfig
} from '../api/quality'

const OPS = ['=', '<>', '>', '>=', '<', '<=', 'LIKE']

const tabs = computed(() => [
  { key: 'metric', label: '指标配置', count: metrics.value.length },
  { key: 'fact', label: '事实层', count: facts.value.length },
  { key: 'history', label: '变更历史', count: null }
])

const tab = ref('metric')
const busy = ref(false)
// writable  = 真源是不是数据库（改了会不会生效）
// writeAllowed = 当前请求有没有写权限（服务端 IP 白名单 / 令牌判定）
// 两者是不同的问题，必须分开说，否则使用者无法判断该找谁处理
const writable = ref(false)
const writeAllowed = ref(true)
const writeHint = ref('')
const statusHint = ref('')
const operator = ref('')

/** 能编辑 = 真源可写 且 有写权限 */
const canEdit = computed(() => writable.value && writeAllowed.value)

const bannerClass = computed(() => {
  if (canEdit.value) return 'ok'
  // 有库但没写权限属于「配置问题」，比 yaml 只读更需要引起注意
  return writable.value ? 'danger' : 'warn'
})

const bannerText = computed(() => {
  if (!writable.value) {
    return `配置真源：YAML 文件 ｜ 页面为只读预览。${statusHint.value}`
  }
  if (!writeAllowed.value) {
    return `配置真源：数据库 ｜ 当前无写权限，页面为只读预览。${writeHint.value}`
  }
  return `配置真源：数据库 ｜ 保存即生效（保存时会先校验 + 试算一遍）｜ 操作人：${operator.value || '未知'}`
})

const metrics = ref([])
const facts = ref([])
const historyList = ref([])

const filterDomain = ref('')
const filterStatus = ref('')
const keyword = ref('')

const historyType = ref('')
const historyKey = ref('')

// ---------------------------------------------------------------------------
// 口径表达式的「结构化 ↔ 文本」互转
//
// 简单模式能成立的前提是可靠的双向转换。策略：拆得出且能原样拼回，才允许进简单模式；
// 只要往返不一致（引号里含 AND、嵌套括号、函数调用等），立即整体切高级模式。
// 宁可让少数复杂口径回到高级模式，也不能让结构化编辑悄悄改掉口径。
// ---------------------------------------------------------------------------

const COND_RE = /^([A-Za-z_][\w.]*)\s*(>=|<=|<>|!=|=|>|<|LIKE)\s*(.+)$/i

/** 新建条件行。group 从 1 开始，同组「并且」、异组「或者」 */
function newRow(group) {
  return { field: '', op: '=', value: '', group: Number(group) > 1 ? Number(group) : 1 }
}

function parseConditions(expr) {
  const text = String(expr || '').trim()
  if (!text) return []
  // 带引号的值可能内含 AND，交给高级模式处理
  if (text.includes("'") || text.includes('(')) return null
  const rows = []
  for (const part of text.split(/\s+AND\s+/i)) {
    const m = part.trim().match(COND_RE)
    if (!m) return null
    rows.push({ field: m[1], op: m[2] === '!=' ? '<>' : m[2].toUpperCase(), value: m[3].trim(), group: 1 })
  }
  return rows
}

function groupOf(r) {
  const n = Number(r.group)
  return n > 1 ? n : 1
}

/**
 * 条件行 → 表达式。
 *
 * 单组时产出 `a = 1 AND b = 2`（与历史口径完全一致，不加多余括号）；
 * 多组时产出 `(a = 1 AND b = 2) OR (c = 3)`。
 * 后端在拼分子命中的 CASE WHEN 时会把整段再包一层括号，因此这里的 OR 不会与
 * 外层 AND 抢优先级（见 SqlCompiler#numCondition）。
 */
function buildGroups(rows) {
  const valid = (rows || []).filter(r => r.field && String(r.value).trim() !== '')
  if (!valid.length) return ''
  const groups = new Map()
  valid.forEach(r => {
    const g = groupOf(r)
    if (!groups.has(g)) groups.set(g, [])
    groups.get(g).push(r)
  })
  const parts = [...groups.keys()].sort((a, b) => a - b).map(g => {
    const conds = groups.get(g).map(r => `${r.field} ${r.op || '='} ${r.value}`)
    return conds.length === 1 ? conds[0] : conds.join(' AND ')
  })
  return parts.length === 1 ? parts[0] : parts.map(p => `(${p})`).join(' OR ')
}

/** 只拆「顶层 OR」，括号内或引号内的 OR 不算分隔符 */
function splitTopLevelOr(text) {
  if (text.includes("'")) return null
  const parts = []
  let depth = 0
  let start = 0
  for (let i = 0; i < text.length; i++) {
    const c = text[i]
    if (c === '(') {
      depth++
    } else if (c === ')') {
      depth--
      if (depth < 0) return null
    } else if (depth === 0 && /^or\b/i.test(text.slice(i)) && (i === 0 || /[\s)]/.test(text[i - 1]))) {
      parts.push(text.slice(start, i).trim())
      i += 1
      start = i + 1
    }
  }
  if (depth !== 0) return null
  parts.push(text.slice(start).trim())
  return parts.length > 1 && parts.every(p => p) ? parts : null
}

/** 剥掉最外层括号；括号不配平或提前闭合则返回 null */
function stripOuterParens(text) {
  const s = String(text).trim()
  if (!s.startsWith('(') || !s.endsWith(')')) return null
  let depth = 0
  for (let i = 0; i < s.length; i++) {
    if (s[i] === '(') depth++
    else if (s[i] === ')') {
      depth--
      if (depth === 0 && i !== s.length - 1) return null
    }
  }
  return depth === 0 ? s.slice(1, -1).trim() : null
}

/** 表达式 → 条件行（带组号）；拆不动返回 null 表示「必须走高级模式」 */
function parseGroups(expr) {
  const text = String(expr || '').trim()
  if (!text) return []
  const flat = parseConditions(text)
  if (flat) return flat
  const parts = splitTopLevelOr(text)
  if (!parts) return null
  const rows = []
  let group = 1
  for (const part of parts) {
    const inner = stripOuterParens(part)
    if (inner === null) return null
    const conds = parseConditions(inner)
    if (!conds) return null
    conds.forEach(c => rows.push({ ...c, group }))
    group++
  }
  return rows
}

/**
 * 拆得开且能原样拼回，才返回条件行；否则返回 null。
 *
 * 往返比对是这里的关键护栏：只要结构化编辑不能精确还原原文，
 * 就整体退回高级模式，绝不「猜一个差不多的口径」。
 */
function toSimple(expr) {
  const text = String(expr || '').trim()
  if (!text) return []
  const rows = parseGroups(text)
  if (!rows) return null
  return buildGroups(rows) === text ? rows : null
}

/** 条件行操作：加一行（跟随末行组号）/ 另起一组（或） */
function addCond(rows) {
  const last = rows.length ? groupOf(rows[rows.length - 1]) : 1
  rows.push(newRow(last))
}

function addGroup(rows) {
  const max = rows.reduce((acc, r) => Math.max(acc, groupOf(r)), 0)
  rows.push(newRow(max + 1))
}

function groupOptions(rows, current) {
  const max = (rows || []).reduce((acc, r) => Math.max(acc, groupOf(r)), Math.max(1, Number(current) || 1))
  return Array.from({ length: max + 1 }, (_, i) => i + 1)
}

function hasOrGroup(rows) {
  return new Set((rows || []).filter(r => r.field && String(r.value).trim() !== '').map(groupOf)).size > 1
}

// ---------------------------------------------------------------------------
// 指标编辑
// ---------------------------------------------------------------------------

const metricDialog = ref(false)
const mode = ref('simple')
const checkResult = ref(null)

/** 「复制 SQL」按钮的短暂反馈态 */
const sqlCopied = ref(false)
let copyTimer = null

/**
 * 复制文本到剪贴板。
 *
 * 内网一般是 http://ip:port 访问，属于非安全上下文，`navigator.clipboard` 为 undefined，
 * 直接调用会抛错、按钮点了没反应。因此这里保留 textarea + execCommand 兜底路径，
 * 两种路径都失败时提示手动复制，避免用户以为按钮坏了。
 */
async function copySql(text) {
  if (!text) return
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.top = '-9999px'
      document.body.appendChild(ta)
      ta.select()
      const ok = document.execCommand('copy')
      document.body.removeChild(ta)
      if (!ok) throw new Error('execCommand copy 返回 false')
    }
    sqlCopied.value = true
    ElMessage.success('SQL 已复制')
    clearTimeout(copyTimer)
    copyTimer = setTimeout(() => { sqlCopied.value = false }, 2000)
  } catch (e) {
    ElMessage.error('复制失败，请手动选中 SQL 后复制')
  }
}

const metricForm = reactive(blankMetric())
const simple = reactive({ whereRows: [], numRows: [], numField: '', denRows: [], dims: [] })
const fieldOptions = ref([])
const dimColumns = ref([])

function blankMetric() {
  return {
    isNew: true, code: '', name: '', domain: '', fact: '', unit: '',
    valueType: 'COUNT', calcMode: 'DSL', implStatus: 'IMPL', agg: 'PT_COUNT',
    where: '', numerator: '', denominatorWhere: '', scale: 100, version: 1,
    dims: [], remark: '', sortNo: null,
    categoryCode: null, groupCode: null, qualityTypeCode: null, indexStandardCode: null,
    amountShowType: null, analysisCountType: null, legacyScript: null,
    legacySource: null, newTarget: null, reuseLevel: null
  }
}

const isSumOrAvg = computed(() => metricForm.agg === 'SUM' || metricForm.agg === 'AVG')

const dimsText = computed({
  get: () => (metricForm.dims || []).join(', '),
  set: (v) => {
    metricForm.dims = String(v || '').split(',').map(s => s.trim()).filter(Boolean)
  }
})

// 注意：列表接口返回的是 QualityMetricDef（落库实体），域字段名为 domainCode；
// 而编辑接口返回 MetricDefinition，域字段名是 domain。两者不同名，勿混用。
const domains = computed(() => {
  const set = new Set()
  metrics.value.forEach(m => { if (m.domainCode) set.add(m.domainCode) })
  return [...set].sort()
})

const filteredMetrics = computed(() => {
  const kw = keyword.value.toLowerCase()
  return metrics.value.filter(m => {
    if (filterDomain.value && m.domainCode !== filterDomain.value) return false
    if (filterStatus.value && m.implStatus !== filterStatus.value) return false
    if (!kw) return true
    return [m.indexCode, m.indexName, m.factName].some(v => String(v || '').toLowerCase().includes(kw))
  })
})

function valueTypeLabel(valueType, agg) {
  const aggLabel = { PT_COUNT: '去重患者数', SUM: '求和', AVG: '均值' }[agg] || agg || '—'
  return valueType ? `${valueType} · ${aggLabel}` : aggLabel
}

function fmt(v) {
  if (v === null || v === undefined || v === '') return '—'
  const n = Number(v)
  return Number.isNaN(n) ? String(v) : (Math.round(n * 10000) / 10000).toString()
}

/** 简单模式 → 表达式字段；高级模式直接用表单里的原始文本 */
function applyModeToForm() {
  if (mode.value !== 'simple') return
  metricForm.where = buildGroups(simple.whereRows)
  metricForm.denominatorWhere = buildGroups(simple.denRows)
  if (isSumOrAvg.value) {
    metricForm.numerator = simple.numField || ''
  } else {
    metricForm.numerator = buildGroups(simple.numRows)
  }
  metricForm.dims = [...simple.dims]
}

// 实时回显「实际口径」：条件组一旦变复杂，光看表格很难确认最终拼出的是什么，
// 尤其「或」分组的括号位置直接影响结果，必须让使用者看得见。
const previewWhere = computed(() => buildGroups(simple.whereRows))
const previewNum = computed(() => (isSumOrAvg.value ? '' : buildGroups(simple.numRows)))
const previewDen = computed(() => buildGroups(simple.denRows))

function switchMode(target) {
  if (target === mode.value) return
  if (target === 'advanced') {
    applyModeToForm()
    mode.value = 'advanced'
    return
  }
  // 高级 → 简单：先尝试结构化，任一表达式拆不动就拒绝切换并说明原因
  const whereRows = toSimple(metricForm.where)
  const denRows = toSimple(metricForm.denominatorWhere)
  const numRows = isSumOrAvg.value ? [] : toSimple(metricForm.numerator || '')
  if (whereRows === null || denRows === null || numRows === null) {
    ElMessage.warning('当前口径含函数、嵌套括号或带引号的条件，无法用简单模式表达，已保留在高级模式')
    return
  }
  simple.whereRows = whereRows
  simple.denRows = denRows
  simple.numRows = numRows
  simple.numField = isSumOrAvg.value ? (metricForm.numerator || '') : ''
  simple.dims = [...(metricForm.dims || [])]
  mode.value = 'simple'
}

async function loadFieldOptions(fact) {
  if (!fact) {
    fieldOptions.value = []
    dimColumns.value = []
    return
  }
  try {
    const cols = await fetchFactFields(fact)
    fieldOptions.value = Array.isArray(cols) ? cols : []
  } catch (e) {
    fieldOptions.value = []
  }
  // 分组维度只应出现在事实层已投影出来的列里，取事实层定义中的投影列更稳妥
  try {
    const f = await fetchConfigFact(fact)
    const projected = []
    ;[...(f.select || []), ...(f.derive || [])].forEach(expr => {
      const m = String(expr).match(/\bAS\s+([A-Za-z_][\w]*)\s*$/i)
      if (m) projected.push(m[1])
    })
    const merged = [...new Set([...projected, ...fieldOptions.value])]
    dimColumns.value = merged.length ? merged : fieldOptions.value
    if (dimColumns.value.includes(f.departKey)) {
      // 科室列默认放最前，是最常用的分组维度
      dimColumns.value = [f.departKey, ...dimColumns.value.filter(c => c !== f.departKey)]
    }
  } catch (e) {
    dimColumns.value = fieldOptions.value
  }
}

async function onFactChange() {
  await loadFieldOptions(metricForm.fact)
}

async function openMetric(code) {
  try {
    const m = await fetchConfigMetric(code)
    if (!m) return
    Object.assign(metricForm, blankMetric(), m, { isNew: false, version: m.version })
    checkResult.value = null
    mode.value = 'simple'
    await loadFieldOptions(m.fact)
    // 存量口径先试着结构化；拆不动就整条走高级模式
    const whereRows = toSimple(m.where)
    const denRows = toSimple(m.denominatorWhere)
    const sumOrAvg = m.agg === 'SUM' || m.agg === 'AVG'
    const numRows = sumOrAvg ? [] : toSimple(m.numerator || '')
    if (whereRows === null || denRows === null || numRows === null) {
      mode.value = 'advanced'
    } else {
      simple.whereRows = whereRows
      simple.denRows = denRows
      simple.numRows = numRows
      simple.numField = sumOrAvg ? (m.numerator || '') : ''
      simple.dims = [...(m.dims || [])]
    }
    metricDialog.value = true
  } catch (e) {
    ElMessage.error('打开失败：' + e.message)
  }
}

function collectMetric() {
  applyModeToForm()
  return {
    code: metricForm.code, name: metricForm.name, domain: metricForm.domain,
    fact: metricForm.fact || null, unit: metricForm.unit,
    valueType: metricForm.valueType, calcMode: metricForm.calcMode,
    implStatus: metricForm.implStatus, agg: metricForm.agg,
    where: metricForm.where || null,
    numerator: metricForm.numerator || null,
    denominatorWhere: metricForm.denominatorWhere || null,
    scale: metricForm.scale, version: metricForm.version,
    dims: metricForm.dims || [], remark: metricForm.remark,
    sortNo: metricForm.sortNo,
    categoryCode: metricForm.categoryCode, groupCode: metricForm.groupCode,
    qualityTypeCode: metricForm.qualityTypeCode, indexStandardCode: metricForm.indexStandardCode,
    amountShowType: metricForm.amountShowType, analysisCountType: metricForm.analysisCountType,
    legacyScript: metricForm.legacyScript, legacySource: metricForm.legacySource,
    newTarget: metricForm.newTarget, reuseLevel: metricForm.reuseLevel
  }
}

function guardEmptyMetric() {
  if (!metricForm.code) { ElMessage.warning('请填写指标编号'); return false }
  if (!metricForm.name) { ElMessage.warning('请填写指标名称'); return false }
  if (metricForm.implStatus === 'IMPL' && !metricForm.fact) {
    ElMessage.warning('「已实现」的指标必须绑定事实层；口径未定请把实现状态改为「空壳」')
    return false
  }
  return true
}

async function doCheckMetric() {
  if (!guardEmptyMetric()) return
  busy.value = true
  try {
    checkResult.value = await validateConfigMetric(collectMetric(), true)
    if (checkResult.value.ok) {
      ElMessage.success(checkResult.value.preview && checkResult.value.preview.length
        ? '校验通过，试算结果见下方' : '校验通过')
    }
  } catch (e) {
    ElMessage.error('校验失败：' + e.message)
  } finally {
    busy.value = false
  }
}

async function doSaveMetric() {
  if (!guardEmptyMetric()) return
  busy.value = true
  try {
    const r = await saveConfigMetric(collectMetric(), true)
    checkResult.value = r.validation
    if (!r.saved) {
      ElMessage.error(r.message || '校验未通过，配置未保存')
      return
    }
    ElMessage.success(`已保存并生效（口径版本 v${r.exprVersion}，同步字典 ${r.syncedCount} 项）`)
    metricDialog.value = false
    await loadMetrics()
  } catch (e) {
    ElMessage.error('保存失败：' + e.message)
  } finally {
    busy.value = false
  }
}

async function doDisable(m) {
  try {
    await ElMessageBox.confirm(
      `确认停用「${m.indexName}」？停用后不再参与计算，历史结果保留，可随时改回启用。`,
      '停用指标', { type: 'warning', customClass: 'qb-overlay' }
    )
  } catch (e) {
    return
  }
  busy.value = true
  try {
    await disableConfigMetric(m.indexCode)
    ElMessage.success('已停用')
    await loadMetrics()
  } catch (e) {
    ElMessage.error('停用失败：' + e.message)
  } finally {
    busy.value = false
  }
}

// ---------------------------------------------------------------------------
// 事实层
// ---------------------------------------------------------------------------

const factDialog = ref(false)
const factEditable = ref(false)
const factCheck = ref(null)
const impact = ref([])
const currentSql = ref('')
const factForm = reactive(blankFact())

function blankFact() {
  return {
    fact: '', domain: '', status: 'ACTIVE', source: '', alias: 't',
    patientKey: 'patient_id', inHospitalNoKey: 'in_hospital_no',
    patientNameKey: 'patient_name', departKey: 'depart_code',
    selectText: '', deriveText: '', whereText: '', groupText: '', note: ''
  }
}

function linesToArray(text) {
  return String(text || '').split('\n').map(s => s.trim()).filter(Boolean)
}

function collectFact() {
  return {
    fact: factForm.fact, domain: factForm.domain, status: factForm.status,
    source: factForm.source, alias: factForm.alias || 't',
    patientKey: factForm.patientKey, inHospitalNoKey: factForm.inHospitalNoKey,
    patientNameKey: factForm.patientNameKey, departKey: factForm.departKey,
    select: linesToArray(factForm.selectText),
    derive: linesToArray(factForm.deriveText),
    where: linesToArray(factForm.whereText),
    group: linesToArray(factForm.groupText),
    note: factForm.note
  }
}

async function openFact(name) {
  await fillFact(name, false)
}

async function editFact(name) {
  await fillFact(name, true)
}

async function fillFact(name, editable) {
  try {
    const f = await fetchConfigFact(name)
    if (!f) return
    Object.assign(factForm, blankFact(), {
      fact: f.fact, domain: f.domain, status: f.status, source: f.source,
      alias: f.alias, patientKey: f.patientKey, inHospitalNoKey: f.inHospitalNoKey,
      patientNameKey: f.patientNameKey, departKey: f.departKey,
      selectText: (f.select || []).join('\n'),
      deriveText: (f.derive || []).join('\n'),
      whereText: (f.where || []).join('\n'),
      groupText: (f.group || []).join('\n'),
      note: f.note
    })
    factEditable.value = editable && canEdit.value
    factCheck.value = null
    factDialog.value = true
    // 三个附属信息互不依赖，任一个失败都不该影响打开弹窗
    impact.value = await fetchFactImpact(name).catch(() => [])
    fieldOptions.value = await fetchFactFields(name).catch(() => [])
    currentSql.value = await fetchConfigFactSql(name).catch(() => '')
  } catch (e) {
    ElMessage.error('打开失败：' + e.message)
  }
}

function closeFact() {
  factDialog.value = false
  factCheck.value = null
}

async function doCheckFact() {
  busy.value = true
  try {
    factCheck.value = await validateConfigFact(collectFact(), true)
    if (factCheck.value.ok) ElMessage.success('校验通过')
  } catch (e) {
    ElMessage.error('校验失败：' + e.message)
  } finally {
    busy.value = false
  }
}

async function doSaveFact() {
  if (impact.value.length) {
    try {
      await ElMessageBox.confirm(
        `本事实层被 ${impact.value.length} 条指标引用，保存后这些指标的口径会立即变化。确认继续？`,
        '影响面确认', { type: 'warning', customClass: 'qb-overlay' }
      )
    } catch (e) {
      return
    }
  }
  busy.value = true
  try {
    const r = await saveConfigFact(collectFact(), true)
    factCheck.value = r.validation
    if (!r.saved) {
      ElMessage.error(r.message || '校验未通过，配置未保存')
      return
    }
    ElMessage.success(`已保存并生效（同步字典 ${r.syncedCount} 项）`)
    closeFact()
    await Promise.all([loadMetrics(), loadFacts()])
  } catch (e) {
    ElMessage.error('保存失败：' + e.message)
  } finally {
    busy.value = false
  }
}

// ---------------------------------------------------------------------------
// 历史与回滚
// ---------------------------------------------------------------------------

const snapshot = ref(null)

async function loadHistory() {
  try {
    historyList.value = await fetchQualityConfigHistory({
      defType: historyType.value || undefined,
      defKey: historyKey.value || undefined,
      limit: 50
    })
  } catch (e) {
    ElMessage.error('查询失败：' + e.message)
  }
}

function openHistory(code) {
  historyKey.value = code
  historyType.value = 'METRIC'
  tab.value = 'history'
  loadHistory()
}

function showSnapshot(h) {
  snapshot.value = h
}

function pretty(text) {
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch (e) {
    return text
  }
}

async function doRollback(h) {
  try {
    await ElMessageBox.confirm(
      `将「${h.defKey}」回滚到 v${h.exprVersion}（${h.changeType}）。回滚会以该版本内容重新发布一次，审计记录保留。`,
      '回滚确认', { type: 'warning', customClass: 'qb-overlay' }
    )
  } catch (e) {
    return
  }
  busy.value = true
  try {
    await rollbackQualityConfig(h.id)
    ElMessage.success('已回滚并生效')
    await Promise.all([loadMetrics(), loadHistory()])
  } catch (e) {
    ElMessage.error('回滚失败：' + e.message)
  } finally {
    busy.value = false
  }
}

async function doReload() {
  busy.value = true
  try {
    const n = await reloadQualityConfig()
    ElMessage.success(`配置已重载，同步字典 ${n} 项`)
    await reloadAll()
  } catch (e) {
    ElMessage.error('重载失败：' + e.message)
  } finally {
    busy.value = false
  }
}

// ---------------------------------------------------------------------------
// 加载
// ---------------------------------------------------------------------------

async function loadStatus() {
  try {
    const s = await fetchQualityConfigStatus()
    writable.value = !!s.writable
    statusHint.value = s.hint || ''
    // writeAllowed 由服务端判定（IP 白名单 / 令牌），页面只做展示与禁用，
    // 真正的拦截在服务端 —— 前端禁用只是体验优化，不是安全边界。
    writeAllowed.value = s.writeAllowed !== false
    writeHint.value = s.writeHint || ''
    operator.value = s.operator || ''
  } catch (e) {
    writable.value = false
    writeAllowed.value = false
    statusHint.value = '未取到配置状态，请稍后刷新'
  }
}

// ---------------------------------------------------------------------------
// 批量导入 / 导出
//
// 导出的是「当前生效口径」的完整 JSON（含表达式正文），因此可以直接回灌导入，
// 这条路径本身就是跨环境迁移手段：测试环境调好 → 导出 → 生产导入。
// ---------------------------------------------------------------------------

const importDialog = ref(false)
const importInput = ref(null)
const importFileName = ref('')
const importMetrics = ref([])
const importMode = ref('skip')
const importResult = ref(null)

function stamp() {
  const d = new Date()
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}-${p(d.getHours())}${p(d.getMinutes())}${p(d.getSeconds())}`
}

async function doExport() {
  busy.value = true
  try {
    const payload = await exportQualityMetricsConfig()
    const list = (payload && payload.metrics) || []
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `quality-metrics-${stamp()}.json`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${list.length} 条指标口径`)
  } catch (e) {
    ElMessage.error('导出失败：' + e.message)
  } finally {
    busy.value = false
  }
}

function openImport() {
  importFileName.value = ''
  importMetrics.value = []
  importMode.value = 'skip'
  importResult.value = null
  importDialog.value = true
}

function pickImportFile() {
  if (importInput.value) importInput.value.click()
}

async function onImportFile(ev) {
  const file = ev.target.files && ev.target.files[0]
  // 清空 value：否则连续选同一个文件不会再触发 change
  ev.target.value = ''
  if (!file) return
  let parsed
  try {
    parsed = JSON.parse(await file.text())
  } catch (e) {
    ElMessage.error('文件不是合法 JSON：' + e.message)
    return
  }
  // 兼容两种形态：导出接口的 {metrics:[...]} 包裹，或裸数组
  const list = Array.isArray(parsed) ? parsed : (parsed && Array.isArray(parsed.metrics) ? parsed.metrics : null)
  if (!list || !list.length) {
    ElMessage.error('文件里没有指标数据（应为本页「导出配置」产出的 JSON）')
    return
  }
  importFileName.value = file.name
  importMetrics.value = list
  importResult.value = null
}

function importStatusLabel(status) {
  return { CREATED: '新增', UPDATED: '覆盖', SKIPPED: '跳过', FAILED: '失败' }[status] || status
}

function importTagClass(status) {
  if (status === 'CREATED' || status === 'UPDATED') return 'on'
  return status === 'FAILED' ? 'off' : 'pend'
}

async function doImport() {
  if (!importMetrics.value.length) return
  if (importMode.value === 'overwrite') {
    try {
      await ElMessageBox.confirm(
        `将用文件内容覆盖 ${importMetrics.value.length} 条中的同编号指标，线上口径会立即变化。确认继续？`,
        '覆盖确认', { type: 'warning', customClass: 'qb-overlay' }
      )
    } catch (e) {
      return
    }
  }
  busy.value = true
  try {
    const r = await importQualityMetricsConfig(importMetrics.value, importMode.value)
    importResult.value = r
    if (r.rejected) {
      ElMessage.error(r.message || '导入未执行')
      return
    }
    if (r.failed) {
      ElMessage.warning(r.message || '导入完成，但有失败条目')
    } else {
      ElMessage.success(r.message || '导入完成')
    }
    // 导入成功后刷新：列表、状态（同步字典条数可能变化）
    if (r.created || r.updated) {
      await reloadAll()
    }
  } catch (e) {
    ElMessage.error('导入失败：' + e.message)
  } finally {
    busy.value = false
  }
}

async function loadMetrics() {
  // 指标是「参考表」性质（百来行），一次性取回后前端过滤：
  // 每敲一个字就往返一次达梦并不划算。
  metrics.value = await fetchConfigMetrics({})
}

async function loadFacts() {
  facts.value = await fetchConfigFacts()
}

async function reloadAll() {
  await Promise.all([loadStatus(), loadMetrics(), loadFacts()])
  if (tab.value === 'history') await loadHistory()
}

onMounted(async () => {
  try {
    await reloadAll()
  } catch (e) {
    ElMessage.error('初始化失败：' + e.message)
  }
})
</script>

<style scoped>
/* ===================================================================
   质控指标配置 · 暖橙主题（设计稿 quality-config.html 适配）
   本页为原生 HTML 控件，所有视觉都在本文件内定义。
   =================================================================== */
.qc {
  padding: 24px 28px 48px;
  background: #fafaf9;
  min-height: 100vh;
  color: #292524;
}
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.title h2 {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #1c1917;
}
.title .sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: #78716c;
}
.filters {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* ---- 真源 / 权限 banner：保留 ok/warn/danger 三态语义，换设计软色 ---- */
.banner {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 16px;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 13px;
  border: 1px solid transparent;
}
.banner .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}
.banner.ok {
  background: #dcfce7;
  border-color: #bbf7d0;
  color: #166534;
}
.banner.ok .dot { background: #16a34a; }
.banner.warn {
  background: #fef3c7;
  border-color: #fde68a;
  color: #92400e;
}
.banner.warn .dot { background: #d97706; }
/* 真源已是数据库、但当前没有写权限：属于配置问题，比 yaml 只读更需要被注意到 */
.banner.danger {
  background: #fee2e2;
  border-color: #fecaca;
  color: #991b1b;
}
.banner.danger .dot { background: #dc2626; }

/* ---- Tab（设计稿下划线 Tab，激活暖橙） ---- */
.tabs {
  display: flex;
  gap: 4px;
  margin-top: 18px;
  border-bottom: 1px solid #e7e5e4;
}
.tab {
  padding: 10px 18px;
  border: none;
  background: transparent;
  font-size: 14px;
  color: #78716c;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: color 0.15s;
}
.tab:hover { color: #ea580c; }
.tab em {
  margin-left: 6px;
  font-style: normal;
  font-size: 12px;
  color: #a8a29e;
}
.tab.active {
  color: #ea580c;
  border-bottom-color: #ea580c;
  font-weight: 600;
}
.tab.active em { color: #c2410c; }

/* 每个 Tab 内容一张白卡 */
.panel {
  margin-top: 16px;
  background: #ffffff;
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  box-shadow: 0 1px 2px rgba(28, 25, 23, 0.04);
  padding: 16px 18px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.toolbar .count {
  margin-left: auto;
  font-size: 12px;
  color: #a8a29e;
}

.tip {
  margin-bottom: 12px;
  padding: 10px 14px;
  background: #fff7ed;
  border-left: 3px solid #f97316;
  border-radius: 4px;
  font-size: 12.5px;
  color: #57534e;
  line-height: 1.7;
}

/* ---- 原生表格：暖灰表头 + 轻格线，圆角由 .panel 卡片裁切 ---- */
.tbl {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  background: #fff;
}
.tbl th,
.tbl td {
  padding: 10px 12px;
  border-bottom: 1px solid #efeeec;
  text-align: left;
  vertical-align: middle;
}
.tbl thead th {
  background: #fafaf9;
  font-weight: 600;
  font-size: 12px;
  color: #57534e;
  white-space: nowrap;
}
.tbl tbody tr:hover { background: #fcfcfb; }
.tbl tbody tr:last-child td { border-bottom: none; }
.tbl tr.off { opacity: 0.55; }
.tbl .remark {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #78716c;
}
.tbl .empty {
  text-align: center;
  color: #a8a29e;
  padding: 32px 0;
}
code.small { font-size: 12px; }
code { font-family: Consolas, Monaco, monospace; }
.tbl code {
  color: #c2410c;
  background: #fff7ed;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12.5px;
}

/* ---- 状态标签：胶囊 + 柔和底色（设计稿 status-* 风格） ---- */
.tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  border-radius: 9999px;
  font-size: 12px;
  line-height: 1.6;
  background: #f5f5f4;
  color: #57534e;
  white-space: nowrap;
}
.tag.on { background: #dcfce7; color: #166534; }
.tag.off { background: #fee2e2; color: #991b1b; }
.tag.pend { background: #fef3c7; color: #92400e; }

/* ---- 按钮 ---- */
.btn {
  padding: 6px 14px;
  border: 1px solid #d6d3d1;
  border-radius: 8px;
  background: #fff;
  color: #44403c;
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s, background 0.15s;
}
.btn:hover:not(:disabled) { border-color: #ea580c; color: #ea580c; }
.btn:disabled { opacity: 0.45; cursor: not-allowed; }
.btn-primary {
  background: #ea580c;
  border-color: #ea580c;
  color: #fff;
}
.btn-primary:hover:not(:disabled) {
  background: #c2410c;
  border-color: #c2410c;
  color: #fff;
}
.btn-text {
  border: none;
  background: transparent;
  padding: 2px 6px;
  color: #ea580c;
}
.btn-text:hover:not(:disabled) { color: #c2410c; }
.btn-text.danger { color: #dc2626; }
.btn-text.danger:hover:not(:disabled) { color: #b91c1c; }

/* ---- 原生输入控件 ---- */
.tb-input {
  padding: 6px 10px;
  border: 1px solid #d6d3d1;
  border-radius: 8px;
  font-size: 13px;
  background: #fff;
  color: #292524;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.tb-input:focus {
  outline: none;
  border-color: #f97316;
  box-shadow: 0 0 0 3px rgba(249, 115, 22, 0.12);
}
.tb-input:disabled { background: #f5f5f4; color: #a8a29e; }
.tb-input.full { width: 100%; box-sizing: border-box; }
.tb-input.search { width: 240px; }

/* ---- 弹窗（原生实现，跟随设计稿 12px 圆角） ---- */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(28, 25, 23, 0.42);
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: 40px 16px;
  overflow: auto;
  z-index: 2000;
}
.modal {
  width: 640px;
  max-width: 100%;
  background: #fff;
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  box-shadow: 0 18px 48px rgba(28, 25, 23, 0.18);
}
.modal.wide { width: 1080px; }
.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #f5f5f4;
}
.modal-head h3 { margin: 0; font-size: 16px; color: #1c1917; }
.modal-close {
  border: none;
  background: transparent;
  font-size: 22px;
  line-height: 1;
  color: #a8a29e;
  cursor: pointer;
}
.modal-close:hover { color: #57534e; }
.modal-body { padding: 18px 20px; }
.modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 14px 20px;
  border-top: 1px solid #f5f5f4;
}

.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 16px;
}
.field { margin-bottom: 10px; }
.field label {
  display: block;
  margin-bottom: 4px;
  font-size: 12.5px;
  color: #57534e;
}
.req { color: #dc2626; }
.code {
  font-family: Consolas, Monaco, monospace;
  font-size: 12.5px;
  line-height: 1.6;
  resize: vertical;
}

/* ---- 简单 / 高级模式切换条 ---- */
.mode-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 6px 0 12px;
  padding: 10px 14px;
  background: #fafaf9;
  border: 1px solid #f5f5f4;
  border-radius: 8px;
}
.modes { display: flex; gap: 0; flex-shrink: 0; }
.mode {
  padding: 5px 16px;
  border: 1px solid #d6d3d1;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
  color: #57534e;
  transition: all 0.15s;
}
.mode:first-child { border-radius: 8px 0 0 8px; }
.mode:last-child { border-radius: 0 8px 8px 0; border-left: none; }
.mode.active { background: #ea580c; border-color: #ea580c; color: #fff; }
.mode-hint { margin: 0; font-size: 12px; color: #78716c; }

/* ---- 口径编辑分区 ---- */
.sect {
  margin: 12px 0;
  padding: 14px 16px;
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  background: #fcfcfb;
}
.sect-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.sect-head h4 { margin: 0; font-size: 13.5px; color: #292524; }
.sect-acts { display: flex; gap: 2px; flex-shrink: 0; }
.sect-hint {
  margin: 4px 0 8px;
  font-size: 12px;
  color: #78716c;
}
.sect-empty {
  margin: 4px 0 0;
  font-size: 12px;
  color: #a8a29e;
}
.cond {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.cond .op { width: 82px; flex-shrink: 0; text-align: center; }
.cond .grow { flex: 1; min-width: 0; }
.cond .field-sel { width: 210px; flex-shrink: 0; }
/* 条件组号：同组「并且」、异组「或者」，窄到不喧宾夺主但仍可点 */
.cond .grp {
  width: 66px;
  flex-shrink: 0;
  padding-left: 4px;
  padding-right: 4px;
  color: #ea580c;
}

/* 实际口径回显：条件组一旦含「或」，光看表格无法确认括号落在哪 */
.expr-preview {
  margin: 8px 0 0;
  font-size: 12px;
  color: #57534e;
  line-height: 1.7;
}
.expr-preview code {
  padding: 1px 6px;
  background: #fff7ed;
  border-radius: 4px;
  color: #c2410c;
  word-break: break-all;
}
.expr-preview em {
  margin-left: 6px;
  font-style: normal;
  font-size: 11.5px;
  color: #a8a29e;
}

.import-file {
  font-size: 12.5px;
  color: #78716c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---- 维度 / 导入模式 选择胶囊 ---- */
.chips { display: flex; flex-wrap: wrap; gap: 6px; }
.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border: 1px solid #d6d3d1;
  border-radius: 9999px;
  font-size: 12.5px;
  color: #57534e;
  cursor: pointer;
  background: #fff;
  transition: all 0.15s;
}
.chip:hover { border-color: #fb923c; }
.chip.on {
  border-color: #ea580c;
  background: #fff7ed;
  color: #c2410c;
  font-weight: 500;
}
.chip input { margin: 0; }

/* ---- 校验 / 试算结果区 ---- */
.result {
  margin-top: 12px;
  padding: 14px 16px;
  border: 1px solid #e7e5e4;
  border-radius: 12px;
  background: #fafaf9;
}
.result-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.dim { font-size: 12px; color: #a8a29e; }
.issues { margin: 0 0 8px; padding-left: 18px; font-size: 12.5px; line-height: 1.8; }
.issues .err { color: #dc2626; }
.issues .warn { color: #b45309; }

.preview { margin-top: 8px; }
.mini {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}
.mini th,
.mini td {
  padding: 6px 10px;
  border: 1px solid #efeeec;
  text-align: left;
}
.mini th { background: #fafaf9; font-weight: 600; color: #57534e; }
.mini .strong { font-weight: 700; color: #ea580c; }

/* ---- 编译后 SQL 折叠块（设计稿近黑代码块） ---- */
.sql-box { margin-top: 10px; }
.sql-box summary {
  cursor: pointer;
  font-size: 12.5px;
  color: #ea580c;
  font-weight: 500;
}
.sql-tools {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
.btn-copy {
  padding: 4px 12px;
  font-size: 12px;
  color: #ea580c;
  background: #fff;
  border: 1px solid #fdba74;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}
.btn-copy:hover { background: #fff7ed; }
.btn-copy.done {
  color: #16a34a;
  border-color: #86efac;
  background: #f0fdf4;
}
.sql,
pre.sql {
  margin: 8px 0 0;
  padding: 12px 14px;
  background: #1c1917;
  color: #e7e5e4;
  border: 1px solid #292524;
  border-radius: 8px;
  font-family: Consolas, Monaco, monospace;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 300px;
  overflow: auto;
}

.fact-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 330px;
  gap: 18px;
}
.fact-side {
  border-left: 1px solid #f5f5f4;
  padding-left: 16px;
}
.side-block { margin-bottom: 18px; }
.side-block h5 {
  margin: 0 0 6px;
  font-size: 13px;
  color: #292524;
}
.side-hint {
  margin: 0 0 6px;
  font-size: 12px;
  color: #78716c;
}
.impact { margin: 0; padding-left: 16px; font-size: 12.5px; line-height: 1.9; color: #44403c; }
.cols { display: flex; flex-wrap: wrap; gap: 4px; }
.col {
  padding: 2px 8px;
  background: #f5f5f4;
  border-radius: 4px;
  font-size: 11.5px;
  color: #57534e;
}
</style>
