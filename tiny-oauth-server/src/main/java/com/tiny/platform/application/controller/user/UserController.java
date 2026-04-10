package com.tiny.platform.application.controller.user;

import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.dto.UserCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.user.dto.UserRequestDto;
import com.tiny.platform.infrastructure.auth.user.dto.UserResponseDto;
import com.tiny.platform.infrastructure.auth.user.service.UserService;
import com.tiny.platform.infrastructure.core.dto.PageResponse;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationAuditRepository;
import com.tiny.platform.infrastructure.auth.user.service.AvatarService;
import com.tiny.platform.infrastructure.idempotent.sdk.annotation.Idempotent;
import com.tiny.platform.core.oauth.tenant.ActiveScope;
import com.tiny.platform.core.oauth.tenant.ActiveTenantResponseSupport;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.AuthUserResolutionService;
import com.tiny.platform.core.oauth.security.CurrentActorResolver;
import com.tiny.platform.core.oauth.security.PermissionVersionService;
import com.tiny.platform.infrastructure.auth.org.repository.OrganizationUnitRepository;
import com.tiny.platform.infrastructure.auth.org.repository.UserUnitRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/sys/users")
public class UserController {
    
    private final UserService userService;
    private final UserAuthenticationAuditRepository auditRepository;
    private final AvatarService avatarService;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final UserUnitRepository userUnitRepository;
    private final UserDetailsService userDetailsService;
    private final AuthUserResolutionService authUserResolutionService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private PermissionVersionService permissionVersionService;

    /**
     * 测试或简化场景使用（ORG/DEPT 与 UserDetails 为 null）。
     */
    public UserController(UserService userService,
                         UserAuthenticationAuditRepository auditRepository,
                         AvatarService avatarService) {
        this(userService, auditRepository, avatarService, null, null, null, null);
    }

    public UserController(UserService userService,
                         UserAuthenticationAuditRepository auditRepository,
                         AvatarService avatarService,
                         OrganizationUnitRepository organizationUnitRepository,
                         UserUnitRepository userUnitRepository,
                         UserDetailsService userDetailsService) {
        this(userService, auditRepository, avatarService, organizationUnitRepository, userUnitRepository, userDetailsService, null);
    }

    /**
     * Spring 容器使用的构造函数；存在多个 public 构造时必须显式标注，否则会回退无参构造并启动失败。
     */
    @Autowired
    public UserController(UserService userService,
                          UserAuthenticationAuditRepository auditRepository,
                          AvatarService avatarService,
                          OrganizationUnitRepository organizationUnitRepository,
                          UserUnitRepository userUnitRepository,
                          UserDetailsService userDetailsService,
                          AuthUserResolutionService authUserResolutionService) {
        this.userService = userService;
        this.auditRepository = auditRepository;
        this.avatarService = avatarService;
        this.organizationUnitRepository = organizationUnitRepository;
        this.userUnitRepository = userUnitRepository;
        this.userDetailsService = userDetailsService;
        this.authUserResolutionService = authUserResolutionService;
    }

    @GetMapping
    @PreAuthorize("@userManagementAccessGuard.canRead(authentication)")
    public ResponseEntity<PageResponse<UserResponseDto>> getUsers(
            @Valid UserRequestDto query,
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(new PageResponse<>(userService.users(query, pageable)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@userManagementAccessGuard.canRead(authentication)")
    public ResponseEntity<UserResponseDto> getUser(@PathVariable("id") Long id) {
        return userService.findUserDtoById(id)
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
                    Long activeTenantIdForPv = ActiveTenantResponseSupport.resolveActiveTenantId(authentication);
                    ActiveScope activeScope = ActiveTenantResponseSupport.resolveActiveScopeFromRequestContext();
                    ActiveTenantResponseSupport.putTenantFields(userInfo, activeTenantIdForPv, activeScope);
                    ActiveTenantResponseSupport.putScopeFields(userInfo, activeScope);
                    String activeScopeType = activeScope != null ? activeScope.scopeType() : null;
                    Long activeScopeId = activeScope != null ? activeScope.scopeId() : null;
                    userInfo.put(
                        "permissionsVersion",
                        CurrentActorResolver.resolvePermissionsVersionForResponse(
                            authentication,
                            activeTenantIdForPv,
                            activeScopeType,
                            activeScopeId,
                            permissionVersionService
                        )
                    );
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

    @PostMapping("/current/active-scope")
    public ResponseEntity<Map<String, Object>> switchActiveScope(@RequestBody ActiveScopeSwitchRequest body,
                                                                 HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "用户未认证"));
        }
        boolean bearerWrite = hasBearerAuthorization(request)
            || CurrentActorResolver.isJwtAuthenticationPrincipal(authentication);
        var boundUserOpt = userService.findByUsername(authentication.getName());
        if (boundUserOpt.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "用户不存在"));
        }
        User boundUser = boundUserOpt.get();
        Long resolvedPrincipalUserId = CurrentActorResolver.resolveUserId(authentication);
        if (resolvedPrincipalUserId == null || resolvedPrincipalUserId <= 0) {
            // Access tokens may omit userId when JwtTokenCustomizer could not attach SecurityUser (see USERID_IN_TOKEN_FIX.md).
            // After JWT validation, authentication.getName() is authoritative; DB-bound id matches that subject.
            resolvedPrincipalUserId = boundUser.getId();
        }
        if (!resolvedPrincipalUserId.equals(boundUser.getId())) {
            return ResponseEntity.status(403).body(Map.of(
                "success", false,
                "error", TenantContextContract.ERROR_BEARER_SUBJECT_USER_MISMATCH,
                "error_description", "Principal userId does not match the user resolved from sub/username."
            ));
        }
        SecurityUser sessionPrincipal = CurrentActorResolver.resolveSecurityUser(authentication);
        if (sessionPrincipal != null && sessionPrincipal.getUserId() != null
            && !sessionPrincipal.getUserId().equals(boundUser.getId())) {
            return ResponseEntity.status(403).body(Map.of(
                "success", false,
                "error", TenantContextContract.ERROR_BEARER_SUBJECT_USER_MISMATCH,
                "error_description", "Session SecurityUser userId does not match the bound user."
            ));
        }

        String requestedScopeType = body != null ? body.scopeType() : null;
        Long requestedScopeId = body != null ? body.scopeId() : null;
        String scopeType = requestedScopeType == null ? null : requestedScopeType.trim().toUpperCase(java.util.Locale.ROOT);
        if (!TenantContextContract.SCOPE_TYPE_PLATFORM.equals(scopeType)
            && !TenantContextContract.SCOPE_TYPE_TENANT.equals(scopeType)
            && !TenantContextContract.SCOPE_TYPE_ORG.equals(scopeType)
            && !TenantContextContract.SCOPE_TYPE_DEPT.equals(scopeType)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "非法 scopeType"));
        }

        Long currentActiveTenantId = CurrentActorResolver.resolveActiveTenantId(authentication);
        Long userId = boundUser.getId();

        Long tenantIdToSet = currentActiveTenantId;
        Long scopeIdToSet = currentActiveTenantId;
        if (TenantContextContract.SCOPE_TYPE_PLATFORM.equals(scopeType)) {
            if (authUserResolutionService == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "success", false,
                    "error", "platform_scope_switch_unavailable",
                    "error_description", "PLATFORM active scope validation is not available in this deployment"));
            }
            var platformUser = authUserResolutionService.resolveUserRecordInPlatform(authentication.getName());
            if (platformUser.isEmpty() || !Objects.equals(platformUser.get().getId(), boundUser.getId())) {
                return ResponseEntity.status(403).body(Map.of("success", false, "error", "当前用户不具备 PLATFORM 作用域赋权"));
            }
            tenantIdToSet = null;
            scopeIdToSet = null;
        } else if (TenantContextContract.SCOPE_TYPE_TENANT.equals(scopeType)) {
            Long requestedTenantId = requestedScopeId != null && requestedScopeId > 0 ? requestedScopeId : currentActiveTenantId;
            if (requestedTenantId == null || requestedTenantId <= 0) {
                return ResponseEntity.status(400).body(Map.of("success", false, "error", "缺少 activeTenantId 或 TENANT scopeId"));
            }
            boolean requiresTenantReResolution = currentActiveTenantId == null
                || currentActiveTenantId <= 0
                || !Objects.equals(currentActiveTenantId, requestedTenantId);
            if (requiresTenantReResolution) {
                if (authUserResolutionService == null) {
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                        "success", false,
                        "error", "tenant_scope_switch_unavailable",
                        "error_description", "TENANT active scope validation is not available in this deployment"));
                }
                var tenantUser = authUserResolutionService.resolveUserRecordInActiveTenant(authentication.getName(), requestedTenantId);
                if (tenantUser.isEmpty() || !Objects.equals(tenantUser.get().getId(), boundUser.getId())) {
                    return ResponseEntity.status(403).body(Map.of("success", false, "error", "当前用户不属于目标 tenant"));
                }
            }
            tenantIdToSet = requestedTenantId;
            scopeIdToSet = tenantIdToSet;
        } else {
            if (currentActiveTenantId == null || currentActiveTenantId <= 0) {
                return ResponseEntity.status(400).body(Map.of("success", false, "error", "缺少 activeTenantId"));
            }
            tenantIdToSet = currentActiveTenantId;
            if (requestedScopeId == null || requestedScopeId <= 0) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "error", "缺少 scopeId"));
            }
            if (organizationUnitRepository == null || userUnitRepository == null) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                    "success", false,
                        "error", "active_scope_deps_unavailable",
                        "error_description", "ORG/DEPT active scope validation is not available in this deployment"));
            }
            var unit = organizationUnitRepository.findByIdAndTenantId(requestedScopeId, tenantIdToSet).orElse(null);
            if (unit == null) {
                return ResponseEntity.status(403).body(Map.of("success", false, "error", "scopeId 不属于当前 tenant"));
            }
            String unitType = unit.getUnitType();
            if (TenantContextContract.SCOPE_TYPE_ORG.equals(scopeType) && !"ORG".equalsIgnoreCase(unitType)) {
                return ResponseEntity.status(403).body(Map.of("success", false, "error", "scopeType 与 scopeId 不匹配"));
            }
            if (TenantContextContract.SCOPE_TYPE_DEPT.equals(scopeType) && !"DEPT".equalsIgnoreCase(unitType)) {
                return ResponseEntity.status(403).body(Map.of("success", false, "error", "scopeType 与 scopeId 不匹配"));
            }
            boolean allowed = TenantContextContract.SCOPE_TYPE_DEPT.equals(scopeType)
                ? userUnitRepository.existsByTenantIdAndUserIdAndUnitId(tenantIdToSet, userId, requestedScopeId)
                : isUserInOrg(tenantIdToSet, userId, requestedScopeId);
            if (!allowed) {
                return ResponseEntity.status(403).body(Map.of("success", false, "error", "当前用户不属于目标 ORG/DEPT"));
            }
            scopeIdToSet = requestedScopeId;
        }

        var session = request.getSession(true);
        if (TenantContextContract.SCOPE_TYPE_PLATFORM.equals(scopeType)) {
            session.removeAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY);
        } else {
            session.setAttribute(TenantContextContract.SESSION_ACTIVE_TENANT_ID_KEY, tenantIdToSet);
        }
        session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_TYPE_KEY, scopeType);
        if (scopeIdToSet != null && scopeIdToSet > 0) {
            session.setAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY, scopeIdToSet);
        } else {
            session.removeAttribute(TenantContextContract.SESSION_ACTIVE_SCOPE_ID_KEY);
        }
        TenantContext.setTenantSource(TenantContext.SOURCE_SESSION);
        TenantContext.setActiveTenantId(TenantContextContract.SCOPE_TYPE_PLATFORM.equals(scopeType) ? null : tenantIdToSet);
        TenantContext.setActiveScopeType(scopeType);
        TenantContext.setActiveScopeId(scopeIdToSet);

        if (userDetailsService != null) {
            var updated = userDetailsService.loadUserByUsername(authentication.getName());
            var newAuth = org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                updated,
                authentication.getCredentials(),
                updated.getAuthorities()
            );
            newAuth.setDetails(authentication.getDetails());
            SecurityContextHolder.getContext().setAuthentication(newAuth);
        }

        Map<String, Object> okBody = new HashMap<>();
        okBody.put("success", true);
        okBody.put("activeScopeType", scopeType);
        okBody.put("activeScopeId", scopeIdToSet);
        okBody.put("newActiveScopeType", scopeType);
        okBody.put("newActiveScopeId", scopeIdToSet);
        okBody.put("tokenRefreshRequired", bearerWrite);
        ActiveTenantResponseSupport.putTenantFields(okBody, tenantIdToSet, ActiveScope.of(scopeType, scopeIdToSet));
        if (bearerWrite) {
            okBody.put(
                "tokenRefreshReason",
                "Bearer access token may still carry stale activeScopeType/activeScopeId claims; refresh or re-issue the token to match the new session scope, or the next request can fail closed as M5 scope conflict."
            );
        }
        return ResponseEntity.ok(okBody);
    }

    private static boolean hasBearerAuthorization(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        return authorization != null
            && authorization.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length());
    }

    private boolean isUserInOrg(Long tenantId, Long userId, Long orgId) {
        if (organizationUnitRepository == null || userUnitRepository == null) {
            return false;
        }
        List<Long> unitIds = userUnitRepository.findUnitIdsByTenantIdAndUserIdAndStatus(tenantId, userId, "ACTIVE");
        if (unitIds == null || unitIds.isEmpty()) {
            return false;
        }
        java.util.Map<Long, com.tiny.platform.infrastructure.auth.org.domain.OrganizationUnit> cache = new java.util.HashMap<>();
        for (Long unitId : unitIds) {
            Long current = unitId;
            int guard = 0;
            java.util.Set<Long> visited = new java.util.LinkedHashSet<>();
            while (current != null && current > 0 && visited.add(current) && guard++ < 50) {
                if (orgId.equals(current)) {
                    return true;
                }
                com.tiny.platform.infrastructure.auth.org.domain.OrganizationUnit unit =
                    cache.computeIfAbsent(current, id -> organizationUnitRepository.findByIdAndTenantId(id, tenantId).orElse(null));
                if (unit == null) {
                    break;
                }
                current = unit.getParentId();
            }
        }
        return false;
    }

    /** 供 {@link #switchActiveScope} 与测试直接构造请求体。 */
    public record ActiveScopeSwitchRequest(String scopeType, Long scopeId) {}

    @PostMapping
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@userManagementAccessGuard.canCreate(authentication)")
    public ResponseEntity<User> create(@Valid @RequestBody UserCreateUpdateDto userDto) {
        User user = userService.createFromDto(userDto);
        return ResponseEntity.ok(user);
    }

    @PutMapping("/{id}")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@userManagementAccessGuard.canUpdate(authentication)")
    public ResponseEntity<User> update(@PathVariable("id") Long id, @Valid @RequestBody UserCreateUpdateDto userDto) {
        // 设置ID
        userDto.setId(id);
        
        User user = userService.updateFromDto(userDto);
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/{id}")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@userManagementAccessGuard.canDelete(authentication)")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * 批量启用用户
     */
    @PostMapping("/batch/enable")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@userManagementAccessGuard.canEnable(authentication)")
    public ResponseEntity<Map<String, Object>> batchEnable(@RequestBody List<Long> ids) {
        userService.batchEnable(ids);
        return ResponseEntity.ok(Map.of("success", true, "message", "批量启用成功"));
    }
    
    /**
     * 批量禁用用户
     */
    @PostMapping("/batch/disable")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@userManagementAccessGuard.canDisable(authentication)")
    public ResponseEntity<Map<String, Object>> batchDisable(@RequestBody List<Long> ids) {
        userService.batchDisable(ids);
        return ResponseEntity.ok(Map.of("success", true, "message", "批量禁用成功"));
    }
    
    /**
     * 批量删除用户
     */
    @PostMapping("/batch/delete")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@userManagementAccessGuard.canDelete(authentication)")
    public ResponseEntity<Map<String, Object>> batchDelete(@RequestBody List<Long> ids) {
        userService.batchDelete(ids);
        return ResponseEntity.ok(Map.of("success", true, "message", "批量删除成功"));
    }

    /**
     * 查询指定用户已绑定的角色ID列表
     */
    @GetMapping("/{id}/roles")
    @PreAuthorize("@userManagementAccessGuard.canUpdate(authentication)")
    public ResponseEntity<List<Long>> getUserRoles(@PathVariable("id") Long id,
                                                   @RequestParam(value = "scopeType", required = false) String scopeType,
                                                   @RequestParam(value = "scopeId", required = false) Long scopeId) {
        try {
            return ResponseEntity.ok(userService.getDirectRoleIdsByUserId(id, scopeType, scopeId));
        } catch (RuntimeException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 保存用户角色绑定
     */
    @PostMapping("/{id}/roles")
    @Idempotent(key = "#request.getHeader('X-Idempotency-Key')", failOpen = false)
    @PreAuthorize("@userManagementAccessGuard.canUpdate(authentication)")
    public ResponseEntity<?> updateUserRoles(@PathVariable("id") Long id, @RequestBody Object body) {
        try {
            var request = parseRoleAssignmentRequest(body);
            userService.updateUserRoles(id, request.scopeType(), request.scopeId(), request.roleIds());
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
            String contentHash = metadata.getContentHash();
            String etag = contentHash != null ? "\"" + contentHash + "\"" : null;
            String ifNoneMatch = request.getHeader("If-None-Match");
            if (etag != null && etag.equals(ifNoneMatch)) {
                // 304 Not Modified
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .build();
            }

            // 构建响应头
            HttpHeaders headers = new HttpHeaders();
            String contentType = metadata.getContentType();
            headers.setContentType(MediaType.parseMediaType(contentType != null ? contentType : "application/octet-stream"));
            headers.setContentLength(metadata.getFileSize());
            if (etag != null) {
                headers.setETag(etag);
            }
            // 缓存控制：public，最大缓存7天
            headers.setCacheControl(CacheControl.maxAge(Objects.requireNonNull(Duration.ofDays(7)))
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

    private RoleAssignmentRequest parseRoleAssignmentRequest(Object body) {
        if (body instanceof List<?> rawRoleIds) {
            return new RoleAssignmentRequest(null, null, parseIdList(rawRoleIds));
        }
        if (body instanceof Map<?, ?> requestMap) {
            Object roleIds = requestMap.get("roleIds");
            return new RoleAssignmentRequest(
                requestMap.get("scopeType") != null ? String.valueOf(requestMap.get("scopeType")) : null,
                parseNullableLong(requestMap.get("scopeId")),
                roleIds instanceof List<?> rawRoleIds ? parseIdList(rawRoleIds) : List.of()
            );
        }
        throw new IllegalArgumentException("请求体格式不正确");
    }

    private List<Long> parseIdList(List<?> rawIds) {
        List<Long> ids = new ArrayList<>();
        for (Object rawId : rawIds) {
            ids.add(parseNullableLong(rawId));
        }
        return ids;
    }

    private Long parseNullableLong(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        if (rawValue instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(rawValue));
    }

    private record RoleAssignmentRequest(String scopeType, Long scopeId, List<Long> roleIds) {}
}
