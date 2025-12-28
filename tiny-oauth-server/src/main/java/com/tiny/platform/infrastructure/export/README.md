# com.tiny.export 使用指南（初版）

> 目标：为团队提供一个可复用的 Excel 导出组件，支持多业务 Sheet、多级表头、顶部信息、流式大数据、异步任务和可插拔 Writer。

## 1. 核心概念

| 组件                | 说明                                                                                                                  |
| ------------------- | --------------------------------------------------------------------------------------------------------------------- |
| `ExportRequest`     | 一次导出请求。包含多个 `SheetConfig`，每个 sheet 可指定不同业务类型（`exportType`）、Sheet 名称、表头树、合计策略等。 |
| `SheetConfig`       | 单个 sheet 的配置：`exportType`、`sheetName`、`columns`（多级表头）、`aggregateKey` 等。                              |
| `ColumnNode`        | 描述多级表头结构（父子节点）。`HeaderBuilder` 会自动转为 `List<List<String>>` 并输出叶子列顺序。                      |
| `DataProvider`      | 数据提供器接口，返回 `Iterator` 或分页迭代器，实现流式写入（避免一次性加载所有数据）。                                |
| `WriterAdapter`     | Excel 写入器接口，负责将 `SheetWriteModel` 写入输出流。已提供 `PoiWriterAdapter` 示例，可扩展为 Fesod。               |
| `TopInfoDecorator`  | 顶部信息装饰器，按 `exportType` 生成顶部说明行（支持多行、合并单元格）。                                              |
| `AggregateStrategy` | 合计/统计策略接口。导出过程中按列累加，写入尾部合计。                                                                 |
| `ExportService`     | 导出编排器，提供 `exportSync`（同步）和 `submitAsync`（异步）。内置并发控制和任务状态管理。                           |

## 2. 使用步骤

1. **定义 Column/Sheet**

   ```java
   ColumnNode columns = ColumnNode.root(
       ColumnNode.leaf("用户ID", "userId"),
       ColumnNode.group("订单信息",
           ColumnNode.leaf("订单号", "orderNo"),
           ColumnNode.leaf("金额", "amount")
       )
   );
   SheetConfig sheet = SheetConfig.builder()
       .sheetName("用户订单")
       .exportType("userOrder")
       .columns(columns)
       .aggregateKey("defaultSum")
       .build();
   ExportRequest request = ExportRequest.builder()
       .fileName("用户订单.xlsx")
       .sheets(List.of(sheet))
       .pageSize(5000)
       .build();
   ```

2. **准备 DataProvider**

   ```java
   @Component("userOrder")
   public class UserOrderDataProvider implements DataProvider<UserOrderVO> {
       @Override
       public Iterator<UserOrderVO> fetchIterator(int pageSize) { ... }
   }
   ```

3. **注入 ExportService 并调用**

   ```java
   @RestController
   public class ExportController {
       private final ExportService exportService;
       @GetMapping("/export/user-order")
       public void export(HttpServletResponse response) throws Exception {
           ExportRequest request = buildRequest();
           response.setHeader("Content-Disposition","attachment; filename=\"user-order.xlsx\"");
           exportService.exportSync(request, response.getOutputStream(), currentUserId());
       }
   }
   ```

4. **异步导出**（可返回 taskId，供前端轮询）
   ```java
   String taskId = exportService.submitAsync(request, currentUserId());
   ```

## 3. 现有功能亮点

- 多业务 Sheet：一个 Excel 文件可包含任意 Sheet，每个 sheet 对应不同 DataProvider。
- 顶部信息 + 多级表头：支持多行 top info、父列合并、复杂表头结构。
- 流式写入：DataProvider → Iterator；`PoiWriterAdapter` 使用 SXSSF 支持百万级数据。
- 合计行：`AggregateStrategy` 在流式写入时累加，尾部写合计。
- 异步导出 + 并发控制：系统级 & 用户级限流；异步任务状态管理。
- WriterAdapter 可插拔：可切换 POI / Fesod，实现差异化能力。
- 完整注释：每个核心类和方法都有详细中文注释，便于二次开发。

## 4. 待完善/规划中的功能

1. ~~**FesodWriterAdapter 完整实现**~~ ✅

   - 现已支持多 Sheet、顶部信息、多级表头、合计行、流式写入等能力，可通过配置切换 POI/Fesod。

2. **任务持久化与恢复**

   - `ExportService` 的任务信息目前保存在内存 Map，建议抽象 `ExportTaskRepository` 落入 DB/Redis。
   - 支持应用重启后的任务重试/恢复，以及过期文件清理。

3. **REST 级任务管理**

   - 提供查询任务列表、下载结果、取消任务的接口，方便前端集成。
   - 增加导出审计日志（导出人、时间、数据范围）。

4. **更灵活的表格格式**

   - 支持列宽、冻结行、条件格式、背景色等样式配置。
   - TopInfoDecorator 可注入模板（如公司 logo、导出说明）。

5. **安全限制与审批**

   - 允许针对某些 exportType 设置审批流程或关停开关。
   - 对敏感字段提供脱敏/加密输出能力。

6. **实用工具与文档**
   - 提供 demo controller、单元测试示例，方便快速接入。
   - 输出 Markdown/Asciidoc 文档，说明常见场景与扩展方式。

欢迎在现有架构基础上继续打磨，逐步沉淀为团队可复用的导出基础设施。
