package com.tiny.platform.infrastructure.auth.user.dto;

import java.time.LocalDateTime;

public class UserResponseDto {

    private Long id;

    private String username;

    private String nickname;

    private boolean enabled = true; // 是否启用

    private boolean accountNonExpired = true; // 账号是否过期

    private boolean accountNonLocked = true; // 是否锁定

    private boolean credentialsNonExpired = true; // 密码是否过期

    private LocalDateTime lastLoginAt;

    public UserResponseDto(Long id, String username, String nickname, boolean enabled, boolean accountNonExpired, boolean accountNonLocked, boolean credentialsNonExpired, LocalDateTime lastLoginAt) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
        this.enabled = enabled;
        this.accountNonExpired = accountNonExpired;
        this.accountNonLocked = accountNonLocked;
        this.credentialsNonExpired = credentialsNonExpired;
        this.lastLoginAt = lastLoginAt;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getNickname() {
        return nickname;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isAccountNonExpired() {
        return accountNonExpired;
    }

    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }
}
