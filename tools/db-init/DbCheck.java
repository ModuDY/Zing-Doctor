import java.sql.*;

/**
 * 极简表存在性探测：install.sh 判断「老库升级 / 新库全量」用。
 *
 * 背景：此前的老库检测只在本机有 disql 时才执行 —— 没有 disql 的现场（走 JDBC
 * 通道）每次执行 install.sh 都会重跑全量初始化。CREATE 类语句会被 DbInit 幂等
 * 跳过，但 27 配置快照的整表 DELETE + INSERT 会照常执行，把现场维护过的配置
 * 打回快照日期（真实发生过：升级后质控配置 / 系统参数全部回滚）。
 *
 * 本类只查 ALL_TABLES 元数据（不 COUNT 业务数据），毫秒级返回，不依赖 disql。
 *
 * 用法:
 *   java -cp "lib/DmJdbcDriver18-8.1.3.140.jar:tools/db-init" DbCheck <url> <user> <pass> <schema> <table>
 *
 * 输出: stdout 为该表在 ALL_TABLES 中的命中行数（0 或 1），供 shell 判断。
 * 退出码: 0=查询成功；1=参数错误；2=连接或查询失败。
 */
public class DbCheck {
    public static void main(String[] args) {
        if (args.length < 5) {
            System.err.println("用法: DbCheck <url> <user> <pass> <schema> <table>");
            System.exit(1);
        }
        try {
            Class.forName("dm.jdbc.driver.DmDriver");
        } catch (ClassNotFoundException e) {
            System.err.println("[DbCheck] 找到达梦驱动: " + e.getMessage());
            System.exit(2);
        }
        try (Connection conn = DriverManager.getConnection(args[0], args[1], args[2]);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM ALL_TABLES WHERE UPPER(OWNER) = ? AND UPPER(TABLE_NAME) = ?")) {
            ps.setString(1, args[3].trim().toUpperCase());
            ps.setString(2, args[4].trim().toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                System.out.println(rs.next() ? rs.getLong(1) : 0L);
            }
            System.exit(0);
        } catch (Exception e) {
            System.err.println("[DbCheck] 连接或查询失败: " + e.getMessage());
            System.exit(2);
        }
    }
}
