import re
with open(r'c:\Users\11515\.trae-cn\attachments\6aa7ec24bb782a3574629440\be337828-8b31-4581-b21c-3cea4e7f8d54_dd410113-0c51-4e13-96ab-73a2452578d0_INSERT INT....txt', 'r', encoding='utf-8') as f:
    raw = f.read()
rows = []
for m in re.finditer(r'VALUES \((.*?)\);', raw, re.S):
    vals = m.group(1)
    tokens = []
    token = []
    in_quote = False
    quote_char = ''
    i = 0
    while i < len(vals):
        c = vals[i]
        if not in_quote and c in "'\"":
            in_quote = True
            quote_char = c
            token.append(c)
        elif in_quote and c == quote_char:
            if i + 1 < len(vals) and vals[i+1] == quote_char:
                token.append(c)
                token.append(quote_char)
                i += 1
            else:
                token.append(c)
                in_quote = False
        elif not in_quote and c == ',':
            tokens.append(''.join(token).strip())
            token = []
        else:
            token.append(c)
        i += 1
    if token:
        tokens.append(''.join(token).strip())
    def strip(s):
        if (s.startswith("'") and s.endswith("'")) or (s.startswith('"') and s.endswith('"')):
            return s[1:-1].replace("''", "'").replace('""', '"')
        if s == 'NULL':
            return None
        return s
    cols = [strip(t) for t in tokens]
    rows.append({
        'id': cols[0], 'count_name': cols[1], 'quality_type_code': cols[2], 'is_visible': cols[3],
        'is_show_page': cols[4], 'sort_no': cols[5], 'remark': cols[6], 'numerator_code': cols[7],
        'numerator_unit': cols[8], 'numerator_precision': cols[9], 'numerator_rate': cols[10],
        'denominator_code': cols[11], 'denominator_unit': cols[12], 'denominator_precision': cols[13],
        'denominator_rate': cols[14], 'percent_unit': cols[15], 'percent_rate': cols[16],
        'percent_precision': cols[17], 'depart_code': cols[18], 'status': cols[19], 'del_flag': cols[20]
    })
print('Total rows:', len(rows))
print('is_show_page=1:', sum(1 for r in rows if r['is_show_page'] == '1'))
print('is_show_page=0:', sum(1 for r in rows if r['is_show_page'] == '0'))
show_rows = [r for r in rows if r['is_show_page'] == '1']
for i, r in enumerate(show_rows, 1):
    print(f"{i}. {r['count_name']} | {r['numerator_code']} / {r['denominator_code']} | {r['percent_unit']} | prec={r['percent_precision']}")
