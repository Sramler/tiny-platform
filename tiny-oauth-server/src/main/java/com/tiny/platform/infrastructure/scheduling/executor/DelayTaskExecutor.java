package com.tiny.platform.infrastructure.scheduling.executor;

import com.tiny.platform.infrastructure.scheduling.service.TaskExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 示例执行器：根据入参延迟一段时间后返回结果，可用于演示重试/超时逻辑。
 */
@Component("delayTaskExecutor")
public class DelayTaskExecutor implements TaskExecutorService.TaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(DelayTaskExecutor.class);

    @Override
    public Object execute(Map<String, Object> params) throws Exception {
        long delayMs = ((Number) params.getOrDefault("delayMs", 1000)).longValue();
        logger.info("[DelayTaskExecutor] 延迟 {} ms 执行", delayMs);
        Thread.sleep(delayMs);
        boolean fail = Boolean.parseBoolean(String.valueOf(params.getOrDefault("fail", false)));
        if (fail) {
            throw new IllegalStateException("模拟执行失败");
        }
        return Map.of(
                "status", "OK",
                "delayMs", delayMs,
                "message", "DelayTaskExecutor finished in " + delayMs + " ms"
        );
    }
}

