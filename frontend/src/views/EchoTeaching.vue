<template>
  <div class="echo-page qb-theme">
    <section class="page-header">
      <div>
        <h1>床旁超声（POCUS）教学</h1>
        <p class="sub">重症床旁超声八个标准部位的操作要点与判读入门 · 仅供科室教学参考，正式诊断以超声科报告为准</p>
      </div>
      <el-input v-model="keyword" placeholder="过滤部位 / 关键词" clearable style="width: 220px" />
    </section>

    <div class="sec-grid">
      <section v-for="s in filtered" :key="s.key" class="sec-card">
        <div class="sec-head"><span class="sec-idx">{{ s.idx }}</span><h2>{{ s.title }}</h2></div>
        <div class="sec-block"><label>检查目的</label><p>{{ s.purpose }}</p></div>
        <div class="sec-block"><label>操作要点</label><ul><li v-for="(p, i) in s.steps" :key="i">{{ p }}</li></ul></div>
        <div class="sec-block"><label>正常 / 异常判读</label>
          <p v-for="(d, i) in s.findings" :key="i" :class="{ warn: d.startsWith('异常') }">{{ d }}</p>
        </div>
        <div class="sec-block" v-if="s.note"><label>注意</label><p class="warn">{{ s.note }}</p></div>
      </section>
    </div>
    <el-empty v-if="!filtered.length" description="无匹配部位" />
  </div>
</template>

<script>
/**
 * 床旁彩超教学：静态内容页（零后端、零数据依赖）。
 * 内容为重症 POCUS 入口级教学要点（BLUE / FAST / FATE / DVT 压迫等常用方案的八部位浓缩版），
 * 仅供科室带教参考 —— 图像留存与正式诊断以超声科报告为准，本页不做任何自动判定。
 */
const SECTIONS = [
  {
    key: 'lung', idx: '01', title: '肺部',
    purpose: '气胸 / 肺水增多 / 胸腔积液 / 肺实变的快速鉴别（BLUE 方案核心）。',
    steps: [
      '半卧位，按 BLUE 点取前胸 3+2 个切面；先看胸膜滑动，再数 B 线、找 A 线',
      'M 型观察「沙滩征」（正常）与「平流层征」（气胸方向）',
      '侧后胸查胸腔积液与实变'
    ],
    findings: [
      '正常：肋骨声影间「蝙蝠征」，胸膜线滑动存在',
      '异常：胸膜滑动消失 + 平流层征 → 高度提示气胸（禁止单凭超声置管引流）',
      '异常：弥漫 B 线 → 肺水增多 / ARDS 方向；局灶 B 线伴实变 → 肺炎方向',
      '异常：无回声胸腔积液，测量宽度并体表定位'
    ],
    note: '皮下气肿时肺滑动判读不可靠。'
  },
  {
    key: 'eye', idx: '02', title: '眼球',
    purpose: '视神经鞘直径（ONSD）估测颅内压升高方向；眼内异物 / 视网膜脱离辅助筛查。',
    steps: [
      '高频线阵探头，闭合眼睑上轻放、严禁加压',
      '横纵两轴扫查，深度 3~4 cm',
      '球后 3 mm 处垂直测量 ONSD，双眼各测 2~3 次取平均'
    ],
    findings: [
      '正常：成人 ONSD 多数 < 5.0 mm',
      '异常：ONSD ≥ 5.0~6.0 mm 提示颅内压升高可能（文献阈值不一致，结合临床）'
    ],
    note: '疑似眼球破裂时禁做加压试验；测量点距球后距离不一致会显著影响读数。'
  },
  {
    key: 'ivc', idx: '03', title: '下腔静脉（IVC）',
    purpose: '容量状态的粗估（补液 / 脱水决策的线索之一）。',
    steps: [
      '剑突下长轴，IVC 入右房后约 2 cm 处测量',
      'M 型记录吸 / 呼气末直径：自发呼吸算塌陷指数，机械通气算变异度'
    ],
    findings: [
      '正常（自发呼吸）：直径 1.5~2.5 cm，吸气塌陷 > 50% 提示低容量方向',
      '异常：扩张无塌陷 → 容量过负荷 / 右心淤血 / 腹内压升高等多种可能'
    ],
    note: '腹内压、PEEP、右心功能都会干扰 IVC，禁止单指标决定补液。'
  },
  {
    key: 'abd', idx: '04', title: '腹部（FAST）',
    purpose: '游离腹腔积液 / 积血快速筛查（创伤与不明血压下降的床旁初筛）。',
    steps: [
      '四窗口：剑突下（心包）、右季肋（Morison 囊）、左季肋（脾肾隐窝）、耻骨上（盆腔）',
      '每窗口先找器官界面，再看无回声带'
    ],
    findings: [
      '正常：器官间隙为线状高回声界面，无游离无回声',
      '异常：Morison 囊 / 脾肾隐窝 / 盆腔无回声带 → 游离液体（结合创伤史判断）'
    ],
    note: 'FAST 阴性不能排除腹内损伤；肠胀气时窗口常不理想。'
  },
  {
    key: 'diaphragm', idx: '05', title: '膈肌',
    purpose: '膈肌移动度与增厚率评估，辅助撤机判断与膈肌功能障碍筛查。',
    steps: [
      '剑突下 / 肋缘下入路，M 型垂直于膈顶记录移动度',
      '腋中线高频测膈肌厚度，计算增厚率 TDI =（吸气末−呼气末）/ 呼气末'
    ],
    findings: [
      '正常：平静呼吸移动度约 1~2 cm；TDI > 20% 常视为功能保留',
      '异常：移动度 < 1 cm / 矛盾运动 / TDI < 20% → 膈肌功能受损方向'
    ],
    note: 'M 型不垂直会高估读数；随访应固定体位与呼吸条件。'
  },
  {
    key: 'kidney', idx: '06', title: '肾脏',
    purpose: '肾积水（梗阻性 AKI 筛查）、肾脏大小与灌注初查。',
    steps: [
      '侧腰部冠状切面（肝 / 脾作声窗），长轴测肾脏大小',
      '看集合系统分离与肾周积液；彩色多普勒测叶间动脉 RI'
    ],
    findings: [
      '正常：成人肾长 9~12 cm，集合系统无分离',
      '异常：集合系统分离 → 梗阻方向；RI > 0.7 非特异性，仅参考'
    ],
    note: '无积水不代表非梗阻性病因，AKI 病因判定不能依赖超声。'
  },
  {
    key: 'heart', idx: '07', title: '心脏（FATE / 心包）',
    purpose: '心包积液 / 填塞、左右心大体功能、容量状态的床旁快速评估。',
    steps: [
      '四标准切面：剑突下四腔、剑突下长轴（IVC）、胸骨旁长轴、短轴',
      '先看心包积液（剑突下最敏感），再看四腔比例与室壁运动',
      '短轴目测左室收缩「乳头肌捏合」，EF 只分 正常 / 减低 / 亢进 三档'
    ],
    findings: [
      '正常：心包无或极少量无回声；右室 < 左室 2/3；室壁收缩均匀',
      '异常：心包积液 + 右室舒张塌陷 → 填塞方向（急症，立即处理并通知）',
      '异常：右室显著扩大 / 室间隔矛盾运动 → 右心负荷增高方向（大面积肺栓塞的可能之一）'
    ],
    note: '床旁目测 EF 误差大，正式 EF 与节段运动以超声科报告为准。'
  },
  {
    key: 'vessel', idx: '08', title: '血管（DVT 压迫超声）',
    purpose: '下肢深静脉血栓（DVT）的床旁压迫初筛（ICU 患者高危）。',
    steps: [
      '高频线阵探头，自腹股沟沿股总静脉 → 股浅静脉 → 腘静脉逐段横切',
      '每段探头按压：正常静脉完全压瘪；压瘪不全为阳性',
      '必要时加彩色多普勒观察管腔'
    ],
    findings: [
      '正常：静脉腔无回声、加压完全压瘪',
      '异常：管腔不能压瘪 / 腔内实性回声 → DVT 可能（报告并避免盲目按摩）'
    ],
    note: '压迫超声阴性不能完全排除小腿深静脉血栓。'
  }
]

export default {
  name: 'EchoTeaching',
  data() {
    return { keyword: '' }
  },
  computed: {
    filtered() {
      const k = (this.keyword || '').trim().toLowerCase()
      if (!k) return SECTIONS
      return SECTIONS.filter((s) =>
        (s.title + s.purpose + s.steps.join('') + s.findings.join('')).toLowerCase().includes(k))
    }
  }
}
</script>

<style scoped>
.echo-page { padding: 16px; }
.page-header { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 16px; gap: 12px; flex-wrap: wrap; }
.page-header h1 { margin: 0; font-size: 18px; font-weight: 700; color: #1c1917; }
.sub { margin: 4px 0 0; color: #78716c; font-size: 12px; }
.sec-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(380px, 1fr)); gap: 14px; }
.sec-card { border: 1px solid #e7e5e4; border-radius: 12px; background: #fff; padding: 14px 18px 16px; }
.sec-head { display: flex; align-items: baseline; gap: 8px; margin-bottom: 10px; }
.sec-idx { color: #a8a29e; font-size: 12px; font-weight: 700; }
.sec-head h2 { margin: 0; font-size: 15px; font-weight: 700; color: #1c1917; }
.sec-block { margin-bottom: 10px; }
.sec-block label { display: block; color: #78716c; font-size: 11px; margin-bottom: 2px; }
.sec-block p, .sec-block li { color: #292524; font-size: 12px; line-height: 1.6; margin: 0 0 3px; }
.sec-block ul { margin: 0; padding-left: 16px; }
.sec-block p.warn { color: #b45309; }
</style>
