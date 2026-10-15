import sys
sql = open('sql/40_qc_fact_def_seed.sql', encoding='utf-8').read()
for f in ['fact_dvt', 'fact_ards', 'fact_staff']:
    i = sql.find("'" + f + "'")
    if i < 0:
        print(f, 'NOT FOUND')
        continue
    print('=' * 20, f, '=' * 20)
    print(sql[i:i + 5200])
    print()
