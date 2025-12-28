#!/bin/bash

# 迁移脚本：将 sys 包下的代码迁移到新的包结构

BASE_DIR="src/main/java/com/tiny/platform"

# 1. 修复已复制的实体文件的包名
echo "修复实体文件包名..."

# User.java
sed -i '' '1,3d' ${BASE_DIR}/infrastructure/auth/user/domain/User.java
sed -i '' '1i\
package com.tiny.platform.infrastructure.auth.user.domain;
' ${BASE_DIR}/infrastructure/auth/user/domain/User.java
sed -i '' 's/import com\.tiny\.oauthserver\.sys\.model\.Role/import com.tiny.platform.infrastructure.auth.role.domain.Role/g' ${BASE_DIR}/infrastructure/auth/user/domain/User.java

# Role.java
sed -i '' '1,3d' ${BASE_DIR}/infrastructure/auth/role/domain/Role.java
sed -i '' '1i\
package com.tiny.platform.infrastructure.auth.role.domain;
' ${BASE_DIR}/infrastructure/auth/role/domain/Role.java
sed -i '' 's/import com\.tiny\.oauthserver\.sys\.model\.User/import com.tiny.platform.infrastructure.auth.user.domain.User/g' ${BASE_DIR}/infrastructure/auth/role/domain/Role.java
sed -i '' 's/import com\.tiny\.oauthserver\.sys\.model\.Resource/import com.tiny.platform.infrastructure.auth.resource.domain.Resource/g' ${BASE_DIR}/infrastructure/auth/role/domain/Role.java

# Resource.java
sed -i '' '1,3d' ${BASE_DIR}/infrastructure/auth/resource/domain/Resource.java
sed -i '' '1i\
package com.tiny.platform.infrastructure.auth.resource.domain;
' ${BASE_DIR}/infrastructure/auth/resource/domain/Resource.java

echo "实体文件包名修复完成"

