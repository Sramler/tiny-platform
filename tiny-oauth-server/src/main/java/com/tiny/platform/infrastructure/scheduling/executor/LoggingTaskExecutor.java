package com.tiny.platform.infrastructure.scheduling.executor;

import com.tiny.platform.infrastructure.scheduling.service.TaskExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 示例执行器：简单地把入参写入日志并返回。
 */
@Component("loggingTaskExecutor")
public class LoggingTaskExecutor implements TaskExecutorService.TaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(LoggingTaskExecutor.class);

    @Override
    public Object execute(Map<String, Object> params) {
        logger.info("[LoggingTaskExecutor] Params={}", params);
        return Map.of(
                "status", "OK",
                "echo", params
        );
    }
}

