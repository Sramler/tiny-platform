# Tiny Platform 数据字典指南

> **统一文档版本**：v1.0  
> **最后更新**：2025-01-XX  
> **文档说明**：本文档整合了数据字典的设计规范、实施总结、检查报告等内容，提供完整的使用指南。

---

## 📑 目录

- [1. 概述](#1-概述)
- [2. 快速开始](#2-快速开始)
- [3. 设计规范](#3-设计规范)
- [4. 当前实现状态](#4-当前实现状态)
- [5. 使用指南](#5-使用指南)
- [6. API 参考](#6-api-参考)
- [7. 最佳实践](#7-最佳实践)
- [8. 常见问题](#8-常见问题)
- [9. 附录](#9-附录)

---

## 1. 概述

### 1.1 什么是数据字典？

数据字典（Dict）是 Tiny Platform 的核心能力之一，用于管理业务数据的语义翻译和配置。

**核心功能**：

- ✅ **数据翻译**：将业务数据中的值（如 `MALE`, `FEMALE`）翻译成可读的标签（如 `男`, `女`）
- ✅ **业务配置**：管理业务状态、类型等配置数据（如订单状态、性别等）
- ✅ **多租户支持**：支持平台字典和租户自定义字典
- ✅ **缓存机制**：提升查询性能，减少数据库压力

### 1.2 架构定位

```
tiny-platform/
├── tiny-core/                    # 核心基础设施模块（必须）
│   └── dict/                     # 数据字典核心能力（Level0）
│       ├── model/                # DictType, DictItem 实体
│       ├── repository/            # Repository 抽象接口
│       ├── runtime/               # DictRuntime API
│       └── cache/                 # 缓存抽象
├── tiny-core-dict-starter/      # Spring Boot Starter（推荐）
├── tiny-core-dict-repository/   # Repository 实现（可选）
├── tiny-core-dict-cache/        # 缓存实现（可选）
├── tiny-core-dict-web/          # REST API（可选）
└── tiny-core-governance/         # 治理扩展模块（可选）
```

### 1.3 核心设计原则

1. **数据字典是平台核心能力**

   - 字典语义是 SaaS 平台的最小不可拆分能力
   - 所有业务模块必须假设字典语义存在

2. **Core 提供语义承载能力，治理能力分级开放**

   - 强治理能力（FORCE 变更、CI 校验、审批、事故追责）通过扩展模块提供
   - Core 不干预业务运行，保证无感知集成

3. **多租户隔离**

   - 租户数据隔离，平台字典 `tenant_id = 0`，租户可覆盖 label，不可覆盖 value

4. **可演进性**
   - 后续功能（报表、低代码、工作流、风控）依赖 Core 语义统一
   - 扩展治理模块可独立迭代，不影响 Core

---

## 2. 快速开始

### 2.1 引入依赖

#### 最小引入（轻量模式）

```xml
<dependency>
    <groupId>com.tiny</groupId>
    <artifactId>tiny-core-dict-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

#### 标准引入（JPA 支持）

```xml
<dependency>
    <groupId>com.tiny</groupId>
    <artifactId>tiny-core-dict-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>com.tiny</groupId>
    <artifactId>tiny-core-dict-repository-jpa</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

#### 生产环境（Redis 缓存）

```xml
<!-- 上述依赖 + -->
<dependency>
    <groupId>com.tiny</groupId>
    <artifactId>tiny-core-dict-cache-redis</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2.2 配置

```yaml
# application.yml
tiny:
  core:
    dict:
      enabled: true # 是否启用（默认 true）
      cache:
        type: memory # 缓存类型：memory（默认）或 redis
        expire-time: 3600 # 缓存过期时间（秒）
        refresh-interval: 300 # 缓存刷新间隔（秒）
```

### 2.3 使用示例

#### 后端使用

```java
@Service
public class UserService {
    @Autowired
    private DictRuntime dictRuntime;

    public String getUserGenderLabel(String genderValue, Long tenantId) {
        // 获取字典标签
        return dictRuntime.getLabel("GENDER", genderValue, tenantId);
    }

    public Map<String, String> getAllGenders(Long tenantId) {
        // 获取整个字典
        return dictRuntime.getAll("GENDER", tenantId);
    }
}
```

#### 前端使用

```typescript
import { useDict } from "@/composables/useDict";

const { translateLabel, loadDictTypes } = useDict(tenantId);

// 翻译字典标签
const label = await translateLabel("GENDER", "MALE");

// 加载字典类型列表
await loadDictTypes();
```

---

## 3. 设计规范

### 3.1 核心表结构

#### dict_type 表（字典类型表）

```sql
CREATE TABLE dict_type (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dict_code VARCHAR(64) NOT NULL UNIQUE COMMENT '字典编码',
    dict_name VARCHAR(128) NOT NULL COMMENT '字典名称',
    description VARCHAR(255) COMMENT '字典描述',
    tenant_id BIGINT DEFAULT 0 COMMENT '租户ID，0表示平台字典',
    category_id BIGINT COMMENT '分类ID',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    sort_order INT DEFAULT 0 COMMENT '排序顺序',
    ext_attrs JSON COMMENT '扩展属性',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_dict_code (dict_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型表';
```

#### dict_item 表（字典项表）

```sql
CREATE TABLE dict_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    dict_type_id BIGINT NOT NULL COMMENT '字典类型ID',
    value VARCHAR(64) NOT NULL COMMENT '字典值',
    label VARCHAR(128) NOT NULL COMMENT '字典标签',
    description VARCHAR(255) COMMENT '字典项描述',
    tenant_id BIGINT DEFAULT 0 COMMENT '租户ID，0表示平台字典项',
    enabled BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    sort_order INT DEFAULT 0 COMMENT '排序顺序',
    ext_attrs JSON COMMENT '扩展属性',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_dict_type_value_tenant (dict_type_id, value, tenant_id),
    INDEX idx_dict_type_id (dict_type_id),
    INDEX idx_tenant_id (tenant_id),
    FOREIGN KEY (dict_type_id) REFERENCES dict_type(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典项表';
```

### 3.2 多租户策略

#### 平台字典 vs 租户字典

- **平台字典**：`tenant_id = 0`，所有租户共享，只读保护
- **租户字典**：`tenant_id > 0`，租户私有，可覆盖平台字典的 label

#### 覆盖规则

- 租户可以覆盖平台字典的 **label**，但不可修改 **value**
- 查询时：`tenantId IN (0, currentTenant)`，租户值优先覆盖平台值
- 相同 value 时，租户字典的 label 会覆盖平台字典的 label

#### 示例

```sql
-- 平台字典
INSERT INTO dict_item (dict_type_id, value, label, tenant_id)
VALUES (1, 'M', '男', 0);

-- 租户覆盖（tenant_id = 100）
INSERT INTO dict_item (dict_type_id, value, label, tenant_id)
VALUES (1, 'M', '男性', 100);  -- 租户看到"男性"而不是"男"
```

### 3.3 缓存策略

#### 缓存设计

- **缓存 Key**：`tenantId:dictCode`
- **缓存 Value**：`Map<value, label>`（整个字典的映射）
- **缓存实现**：内存/Redis 可选
- **缓存刷新**：支持异步刷新、TTL 自动过期

#### 查询流程

```
1. 检查缓存（Key: tenantId:dictCode）
   ├─ 命中 → 返回缓存值
   └─ 未命中 → 查询数据库
       ├─ 查询 tenantId IN (0, tenantId)
       ├─ 内存合并（租户值覆盖平台值）
       ├─ 写入缓存
       └─ 返回结果
```

### 3.4 能力分级设计

#### Level0（Core 默认能力）

- ✅ 字典语义承载
- ✅ 多租户隔离
- ✅ label/value 解析
- ✅ API 接口访问
- ✅ 缓存 + 异步刷新
- ❌ 不强制校验

#### Level1（租户可选治理）

- ✅ 严格校验字典值合法性
- ✅ 禁止非法 value 写入
- ✅ 租户可选择开启，增强数据一致性

#### Level2（高级治理模块）

- ✅ FORCE 变更
- ✅ CI / 静态校验（dict-checker）
- ✅ 审批流程
- ✅ 事故追责
- ✅ 仅扩展模块可用，不影响 Core

---

## 4. 当前实现状态

### 4.1 已完成功能 ✅

| 模块          | 完成度  | 说明                         |
| ------------- | ------- | ---------------------------- |
| 实体类        | ✅ 100% | DictType、DictItem 完整实现  |
| Repository 层 | ✅ 100% | 支持多租户查询和分页         |
| Service 层    | ⚠️ 80%  | 基本 CRUD 完整，缺少缓存机制 |
| Controller 层 | ✅ 100% | REST API 接口完整            |
| 数据库表结构  | ✅ 100% | 设计合理，索引完善           |

### 4.2 待实现功能 ⚠️

| 功能                      | 优先级 | 状态      | 说明                   |
| ------------------------- | ------ | --------- | ---------------------- |
| DictRuntime 核心 API      | P0     | ❌ 未实现 | 统一字典访问入口       |
| DictCacheManager 缓存机制 | P0     | ❌ 未实现 | 内存/Redis 缓存        |
| 平台字典只读保护          | P1     | ❌ 未实现 | 防止误操作修改平台字典 |
| 缓存刷新机制              | P1     | ❌ 未实现 | 字典变更事件通知       |
| 字典初始化机制            | P2     | ❌ 未实现 | 应用启动时自动初始化   |
| 治理能力模块              | P2     | ❌ 未实现 | Level1/Level2 治理     |

### 4.3 实现完成度

**总体完成度：约 60%**

- ✅ 核心实体类和 Repository 层实现完整
- ✅ Service 层和 Controller 层基本功能完整
- ❌ 缺少核心组件：DictRuntime 和 DictCacheManager
- ❌ 缺少缓存机制：每次查询都访问数据库
- ⚠️ 缺少平台字典保护：可能误操作修改平台字典

**详细检查报告**：参见 [附录 - 设计检查报告](#91-设计检查报告)

---

## 5. 使用指南

### 5.1 字典编码规范

```java
// ✅ 推荐：使用大写字母和下划线
"GENDER"           // 性别
"ORDER_STATUS"     // 订单状态
"USER_TYPE"        // 用户类型

// ❌ 不推荐：使用小写或驼峰
"gender"           // 不统一
"orderStatus"      // 不符合规范
```

### 5.2 多租户使用

```java
@Service
public class OrderService {
    @Autowired
    private DictRuntime dictRuntime;

    public void processOrder(Order order, Long tenantId) {
        // ✅ 正确：传入 tenantId
        String statusLabel = dictRuntime.getLabel("ORDER_STATUS",
                                                   order.getStatus(),
                                                   tenantId);

        // ❌ 错误：使用固定 tenantId
        String wrongLabel = dictRuntime.getLabel("ORDER_STATUS",
                                                 order.getStatus(),
                                                 0L);  // 只查询平台字典
    }
}
```

### 5.3 缓存使用

```java
// ✅ 推荐：直接使用 DictRuntime，自动缓存
String label = dictRuntime.getLabel("GENDER", "M", tenantId);

// ❌ 不推荐：手动管理缓存
// 不要自己实现缓存逻辑，使用平台提供的缓存机制
```

### 5.4 异常处理

```java
@Service
public class OrderService {
    @Autowired
    private DictRuntime dictRuntime;

    public String getStatusLabel(String status, Long tenantId) {
        String label = dictRuntime.getLabel("ORDER_STATUS", status, tenantId);

        // ✅ 推荐：处理空值情况
        if (label == null || label.isEmpty()) {
            logger.warn("字典值不存在: ORDER_STATUS={}, tenantId={}", status, tenantId);
            return status;  // 返回原始值
        }

        return label;
    }
}
```

### 5.5 前端集成

```vue
<template>
  <a-table :columns="columns" :data-source="dataSource" />
</template>

<script setup lang="ts">
import { useDict } from "@/composables/useDict";

const { translateDict } = useDict();

const columns = [
  {
    title: "状态",
    dataIndex: "status",
    customRender: ({ text }) => translateDict("ORDER_STATUS", text),
  },
];
</script>
```

---

## 6. API 参考

### 6.1 DictRuntime API

#### getLabel

根据字典编码和值获取标签。

```java
String getLabel(String dictCode, String value, Long tenantId)
```

**参数**：

- `dictCode`：字典编码（如 "GENDER"）
- `value`：字典值（如 "MALE"）
- `tenantId`：租户 ID

**返回**：字典标签，如果不存在返回空字符串

**示例**：

```java
String label = dictRuntime.getLabel("GENDER", "MALE", tenantId);
// 结果：label = "男"
```

#### getAll

获取字典的所有项（value -> label 映射）。

```java
Map<String, String> getAll(String dictCode, Long tenantId)
```

**参数**：

- `dictCode`：字典编码
- `tenantId`：租户 ID

**返回**：value -> label 映射表

**示例**：

```java
Map<String, String> genderMap = dictRuntime.getAll("GENDER", tenantId);
// 结果：{"MALE": "男", "FEMALE": "女"}
```

### 6.2 REST API

#### 获取字典标签

```http
GET /api/dict/label?dictCode=GENDER&value=MALE&tenantId=100
```

**响应**：

```
"男"
```

#### 获取字典所有项

```http
GET /api/dict/{dictCode}?tenantId=100
```

**响应**：

```json
{
  "MALE": "男",
  "FEMALE": "女"
}
```

#### 批量获取字典标签

```http
POST /api/dict/labels/batch
Content-Type: application/json

{
  "dictCode": "GENDER",
  "values": ["MALE", "FEMALE"],
  "tenantId": 100
}
```

**响应**：

```json
{
  "MALE": "男",
  "FEMALE": "女"
}
```

**完整 API 文档**：参见 [附录 - REST API 完整设计](#92-rest-api-完整设计)

---

## 7. 最佳实践

### 7.1 字典编码规范

- ✅ 使用大写字母和下划线：`GENDER`、`ORDER_STATUS`
- ✅ 长度控制在 3-64 个字符
- ✅ 语义清晰，见名知意
- ❌ 避免使用小写或驼峰命名

### 7.2 多租户使用

- ✅ 始终传入正确的 `tenantId`
- ✅ 使用 ThreadLocal 存储租户上下文
- ✅ 在拦截器中自动设置租户上下文
- ❌ 不要硬编码 `tenantId = 0`

### 7.3 性能优化

- ✅ 使用 `getAll()` 批量获取字典，避免循环单个查询
- ✅ 启用缓存机制（内存或 Redis）
- ✅ 合理设置缓存 TTL
- ❌ 不要手动管理缓存

### 7.4 数据安全

- ✅ 平台字典（tenantId=0）只读保护
- ✅ 使用参数化查询，防止 SQL 注入
- ✅ 多租户数据隔离
- ❌ 不要直接拼接 SQL

---

## 8. 常见问题

### Q1: 如何添加新的字典类型？

**A**: 通过数据库直接插入，或使用管理界面：

```sql
INSERT INTO dict_type (dict_code, dict_name, tenant_id)
VALUES ('NEW_DICT', '新字典', 0);

INSERT INTO dict_item (dict_type_id, value, label, sort_order, tenant_id)
VALUES (1, 'VALUE1', '标签1', 1, 0);
```

### Q2: 租户如何覆盖平台字典的 label？

**A**: 租户可以插入相同 `dict_type_id` 和 `value`，但 `tenant_id` 不同的记录：

```sql
-- 平台字典
INSERT INTO dict_item (dict_type_id, value, label, tenant_id)
VALUES (1, 'M', '男', 0);

-- 租户覆盖（tenant_id = 100）
INSERT INTO dict_item (dict_type_id, value, label, tenant_id)
VALUES (1, 'M', '男性', 100);  -- 租户看到"男性"而不是"男"
```

### Q3: 如何禁用字典功能？

**A**: 通过配置禁用：

```yaml
tiny:
  core:
    dict:
      enabled: false
```

### Q4: 字典数据更新后，缓存何时刷新？

**A**:

- **内存缓存**：立即刷新（通过 `refreshDictCacheAsync()`）
- **Redis 缓存**：支持 TTL 自动过期，或手动刷新

### Q5: 如何自定义缓存实现？

**A**: 实现 `DictCacheManager` 接口，并注册为 Bean：

```java
@Bean
@Primary  // 覆盖默认实现
public DictCacheManager customDictCacheManager() {
    return new CustomDictCacheManager();
}
```

### Q6: 字典数据量大时如何优化？

**A**:

1. 使用 Redis 缓存（分布式共享）
2. 分租户缓存（减少单次查询数据量）
3. 异步刷新缓存（不阻塞业务）
4. 考虑字典数据分表（如果单个字典项 > 10 万）

---

## 9. 附录

### 9.1 REST API 完整接口列表

**主要接口**：

- `GET /api/dict/label` - 获取字典标签
- `GET /api/dict/{dictCode}` - 获取字典所有项
- `POST /api/dict/labels/batch` - 批量获取字典标签
- `GET /api/dict/types` - 分页查询字典类型
- `POST /api/dict/types` - 创建字典类型
- `PUT /api/dict/types/{id}` - 更新字典类型
- `DELETE /api/dict/types/{id}` - 删除字典类型
- `GET /api/dict/items` - 分页查询字典项
- `POST /api/dict/items` - 创建字典项
- `PUT /api/dict/items/{id}` - 更新字典项
- `DELETE /api/dict/items/{id}` - 删除字典项
- `POST /api/dict/cache/refresh` - 刷新字典缓存

### 9.2 实施阶段划分

**阶段划分**：

- **Phase 0**: 准备阶段（1-2 天）- 创建模块结构、基础配置
- **Phase 1**: Core 核心能力（3-5 天）- 实现 Level0 基础能力
- **Phase 2**: Starter 自动配置（2-3 天）- Spring Boot 自动装配
- **Phase 3**: Repository 实现（2-3 天）- JPA/JDBC 数据访问
- **Phase 4**: 缓存实现（2-3 天）- 内存/Redis 缓存
- **Phase 5**: REST API 模块（3-4 天）- 前端调用接口
- **Phase 6**: 管理界面（5-7 天）- 字典管理 UI
- **Phase 7**: 治理能力（3-5 天）- Level1/Level2 治理
- **Phase 8**: 扩展功能（5-7 天）- 版本管理、审计日志等

**当前状态**：

- ✅ Phase 0-3: 已完成（实体类、Repository、Service、Controller）
- ❌ Phase 4: 待实现（缓存机制）
- ⚠️ Phase 5-8: 部分完成（REST API 已实现，管理界面已实现，治理能力待实现）

### 9.3 数据库表结构

**详细 SQL 脚本**：参见 [scripts/dict-schema.sql](../scripts/dict-schema.sql)

**核心表**：

- `dict_type` - 字典类型表
- `dict_item` - 字典项表
- `tenant_policy` - 租户策略表（可选，用于治理能力）
- `capability_matrix` - 能力矩阵表（可选，用于治理能力）
- `dict_version` - 字典版本表（可选，用于版本管理）
- `dict_item_version_snapshot` - 字典项版本快照表（可选）
- `dict_audit_log` - 字典审计日志表（可选，用于审计）

### 9.4 设计检查报告摘要

**主要发现**：

✅ **已完成**：

- 核心实体类和 Repository 层实现完整
- Service 层和 Controller 层基本功能完整
- 数据库表结构设计合理

❌ **待实现**（P0 优先级）：

- DictRuntime 核心 API（统一字典访问入口）
- DictCacheManager 缓存机制（内存/Redis 缓存）

⚠️ **待完善**（P1 优先级）：

- 平台字典只读保护（防止误操作修改平台字典）
- 缓存刷新机制（字典变更事件通知）

📋 **可选功能**（P2 优先级）：

- 字典初始化机制（应用启动时自动初始化）
- 治理能力模块（Level1/Level2 治理）

---

## 📝 更新日志

| 版本 | 日期       | 更新内容                           |
| ---- | ---------- | ---------------------------------- |
| v1.0 | 2025-01-XX | 创建统一指南文档，整合所有相关文档 |

---

## 📧 反馈与支持

如有问题或建议，请通过以下方式反馈：

- 提交 Issue
- 联系项目维护者

---

**文档维护者**：Tiny Platform 团队  
**最后更新**：2025-01-XX
