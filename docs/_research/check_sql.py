import io, sys
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sql = open('sql/50_quality_gap_impl.sql', encoding='utf-8').read()
i = sql.find('fact_device')
print(sql[i-120:i+260])
print('...')
k = sql.find("WHERE \"index_code\" = 'quality_24'")
print(sql[k-780:k+140])
print('...')
# 引号平衡检查（去掉 SQL 注释行后，单引号应成对）
lines = [l for l in sql.splitlines() if not l.strip().startswith('--')]
body = '\n'.join(lines)
print('单引号总数(应为偶数):', body.count("'"))
