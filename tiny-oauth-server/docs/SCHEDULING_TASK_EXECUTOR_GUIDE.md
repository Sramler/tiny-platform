# 调度任务执行器开发指南

本文档介绍如何在 `tiny-oauth-server` 的调度模块中**创建、注册和调试**自定义 TaskExecutor。

## 1. 基本概念

执行器接口位于 `com.tiny.platform.infrastructure.scheduling.service.TaskExecutorService.TaskExecutor`：

```java
public interface TaskExecutor {
    Object execute(Map<String, Object> params) throws Exception;
}
```

- **params**：运行时传递的参数，已合并「任务默认参数 + 节点覆盖参数 + 实例覆盖参数」。
- **返回值**：会被序列化后写入 `scheduling_task_instance.result`（建议返回可 JSON 序列化的 Map/List 或简单类型）。
- **异常**：抛出的异常会被 Worker 捕获并记录到历史表，任务进入重试或失败流程。

## 2. 注册方式

1. 创建实现类并加上 `@Component("beanName")`，Bean 名称即执行器标识。
2. 启动时由 `TaskExecutorRegistry` 自动发现所有 `TaskExecutor` 类型的 Bean 并注册。
3. 在**任务类型**（`SchedulingTaskType`）的 `executor` 字段中填写：
   - **推荐**：Bean 名称，如 `loggingTaskExecutor`
   - **备选**：类全限定名，注册表未命中时会按类名从 Spring 容器查找。

前端任务类型表单的「执行器」下拉选项来自 `GET /scheduling/executors`，即上述已注册的标识列表。

## 3. 内置示例执行器

项目内置三个示例，位于 `com.tiny.platform.infrastructure.scheduling.executor`，可直接用作联调或复制为模板：

| Bean 名称 | 类名 | 描述 | 参数示例 |
|-----------|------|------|----------|
| `shellExecutor` | `ShellTaskExecutor` | 占位 Shell 执行器（仅打日志、不执行命令） | 任意，当前不解析 |
| `loggingTaskExecutor` | `LoggingTaskExecutor` | 将入参打日志并 echo 返回 | 无要求 |
| `delayTaskExecutor` | `DelayTaskExecutor` | 按参数延迟后返回，可模拟失败 | `{"delayMs":2000,"fail":false}` |
| `upperCaseExecutor` | `UpperCaseExecutor` | 将 `message` 转为大写返回（开发指南模板） | `{"message":"hello"}` |

## 4. 从零编写自定义执行器（步骤与示例）

### 4.1 新建类并实现接口

在包 `com.tiny.platform.infrastructure.scheduling.executor`（或同级子包）下新建类，实现 `TaskExecutorService.TaskExecutor`：

```java
package com.tiny.platform.infrastructure.scheduling.executor;

import com.tiny.platform.infrastructure.scheduling.service.TaskExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 自定义示例：将 message 参数大写后返回。
 */
@Component("upperCaseExecutor")
public class UpperCaseExecutor implements TaskExecutorService.TaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(UpperCaseExecutor.class);

    @Override
    public Object execute(Map<String, Object> params) throws Exception {
        String message = params != null && params.containsKey("message")
                ? String.valueOf(params.get("message"))
                : "";
        String result = message.toUpperCase();
        logger.info("[UpperCaseExecutor] in={}, out={}", message, result);
        return Map.of(
                "status", "OK",
                "original", message,
                "result", result
        );
    }
}
```

### 4.2 任务类型 paramSchema（可选）

若希望前端/后端对参数做校验，可在任务类型的 `paramSchema` 中声明，例如：

```json
{
  "type": "object",
  "required": ["message"],
  "properties": {
    "message": { "type": "string", "description": "待转大写的文本" }
  }
}
```

### 4.3 绑定到任务类型

1. 创建或编辑「任务类型」，在「执行器」下拉中选择 `upperCaseExecutor`（或你设置的 Bean 名称）。
2. 创建「任务」时选择该任务类型，在「参数」中填写如 `{"message":"hello"}`。
3. 在 DAG 中引用该任务并触发运行，即可在任务实例的 `result` 中看到返回的 JSON。

### 4.4 依赖注入

执行器本身是普通 Spring Bean，可注入其他服务，例如：

```java
@Component("myExecutor")
public class MyExecutor implements TaskExecutorService.TaskExecutor {

    private final SomeService someService;

    public MyExecutor(SomeService someService) {
        this.someService = someService;
    }

    @Override
    public Object execute(Map<String, Object> params) throws Exception {
        // 使用 someService 做业务逻辑
        return Map.of("status", "OK");
    }
}
```

## 5. 调试建议

1. 使用 Actuator 的 `/actuator/health` 确认应用启动正常。
2. 创建任务类型 → 任务定义 → DAG → 版本/节点 → 手动触发 DAG/节点。
3. 通过 `/scheduling/task-instance/{id}/log` 或数据库表查看执行结果。
4. 通过任务运行历史与日志快速验证结果，必要时查看数据库中 `scheduling_task_history`、`scheduling_task_instance`。

## 6. 一键脚本（可选）

- 若存在 `docs/scripts/scheduling-task-type-demo.http`，可用 VSCode Rest Client / IntelliJ HTTP Client 执行，快速创建基于 `loggingTaskExecutor`、`delayTaskExecutor` 的任务类型用于联调。

## 7. 最佳实践

- 明确输入/输出 Schema，并在 `paramSchema` 中声明必填字段。
- 处理幂等：同一任务可能因重试被多次执行。
- 对外部系统调用要设置超时，并在执行器内部进行异常捕获与语义化报错。
- 如需访问 Spring Bean，可注入依赖；执行器本身是普通 Spring 组件。

