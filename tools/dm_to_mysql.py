#!/usr/bin/env python3
"""
达梦 DM8 SQL → MySQL 8.x 建表脚本转换器（v2）。

处理内容：
  - CREATE SEQUENCE：删除；列上的 NEXTVAL 默认值：转为 AUTO_INCREMENT
    （不能简单删除：种子/配置 INSERT 不写 id，MySQL 严格模式下会直接报错）
  - schema 前缀、双引号标识符：去前缀、双引号→反引号
  - COMMENT ON TABLE/COLUMN：合并进 CREATE TABLE 列定义
  - 类型映射：VARCHAR2→VARCHAR, CLOB→LONGTEXT, BLOB→LONGBLOB, NUMBER→DECIMAL
  - PL/SQL 幂等块（DECLARE/BEGIN...END;/）：解析提取 EXECUTE IMMEDIATE 内的 DDL
  - CREATE TABLE → CREATE TABLE IF NOT EXISTS
  - CREATE INDEX → CALL zing_add_index(...) 幂等存储过程
  - 索引列含表达式（达梦部分唯一索引，如 CASE WHEN status=1 THEN 1 ELSE NULL END）：
    先补 STORED 生成列，再对生成列建唯一索引
  - ALTER TABLE ADD COLUMN → CALL zing_add_column(...) 幂等存储过程
  - ALTER TABLE MODIFY → 原样（MODIFY 天然幂等）
"""
import re
import os
import sys

SCHEMAS = ['zing_doctor_db_prod', 'zing_icu_db_prod']


def strip_schema(s):
    for sch in SCHEMAS:
        s = s.replace(f'"{sch}".', '')
        s = s.replace(f'`{sch}`.', '')
    return s


def quotes_to_backticks(s):
    return re.sub(r'"(\w+)"', r'`\1`', s)


def convert_type(line):
    line = re.sub(r'\bVARCHAR2\s*\(', 'VARCHAR(', line, flags=re.IGNORECASE)
    line = re.sub(r'\bCLOB\b', 'LONGTEXT', line, flags=re.IGNORECASE)
    line = re.sub(r'\bBLOB\b', 'LONGBLOB', line, flags=re.IGNORECASE)
    line = re.sub(r'\bNUMBER\s*\(', 'DECIMAL(', line, flags=re.IGNORECASE)
    line = re.sub(r'\bNUMBER\b(?!\s*\()', 'DECIMAL(20,4)', line, flags=re.IGNORECASE)
    return line


# ---------------------------------------------------------------------------
# 索引里的表达式（达梦「部分唯一索引」）兼容处理
# 达梦允许索引列写表达式，例如：
#   CREATE UNIQUE INDEX uk_ards_prone_cell ON ards_prone_cell
#     (record_id, tp_index, param_key, CASE WHEN status = 1 THEN 1 ELSE NULL END)
# 语义：只有 status=1 的行参与唯一性（NULL 在唯一索引里不算冲突），软删除行被排除。
# MySQL / MariaDB 不支持表达式索引，等价做法是：
#   1) 加一个 STORED 生成列 = 该表达式；
#   2) 再对「普通列 + 生成列」建唯一索引（生成列为 NULL 时同样不参与唯一性判断）。
# ---------------------------------------------------------------------------
_CASE_GUARD_RE = re.compile(
    r'(?is)^CASE\s+WHEN\s+`?(\w+)`?\s*=\s*(\d+)\s+THEN\s+\d+\s+ELSE\s+NULL\s+END$')
_PLAIN_COL_RE = re.compile(r'^`\w+`$')


def convert_index_statements(tbl, idx, uniq, cols):
    """把一条索引转换成 MySQL 语句列表（含表达式列时先补生成列）。"""
    parts = [p.strip() for p in cols.split(',') if p.strip()]
    guards, new_parts = [], []
    for p in parts:
        if _PLAIN_COL_RE.match(p):
            new_parts.append(p)
            continue
        gm = _CASE_GUARD_RE.match(p)
        if gm:
            gcol = (idx + '_guard')[:64]
            guards.append(
                f"CALL zing_add_column('{tbl}', '{gcol}', "
                f"'TINYINT AS ({p}) STORED');")
            new_parts.append(f'`{gcol}`')
            continue
        # 其它表达式（函数索引等）无法自动等价，跳过并在脚本里留提示
        return [
            f'-- ⚠️ 索引 {idx}（表 {tbl}）在达梦上含表达式列：{p}',
            '--    MySQL/MariaDB 不支持表达式索引，已跳过创建，请人工改造后补建',
        ]
    return guards + [
        f"CALL zing_add_index('{tbl}', '{idx}', {uniq}, '{', '.join(new_parts)}');"]


def extract_plsql_blocks(raw):
    """
    提取 PL/SQL 匿名块（DECLARE ... END; 后面跟单独一行 /）。
    返回 (blocks_list, raw_with_blocks_removed)。
    每个 block 是块内完整文本（含 EXECUTE IMMEDIATE '...'）。
    """
    blocks = []

    # 匹配 DECLARE/BEGIN 开始，到 END; 后跟换行和 / 的块
    pattern = re.compile(
        r"(?:DECLARE\b.*?)?BEGIN\b.*?END\s*;\s*\n\s*/\s*\n?",
        re.IGNORECASE | re.DOTALL
    )

    def repl(m):
        blocks.append(m.group(0))
        return '\n'

    new_raw = pattern.sub(repl, raw)
    return blocks, new_raw


def extract_execute_immediate(block):
    """
    从 PL/SQL 块里提取所有 EXECUTE IMMEDIATE 后面的动态 SQL 文本。
    支持：
      - 单字符串：EXECUTE IMMEDIATE 'DDL';
      - 跨行 || 拼接：EXECUTE IMMEDIATE '... ' || '''...''';
    处理单引号转义（'' → '）。
    """
    # 1) 还原 || 字符串拼接：把 '...' || '...' 相邻字符串的边界引号去掉
    #    '\s*||\s*' （右引号 + || + 左引号）整体删除，合并为一个字符串字面量
    merged = re.sub(r"'\s*\|\|\s*'", '', block)

    stmts = []
    # 2) 提取 EXECUTE IMMEDIATE '...'  （可能跨行，以 '; 结尾）
    for m in re.finditer(r"EXECUTE\s+IMMEDIATE\s+'(.*?)'\s*;", merged,
                         re.IGNORECASE | re.DOTALL):
        sql = m.group(1).replace("''", "'")
        stmts.append(sql.strip())
    return stmts


def ddl_to_idempotent(sql, table_comments=None, col_comments=None, block_col_comments=None,
                      global_table_comments=None, global_col_comments=None):
    """把单条达梦 DDL 转成 MySQL 幂等形式。返回 MySQL 语句字符串。"""
    table_comments = table_comments or {}
    col_comments = col_comments or {}
    block_col_comments = block_col_comments or {}
    global_table_comments = global_table_comments or {}
    global_col_comments = global_col_comments or {}
    s = strip_schema(sql)
    s = quotes_to_backticks(s)
    s = convert_type(s)
    # 达梦的「省略该列时取序列下一个值」→ MySQL 的 AUTO_INCREMENT
    # ⚠️ 不能直接删掉：种子/配置类 INSERT 不写 id，MySQL 严格模式下会报
    #    Field 'id' doesn't have a default value（达梦侧靠 SEQ 默认值兜底）。
    s = re.sub(r'DEFAULT\s+`?\w+`?\.`?SEQ_\w+`?\.NEXTVAL\s*', 'AUTO_INCREMENT ', s,
               flags=re.IGNORECASE)
    s = re.sub(r'DEFAULT\s+`?SEQ_\w+`?\.NEXTVAL\s*', 'AUTO_INCREMENT ', s, flags=re.IGNORECASE)

    # CREATE SEQUENCE → 丢弃
    if re.match(r'(?is)^\s*CREATE\s+SEQUENCE', s.strip()):
        return None

    # CONSTRAINT `pk` PRIMARY KEY → PRIMARY KEY
    s = re.sub(r'CONSTRAINT\s+`?\w+`?\s+PRIMARY\s+KEY', 'PRIMARY KEY', s, flags=re.IGNORECASE)

    # CREATE TABLE → IF NOT EXISTS（含注释注入）
    m = re.match(r'(?is)^CREATE\s+TABLE\s+`?(\w+)`?\s*\((.*)\)\s*;?\s*$', s.strip())
    if m:
        # 合并本文件注释与全局注释（本文件优先）
        merged_t = {**global_table_comments, **table_comments}
        merged_c = {**global_col_comments, **col_comments}
        return build_create_table(m.group(1), m.group(2), merged_t, merged_c,
                                  global_table_comments, global_col_comments)

    # 压缩空白（非 CREATE TABLE）
    s = re.sub(r'[ \t]+', ' ', s).strip()

    # CREATE [UNIQUE] INDEX
    m = re.match(
        r'(?is)^CREATE\s+(UNIQUE\s+)?INDEX\s+`?(\w+)`?\s+ON\s+`?(\w+)`?\s*\(([^)]+)\)\s*;?$', s)
    if m:
        uniq = 1 if m.group(1) else 0
        idx, tbl, cols = m.group(2), m.group(3), m.group(4).strip()
        return '\n'.join(convert_index_statements(tbl, idx, uniq, cols))

    # ALTER TABLE ... ADD [COLUMN]
    m = re.match(
        r'(?is)^ALTER\s+TABLE\s+`?(\w+)`?\s+ADD\s+(?:COLUMN\s+)?`?(\w+)`?\s+(.+?);?$', s)
    if m:
        tbl, col, coldef = m.group(1), m.group(2), m.group(3).rstrip(';').strip()
        # 合并块内该列的注释（MySQL ADD COLUMN 支持 COMMENT）
        cmt = block_col_comments.get((tbl, col))
        if cmt and 'COMMENT' not in coldef.upper():
            coldef += " COMMENT '" + cmt + "'"
        # 整体作为存储过程字符串参数：所有单引号双写转义（SQL 标准）
        coldef_sql = coldef.replace("'", "''")
        return f"CALL zing_add_column('{tbl}', '{col}', '{coldef_sql}');"

    # ALTER TABLE ... MODIFY [COLUMN]（天然幂等，直接执行）
    if re.match(r'(?is)^ALTER\s+TABLE\s+`?(\w+)`?\s+MODIFY\s+', s):
        return s if s.endswith(';') else s + ';'

    return s if s.endswith(';') else s + ';'


def scan_global_comments(src_dir):
    """扫描全部达梦源文件，建立跨文件的全局表/列注释字典（兜底用）。"""
    g_tbl, g_col = {}, {}
    for fn in os.listdir(src_dir):
        if not fn.endswith('.sql'):
            continue
        raw = open(os.path.join(src_dir, fn), encoding='utf-8').read()
        raw = strip_schema(quotes_to_backticks(raw))
        # 块内 || 拼接的 COMMENT 也还原
        raw = re.sub(r"'\s*\|\|\s*'", '', raw)
        for m in re.finditer(
            r"COMMENT\s+ON\s+TABLE\s+`?(\w+)`?\s+IS\s+'((?:[^']|'')*)'\s*;",
            raw, re.IGNORECASE
        ):
            g_tbl.setdefault(m.group(1), m.group(2).replace("''", "'"))
        for m in re.finditer(
            r"COMMENT\s+ON\s+COLUMN\s+`?(\w+)`?\.`?(\w+)`?\s+IS\s+'((?:[^']|'')*)'\s*;",
            raw, re.IGNORECASE
        ):
            g_col.setdefault((m.group(1), m.group(2)), m.group(3).replace("''", "'"))
    return g_tbl, g_col


def build_create_table(table_name, body, table_comments, col_comments,
                       global_table_comments=None, global_col_comments=None):
    """构造 CREATE TABLE IF NOT EXISTS，注入列/表注释。"""
    global_table_comments = global_table_comments or {}
    global_col_comments = global_col_comments or {}
    lines = body.split('\n')
    new_lines = []
    for line in lines:
        stripped = line.strip().rstrip(',').strip()
        if not stripped:
            continue
        # 行尾逗号先剥离，统一处理
        has_comma = line.strip().endswith(',')
        pk_match = re.match(r"CONSTRAINT\s+`?\w+`?\s+PRIMARY\s+KEY\s*\(([^)]+)\)",
                            stripped, re.IGNORECASE)
        if pk_match:
            new_lines.append(f'  PRIMARY KEY ({pk_match.group(1)})')
            continue
        col_match = re.match(r'^`(\w+)`\s+(.*)', stripped)
        if col_match:
            col_name, col_rest = col_match.group(1), col_match.group(2)
            col_rest = convert_type(col_rest)
            # 序列默认值 → AUTO_INCREMENT（保留「省略 id 也能插」的语义，见 ddl_to_idempotent 注释）
            col_rest = re.sub(r'DEFAULT\s+`?\w+`?\.`?SEQ_\w+`?\.NEXTVAL\s*', 'AUTO_INCREMENT ',
                              col_rest, flags=re.IGNORECASE)
            col_rest = re.sub(r'DEFAULT\s+`?SEQ_\w+`?\.NEXTVAL\s*', 'AUTO_INCREMENT ',
                              col_rest, flags=re.IGNORECASE)
            cmt = col_comments.get((table_name, col_name)) \
                or global_col_comments.get((table_name, col_name))
            if cmt:
                cmt_e = cmt.replace("\\", "\\\\").replace("'", "\\'")
                col_rest += f" COMMENT '{cmt_e}'"
            new_lines.append(f'  `{col_name}` {col_rest}')
            continue
        new_lines.append(convert_type(stripped))

    table_cmt = table_comments.get(table_name) \
        or global_table_comments.get(table_name)
    suffix = ''
    if table_cmt:
        cmt_e = table_cmt.replace("\\", "\\\\").replace("'", "\\'")
        suffix = f" COMMENT='{cmt_e}'"
    # 除最后一行外，每行末尾补逗号
    joined = '\n'.join(
        (ln + (',' if i < len(new_lines) - 1 else ''))
        for i, ln in enumerate(new_lines)
    )
    return f'CREATE TABLE IF NOT EXISTS `{table_name}` (\n{joined}\n){suffix};'


def _helper_body():
    return """DROP PROCEDURE IF EXISTS zing_add_column;
DROP PROCEDURE IF EXISTS zing_add_index;

DELIMITER $$

CREATE PROCEDURE zing_add_column(IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_def TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND COLUMN_NAME = p_column
    ) THEN
        SET @s = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_def);
        PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END IF;
END$$

CREATE PROCEDURE zing_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64),
                                IN p_unique TINYINT, IN p_cols VARCHAR(500))
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = p_table AND INDEX_NAME = p_index
    ) THEN
        SET @s = CONCAT('CREATE ', IF(p_unique = 1, 'UNIQUE ', ''),
                        'INDEX `', p_index, '` ON `', p_table, '` (', p_cols, ')');
        PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
    END IF;
END$$

DELIMITER ;
"""


def _helper_header(target_db, scope):
    return (
        "-- ============================================================\n"
        f"-- MySQL/MariaDB 幂等 DDL 辅助存储过程（{scope}）\n"
        "--   zing_add_column(table, column, coldef)  列不存在才 ADD\n"
        "--   zing_add_index(table, index, unique, cols)  索引不存在才 CREATE\n"
        "-- 存储过程是库级对象，故按库分别提供：\n"
        "--   00b_idempotent_helpers_doctor.sql  建在 zing_doctor_db_prod\n"
        "--   00c_idempotent_helpers_icu.sql     建在 zing_icu_db_prod（异机部署时用）\n"
        "-- 全新部署 / 增量升级通用，可重复执行。需用 mysql/mariadb 客户端执行（含 DELIMITER）。\n"
        "-- ============================================================\n\n"
        f"USE `{target_db}`;\n\n"
    )


HELPERS_DOCTOR = _helper_header("zing_doctor_db_prod", "应用主库") + _helper_body()
HELPERS_ICU = _helper_header("zing_icu_db_prod", "ICU 只读库") + _helper_body()



def convert(input_path, output_path, global_table_comments=None, global_col_comments=None):
    global_table_comments = global_table_comments or {}
    global_col_comments = global_col_comments or {}
    with open(input_path, 'r', encoding='utf-8') as f:
        raw = f.read().replace('\r\n', '\n')

    # 0) 先归一化引号/schema，便于 COMMENT 正则匹配（块内外都能识别）
    normalized = strip_schema(raw)
    normalized = quotes_to_backticks(normalized)

    # 1) 收集 COMMENT ON（在提取 PL/SQL 块之前，覆盖全部表）
    table_comments = {}
    for m in re.finditer(
        r"COMMENT\s+ON\s+TABLE\s+`?(\w+)`?\s+IS\s+'((?:[^']|'')*)'\s*;",
        normalized, re.IGNORECASE
    ):
        table_comments[m.group(1)] = m.group(2).replace("''", "'")

    col_comments = {}
    for m in re.finditer(
        r"COMMENT\s+ON\s+COLUMN\s+`?(\w+)`?\.`?(\w+)`?\s+IS\s+'((?:[^']|'')*)'\s*;",
        normalized, re.IGNORECASE
    ):
        col_comments[(m.group(1), m.group(2))] = m.group(3).replace("''", "'")

    # 2) 提取并转换 PL/SQL 块（在归一化后的文本上提取，保证引号统一）
    blocks, body_text = extract_plsql_blocks(normalized)
    plsql_outputs = []
    # 块内 COMMENT ON COLUMN（ALTER ADD 新列时顺带的注释），按 (table,col) 收集
    block_col_comments = {}

    def parse_stmt(stmt):
        """归一化一条动态 SQL 并识别 COMMENT ON COLUMN；返回类型或 None。"""
        n = strip_schema(quotes_to_backticks(stmt))
        cm = re.match(
            r"(?is)COMMENT\s+ON\s+COLUMN\s+`?(\w+)`?\.`?(\w+)`?\s+IS\s+'((?:[^']|'')*)'\s*;?$",
            n.strip())
        if cm:
            return ('comment', (cm.group(1), cm.group(2)), cm.group(3).replace("''", "'"))
        return ('ddl', None, stmt)

    for blk in blocks:
        stmts = extract_execute_immediate(blk)
        parsed = [parse_stmt(s) for s in stmts]
        # 第一遍：收集本块 COMMENT
        local_comments = {}
        for kind, key, val in parsed:
            if kind == 'comment':
                local_comments[key] = val
                block_col_comments[key] = val
        # 第二遍：转换 DDL（此时块内注释已就绪，可合并进 ADD COLUMN）
        for kind, key, val in parsed:
            if kind == 'comment':
                continue
            conv = ddl_to_idempotent(val, table_comments, col_comments, local_comments,
                                     global_table_comments, global_col_comments)
            if conv:
                plsql_outputs.append(conv)
    raw = body_text

    # 3) 删除 CREATE SEQUENCE 语句
    raw = re.sub(r'CREATE\s+SEQUENCE\s+[^;]+;\s*\n?', '', raw, flags=re.IGNORECASE)

    # 4) 删除 COMMENT ON 语句（已注入列定义）
    raw = re.sub(r"COMMENT\s+ON\s+(TABLE|COLUMN)\s+[^;]+;\s*\n?", '', raw,
                 flags=re.IGNORECASE)

    # 5) CREATE TABLE 转 IF NOT EXISTS + 注入注释
    def convert_create_table(match):
        merged_t = {**global_table_comments, **table_comments}
        merged_c = {**global_col_comments, **col_comments}
        return build_create_table(match.group(1), match.group(2),
                                  merged_t, merged_c,
                                  global_table_comments, global_col_comments)

    raw = re.sub(r'CREATE\s+TABLE\s+`?(\w+)`?\s*\((.*?)\)\s*;',
                 convert_create_table, raw, flags=re.IGNORECASE | re.DOTALL)

    # 6) CREATE INDEX（裸写）→ 幂等存储过程
    def convert_index(m):
        uniq = 1 if m.group(1) else 0
        idx, tbl, cols = m.group(2), m.group(3), m.group(4).strip()
        return '\n'.join(convert_index_statements(tbl, idx, uniq, cols))

    raw = re.sub(
        r'CREATE\s+(UNIQUE\s+)?INDEX\s+`?(\w+)`?\s+ON\s+`?(\w+)`?\s*\(([^)]+)\)\s*;?',
        convert_index, raw, flags=re.IGNORECASE
    )

    # 7) 裸 ALTER ADD COLUMN → 幂等存储过程
    def convert_alter_add(m):
        tbl, col, coldef = m.group(1), m.group(2), m.group(3).strip()
        return f"CALL zing_add_column('{tbl}', '{col}', '{coldef}');"

    raw = re.sub(
        r'(?is)ALTER\s+TABLE\s+`?(\w+)`?\s+ADD\s+(?:COLUMN\s+)?`?(\w+)`?\s+([^;]+);',
        convert_alter_add, raw
    )

    # 7b) 把本文件 COMMENT ON 的列注释合并进所有 zing_add_column 调用
    #     （覆盖「本文件 ALTER ADD 列 + 本文件 COMMENT ON」，含块内/裸写两种）
    def inject_addcolumn_comment(text):
        def repl(m):
            tbl, col, coldef = m.group(1), m.group(2), m.group(3)
            # 归一化：把此前可能已做的 '' 转义还原，避免双重转义
            coldef = coldef.replace("''", "'")
            cmt = col_comments.get((tbl, col)) or block_col_comments.get((tbl, col))
            if cmt and 'COMMENT' not in coldef.upper():
                coldef = coldef + " COMMENT '" + cmt + "'"
            # 统一转义一次：列定义内单引号双写
            coldef_sql = coldef.replace("'", "''")
            return f"CALL zing_add_column('{tbl}', '{col}', '{coldef_sql}');"
        return re.sub(
            r"CALL\s+zing_add_column\('(\w+)',\s*'(\w+)',\s*'(.*?)'\)\s*;",
            repl, text, flags=re.IGNORECASE | re.DOTALL
        )

    raw = inject_addcolumn_comment(raw)
    plsql_outputs = [inject_addcolumn_comment(s) for s in plsql_outputs]

    # 8) 裸 ALTER MODIFY 保留（转反引号/类型已在前面处理）
    # 9) 清理残留 schema 前缀和空行
    for sch in SCHEMAS:
        raw = raw.replace(f'`{sch}`.', '')
    raw = re.sub(r'\n{3,}', '\n\n', raw)

    # 去掉 COMMIT;（MySQL DDL 自动提交，且无需显式 COMMIT）
    raw = re.sub(r'(?m)^\s*COMMIT\s*;\s*$', '', raw)

    # 10) 组装
    basename = os.path.basename(input_path)
    if basename == '00_init_user.sql':
        raw = (
            '-- MySQL 初始化：创建数据库（MySQL 中 schema = database）\n'
            "CREATE DATABASE IF NOT EXISTS zing_doctor_db_prod DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;\n"
            "CREATE DATABASE IF NOT EXISTS zing_icu_db_prod DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;\n"
        )
    else:
        target_db = 'zing_icu_db_prod' if 'icu' in basename.lower() else 'zing_doctor_db_prod'
        needs_helpers = bool(plsql_outputs) or bool(re.search(r'zing_add_(column|index)', raw))
        header = (
            '-- ============================================================\n'
            '-- MySQL 8.x 版本（由达梦 DM8 脚本自动转换 + 人工校验）\n'
            '-- 主键由应用雪花算法生成（MyBatis-Plus ASSIGN_ID），显式插入时以插入值为准；\n'
            '-- 仅当 INSERT 省略 id 时由 AUTO_INCREMENT 兜底（对应达梦原有的 SEQ.NEXTVAL 默认值）\n'
            '-- 执行：mysql -uroot -p < 本文件（需先执行 00b_idempotent_helpers.sql）\n'
            '-- ============================================================\n'
            'SET NAMES utf8mb4;\n'
            f'USE `{target_db}`;\n\n'
        )
        parts = [header]
        if needs_helpers:
            parts.append('-- ---- 以下为原达梦 PL/SQL 幂等块转换得到的 DDL ----\n')
            parts.append('\n'.join(plsql_outputs) + '\n\n')
        parts.append(raw.strip())
        parts.append('\n')
        raw = ''.join(parts)

    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, 'w', encoding='utf-8', newline='\n') as f:
        f.write(raw)


if __name__ == '__main__':
    src_dir = r'C:\Users\LDY\Doubao\chats\2026-09-04\new-chat\zing-doctor\sql'
    dst_dir = r'C:\Users\LDY\Doubao\chats\2026-09-04\new-chat\zing-doctor\sql\mysql'

    # 先写辅助存储过程脚本（按库拆分，支持主库/ICU 异机部署）
    os.makedirs(dst_dir, exist_ok=True)
    with open(os.path.join(dst_dir, '00b_idempotent_helpers_doctor.sql'), 'w',
              encoding='utf-8', newline='\n') as f:
        f.write(HELPERS_DOCTOR)
    print('  OK: 00b_idempotent_helpers_doctor.sql')
    with open(os.path.join(dst_dir, '00c_idempotent_helpers_icu.sql'), 'w',
              encoding='utf-8', newline='\n') as f:
        f.write(HELPERS_ICU)
    print('  OK: 00c_idempotent_helpers_icu.sql')

    # 全局注释字典（跨文件兜底，如 08 的注释对应 07 建的列）
    g_tbl, g_col = scan_global_comments(src_dir)

    files = sorted(f for f in os.listdir(src_dir) if f.endswith('.sql'))
    for fname in files:
        try:
            convert(os.path.join(src_dir, fname), os.path.join(dst_dir, fname), g_tbl, g_col)
            print(f'  OK: {fname}')
        except Exception as e:
            print(f'  FAIL: {fname}: {e}')
            import traceback
            traceback.print_exc()
    print('done')
