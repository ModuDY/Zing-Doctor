import io, sys, yaml
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
d = yaml.safe_load(open('src/main/resources/quality/facts/support.yaml', encoding='utf-8'))
f = [x for x in d if x['fact'] == 'fact_dvt'][0]
dr = f['derive']
print('drug join 片段出现次数(应为3):', sum(1 for c in dr if 'patient_advice_execute" x ON' in c.replace("zing_icu_db_prod\".\"patient_advice_execute\" x ON", "patient_advice_execute\" x ON") or ('execute_drug' in c and 'x.start_time' in c)))
print('mech 时间过滤出现次数:', sum(1 for c in dr if "'%气压治疗%'" in c and 'o.start_time' in c))
print('filter 时间过滤出现次数:', sum(1 for c in dr if "'%下腔静脉滤器%'" in c and 'o.start_time' in c and 'LIKE' in c))
print('总 derive 列数:', len(dr))
for c in dr:
    assert c.count('"') % 2 == 0, '引号不平衡: ' + c[:80]
print('引号平衡 OK')
