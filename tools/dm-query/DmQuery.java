import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 达梦查询小工具：本机通常没有 disql，用 JDBC 驱动直连执行 SQL，TSV 打印结果。
 *
 * 用法（项目根目录）：
 *   javac -encoding UTF-8 -cp "lib/DmJdbcDriver18-8.1.3.140.jar" -d tools\dm-query tools\dm-query\DmQuery.java
 *   Get-Content my.sql -Raw -Encoding UTF8 | java -cp 'tools\dm-query;lib\DmJdbcDriver18-8.1.3.140.jar' '-Ddm.pass=口令' DmQuery
 *
 * 可选参数：-Ddm.host（默认见下） -Ddm.port -Ddm.user
 * 说明：多条 SQL 用 ; 分隔；单条最多返回 MAX_ROWS 行；SQL 文件若带 BOM 会自动剔除。
 */
public class DmQuery {

    private static final int MAX_ROWS = 400;

    private static PrintStream out;

    public static void main(String[] args) throws Exception {
        String host = System.getProperty("dm.host", "172.88.1.184");
        String port = System.getProperty("dm.port", "34567");
        String user = System.getProperty("dm.user", "SYSDBA");
        String pass = System.getProperty("dm.pass");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = System.in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        String raw = new String(bos.toByteArray(), "UTF-8").trim().replace("\uFEFF", "");

        out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8");
        Class.forName("dm.jdbc.driver.DmDriver");
        try (Connection c = DriverManager.getConnection("jdbc:dm://" + host + ":" + port, user, pass);
             Statement st = c.createStatement()) {
            st.setMaxRows(MAX_ROWS);
            for (String each : raw.split(";")) {
                String sql = each.trim();
                if (sql.isEmpty()) {
                    continue;
                }
                out.println("== " + sql);
                try {
                    boolean has = st.execute(sql);
                    while (has) {
                        printRs(st.getResultSet());
                        has = st.getMoreResults();
                    }
                } catch (SQLException e) {
                    out.println("!! " + e.getMessage());
                }
            }
        }
    }

    private static void printRs(ResultSet rs) throws Exception {
        if (rs == null) {
            return;
        }
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        StringBuilder head = new StringBuilder();
        for (int i = 1; i <= cols; i++) {
            if (i > 1) {
                head.append('\t');
            }
            head.append(md.getColumnLabel(i));
        }
        out.println(head);
        int row = 0;
        while (rs.next()) {
            row++;
            if (row > MAX_ROWS) {
                out.println("... 超过 " + MAX_ROWS + " 行已截断");
                break;
            }
            StringBuilder line = new StringBuilder();
            for (int i = 1; i <= cols; i++) {
                if (i > 1) {
                    line.append('\t');
                }
                String v = rs.getString(i);
                if (v != null) {
                    line.append(v.replace("\r", " ").replace("\n", " ").replace("\t", " "));
                }
            }
            out.println(line);
        }
        out.println("--- rows=" + Math.min(row, MAX_ROWS) + " ---");
    }
}
