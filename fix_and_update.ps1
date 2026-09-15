$path = 'd:\work\ZING\quality-board-redesign\pages\quality-monthly.html'
$html = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$rows = [System.IO.File]::ReadAllText('d:\work\ZING\quality-board-redesign\.preflight\monthly-rows-127.html', [System.Text.Encoding]::UTF8)

# Fix title and CSS comment
$html = $html.Replace('质控月度汇�?· 质控指标看板', '质控月度汇总 · 质控指标看板')
$html = $html.Replace('Quality Board Redesign �?Warm Clean White', 'Quality Board Redesign — Warm Clean White')

# Replace summary cards section
$summarySection = @'
        <!-- Monthly summary overview -->
        <section class="qb-card qb-overview" data-dom-id="summary-overview">
          <div class="qb-overview-head">
            <div style="display:flex;align-items:baseline;gap:12px;">
              <h2 class="qb-overview-title">月度汇总概览</h2>
              <span class="qb-overview-period">2026 年度 · 全院</span>
            </div>
            <div class="qb-overview-tags">
              <span class="qb-overview-tag">原子指标 <strong>127</strong></span>
              <span class="qb-overview-tag">覆盖域 <strong>8</strong></span>
            </div>
          </div>
          <div class="qb-stat-grid">
            <div class="qb-stat-card">
              <div class="qb-stat-head">
                <span class="qb-stat-label">汇总指标数</span>
                <span class="qb-stat-icon orange"><i data-lucide="list" class="w-4 h-4"></i></span>
              </div>
              <div class="qb-stat-value">127</div>
              <div class="qb-stat-bar"><i class="orange" style="width:100%;"></i></div>
              <div class="qb-stat-foot">本年度全部原子指标</div>
            </div>
            <div class="qb-stat-card">
              <div class="qb-stat-head">
                <span class="qb-stat-label">本年度有数据</span>
                <span class="qb-stat-icon green"><i data-lucide="database" class="w-4 h-4"></i></span>
              </div>
              <div class="qb-stat-value green">85</div>
              <div class="qb-stat-bar"><i class="green" style="width:66.9%;"></i></div>
              <div class="qb-stat-foot">已接入或已汇总指标</div>
            </div>
            <div class="qb-stat-card">
              <div class="qb-stat-head">
                <span class="qb-stat-label">覆盖域</span>
                <span class="qb-stat-icon blue"><i data-lucide="layout-grid" class="w-4 h-4"></i></span>
              </div>
              <div class="qb-stat-value">8</div>
              <div class="qb-stat-bar"><i class="gray" style="width:100%;"></i></div>
              <div class="qb-stat-foot">患者流转 / 脓毒症 / 抗菌药 / DVT / 评分 / 评估 / ARDS / 导管</div>
            </div>
            <div class="qb-stat-card">
              <div class="qb-stat-head">
                <span class="qb-stat-label">当前展示</span>
                <span class="qb-stat-icon gray"><i data-lucide="eye" class="w-4 h-4"></i></span>
              </div>
              <div class="qb-stat-value">127</div>
              <div class="qb-stat-bar"><i class="orange" style="width:100%;"></i></div>
              <div class="qb-stat-foot">宽表已展开全部指标</div>
            </div>
          </div>
        </section>
'@
$html = [regex]::Replace($html, '<!-- Monthly summary overview -->.*?<!-- Annual trend \(collapsible drawer\) -->', $summarySection + '        <!-- Annual trend (collapsible drawer) -->', [System.Text.RegularExpressions.RegexOptions]::Singleline)

# Replace trend select options
$trendOptions = @'
                <option value="quality_0" data-domain="患者流转" data-unit="人">患者流转 · 收治人数（转入+入院）</option>
                <option value="quality_9" data-domain="脓毒症_感染性休克" data-unit="人">脓毒症_感染性休克 · 完成3h集束化治疗的患者数</option>
                <option value="quality_12" data-domain="抗菌药送检" data-unit="%">抗菌药送检 · 使用抗菌药物前病原学检验标本送检</option>
                <option value="quality_15" data-domain="评分_资源" data-unit="%">评分_资源 · ICU收治患者预计病死率(%)</option>
                <option value="quality_305" data-domain="评估依从" data-unit="人">评估依从 · 进行镇静评估的 ICU患者人数</option>
                <option value="quality_311" data-domain="ARDS专项" data-unit="人">ARDS专项 · 中重度 ARDS 患者中实施俯卧位通气治疗的人数</option>
                <option value="quality_410" data-domain="DVT预防" data-unit="人">DVT预防 · 深静脉血栓相关死亡人数</option>
'@
$html = [regex]::Replace($html, '(<select class="qb-select" data-dom-id="trend-select"[^>]*>).*?(</select>)', '$1\n' + $trendOptions + '              $2', [System.Text.RegularExpressions.RegexOptions]::Singleline)

# Replace dataset
$dataset = @'
          const dataset = {
            quality_0:  [46,48,0,2,4,6,8,10,12,14,16,18], // 人
            quality_9:  [14,16,18,20,22,24,26,28,30,32,34,36], // 人
            quality_12: [96.55,0.54,4.52,8.51,12.50,16.48,20.47,24.46,28.45,32.43,36.42,40.41], // %
            quality_15: [4,6,8,10,12,14,16,18,20,22,24,26], // %
            quality_305:[3,5,7,9,11,13,15,17,19,21,23,25], // 人
            quality_311:[47,49,1,3,5,7,9,11,13,15,17,19], // 人
            quality_410:[47,49,1,3,5,7,9,11,13,15,17,19]  // 人
          };
'@
$html = [regex]::Replace($html, 'const dataset = \{[^}]+\};', $dataset, [System.Text.RegularExpressions.RegexOptions]::Singleline)

# Replace table
$thead = @'
            <thead><tr>
              <th style="min-width: 120px; position: sticky; left: 0; z-index: 2; background: var(--qb-stone-50);">指标编码</th>
              <th style="min-width: 260px; position: sticky; left: 120px; z-index: 2; background: var(--qb-stone-50);">指标名称</th>
              <th style="width: 130px; position: sticky; left: 380px; z-index: 2; background: var(--qb-stone-50);">域</th>
              <th style="width: 60px; text-align: center;">单位</th>
              <th style="width: 84px; text-align: right;">1月</th><th style="width: 84px; text-align: right;">2月</th><th style="width: 84px; text-align: right;">3月</th><th style="width: 84px; text-align: right;">4月</th><th style="width: 84px; text-align: right;">5月</th><th style="width: 84px; text-align: right;">6月</th>
              <th style="width: 84px; text-align: right;">7月</th><th style="width: 84px; text-align: right;">8月</th><th style="width: 84px; text-align: right;">9月</th><th style="width: 84px; text-align: right;">10月</th><th style="width: 84px; text-align: right;">11月</th><th style="width: 84px; text-align: right;">12月</th>
              <th style="width: 86px; text-align: right;">Q1</th><th style="width: 86px; text-align: right;">Q2</th><th style="width: 86px; text-align: right;">Q3</th><th style="width: 86px; text-align: right;">Q4</th>
              <th style="width: 100px; text-align: right;">全年合计</th>
              <th style="width: 86px; text-align: right;">月均</th>
              <th style="width: 76px; text-align: center;">最高月</th>
              <th style="width: 76px; text-align: center;">最低月</th>
            </tr></thead>
'@
$newTable = "<table class=`"qb-table qb-wide-table`" style=`"min-width: 2400px;`">`n" + $thead + "            <tbody>`n" + $rows + "`n            </tbody>`n          </table>"
$html = [regex]::Replace($html, '<table class="qb-table qb-wide-table" style="min-width: 1900px;">.*?</table>', $newTable, [System.Text.RegularExpressions.RegexOptions]::Singleline)

[System.IO.File]::WriteAllText($path, $html, [System.Text.Encoding]::UTF8)
Write-Host "Done"
