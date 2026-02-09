# Quartz 集成状态分析

> 触发链路与实现细节见 [SCHEDULING_TRIGGER_DESIGN.md](./SCHEDULING_TRIGGER_DESIGN.md)。

## 当前集成状态

### ✅ 已实现的功能

#### 1. **手动触发 DAG** — 已实现
- **位置**: `SchedulingService.triggerDag()`
- **实现**: 先创建 `SchedulingDagRun`，再调用 `triggerDagNow(dag, run.getId(), version.getId())`，JobDataMap 传递 dagRunId/dagVersionId
- **状态**: ✅ 逻辑正确，Run 与执行一一对应

#### 2. **定时调度（Cron）** — 已实现
- **位置**: `createDag()` / `updateDag()`，`SchedulingDagCreateUpdateDto.cronExpression`
- **实现**: 有 cron 时调用 `createOrUpdateDagJob(dag, cronExpression)`；更新时清空 cron 则 `deleteDagJob(dagId)`
- **状态**: ✅ Cron 触发时 Job 仅传 dagId，`executeDag(dagId, null, null)` 内创建 Run

#### 3. **暂停 DAG** — 已实现
- **位置**: `SchedulingService.pauseDag()`
- **实现**: 调用 `QuartzSchedulerService.pauseDagJob()`
- **状态**: ✅ 已关联 Quartz

#### 4. **恢复 DAG** — 已实现
- **位置**: `SchedulingService.resumeDag()`
- **实现**: 调用 `QuartzSchedulerService.resumeDagJob()`
- **状态**: ✅ 已关联 Quartz

#### 5. **停止 DAG** — 已实现
- **位置**: `SchedulingService.stopDag()`
- **实现**: 调用 `pauseDagJob(dagId)`，并将 RUNNING 的 Run 标为 CANCELLED
- **状态**: ✅ 已关联 Quartz

#### 6. **重试 DAG** — 已实现
- **位置**: `SchedulingService.retryDag()`
- **实现**: 为失败 Run 创建 retry Run，再调用 `triggerDagNow(dag, retryRun.getId(), retryRun.getDagVersionId())`
- **状态**: ✅ 重试会真正触发执行

#### 7. **删除 DAG** — 已实现
- **位置**: `SchedulingService.deleteDag()`
- **实现**: 调用 `quartzSchedulerService.deleteDagJob(id)`；失败仅用 logger 记录，不阻止删除
- **状态**: ✅ 已清理 Quartz Job

#### 8. **DAG 执行 Job** — 已实现
- **位置**: `DagExecutionJob.execute()`
- **实现**: 从 JobDataMap 读取 dagRunId/dagVersionId，调用 `executeDag(dagId, dagRunId, dagVersionId)`；为空时走定时分支在内部创建 Run
- **状态**: ✅ 已实现

### 历史问题（已修复）

- **triggerDag 顺序**：已改为先创建 `SchedulingDagRun`，再 `triggerDagNow(dag, run.getId(), version.getId())`，Job 内使用已有 Run。
- **triggerDagNow 参数**：已支持传入 `dagRunId`、`dagVersionId` 并写入 JobDataMap。
- **DagExecutionJob**：已根据 JobDataMap 中是否有 dagRunId/dagVersionId 区分手动/定时，定时时在 `executeDag(dagId, null, null)` 内创建 Run。

## 需要改进的地方（可选）

### 1. 添加 Cron 表达式支持

**DTO 修改**:
```java
public class SchedulingDagCreateUpdateDto {
    // 添加 cron 表达式字段
    private String cronExpression;
}
```

**Service 修改**:
```java
@Transactional
public SchedulingDag createDag(SchedulingDagCreateUpdateDto dto) {
    SchedulingDag dag = // ... 创建 DAG
    
    // 如果有 cron 表达式，创建定时调度的 Quartz Job
    if (dto.getCronExpression() != null && !dto.getCronExpression().isEmpty()) {
        try {
            quartzSchedulerService.createOrUpdateDagJob(dag, dto.getCronExpression());
        } catch (SchedulerException e) {
            throw new RuntimeException("创建定时调度失败: " + e.getMessage(), e);
        }
    }
    
    return dag;
}
```

### 2. 修复 triggerDag 方法

**修改顺序**:
1. 先创建 `dagRun`
2. 再触发 Quartz Job，传递 `dagRunId`

### 3. 修复 triggerDagNow 方法

**添加参数**:
- `dagRunId`: 手动触发时传递
- `dagVersionId`: 手动触发时传递

### 4. 修复 DagExecutionJob

**区分触发类型**:
- 手动触发：使用已存在的 `dagRun`
- 定时触发：创建新的 `dagRun`

### 5. 完善 stopDag 方法

**添加 Quartz 操作**:
```java
public void stopDag(Long dagId) {
    // 停止 Quartz Job
    try {
        quartzSchedulerService.pauseDagJob(dagId);
    } catch (SchedulerException e) {
        // 处理异常
    }
    
    // 更新数据库状态
    // ...
}
```

### 6. 完善 retryDag 方法

**添加 Quartz 触发**:
```java
public void retryDag(Long dagId) {
    // 创建重试的 dagRun
    SchedulingDagRun retryRun = // ...
    
    // 触发 Quartz Job
    try {
        quartzSchedulerService.triggerDagNow(dag, retryRun.getId(), version.getId());
    } catch (SchedulerException e) {
        // 处理异常
    }
}
```

### 7. 完善 deleteDag 方法

**添加 Quartz 清理**:
```java
public void deleteDag(Long id) {
    // 删除 Quartz Job
    try {
        quartzSchedulerService.deleteDagJob(id);
    } catch (SchedulerException e) {
        // 处理异常
    }
    
    // 删除数据库记录
    // ...
}
```

## 总结

### 已关联 Quartz 的功能
- ✅ 手动触发 DAG（部分，有逻辑问题）
- ✅ 暂停 DAG
- ✅ 恢复 DAG
- ✅ DAG 执行 Job

### 未关联 Quartz 的功能
- ❌ 定时调度（Cron）
- ❌ 停止 DAG
- ❌ 重试 DAG
- ❌ 删除 DAG（清理）

### 需要修复的问题
- ⚠️ `triggerDag` 方法的执行顺序
- ⚠️ `triggerDagNow` 方法的参数传递
- ⚠️ `DagExecutionJob` 的触发类型区分

## 建议

1. **优先级高**: 修复 `triggerDag` 和 `triggerDagNow` 的逻辑问题
2. **优先级中**: 添加 Cron 表达式支持，实现定时调度
3. **优先级中**: 完善 `stopDag`、`retryDag`、`deleteDag` 方法
4. **优先级低**: 优化错误处理和日志记录

