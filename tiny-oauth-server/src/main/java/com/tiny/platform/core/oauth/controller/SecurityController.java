package com.tiny.platform.core.oauth.controller;

import com.tiny.platform.core.oauth.config.CustomWebAuthenticationDetailsSource;
import com.tiny.platform.core.oauth.config.FrontendProperties;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationMethodRepository;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationSessionManager;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken;
import com.tiny.platform.core.oauth.service.AuthenticationAuditService;
import com.tiny.platform.core.oauth.service.SecurityService;
import com.tiny.platform.infrastructure.core.util.IpUtils;
import com.tiny.platform.infrastructure.core.util.DeviceUtils;
import com.tiny.platform.infrastructure.core.util.QrCodeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 用户安全与TOTP相关接口 Controller层，所有业务逻辑由 SecurityService 负责
 *
 * 根据配置区分开发环境和生产环境：
 * - 开发环境：重定向到 Vite dev server (http://localhost:5173)
 * - 生产环境：转发到打包后的静态文件 (/dist/index.html)
 */
@Controller
@RequestMapping("/self/security")
public class SecurityController {
    private final UserRepository userRepository;
    private final SecurityService securityService;
    private final UserAuthenticationMethodRepository authenticationMethodRepository;
    private final FrontendProperties frontendProperties;
    private final MultiFactorAuthenticationSessionManager sessionManager;
    private final AuthenticationAuditService auditService;

    @Autowired
    public SecurityController(UserRepository userRepository,
                             SecurityService securityService,
                             UserAuthenticationMethodRepository authenticationMethodRepository,
                             FrontendProperties frontendProperties,
                             MultiFactorAuthenticationSessionManager sessionManager,
                             AuthenticationAuditService auditService) {
        this.userRepository = userRepository;
        this.securityService = securityService;
        this.authenticationMethodRepository = authenticationMethodRepository;
        this.frontendProperties = frontendProperties;
        this.sessionManager = sessionManager;
        this.auditService = auditService;
    }

    /** 查询当前用户的安全状态 */
    @GetMapping("/status")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> status() {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("success", false, "error", "未登录"));
        return ResponseEntity.ok(securityService.getSecurityStatus(user));
    }

    /**
     * 绑定TOTP
     * 说明：用户已登录（已通过密码或其他方式验证），绑定 TOTP 时仅需要 TOTP 码验证
     * 理由：
     * 1. 用户已经通过密码登录，身份已验证
     * 2. TOTP 码本身就是一个强验证因子（"你拥有什么"）
     * 3. 绑定 TOTP 是为了增强安全性，而不是降低安全性
     * 4. 简化流程，提升用户体验
     */
    @PostMapping("/totp/bind")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> bindTotp(@RequestBody Map<String, String> req) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("success", false, "error", "未登录"));

        // 用户已登录，不需要再次验证密码，仅需要 TOTP 码
        String totpCode = req.get("totpCode");
        if (totpCode == null || totpCode.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "缺少TOTP验证码"));
        }

        // 传递 null 作为密码，表示不需要密码验证
        return ResponseEntity.ok(securityService.bindTotp(user, null, totpCode));
    }

    /**
     * 解绑TOTP
     * 说明：解绑是敏感操作（会降低账户安全性），需要更强的验证
     * - 必须验证 TOTP 码
     * - 如果用户有本地密码，推荐验证密码（可选，但强烈推荐）
     * 理由：
     * 1. 解绑 TOTP 会降低账户安全性
     * 2. 需要确保是用户本人操作
     * 3. 提供双重验证（密码 + TOTP）可以更好地防止误操作
     */
    @PostMapping("/totp/unbind")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> unbindTotp(@RequestBody Map<String, String> req,
                                                          HttpServletRequest request) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("success", false, "error", "未登录"));

        // 获取用户当前的登录方式
        String[] loginMethod = getCurrentLoginMethod();
        String provider = loginMethod[0];  // authenticationProvider
        String type = loginMethod[1];      // authenticationType

        // 如果用户是通过 LOCAL + PASSWORD 登录的，且数据库中有本地密码记录，推荐验证密码
        // 但为了兼容性，如果用户不提供密码，也可以仅通过 TOTP 码解绑
        String plainPassword = null;
        if ("LOCAL".equals(provider) && "PASSWORD".equals(type)) {
            // 检查用户是否有本地密码记录
            boolean hasLocalPassword = authenticationMethodRepository
                    .existsByUserIdAndAuthenticationProviderAndAuthenticationType(user.getId(), "LOCAL", "PASSWORD");
            if (hasLocalPassword) {
                // 推荐提供密码，但不强制（为了兼容性）
                plainPassword = req.get("password");
                // 如果用户提供了密码，则验证；如果没有提供，仅通过 TOTP 码解绑
            }
        }

        String totpCode = req.get("totpCode");
        if (totpCode == null || totpCode.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "缺少TOTP验证码"));
        }
        
        Map<String, Object> result = securityService.unbindTotp(user, plainPassword, totpCode);
        if (Boolean.TRUE.equals(result.get("success"))) {
            // 记录MFA解绑审计
            auditService.recordMfaUnbind(user.getUsername(), user.getId(), "TOTP", request);
        }
        return ResponseEntity.ok(result);
    }

    /** step-up、敏感操作TOTP校验 */
    @PostMapping("/totp/check")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> checkTotp(@RequestBody Map<String, String> req) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("success", false, "error", "未登录"));
        String totpCode = req.get("totpCode");
        if (totpCode == null)
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "缺少参数"));
        return ResponseEntity.ok(securityService.checkTotp(user, totpCode));
    }

    /** 跳过/不再提醒绑定TOTP */
    @PostMapping("/skip-mfa-remind")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> skipMfaRemind(@RequestBody Map<String, Object> req) {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("success", false, "error", "未登录"));
        boolean skip = Boolean.TRUE.equals(req.get("skipMfaRemind"));
        return ResponseEntity.ok(securityService.skipMfaRemind(user, skip));
    }

    /**
     * 获取TOTP绑定二维码信息（未激活阶段，仅扫码用 otpauthUri，兼容iPhone等密码工具）
     */
    @GetMapping("/totp/pre-bind")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> preBindTotp() {
        User user = getCurrentUser();
        if (user == null) return ResponseEntity.status(401).body(Map.of("success", false, "error", "未登录"));
        Map<String, Object> result = new java.util.HashMap<>(securityService.preBindTotp(user));
        if (Boolean.TRUE.equals(result.get("success"))) {
            Object otpauthUri = result.get("otpauthUri");
            if (otpauthUri != null) {
                String qrCode = QrCodeUtil.generateBase64QrCode(String.valueOf(otpauthUri));
                if (qrCode != null && !qrCode.isEmpty()) {
                    result.put("qrCodeDataUrl", qrCode);
                }
            }
        }
        return ResponseEntity.ok(result);
    }

    /**
     * GET TOTP绑定页（页面渲染）
     * 支持部分认证 Token（已完成 PASSWORD 验证，等待 TOTP 验证）
     *
     * 根据配置区分开发环境和生产环境：
     * - 开发环境：重定向到 Vite dev server (http://localhost:5173/self/security/totp-bind)
     * - 生产环境：转发到打包后的静态文件 (/dist/index.html)
     */
    @GetMapping("/totp-bind")
    public String totpBindPage(@RequestParam(value="redirect",required=false,defaultValue="/") String redirect,
                               HttpServletRequest request) {
        return buildFrontendUrl(frontendProperties.getTotpBindUrl(), request);
    }

    /**
     * POST TOTP绑定表单（页面表单提交），成功后重定向回原目标
     * 使用 -form 后缀以避免与 JSON 接口 /totp/bind 冲突
     * 注意：password 参数是可选的，如果用户没有本地密码（如通过 OAuth2 登录），则不需要密码
     * 支持部分认证 Token（已完成 PASSWORD 验证，等待 TOTP 验证）
     */
    @PostMapping("/totp/bind-form")
    public String bindTotpForm(@RequestParam String totpCode,
                               @RequestParam(required = false) String password,
                               @RequestParam String redirect,
                               HttpServletRequest request,
                               jakarta.servlet.http.HttpServletResponse response) {
        User user = getCurrentUser();
        if (user == null) {
            return "redirect:/login?error=" + URLEncoder.encode("未登录", StandardCharsets.UTF_8);
        }

        Map<String, Object> result = securityService.bindTotp(user, password, totpCode);
        if(Boolean.TRUE.equals(result.get("success"))) {
            // 记录登录IP和登录时间
            recordLoginInfo(user, request);
            // 记录MFA绑定审计
            auditService.recordMfaBind(user.getUsername(), user.getId(), "TOTP", request);
            promoteToFullyAuthenticated(user, request, response);
            return buildRedirectUrl(redirect);
        } else {
            String error = String.valueOf(result.getOrDefault("error", "绑定失败"));
            String encodedRedirect = URLEncoder.encode(redirect, StandardCharsets.UTF_8);
            String encodedError = URLEncoder.encode(error, StandardCharsets.UTF_8);
            return "redirect:/self/security/totp-bind?redirect=" + encodedRedirect + "&error=" + encodedError;
        }
    }

    /**
     * POST 跳过二次认证（页面表单），成功后重定向回原目标
     * 支持部分认证 Token（已完成 PASSWORD 验证，等待 TOTP 验证）
     */
    @PostMapping("/totp/skip")
    public String skipTotp(@RequestParam String redirect) {
        User user = getCurrentUser();
        if (user == null) {
            return "redirect:/login?error=" + URLEncoder.encode("未登录", StandardCharsets.UTF_8);
        }
        securityService.skipMfaRemind(user, true);
        return buildRedirectUrl(redirect);
    }

    /**
     * GET 二步验证step-up页面（页面渲染）
     *
     * 根据配置区分开发环境和生产环境：
     * - 开发环境：重定向到 Vite dev server (http://localhost:5173/self/security/totp-verify)
     * - 生产环境：转发到打包后的静态文件 (/dist/index.html)
     */
    @GetMapping("/totp-verify")
    public String totpVerifyPage(@RequestParam(value="redirect",required=false,defaultValue="/") String redirect,
                                 HttpServletRequest request) {
        return buildFrontendUrl(frontendProperties.getTotpVerifyUrl(), request);
    }

    /**
     * POST 二步校验表单（页面表单提交）
     * 使用 -form 后缀以避免与 JSON 接口 /totp/check 冲突
     * 注意：step-up 认证通常要求用户已完全认证，但为了安全性和一致性，也支持部分认证 Token
     */
    @PostMapping("/totp/check-form")
    public String checkTotpForm(@RequestParam String totpCode,
                                @RequestParam String redirect,
                                HttpServletRequest request,
                                jakarta.servlet.http.HttpServletResponse response) {
        User user = getCurrentUser();
        if (user == null) {
            String encodedRedirect = URLEncoder.encode(redirect, StandardCharsets.UTF_8);
            String encodedError = URLEncoder.encode("未登录", StandardCharsets.UTF_8);
            return "redirect:/self/security/totp-verify?redirect=" + encodedRedirect + "&error=" + encodedError;
        }

        Map<String, Object> result = securityService.checkTotp(user, totpCode);
        if(Boolean.TRUE.equals(result.get("success"))) {
            // 记录登录IP和登录时间
            recordLoginInfo(user, request);
            // 记录登录成功审计（TOTP验证完成，完全登录）
            auditService.recordLoginSuccess(user.getUsername(), user.getId(), "LOCAL", "MFA", request);
            promoteToFullyAuthenticated(user, request, response);
            return buildRedirectUrl(redirect);
        } else {
            String error = String.valueOf(result.getOrDefault("error", "验证失败"));
            String encodedRedirect = URLEncoder.encode(redirect, StandardCharsets.UTF_8);
            String encodedError = URLEncoder.encode(error, StandardCharsets.UTF_8);
            return "redirect:/self/security/totp-verify?redirect=" + encodedRedirect + "&error=" + encodedError;
        }
    }

    // ----- 通用获取用户方法 -----
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }

        // 支持部分认证 Token（MultiFactorAuthenticationToken with completedFactors）
        // 即使 authenticated=false，只要有已完成因子，也应该允许获取用户信息
        if (authentication instanceof com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken mfaToken) {
            // 如果是多因素认证 Token，即使 authenticated=false，只要有已完成因子，也允许访问
            if (!mfaToken.getCompletedFactors().isEmpty() || mfaToken.isAuthenticated()) {
                String username = mfaToken.getUsername();
                return userRepository.findUserByUsername(username).orElse(null);
            }
        }

        // 完全认证的 Token
        if (authentication.isAuthenticated()) {
            String username = authentication.getName();
            return userRepository.findUserByUsername(username).orElse(null);
        }

        return null;
    }

    /**
     * 获取当前用户的登录方式
     * <p>
     * 支持部分认证的 {@link MultiFactorAuthenticationToken}（即使 {@code authenticated=false}，
     * 只要有已完成因子，也能获取登录方式，与 {@link #getCurrentUser()} 保持一致）。
     *
     * @return [authenticationProvider, authenticationType]，如果无法确定则返回 [null, null]
     */
    private String[] getCurrentLoginMethod() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return new String[]{null, null};
        }

        // 优先处理 MultiFactorAuthenticationToken（允许部分认证）
        // 即使 authenticated=false，只要有已完成因子，也应该允许获取登录方式
        if (authentication instanceof com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken mfa) {
            String provider = mfa.getAuthenticationProvider();
            String type = mfa.getAuthenticationType();
            // 如果 provider 为 null 或 type 为 UNKNOWN，表示无法确定登录方式
            if (provider == null || "UNKNOWN".equals(type)) {
                return new String[]{null, null};
            }
            return new String[]{provider, type};
        }

        // 对于其他 Token，确保已认证再返回
        if (!authentication.isAuthenticated()) {
            return new String[]{null, null};
        }

        // 尝试从 Authentication.getDetails() 中获取登录方式
        Object details = authentication.getDetails();
        if (details instanceof CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails) {
            CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails customDetails =
                    (CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails) details;
            String provider = customDetails.getAuthenticationProvider();
            String type = customDetails.getAuthenticationType();
            return new String[]{provider, type};
        }

        // 默认返回 null，表示无法确定登录方式
        // 这种情况下，Service 层会回退到检查数据库中是否存在 LOCAL + PASSWORD 记录
        return new String[]{null, null};
    }

    /**
     * 升级当前会话为完全认证（完成 PASSWORD + TOTP）
     * 委托给 MultiFactorAuthenticationSessionManager，并持久化到 session
     */
    private void promoteToFullyAuthenticated(User user, HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        sessionManager.promoteToFullyAuthenticated(user, request, response);
    }

    /**
     * 构建前端 URL，支持查询参数传递
     * - 如果是 redirect: 前缀，将查询参数附加到重定向 URL
     * - 如果是 forward: 前缀，直接返回（forward 会自动保留查询参数）
     */
    private String buildFrontendUrl(String configuredUrl, HttpServletRequest request) {
        if (configuredUrl.startsWith("redirect:")) {
            // 开发环境：重定向到 Vite dev server，需要附加查询参数
            String baseUrl = configuredUrl.substring("redirect:".length());
            String queryString = request.getQueryString();
            if (queryString != null && !queryString.isEmpty()) {
                return configuredUrl + (baseUrl.contains("?") ? "&" : "?") + queryString;
            }
            return configuredUrl;
        } else {
            // 生产环境：forward 会自动保留查询参数，直接返回
            return configuredUrl;
        }
    }

    /**
     * 构建重定向 URL，根据环境配置处理相对路径
     * - 开发环境：如果是相对路径，转换为前端完整 URL
     * - 生产环境：保持相对路径
     */
    private String buildRedirectUrl(String redirect) {
        // 如果已经是完整 URL，直接返回
        if (redirect.startsWith("http://") || redirect.startsWith("https://")) {
            return "redirect:" + redirect;
        }

        // 某些路径必须回到后端（如 /oauth2/authorize 等），否则会出现 OIDC state 丢失
        if (isBackendOnlyPath(redirect)) {
            return "redirect:" + redirect;
        }

        // 获取登录页面配置，用于判断环境
        String loginUrl = frontendProperties.getLoginUrl();
        if (loginUrl.startsWith("redirect:")) {
            // 开发环境：重定向到 Vite dev server
            String baseUrl = loginUrl.substring("redirect:".length());
            // 提取基础 URL（去掉路径部分）
            String devServerBase = baseUrl.substring(0, baseUrl.indexOf("/", baseUrl.indexOf("://") + 3));
            String redirectUrl = devServerBase + redirect;
            return "redirect:" + redirectUrl;
        } else {
            // 生产环境：使用相对路径
            return "redirect:" + redirect;
        }
    }

    private boolean isBackendOnlyPath(String redirect) {
        if (!redirect.startsWith("/")) {
            return false;
        }
        return redirect.startsWith("/oauth2/")
                || redirect.startsWith("/login")
                || redirect.startsWith("/logout")
                || redirect.startsWith("/error")
                || redirect.startsWith("/actuator")
                || redirect.startsWith("/self/security/status");
    }

    /**
     * 记录用户登录信息（IP地址、登录时间、设备信息）
     * 登录成功时重置失败登录次数
     */
    private void recordLoginInfo(User user, HttpServletRequest request) {
        try {
            String clientIp = IpUtils.getClientIp(request);
            String deviceInfo = DeviceUtils.getDeviceInfo(request);
            
            user.setLastLoginIp(clientIp);
            user.setLastLoginAt(java.time.LocalDateTime.now());
            user.setLastLoginDevice(deviceInfo);
            // 登录成功，重置失败登录次数
            user.setFailedLoginCount(0);
            
            userRepository.save(user);
        } catch (Exception e) {
            // 记录登录信息失败不应该影响登录流程，只记录日志
            org.slf4j.LoggerFactory.getLogger(SecurityController.class)
                .warn("记录用户 {} 登录信息失败: {}", user.getUsername(), e.getMessage());
        }
    }
}
