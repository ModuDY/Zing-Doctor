import io, sys, re
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sql = open('sql/27_restore_config_snapshot.sql', encoding='utf-8').read()
i = sql.lower().find('quality_count_rule')
seg = sql[i:i+500000]
rows = re.findall(r"VALUES \((\d+),\s*'([^']+)',\s*'([^']+)',\s*'([^']*)',\s*(\d+),\s*(\d+),\s*(\d+),\s*'([^']*)',\s*'([^']+)',\s*'([^']+)',\s*'([^']*)',\s*(\d+)", seg)
print('规则行数:', len(rows))
for r in rows:
    rid, name = r[0], r[1]
    num, den, rate = r[8], r[9], r[11]
    print(f"{rid:>10} | {name[:34]:<36} | num={num:<12} den={den:<12} x{rate}")
