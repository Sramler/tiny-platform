package com.tiny.platform.infrastructure.auth.user.domain;

import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体。授权与可见性以 tenant_user + activeTenantId 为准；
 * username 全局唯一（uk_user_username）。
 * <p>物理列 {@code user.tenant_id} 计划在后续 migration 中删除；实体不再映射该列。
 */
@Entity
@Table(name = "user",
    uniqueConstraints = @UniqueConstraint(name = "uk_user_username", columnNames = {"username"})
)
public class User implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    private String nickname;

    @Column(nullable = false)
    private boolean enabled = true; // 是否启用

    @Column(name = "account_non_expired", nullable = false)
    private boolean accountNonExpired = true; // 账号是否过期

    @Column(name = "account_non_locked", nullable = false)
    private boolean accountNonLocked = true; // 是否锁定

    @Column(name = "credentials_non_expired", nullable = false)
    private boolean credentialsNonExpired = true; // 密码是否过期

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;
    
    @Column(name = "last_login_ip", length = 50)
    private String lastLoginIp;
    
    @Column(length = 100)
    private String email;
    
    @Column(length = 20)
    private String phone;
    
    @Column(name = "last_login_device", length = 200)
    private String lastLoginDevice;
    
    @Column(name = "failed_login_count", nullable = false)
    private Integer failedLoginCount = 0;
    
    @Column(name = "last_failed_login_at")
    private LocalDateTime lastFailedLoginAt;

    // getter/setter（可用 Lombok 简化）

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    public void setAccountNonExpired(boolean accountNonExpired) {
        this.accountNonExpired = accountNonExpired;
    }

    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    public void setAccountNonLocked(boolean accountNonLocked) {
        this.accountNonLocked = accountNonLocked;
    }

    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    public void setCredentialsNonExpired(boolean credentialsNonExpired) {
        this.credentialsNonExpired = credentialsNonExpired;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public String getLastLoginIp() {
        return lastLoginIp;
    }

    public void setLastLoginIp(String lastLoginIp) {
        this.lastLoginIp = lastLoginIp;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getLastLoginDevice() {
        return lastLoginDevice;
    }

    public void setLastLoginDevice(String lastLoginDevice) {
        this.lastLoginDevice = lastLoginDevice;
    }

    public Integer getFailedLoginCount() {
        return failedLoginCount;
    }

    public void setFailedLoginCount(Integer failedLoginCount) {
        this.failedLoginCount = failedLoginCount;
    }

    public LocalDateTime getLastFailedLoginAt() {
        return lastFailedLoginAt;
    }

    public void setLastFailedLoginAt(LocalDateTime lastFailedLoginAt) {
        this.lastFailedLoginAt = lastFailedLoginAt;
    }
}
