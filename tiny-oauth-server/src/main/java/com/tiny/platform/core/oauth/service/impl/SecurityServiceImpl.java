package com.tiny.platform.core.oauth.service.impl;

import com.tiny.platform.core.oauth.config.MfaProperties;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationMethodRepository;
import com.tiny.platform.core.oauth.security.TotpVerificationGuard;
import com.tiny.platform.core.oauth.service.SecurityService;
import com.tiny.platform.infrastructure.core.util.IpUtils;
import com.tiny.platform.infrastructure.core.util.DeviceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * SecurityServiceImpl — 用户安全与TOTP相关业务逻辑实现
 */
@Service
public class SecurityServiceImpl implements SecurityService {
    private static final Logger logger = LoggerFactory.getLogger(SecurityServiceImpl.class);
    private static final String REMIND_PROVIDER = "LOCAL";
    private static final String REMIND_TYPE = "MFA_REMIND";
    private static final String REMIND_CONFIG_SKIP = "skipMfaRemind";
    private static final String REMIND_CONFIG_SKIP_UNTIL = "skipUntil";
    private static final String REMIND_CONFIG_DEVICE = "deviceFingerprint";
    private static final int REMIND_SKIP_DAYS = 30;
    
    private final UserAuthenticationMethodRepository authenticationMethodRepository;
    private final PasswordEncoder passwordEncoder;
    private final MfaProperties mfaProperties;
    private final TotpVerificationGuard totpVerificationGuard;

    @Autowired
    public SecurityServiceImpl(UserAuthenticationMethodRepository authenticationMethodRepository,
                               PasswordEncoder passwordEncoder,
                               MfaProperties mfaProperties,
                               TotpVerificationGuard totpVerificationGuard) {
        this.authenticationMethodRepository = authenticationMethodRepository;
        this.passwordEncoder = passwordEncoder;
        this.mfaProperties = mfaProperties;
        this.totpVerificationGuard = totpVerificationGuard;
    }

    @Override
    public Map<String, Object> getSecurityStatus(User user) {
        boolean totpBound = authenticationMethodRepository.existsByUserIdAndTenantIdAndAuthenticationProviderAndAuthenticationType(
                user.getId(), user.getTenantId(), "LOCAL", "TOTP");
        boolean totpActivated = false;
        String otpauthUri = null;
        if (totpBound) {
            Optional<UserAuthenticationMethod> totp = authenticationMethodRepository
                    .findByUserIdAndTenantIdAndAuthenticationProviderAndAuthenticationType(user.getId(), user.getTenantId(), "LOCAL", "TOTP");
            totpActivated = totp.isPresent() && Boolean.TRUE.equals(
                    getMapBool(totp.get().getAuthenticationConfiguration(), "activated"));
            otpauthUri = totp.map(m -> (String) m.getAuthenticationConfiguration().get("otpauthUri")).orElse(null);
        }
        boolean skipMfaRemind = resolveSkipMfaRemind(user);

        // 基于全局配置 + 用户绑定状态计算本次会话“是否要求 TOTP”
        boolean requireTotpThisSession = isTotpRequiredForUser(totpBound, totpActivated);
        if (logger.isDebugEnabled()) {
            logger.debug("[MFA] getSecurityStatus - userId={}, mode={}, totpBound={}, totpActivated={}, requireTotp={}",
                    user.getId(), mfaProperties.getMode(), totpBound, totpActivated, requireTotpThisSession);
        }

        boolean forceMfa = mfaProperties.isRequired();
        boolean disableMfa = mfaProperties.isDisabled();
        String safeOtpauthUri = otpauthUri == null ? "" : otpauthUri;
        return Map.of(
                "totpBound", totpBound,
                "totpActivated", totpActivated,
                "skipMfaRemind", skipMfaRemind,
                "otpauthUri", safeOtpauthUri,
                "forceMfa", forceMfa,                    // true: 页面不能跳过
                "disableMfa", disableMfa,                // true: 完全不弹窗不推荐
                "requireTotp", requireTotpThisSession    // ✅ 思路 A：本次会话是否必须完成 TOTP
        );
    }

    /**
     * 根据全局配置（security.mfa.mode）和用户当前 TOTP 绑定/激活状态，
     * 计算“本次会话是否要求 TOTP 作为必需因子”。
     * <p>
     * 思路 A 的核心就是：先算出本次会话的必需因子集合 requiredFactors，再在所有必需因子完成后才发最终 Token。
     * 这里先聚焦在 PASSWORD / TOTP 两种因子的决策：
     * <ul>
     *   <li>NONE：完全关闭 MFA，本次永远不要求 TOTP</li>
     *   <li>OPTIONAL：推荐绑定；若用户已绑定且激活 TOTP，则本次会话要求完成 TOTP；未绑定用户可在绑定页选择跳过</li>
     *   <li>REQUIRED：全局强制 MFA，只要用户已绑定且已激活，就必须走 TOTP；未绑定时由上层流程引导绑定</li>
     * </ul>
     *
     * @param totpBound     用户是否存在 LOCAL+TOTP 方法
     * @param totpActivated 用户 TOTP 是否已激活
     * @return 本次会话是否必须完成 TOTP
     */
    private boolean isTotpRequiredForUser(boolean totpBound, boolean totpActivated) {
        // 全局关闭：永远不要求 TOTP
        if (mfaProperties.isDisabled()) {
            logger.debug("[MFA] mode=NONE, 全局关闭 MFA，本次不要求 TOTP");
            return false;
        }

        // 未绑定 / 未激活：无 TOTP 可用，当前会话不要求，但上层可以引导用户去绑定
        if (!totpBound || !totpActivated) {
            logger.debug("[MFA] 用户未绑定或未激活 TOTP (bound={}, activated={})，本次不要求 TOTP", totpBound, totpActivated);
            return false;
        }

        // REQUIRED：只要用户有已激活的 TOTP，本次会话必须走 TOTP
        if (mfaProperties.isRequired()) {
            logger.debug("[MFA] mode=REQUIRED，用户已绑定且激活 TOTP，本次必须要求 TOTP");
            return true;
        }

        // OPTIONAL：推荐但可跳过
        // 对已绑定且激活 TOTP 的用户，要求完成 TOTP；
        // 未绑定用户由上层流程进入绑定页，并可通过“跳过”继续登录。
        if (mfaProperties.isRecommended()) {
            logger.debug("[MFA] mode=OPTIONAL，用户已绑定且激活 TOTP，本次要求 TOTP");
            return true;
        }

        // 兜底：未知模式时不强制
        return false;
    }

    /**
     * 从认证配置中获取密码哈希
     * @param config 认证配置 Map
     * @return 密码哈希值，如果不存在则返回 null
     */
    private String getPasswordHashFromConfig(Map<String, Object> config) {
        // 只使用 password 键名
        if (config == null) {
            return null;
        }
        Object passwordValue = config.get("password");
        if (passwordValue == null) {
            return null;
        }
        if (passwordValue instanceof String) {
            return (String) passwordValue;
        }
        return passwordValue.toString();
    }


    @Override
    public Map<String, Object> bindTotp(User user, String plainPassword, String totpCode) {
        // TOTP 绑定安全设计说明：
        // 1. 用户已登录（已通过密码或其他方式验证），身份已验证
        // 2. TOTP 码本身就是一个强验证因子（"你拥有什么"），足以验证用户身份
        // 3. 绑定 TOTP 是为了增强安全性，而不是降低安全性
        // 4. 因此，绑定 TOTP 时不需要再次验证密码，仅需要 TOTP 码即可
        // 
        // 如果将来需要更强的安全措施，可以考虑：
        // - 记录绑定操作的 IP 地址、设备信息等
        // - 发送邮件/短信通知用户
        // - 要求用户在一定时间内（如 24 小时）确认绑定
        
        // 注意：plainPassword 参数保留是为了向后兼容，但在新的设计中，绑定 TOTP 时不需要密码验证
        // 如果传递了密码，我们忽略它（为了兼容性，不报错）
        Optional<UserAuthenticationMethod> totpMethodOpt = authenticationMethodRepository
                .findByUserIdAndTenantIdAndAuthenticationProviderAndAuthenticationType(user.getId(), user.getTenantId(), "LOCAL", "TOTP");
        UserAuthenticationMethod method = totpMethodOpt.orElse(new UserAuthenticationMethod());
        Map<String, Object> totpConfig = method.getAuthenticationConfiguration() == null ? new HashMap<>() : method.getAuthenticationConfiguration();

        // 如果未有secret则生成
        String secret;
        if (totpConfig.get("secretKey") == null) {
            secret = generateTotpSecret();
            totpConfig.put("secretKey", secret);
            totpConfig.put("issuer", "TinyOAuthServer");
            totpConfig.put("digits", 6);
            totpConfig.put("period", 30);
            totpConfig.put("activated", false);
            // 生成 otpauth URI，便于前端生成二维码
            String account = user.getUsername();
            String issuer = "TinyOAuthServer";
            String otpauthUri = String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s&digits=6&period=30",
                    urlEncode(issuer), urlEncode(account), secret, urlEncode(issuer));
            totpConfig.put("otpauthUri", otpauthUri);
        } else {
            secret = String.valueOf(totpConfig.get("secretKey"));
        }
        // 用真实 TOTP 算法校验
        method.setUserId(user.getId());
        method.setTenantId(user.getTenantId());
        method.setAuthenticationProvider("LOCAL");
        method.setAuthenticationType("TOTP");

        try {
            totpVerificationGuard.verifyOrThrow(user.getUsername(), method, secret, totpCode, "验证码错误");
        } catch (org.springframework.security.authentication.BadCredentialsException ex) {
            return Map.of("success", false, "error", ex.getMessage());
        }
        totpConfig.put("activated", true);
        method.setAuthenticationConfiguration(totpConfig);
        method.setIsPrimaryMethod(false);
        method.setIsMethodEnabled(true);
        method.setAuthenticationPriority(1);
        method.setUpdatedAt(LocalDateTime.now());
        if (method.getId() == null) method.setCreatedAt(LocalDateTime.now());
        authenticationMethodRepository.save(method);
        return Map.of("success", true, "message", "TOTP绑定并激活成功", "otpauthUri", totpConfig.get("otpauthUri"));
    }

    @Override
    public Map<String, Object> unbindTotp(User user, String plainPassword, String totpCode) {
        Optional<UserAuthenticationMethod> totpMethodOpt = authenticationMethodRepository
                .findByUserIdAndTenantIdAndAuthenticationProviderAndAuthenticationType(user.getId(), user.getTenantId(), "LOCAL", "TOTP");
        if (totpMethodOpt.isEmpty())
            return Map.of("success", false, "error", "未绑定二次验证");
        
        // 密码验证逻辑：
        // 1. 如果 Controller 传递了密码（plainPassword != null），说明 Controller 已经判断用户是通过 LOCAL + PASSWORD 登录的，需要验证密码
        // 2. 如果 Controller 没有传递密码（plainPassword == null），说明用户是通过其他方式登录的，不需要密码验证
        
        if (plainPassword != null && !plainPassword.isEmpty()) {
            // Controller 层已经判断用户是通过 LOCAL + PASSWORD 登录的，需要验证密码
            Optional<UserAuthenticationMethod> passwordMethodOpt = authenticationMethodRepository
                    .findByUserIdAndTenantIdAndAuthenticationProviderAndAuthenticationType(user.getId(), user.getTenantId(), "LOCAL", "PASSWORD");
            
            if (passwordMethodOpt.isEmpty()) {
                // 理论上不应该发生：Controller 判断是 LOCAL + PASSWORD 登录，但数据库中没有记录
                return Map.of("success", false, "error", "系统错误：未找到密码认证方法");
            }
            
            UserAuthenticationMethod passwordMethod = passwordMethodOpt.get();
            if (!Boolean.TRUE.equals(passwordMethod.getIsMethodEnabled())) {
                return Map.of("success", false, "error", "本地密码认证方法已被禁用");
            }
            
            Map<String, Object> passwordConfig = passwordMethod.getAuthenticationConfiguration();
            if (passwordConfig == null) {
                return Map.of("success", false, "error", "认证配置为空");
            }
            String hash = getPasswordHashFromConfig(passwordConfig);
            if (hash == null || hash.isEmpty()) {
                return Map.of("success", false, "error", "未找到密码哈希");
            }
            // 验证密码（数据库中的密码应该已经包含前缀，如 {bcrypt}...）
            if (!passwordEncoder.matches(plainPassword, hash)) {
                return Map.of("success", false, "error", "密码错误");
            }
        }
        // 如果 plainPassword == null，说明用户通过其他方式登录（如 OAuth2），不需要密码验证
        // 因为用户已经登录，说明身份已验证
        
        // 验证 TOTP 验证码
        Map<String, Object> totpConfig = totpMethodOpt.get().getAuthenticationConfiguration();
        if (totpConfig == null) {
            return Map.of("success", false, "error", "TOTP认证配置为空");
        }
        String secret = String.valueOf(totpConfig.get("secretKey"));
        if (secret == null || secret.isEmpty() || "null".equals(secret)) {
            return Map.of("success", false, "error", "未找到TOTP密钥");
        }
        if (totpCode == null || totpCode.isEmpty()) {
            return Map.of("success", false, "error", "请提供TOTP验证码");
        }
        try {
            totpVerificationGuard.verifyOrThrow(user.getUsername(), totpMethodOpt.get(), secret, totpCode, "验证码错误");
        } catch (org.springframework.security.authentication.BadCredentialsException ex) {
            return Map.of("success", false, "error", ex.getMessage());
        }
        
        // 验证通过，删除 TOTP 认证方法
        authenticationMethodRepository.delete(totpMethodOpt.get());
        return Map.of("success", true, "message", "二步验证已解绑");
    }

    @Override
    public Map<String, Object> checkTotp(User user, String totpCode) {
        Optional<UserAuthenticationMethod> totpMethodOpt = authenticationMethodRepository
                .findByUserIdAndTenantIdAndAuthenticationProviderAndAuthenticationType(user.getId(), user.getTenantId(), "LOCAL", "TOTP");
        if (totpMethodOpt.isEmpty())
            return Map.of("success", false, "error", "未绑定二步验证");
        Map<String, Object> config = totpMethodOpt.get().getAuthenticationConfiguration();
        if (config == null) {
            return Map.of("success", false, "error", "认证配置为空");
        }
        String secret = String.valueOf(config.get("secretKey"));
        if (secret == null || secret.isEmpty() || "null".equals(secret)) {
            return Map.of("success", false, "error", "未找到TOTP密钥");
        }
        UserAuthenticationMethod totpMethod = totpMethodOpt.get();
        try {
            totpVerificationGuard.verifyOrThrow(user.getUsername(), totpMethod, secret, totpCode, "验证码错误");
        } catch (org.springframework.security.authentication.BadCredentialsException ex) {
            return Map.of("success", false, "error", ex.getMessage());
        }
        
        // 记录认证方法验证成功的信息
        recordAuthenticationMethodVerification(totpMethod);
        
        return Map.of("success", true, "message", "验证码校验通过");
    }

    @Override
    public Map<String, Object> skipMfaRemind(User user, boolean skip) {
        Optional<UserAuthenticationMethod> remindMethodOpt = findReminderMethod(user);
        if (!skip) {
            remindMethodOpt.ifPresent(authenticationMethodRepository::delete);
            return Map.of("success", true, "message", "已启用二次验证绑定提醒");
        }

        UserAuthenticationMethod remindMethod = remindMethodOpt.orElseGet(UserAuthenticationMethod::new);
        Map<String, Object> config = remindMethod.getAuthenticationConfiguration() == null
                ? new HashMap<>()
                : new HashMap<>(remindMethod.getAuthenticationConfiguration());

        config.put(REMIND_CONFIG_SKIP, true);
        config.put(REMIND_CONFIG_SKIP_UNTIL, LocalDateTime.now().plusDays(REMIND_SKIP_DAYS).toString());
        config.put(REMIND_CONFIG_DEVICE, buildDeviceFingerprint());

        remindMethod.setUserId(user.getId());
        remindMethod.setTenantId(user.getTenantId());
        remindMethod.setAuthenticationProvider(REMIND_PROVIDER);
        remindMethod.setAuthenticationType(REMIND_TYPE);
        remindMethod.setAuthenticationConfiguration(config);
        remindMethod.setIsPrimaryMethod(false);
        remindMethod.setIsMethodEnabled(false);
        remindMethod.setAuthenticationPriority(99);
        remindMethod.setUpdatedAt(LocalDateTime.now());
        if (remindMethod.getId() == null) {
            remindMethod.setCreatedAt(LocalDateTime.now());
        }
        authenticationMethodRepository.save(remindMethod);
        return Map.of("success", true, "message", "已设置跳过二次验证绑定提醒");
    }

    @Override
    public Map<String, Object> preBindTotp(User user) {
        Optional<UserAuthenticationMethod> methodOpt = authenticationMethodRepository
                .findByUserIdAndTenantIdAndAuthenticationProviderAndAuthenticationType(user.getId(), user.getTenantId(), "LOCAL", "TOTP");
        UserAuthenticationMethod method;
        Map<String, Object> config;
        boolean needCreate = true;
        if (methodOpt.isPresent()) {
            method = methodOpt.get();
            config = method.getAuthenticationConfiguration();
            boolean activated = config != null && Boolean.TRUE.equals(config.get("activated"));
            // 若已绑定并激活则提示不能多次预初始化
            if (activated) {
                return Map.of("success", false, "error", "TOTP 已绑定无需重复绑定");
            }
            // 若未激活，复用旧secret
            if (config != null && config.get("secretKey") != null) needCreate = false;
        } else {
            method = new UserAuthenticationMethod();
            config = new HashMap<>();
        }
        String secret, issuer = "TinyOAuthServer", account = user.getUsername();
        if (needCreate) {
            secret = generateTotpSecret();
            if (config == null) {
                config = new HashMap<>();
            }
            config.put("secretKey", secret);
            config.put("issuer", issuer);
            config.put("digits", 6);
            config.put("period", 30);
            config.put("activated", false);
            String otpauthUri = String.format(
                    "otpauth://totp/%s:%s?secret=%s&issuer=%s&digits=6&period=30",
                    urlEncode(issuer), urlEncode(account), secret, urlEncode(issuer));
            config.put("otpauthUri", otpauthUri);
            // 持久化未激活
            method.setUserId(user.getId());
            method.setTenantId(user.getTenantId());
            method.setAuthenticationProvider("LOCAL");
            method.setAuthenticationType("TOTP");
            method.setAuthenticationConfiguration(config);
            method.setIsPrimaryMethod(false);
            method.setIsMethodEnabled(true);
            method.setAuthenticationPriority(1);
            method.setUpdatedAt(LocalDateTime.now());
            if (method.getId() == null) method.setCreatedAt(LocalDateTime.now());
            authenticationMethodRepository.save(method);
        } else {
            if (config == null || config.get("secretKey") == null) {
                return Map.of("success", false, "error", "TOTP 配置错误：缺少 secretKey");
            }
            secret = String.valueOf(config.get("secretKey"));
        }
        if (config == null || config.get("otpauthUri") == null) {
            return Map.of("success", false, "error", "TOTP 配置错误：缺少 otpauthUri");
        }
        String otpauthUri = String.valueOf(config.get("otpauthUri"));
        return Map.of(
                "success", true,
                "secretKey", secret,
                "otpauthUri", otpauthUri,
                "issuer", issuer,
                "account", account
        );
    }

    // =========== 工具方法 ==========

    private boolean getMapBool(Map<String, Object> m, String k) {
        return m != null && m.containsKey(k) && Boolean.TRUE.equals(m.get(k));
    }

    /**
     * 生成简单TOTP密钥 base32编码
     */
    private String generateTotpSecret() {
        final String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        SecureRandom rnd = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        return sb.toString();
    }

    private String urlEncode(String str) {
        return URLEncoder.encode(str, StandardCharsets.UTF_8);
    }

    private Optional<UserAuthenticationMethod> findReminderMethod(User user) {
        return authenticationMethodRepository.findByUserIdAndTenantIdAndAuthenticationProviderAndAuthenticationType(
                user.getId(), user.getTenantId(), REMIND_PROVIDER, REMIND_TYPE);
    }

    private boolean resolveSkipMfaRemind(User user) {
        Optional<UserAuthenticationMethod> remindMethodOpt = findReminderMethod(user);
        if (remindMethodOpt.isEmpty()) {
            return false;
        }

        Map<String, Object> config = remindMethodOpt.get().getAuthenticationConfiguration();
        if (config == null || !Boolean.TRUE.equals(config.get(REMIND_CONFIG_SKIP))) {
            return false;
        }

        String skipUntilRaw = config.get(REMIND_CONFIG_SKIP_UNTIL) == null
                ? null
                : String.valueOf(config.get(REMIND_CONFIG_SKIP_UNTIL));
        LocalDateTime skipUntil = parseDateTime(skipUntilRaw);
        if (skipUntil == null || skipUntil.isBefore(LocalDateTime.now())) {
            return false;
        }

        String storedFingerprint = config.get(REMIND_CONFIG_DEVICE) == null
                ? null
                : String.valueOf(config.get(REMIND_CONFIG_DEVICE));
        if (storedFingerprint == null || storedFingerprint.isBlank()) {
            return false;
        }

        String currentFingerprint = buildDeviceFingerprint();
        return storedFingerprint.equals(currentFingerprint);
    }

    private LocalDateTime parseDateTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String buildDeviceFingerprint() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }
        HttpServletRequest request = attributes.getRequest();
        String userAgent = Optional.ofNullable(request.getHeader("User-Agent")).orElse("");
        String deviceInfo = DeviceUtils.getDeviceInfo(request);
        String clientIp = Optional.ofNullable(IpUtils.getClientIp(request)).orElse("");
        String raw = userAgent + "|" + deviceInfo + "|" + clientIp;
        return sha256(raw);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            return Integer.toHexString(input.hashCode());
        }
    }

    /**
     * 记录认证方法验证成功的信息（最后验证时间和IP）
     */
    private void recordAuthenticationMethodVerification(UserAuthenticationMethod method) {
        try {
            // 尝试从 RequestContextHolder 获取当前请求
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String clientIp = IpUtils.getClientIp(request);
                
                method.setLastVerifiedAt(LocalDateTime.now());
                method.setLastVerifiedIp(clientIp);
                authenticationMethodRepository.save(method);
                
                logger.debug("认证方法 {} (id={}) 验证信息已记录: IP={}, Time={}", 
                        method.getAuthenticationProvider() + "+" + method.getAuthenticationType(),
                        method.getId(), clientIp, method.getLastVerifiedAt());
            } else {
                // 如果无法获取请求（例如非HTTP请求），只记录时间
                method.setLastVerifiedAt(LocalDateTime.now());
                authenticationMethodRepository.save(method);
                logger.debug("认证方法 {} (id={}) 验证信息已记录: Time={} (无IP信息)", 
                        method.getAuthenticationProvider() + "+" + method.getAuthenticationType(),
                        method.getId(), method.getLastVerifiedAt());
            }
        } catch (Exception e) {
            // 记录验证信息失败不应该影响认证流程，只记录日志
            logger.warn("记录认证方法 {} 验证信息失败: {}", 
                    method.getAuthenticationProvider() + "+" + method.getAuthenticationType(), e.getMessage());
        }
    }
}
