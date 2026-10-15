import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/**
 * SQL 脚本执行器（与 install.sh 的 DbInit 同套路）：
 *   java DbScript <url> <user> <pass> <file1.sql> [file2.sql ...]
 * 按「独占一行的 /」切分语句；跳过 -- 注释行；逐条执行并打印结果。
 * 仅用于执行项目内幂等脚本（50/51/52）。
 */
public class DbScript {
    public static void main(String[] args) throws Exception {
        String url = args[0], user = args[1], pass = args[2];
        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            for (int i = 3; i < args.length; i++) {
                System.out.println("== " + args[i]);
                List<String> lines = Files.readAllLines(Paths.get(args[i]), StandardCharsets.UTF_8);
                List<String> stmts = split(lines);
                System.out.println("   lines=" + lines.size() + " parsed statements: " + stmts.size());
                for (String s : stmts) {
                    System.out.println("   >>> " + s.substring(0, Math.min(80, s.length())).replace('\n', ' '));
                }
                int ok = 0, skip = 0;
                for (String s : stmts) {
                    String t = s.trim();
                    if (t.isEmpty()) continue;
                    try (Statement st = c.createStatement()) {
                        boolean hasRs = st.execute(t);
                        if (hasRs) { try (ResultSet rs = st.getResultSet()) { /* consume */ } }
                        ok++;
                    } catch (SQLException e) {
                        String msg = String.valueOf(e.getMessage());
                        if (msg.contains("已存在") || msg.toLowerCase().contains("exists") || msg.contains("名字已使用")) {
                            skip++;
                        } else {
                            System.out.println("  [FAIL] " + t.substring(0, Math.min(120, t.length())).replace('\n', ' '));
                            System.out.println("         " + msg);
                            throw e;
                        }
                    }
                }
                System.out.println("   ok=" + ok + " skip=" + skip);
            }
        }
    }

    private static List<String> split(List<String> lines) {
        // 混合策略：
        //  1) 以「独占一行的 /」切块（PL/SQL 块的 disql 终止符）；
        //  2) 块若以 DECLARE/BEGIN 开头 → 整块一条语句（JDBC 可直接执行，去尾部分号）；
        //  3) 其余块按引号感知的「;」切分（字符串字面量里的分号不切，
        //     如 EXECUTE IMMEDIATE 的 DDL、带引号的中文备注）。
        List<List<String>> chunks = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        for (String raw : lines) {
            String t = raw.trim();
            if (t.startsWith("--") || t.isEmpty()) continue;
            if (t.equals("/")) {
                if (!cur.isEmpty()) { chunks.add(cur); cur = new ArrayList<>(); }
                continue;
            }
            cur.add(raw);
        }
        if (!cur.isEmpty()) chunks.add(cur);

        List<String> out = new ArrayList<>();
        for (List<String> chunk : chunks) {
            String head = chunk.get(0).trim().toUpperCase();
            if (head.startsWith("DECLARE") || head.startsWith("BEGIN")) {
                String body = String.join("\n", chunk).trim();
                if (body.endsWith(";")) body = body.substring(0, body.length() - 1);
                out.add(body);
                continue;
            }
            StringBuilder buf = new StringBuilder();
            boolean inQuote = false;
            for (String line : chunk) {
                for (int i = 0; i < line.length(); i++) {
                    char ch = line.charAt(i);
                    if (ch == '\'') { inQuote = !inQuote; buf.append(ch); }
                    else if (ch == ';' && !inQuote) {
                        String st = buf.toString().trim();
                        if (!st.isEmpty()) out.add(st);
                        buf.setLength(0);
                    } else buf.append(ch);
                }
                buf.append('\n');
            }
            String st = buf.toString().trim();
            if (!st.isEmpty()) out.add(st.endsWith(";") ? st.substring(0, st.length() - 1) : st);
        }
        return out;
    }
}
