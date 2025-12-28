package com.tiny.platform.core.oauth.controller;

import com.tiny.platform.core.oauth.config.FrontendProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 登录页面控制器
 * 将登录页请求交给前端单页应用处理
 * 
 * 根据配置区分开发环境和生产环境：
 * - 开发环境：重定向到 Vite dev server (http://localhost:5173/login)
 * - 生产环境：转发到打包后的静态文件 (/dist/index.html)
 */
@Controller
public class LoginController {

    private final FrontendProperties frontendProperties;

    @Autowired
    public LoginController(FrontendProperties frontendProperties) {
        this.frontendProperties = frontendProperties;
    }

    @GetMapping("/login")
    public String login(HttpServletRequest request) {
        return buildFrontendUrl(frontendProperties.getLoginUrl(), request);
    }

    /**
     * 构建前端 URL，支持查询参数传递
     * - 如果是 redirect: 前缀，将查询参数附加到重定向 URL
     * - 如果是 forward: 前缀，直接返回（forward 会自动保留查询参数）
     */
    private String buildFrontendUrl(String configuredUrl, HttpServletRequest request) {
        if (configuredUrl.startsWith("redirect:")) {
            // 开发环境：重定向到 Vite dev server，需要附加查询参数
            String baseUrl = configuredUrl.substring("redirect:".length());
            String queryString = request.getQueryString();
            if (queryString != null && !queryString.isEmpty()) {
                return configuredUrl + (baseUrl.contains("?") ? "&" : "?") + queryString;
            }
            return configuredUrl;
        } else {
            // 生产环境：forward 会自动保留查询参数，直接返回
            return configuredUrl;
        }
    }
}
