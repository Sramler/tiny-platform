package com.tiny.platform.core.oauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 前端页面配置属性类
 * 使用 @ConfigurationProperties 实现类型安全的配置绑定
 * 
 * 用于区分开发环境和生产环境的前端页面路由方式：
 * - 开发环境：重定向到 Vite dev server (http://localhost:5173)
 * - 生产环境：转发到打包后的静态文件 (/dist/index.html)
 */
@Component
@ConfigurationProperties(prefix = "frontend")
public class FrontendProperties {

    /**
     * 登录页面 URL
     * 开发环境示例: redirect:http://localhost:5173/login
     * 生产环境示例: forward:/dist/index.html
     */
    private String loginUrl = "forward:/dist/index.html";

    /**
     * TOTP 绑定页面 URL
     * 开发环境示例: redirect:http://localhost:5173/self/security/totp-bind
     * 生产环境示例: forward:/dist/index.html
     */
    private String totpBindUrl = "forward:/dist/index.html";

    /**
     * TOTP 验证页面 URL
     * 开发环境示例: redirect:http://localhost:5173/self/security/totp-verify
     * 生产环境示例: forward:/dist/index.html
     */
    private String totpVerifyUrl = "forward:/dist/index.html";

    public String getLoginUrl() {
        return loginUrl;
    }

    public void setLoginUrl(String loginUrl) {
        this.loginUrl = loginUrl;
    }

    public String getTotpBindUrl() {
        return totpBindUrl;
    }

    public void setTotpBindUrl(String totpBindUrl) {
        this.totpBindUrl = totpBindUrl;
    }

    public String getTotpVerifyUrl() {
        return totpVerifyUrl;
    }

    public void setTotpVerifyUrl(String totpVerifyUrl) {
        this.totpVerifyUrl = totpVerifyUrl;
    }
}
