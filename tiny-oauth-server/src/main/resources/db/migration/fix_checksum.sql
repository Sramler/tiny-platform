-- 修复 Liquibase 校验和问题
-- 清除旧的校验和记录，让 Liquibase 使用新的校验和

-- 方法1：删除旧的校验和记录（推荐）
DELETE FROM databasechangelog 
WHERE id = 'init-default-dicts' 
  AND author = 'tiny' 
  AND md5sum = '9:51ddacb931e8b58d14ef9ebd577b72e7';

-- 方法2：如果方法1不行，可以更新校验和记录
-- UPDATE databasechangelog 
-- SET md5sum = '9:19487149e95369d870f3dc5449723aeb'
-- WHERE id = 'init-default-dicts' 
--   AND author = 'tiny';

