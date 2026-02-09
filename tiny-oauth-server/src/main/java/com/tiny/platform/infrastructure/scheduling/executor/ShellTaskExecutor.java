package com.tiny.platform.infrastructure.scheduling.executor;

import com.tiny.platform.infrastructure.scheduling.service.TaskExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Shell 执行器：供任务类型 executor=shellExecutor 解析使用。
 * 当前为占位实现，仅记录参数并返回成功，便于 DAG 示例与运行历史验证。
 * 生产环境若需真实执行 Shell，需在此实现命令执行并做好白名单/校验与审计。
 */
@Component("shellExecutor")
public class ShellTaskExecutor implements TaskExecutorService.TaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ShellTaskExecutor.class);

    @Override
    public Object execute(Map<String, Object> params) {
        logger.info("[ShellTaskExecutor] Params={}", params);
        return Map.of(
                "status", "OK",
                "executor", "shellExecutor",
                "message", "Shell executor placeholder executed (no command run)"
        );
    }
}
