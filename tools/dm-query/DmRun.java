import java.io.PrintStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

/**
 * SQL 文件执行器：按「行」执行（每行一条语句，以 ; 结尾）。
 *
 * 为什么不用 DmQuery：DmQuery 按 ';' 切分整段文本，导出的 INSERT 里若某个配置值
 * 含分号（SQL 表达式 / select_cols 里很常见）就会被切成两条半截语句，报出来的语法错误是假的。
 * 导出脚本每行一条完整语句且不含换行，按行执行才是准确的。
 *
 * 用法：
 *   java -cp 'tools\dm-query;lib\DmJdbcDriver18-8.1.3.140.jar' '-Ddm.pass=口令' '-Ddm.file=路径' DmRun
 * 可选：-Ddm.schema=OTHER 把文件里的 "zing_doctor_db_prod" 换成该模式（冒烟测试用）
 *      -Ddm.stop=false  遇错不中断，继续执行并把错误汇总（默认 true）
 */
public class DmRun {

    public static void main(String[] args) throws Exception {
        String host = System.getProperty("dm.host", "172.88.1.184");
        String port = System.getProperty("dm.port", "34567");
        String user = System.getProperty("dm.user", "SYSDBA");
        String pass = System.getProperty("dm.pass");
        String file = System.getProperty("dm.file");
        String schema = System.getProperty("dm.schema");
        boolean stop = Boolean.parseBoolean(System.getProperty("dm.stop", "true"));

        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8");
        if (file == null) {
            out.println("!! 缺少 -Ddm.file");
            return;
        }

        List<String> lines = Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8);
        Class.forName("dm.jdbc.driver.DmDriver");
        int ok = 0, fail = 0;
        try (Connection c = DriverManager.getConnection("jdbc:dm://" + host + ":" + port, user, pass);
             Statement st = c.createStatement()) {
            for (int i = 0; i < lines.size(); i++) {
                String raw = lines.get(i).trim();
                if (raw.isEmpty() || raw.startsWith("--")) {
                    continue;
                }
                String sql = raw.endsWith(";") ? raw.substring(0, raw.length() - 1) : raw;
                if (schema != null) {
                    sql = sql.replace("\"zing_doctor_db_prod\"", "\"" + schema + "\"");
                }
                try {
                    st.execute(sql);
                    ok++;
                } catch (Exception e) {
                    fail++;
                    out.println("[FAIL] line " + (i + 1) + ": " + e.getMessage().replaceAll("\\s+", " "));
                    out.println("       " + raw.substring(0, Math.min(160, raw.length())));
                    if (stop) {
                        break;
                    }
                }
            }
        }
        out.println("OK=" + ok + " FAIL=" + fail);
    }
}
