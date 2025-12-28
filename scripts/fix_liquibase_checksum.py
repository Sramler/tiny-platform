#!/usr/bin/env python3
"""
修复 Liquibase 校验和问题
清除旧的校验和记录，让 Liquibase 使用新的校验和
"""
import pymysql
import sys

# 数据库配置
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'user': 'root',
    'password': 'Tianye0903.',
    'database': 'tiny_web',
    'charset': 'utf8mb4'
}

def fix_checksum():
    """修复 Liquibase 校验和"""
    try:
        # 连接数据库
        connection = pymysql.connect(**DB_CONFIG)
        cursor = connection.cursor()
        
        # 查询当前的校验和记录
        print("查询当前的校验和记录...")
        cursor.execute("""
            SELECT id, author, md5sum, exectype, orderexecuted 
            FROM databasechangelog 
            WHERE id = 'init-default-dicts' AND author = 'tiny'
        """)
        records = cursor.fetchall()
        
        if not records:
            print("✓ 未找到相关记录，无需修复")
            return
        
        print(f"找到 {len(records)} 条记录:")
        for record in records:
            print(f"  - ID: {record[0]}, Author: {record[1]}, MD5: {record[2]}, Type: {record[3]}, Order: {record[4]}")
        
        # 删除旧的校验和记录
        print("\n删除旧的校验和记录...")
        cursor.execute("""
            DELETE FROM databasechangelog 
            WHERE id = 'init-default-dicts' 
              AND author = 'tiny' 
              AND md5sum = '9:51ddacb931e8b58d14ef9ebd577b72e7'
        """)
        deleted_count = cursor.rowcount
        
        if deleted_count > 0:
            print(f"✓ 成功删除 {deleted_count} 条旧校验和记录")
        else:
            print("✓ 未找到需要删除的旧校验和记录")
        
        # 提交事务
        connection.commit()
        print("\n✓ 修复完成！现在可以重启应用了。")
        
    except pymysql.Error as e:
        print(f"❌ 数据库错误: {e}")
        sys.exit(1)
    except Exception as e:
        print(f"❌ 错误: {e}")
        sys.exit(1)
    finally:
        if 'connection' in locals():
            cursor.close()
            connection.close()

if __name__ == '__main__':
    fix_checksum()

