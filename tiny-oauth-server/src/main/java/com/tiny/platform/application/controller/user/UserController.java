package com.tiny.platform.application.controller.user;

import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.dto.UserCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.user.dto.UserRequestDto;
import com.tiny.platform.infrastructure.auth.user.dto.UserResponseDto;
import com.tiny.platform.infrastructure.auth.user.service.UserService;
import com.tiny.platform.infrastructure.core.dto.PageResponse;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationAuditRepository;
import com.tiny.platform.infrastructure.auth.user.service.AvatarService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/sys/users")
public class UserController {
    
    private final UserService userService;
    private final UserAuthenticationAuditRepository auditRepository;
    private final AvatarService avatarService;
    
    public UserController(UserService userService, 
                         UserAuthenticationAuditRepository auditRepository,
                         AvatarService avatarService) {
        this.userService = userService;
        this.auditRepository = auditRepository;
        this.avatarService = avatarService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<UserResponseDto>> getUsers(
            @Valid UserRequestDto query,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(new PageResponse<>(userService.users(query, pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<com.tiny.platform.infrastructure.auth.user.domain.User> getUser(@PathVariable("id") Long id) {
        return userService.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrentUser() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "error", "用户未认证"
                ));
            }

            String username = authentication.getName();
            return userService.findByUsername(username)
                .map(user -> {
                    // 返回完整的用户信息，包括所有状态字段
                    Map<String, Object> userInfo = new java.util.HashMap<>();
                    userInfo.put("id", user.getId().toString());
                    userInfo.put("username", user.getUsername());
                    userInfo.put("nickname", user.getNickname() != null ? user.getNickname() : "");
                    userInfo.put("enabled", user.isEnabled());
                    userInfo.put("accountNonExpired", user.isAccountNonExpired());
                    userInfo.put("accountNonLocked", user.isAccountNonLocked());
                    userInfo.put("credentialsNonExpired", user.isCredentialsNonExpired());
                    userInfo.put("email", user.getEmail() != null ? user.getEmail() : "");
                    userInfo.put("phone", user.getPhone() != null ? user.getPhone() : "");
                    if (user.getLastLoginAt() != null) {
                        userInfo.put("lastLoginAt", user.getLastLoginAt().toString());
                    }
                    if (user.getLastLoginIp() != null) {
                        userInfo.put("lastLoginIp", user.getLastLoginIp());
                    }
                    if (user.getLastLoginDevice() != null) {
                        userInfo.put("lastLoginDevice", user.getLastLoginDevice());
                    }
                    userInfo.put("failedLoginCount", user.getFailedLoginCount() != null ? user.getFailedLoginCount() : 0);
                    if (user.getLastFailedLoginAt() != null) {
                        userInfo.put("lastFailedLoginAt", user.getLastFailedLoginAt().toString());
                    }
                    return ResponseEntity.ok(userInfo);
                })
                .orElse(ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "用户不存在"
                )));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "获取用户信息失败"
            ));
        }
    }

    @PostMapping
    public ResponseEntity<User> create(@Valid @RequestBody UserCreateUpdateDto userDto) {
        User user = userService.createFromDto(userDto);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> update(@PathVariable("id") Long id, @Valid @RequestBody UserCreateUpdateDto userDto) {
        // 设置ID
        userDto.setId(id);
        
        User user = userService.updateFromDto(userDto);
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * 批量启用用户
     */
    @PostMapping("/batch/enable")
    public ResponseEntity<Map<String, Object>> batchEnable(@RequestBody List<Long> ids) {
        userService.batchEnable(ids);
        return ResponseEntity.ok(Map.of("success", true, "message", "批量启用成功"));
    }
    
    /**
     * 批量禁用用户
     */
    @PostMapping("/batch/disable")
    public ResponseEntity<Map<String, Object>> batchDisable(@RequestBody List<Long> ids) {
        userService.batchDisable(ids);
        return ResponseEntity.ok(Map.of("success", true, "message", "批量禁用成功"));
    }
    
    /**
     * 批量删除用户
     */
    @PostMapping("/batch/delete")
    public ResponseEntity<Map<String, Object>> batchDelete(@RequestBody List<Long> ids) {
        userService.batchDelete(ids);
        return ResponseEntity.ok(Map.of("success", true, "message", "批量删除成功"));
    }

    /**
     * 查询指定用户已绑定的角色ID列表
     */
    @GetMapping("/{id}/roles")
    public ResponseEntity<List<Long>> getUserRoles(@PathVariable("id") Long id) {
        return userService.findById(id)
            .map(user -> ResponseEntity.ok(user.getRoles().stream().map(role -> role.getId()).toList()))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 保存用户角色绑定
     */
    @PostMapping("/{id}/roles")
    public ResponseEntity<?> updateUserRoles(@PathVariable("id") Long id, @RequestBody List<Long> roleIds) {
        try {
            userService.updateUserRoles(id, roleIds);
            return ResponseEntity.ok(Map.of("success", true, "message", "用户角色已更新"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 获取当前用户的登录历史（认证审计记录）
     */
    @GetMapping("/current/login-history")
    public ResponseEntity<Map<String, Object>> getCurrentUserLoginHistory(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "error", "用户未认证"
                ));
            }

            String username = authentication.getName();
            return userService.findByUsername(username)
                .<ResponseEntity<Map<String, Object>>>map(user -> {
                    // 查询登录事件（LOGIN 类型）
                    var auditPage = auditRepository.findByUserIdAndEventTypeOrderByCreatedAtDesc(
                        user.getId(), "LOGIN", pageable);
                    
                    // 转换为前端需要的格式
                    List<Map<String, Object>> historyList = auditPage.getContent().stream()
                        .map(audit -> {
                            Map<String, Object> record = new java.util.HashMap<>();
                            record.put("id", audit.getId());
                            record.put("eventType", audit.getEventType());
                            record.put("success", audit.getSuccess());
                            record.put("authenticationProvider", audit.getAuthenticationProvider() != null ? audit.getAuthenticationProvider() : "");
                            record.put("authenticationFactor", audit.getAuthenticationFactor() != null ? audit.getAuthenticationFactor() : "");
                            record.put("ipAddress", audit.getIpAddress() != null ? audit.getIpAddress() : "");
                            record.put("userAgent", audit.getUserAgent() != null ? audit.getUserAgent() : "");
                            record.put("createdAt", audit.getCreatedAt().toString());
                            return record;
                        })
                        .collect(Collectors.toList());
                    
                    Map<String, Object> result = new java.util.HashMap<>();
                    result.put("success", true);
                    result.put("content", historyList);
                    result.put("totalElements", auditPage.getTotalElements());
                    result.put("totalPages", auditPage.getTotalPages());
                    result.put("number", auditPage.getNumber());
                    result.put("size", auditPage.getSize());
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "用户不存在"
                )));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage() != null ? e.getMessage() : "获取登录历史失败"
                ));
        }
    }

    /**
     * 上传当前用户头像
     */
    @PostMapping("/current/avatar")
    public ResponseEntity<Map<String, Object>> uploadCurrentUserAvatar(@RequestParam("file") MultipartFile file) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "error", "用户未认证"
                ));
            }

            String username = authentication.getName();
            return userService.findByUsername(username)
                .<ResponseEntity<Map<String, Object>>>map(user -> {
                    try {
                        // 验证文件
                        if (file.isEmpty()) {
                            Map<String, Object> errorBody = Map.of(
                                "success", false,
                                "error", "文件不能为空"
                            );
                            return ResponseEntity.badRequest().body(errorBody);
                        }

                        // 上传头像
                        boolean success = avatarService.uploadAvatar(
                            user.getId(),
                            file.getInputStream(),
                            file.getContentType(),
                            file.getOriginalFilename(),
                            file.getSize()
                        );

                        if (success) {
                            Map<String, Object> successBody = Map.of(
                                "success", true,
                                "message", "头像上传成功"
                            );
                            return ResponseEntity.ok(successBody);
                        } else {
                            Map<String, Object> errorBody = Map.of(
                                "success", false,
                                "error", "头像上传失败"
                            );
                            return ResponseEntity.badRequest().body(errorBody);
                        }
                    } catch (IllegalArgumentException e) {
                        Map<String, Object> errorBody = Map.of(
                            "success", false,
                            "error", e.getMessage()
                        );
                        return ResponseEntity.badRequest().body(errorBody);
                    } catch (Exception e) {
                        Map<String, Object> errorBody = Map.of(
                            "success", false,
                            "error", "头像上传失败: " + e.getMessage()
                        );
                        return ResponseEntity.status(500).body(errorBody);
                    }
                })
                .orElse(ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "用户不存在"
                )));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "头像上传失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 获取用户头像（支持缓存和ETag）
     */
    @GetMapping("/{id}/avatar")
    public ResponseEntity<StreamingResponseBody> getUserAvatar(
            @PathVariable("id") Long userId,
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            // 获取头像元信息
            AvatarService.AvatarMetadata metadata = avatarService.getAvatarMetadata(userId);
            if (metadata == null) {
                return ResponseEntity.notFound().build();
            }

            // 获取头像数据
            byte[] avatarData = avatarService.getAvatarData(userId);
            if (avatarData == null) {
                return ResponseEntity.notFound().build();
            }

            // 设置ETag（使用content_hash）
            String etag = "\"" + metadata.getContentHash() + "\"";
            String ifNoneMatch = request.getHeader("If-None-Match");
            if (ifNoneMatch != null && ifNoneMatch.equals(etag)) {
                // 304 Not Modified
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .build();
            }

            // 构建响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(metadata.getContentType()));
            headers.setContentLength(metadata.getFileSize());
            headers.setETag(etag);
            // 缓存控制：public，最大缓存7天
            headers.setCacheControl(CacheControl.maxAge(Duration.ofDays(7))
                .cachePublic()
                .mustRevalidate()
                .getHeaderValue());

            // 流式返回
            StreamingResponseBody responseBody = outputStream -> {
                try (ByteArrayInputStream bis = new ByteArrayInputStream(avatarData)) {
                    bis.transferTo(outputStream);
                }
            };

            return ResponseEntity.ok()
                .headers(headers)
                .body(responseBody);

        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 获取当前用户头像
     */
    @GetMapping("/current/avatar")
    public ResponseEntity<StreamingResponseBody> getCurrentUserAvatar(
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).build();
            }

            String username = authentication.getName();
            return userService.findByUsername(username)
                .map(user -> getUserAvatar(user.getId(), request, response))
                .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * 删除当前用户头像
     */
    @DeleteMapping("/current/avatar")
    public ResponseEntity<Map<String, Object>> deleteCurrentUserAvatar() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).body(Map.of(
                    "success", false,
                    "error", "用户未认证"
                ));
            }

            String username = authentication.getName();
            return userService.findByUsername(username)
                .<ResponseEntity<Map<String, Object>>>map(user -> {
                    boolean success = avatarService.deleteAvatar(user.getId());
                    if (success) {
                        Map<String, Object> successBody = Map.of(
                            "success", true,
                            "message", "头像删除成功"
                        );
                        return ResponseEntity.ok(successBody);
                    } else {
                        Map<String, Object> errorBody = Map.of(
                            "success", false,
                            "error", "头像不存在"
                        );
                        return ResponseEntity.badRequest().body(errorBody);
                    }
                })
                .orElse(ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "error", "用户不存在"
                )));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", "头像删除失败: " + e.getMessage()
            ));
        }
    }
}