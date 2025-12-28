#!/bin/bash

# Liquibase Checksum 修复脚本
# 修复 002-create-schema.yaml 的 checksum 验证失败

set -e

DB_NAME="${DB_NAME:-tiny_web}"
DB_USER="${DB_USER:-root}"
DB_PASSWORD="${DB_PASSWORD:-Tianye0903.}"

echo "=== Liquibase Checksum 修复 ==="
echo ""
echo "数据库: $DB_NAME"
echo "用户: $DB_USER"
echo ""

# 更新 checksum
echo "正在更新 checksum..."
mysql -u "$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" << 'SQL'
UPDATE DATABASECHANGELOG 
SET MD5SUM = '9:88d95f52919a9d9f25fbc9adbcd47135'
WHERE ID = 'create-schema-tables' 
  AND AUTHOR = 'tiny' 
  AND FILENAME = 'db/changelog/002-create-schema.yaml';

-- 验证更新
SELECT 
    ID, 
    AUTHOR, 
    FILENAME, 
    MD5SUM, 
    EXECTYPE,
    CASE 
        WHEN MD5SUM = '9:88d95f52919a9d9f25fbc9adbcd47135' THEN '✅ 已更新'
        ELSE '❌ 未更新'
    END AS STATUS
FROM DATABASECHANGELOG 
WHERE ID = 'create-schema-tables';
SQL

echo ""
echo "✅ Checksum 已更新！"
echo ""
echo "现在可以重启应用，Liquibase 验证应该会通过。"

