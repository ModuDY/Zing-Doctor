# -*- coding: utf-8 -*-
"""按引擎 compileFact 的装配方式（select + derive + where，参数替换为字面量）
生成 4 个事实层的实测 SQL，用于在真实达梦库上验证 DSL 可执行性。"""
import yaml, glob, io, sys, os

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

def load_one(fname, fact):
    for doc in yaml.safe_load_all(open(fname, encoding='utf-8')):
        items = doc if isinstance(doc, list) else [doc]
        for d in items:
            if d and d.get('fact') == fact:
                return d
    raise SystemExit('fact not found: ' + fact)

SUPPORT = 'src/main/resources/quality/facts/support.yaml'
PATIENT = 'src/main/resources/quality/facts/patient.yaml'
CLINICAL = 'src/main/resources/quality/facts/clinical.yaml'

os.makedirs('docs/_research/tests', exist_ok=True)
PS = "'2026-09-01 00:00:00'"
PE = "'2026-10-01 00:00:00'"

def build(fact, fname, out):
    d = load_one(fname, fact)
    cols = list(d.get('select') or []) + list(d.get('derive') or [])
    where = ' AND '.join('(' + w + ')' for w in (d.get('where') or []))
    sql = 'SELECT ' + ',\n  '.join(c.strip() for c in cols) + \
          '\nFROM "zing_icu_db_prod"."' + d['source'] + '" ' + d['alias'] + \
          '\nWHERE ' + where
    sql = sql.replace(':periodStart', 'TIMESTAMP ' + PS).replace(':periodEnd', 'TIMESTAMP ' + PE)
    open(out, 'w', encoding='utf-8').write(sql)
    print('written', out)

build('fact_device', SUPPORT, 'docs/_research/tests/fact_device.sql')
build('fact_dvt', SUPPORT, 'docs/_research/tests/fact_dvt.sql')
build('fact_staff', PATIENT, 'docs/_research/tests/fact_staff.sql')
build('fact_bed', PATIENT, 'docs/_research/tests/fact_bed.sql')
build('fact_ards', CLINICAL, 'docs/_research/tests/fact_ards.sql')
