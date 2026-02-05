# 80 REST API 设计规范

## 适用范围

- 适用于：`**/controller/**`、`**/*Controller.java`、Controller 相关代码
- 不适用于：OAuth2 标准端点（由框架自动处理）

## 总体策略

1. **RESTful 设计**：遵循 REST 原则，使用资源名词和 HTTP 方法表示操作。
2. **统一响应格式**：成功使用 `GlobalResponse<T>`，失败使用 `ErrorResponse`。
3. **版本管理**：API 版本通过路径（如 `/v1/users`）或 Header（如 `Accept: application/vnd.api+json;version=1`）管理。

---

## 禁止（Must Not）

### 1) 路径设计

- ❌ 路径使用动词（应使用资源名词：`/scheduling/task-type` 而非 `/scheduling/getTaskType`）。
- ❌ 路径使用 `/api/` 前缀（项目不使用此前缀）。
- ❌ 路径使用驼峰命名（应使用 kebab-case：`/task-type` 而非 `/taskType`）。

### 2) 响应格式

- ❌ Controller 中手动返回错误格式（应抛出异常，由 GlobalExceptionHandler 统一处理）。
- ❌ 使用 `Map.of("success", false, "error", "...")` 等临时格式。
- ❌ 混用不同的响应格式（必须统一使用 GlobalResponse 或 ErrorResponse）。
- ❌ 返回纯文本错误信息（应返回结构化错误响应）。

### 3) HTTP 方法

- ❌ 使用 GET 请求修改数据（应使用 POST/PUT/DELETE）。
- ❌ 使用 POST 请求查询数据（应使用 GET，复杂查询可使用 POST `/resource/query`）。

---

## 必须（Must）

### 1) RESTful 设计

- ✅ RESTful 路径：资源名词（`/scheduling`、`/process`、`/user`），HTTP 方法表示操作（GET/POST/PUT/DELETE）。
- ✅ HTTP 方法语义：
  - `GET`：查询资源（幂等、安全）
  - `POST`：创建资源（非幂等）
  - `PUT`：更新资源（幂等，全量更新）
  - `PATCH`：部分更新资源（非幂等）
  - `DELETE`：删除资源（幂等）

### 2) 响应格式

- ✅ 统一响应格式：成功使用 `GlobalResponse<T>`，失败使用 `ErrorResponse`（code, message, detail, status, path, timestamp）。
- ✅ HTTP 状态码：200（成功）、400（参数错误）、401（未授权）、403（无权限）、404（不存在）、500（服务器错误）。

### 3) 参数规范

- ✅ 参数命名：请求/响应参数使用 camelCase（Java 标准）。
- ✅ 路径参数：使用 `@PathVariable`，命名与路径一致。
- ✅ 查询参数：使用 `@RequestParam`，提供默认值和校验。

### 4) 异常处理

- ✅ 异常处理：Service 层抛出异常，Controller 不捕获，由 `GlobalExceptionHandler` 统一处理。
- ✅ 错误码：使用 `ResponseCode` 枚举，不硬编码错误码。

### 5) 请求验证

- ✅ 请求验证：使用 `@Valid` 和 Bean Validation 验证请求参数。
- ✅ 参数校验：所有外部输入必须验证，防止注入攻击。

---

## 应该（Should）

### 1) 分页与排序

- ⚠️ 分页参数：统一使用 `page`（页码，从 1 开始）和 `size`（每页数量），返回 `Page<T>` 或 `PageResponse<T>`。
- ⚠️ 查询参数：列表查询支持 `sort`（排序字段）、`order`（asc/desc）、`keyword`（关键词搜索）。
- ⚠️ 分页默认值：`page=1`，`size=20`，最大 `size=100`。

### 2) 响应头

- ⚠️ 响应头：跨域、缓存控制等通过 `ResponseEntity` 的 `HttpHeaders` 设置。
- ⚠️ 内容类型：使用 `Content-Type: application/json`，字符集 `UTF-8`。

### 3) API 文档

- ⚠️ 文档：使用 Swagger/OpenAPI 注解（`@ApiOperation`、`@ApiParam`）标注接口。
- ⚠️ 接口说明：每个接口必须有清晰的说明、参数说明、返回值说明。

### 4) 版本管理

- ⚠️ API 版本：通过路径（`/v1/users`）或 Header（`Accept`）管理版本。
- ⚠️ 向后兼容：新版本应保持向后兼容，破坏性变更使用新版本号。

---

## 可以（May）

- 💡 批量操作：支持批量创建/更新/删除，路径 `/scheduling/task/batch`，请求体为数组。
- 💡 条件查询：复杂查询使用 POST `/scheduling/task/query`，请求体包含查询条件。
- 💡 导出功能：导出接口使用 GET `/export/task`，返回文件流。
- 💡 字段过滤：使用 `fields` 参数控制返回字段（如 `?fields=id,name,email`）。

---

## 例外与裁决

### OAuth2 端点

- OAuth2 标准端点（`/oauth2/authorize`、`/oauth2/token`）遵循 OAuth2 规范，不受本规范约束。

### 文件操作

- 文件上传/下载：可使用 `MultipartFile` 和 `ResponseEntity<Resource>`。
- 文件上传路径：使用 POST `/upload` 或 POST `/resource/{id}/upload`。

### 冲突裁决

- 平台特定规则（90+）优先于本规范。
- API 规范与业务规范冲突时，优先保证 API 一致性和可维护性。

---

## 示例

### ✅ 正例：RESTful 设计

```java
@RestController
@RequestMapping("/scheduling")
@Api(tags = "任务调度管理")
public class SchedulingController {

    @GetMapping("/task-type/{id}")
    @ApiOperation(value = "获取任务类型", notes = "根据ID获取任务类型详情")
    public ResponseEntity<GlobalResponse<TaskTypeDTO>> getTaskType(
            @ApiParam(value = "任务类型ID", required = true) @PathVariable Long id) {
        TaskTypeDTO taskType = schedulingService.getTaskTypeById(id);
        return ResponseUtil.ok(taskType);
    }

    @PostMapping("/task-type")
    @ApiOperation(value = "创建任务类型", notes = "创建新的任务类型")
    public ResponseEntity<GlobalResponse<TaskTypeDTO>> createTaskType(
            @Valid @RequestBody CreateTaskTypeRequest request) {
        TaskTypeDTO taskType = schedulingService.createTaskType(request);
        return ResponseUtil.ok(taskType);
    }

    @PutMapping("/task-type/{id}")
    @ApiOperation(value = "更新任务类型", notes = "全量更新任务类型")
    public ResponseEntity<GlobalResponse<TaskTypeDTO>> updateTaskType(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskTypeRequest request) {
        TaskTypeDTO taskType = schedulingService.updateTaskType(id, request);
        return ResponseUtil.ok(taskType);
    }

    @DeleteMapping("/task-type/{id}")
    @ApiOperation(value = "删除任务类型", notes = "删除指定任务类型")
    public ResponseEntity<GlobalResponse<Void>> deleteTaskType(
            @ApiParam(value = "任务类型ID", required = true) @PathVariable Long id) {
        schedulingService.deleteTaskType(id); // 内部抛出 BusinessException
        return ResponseUtil.ok(null);
    }

    @GetMapping("/task-type/list")
    @ApiOperation(value = "查询任务类型列表", notes = "分页查询任务类型列表")
    public ResponseEntity<GlobalResponse<PageResponse<TaskTypeDTO>>> listTaskTypes(
            @ApiParam(value = "页码", defaultValue = "1") @RequestParam(defaultValue = "1") Integer page,
            @ApiParam(value = "每页数量", defaultValue = "20") @RequestParam(defaultValue = "20") Integer size,
            @ApiParam(value = "排序字段") @RequestParam(required = false) String sort,
            @ApiParam(value = "排序方向", allowableValues = "asc,desc") @RequestParam(required = false) String order,
            @ApiParam(value = "关键词搜索") @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(page - 1, size,
            Sort.by(Sort.Direction.fromString(order != null ? order : "desc"),
                sort != null ? sort : "id"));
        PageResponse<TaskTypeDTO> result = schedulingService.listTaskTypes(pageable, keyword);
        return ResponseUtil.ok(result);
    }
}
```

### ❌ 反例：手动返回错误格式、路径使用动词、使用 /api/ 前缀、混用响应格式

```java
// 错误：手动返回错误格式、路径使用动词、使用 /api/ 前缀、混用响应格式
@RestController
@RequestMapping("/api/scheduling") // ❌ 不应使用 /api/ 前缀
public class SchedulingController {

    @GetMapping("/getTaskType/{id}") // ❌ 路径使用动词
    public Map<String, Object> getTaskType(@PathVariable Long id) {
        TaskType taskType = schedulingService.getTaskTypeById(id);
        if (taskType == null) {
            return Map.of("success", false, "error", "任务类型不存在"); // ❌ 手动返回错误格式
        }
        return Map.of("success", true, "data", taskType); // ❌ 混用响应格式
    }
}
```

### ✅ 正例：批量操作

```java
@PostMapping("/task/batch")
@ApiOperation(value = "批量创建任务", notes = "批量创建多个任务")
public ResponseEntity<GlobalResponse<List<TaskDTO>>> batchCreateTasks(
        @Valid @RequestBody List<CreateTaskRequest> requests) {
    List<TaskDTO> tasks = schedulingService.batchCreateTasks(requests);
    return ResponseUtil.ok(tasks);
}
```

### ✅ 正例：复杂查询（POST /query）

```java
@PostMapping("/task/query")
@ApiOperation(value = "复杂查询任务", notes = "使用POST请求体进行复杂条件查询")
public ResponseEntity<GlobalResponse<PageResponse<TaskDTO>>> queryTasks(
        @RequestBody TaskQueryRequest queryRequest,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer size) {
    Pageable pageable = PageRequest.of(page - 1, size);
    PageResponse<TaskDTO> result = schedulingService.queryTasks(queryRequest, pageable);
    return ResponseUtil.ok(result);
}
```

### ✅ 正例：文件上传

```java
@PostMapping("/task/{id}/upload")
@ApiOperation(value = "上传任务附件", notes = "上传任务相关附件文件")
public ResponseEntity<GlobalResponse<FileDTO>> uploadFile(
        @PathVariable Long id,
        @RequestParam("file") MultipartFile file) {
    FileDTO fileDTO = schedulingService.uploadFile(id, file);
    return ResponseUtil.ok(fileDTO);
}
```
