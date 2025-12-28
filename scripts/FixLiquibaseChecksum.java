import java.sql.*;

/**
 * 修复 Liquibase 校验和问题
 * 清除旧的校验和记录，让 Liquibase 使用新的校验和
 */
public class FixLiquibaseChecksum {
    private static final String DB_URL = "jdbc:mysql://localhost:3306/tiny_web?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "Tianye0903.";

    public static void main(String[] args) {
        try {
            // 加载 MySQL 驱动
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // 连接数据库
            System.out.println("连接数据库...");
            Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            
            // 查询当前的校验和记录
            System.out.println("查询当前的校验和记录...");
            String selectSql = "SELECT id, author, md5sum, exectype, orderexecuted FROM databasechangelog WHERE id = 'init-default-dicts' AND author = 'tiny'";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(selectSql);
            
            int count = 0;
            while (rs.next()) {
                count++;
                System.out.println(String.format("  - ID: %s, Author: %s, MD5: %s, Type: %s, Order: %d",
                    rs.getString("id"), rs.getString("author"), rs.getString("md5sum"),
                    rs.getString("exectype"), rs.getInt("orderexecuted")));
            }
            rs.close();
            
            if (count == 0) {
                System.out.println("✓ 未找到相关记录，无需修复");
                conn.close();
                return;
            }
            
            // 删除旧的校验和记录
            System.out.println("\n删除旧的校验和记录...");
            String deleteSql = "DELETE FROM databasechangelog WHERE id = 'init-default-dicts' AND author = 'tiny' AND md5sum = '9:51ddacb931e8b58d14ef9ebd577b72e7'";
            int deletedCount = stmt.executeUpdate(deleteSql);
            
            if (deletedCount > 0) {
                System.out.println("✓ 成功删除 " + deletedCount + " 条旧校验和记录");
            } else {
                System.out.println("✓ 未找到需要删除的旧校验和记录");
            }
            
            // 提交事务
            conn.commit();
            System.out.println("\n✓ 修复完成！现在可以重启应用了。");
            
            stmt.close();
            conn.close();
            
        } catch (Exception e) {
            System.err.println("❌ 错误: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

