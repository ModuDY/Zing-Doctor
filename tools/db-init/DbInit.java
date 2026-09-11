import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 达梦数据库初始化工具（纯 JDBC，无 Spring 依赖）。
 *
 * <p>用途：一键部署时连接达梦执行建表/种子 SQL 文件，替代手动 disql。
 * 复用项目 lib/ 下的达梦驱动（DmJdbcDriver18）。
 *
 * <p>用法：
 *   java -cp "lib/DmJdbcDriver18-8.1.3.140.jar:tools/db-init-classes" \
 *        DbInit "jdbc:dm://<host>:<port>" <user> <pass> <sqlFile...>
 *
 * <p>特性：
 *   1. 按分号拆分语句，自动忽略 "--" 行注释与单引号字符串内的分号，逐条执行；
 *   2. 幂等：CREATE SCHEMA / CREATE TABLE / CREATE [UNIQUE] INDEX 在执行前先查元数据，
 *      对象已存在则跳过（达梦 8.1.3.140 不支持 CREATE SCHEMA IF NOT EXISTS，
 *      CREATE TABLE/INDEX 也以"存在即跳过"方式保证脚本可重复执行）；
 *   3. 其余语句（COMMENT ON、INSERT、DELETE 等）直接执行，COMMENT 重复执行为覆盖（无害）。
 */
public class DbInit {

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("用法: DbInit <jdbcUrl> <user> <pass> <sqlFile...>");
            System.exit(2);
        }
        String url = args[0];
        String user = args[1];
        String pass = args[2];

        Class.forName("dm.jdbc.driver.DmDriver");
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            System.out.println("[DB] 已连接 " + url);
            for (int i = 3; i < args.length; i++) {
                runFile(conn, args[i]);
            }
        }
        System.out.println("[OK] 数据库初始化完成");
    }

    private static void runFile(Connection conn, String path) throws Exception {
        File f = new File(path);
        if (!f.exists()) {
            throw new IllegalStateException("SQL 文件不存在: " + path);
        }
        String content = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
        List<String> stmts = splitStatements(content);
        int ok = 0;
        try (Statement st = conn.createStatement()) {
            for (String s : stmts) {
                String t = s.trim();
                // 忽略空语句、纯 "/" 或 "/" 开头（disql CREATE SCHEMA 专属终止符，JDBC 无需执行）
                if (t.isEmpty() || t.equals("/") || t.startsWith("/")) {
                    continue;
                }
                if (isCreateSchema(t)) {
                    ensureSchema(st, t);
                } else if (isCreateTable(t)) {
                    ensureTable(st, t);
                } else if (isCreateIndex(t)) {
                    ensureIndex(st, t);
                } else if (isAlterAddColumn(t)) {
                    ensureColumn(st, t);
                } else if (isCommentOnColumn(t)) {
                    ensureCommentColumn(st, t);
                } else {
                    st.execute(t);
                }
                ok++;
            }
        }
        System.out.println("[OK] " + path + " 执行 " + ok + " 条语句");
    }

    // ------------------------------------------------------------------
    // 语句类型识别
    // ------------------------------------------------------------------

    /** 是否 CREATE SCHEMA 语句（大小写不敏感） */
    private static boolean isCreateSchema(String t) {
        return t.matches("(?is)^\\s*CREATE\\s+SCHEMA(\\s|$).*");
    }

    /** 是否 CREATE TABLE 语句 */
    private static boolean isCreateTable(String t) {
        return t.matches("(?is)^\\s*CREATE\\s+TABLE(\\s|$).*");
    }

    /** 是否 CREATE [UNIQUE] INDEX 语句 */
    private static boolean isCreateIndex(String t) {
        return t.matches("(?is)^\\s*CREATE\\s+(?:UNIQUE\\s+)?INDEX(\\s|$).*");
    }

    // ------------------------------------------------------------------
    // 幂等创建（对象已存在则跳过）
    // ------------------------------------------------------------------

    /** 幂等创建模式：达梦 8.1.3.140 不支持 CREATE SCHEMA IF NOT EXISTS，先查 SYS.SYSOBJECTS 判断 */
    private static void ensureSchema(Statement st, String createSql) throws Exception {
        String schema = extractSchemaName(createSql);
        if (schema == null) {
            st.execute(createSql);
            return;
        }
        boolean exists = existsByQuery(st,
                "SELECT COUNT(*) FROM SYS.SYSOBJECTS WHERE TYPE$='SCH' AND UPPER(NAME)=UPPER('" + esc(schema) + "')");
        if (exists) {
            System.out.println("[DB] 模式已存在，跳过创建: " + schema);
            return;
        }
        st.execute(createSql);
        System.out.println("[DB] 已创建模式: " + schema);
    }

    /** 幂等创建表：查 ALL_TABLES，已存在则跳过（不执行 CREATE TABLE） */
    private static void ensureTable(Statement st, String createSql) throws Exception {
        String[] parts = extractObjectName(createSql);
        String schema = parts.length >= 2 ? parts[0] : null;
        String name = parts[parts.length - 1];
        String cond = schema != null
                ? "UPPER(OWNER)=UPPER('" + esc(schema) + "')"
                : "UPPER(OWNER)=USER";
        boolean exists = existsByQuery(st,
                "SELECT COUNT(*) FROM ALL_TABLES WHERE " + cond + " AND UPPER(TABLE_NAME)=UPPER('" + esc(name) + "')");
        if (exists) {
            System.out.println("[DB] 表已存在，跳过: " + (schema != null ? schema + "." : "") + name);
            return;
        }
        st.execute(createSql);
        System.out.println("[DB] 已创建表: " + (schema != null ? schema + "." : "") + name);
    }

    /** 幂等创建索引：查 ALL_INDEXES，已存在则跳过；创建失败（如列列表已被其他索引覆盖）时警告并继续 */
    private static void ensureIndex(Statement st, String createSql) throws Exception {
        String[] parts = extractObjectName(createSql);
        String schema = parts.length >= 2 ? parts[0] : null;
        String name = parts[parts.length - 1];
        String cond = schema != null
                ? "UPPER(OWNER)=UPPER('" + esc(schema) + "')"
                : "UPPER(OWNER)=USER";
        boolean exists = existsByQuery(st,
                "SELECT COUNT(*) FROM ALL_INDEXES WHERE " + cond + " AND UPPER(INDEX_NAME)=UPPER('" + esc(name) + "')");
        if (exists) {
            System.out.println("[DB] 索引已存在，跳过: " + (schema != null ? schema + "." : "") + name);
            return;
        }
        try {
            st.execute(createSql);
            System.out.println("[DB] 已创建索引: " + (schema != null ? schema + "." : "") + name);
        } catch (Exception e) {
            // 达梦可能报"此列列表已索引"（同列不同名索引已存在）或其他索引冲突，警告并继续，不中断整个脚本
            String msg = e.getMessage() != null ? e.getMessage() : "";
            System.out.println("[DB] 索引创建跳过（" + msg.replaceAll("\\s+", " ").trim() + "）: "
                    + (schema != null ? schema + "." : "") + name);
        }
    }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    /** 是否 ALTER TABLE ... ADD [COLUMN] 语句（加列） */
    private static boolean isAlterAddColumn(String t) {
        return t.matches("(?is)^\\s*ALTER\\s+TABLE[\\s\\S]*?\\bADD\\s+(?:COLUMN\\s+)?\".*")
                || t.matches("(?is)^\\s*ALTER\\s+TABLE[\\s\\S]*?\\bADD\\s+(?!CONSTRAINT\\b)(?!PRIMARY\\b)(?!UNIQUE\\b)[A-Za-z_].*");
    }

    /** 幂等加列：查 ALL_TAB_COLUMNS，列已存在则跳过 */
    private static void ensureColumn(Statement st, String alterSql) throws Exception {
        // 表名：ALTER TABLE 之后的 "schema"."table" 或 table
        java.util.regex.Matcher tm = Pattern.compile(
                "(?is)^\\s*ALTER\\s+TABLE\\s+(?:\"([^\"]+)\"\\s*\\.\\s*)?\"([^\"]+)\"").matcher(alterSql);
        String schema = null, table = null;
        if (tm.find()) {
            schema = tm.group(1);
            table = tm.group(2);
        } else {
            java.util.regex.Matcher tm2 = Pattern.compile(
                    "(?is)^\\s*ALTER\\s+TABLE\\s+(?:([\\w$]+)\\s*\\.\\s*)?([\\w$]+)").matcher(alterSql);
            if (tm2.find()) {
                schema = tm2.group(1);
                table = tm2.group(2);
            }
        }
        // 列名：ADD [COLUMN] 之后的 "col" 或 col
        java.util.regex.Matcher cm = Pattern.compile(
                "(?is)\\bADD\\s+(?:COLUMN\\s+)?(?:\"([^\"]+)\"|([A-Za-z_][\\w$]*))").matcher(alterSql);
        String column = null;
        if (cm.find()) {
            column = cm.group(1) != null ? cm.group(1) : cm.group(2);
        }
        if (table == null || column == null) {
            st.execute(alterSql);
            return;
        }
        String cond = schema != null
                ? "UPPER(OWNER)=UPPER('" + esc(schema) + "')"
                : "UPPER(OWNER)=USER";
        boolean exists = existsByQuery(st,
                "SELECT COUNT(*) FROM ALL_TAB_COLUMNS WHERE " + cond
                        + " AND UPPER(TABLE_NAME)=UPPER('" + esc(table) + "')"
                        + " AND UPPER(COLUMN_NAME)=UPPER('" + esc(column) + "')");
        if (exists) {
            System.out.println("[DB] 列已存在，跳过加列: " + table + "." + column);
            return;
        }
        st.execute(alterSql);
        System.out.println("[DB] 已加列: " + table + "." + column);
    }

    /** 是否 COMMENT ON COLUMN 语句（列注释） */
    private static boolean isCommentOnColumn(String t) {
        return t.matches("(?is)^\\s*COMMENT\\s+ON\\s+COLUMN\\b.*");
    }

    /**
     * 幂等列注释：目标列不存在时跳过（典型场景：老环境跳过 CREATE TABLE，
     * 而该列由后续增量脚本 ALTER 补加，先执行到这里的 COMMENT 不应中断初始化）；
     * 列存在则正常执行注释。
     */
    private static void ensureCommentColumn(Statement st, String commentSql) throws Exception {
        // 依次取出 COLUMN 之后被双引号包裹的标识符：[schema,] table, column
        java.util.regex.Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(
                commentSql.replaceAll("(?is)^.*?\\bCOLUMN\\b", "COLUMN"));
        java.util.List<String> ids = new java.util.ArrayList<>();
        while (m.find()) {
            ids.add(m.group(1));
        }
        String schema = null, table = null, column = null;
        if (ids.size() >= 2) {
            column = ids.get(ids.size() - 1);
            table = ids.get(ids.size() - 2);
            if (ids.size() >= 3) {
                schema = ids.get(ids.size() - 3);
            }
        }
        if (table == null || column == null) {
            st.execute(commentSql);
            return;
        }
        String cond = schema != null
                ? "UPPER(OWNER)=UPPER('" + esc(schema) + "')"
                : "UPPER(OWNER)=USER";
        boolean exists = existsByQuery(st,
                "SELECT COUNT(*) FROM ALL_TAB_COLUMNS WHERE " + cond
                        + " AND UPPER(TABLE_NAME)=UPPER('" + esc(table) + "')"
                        + " AND UPPER(COLUMN_NAME)=UPPER('" + esc(column) + "')");
        if (!exists) {
            System.out.println("[DB] 列尚不存在，跳过列注释（待加列后由增量脚本补注释）: " + table + "." + column);
            return;
        }
        st.execute(commentSql);
    }

    /** 提取 CREATE SCHEMA 后的模式名（去双引号）；解析不到返回 null */
    private static String extractSchemaName(String t) {
        String[] parts = t.trim().split("\\s+");
        for (int i = 0; i + 1 < parts.length; i++) {
            if (parts[i].equalsIgnoreCase("SCHEMA")) {
                return parts[i + 1].replace("\"", "");
            }
        }
        return null;
    }

    /**
     * 提取 CREATE TABLE / CREATE [UNIQUE] INDEX 的目标对象限定名。
     * 支持 "schema"."name"、schema.name 或裸 name。
     * 返回数组：长度 2 为 {schema, name}，长度 1 为 {name}。
     */
    private static String[] extractObjectName(String t) {
        String body = t.replaceFirst("(?is)^\\s*CREATE\\s+(?:UNIQUE\\s+)?(?:TABLE|INDEX)\\s+", "").trim();
        Pattern ident = Pattern.compile("^(?:\"([^\"]+)\"|([^\\s.\\\"(]+))");
        Matcher m = ident.matcher(body);
        if (!m.find()) {
            return new String[]{body.split("\\s+")[0]};
        }
        String first = m.group(1) != null ? m.group(1) : m.group(2);
        String rest = body.substring(m.end()).trim();
        if (rest.startsWith(".")) {
            Matcher m2 = ident.matcher(rest.substring(1).trim());
            if (m2.find()) {
                String second = m2.group(1) != null ? m2.group(1) : m2.group(2);
                return new String[]{first, second};
            }
        }
        return new String[]{first};
    }

    /** 执行 count 查询，返回 >0 是否成立 */
    private static boolean existsByQuery(Statement st, String sql) throws Exception {
        try (ResultSet rs = st.executeQuery(sql)) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    /** SQL 单引号转义（防注入） */
    private static String esc(String s) {
        return s.replace("'", "''");
    }

    /** 按分号拆分 SQL；忽略 "--" 行注释与单引号字符串内的分号 */
    private static List<String> splitStatements(String content) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inStr = false;
        boolean inLineComment = false;
        int n = content.length();
        for (int i = 0; i < n; i++) {
            char c = content.charAt(i);
            if (inLineComment) {
                // 行注释内容不拼入语句（保留换行避免语句粘连）
                if (c == '\n') {
                    inLineComment = false;
                    cur.append(c);
                }
                continue;
            }
            if (!inStr && c == '-' && i + 1 < n && content.charAt(i + 1) == '-') {
                // 行注释：不拼入语句（避免语句开头带 "--" 导致语句类型正则匹配失败），换行保留防粘连
                inLineComment = true;
                i++;
                continue;
            }
            if (c == '\'') {
                if (i + 1 < n && content.charAt(i + 1) == '\'') {
                    // '' 转义的单引号
                    cur.append("''");
                    i++;
                    continue;
                }
                inStr = !inStr;
                cur.append(c);
                continue;
            }
            if (c == ';' && !inStr) {
                if (cur.toString().trim().length() > 0) {
                    out.add(cur.toString());
                }
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.toString().trim().length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }
}
