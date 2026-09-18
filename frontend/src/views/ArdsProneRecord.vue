<template>
  <div class="ards-page ards-theme">
    <!-- ============================ 填写页 ============================ -->
    <div v-if="scr === 'form'">
      <div class="topbar">
        <div>
          <h1>ARDS 俯卧位通气治疗记录</h1>
          <div class="sub">适用于中重度 ARDS（PaO₂/FiO₂ &lt; 150 mmHg，PEEP ≥ 5 cmH₂O）俯卧位通气治疗监测</div>
        </div>
        <div class="spacer"></div>
        <div v-if="record" class="who">
          <b>{{ record.patientName || '—' }}</b>
          <span>{{ record.sex || '—' }} · {{ record.age || '—' }} · 住院号 {{ record.inHospitalNo }} · 床号 {{ record.bedCode || '—' }}</span>
          <span class="tag" :class="gradeClass(record.ardsGrade)">{{ record.ardsGrade || '未分级' }}</span>
          <span class="tag orange">{{ record.proneDay || '第 1 天' }} · 共 {{ record.proneTimes || 1 }} 次</span>
        </div>
        <button class="btn" @click="goList">历史记录</button>
        <button class="btn" @click="scr = 'print'">打印</button>
        <button class="btn" @click="onSave('draft')">保存草稿</button>
        <button class="btn primary" @click="onSave('submitted')">提交记录</button>
      </div>

      <!-- 患者基本信息 -->
      <div class="card">
        <div class="hd">
          <h2>患者基本信息</h2>
          <span class="hint">来自 HIS 自动带入，科室/住院号只读</span>
          <div class="spacer"></div>
          <span class="tag gray">记录编号 {{ record?.recordNo || '—' }}</span>
        </div>
        <div class="bd">
          <div class="grid">
            <div class="f"><label>姓名</label><div class="v"><b>{{ record?.patientName || '—' }}</b></div></div>
            <div class="f"><label>性别</label><div class="v">{{ record?.sex || '—' }}</div></div>
            <div class="f"><label>年龄</label><div class="v">{{ record?.age || '—' }}</div></div>
            <div class="f"><label>住院号</label><div class="v">{{ record?.inHospitalNo || '—' }}</div></div>
            <div class="f"><label>床号</label><div class="v">{{ record?.bedCode || '—' }}</div></div>
            <div v-if="apache2Show" class="f">
              <label>APACHE II</label>
              <input v-model="form.apache2Score" class="v edit" placeholder="—" />
            </div>
            <div class="f span2"><label>诊断</label><input v-model="form.diagnosis" class="v edit" placeholder="请填写诊断" /></div>
            <div class="f">
              <label>ARDS 分级</label>
              <div class="v">
                <div class="seg">
                  <span v-for="g in GRADES" :key="g" class="opt" :class="[gradeClass(g), { on: form.ardsGrade === g }]" @click="form.ardsGrade = g">{{ g }}</span>
                </div>
              </div>
            </div>
            <div class="f"><label>经管医师</label><StaffSearchInput v-model="form.attendingDoctor" placeholder="检索或输入经管医师" /></div>
            <div class="f" style="max-width:200px">
              <label>入院时间</label>
              <el-date-picker
                v-model="form.admitDate"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm"
                format="YYYY-MM-DD HH:mm"
                placeholder="选择入院时间"
                class="w-full"
              />
            </div>
            <div class="f" style="max-width:200px"><label>记录日期</label><div class="v">{{ record?.recordDate || '—' }}</div></div>
          </div>
        </div>
      </div>

      <!-- 本次治疗 -->
      <div class="card">
        <div class="hd">
          <h2>本次俯卧位治疗</h2>
          <span class="hint">时长由开始/结束时间自动计算</span>
          <div class="spacer"></div>
          <span class="tag" :class="recordStatusClass">{{ recordStatusText }}</span>
        </div>
        <div class="bd">
          <div class="timebar">
            <div class="seg-cell" style="max-width:220px">
              <div class="k">俯卧位开始</div>
              <el-date-picker
                v-model="form.startTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                format="YYYY-MM-DD HH:mm"
                placeholder="选择开始时间"
                class="w-full"
                :clearable="false"
              />
            </div>
            <div class="seg-cell" style="max-width:220px">
              <div class="k">俯卧位结束</div>
              <el-date-picker
                v-model="form.endTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                format="YYYY-MM-DD HH:mm"
                placeholder="未结束"
                class="w-full"
              />
            </div>
            <div class="seg-cell"><div class="k">持续时长</div><div class="v">{{ durationText }} <small>自动计算</small></div></div>
            <div class="seg-cell"><div class="k">当前时点</div><div class="v cur">{{ currentTp?.tpLabel || '—' }} <small>{{ currentTpPlan }}</small></div></div>
            <div class="seg-cell"><div class="k">已填参数</div><div class="v">{{ filledCount }} / {{ params.length }}</div></div>
            <div class="seg-cell">
              <div class="k">时点数</div>
              <div class="v">{{ timepoints.length }} <small @click="tpDrawer = true" class="link">配置</small></div>
            </div>
          </div>
        </div>
      </div>

      <!-- 监测记录 -->
      <div class="card">
        <div class="hd">
          <h2>监测记录</h2>
          <div class="tabs">
            <div class="t" :class="{ on: mode === 'table' }" @click="mode = 'table'">时点横表</div>
            <div class="t" :class="{ on: mode === 'single' }" @click="mode = 'single'">单时点录入</div>
          </div>
          <button class="btn" @click="openTp">时点配置 · {{ timepoints.length }} 个</button>
          <div class="spacer"></div>
          <div class="legend">
            <span><i class="ab"></i>超出内置参考区间</span>
            <span><i class="ca"></i>自动计算</span>
            <span><i class="em"></i>未填</span>
            <span class="src auto">自动采集</span>
            <span class="src lis">检验同步</span>
            <span class="src man">手工录入</span>
            <span class="src calc">系统计算</span>
          </div>
        </div>
        <div class="bd">
          <div class="sysbar">
            <span class="it">采集窗口：生命体征/呼吸机 ±15 min · 血气/检验 ±60 min；窗口内无数据 → 置空，手工录入</span>
            <span class="it">自动采集值修正须填原因，原值保留</span>
            <span class="spacer"></span>
            <button v-if="collectDetail" class="btn" @click="detailOpen = true">
              采集明细（命中 {{ collectDetail.filled }} 项）
            </button>
            <button class="btn" @click="onCollectAll">采集全部时点</button>
            <button class="btn">导出 Excel</button>
          </div>

          <!-- 模式一：时点横表 -->
          <div v-if="mode === 'table'">
            <div class="tbl-wrap">
              <table class="mon">
                <thead>
                  <tr>
                    <th class="c-cat">分类</th>
                    <th class="c-name">参数</th>
                    <th class="c-unit">单位</th>
                    <th v-for="tp in timepoints" :key="tp.tpIndex">
                      <div class="tp">{{ tp.tpLabel }}</div>
                      <div class="tm">{{ fmtPlan(tp.planTime) }}</div>
                      <div class="tm">
                        <span :class="['dot', tp.collectStatus === 'done' ? 'ok' : 'pend']"></span>
                        <span class="at" @click="onCollect(tp.tpIndex)" title="采集此时点">采集</span>
                      </div>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <template v-for="g in groups" :key="g.name">
                    <tr v-for="(p, pi) in g.items" :key="p.key">
                      <td v-if="pi === 0" class="c-cat" :rowspan="g.items.length">
                        <div class="cat-name">{{ g.name }}</div>
                      </td>
                      <td class="c-name">
                        {{ p.name }}
                        <span v-if="p.calc" class="calc">自动计算</span>
                      </td>
                      <td class="c-unit">{{ p.unit || '—' }}</td>
                      <td v-for="tp in timepoints" :key="tp.tpIndex"
                          class="cell"
                          :class="cellClass(p, tp.tpIndex)">
                        <template v-if="p.calc">
                          <span class="val">{{ cellText(tp.tpIndex, p.key) }}</span>
                        </template>
                        <template v-else>
                          <input class="val" v-model="ensureDraft(tp.tpIndex)[p.key]" :placeholder="'—'" @blur="onCellBlur(tp.tpIndex, p.key)" />
                          <span v-if="cellSource(tp.tpIndex, p.key)" class="src" :class="cellSource(tp.tpIndex, p.key)">{{ srcText(cellSource(tp.tpIndex, p.key)) }}</span>
                        </template>
                      </td>
                    </tr>
                  </template>
                </tbody>
              </table>
            </div>
          </div>

          <!-- 模式二：单时点录入 -->
          <div v-else>
            <div class="single-bar">
              <span class="hint">床旁/平板录入：一次只填一个时点，逐项纵排</span>
              <span class="spacer"></span>
              <button class="btn" @click="onCollect(currentTpIndex)">采集本时点</button>
              <button class="btn primary" @click="onSave('draft')">保存本时点</button>
            </div>
            <div class="tp-nav">
              <span v-for="tp in timepoints" :key="tp.tpIndex"
                    class="p"
                    :class="{ on: tp.tpIndex === currentTpIndex, ok: tp.collectStatus === 'done' }"
                    @click="currentTpIndex = tp.tpIndex">{{ tp.tpLabel }}</span>
            </div>
            <div class="single">
              <div v-for="g in groups" :key="g.name" class="grp">
                <div class="gh">{{ g.name }}</div>
                <div class="gl">
                  <div v-for="p in g.items" :key="p.key" :class="['f', g.items.length % 2 === 1 && g.items.indexOf(p) === g.items.length - 1 ? 'wide' : '']">
                    <label>{{ p.name }}{{ p.unit ? '（' + p.unit + '）' : '' }}</label>
                    <input v-if="!p.calc" class="v edit" v-model="ensureDraft(currentTpIndex)[p.key]" placeholder="—" @blur="onCellBlur(currentTpIndex, p.key)" />
                    <div v-else class="v calc">{{ cellText(currentTpIndex, p.key) }}</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 并发症 -->
      <div class="card">
        <div class="hd">
          <h2>并发症与事件记录</h2>
          <span class="hint">勾选后需在下方填写处理措施</span>
          <div class="spacer"></div>
          <span class="tag" :class="compList.length ? 'orange' : 'green'">
            {{ compList.length ? compList.length + ' 项' : '暂无并发症' }}
          </span>
        </div>
        <div class="bd">
          <div class="checks">
            <span v-for="c in COMPLICATIONS" :key="c"
                  class="ck"
                  :class="{ on: compList.includes(c) }"
                  @click="toggleComp(c)">
              <i class="box">✓</i>{{ c }}
            </span>
          </div>
          <div class="f" style="margin-top: 12px">
            <label>并发症详细描述及处理措施</label>
            <textarea v-model="form.complicationDesc" class="bx" placeholder="勾选并发症后填写处理措施"></textarea>
          </div>
        </div>
      </div>

      <!-- 终止指征 -->
      <div class="card">
        <div class="hd">
          <h2>停止 / 终止俯卧位指征评估</h2>
          <div class="spacer"></div>
          <span class="tag" :class="stopTagClass">{{ stopText }}</span>
        </div>
        <div class="bd">
          <div class="f" style="margin-bottom: 12px">
            <label>达标终止</label>
            <div class="checks">
              <span class="ck" :class="{ on: form.stopType === 'reach' }" @click="form.stopType = form.stopType === 'reach' ? 'none' : 'reach'">
                <i class="box">✓</i>PaO₂/FiO₂ 持续 &gt; 150 mmHg 且稳定 ≥ 4 小时
              </span>
              <span class="ck" :class="{ on: form.stopType === 'reach' }" @click="form.stopType = form.stopType === 'reach' ? 'none' : 'reach'">
                <i class="box">✓</i>临床综合评估改善
              </span>
            </div>
          </div>
          <div class="f" style="margin-bottom: 12px">
            <label>紧急终止</label>
            <div class="checks">
              <span v-for="e in EMERGENCY_STOPS" :key="e"
                    class="ck"
                    :class="{ on: emergencyList.includes(e) }"
                    @click="toggleEmergency(e)"><i class="box">✓</i>{{ e }}</span>
            </div>
          </div>
          <div class="f">
            <label>实际终止原因</label>
            <div class="v muted">{{ stopDetailText }}</div>
          </div>
        </div>
      </div>

      <!-- 备注与签名 -->
      <div class="card">
        <div class="hd">
          <h2>备注与签名</h2>
          <span class="hint">提交后如需更正：<b>不限时</b>，每次修改保留原值、记录修改人与原因</span>
          <div class="spacer"></div>
          <span class="tag gray">{{ record?.recordStatus === 'submitted' ? '已提交' : '未提交' }}</span>
        </div>
        <div class="bd">
          <div class="f" style="margin-bottom: 14px">
            <label>备注</label>
            <textarea v-model="form.remark" class="bx" placeholder="本次俯卧位治疗的补充说明"></textarea>
          </div>
          <div class="grid">
            <div class="f span2"><label>记录医师</label><StaffSearchInput v-model="form.doctorSign" placeholder="签名：检索后选中" /></div>
            <div class="f span2"><label>记录护士</label><StaffSearchInput v-model="form.nurseSign" placeholder="签名：检索后选中" /></div>
            <div class="f span2"><label>上级医师</label><StaffSearchInput v-model="form.seniorSign" placeholder="签名：检索后选中" /></div>
          </div>
        </div>
      </div>
    </div>

    <!-- ============================ 打印预览 ============================ -->
    <div v-else class="print-screen">
      <div class="filter">
        <span class="ipt">纸张：A4 横向</span>
        <span class="ipt">打印范围：{{ params.length }} 项 × {{ timepoints.length }} 时点</span>
        <span class="tag" :class="record?.archiveStatus === 1 ? 'green' : 'gray'">
          归档：{{ record?.archiveStatus === 1 ? '已回传' + (record?.archiveDocNo ? '（文档号 ' + record.archiveDocNo + '）' : '') : '未回传' }}
        </span>
        <button class="btn" @click="onArchive">{{ record?.archiveStatus === 1 ? '重新回传' : '归档回传' }}</button>
        <button class="btn" @click="downloadPdf">导出 PDF</button>
        <button class="btn primary" @click="onPrintAndArchive">打印并归档回传</button>
        <span class="spacer"></span>
        <span class="hint">APACHE II 由参数设置控制（当前：{{ apache2Show ? '显示' : '不显示' }}，全院统一）</span>
        <button class="btn" @click="scr = 'form'">返回填写</button>
      </div>
      <div class="sysbar" style="margin-bottom: 12px">
        <span class="it">真实 1:1 预览：A4 横向、所见即所得，纸面版式与打印一致</span>
        <span class="spacer"></span>
        <span class="it">归档接口：调用<b>「参数设置」中配置的现有归档接口</b>，与 APACHE II、SOFA 评分归档同一接口、传参一致</span>
      </div>
      <div ref="paperRef" class="paper">
        <div class="p-title">ARDS 俯卧位通气治疗记录单</div>
        <div class="p-sub">{{ record?.departCode || '重症医学科' }} · 记录编号 {{ record?.recordNo || '—' }}</div>

        <table class="p-info">
          <tr>
            <td class="k">姓名</td><td>{{ record?.patientName || '—' }}</td>
            <td class="k">性别</td><td>{{ record?.sex || '—' }}</td>
            <td class="k">年龄</td><td>{{ record?.age || '—' }}</td>
            <td class="k">床号</td><td>{{ record?.bedCode || '—' }}</td>
            <td class="k">住院号</td><td>{{ record?.inHospitalNo || '—' }}</td>
            <template v-if="apache2Show">
              <td class="k">APACHE II</td><td>{{ form.apache2Score || '—' }}</td>
            </template>
          </tr>
          <tr>
            <td class="k">诊断</td><td colspan="3">{{ form.diagnosis || '—' }}</td>
            <td class="k">ARDS 分级</td><td>{{ form.ardsGrade || '—' }}</td>
            <td class="k">疗程</td><td colspan="3">{{ record?.proneDay || '—' }} / 第 {{ record?.proneTimes || 1 }} 次</td>
          </tr>
          <tr>
            <td class="k">开始</td><td colspan="3">{{ fmtMinute(form.startTime) }}</td>
            <td class="k">结束</td><td colspan="3">{{ form.endTime ? fmtMinute(form.endTime) : '进行中' }}</td>
            <td class="k">持续</td><td colspan="2">{{ durationText }}</td>
          </tr>
        </table>

        <div class="p-sec">监测记录（{{ params.length }} 项 × {{ timepoints.length }} 时点）</div>
        <table class="p-mon">
          <thead>
            <tr>
              <th style="width: 24px">类别</th>
              <th style="width: 130px">参数</th>
              <th style="width: 52px">单位</th>
              <th v-for="tp in timepoints" :key="tp.tpIndex">{{ tp.tpLabel }}</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="g in groups" :key="g.name">
              <tr v-for="(p, pi) in g.items" :key="p.key">
                <td v-if="pi === 0" class="cat" :rowspan="g.items.length">
                  <div class="cat-v">{{ g.name }}</div>
                </td>
                <td class="l">{{ p.name }}</td>
                <td>{{ p.unit || '—' }}</td>
                <td v-for="tp in timepoints" :key="tp.tpIndex"
                    :class="{ calc: p.calc, abn: isAbnormal(p, tp.tpIndex) }">
                  {{ cellText(tp.tpIndex, p.key) }}
                </td>
              </tr>
            </template>
          </tbody>
        </table>

        <div class="p-sec">并发症与事件</div>
        <div class="p-line">{{ compList.length ? compList.join('、') : '无并发症' }}　{{ form.complicationDesc || '' }}</div>

        <div class="p-sec">终止指征</div>
        <div class="p-line">{{ stopDetailText }}</div>

        <div class="p-sec">备注</div>
        <div class="p-line">{{ form.remark || '—' }}</div>

        <div class="p-sign">
          <div>记录医师：{{ form.doctorSign || '—' }}</div>
          <div>记录护士：{{ form.nurseSign || '—' }}</div>
          <div>上级医师：{{ form.seniorSign || '—' }}</div>
        </div>
        <div class="p-foot">
          <span>记录编号：{{ record?.recordNo || '—' }}</span>
          <span>归档文档号：{{ record?.archiveDocNo || '—' }}</span>
          <span>打印人：{{ printUser }}</span>
          <span>打印时间：{{ printTime }}</span>
          <span>第 1 页 / 共 1 页</span>
        </div>
      </div>
    </div>

    <!-- ============================ 时点配置抽屉 ============================ -->
    <div v-if="tpDrawer" class="mask" @click.self="tpDrawer = false"></div>
    <div v-if="tpDrawer" class="drawer">
      <div class="dh">
        <h3>监测时点配置</h3>
        <span class="hint">默认 11 个时点；可按科室 / 患者增删</span>
        <div class="spacer"></div>
        <button class="btn" @click="tpDrawer = false">关闭</button>
      </div>
      <div class="db">
        <div class="win-note">
          <b>采集窗口</b>：生命体征 / 呼吸机参数 <b>±15 min</b>；血气 / 检验 <b>±60 min</b>。<br>
          <b>窗口内取最近一条</b> → 标「已采集」；<b>窗口内无数据</b> → 置空，由护士手工录入；<b>不沿用历史值</b>。
        </div>
        <table class="tp-table">
          <thead>
            <tr>
              <th style="width: 46px">序号</th><th>时点名称</th><th>相对开始偏移（分钟）</th>
              <th>计划时间</th><th>采集状态</th><th style="width: 110px">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(tp, i) in tpDraft" :key="i">
              <td class="n">{{ i }}</td>
              <td><input v-model="tp.tpLabel" class="mini" /></td>
              <td><input v-model.number="tp.offsetMinutes" class="mini" type="number" /></td>
              <td>{{ planOf(tp.offsetMinutes) }}</td>
              <td>{{ tp.collectStatus === 'done' ? '已采集' : '待采集' }}</td>
              <td>
                <button class="mini" @click="onTpRemove(i)">删除</button>
                <button class="mini" @click="onTpSave(i)">保存</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div class="tp-ops">
          <button class="btn primary" @click="onTpAdd">+ 新增时点</button>
          <button class="btn" @click="onTpReset">恢复默认时点</button>
          <button class="btn" @click="onTplSave">保存为科室模板</button>
        </div>
        <div class="notice">
          删除已填写的时点：<b>不物理删除数据</b>，仅标记为「已移除」，历史查询与统计仍可追溯；
          新增时点按「相对俯卧位开始的偏移」自动生成计划时间。
        </div>
      </div>
      <div class="df">
        <span class="hint">修改只作用于本条记录；科室默认模板另行维护</span>
        <div class="spacer"></div>
        <button class="btn" @click="tpDrawer = false">取消</button>
        <button class="btn primary" @click="tpDrawer = false">完成</button>
      </div>
    </div>

    <!-- 采集明细：每项实际取自哪个项目（配置映射 / 内置关键字） -->
    <div v-if="detailOpen && collectDetail" class="mask" @click.self="detailOpen = false">
      <div class="drawer">
        <div class="dh">
          <h3>采集明细</h3>
          <span class="hint" style="margin-left: 10px">
            {{ collectDetail.tpLabel || ('时点 ' + collectDetail.tpIndex) }} · 计划时间 {{ collectDetail.planTime }}
          </span>
          <span class="spacer"></span>
          <button class="btn" @click="detailOpen = false">关闭</button>
        </div>
        <div class="db">
          <div class="win-note">
            命中 <b>{{ collectDetail.filled }}</b> 项 · 窗口内无数据 <b>{{ collectDetail.pending }}</b> 项 ·
            已有值保留 <b>{{ collectDetail.kept || 0 }}</b> 项（取到监护记录 {{ collectDetail.observeCount }} 条 /
            检验记录 {{ collectDetail.labCount }} 条）。<br>
            来源「规则」= 走数据映射配置；「内置」= 回退字典内置关键字。映射不合适时到「ARDS 数据映射」页调整。
          </div>
          <table class="tp-table">
            <thead>
              <tr>
                <th>参数项</th>
                <th>取到的值</th>
                <th>来源</th>
                <th>命中方式</th>
                <th>来源项目</th>
                <th>项目编码</th>
                <th>源时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(it, i) in detailItems" :key="i">
                <td>{{ it.paramName }}</td>
                <td>{{ it.value || '—' }}</td>
                <td>{{ detailFrom(it) }}</td>
                <td>{{ it.matchType === 'code' ? '编码精确' : (it.matchType ? '名称包含' : '—') }}</td>
                <td>{{ it.itemName || '—' }}</td>
                <td>{{ it.itemCode || '—' }}</td>
                <td>{{ it.itemTime || '—' }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 修正原因弹窗 -->
    <div v-if="reasonBox.show" class="mask" @click.self="reasonBox.show = false">
      <div class="dialog">
        <div class="dh"><h3>修正自动采集值</h3></div>
        <div class="db">
          <div class="tip" style="margin-bottom: 10px">
            参数「{{ reasonBox.name }}」原值 <b>{{ reasonBox.old }}</b>（{{
              reasonBox.source === 'lis' ? '检验同步' : '自动采集' }}），
            修正为 <b>{{ reasonBox.now }}</b>。<br>
            原值与新值均保留，进入更正留痕。
          </div>
          <div class="f">
            <label>修正原因 <b class="req">*</b></label>
            <textarea v-model="reasonBox.reason" class="bx" placeholder="如：采集时刻与实际不符，以床旁监护仪读数为准"></textarea>
          </div>
        </div>
        <div class="df">
          <span class="spacer"></span>
          <button class="btn" @click="reasonBox.show = false">取消</button>
          <button class="btn primary" @click="confirmReason">确定修正</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import '../styles/ards-theme.css'
import { jsPDF } from 'jspdf'
import html2canvas from 'html2canvas'
import {
  fetchArdsProneRecord, fetchArdsProneDict, fetchArdsProneApache2Show,
  saveArdsProneRecord, saveArdsProneCells, collectArdsProneTp,
  addArdsProneTp, updateArdsProneTp, deleteArdsProneTp, resetArdsProneTp,
  saveArdsProneTpl, attachArdsPronePdf, pushArdsProneArchive, checkArdsPronePrint
} from '../api/ardsProne'
import StaffSearchInput from '../components/StaffSearchInput.vue'

const route = useRoute()
const router = useRouter()
const recordId = ref(route.query.id ? Number(route.query.id) : null)
const scr = ref(route.query.scr === 'print' ? 'print' : 'form')

const GRADES = ['轻度', '中度', '重度']

/** ARDS 分级配色：轻度绿 / 中度橙 / 重度红 / 未分级灰（与监测看板分级色一致） */
function gradeClass(g) {
  if (g === '轻度') return 'g-mild'
  if (g === '中度') return 'g-moderate'
  if (g === '重度') return 'g-severe'
  return 'g-unknown'
}
const COMPLICATIONS = ['无并发症', '插管移位/脱管', '皮肤压力性损伤', '眼部损伤', '血流动力学不稳定',
  '导管脱出', '反流误吸', '恶性心律失常', '痰液堵塞', '其他']
const EMERGENCY_STOPS = ['严重低血压/难治性休克', '气道出血/插管移位', '恶性心律失常',
  '颅内压升高', '皮肤严重受损', '氧合进行性下降']

const loading = ref(false)
const record = ref(null)
const params = ref([])
const timepoints = ref([])
const apache2Show = ref(true)
const mode = ref('table')
const currentTpIndex = ref(0)
const tpDrawer = ref(false)
const tpDraft = ref([])
const paperRef = ref(null)
/** 最近一次采集明细（哪个项目映射到哪个值），供「采集明细」抽屉核对 */
const collectDetail = ref(null)
const detailOpen = ref(false)

/** 单元格草稿值：draft[tpIndex][paramKey] = 文本值 */
const draft = ref({})
/** 单元格原始值快照：用于识别改动与来源 */
const origin = reactive({})

// 新增/删除时点后 timepoints 会整体替换，而 draft 只在 applyView 里按 cells 重建，
// 于是新时点没有对应的 draft[tpIndex]，模板里 draft[tp.tpIndex][p.key] 就会读 undefined 报错。
// 这里兜底补齐（已有的键不动，避免把用户正在编辑的值清掉）。
watch(timepoints, (list) => {
  ;(list || []).forEach(tp => {
    if (tp.tpIndex == null) return
    if (!draft.value[tp.tpIndex]) draft.value[tp.tpIndex] = {}
  })
  // 当前时点被删掉时回退到第一个时点，避免停在已不存在的 tpIndex 上
  if ((list || []).length && !(list || []).some(t => t.tpIndex === currentTpIndex.value)) {
    currentTpIndex.value = list[0].tpIndex
  }
}, { immediate: true })

const form = reactive({
  diagnosis: '', ardsGrade: '', attendingDoctor: '', admitDate: '', apache2Score: '',
  startTime: '', endTime: '', complicationDesc: '', remark: '',
  stopType: 'none', doctorSign: '', nurseSign: '', seniorSign: ''
})
const compList = ref([])
const emergencyList = ref([])

const reasonBox = reactive({ show: false, name: '', old: '', now: '', source: '', reason: '', pending: null })

onMounted(async () => {
  if (!recordId.value) {
    ElMessage.warning('缺少记录 ID，请从列表页进入')
    return
  }
  await Promise.all([loadDict(), loadRecord()])
})

async function loadDict() {
  try {
    const d = await fetchArdsProneDict()
    params.value = Array.isArray(d) ? d : []
  } catch (e) {
    console.error('参数字典加载失败', e)
  }
  try {
    apache2Show.value = await fetchArdsProneApache2Show()
  } catch (e) {
    apache2Show.value = true
  }
}

async function loadRecord() {
  loading.value = true
  try {
    const view = await fetchArdsProneRecord(recordId.value)
    if (!view || !view.record) {
      ElMessage.error('记录不存在')
      return
    }
    applyView(view)
  } catch (e) {
    console.error('记录加载失败', e)
  } finally {
    loading.value = false
  }
}

function applyView(view) {
  record.value = view.record
  timepoints.value = (view.timepoints || []).slice().sort((a, b) => (a.tpIndex || 0) - (b.tpIndex || 0))
  apache2Show.value = view.apache2Show !== undefined ? view.apache2Show : apache2Show.value

  const r = view.record
  Object.assign(form, {
    diagnosis: r.diagnosis || '', ardsGrade: r.ardsGrade || '', attendingDoctor: r.attendingDoctor || '',
    admitDate: r.admitDate ? (r.admitDate.length === 10 ? r.admitDate + ' 00:00' : r.admitDate) : '', apache2Score: r.apache2Score || '',
    startTime: fmtInput(r.startTime), endTime: fmtInput(r.endTime),
    complicationDesc: r.complicationDesc || '', remark: r.remark || '',
    stopType: r.stopType || 'none', doctorSign: r.doctorSign || '',
    nurseSign: r.nurseSign || '', seniorSign: r.seniorSign || ''
  })
  try {
    compList.value = r.complicationJson ? JSON.parse(r.complicationJson) : []
  } catch (e) {
    compList.value = []
  }
  if (r.stopDetail) {
    emergencyList.value = String(r.stopDetail).split('、').filter(Boolean)
  }

  // 单元格矩阵
  const d = {}
  timepoints.value.forEach(tp => { d[tp.tpIndex] = {} })
  ;(view.cells || []).forEach(c => {
    if (!d[c.tpIndex]) d[c.tpIndex] = {}
    d[c.tpIndex][c.paramKey] = c.value == null ? '' : String(c.value)
    origin[`${c.tpIndex}_${c.paramKey}`] = {
      value: c.value == null ? '' : String(c.value),
      source: c.source
    }
  })
  draft.value = d
}

// ---------------------------------------------------------------- 计算属性

const groups = computed(() => {
  const m = []
  params.value.forEach(p => {
    const g = p.group || '其他'
    let cur = m.find(x => x.name === g)
    if (!cur) { cur = { name: g, items: [] }; m.push(cur) }
    cur.items.push(p)
  })
  return m
})

const currentTp = computed(() => timepoints.value.find(t => t.tpIndex === currentTpIndex.value) || timepoints.value[0])
const currentTpPlan = computed(() => (currentTp.value ? fmtPlan(currentTp.value.planTime) : ''))

const durationText = computed(() => {
  const r = record.value
  if (r && r.durationMin != null) {
    const h = Math.floor(r.durationMin / 60)
    const m = r.durationMin % 60
    return h > 0 ? `${h}h${m ? ' ' + m + 'min' : ''}` : `${m}min`
  }
  if (form.startTime && form.endTime) {
    const diff = (new Date(form.endTime.replace(/-/g, '/')) - new Date(form.startTime.replace(/-/g, '/'))) / 60000
    if (diff > 0) {
      const h = Math.floor(diff / 60)
      const m = Math.round(diff % 60)
      return h > 0 ? `${h}h${m ? ' ' + m + 'min' : ''}` : `${m}min`
    }
  }
  return '待计算'
})

const filledCount = computed(() => {
  const d = draft.value[currentTpIndex.value] || {}
  return Object.keys(d).filter(k => d[k] !== '' && d[k] != null).length
})

const recordStatusText = computed(() => {
  if (!record.value) return '—'
  if (form.stopType === 'emergency') return '紧急终止'
  if (record.value.recordStatus === 'submitted') return '已提交'
  if (record.value.startTime && !record.value.endTime) return '进行中'
  return '草稿'
})
const recordStatusClass = computed(() => ({
  blue: recordStatusText.value === '进行中',
  green: recordStatusText.value === '已提交',
  red: recordStatusText.value === '紧急终止',
  gray: recordStatusText.value === '草稿'
}))

const stopText = computed(() => {
  if (form.stopType === 'emergency') return '紧急终止'
  if (form.stopType === 'reach') return '达标终止'
  return '未终止'
})
const stopTagClass = computed(() => (form.stopType === 'none' ? 'gray' : 'orange'))
const stopDetailText = computed(() => {
  if (form.stopType === 'emergency') return '紧急终止：' + (emergencyList.value.join('、') || '—')
  if (form.stopType === 'reach') return '达标终止：PaO₂/FiO₂ 持续 > 150 mmHg 且稳定 ≥ 4 小时'
  return '本次俯卧位尚未终止'
})

const printUser = computed(() => {
  const params2 = new URLSearchParams(window.location.search)
  return params2.get('realname') || sessionStorage.getItem('doctor_realname') || '—'
})
const printTime = ref('')

// ---------------------------------------------------------------- 单元格

/** 取（必要时创建）某个时点的草稿对象：模板 v-model 的下标访问需要它兜底 */
function ensureDraft(tpIndex) {
  if (tpIndex == null) return {}
  if (!draft.value[tpIndex]) draft.value[tpIndex] = {}
  return draft.value[tpIndex]
}

function cellText(tpIndex, paramKey) {
  const d = draft.value[tpIndex]
  const v = d ? d[paramKey] : ''
  return (v === '' || v == null) ? '—' : v
}
function cellSource(tpIndex, paramKey) {
  const o = origin[`${tpIndex}_${paramKey}`]
  return o ? o.source : ''
}
function srcText(s) {
  return { auto: '自动', lis: '检验', man: '手工', calc: '计算' }[s] || ''
}
/** 是否超出内置参考区间：仅视觉标红，不做告警、不阻断 */
function isAbnormal(p, tpIndex) {
  const v = draft.value[tpIndex] ? draft.value[tpIndex][p.key] : ''
  if (v === '' || v == null || isNaN(Number(v))) return false
  const n = Number(v)
  if (p.refLow != null && n < Number(p.refLow)) return true
  return p.refHigh != null && n > Number(p.refHigh)
}
function cellClass(p, tpIndex) {
  const cls = []
  if (p.calc) cls.push('calc')
  const d = draft.value[tpIndex]
  if (!d || d[p.key] === '' || d[p.key] == null) cls.push('empty')
  if (!p.calc && isAbnormal(p, tpIndex)) cls.push('abn')
  return cls
}

// ---------------------------------------------------------------- 保存

function toggleComp(c) {
  if (c === '无并发症') {
    compList.value = compList.value.includes(c) ? [] : [c]
    return
  }
  const i = compList.value.indexOf(c)
  if (i >= 0) compList.value.splice(i, 1)
  else {
    compList.value = compList.value.filter(x => x !== '无并发症')
    compList.value.push(c)
  }
}
function toggleEmergency(e) {
  const i = emergencyList.value.indexOf(e)
  if (i >= 0) emergencyList.value.splice(i, 1)
  else emergencyList.value.push(e)
  if (emergencyList.value.length) form.stopType = 'emergency'
  else if (form.stopType === 'emergency') form.stopType = 'none'
}

function collectChanges() {
  const changes = []
  timepoints.value.forEach(tp => {
    params.value.forEach(p => {
      if (p.calc) return
      const d = draft.value[tp.tpIndex] || {}
      const now = d[p.key] == null ? '' : String(d[p.key]).trim()
      const o = origin[`${tp.tpIndex}_${p.key}`]
      const old = o ? (o.value || '') : ''
      if (now === old) return
      changes.push({ tpIndex: tp.tpIndex, paramKey: p.key, value: now, old, source: o ? o.source : null, name: p.name })
    })
  })
  return changes
}

// 单元格失去焦点时自动保存当前单元格
async function onCellBlur(tpIndex, paramKey) {
  if (!record.value) return
  const d = draft.value[tpIndex] || {}
  const now = d[paramKey] == null ? '' : String(d[paramKey]).trim()
  const o = origin[tpIndex + "_" + paramKey]
  const old = o ? (o.value || '') : ''
  if (now === old) return
  const p = params.value.find(x => x.key === paramKey)
  const change = { tpIndex, paramKey, value: now, old, source: o ? o.source : null, name: p ? p.name : paramKey }
  if (change.source === 'auto' || change.source === 'lis') {
    const ok = await askReason(change)
    if (!ok) return
    change.reason = reasonBox.reason
  }
  try {
    await saveArdsProneCells(recordId.value, [{
      tpIndex: change.tpIndex, paramKey: change.paramKey, value: change.value, reason: change.reason || ''
    }])
    origin[tpIndex + "_" + paramKey] = { value: now, source: 'man' }
    ElMessage.success('已保存')
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || '请稍后重试'))
  }
}

async function onSave(status) {
  if (!record.value) return
  const changes = collectChanges()
  let pending = changes
  // 覆盖自动采集 / 检验同步值必须填原因：逐个弹窗收集
  const needReason = changes.filter(c => c.source === 'auto' || c.source === 'lis')
  if (needReason.length) {
    for (const c of needReason) {
      const ok = await askReason(c)
      if (!ok) return
      c.reason = reasonBox.reason
    }
  }
  try {
    const body = Object.assign({}, record.value, {
      diagnosis: form.diagnosis, ardsGrade: form.ardsGrade, attendingDoctor: form.attendingDoctor,
      admitDate: form.admitDate, apache2Score: form.apache2Score,
      startTime: toFullTime(form.startTime), endTime: toFullTime(form.endTime),
      complicationJson: JSON.stringify(compList.value), complicationDesc: form.complicationDesc,
      stopType: form.stopType,
      stopDetail: form.stopType === 'emergency' ? emergencyList.value.join('、') : (form.stopType === 'reach' ? '达标终止' : ''),
      remark: form.remark, doctorSign: form.doctorSign, nurseSign: form.nurseSign, seniorSign: form.seniorSign,
      recordStatus: status || record.value.recordStatus
    })
    const saved = await saveArdsProneRecord(body)
    if (pending.length) {
      await saveArdsProneCells(recordId.value, pending.map(c => ({
        tpIndex: c.tpIndex, paramKey: c.paramKey, value: c.value, reason: c.reason || ''
      })))
    }
    await loadRecord()
    ElMessage.success(status === 'submitted' ? '已提交' : '已保存')
    if (saved && saved.id) record.value = saved
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || '请稍后重试'))
  }
}

function askReason(c) {
  return new Promise(resolve => {
    reasonBox.name = c.name
    reasonBox.old = c.old || '空'
    reasonBox.now = c.value || '空'
    reasonBox.source = c.source
    reasonBox.reason = ''
    reasonBox.show = true
    reasonBox.pending = resolve
  })
}
function confirmReason() {
  if (!reasonBox.reason || !reasonBox.reason.trim()) {
    ElMessage.warning('请填写修正原因')
    return
  }
  reasonBox.show = false
  if (reasonBox.pending) {
    const r = reasonBox.pending
    reasonBox.pending = null
    r(true)
  }
}

async function onCollect(tpIndex) {
  try {
    const res = await collectArdsProneTp(recordId.value, tpIndex, true)
    const tp = timepoints.value.find(t => t.tpIndex === tpIndex)
    collectDetail.value = { ...res, tpIndex, tpLabel: tp ? tp.tpLabel : '' }
    await loadRecord()
    ElMessage.success(`采集完成：自动填充 ${res.filled} 项，${res.pending} 项窗口内无数据需手工录入`)
  } catch (e) {
    ElMessage.error('采集失败：' + (e.message || '请稍后重试'))
  }
}

async function onCollectAll() {
  try {
    const merged = new Map()
    let last = null
    for (const tp of timepoints.value) {
      const res = await collectArdsProneTp(recordId.value, tp.tpIndex, true)
      last = res
      ;(res.items || []).forEach(it => {
        const prev = merged.get(it.paramKey)
        // 同一参数取第一个“有命中”的时点作为代表，便于整体核对映射
        if (!prev || (prev.from === 'miss' && it.from !== 'miss')) {
          merged.set(it.paramKey, { ...it, tpIndex: tp.tpIndex, tpLabel: tp.tpLabel })
        }
      })
    }
    if (last) {
      collectDetail.value = {
        tpIndex: null,
        tpLabel: '全部时点（按参数取首个命中时点）',
        planTime: last.planTime,
        filled: merged.size,
        pending: 0,
        kept: 0,
        observeCount: last.observeCount,
        labCount: last.labCount,
        items: Array.from(merged.values())
      }
    }
    await loadRecord()
    ElMessage.success('全部时点采集完成')
  } catch (e) {
    ElMessage.error('采集失败：' + (e.message || '请稍后重试'))
  }
}

/** 采集明细行：参数项维度展示 */
const detailItems = computed(() => (collectDetail.value && collectDetail.value.items) || [])

function detailFrom(it) {
  if (it.from === 'rule') return '规则'
  if (it.from === 'builtin') return '内置关键字'
  if (it.from === 'keep') return '已有值保留'
  return '无数据'
}

// ---------------------------------------------------------------- 时点配置

function openTp() {
  tpDraft.value = timepoints.value.map(t => ({
    id: t.id, tpIndex: t.tpIndex, tpLabel: t.tpLabel,
    offsetMinutes: t.offsetMinutes, collectStatus: t.collectStatus
  }))
  tpDrawer.value = true
}
function planOf(offset) {
  if (!form.startTime) return '—'
  const base = new Date(form.startTime.replace(/-/g, '/')).getTime()
  if (isNaN(base)) return '—'
  const d = new Date(base + Number(offset || 0) * 60000)
  const p = n => String(n).padStart(2, '0')
  return `${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
async function onTpSave(i) {
  const t = tpDraft.value[i]
  if (!t || !t.id) return
  try {
    const list = await updateArdsProneTp(t.id, t.tpLabel, t.offsetMinutes)
    timepoints.value = list
    ElMessage.success('时点已保存')
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || '请稍后重试'))
  }
}
async function onTpAdd() {
  try {
    const last = tpDraft.value[tpDraft.value.length - 1]
    const offset = last ? Number(last.offsetMinutes || 0) + 120 : 0
    timepoints.value = await addArdsProneTp(recordId.value, '新时点', offset)
    openTp()
  } catch (e) {
    ElMessage.error('新增失败：' + (e.message || '请稍后重试'))
  }
}
async function onTpRemove(i) {
  const t = tpDraft.value[i]
  if (!t || !t.id) { tpDraft.value.splice(i, 1); return }
  try {
    await ElMessageBox.confirm('删除后该时点已填数据不再显示（历史数据保留可追溯），确定删除？', '删除时点', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning', customClass: 'ards-overlay'
    })
  } catch (e) { return }
  try {
    timepoints.value = await deleteArdsProneTp(t.id)
    openTp()
  } catch (e) {
    ElMessage.error('删除失败：' + (e.message || '请稍后重试'))
  }
}
async function onTpReset() {
  try {
    await ElMessageBox.confirm('恢复默认时点将重建本记录的时点，已填数据保留可查，确定继续？', '恢复默认时点', {
      confirmButtonText: '恢复', cancelButtonText: '取消', type: 'warning', customClass: 'ards-overlay'
    })
  } catch (e) { return }
  try {
    timepoints.value = await resetArdsProneTp(recordId.value)
    openTp()
    ElMessage.success('已恢复默认时点')
  } catch (e) {
    ElMessage.error('恢复失败：' + (e.message || '请稍后重试'))
  }
}
async function onTplSave() {
  try {
    await saveArdsProneTpl(record.value?.departCode || '', tpDraft.value.map((t, i) => ({
      tpIndex: i, tpLabel: t.tpLabel, offsetMinutes: t.offsetMinutes
    })))
    ElMessage.success('已保存为科室模板')
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || '请稍后重试'))
  }
}

// ---------------------------------------------------------------- 打印与归档

/**
 * 时间回填：统一补成 yyyy-MM-dd HH:mm:ss。
 * 时间选择器按 value-format 解析 v-model，缺秒会解析失败导致显示空白，故这里补齐。
 */
/** 展示到分钟：yyyy-MM-dd HH:mm（入院日期 / 记录日期 / 文书起止时间统一口径） */
function fmtMinute(v) {
  if (!v) return '—'
  const s = String(v).trim().replace('T', ' ')
  return s.length > 16 ? s.slice(0, 16) : s
}

function fmtInput(v) {
  if (!v) return ''
  const s = String(v).trim().replace('T', ' ')
  if (s.length === 16) return s + ':00'
  return s.length > 19 ? s.slice(0, 19) : s
}
function fmtPlan(v) {
  if (!v) return '—'
  return String(v).slice(5, 16)
}

// 保存时补全秒：前端显示到分钟，后端 @JsonFormat 要求 yyyy-MM-dd HH:mm:ss
function toFullTime(v) {
  if (!v) return null
  const s = String(v).trim()
  if (s.length === 16) return s + ':00'
  return s
}

async function buildPdf() {
  await nextTick()
  const el = paperRef.value
  if (!el) throw new Error('文书未渲染')
  const canvas = await html2canvas(el, { scale: 1.5, useCORS: true, backgroundColor: '#ffffff', logging: false })
  const pdf = new jsPDF('l', 'mm', 'a4')
  const pageW = 297
  const pageH = 210
  const margin = 4
  const maxW = pageW - margin * 2
  const maxH = pageH - margin * 2
  let imgW = maxW
  let imgH = canvas.height * imgW / canvas.width
  if (imgH > maxH) {
    imgH = maxH
    imgW = canvas.width * imgH / canvas.height
  }
  pdf.addImage(canvas.toDataURL('image/jpeg', 0.85), 'JPEG', (pageW - imgW) / 2, margin, imgW, imgH)
  return pdf
}

async function buildPdfBase64() {
  const pdf = await Promise.race([
    buildPdf(),
    new Promise((_, reject) => setTimeout(() => reject(new Error('文书渲染超时(15s)')), 15000))
  ])
  const uri = pdf.output('datauristring')
  return uri.includes(',') ? uri.split(',')[1] : uri
}

function fileName() {
  const no = record.value?.inHospitalNo || ''
  // recordDate 现为 yyyy-MM-dd HH:mm，文件名只取日期部分
  const day = (record.value?.recordDate || '').slice(0, 10).replace(/-/g, '')
  return `ARDS俯卧位通气治疗记录_${no}_${day}.pdf`
}

async function downloadPdf() {
  try {
    const pdf = await buildPdf()
    pdf.save(fileName())
  } catch (e) {
    ElMessage.error('导出 PDF 失败：' + (e.message || '请稍后重试'))
  }
}

function doPrint() {
  const el = paperRef.value
  if (!el) return
  const w = window.open('', '_blank')
  if (!w) { ElMessage.warning('浏览器拦截了打印窗口，请允许弹窗'); return }
  w.document.write(`<!DOCTYPE html><html><head><meta charset="utf-8"><title>${fileName()}</title>
  <style>
    body{margin:0;background:#fff;font-family:'SimSun','宋体',sans-serif;color:#000}
    .paper{width:277mm;margin:0 auto;padding:4mm 0}
    @page{size:A4 landscape;margin:8mm}
    table{border-collapse:collapse;width:100%}
  </style></head><body>${el.outerHTML}</body></html>`)
  w.document.close()
  w.focus()
  setTimeout(() => { w.print() }, 350)
}

/** 打印并归档回传：先校验必填 → 生成 PDF 入库 → 调归档接口（与 APACHE II、SOFA 同一接口） */
async function onPrintAndArchive() {
  printTime.value = nowText()
  try {
    const chk = await checkArdsPronePrint(recordId.value)
    if (chk && chk.ok === false) {
      await ElMessageBox.alert('以下必填项缺失，无法打印归档：\n' + (chk.missing || []).join('、'), '缺项', { type: 'warning', customClass: 'ards-overlay' })
      return
    }
  } catch (e) {
    console.error('打印校验失败', e)
  }
  try {
    await onSave(record.value?.recordStatus || 'draft')
    const b64 = await buildPdfBase64()
    await attachArdsPronePdf(recordId.value, b64, fileName())
    doPrint()
    const res = await pushArdsProneArchive(recordId.value)
    if (res && res.idempotent) {
      ElMessage.info('该记录已归档，未重复推送')
    } else {
      ElMessage.success('打印并归档回传成功')
    }
    await loadRecord()
  } catch (e) {
    ElMessage.error('归档回传失败：' + (e.message || '请稍后重试'))
  }
}

async function onArchive() {
  printTime.value = nowText()
  try {
    const b64 = await buildPdfBase64()
    await attachArdsPronePdf(recordId.value, b64, fileName())
    const res = await pushArdsProneArchive(recordId.value)
    if (res && res.idempotent) ElMessage.info('该记录已归档，未重复推送')
    else ElMessage.success('归档回传成功' + (res && res.docNo ? '，文档号 ' + res.docNo : ''))
    await loadRecord()
  } catch (e) {
    ElMessage.error('归档回传失败：' + (e.message || '请稍后重试'))
  }
}

function nowText() {
  const d = new Date()
  const p = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

function goList() {
  router.push({ path: '/page/ards-prone-list', query: { inHospitalNo: record.value?.inHospitalNo || '' } })
}
</script>

<style scoped>
.ards-page {
  max-width: 1720px; margin: 0 auto; padding: 16px 20px 60px;
  color: var(--el-text-color-primary);
}
* { box-sizing: border-box; }
.spacer { flex: 1; }
.hint { font-size: 12px; color: var(--el-text-color-placeholder); }

/* 顶部条 */
.topbar {
  display: flex; align-items: center; gap: 12px; background: #fff;
  border: 1px solid var(--el-border-color); border-radius: var(--ards-card-radius);
  padding: 16px 20px; margin-bottom: 14px; flex-wrap: wrap; box-shadow: var(--ards-card-shadow);
}
.topbar h1 { font-size: 20px; font-weight: 600; margin: 0; color: var(--ards-title-color); }
.topbar .sub { font-size: 12px; color: var(--el-text-color-placeholder); margin-top: 4px; }
.topbar .who {
  display: flex; align-items: center; gap: 10px; border: 1px solid var(--el-border-color);
  border-radius: var(--ards-ctl-radius); padding: 6px 12px; background: var(--el-fill-color-light);
}
.topbar .who b { font-size: 15px; color: var(--el-text-color-primary); }
.topbar .who span { color: var(--el-text-color-secondary); font-size: 12px; }

/* 按钮 */
.btn {
  border: 1px solid var(--el-border-color); background: #fff; color: var(--el-text-color-regular);
  border-radius: var(--ards-ctl-radius); padding: 7px 14px; font-size: 13px; font-weight: 500;
  cursor: pointer; transition: background .15s ease, border-color .15s ease, color .15s ease;
}
.btn:hover {
  color: var(--el-color-primary); border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}
.btn.primary { background: var(--el-color-primary); border-color: var(--el-color-primary); color: #fff; }
.btn.primary:hover {
  background: var(--el-color-primary-dark-2); border-color: var(--el-color-primary-dark-2); color: #fff;
}

/* 标签：胶囊 + 柔和底色 */
.tag {
  display: inline-block; padding: 2px 10px; border-radius: 9999px; font-size: 12px;
  line-height: 18px; border: 1px solid transparent; font-weight: 500;
}
.tag.blue { background: var(--ards-accent-wash); color: var(--ards-accent-dark); border-color: var(--ards-accent-soft); }
.tag.green { background: #dcfce7; color: #16a34a; border-color: #bbf7d0; }
.tag.orange { background: #ffedd5; color: #c2410c; border-color: #fed7aa; }
.tag.red { background: #fee2e2; color: #dc2626; border-color: #fecaca; }
.tag.gray { background: #f5f5f4; color: #78716c; border-color: #e7e5e4; }

/* 病情分级配色：轻度绿 / 中度橙 / 重度红 / 未分级灰，望色知轻重 */
.tag.g-mild { background: var(--ards-grade-mild-bg); color: var(--ards-grade-mild); border-color: var(--ards-grade-mild-bd); }
.tag.g-moderate { background: var(--ards-grade-moderate-bg); color: var(--ards-grade-moderate); border-color: var(--ards-grade-moderate-bd); }
.tag.g-severe { background: var(--ards-grade-severe-bg); color: var(--ards-grade-severe); border-color: var(--ards-grade-severe-bd); }
.tag.g-unknown { background: var(--ards-grade-unknown-bg); color: var(--ards-grade-unknown); border-color: var(--ards-grade-unknown-bd); }

/* 卡片 */
.card {
  background: #fff; border: 1px solid var(--el-border-color);
  border-radius: var(--ards-card-radius); margin-bottom: 14px; overflow: hidden;
  box-shadow: var(--ards-card-shadow);
}
.card > .hd {
  display: flex; align-items: center; gap: 12px; padding: 14px 18px;
  border-bottom: 1px solid var(--el-border-color-lighter); background: #fff; flex-wrap: wrap;
}
.card > .hd h2 {
  font-size: 15px; font-weight: 600; margin: 0; padding-left: 10px; color: var(--ards-title-color);
  border-left: 3px solid var(--el-color-primary); line-height: 16px;
}
.card > .bd { padding: 14px 18px; }

/* 信息栅格 */
.grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 12px 16px; }
.f { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.f.span2 { grid-column: span 2; }
.f > label { font-size: 12px; color: var(--el-text-color-secondary); }
.f > .v {
  border: 1px solid var(--el-border-color); border-radius: var(--ards-ctl-radius);
  padding: 6px 10px; min-height: 34px; display: flex; align-items: center;
  background: #fff; font-size: 13px; color: var(--el-text-color-regular);
}
.f > .v.muted { color: var(--el-text-color-disabled); }
.v.edit {
  width: 100%; height: 34px; font: 13px/1.5 inherit; color: var(--el-text-color-primary);
  outline: none; transition: border-color .15s ease, box-shadow .15s ease;
}
.v.edit:focus { border-color: var(--el-color-primary); box-shadow: 0 0 0 3px var(--el-color-primary-light-8); }
.seg { display: flex; gap: 8px; align-items: center; }
.seg .opt {
  border: 1px solid var(--el-border-color); border-radius: 9999px; padding: 4px 14px;
  font-size: 13px; color: var(--el-text-color-secondary); background: #fff; cursor: pointer;
  transition: background .15s ease, border-color .15s ease, color .15s ease;
}
.seg .opt:hover { color: var(--el-color-primary); border-color: var(--el-color-primary-light-5); }
.seg .opt.on {
  background: var(--el-color-primary-light-8); border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary-dark-2); font-weight: 600;
}
/* 分级选中态同分级色，避免「选了重度却显示成主色」造成误读 */
.seg .opt.g-mild.on {
  background: var(--ards-grade-mild-bg); border-color: var(--ards-grade-mild-bd);
  color: var(--ards-grade-mild); font-weight: 600;
}
.seg .opt.g-moderate.on {
  background: var(--ards-grade-moderate-bg); border-color: var(--ards-grade-moderate-bd);
  color: var(--ards-grade-moderate); font-weight: 600;
}
.seg .opt.g-severe.on {
  background: var(--ards-grade-severe-bg); border-color: var(--ards-grade-severe-bd);
  color: var(--ards-grade-severe); font-weight: 600;
}
.seg .opt.g-unknown.on {
  background: var(--ards-grade-unknown-bg); border-color: var(--ards-grade-unknown-bd);
  color: var(--ards-grade-unknown); font-weight: 600;
}

/* 时间条 */
.timebar { display: flex; align-items: stretch; flex-wrap: wrap; }
.timebar .seg-cell { flex: 1; min-width: 150px; padding: 0 16px; border-right: 1px solid var(--el-border-color-lighter); }
.timebar .seg-cell .k { font-size: 12px; color: var(--el-text-color-placeholder); margin-bottom: 4px; }
.timebar .seg-cell .v { font-size: 16px; font-weight: 600; color: var(--el-text-color-primary); }
.timebar .seg-cell .v small { font-size: 12px; font-weight: 400; color: var(--el-text-color-placeholder); margin-left: 6px; }
/* 本次俯卧位治疗输入框：与患者基本信息输入框样式一致（覆盖 .timebar .seg-cell .v 的 16px bold） */
.timebar .seg-cell .v.edit {
  border: 1px solid var(--el-border-color); border-radius: var(--ards-ctl-radius);
  padding: 6px 10px; background: #fff;
  font-size: 13px; font-weight: 400; color: var(--el-text-color-regular);
}
/* 当前时点：ARDS 识别色（青蓝） */
.timebar .seg-cell .v.cur { color: var(--ards-accent-dark); }
.link { color: var(--el-color-primary); cursor: pointer; }
.link:hover { color: var(--el-color-primary-dark-2); }

/* tabs / 图例 */
.tabs { display: flex; gap: 4px; background: var(--el-fill-color); padding: 3px; border-radius: 8px; }
.tabs .t {
  padding: 5px 14px; font-size: 13px; border-radius: 6px; color: var(--el-text-color-secondary);
  cursor: pointer; transition: color .15s ease, background .15s ease;
}
.tabs .t:hover { color: var(--el-color-primary); }
.tabs .t.on {
  background: #fff; color: var(--el-color-primary-dark-2); font-weight: 600;
  box-shadow: var(--ards-card-shadow);
}
.legend { display: flex; gap: 12px; align-items: center; font-size: 12px; color: var(--el-text-color-placeholder); flex-wrap: wrap; }
.legend i {
  display: inline-block; width: 12px; height: 12px; border-radius: 3px; margin-right: 4px;
  vertical-align: -2px; border: 1px solid;
}
.legend .ab { background: #fee2e2; border-color: #fecaca; }
.legend .ca { background: var(--el-fill-color); border-color: var(--el-border-color); }
.legend .em { background: #fff; border-color: var(--el-border-color); border-style: dashed; }

/* 来源角标 */
.src {
  display: inline-block; font-size: 10px; line-height: 14px; padding: 0 4px; border-radius: 3px;
  border: 1px solid; font-weight: 400;
}
.src.auto { color: var(--ards-accent-dark); background: var(--ards-accent-wash); border-color: var(--ards-accent-soft); }
.src.lis { color: #6d28d9; background: #ede9fe; border-color: #ddd6fe; }
.src.man { color: #b45309; background: #fef3c7; border-color: #fde68a; }
.src.calc { color: #78716c; background: #f5f5f4; border-color: #e7e5e4; }

.sysbar {
  display: flex; align-items: center; gap: 16px; flex-wrap: wrap; background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter); border-radius: var(--ards-ctl-radius);
  padding: 8px 12px; margin-bottom: 12px; font-size: 12px; color: var(--el-text-color-regular);
}
.sysbar .it { display: flex; align-items: center; gap: 6px; }
.dot { display: inline-block; width: 7px; height: 7px; border-radius: 50%; }
.dot.ok { background: var(--el-color-success); }
.dot.pend { background: var(--el-text-color-disabled); }

/* 监测横表 */
.tbl-wrap {
  overflow-x: auto; max-width: 100%; border: 1px solid var(--el-border-color);
  border-radius: var(--ards-ctl-radius);
}
table.mon { border-collapse: separate; border-spacing: 0; width: max-content; min-width: 100%; font-size: 13px; }
table.mon th, table.mon td {
  border-right: 1px solid var(--el-border-color-lighter); border-bottom: 1px solid var(--el-border-color-lighter);
  padding: 0; height: 36px; text-align: center; background: #fff;
}
table.mon thead th {
  background: var(--el-table-header-bg-color); font-weight: 600; position: sticky; top: 0;
  z-index: 3; height: 54px; color: var(--el-table-header-text-color);
}
table.mon thead th .tp { font-size: 13px; }
table.mon thead th .tm { font-size: 11px; color: var(--el-text-color-placeholder); font-weight: 400; }
table.mon thead th.now { background: var(--el-color-primary-light-8); }
table.mon thead th .tm .at { color: var(--el-color-primary); cursor: pointer; }

table.mon .c-cat, table.mon .c-name, table.mon .c-unit { text-align: left; }
table.mon .c-cat { position: sticky; left: 0; z-index: 2; width: 76px; background: var(--el-table-header-bg-color); }
table.mon .c-name { position: sticky; left: 76px; z-index: 2; width: 176px; background: #fff; }
table.mon .c-unit { position: sticky; left: 252px; z-index: 2; width: 74px; background: #fff; color: var(--el-text-color-placeholder); font-size: 12px; }
table.mon thead .c-cat, table.mon thead .c-name, table.mon thead .c-unit { z-index: 5; background: var(--el-table-header-bg-color); }
table.mon td.c-cat { vertical-align: middle; }
table.mon td.c-cat .cat-name {
  writing-mode: vertical-rl; letter-spacing: 4px; margin: 0 auto; font-size: 13px; font-weight: 600;
  color: var(--el-text-color-secondary); white-space: nowrap;
  border-left: 3px solid var(--el-color-primary); padding: 4px 0;
}
table.mon td.c-name { padding-left: 10px; color: var(--el-text-color-regular); }
table.mon td.c-name .calc { color: var(--el-text-color-placeholder); font-size: 11px; margin-left: 4px; }
table.mon td.cell { position: relative; min-width: 92px; }
table.mon td.cell input.val {
  width: 100%; height: 34px; border: none; outline: none; text-align: center;
  font: 13px/1.5 inherit; background: transparent; color: inherit;
}
table.mon td.cell .val { display: block; padding: 7px 6px; }
table.mon td.cell.calc { background: var(--el-fill-color-light); color: var(--el-text-color-secondary); }
table.mon td.cell.abn { background: #fee2e2; color: var(--el-color-danger); font-weight: 600; }
table.mon td.cell.empty input.val { color: var(--el-text-color-disabled); }
table.mon td.cell.now { box-shadow: inset 0 0 0 2px var(--el-color-primary); }
table.mon td.cell .src { position: absolute; top: 1px; right: 2px; }
table.mon tbody tr:hover td { background: var(--el-table-row-hover-bg-color); }
table.mon tbody tr:hover td.calc { background: var(--el-color-primary-light-9); }
table.mon tbody tr:hover td.abn { background: #fecaca; }

/* 单时点录入 */
.single-bar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.tp-nav { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 14px; }
.tp-nav .p {
  border: 1px solid var(--el-border-color); background: #fff; border-radius: 9999px;
  padding: 5px 14px; font-size: 13px; color: var(--el-text-color-secondary); cursor: pointer;
  transition: background .15s ease, border-color .15s ease, color .15s ease;
}
.tp-nav .p:hover { color: var(--el-color-primary); border-color: var(--el-color-primary-light-5); }
.tp-nav .p.on { background: var(--el-color-primary); border-color: var(--el-color-primary); color: #fff; font-weight: 600; }
.tp-nav .p.ok { border-color: var(--el-color-success-light-7); }
.single { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 16px; }
.single .grp { border: 1px solid var(--el-border-color-lighter); border-radius: var(--ards-card-radius); overflow: hidden; }
.single .grp .gh {
  background: var(--el-fill-color-light); padding: 10px 12px; font-weight: 600; font-size: 13px;
  color: var(--el-text-color-secondary); border-bottom: 1px solid var(--el-border-color-lighter);
  border-left: 3px solid var(--el-color-primary);
}
.single .grp .gl { padding: 12px; display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.single .grp .gl .f.wide { grid-column: span 2; }
.v.calc { background: var(--el-fill-color-light); }

/* 勾选区 */
.checks { display: flex; flex-wrap: wrap; gap: 10px 22px; }
.checks .ck { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--el-text-color-regular); cursor: pointer; }
.checks .ck .box {
  width: 15px; height: 15px; border: 1px solid var(--el-border-color-dark); border-radius: 4px;
  background: #fff; display: inline-flex; align-items: center; justify-content: center;
  font-size: 11px; color: transparent;
}
.checks .ck.on { color: var(--el-color-danger); font-weight: 600; }
.checks .ck.on .box { background: var(--el-color-danger); border-color: var(--el-color-danger); color: #fff; }
textarea.bx {
  width: 100%; min-height: 62px; border: 1px solid var(--el-border-color);
  border-radius: var(--ards-ctl-radius); padding: 8px 10px; font: 13px/1.6 inherit;
  color: var(--el-text-color-regular); background: #fff; resize: vertical; outline: none;
}
textarea.bx:focus { border-color: var(--el-color-primary); box-shadow: 0 0 0 3px var(--el-color-primary-light-8); }
/* 时间选择器撑满所在栅格 / 时间条 */
.w-full { width: 100%; }

/* 打印预览 */
.print-screen .filter {
  display: flex; align-items: center; gap: 10px; flex-wrap: wrap; background: #fff;
  border: 1px solid #e4e7ed; border-radius: 8px; padding: 12px 14px; margin-bottom: 12px;
}
.print-screen .filter .ipt {
  border: 1px solid #e4e7ed; border-radius: 6px; padding: 6px 10px; font-size: 13px; color: #303133;
}
.paper {
  width: 297mm; min-height: 210mm; margin: 0 auto; background: #fff; padding: 8mm 8mm 6mm;
  box-shadow: 0 2px 12px rgba(0, 0, 0, .12); color: #000; font-size: 9pt; line-height: 1.25;
}
.paper .p-title { text-align: center; font-size: 15pt; font-weight: 700; letter-spacing: 2px; }
.paper .p-sub { text-align: center; font-size: 8pt; color: #444; margin: 2px 0 6px; }
.paper table { border-collapse: collapse; width: 100%; }
.paper table th, .paper table td { border: 1px solid #000; padding: 2px 3px; text-align: center; height: 15px; font-size: 8pt; }
.paper table.p-info td { text-align: left; height: 17px; }
.paper table.p-info td.k { background: #f2f2f2; width: 62px; font-weight: 600; }
.paper table.p-mon td.l, .paper table.p-mon th.l { text-align: left; }
.paper table.p-mon th { background: #f2f2f2; }
.paper table.p-mon td.cat { background: #fafafa; font-weight: 600; width: 18px; }
.paper table.p-mon td.cat .cat-v { writing-mode: vertical-rl; letter-spacing: 2px; margin: 0 auto; }
.paper table.p-mon td.calc { background: #f7f7f7; color: #555; }
.paper table.p-mon td.abn { background: #fee; color: #c00; font-weight: 600; }
.paper .p-sec { font-weight: 700; margin: 5px 0 2px; font-size: 9pt; border-left: 3px solid #000; padding-left: 5px; }
.paper .p-line { border: 1px solid #000; padding: 3px 5px; min-height: 26px; margin-bottom: 3px; }
.paper .p-sign { display: flex; gap: 10px; margin-top: 6px; }
.paper .p-sign div { flex: 1; border: 1px solid #000; padding: 3px 6px; min-height: 30px; }
.paper .p-foot { display: flex; justify-content: space-between; margin-top: 6px; font-size: 8pt; color: #444; }

/* 抽屉 / 弹窗 */
.mask { position: fixed; inset: 0; background: var(--el-mask-color); z-index: 60; }
.drawer {
  position: fixed; top: 0; right: 0; bottom: 0; width: 760px; max-width: 92vw; z-index: 61;
  background: #fff; box-shadow: -8px 0 24px rgba(28, 25, 23, .12);
  display: flex; flex-direction: column;
}
.drawer .dh { display: flex; align-items: center; padding: 16px 20px; border-bottom: 1px solid var(--el-border-color-extra-light); }
.drawer .dh h3 {
  margin: 0; font-size: 15px; font-weight: 600; color: var(--ards-title-color);
  padding-left: 10px; border-left: 3px solid var(--el-color-primary); line-height: 16px;
}
.drawer .db { padding: 18px 20px; overflow: auto; flex: 1; background: #fff; }
.drawer .df {
  padding: 12px 20px; border-top: 1px solid var(--el-border-color-extra-light);
  display: flex; gap: 10px; align-items: center; background: var(--el-fill-color-light);
}
.win-note {
  background: var(--el-fill-color-light); border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--ards-ctl-radius); padding: 10px 12px; font-size: 12px;
  color: var(--el-text-color-regular); margin-bottom: 14px; line-height: 1.7;
}
.tp-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.tp-table th, .tp-table td { border: 1px solid var(--el-border-color-lighter); padding: 8px 10px; text-align: left; }
.tp-table th { background: var(--el-table-header-bg-color); font-weight: 600; color: var(--el-table-header-text-color); }
.tp-table td.n { text-align: center; width: 46px; color: var(--el-text-color-placeholder); }
.tp-table .mini {
  border: 1px solid var(--el-border-color); border-radius: 6px; padding: 3px 8px;
  font-size: 12px; background: #fff; color: var(--el-text-color-secondary); cursor: pointer; width: 100%;
  transition: background .15s ease, border-color .15s ease, color .15s ease;
}
.tp-table .mini:hover {
  color: var(--el-color-primary); border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}
.tp-table td .mini { width: 100%; }
.tp-ops { margin-top: 12px; display: flex; gap: 10px; }
.notice {
  border: 1px solid #fde68a; background: #fffbeb; color: #92400e;
  border-radius: var(--ards-ctl-radius); padding: 10px 12px; font-size: 12px; margin-top: 12px;
}
.dialog {
  position: fixed; top: 50%; left: 50%; transform: translate(-50%, -50%); width: 480px;
  max-width: 92vw; background: #fff; border-radius: var(--ards-card-radius); z-index: 61;
  overflow: hidden; box-shadow: 0 12px 32px rgba(28, 25, 23, .18);
}
.dialog .dh { padding: 16px 20px; border-bottom: 1px solid var(--el-border-color-extra-light); }
.dialog .dh h3 {
  margin: 0; font-size: 15px; font-weight: 600; color: var(--ards-title-color);
  padding-left: 10px; border-left: 3px solid var(--el-color-primary); line-height: 16px;
}
.dialog .db { padding: 18px 20px; }
.dialog .df {
  padding: 12px 20px; border-top: 1px solid var(--el-border-color-extra-light);
  display: flex; gap: 10px; align-items: center; background: var(--el-fill-color-light);
}
.req { color: var(--el-color-danger); }
.tip {
  font-size: 12px; color: var(--el-text-color-regular); background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter); border-radius: var(--ards-ctl-radius);
  padding: 10px 12px; line-height: 1.7;
}
</style>
