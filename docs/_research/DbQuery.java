import java.sql.*;

/**
 * 只读查询小工具：java DbQuery <url> <user> <pass> <sql> [maxRows]
 * 仅用于连库核实表结构/样本数据；默认最多 40 行，列用 " | " 分隔。
 */
public class DbQuery {
    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("usage: DbQuery <url> <user> <pass> <sql> [maxRows]");
            System.exit(2);
        }
        String url = args[0], user = args[1], pass = args[2], sql = args[3];
        if (sql.startsWith("@")) {
            sql = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(sql.substring(1))),
                    java.nio.charset.StandardCharsets.UTF_8);
        }
        int maxRows = args.length > 4 ? Integer.parseInt(args[4]) : 40;
        try (Connection c = DriverManager.getConnection(url, user, pass);
             Statement st = c.createStatement()) {
            st.setMaxRows(maxRows + 1);
            boolean hasRs = st.execute(sql);
            if (!hasRs) {
                System.out.println("updated/ok: " + st.getUpdateCount());
                return;
            }
            try (ResultSet rs = st.getResultSet()) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                StringBuilder h = new StringBuilder();
                for (int i = 1; i <= n; i++) {
                    if (i > 1) h.append(" | ");
                    h.append(md.getColumnLabel(i));
                }
                System.out.println(h);
                int rows = 0;
                while (rs.next() && rows < maxRows) {
                    StringBuilder b = new StringBuilder();
                    for (int i = 1; i <= n; i++) {
                        if (i > 1) b.append(" | ");
                        String v = rs.getString(i);
                        b.append(v == null ? "NULL" : v.replace("\r", " ").replace("\n", " "));
                    }
                    System.out.println(b);
                    rows++;
                }
                System.out.println("(" + rows + " rows)");
            }
        }
    }
}
