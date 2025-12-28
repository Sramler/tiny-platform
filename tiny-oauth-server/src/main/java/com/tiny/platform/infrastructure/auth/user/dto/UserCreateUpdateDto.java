package com.tiny.platform.infrastructure.auth.user.dto;

import com.tiny.platform.infrastructure.core.validation.PasswordConfirm;
import jakarta.validation.constraints.*;
import java.util.List;

/**
 * 用户创建和更新请求DTO
 * 用于处理前端提交的用户数据，包含密码校验
 */
@PasswordConfirm
public class UserCreateUpdateDto {
    
    private Long id; // 用户ID，更新时使用
    
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度必须在3-20个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;
    
    @NotBlank(message = "昵称不能为空")
    @Size(max = 50, message = "昵称长度不能超过50个字符")
    private String nickname;
    
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    private String email; // 可选字段，更新时可为空
    
    @Pattern(regexp = "^(1[3-9]\\d{9})?$", message = "手机号格式不正确（11位数字，以1开头）")
    @Size(max = 20, message = "手机号长度不能超过20个字符")
    private String phone; // 可选字段，更新时可为空
    
    // 密码字段，创建时必填，更新时可选
    private String password;
    
    // 确认密码字段，用于前端校验
    private String confirmPassword;
    
    @NotNull(message = "是否启用不能为空")
    private Boolean enabled = true;
    
    @NotNull(message = "账号是否未过期不能为空")
    private Boolean accountNonExpired = true;
    
    @NotNull(message = "账号是否未锁定不能为空")
    private Boolean accountNonLocked = true;
    
    @NotNull(message = "密码是否未过期不能为空")
    private Boolean credentialsNonExpired = true;
    
    private List<Long> roleIds;
    
    // 构造函数
    public UserCreateUpdateDto() {}
    
    // Getter和Setter方法
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
    
    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getConfirmPassword() {
        return confirmPassword;
    }
    
    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
    
    public Boolean getEnabled() {
        return enabled;
    }
    
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
    
    public Boolean getAccountNonExpired() {
        return accountNonExpired;
    }
    
    public void setAccountNonExpired(Boolean accountNonExpired) {
        this.accountNonExpired = accountNonExpired;
    }
    
    public Boolean getAccountNonLocked() {
        return accountNonLocked;
    }
    
    public void setAccountNonLocked(Boolean accountNonLocked) {
        this.accountNonLocked = accountNonLocked;
    }
    
    public Boolean getCredentialsNonExpired() {
        return credentialsNonExpired;
    }
    
    public void setCredentialsNonExpired(Boolean credentialsNonExpired) {
        this.credentialsNonExpired = credentialsNonExpired;
    }
    
    public List<Long> getRoleIds() {
        return roleIds;
    }
    
    public void setRoleIds(List<Long> roleIds) {
        this.roleIds = roleIds;
    }
    
    /**
     * 判断是否为创建模式
     * @return true表示创建，false表示更新
     */
    public boolean isCreateMode() {
        return id == null;
    }
    
    /**
     * 判断是否需要更新密码
     * @return true表示需要更新密码，false表示不更新密码
     */
    public boolean needUpdatePassword() {
        return password != null && !password.trim().isEmpty();
    }
} 