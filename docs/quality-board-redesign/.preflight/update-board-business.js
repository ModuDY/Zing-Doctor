const fs = require('fs');

const pagePath = 'd:/work/ZING/quality-board-redesign/pages/quality-board.html';
let html = fs.readFileSync(pagePath, 'utf8');

const rows = fs.readFileSync('d:/work/ZING/quality-board-redesign/.preflight/board-rows-business.html', 'utf8');

const newPanel = `        <!-- Tab panel: Board -->
        <section class="qb-card qb-tab-panel" style="border-radius: 0 0 12px 12px; border-top: none; overflow-x: auto;">
          <!-- 60 business metrics from quality_count_rule -->
          <div class="table-wrap" style="min-width: 1100px;">
            <table class="metric-table business-table">
              <thead>
                <tr>
                  <th rowspan="2" style="min-width: 220px;">指标名称</th>
                  <th colspan="2" style="text-align: center;">分子</th>
                  <th colspan="2" style="text-align: center;">分母</th>
                  <th rowspan="2">单位</th>
                  <th rowspan="2">本期值</th>
                  <th rowspan="2">目标值</th>
                  <th rowspan="2">预警值</th>
                  <th rowspan="2">状态</th>
                  <th rowspan="2" style="min-width: 90px;">血缘</th>
                </tr>
                <tr>
                  <th>指标编码</th>
                  <th>本期值</th>
                  <th>指标编码</th>
                  <th>本期值</th>
                </tr>
              </thead>
              <tbody>
${rows}
              </tbody>
            </table>
          </div>
        </section>`;

const startMarker = '        <!-- Tab panel: Board -->';
const endMarker = '        </section>\n      </div>\n    </main>';
const startIdx = html.indexOf(startMarker);
const endIdx = html.indexOf(endMarker);
if (startIdx === -1 || endIdx === -1) {
  console.error('Markers not found');
  process.exit(1);
}
html = html.slice(0, startIdx) + newPanel + '\n      </div>\n    </main>' + html.slice(endIdx + endMarker.length - '      </div>\n    </main>'.length);

// Update overview tags
html = html.replace('来源 quality/metrics/*.yaml', '来源 quality_count_rule');
html = html.replace(/看板指标 <strong>\d+<\/strong> 条 · 出数率 [\d.]+%/, '看板指标 <strong>60</strong> 条 · 出数率 0.0%');

// Update summary cards
html = html.replace(/<div class="qb-stat-value orange">\d+<\/div>/, '<div class="qb-stat-value orange">60</div>');
html = html.replace(/<div class="qb-stat-value green">\d+<\/div>/, '<div class="qb-stat-value green">0</div>');
html = html.replace(/<div class="qb-stat-value orange">\d+<\/div>/, '<div class="qb-stat-value orange">0</div>');
html = html.replace(/<div class="qb-stat-value gray">\d+<\/div>/, '<div class="qb-stat-value gray">60</div>');

// Update stat bars
html = html.replace(/<i class="orange" style="width: \d+%"><\/i>/, '<i class="orange" style="width: 0%"></i>');
html = html.replace(/<i class="green" style="width: \d+%"><\/i>/, '<i class="green" style="width: 0%"></i>');
html = html.replace(/<i class="gray" style="width: [\d.]+%"><\/i>/, '<i class="gray" style="width: 100%"></i>');

fs.writeFileSync(pagePath, html);
console.log('Updated quality-board.html with business metrics');
