import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 配置导出工具：把达梦医生库里的「配置类」表全量导出成可执行的 INSERT 脚本。
 *
 * 与 DmQuery 的区别：DmQuery 只做 TSV 预览（400 行截断、不转义），本工具
 *   · 不截断，全量读取；
 *   · 按 JDBC 类型格式化字面量（NULL / 数值 / 日期 / 字符串）；
 *   · 单引号转义、换行用 CHR(10) 拼接、超长文本按 2000 字符分片（达梦字符串字面量有长度上限）；
 *   · 同时产出达梦版与 MySQL/MariaDB 版两份脚本。
 *
 * 用法（项目根目录，口令用 -Ddm.pass 传，不要写进源码）：
 *   javac -encoding UTF-8 -cp "lib/DmJdbcDriver18-8.1.3.140.jar" -d tools\dm-query tools\dm-query\DmExport.java
 *   java -cp 'tools\dm-query;lib\DmJdbcDriver18-8.1.3.140.jar' '-Ddm.pass=口令' DmExport
 *
 * 可选：-Ddm.host（默认 172.88.1.184） -Ddm.port -Ddm.user -Ddm.out（输出目录）
 */
public class DmExport {

    private static final String SCHEMA = "zing_doctor_db_prod";

    /** 只导出配置类：{库内旧名, 改后的新名, 说明}。新名 = 全新部署时脚本建出来的表名。 */
    private static final String[][] TABLES = {
        {"zing_page_config",     "sys_page_config",            "页面注册（菜单 / 外链路径 / 前端路由）"},
        {"zing_sys_param",       "sys_param",                  "系统参数（归档地址、阈值等）"},
        {"zing_param_group",     "sys_param_group",            "参数分组"},
        {"zing_sys_user",        "sys_user",                   "账号（含密码哈希，重建后密码不变）"},
        {"zing_ddd_config",      "config_ddd",                 "DDD 值字典"},
        {"zing_mdro_config",     "config_mdro",                "MDRO 细菌分类"},
        {"zing_abx_drug_dict",   "config_abx_drug_dict",       "抗菌药字典"},
        {"zing_abx_word_config", "config_abx_word",            "抗菌药识别词库（白/黑名单）"},
        {"ards_prone_config",    "config_prone_item",          "ARDS 采集映射规则"},
        {"ards_prone_tp_tpl",    "config_prone_timepoint_tpl", "ARDS 时点模板"},
        {"apache2_config",       "config_apache2",             "APACHE II 评分配置"},
        {"sofa_config",          "config_sofa",                "SOFA 评分配置"},
        {"quality_index",        "quality_index",              "质控指标（含本地覆盖 local_override）"},
        {"quality_metric_def",   "quality_metric_def",         "指标定义"},
        {"quality_count_rule",   "quality_count_rule",         "计数规则"},
        {"quality_fact_def",     "quality_fact_def",           "事实层定义（含 select_cols 投影列）"},
        {"quality_def_history",  "quality_def_history",        "定义变更历史"},
    };

    public static void main(String[] args) throws Exception {
        String host = System.getProperty("dm.host", "172.88.1.184");
        String port = System.getProperty("dm.port", "34567");
        String user = System.getProperty("dm.user", "SYSDBA");
        String pass = System.getProperty("dm.pass");
        String outDir = System.getProperty("dm.out", "tools/restore-config");

        PrintStream out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, "UTF-8");
        Class.forName("dm.jdbc.driver.DmDriver");

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        StringBuilder dm = new StringBuilder();
        StringBuilder my = new StringBuilder();

        try (Connection c = DriverManager.getConnection("jdbc:dm://" + host + ":" + port, user, pass)) {
            header(dm, stamp, true);
            header(my, stamp, false);

            for (String[] t : TABLES) {
                String oldName = t[0], newName = t[1], memo = t[2];
                List<String> cols = columns(c, oldName);
                if (cols.isEmpty()) {
                    out.println("[SKIP] " + oldName + " (no columns / not found)");
                    continue;
                }
                String dmCols = quoteDm(cols);
                String myCols = quoteMy(cols);
                String select = "SELECT " + dmCols + " FROM \"" + SCHEMA + "\".\"" + oldName + "\"";

                StringBuilder dmBody = new StringBuilder();
                StringBuilder myBody = new StringBuilder();
                int n = 0;
                try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(select)) {
                    ResultSetMetaData md = rs.getMetaData();
                    while (rs.next()) {
                        n++;
                        dmBody.append("INSERT INTO \"").append(SCHEMA).append("\".\"").append(newName)
                             .append("\" (").append(dmCols).append(") VALUES (")
                             .append(rowValues(rs, md, false)).append(");\n");
                        myBody.append("INSERT INTO `").append(newName)
                             .append("` (").append(myCols).append(") VALUES (")
                             .append(rowValues(rs, md, true)).append(");\n");
                    }
                }

                dm.append("\n-- ===== ").append(newName).append(" (").append(n).append(" rows) =====\n")
                  .append("-- source: ").append(SCHEMA).append(".").append(oldName)
                  .append("  |  ").append(memo).append("\n");
                my.append("\n-- ===== ").append(newName).append(" (").append(n).append(" rows) =====\n")
                  .append("-- source: ").append(SCHEMA).append(".").append(oldName)
                  .append("  |  ").append(memo).append("\n");
                if (n > 0) {
                    // 配置表按「整表重置为快照」处理：全新库上顺带覆盖出厂种子，语义明确、不会因唯一键冲突中断
                    dm.append("DELETE FROM \"").append(SCHEMA).append("\".\"").append(newName).append("\";\n");
                    my.append("DELETE FROM `").append(newName).append("`;\n");
                }
                dm.append(dmBody);
                my.append(myBody);
                out.println("[OK] " + oldName + " -> " + newName + " : " + n + " rows, " + cols.size() + " cols");
            }
        }

        Path dir = Paths.get(outDir);
        Files.createDirectories(dir);
        Path f1 = dir.resolve("restore_config_dm_" + stamp + ".sql");
        Path f2 = dir.resolve("restore_config_mysql_" + stamp + ".sql");
        Files.write(f1, dm.toString().getBytes(StandardCharsets.UTF_8));
        Files.write(f2, my.toString().getBytes(StandardCharsets.UTF_8));
        out.println("WROTE " + f1 + " (" + dm.length() + " chars)");
        out.println("WROTE " + f2 + " (" + my.length() + " chars)");
    }

    private static void header(StringBuilder sb, String stamp, boolean isDm) {
        sb.append("-- ============================================================\n")
          .append("-- 医生系统配置还原脚本（").append(stamp).append(" 生产快照）\n")
          .append("--\n")
          .append("-- 来源：达梦 ").append(SCHEMA).append("（由 tools/dm-query/DmExport.java 导出，全量）\n")
          .append("-- 用途：删库重建 / 全新部署后，把现场维护过的配置一次性灌回去。\n")
          .append("--\n")
          .append("-- ⚠️ 使用前提：先按正常流程跑完初始化脚本（01_schema.sql → … → 26），\n")
          .append("--    表按新名建好之后再执行本文件。表名已按 25/26 号 rename 后的新名生成。\n")
          .append("--\n")
          .append("-- ⚠️ 语义是「整表重置为本快照」：每张配置表先 DELETE 再 INSERT，\n")
          .append("--    因此新版本之后新增的出厂配置行会被清掉，只保留本快照里的行。\n")
          .append("--    重复执行是安全的（结果一致），但会把配置拉回本快照的状态。\n")
          .append("--\n")
          .append("-- ⚠️ 不要把它放进 install.sh 的 INCREMENTAL_SQL / MAIN_SQL：\n")
          .append("--    否则每次升级都会把现场配置重置成 ")
          .append(stamp).append(" 的旧值。只在重建后人工执行一次。\n")
          .append("--\n")
          .append("-- 未包含（不属于配置）：业务数据（俯卧位记录 / 评分记录 / 脓毒症记录）、\n")
          .append("--    计算结果（quality_metric_result / calc_run / calc_trace / monthly_report）、\n")
          .append("--    日志（archive_log / external_access_log）、运行时物化表（quality_fact_*）。\n")
          .append("--    这些要用 dexp / mysqldump 单独备份。\n")
          .append("--\n")
          .append("-- 执行：\n");
        if (isDm) {
            sb.append("--   disql SYSDBA/口令@host:port\n--   SQL> start 本文件\n--\n")
              .append("-- 执行后核对（行数应与本文件各段注释里的 rows 一致）：\n")
              .append("--   SELECT COUNT(*) FROM \"").append(SCHEMA).append("\".\"sys_page_config\";\n");
        } else {
            sb.append("--   mysql -uroot -p zing_doctor_db_prod < 本文件\n--\n")
              .append("-- 执行后核对：\n")
              .append("--   SELECT COUNT(*) FROM `sys_page_config`;\n");
        }
        sb.append("-- ============================================================\n\n");
        if (!isDm) {
            sb.append("SET NAMES utf8mb4;\nUSE `").append(SCHEMA).append("`;\n");
        }
    }

    private static List<String> columns(Connection c, String table) throws Exception {
        List<String> cols = new ArrayList<>();
        String sql = "SELECT COLUMN_NAME FROM ALL_TAB_COLUMNS WHERE OWNER='" + SCHEMA
                   + "' AND TABLE_NAME='" + table + "' ORDER BY COLUMN_ID";
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                cols.add(rs.getString(1));
            }
        }
        return cols;
    }

    private static String quoteDm(List<String> cols) {
        StringBuilder sb = new StringBuilder();
        for (String col : cols) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append('"').append(col).append('"');
        }
        return sb.toString();
    }

    private static String quoteMy(List<String> cols) {
        StringBuilder sb = new StringBuilder();
        for (String col : cols) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append('`').append(col).append('`');
        }
        return sb.toString();
    }

    private static String rowValues(ResultSet rs, ResultSetMetaData md, boolean mysql) throws Exception {
        StringBuilder sb = new StringBuilder();
        int cols = md.getColumnCount();
        for (int i = 1; i <= cols; i++) {
            if (i > 1) {
                sb.append(", ");
            }
            sb.append(literal(rs, i, md.getColumnType(i), mysql));
        }
        return sb.toString();
    }

    private static String literal(ResultSet rs, int i, int type, boolean mysql) throws Exception {
        Object o = rs.getObject(i);
        if (o == null) {
            return "NULL";
        }
        switch (type) {
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.INTEGER:
            case Types.BIGINT:
            case Types.NUMERIC:
            case Types.DECIMAL:
            case Types.FLOAT:
            case Types.DOUBLE:
            case Types.REAL:
                return rs.getBigDecimal(i).toPlainString();
            case Types.DATE:
            case Types.TIME:
            case Types.TIMESTAMP:
                String s = rs.getString(i);
                if (s == null) {
                    return "NULL";
                }
                if (s.length() > 19) {
                    s = s.substring(0, 19);
                }
                return mysql ? ("'" + s + "'") : ("TIMESTAMP '" + s + "'");
            default:
                String v = rs.getString(i);
                return v == null ? "NULL" : text(v, mysql);
        }
    }

    /** 字符串字面量：单引号转义；换行用 CHR(10)/CHAR(10) 拼接；超长分片避免超出字面量长度上限。 */
    private static String text(String v, boolean mysql) {
        String esc = v.replace("'", "''");
        List<String> segs = new ArrayList<>();
        String[] lines = esc.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                segs.add(mysql ? "CHAR(10)" : "CHR(10)");
            }
            String line = lines[i];
            for (int p = 0; p < line.length(); p += 2000) {
                segs.add("'" + line.substring(p, Math.min(line.length(), p + 2000)) + "'");
            }
        }
        if (segs.isEmpty()) {
            return "''";
        }
        if (segs.size() == 1) {
            return segs.get(0);
        }
        if (mysql) {
            return "CONCAT(" + String.join(", ", segs) + ")";
        }
        return String.join(" || ", segs);
    }
}
