-- 修复 Liquibase 校验和：002-create-schema.yaml::create-schema-tables
-- 原因：该 changeset 或其引用的 schema.sql 在首次执行后被修改，导致校验和不匹配。
-- 适用：已应用过该 changeset 的库，仅同步校验和以便启动通过。
-- 执行前请确认当前库中表结构已正确（若曾通过 015 等后续 changeset 增加 error_message 等，则无需再执行 DDL）。

-- 若当前默认 schema 为 tiny_web，可直接用 databasechangelog；否则用 tiny_web.databasechangelog
UPDATE tiny_web.databasechangelog
SET md5sum = '9:29b3fc2e5b2a95f5735a3bad0fcccb0f'
WHERE filename = 'db/changelog/002-create-schema.yaml'
  AND id = 'create-schema-tables'
  AND author = 'tiny';
