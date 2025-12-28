package com.tiny.platform.application.oauth.model;

import jakarta.validation.constraints.Size;

/**
 * 用户查询请求DTO
 */
public class UserRequestDto {
    
    @Size(max = 20, message = "用户名长度不能超过20个字符")
    private String username;
    
    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;

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
}
