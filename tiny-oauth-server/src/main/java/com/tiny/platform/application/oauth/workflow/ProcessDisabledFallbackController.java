package com.tiny.platform.application.oauth.workflow;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 流程引擎未启用时的显式 API 兜底。
 *
 * <p>e2e 等轻量运行环境会关闭 {@code camunda.bpm.enabled}，此时 {@link ProcessController}
 * 和 {@link ProcessEngineService} 都不会装配。若没有该兜底，前端访问 {@code /process/**}
 * 会落入 Spring 的 no endpoint 分支并被统一异常处理包装成 500，容易被误判为服务器内部错误。</p>
 */
@RestController
@RequestMapping("/process")
@ConditionalOnMissingBean(ProcessEngineService.class)
public class ProcessDisabledFallbackController {

    @RequestMapping(
        value = {"", "/**"},
        method = {
            RequestMethod.GET,
            RequestMethod.POST,
            RequestMethod.PUT,
            RequestMethod.PATCH,
            RequestMethod.DELETE
        }
    )
    public ResponseEntity<Map<String, Object>> processEngineDisabled(HttpServletRequest request) {
        String path = request.getRequestURI();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
            "success", false,
            "error", "workflow_engine_disabled",
            "message", "当前运行环境未启用流程引擎，流程管理接口不可用",
            "path", path
        ));
    }
}
