package com.tiny.platform.application.oauth.model;

public class RoleRequestDto {
    private String name;
    private String code; // 角色标识
    private String description;
    // getter/setter
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
} 