package com.tiny.platform.core.oauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "security.login")
public class LoginProtectionProperties {

    /**
     * 密码登录连续失败阈值，达到后进入临时锁定窗口。
     */
    private int maxFailedAttempts = 5;

    /**
     * 密码登录临时锁定时长（分钟）。
     */
    private int lockMinutes = 15;

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public void setMaxFailedAttempts(int maxFailedAttempts) {
        this.maxFailedAttempts = maxFailedAttempts;
    }

    public int getLockMinutes() {
        return lockMinutes;
    }

    public void setLockMinutes(int lockMinutes) {
        this.lockMinutes = lockMinutes;
    }
}
