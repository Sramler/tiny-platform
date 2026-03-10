package com.tiny.platform.infrastructure.idempotent.console.dto;

import java.time.LocalDateTime;

/**
 * 幂等黑名单 DTO
 *
 * @author tiny-platform
 * @since 1.0.0
 */
public class IdempotentBlacklistDto {

    private Long id;
    private String keyPattern;
    private String reason;
    private LocalDateTime createdAt;

    public IdempotentBlacklistDto() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKeyPattern() {
        return keyPattern;
    }

    public void setKeyPattern(String keyPattern) {
        this.keyPattern = keyPattern;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
