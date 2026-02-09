package com.tiny.platform.infrastructure.scheduling.executor;

import com.tiny.platform.infrastructure.scheduling.service.TaskExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 自定义执行器示例：将 message 参数转为大写后返回。
 * 用于《调度任务执行器开发指南》中的「从零编写自定义执行器」模板，便于开发者复制扩展。
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
