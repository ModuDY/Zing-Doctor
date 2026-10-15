import yaml, glob, re, sys

def load(pattern):
    out = []
    for f in sorted(glob.glob(pattern, recursive=True)):
        for doc in yaml.safe_load_all(open(f, encoding='utf-8')):
            if not doc:
                continue
            items = doc if isinstance(doc, list) else [doc]
            for d in items:
                out.append((f, d))
    return out

facts = {}
for f, d in load('src/main/resources/quality/facts/*.yaml'):
    cols = set()
    for c in (d.get('select') or []) + (d.get('derive') or []):
        m = re.search(r'\bAS\s+([A-Za-z0-9_]+)\s*$', c, re.I)
        if m:
            cols.add(m.group(1).lower())
    # 源表原列也可引用
    alias = d.get('alias') or 't'
    facts[d['fact']] = {'cols': cols, 'status': d.get('status'), 'file': f, 'alias': alias}

SQL_FUNCS = {'case','when','then','else','end','exists','not','and','or','is','null','in','like','between','datediff','regexp_like','cast','trim','sum','count','avg','max','min','distinct','coalesce','interval','limit','rownum','select','from','where','as','on','by','group','order','having','asc','desc','to_date','to_char','substr','instr','nvl','decode','ifnull','length','lower','upper','replace','concat','abs','round','floor','ceil','greatest','least','add_months','months_between','trunc','current_timestamp','sysdate','year','month','day','hour','minute','second','date','time','timestamp','numtodsinterval','extract','to_number','to_timestamp','lpad','rpad','mod','power','sqrt','sign','nullif','if','ifnull','str_to_date','date_format','dateadd','datediff_d','now','current_date','date_sub','date_add','timestampdiff','day','days','hour','hours','minute','minutes','dayofmonth','ifnull','if','values','true','false','unknown','escape','collate','cast','convert','using','natural','full','outer','inner','left','right','cross','join','union','all','some','any','exists'}

errors = []
warnings = []
for f, d in load('src/main/resources/quality/metrics/*.yaml'):
    code = d.get('code')
    fact = d.get('fact')
    if fact not in facts:
        errors.append(f'{code}: 事实层 {fact} 不存在')
        continue
    cols = facts[fact]['cols']
    for field in ('where', 'numerator', 'denominatorWhere'):
        expr = d.get(field)
        if not expr:
            continue
        # 去掉字符串字面量与数字
        e = re.sub(r"'[^']*'", " ", expr)
        e = re.sub(r'"[^"]*"', " ", e)
        for ident in re.findall(r'[A-Za-z_][A-Za-z0-9_]*', e):
            low = ident.lower()
            if low in SQL_FUNCS or low in cols:
                continue
            if re.fullmatch(r'[a-z_]*\d+', low):  # quality_12 之类指标编号不会出现在表达式
                continue
            warnings.append(f'{code}.{field}: 标识符 {ident} 不在事实层 {fact} 投影列中')

# 指标编号唯一性
codes = [d.get('code') for f, d in load('src/main/resources/quality/metrics/*.yaml')]
dup = {c for c in codes if codes.count(c) > 1}
if dup:
    errors.append(f'重复指标编号: {dup}')

# 派生指标引用存在性
for f, d in load('src/main/resources/quality/metrics/*.yaml'):
    for field in ('numeratorMetric', 'denominatorMetric'):
        ref = d.get(field)
        if ref and ref not in codes:
            errors.append(f'{d.get("code")}.{field} 引用了不存在的指标 {ref}')

print(f'事实层 {len(facts)} 个，指标 {len(codes)} 条')
print(f'状态: ' + ', '.join(f'{k}={v["status"]}' for k, v in facts.items()))
if warnings:
    print('\n-- 警告（可能是函数名或源表列，需人工确认）--')
    for w in sorted(set(warnings)):
        print(' ', w)
if errors:
    print('\n-- 错误 --')
    for e in errors:
        print(' ', e)
    sys.exit(1)
print('\n校验通过：无错误')
