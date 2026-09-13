<template>
  <div class="qc">
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

    <!-- 真源提示：先说清楚「能不能改」，而不是等点了保存才拒绝 -->
    <div class="banner" :class="writable ? 'ok' : 'warn'">
      <span class="dot"></span>
      <span v-if="writable">配置真源：数据库 ｜ 保存即生效（保存时会先校验 + 试算一遍）</span>
      <span v-else>配置真源：YAML 文件 ｜ 页面为只读预览。{{ statusHint }}</span>
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
              <button class="btn btn-text" :disabled="!writable" @click="openMetric(m.indexCode)">编辑</button>
              <button class="btn btn-text" :disabled="!writable" @click="openHistory(m.indexCode)">历史</button>
              <button v-if="m.status === 1" class="btn btn-text danger" :disabled="!writable"
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
              <button class="btn btn-text" :disabled="!writable" @click="editFact(f.fact)">编辑</button>
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
              <button class="btn btn-text danger" :disabled="!writable" @click="doRollback(h)">回滚到此版</button>
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
                <button class="btn btn-text" @click="simple.whereRows.push({ field: '', op: '=', value: '' })">+ 加一行</button>
              </div>
              <p class="sect-hint">多行之间是「并且」的关系，全部满足才计入。</p>
              <div class="cond" v-for="(r, i) in simple.whereRows" :key="'w' + i">
                <select class="tb-input" v-model="r.field">
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
            </div>

            <div class="sect">
              <div class="sect-head">
                <h4>{{ isSumOrAvg ? '② 要统计的数值列' : '② 额外条件（一般留空）' }}</h4>
                <button v-if="!isSumOrAvg" class="btn btn-text"
                        @click="simple.numRows.push({ field: '', op: '=', value: '' })">+ 加一行</button>
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
                  去重患者数已按①筛选，这里通常不需要再填。仅在「同一患者还需满足另一列条件」时使用。
                </p>
                <div class="cond" v-for="(r, i) in simple.numRows" :key="'n' + i">
                  <select class="tb-input" v-model="r.field">
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
              </template>
            </div>

            <div class="sect">
              <div class="sect-head">
                <h4>③ 分母筛选条件</h4>
                <button class="btn btn-text" @click="simple.denRows.push({ field: '', op: '=', value: '' })">+ 加一行</button>
              </div>
              <p class="sect-hint">留空 = 同期全部患者。率类指标（如使用率）必须设置。</p>
              <div class="cond" v-for="(r, i) in simple.denRows" :key="'d' + i">
                <select class="tb-input" v-model="r.field">
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
  reloadQualityConfig
} from '../api/quality'

const OPS = ['=', '<>', '>', '>=', '<', '<=', 'LIKE']

const tabs = computed(() => [
  { key: 'metric', label: '指标配置', count: metrics.value.length },
  { key: 'fact', label: '事实层', count: facts.value.length },
  { key: 'history', label: '变更历史', count: null }
])

const tab = ref('metric')
const busy = ref(false)
const writable = ref(false)
const statusHint = ref('')

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

function parseConditions(expr) {
  const text = String(expr || '').trim()
  if (!text) return []
  // 带引号的值可能内含 AND，交给高级模式处理
  if (text.includes("'") || text.includes('(')) return null
  const rows = []
  for (const part of text.split(/\s+AND\s+/i)) {
    const m = part.trim().match(COND_RE)
    if (!m) return null
    rows.push({ field: m[1], op: m[2] === '!=' ? '<>' : m[2].toUpperCase(), value: m[3].trim() })
  }
  return rows
}

function buildConditions(rows) {
  return (rows || [])
    .filter(r => r.field && String(r.value).trim() !== '')
    .map(r => `${r.field} ${r.op || '='} ${r.value}`)
    .join(' AND ')
}

/** 拆得开且拼得回，才返回条件行；否则返回 null 表示「必须走高级模式」 */
function toSimple(expr) {
  const text = String(expr || '').trim()
  if (!text) return []
  const rows = parseConditions(text)
  if (!rows) return null
  return buildConditions(rows) === text ? rows : null
}

// ---------------------------------------------------------------------------
// 指标编辑
// ---------------------------------------------------------------------------

const metricDialog = ref(false)
const mode = ref('simple')
const checkResult = ref(null)
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
  metricForm.where = buildConditions(simple.whereRows)
  metricForm.denominatorWhere = buildConditions(simple.denRows)
  if (isSumOrAvg.value) {
    metricForm.numerator = simple.numField || ''
  } else {
    metricForm.numerator = buildConditions(simple.numRows)
  }
  metricForm.dims = [...simple.dims]
}

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
    ElMessage.warning('当前口径含函数、括号或带引号的条件，无法用简单模式表达，已保留在高级模式')
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
    const r = await saveConfigMetric(collectMetric(), true, currentOperator())
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
      '停用指标', { type: 'warning' }
    )
  } catch (e) {
    return
  }
  busy.value = true
  try {
    await disableConfigMetric(m.indexCode, currentOperator())
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
    factEditable.value = editable && writable.value
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
        '影响面确认', { type: 'warning' }
      )
    } catch (e) {
      return
    }
  }
  busy.value = true
  try {
    const r = await saveConfigFact(collectFact(), true, currentOperator())
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
      '回滚确认', { type: 'warning' }
    )
  } catch (e) {
    return
  }
  busy.value = true
  try {
    await rollbackQualityConfig(h.id, currentOperator())
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

/** 操作人：质控页面目前无账号体系，统一记 admin，保证审计字段不为空 */
function currentOperator() {
  return 'admin'
}

async function loadStatus() {
  try {
    const s = await fetchQualityConfigStatus()
    writable.value = !!s.writable
    statusHint.value = s.hint || ''
  } catch (e) {
    writable.value = false
    statusHint.value = '未取到配置状态，请稍后刷新'
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
.qc {
  padding: 16px 20px 40px;
  color: #1f2937;
}
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.title h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #111827;
}
.title .sub {
  margin: 4px 0 0;
  font-size: 12px;
  color: #6b7280;
}
.filters {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.banner {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding: 9px 12px;
  border-radius: 6px;
  font-size: 13px;
}
.banner .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}
.banner.ok {
  background: #f0f9f4;
  border: 1px solid #cdeadb;
  color: #22684a;
}
.banner.ok .dot { background: #2f9e6a; }
.banner.warn {
  background: #fff8ec;
  border: 1px solid #ffe0b2;
  color: #8a5a10;
}
.banner.warn .dot { background: #e8a33d; }

.tabs {
  display: flex;
  gap: 4px;
  margin-top: 14px;
  border-bottom: 1px solid #e5e7eb;
}
.tab {
  padding: 8px 16px;
  border: none;
  background: transparent;
  font-size: 14px;
  color: #4b5563;
  cursor: pointer;
  border-bottom: 2px solid transparent;
}
.tab em {
  margin-left: 6px;
  font-style: normal;
  font-size: 12px;
  color: #9ca3af;
}
.tab.active {
  color: #1d4ed8;
  border-bottom-color: #1d4ed8;
  font-weight: 600;
}

.panel { margin-top: 12px; }

.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}
.toolbar .count {
  margin-left: auto;
  font-size: 12px;
  color: #9ca3af;
}

.tip {
  margin-bottom: 10px;
  padding: 8px 12px;
  background: #f6f8fb;
  border-left: 3px solid #93a7c4;
  border-radius: 3px;
  font-size: 12.5px;
  color: #4b5563;
  line-height: 1.7;
}

.tbl {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  background: #fff;
}
.tbl th,
.tbl td {
  padding: 8px 10px;
  border-bottom: 1px solid #eceff3;
  text-align: left;
  vertical-align: middle;
}
.tbl th {
  background: #f7f9fc;
  font-weight: 600;
  color: #374151;
  white-space: nowrap;
}
.tbl tr.off { opacity: 0.55; }
.tbl .remark {
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #6b7280;
}
.tbl .empty {
  text-align: center;
  color: #9ca3af;
  padding: 28px 0;
}
code.small { font-size: 12px; }
code { font-family: Consolas, Monaco, monospace; }

.tag {
  display: inline-block;
  padding: 1px 7px;
  border-radius: 3px;
  font-size: 12px;
  background: #eef2f7;
  color: #55606e;
}
.tag.on { background: #e7f6ee; color: #1f7a4d; }
.tag.off { background: #fdeaea; color: #a33232; }
.tag.pend { background: #fff4e0; color: #96650f; }

.btn {
  padding: 6px 14px;
  border: 1px solid #d5dbe4;
  border-radius: 5px;
  background: #fff;
  color: #374151;
  font-size: 13px;
  cursor: pointer;
}
.btn:hover:not(:disabled) { border-color: #1d4ed8; color: #1d4ed8; }
.btn:disabled { opacity: 0.45; cursor: not-allowed; }
.btn-primary {
  background: #1d4ed8;
  border-color: #1d4ed8;
  color: #fff;
}
.btn-primary:hover:not(:disabled) { background: #1a45be; color: #fff; }
.btn-text {
  border: none;
  background: transparent;
  padding: 2px 6px;
  color: #1d4ed8;
}
.btn-text.danger { color: #c0392b; }

.tb-input {
  padding: 6px 8px;
  border: 1px solid #d5dbe4;
  border-radius: 5px;
  font-size: 13px;
  background: #fff;
  color: #1f2937;
}
.tb-input:focus { outline: none; border-color: #1d4ed8; }
.tb-input:disabled { background: #f4f6f9; color: #8a94a2; }
.tb-input.full { width: 100%; box-sizing: border-box; }
.tb-input.search { width: 240px; }

/* ---- 弹窗 ---- */
.modal-mask {
  position: fixed;
  inset: 0;
  background: rgba(17, 24, 39, 0.42);
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
  border-radius: 8px;
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.22);
}
.modal.wide { width: 1080px; }
.modal-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid #eceff3;
}
.modal-head h3 { margin: 0; font-size: 16px; }
.modal-close {
  border: none;
  background: transparent;
  font-size: 22px;
  line-height: 1;
  color: #9ca3af;
  cursor: pointer;
}
.modal-body { padding: 16px 18px; }
.modal-foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 18px;
  border-top: 1px solid #eceff3;
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
  color: #4b5563;
}
.req { color: #c0392b; }
.code {
  font-family: Consolas, Monaco, monospace;
  font-size: 12.5px;
  line-height: 1.6;
  resize: vertical;
}

.mode-bar {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 6px 0 12px;
  padding: 8px 12px;
  background: #f7f9fc;
  border-radius: 6px;
}
.modes { display: flex; gap: 0; flex-shrink: 0; }
.mode {
  padding: 5px 16px;
  border: 1px solid #d5dbe4;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
  color: #4b5563;
}
.mode:first-child { border-radius: 5px 0 0 5px; }
.mode:last-child { border-radius: 0 5px 5px 0; border-left: none; }
.mode.active { background: #1d4ed8; border-color: #1d4ed8; color: #fff; }
.mode-hint { margin: 0; font-size: 12px; color: #6b7280; }

.sect {
  margin: 12px 0;
  padding: 12px 14px;
  border: 1px solid #e8ecf2;
  border-radius: 6px;
  background: #fcfdff;
}
.sect-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.sect-head h4 { margin: 0; font-size: 13.5px; color: #1f2937; }
.sect-hint {
  margin: 4px 0 8px;
  font-size: 12px;
  color: #7b8794;
}
.sect-empty {
  margin: 4px 0 0;
  font-size: 12px;
  color: #9ca3af;
}
.cond {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.cond .op { width: 82px; flex-shrink: 0; text-align: center; }
.cond .grow { flex: 1; min-width: 0; }
.cond .tb-input:first-child { width: 210px; flex-shrink: 0; }

.chips { display: flex; flex-wrap: wrap; gap: 6px; }
.chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border: 1px solid #d5dbe4;
  border-radius: 14px;
  font-size: 12.5px;
  color: #4b5563;
  cursor: pointer;
  background: #fff;
}
.chip.on {
  border-color: #1d4ed8;
  background: #eef3ff;
  color: #1d4ed8;
}
.chip input { margin: 0; }

.result {
  margin-top: 12px;
  padding: 12px 14px;
  border: 1px solid #e8ecf2;
  border-radius: 6px;
  background: #fafbfd;
}
.result-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.dim { font-size: 12px; color: #8a94a2; }
.issues { margin: 0 0 8px; padding-left: 18px; font-size: 12.5px; line-height: 1.8; }
.issues .err { color: #c0392b; }
.issues .warn { color: #a26a12; }

.preview { margin-top: 8px; }
.mini {
  width: 100%;
  border-collapse: collapse;
  font-size: 12.5px;
  background: #fff;
}
.mini th,
.mini td {
  padding: 5px 10px;
  border: 1px solid #eceff3;
  text-align: left;
}
.mini th { background: #f7f9fc; font-weight: 600; }
.mini .strong { font-weight: 600; color: #1d4ed8; }

.sql-box { margin-top: 10px; }
.sql-box summary {
  cursor: pointer;
  font-size: 12.5px;
  color: #1d4ed8;
}
.sql,
pre.sql {
  margin: 8px 0 0;
  padding: 10px;
  background: #1f2937;
  color: #d7e0ea;
  border-radius: 5px;
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
  border-left: 1px solid #eceff3;
  padding-left: 16px;
}
.side-block { margin-bottom: 18px; }
.side-block h5 {
  margin: 0 0 6px;
  font-size: 13px;
  color: #1f2937;
}
.side-hint {
  margin: 0 0 6px;
  font-size: 12px;
  color: #7b8794;
}
.impact { margin: 0; padding-left: 16px; font-size: 12.5px; line-height: 1.9; color: #374151; }
.cols { display: flex; flex-wrap: wrap; gap: 4px; }
.col {
  padding: 1px 6px;
  background: #eef2f7;
  border-radius: 3px;
  font-size: 11.5px;
  color: #4b5563;
}
</style>
