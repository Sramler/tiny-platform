package com.tiny.platform.infrastructure.idempotent.console.dto;

import java.time.LocalDateTime;

/**
 * 幂等记录 DTO（控制台查询用）
 *
 * @author tiny-platform
 * @since 1.0.0
 */
public class IdempotentRecordDto {

    private String key;
    private String state;
    private LocalDateTime createdAt;
    private LocalDateTime expireAt;

    public IdempotentRecordDto() {
    }

    public IdempotentRecordDto(String key, String state, LocalDateTime createdAt, LocalDateTime expireAt) {
        this.key = key;
        this.state = state;
        this.createdAt = createdAt;
        this.expireAt = expireAt;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }
}
