package com.tiny.platform.core.oauth.security;

import com.tiny.platform.core.oauth.config.CustomWebAuthenticationDetailsSource;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.auth.user.support.UserAuthenticationMethodProfile;
import com.tiny.platform.infrastructure.auth.user.service.UserAuthenticationBridgeWriter;
import com.tiny.platform.infrastructure.auth.user.service.UserAuthenticationMethodProfileService;
import com.tiny.platform.core.oauth.service.SecurityService;
import com.tiny.platform.core.oauth.tenant.ActiveTenantResponseSupport;
import com.tiny.platform.core.oauth.tenant.TenantContext;
import com.tiny.platform.core.oauth.tenant.TenantContextContract;
import com.tiny.platform.infrastructure.tenant.repository.TenantRepository;
import com.tiny.platform.infrastructure.core.util.IpUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * MultiAuthenticationProvider - 支持多认证方式与 MFA 分步/一次性校验。
 *
 * 设计原则：
 *  - 最少 DB 查询（复用 enabledMethods）
 *  - 不在日志中输出敏感信息（密码/secret/验证码）
 *  - 为 MFA 提供清晰的分支：一次性验证 / 分步返回带 factor authority 的 partial token / 完成所有因子返回完全认证 token
 *  - partial MFA token 保持 authenticated=false，后续是否允许继续 challenge 由 factor authority 决定
 *  - TOTP 因子统一经过 TotpVerificationGuard，防止在线爆破
 */
@Component
public class MultiAuthenticationProvider implements AuthenticationProvider {

    private static final Logger logger = LoggerFactory.getLogger(MultiAuthenticationProvider.class);

    // 常量：认证因子/类型
    private static final String FACTOR_PASSWORD = "PASSWORD";
    private static final String FACTOR_TOTP = "TOTP";
    private static final String PROVIDER_LOCAL = "LOCAL";

    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final AuthUserResolutionService authUserResolutionService;
    private final UserAuthenticationMethodProfileService authenticationMethodProfileService;
    private final UserAuthenticationBridgeWriter authenticationBridgeWriter;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsService userDetailsService;
    private final SecurityService securityService;
    private final TotpVerificationGuard totpVerificationGuard;
    private final LoginFailurePolicy loginFailurePolicy;

    @Autowired
    public MultiAuthenticationProvider(UserRepository userRepository,
                                       TenantRepository tenantRepository,
                                       UserAuthenticationMethodProfileService authenticationMethodProfileService,
                                       UserAuthenticationBridgeWriter authenticationBridgeWriter,
                                       PasswordEncoder passwordEncoder,
                                       UserDetailsService userDetailsService,
                                       SecurityService securityService,
                                       AuthUserResolutionService authUserResolutionService,
                                       TotpVerificationGuard totpVerificationGuard,
                                       LoginFailurePolicy loginFailurePolicy) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.authUserResolutionService = authUserResolutionService;
        this.authenticationMethodProfileService = authenticationMethodProfileService;
        this.authenticationBridgeWriter = authenticationBridgeWriter;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
        this.securityService = securityService;
        this.totpVerificationGuard = totpVerificationGuard;
        this.loginFailurePolicy = loginFailurePolicy;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username;
        Object credentials;
        String provider = null;
        String type = null;

        // 支持自定义 MultiFactorAuthenticationToken 或 标准 UsernamePasswordAuthenticationToken
        if (authentication instanceof MultiFactorAuthenticationToken mfaToken) {
            username = mfaToken.getUsername();
            credentials = mfaToken.getCredentials();
            provider = mfaToken.getAuthenticationProvider();
            type = mfaToken.getAuthenticationType();
            logger.debug("使用 MultiFactorAuthenticationToken 进行认证 (user={}, provider={}, type={})",
                    username, mask(provider), mask(type));
        } else if (authentication instanceof UsernamePasswordAuthenticationToken upToken) {
            username = upToken.getName();
            credentials = upToken.getCredentials();
            if (upToken.getDetails() instanceof CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails details) {
            provider = details.getAuthenticationProvider();
            type = details.getAuthenticationType();
            }
            logger.debug("使用 UsernamePasswordAuthenticationToken 进行认证 (user={}, provider={}, type={})",
                    username, mask(provider), mask(type));
        } else {
            logger.error("不支持的 Authentication 类型: {}", authentication.getClass().getName());
            throw new BadCredentialsException("不支持的认证类型");
        }

        // 基本校验
        if (username == null || username.isBlank()) {
            throw new BadCredentialsException("用户名不能为空");
        }

        Long activeTenantId = resolveActiveTenantId();
        String activeScopeType = resolveActiveScopeType();
        if (logger.isDebugEnabled()) {
            logger.debug(
                    "[auth-login] 解析登录上下文: username='{}', activeTenantId={}, activeScopeType={}",
                    username,
                    activeTenantId,
                    activeScopeType
            );
        }
        boolean platformScope = TenantContextContract.SCOPE_TYPE_PLATFORM.equalsIgnoreCase(activeScopeType);
        Optional<String> blockedLifecycleStatus = Optional.empty();
        if (!platformScope && activeTenantId != null && tenantRepository != null) {
            blockedLifecycleStatus = tenantRepository.findLoginBlockedLifecycleStatus(activeTenantId);
            if (blockedLifecycleStatus == null) {
                blockedLifecycleStatus = Optional.empty();
            }
        }
        if (blockedLifecycleStatus.isPresent()) {
            String lifecycleStatus = blockedLifecycleStatus.get();
            logger.warn(
                    "[auth-login] 活动租户不可登录，将拒绝登录 username='{}', activeTenantId={}, lifecycleStatus={}",
                    username,
                    activeTenantId,
                    lifecycleStatus
            );
            if ("DECOMMISSIONED".equalsIgnoreCase(lifecycleStatus)) {
                throw new BadCredentialsException("租户已下线");
            }
            throw new BadCredentialsException("租户已冻结");
        }

        // 先查用户（单次）
        Optional<User> resolvedUser = resolveUserByScope(username, activeTenantId, activeScopeType);
        if (resolvedUser.isEmpty()) {
            if (activeTenantId == null && !TenantContextContract.SCOPE_TYPE_PLATFORM.equalsIgnoreCase(activeScopeType)) {
                logger.warn("[auth-login] 缺少登录租户信息，将返回 BadCredentialsException(username='{}')", username);
                throw new BadCredentialsException("缺少租户信息");
            }
            logger.warn(
                    "[auth-login] 在当前作用域下未找到用户记录，将返回 BadCredentialsException(username='{}', activeTenantId={}, activeScopeType={})",
                    username,
                    activeTenantId,
                    activeScopeType
            );
            throw new BadCredentialsException("用户不存在");
        }
        User user = resolvedUser.get();
        if (activeTenantId == null && !platformScope) {
            platformScope = true;
            activeScopeType = TenantContextContract.SCOPE_TYPE_PLATFORM;
            TenantContext.setActiveScopeType(TenantContextContract.SCOPE_TYPE_PLATFORM);
            TenantContext.setActiveTenantId(null);
        }
        LocalDateTime now = LocalDateTime.now();
        if (loginFailurePolicy.isManuallyLocked(user)) {
            throw new LockedException(loginFailurePolicy.buildManualLockMessage());
        }
        if (loginFailurePolicy.isTemporarilyLocked(user, now)) {
            throw new LockedException(loginFailurePolicy.buildTemporaryLockMessage(user, now));
        }
        if (loginFailurePolicy.shouldResetFailureWindow(user, now)) {
            loginFailurePolicy.clearExpiredFailureWindow(user);
            userRepository.save(user);
        }
        Supplier<UserDetails> userDetailsSupplier = memoizedUserDetailsLoader(user.getUsername());

        // 读取所有已启用的方法（只查询一次）
        List<UserAuthenticationMethodProfile> enabledMethodProfiles = authenticationMethodProfileService.loadEnabledMethodProfiles(
                user.getId(),
                activeScopeType,
                activeTenantId
        );
        if (enabledMethodProfiles == null) {
            enabledMethodProfiles = Collections.emptyList();
        }

        // 智能回退：如果 provider/type 未指定并且只有一种启用方法，则自动选择
        String finalProvider = provider;
        String finalType = type;
        if ((finalProvider == null || finalProvider.isBlank() || finalType == null || finalType.isBlank())) {
            if (enabledMethodProfiles.isEmpty()) {
                logger.error("用户 {} 未配置任何启用的认证方法", username);
                throw new BadCredentialsException("该用户未配置任何认证方式");
            }
            
            if (enabledMethodProfiles.size() == 1) {
                UserAuthenticationMethodProfile only = enabledMethodProfiles.get(0);
                finalProvider = only.authenticationProvider();
                finalType = only.authenticationType();
                logger.info("用户 {} 未指定认证方式，自动选择唯一启用方法 {}+{}", username, mask(finalProvider), mask(finalType));
            } else {
                // 多个方法且用户未指定，必须明确
                String allowed = String.join(", ",
                        enabledMethodProfiles.stream()
                                .map(m -> m.authenticationProvider() + "+" + m.authenticationType())
                                .toList());
                throw new BadCredentialsException("用户配置了多种认证方式，请指定认证方式。可选：" + allowed);
            }
        }

        finalProvider = finalProvider.trim().toUpperCase(Locale.ROOT);
        finalType = finalType.trim().toUpperCase(Locale.ROOT);

        // 限长/格式校验
        if (!isValidParam(finalProvider) || !isValidParam(finalType)) {
            throw new BadCredentialsException("认证参数格式错误");
        }
        
        // 找到请求的具体认证方法配置
        final String finalProviderForLambda = finalProvider;
        final String finalTypeForLambda = finalType;
        Optional<UserAuthenticationMethodProfile> methodOpt = enabledMethodProfiles.stream()
                .filter(m -> finalProviderForLambda.equalsIgnoreCase(m.authenticationProvider()))
                .filter(m -> finalTypeForLambda.equalsIgnoreCase(m.authenticationType()))
                .findFirst();

        if (methodOpt.isEmpty()) {
            logger.warn("用户 {} 未配置 {}+{} 方法", username, mask(finalProvider), mask(finalType));
            throw new BadCredentialsException("该用户未配置此认证方式");
        }

        UserAuthenticationMethodProfile method = methodOpt.get();

        // 计算是否需要 MFA 流程（只考虑 LOCAL + PASSWORD/TOTP 的常见组合）
        List<UserAuthenticationMethodProfile> mfaCandidates = enabledMethodProfiles.stream()
                .filter(m -> PROVIDER_LOCAL.equalsIgnoreCase(m.authenticationProvider()))
                .filter(m -> FACTOR_PASSWORD.equalsIgnoreCase(m.authenticationType()) || FACTOR_TOTP.equalsIgnoreCase(m.authenticationType()))
                .sorted((a, b) -> {
                    // password 优先
                    if (FACTOR_PASSWORD.equalsIgnoreCase(a.authenticationType()) && FACTOR_TOTP.equalsIgnoreCase(b.authenticationType())) return -1;
                    if (FACTOR_TOTP.equalsIgnoreCase(a.authenticationType()) && FACTOR_PASSWORD.equalsIgnoreCase(b.authenticationType())) return 1;
                    return 0;
                })
                .toList();

        if (mfaCandidates.size() > 1) {
            // ===== 思路 A + 企业级 MFA 策略集成点 =====
            // 通过 SecurityService + MfaProperties 计算当前会话是否“必须”走 TOTP。
            // 如果本次会话不要求 TOTP，则即使用户配置了 PASSWORD+TOTP 组合，也按单因子流程处理，
            // 直接在这里完成最终认证并交给后续 OAuth2/OIDC 流程签发 Token。
            boolean requireTotpThisSession = false;
            try {
                Map<String, Object> securityStatus = securityService.getSecurityStatus(user);
                Object requireTotpFlag = securityStatus.get("requireTotp");
                requireTotpThisSession = Boolean.TRUE.equals(requireTotpFlag);
                logger.debug("[MFA] MultiAuthenticationProvider - userId={}, username={}, requireTotpThisSession={}, enabledMethods={}",
                        user.getId(), user.getUsername(), requireTotpThisSession, enabledMethodProfiles.size());
            } catch (Exception ex) {
                logger.warn("计算用户 {} 的 MFA 策略时发生异常，降级为仅 PASSWORD 流程: {}", username, ex.getMessage());
            }

            if (!requireTotpThisSession) {
                // 当前会话允许跳过 TOTP：按单因子方式直接认证（通常就是 PASSWORD），
                // 保证符合思路 A：本次 requiredFactors = {PASSWORD}，完成后即可发最终 Token。
                if (FACTOR_TOTP.equalsIgnoreCase(finalType)
                        && mfaCandidates.stream().anyMatch(m -> FACTOR_PASSWORD.equalsIgnoreCase(m.authenticationType()))) {
                    logger.warn("用户 {} 在未完成密码验证前尝试直接使用 TOTP 登录，已拒绝", username);
                    throw new BadCredentialsException("必须先完成前置认证步骤");
                }
                logger.info("[MFA] 本次会话不要求 TOTP，按单因子 {} 完成认证 (user={})", finalType, username);
                String cred = credentials != null ? credentials.toString() : null;
                return switch (finalType) {
                    case FACTOR_PASSWORD -> authenticatePassword(user, cred, method, finalProvider, finalType, userDetailsSupplier);
                    case FACTOR_TOTP -> authenticateTotp(user, cred, method, finalProvider, finalType, userDetailsSupplier);
                    default -> throw new BadCredentialsException("不支持的认证类型: " + finalType);
                };
            }

            // 当前会话“必须”完成 TOTP：走原有 MFA 分步/一次性流程。
            logger.info("[MFA] 本次会话要求 TOTP，进入多因子分步流程 (user={})", username);
            return handleMultiFactorAuthentication(user, credentials, mfaCandidates, finalProvider, finalType, userDetailsSupplier);
        } else {
            // 普通单因子认证
            String cred = credentials != null ? credentials.toString() : null;
            return switch (finalType) {
                case FACTOR_PASSWORD -> authenticatePassword(user, cred, method, finalProvider, finalType, userDetailsSupplier);
                case FACTOR_TOTP -> authenticateTotp(user, cred, method, finalProvider, finalType, userDetailsSupplier);
                default -> throw new BadCredentialsException("不支持的认证类型: " + finalType);
            };
        }
    }
    
    /**
     * MFA 处理：支持一次性提交多个凭证（Map）或分步提交（先 password 再 totp）
     *
     * 设计要点：
     *  - 如果用户提交了多个凭证（Map），尝试一次性验证所有因子
     *  - 如果只提交了第一个因子（通常是 password），验证后返回 partial token，并把已完成因子映射到 factor authority，
     *    由 success handler 判断是否仍需 TOTP 验证并跳转到相应页面
     *  - 未完成全部因子时返回 authenticated=false 的 token，避免把“半程 MFA”误当成完整登录
     */
    private Authentication handleMultiFactorAuthentication(User user,
                                                           Object credentials,
                                                           List<UserAuthenticationMethodProfile> mfaMethods,
                                                           String provider,
                                                           String requestedType,
                                                           Supplier<UserDetails> userDetailsSupplier) {
        // 解析传入凭证：支持 Map<String, Object> 或单值
        Map<String, String> credentialMap = new HashMap<>();
        if (credentials instanceof Map<?, ?> rawMap) {
            rawMap.forEach((k, v) -> {
                if (k != null && v != null) credentialMap.put(k.toString().toLowerCase(Locale.ROOT), v.toString());
            });
        } else if (credentials != null) {
            // 单值凭证，按照请求类型放入 map
            credentialMap.put(requestedType.toLowerCase(Locale.ROOT), credentials.toString());
        }

        // 不允许跳过前置因子直接验证后置因子。
        // 典型场景：同时配置 PASSWORD + TOTP 时，单独提交 authenticationType=TOTP 不得直接完成登录。
        validateNoSkippedPrerequisite(user, mfaMethods, credentialMap);
        
        Set<MultiFactorAuthenticationToken.AuthenticationFactorType> completed = new LinkedHashSet<>();
        List<UserAuthenticationMethodProfile> remaining = new ArrayList<>(mfaMethods);
        
        // 顺序验证每个因子
        for (UserAuthenticationMethodProfile method : List.copyOf(mfaMethods)) {
            String methodType = method.authenticationType();
            String methodKeyLower = methodType.toLowerCase(Locale.ROOT);

            // 支持常见的 key 名（password, totp, totpCode 等）
            String provided = credentialMap.get(methodKeyLower);
            if (provided == null && FACTOR_TOTP.equalsIgnoreCase(methodType)) {
                // 尝试 totpcode 这种键名
                provided = credentialMap.get("totpcode");
                if (provided == null) provided = credentialMap.get("totp_code");
            }

            if (provided == null || provided.isBlank()) {
                // 没有该因子的凭证：分步情形或跳过
                logger.debug("用户 {} 未提供 {} 因子的凭证（可能分步验证）", user.getUsername(), methodType);
                // 如果已有完成的因子，则表明这是第二步，返回 partial token 让 successHandler 处理
                if (!completed.isEmpty()) {
                    MultiFactorAuthenticationToken partial = buildPartialToken(
                        user.getUsername(), provider, completed, userDetailsSupplier.get()
                    );
                    logger.info("用户 {} 已完成部分因子，返回 partial MFA token（需后续验证）", user.getUsername());
                    return partial;
                } else {
                    // 尚未完成任何因子，继续尝试下一个（可能用户只提交 TOTP，但没有密码）
                    remaining.remove(method);
                    continue;
                }
            }

            // 验证当前因子
            Authentication step = authenticateFactor(user, provided, method, methodType, userDetailsSupplier);
            if (!step.isAuthenticated()) {
                throw new BadCredentialsException(methodType + " 验证失败");
            }
            
            // 记录认证方法验证成功的信息（在 authenticateFactor 中已记录，这里不需要重复记录）
            
            MultiFactorAuthenticationToken.AuthenticationFactorType factor = MultiFactorAuthenticationToken.AuthenticationFactorType.from(methodType);
            if (factor != MultiFactorAuthenticationToken.AuthenticationFactorType.UNKNOWN) {
                completed.add(factor);
            }
            remaining.remove(method);
            logger.info("用户 {} 成功完成 {} 验证（已完成 {}）", user.getUsername(), methodType, completed.size());
        }

        // 至此，至少完成了一个因子
        if (completed.isEmpty()) {
            throw new BadCredentialsException("未提供任何有效凭证");
        }

        // 如果未完成全部因子（仍有 remaining），需要分步处理
        if (!remaining.isEmpty()) {
            // 如果已经完成了 PASSWORD 并还剩 TOTP，则返回 partial token。
            // successHandler 和后续授权链通过 factor authority + authenticated=false 判断这是待补全的 MFA 会话。
            if (completed.contains(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD) && 
                remaining.stream().anyMatch(m -> FACTOR_TOTP.equalsIgnoreCase(m.authenticationType()))) {
                MultiFactorAuthenticationToken partial = buildPartialToken(
                    user.getUsername(), provider, completed, userDetailsSupplier.get()
                );
                logger.info("用户 {} 已完成密码验证，返回 partial MFA token（仍需 TOTP）", user.getUsername());
                return partial;
            } else {
                // 其他情况也统一返回 partial token，让 successHandler 处理后续跳转。
                MultiFactorAuthenticationToken partial = buildPartialToken(
                    user.getUsername(), provider, completed, userDetailsSupplier.get()
                );
                logger.info("用户 {} 已完成部分因子，返回 partial MFA token（需后续验证）", user.getUsername());
                return partial;
            }
        }

        // 所有因子完成 => 完全认证
        MultiFactorAuthenticationToken finalToken = buildAuthenticatedToken(
            user.getUsername(), provider, completed, userDetailsSupplier.get()
        );
        logger.info("用户 {} 完成所有 MFA 因子，认证成功", user.getUsername());
        return finalToken;
    }

    private Optional<User> resolveUserByScope(String username, Long activeTenantId, String activeScopeType) {
        if (TenantContextContract.SCOPE_TYPE_PLATFORM.equalsIgnoreCase(activeScopeType)) {
            return requireAuthUserResolutionService().resolveUserRecordInPlatform(username);
        }
        return requireAuthUserResolutionService().resolveUserRecordInActiveTenant(username, activeTenantId);
    }

    private Long resolveActiveTenantId() {
        return ActiveTenantResponseSupport.resolveActiveTenantIdFromRequestContext();
    }

    private String resolveActiveScopeType() {
        String scopeType = ActiveTenantResponseSupport.resolveActiveScopeTypeFromRequestContext();
        if (scopeType == null || scopeType.isBlank()) {
            return TenantContextContract.SCOPE_TYPE_TENANT;
        }
        return scopeType.trim().toUpperCase(Locale.ROOT);
    }

    private AuthUserResolutionService requireAuthUserResolutionService() {
        if (authUserResolutionService == null) {
            throw new IllegalStateException("AuthUserResolutionService 未配置");
        }
        return authUserResolutionService;
    }

    private void validateNoSkippedPrerequisite(User user,
                                               List<UserAuthenticationMethodProfile> orderedMethods,
                                               Map<String, String> credentialMap) {
        Set<String> providedFactors = providedFactors(credentialMap);
        if (providedFactors.isEmpty()) {
            return;
        }

        boolean previousFactorMissing = false;
        for (UserAuthenticationMethodProfile method : orderedMethods) {
            String factorType = method.authenticationType();
            if (factorType == null || factorType.isBlank()) {
                continue;
            }
            String normalizedFactor = factorType.trim().toUpperCase(Locale.ROOT);
            boolean provided = providedFactors.contains(normalizedFactor);
            if (!provided) {
                previousFactorMissing = true;
                continue;
            }
            if (previousFactorMissing) {
                logger.warn("用户 {} 尝试跳过前置因子直接验证 {}，已拒绝", user.getUsername(), normalizedFactor);
                throw new BadCredentialsException("必须先完成前置认证步骤");
            }
        }
    }

    private Set<String> providedFactors(Map<String, String> credentialMap) {
        Set<String> providedFactors = new LinkedHashSet<>();
        if (hasCredential(credentialMap, FACTOR_PASSWORD)) {
            providedFactors.add(FACTOR_PASSWORD);
        }
        if (hasCredential(credentialMap, FACTOR_TOTP)) {
            providedFactors.add(FACTOR_TOTP);
        }
        return providedFactors;
    }

    private boolean hasCredential(Map<String, String> credentialMap, String factorType) {
        if (credentialMap == null || credentialMap.isEmpty()) {
            return false;
        }
        if (FACTOR_TOTP.equalsIgnoreCase(factorType)) {
            return hasText(credentialMap.get(FACTOR_TOTP.toLowerCase(Locale.ROOT)))
                    || hasText(credentialMap.get("totpcode"))
                    || hasText(credentialMap.get("totp_code"));
        }
        return hasText(credentialMap.get(factorType.toLowerCase(Locale.ROOT)));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
    
    private Authentication authenticateFactor(User user,
                                              String credential,
                                              UserAuthenticationMethodProfile method,
                                              String factorType,
                                              Supplier<UserDetails> userDetailsSupplier) {
        if (FACTOR_PASSWORD.equalsIgnoreCase(factorType)) {
                return authenticatePassword(user, credential, method, method.authenticationProvider(), factorType, userDetailsSupplier);
        } else if (FACTOR_TOTP.equalsIgnoreCase(factorType)) {
                return authenticateTotp(user, credential, method, method.authenticationProvider(), factorType, userDetailsSupplier);
        } else {
            throw new BadCredentialsException("不支持的认证因子: " + factorType);
        }
    }
    
    /**
     * 密码认证（已包含详细日志与异常处理）
     */
    private Authentication authenticatePassword(User user,
                                                String password,
                                                UserAuthenticationMethodProfile method,
                                                String provider,
                                                String type,
                                                Supplier<UserDetails> userDetailsSupplier) {
        Map<String, Object> config = method.authenticationConfiguration();
        UserAuthenticationMethod storageRecord = method.storageRecord();
        
        if (config == null || !config.containsKey("password")) {
            logger.error("用户 {} 的认证配置缺少 password 字段（methodId={}）", user.getUsername(), storageRecord.getId());
            throw new BadCredentialsException("认证配置错误");
        }

        Object stored = config.get("password");
        if (stored == null) {
            throw new BadCredentialsException("认证配置错误：无密码");
        }

        String encoded = stored.toString();
        if (encoded.isEmpty()) {
            throw new BadCredentialsException("认证配置错误：密码为空");
        }

        // 仅记录长度/前缀（避免记录实际密码）
        logger.debug("用户 {} 的密码长度 {}，开始匹配", user.getUsername(), encoded.length());

        boolean matches = passwordEncoder.matches(password, encoded);
        if (!matches) {
            logger.warn(
                    "用户 {} 密码验证失败（scopeTenantId={}, storedHashLen={}；明文与库内哈希不一致或哈希格式无法被 DelegatingPasswordEncoder 识别，可核对新认证模型数据或执行 scripts/ensure-platform-admin.sh）",
                    user.getUsername(),
                    method.scopeTenantId(),
                    encoded.length());
            throw new BadCredentialsException("密码错误");
        }

        // 记录认证方法验证成功的信息
        recordAuthenticationMethodVerification(storageRecord);

        UserDetails userDetails = userDetailsSupplier.get();

        // 返回 MultiFactorAuthenticationToken 以携带 provider/type 信息（向后兼容）
        MultiFactorAuthenticationToken.AuthenticationFactorType initialFactor = MultiFactorAuthenticationToken.AuthenticationFactorType.from(type);
        MultiFactorAuthenticationToken token = new MultiFactorAuthenticationToken(
                user.getUsername(),
            null,
            MultiFactorAuthenticationToken.AuthenticationProviderType.from(provider),
            initialFactor,
                userDetails.getAuthorities()
        );
        attachSecurityUserDetails(token, userDetails, user.getUsername());
        return token;
    }

    /**
     * TOTP 验证（使用 TotpService）
     */
    private Authentication authenticateTotp(User user,
                                            String totpCode,
                                            UserAuthenticationMethodProfile method,
                                            String provider,
                                            String type,
                                            Supplier<UserDetails> userDetailsSupplier) {
        Map<String, Object> config = method.authenticationConfiguration();
        UserAuthenticationMethod storageRecord = method.storageRecord();

        if (config == null) {
            logger.error("用户 {} 的 TOTP 配置为空 (methodId={})", user.getUsername(), storageRecord.getId());
            throw new BadCredentialsException("TOTP 配置错误");
        }

        // 支持多种 key 名称
        String secret = null;
        if (config.containsKey("secretKey")) secret = Objects.toString(config.get("secretKey"), null);
        if ((secret == null || secret.isBlank()) && config.containsKey("secret")) secret = Objects.toString(config.get("secret"), null);

        if (secret == null || secret.isBlank() || "null".equalsIgnoreCase(secret)) {
            logger.error("用户 {} 未配置有效的 TOTP secret (methodId={})", user.getUsername(), storageRecord.getId());
            throw new BadCredentialsException("TOTP 配置错误");
        }

        // 不在日志中打印 secret 或 code
        totpVerificationGuard.verifyOrThrow(
                user.getUsername(),
                storageRecord,
                secret,
                totpCode,
                "TOTP 验证失败"
        );

        // 记录认证方法验证成功的信息
        recordAuthenticationMethodVerification(storageRecord);

        UserDetails userDetails = userDetailsSupplier.get();

        MultiFactorAuthenticationToken.AuthenticationFactorType initialFactor = MultiFactorAuthenticationToken.AuthenticationFactorType.from(type);
        MultiFactorAuthenticationToken token = new MultiFactorAuthenticationToken(
                user.getUsername(),
            null,
            MultiFactorAuthenticationToken.AuthenticationProviderType.from(provider),
            initialFactor,
                userDetails.getAuthorities()
        );
        attachSecurityUserDetails(token, userDetails, user.getUsername());
        return token;
    }

    private Supplier<UserDetails> memoizedUserDetailsLoader(String username) {
        AtomicReference<UserDetails> cachedUserDetails = new AtomicReference<>();
        return () -> {
            UserDetails existing = cachedUserDetails.get();
            if (existing != null) {
                return existing;
            }
            UserDetails loaded = userDetailsService.loadUserByUsername(username);
            cachedUserDetails.compareAndSet(null, loaded);
            return cachedUserDetails.get();
        };
    }

    private MultiFactorAuthenticationToken buildAuthenticatedToken(String username,
                                                                   String provider,
                                                                   Set<MultiFactorAuthenticationToken.AuthenticationFactorType> resolvedFactors,
                                                                   UserDetails userDetails) {
        MultiFactorAuthenticationToken token = new MultiFactorAuthenticationToken(
            username,
            null,
            MultiFactorAuthenticationToken.AuthenticationProviderType.from(provider),
            resolvedFactors,
            userDetails.getAuthorities()
        );
        token.setAuthenticated(true);
        attachSecurityUserDetails(token, userDetails, username);
        return token;
    }

    private MultiFactorAuthenticationToken buildPartialToken(String username,
                                                             String provider,
                                                             Set<MultiFactorAuthenticationToken.AuthenticationFactorType> resolvedFactors,
                                                             UserDetails userDetails) {
        MultiFactorAuthenticationToken token = MultiFactorAuthenticationToken.partiallyAuthenticated(
                username,
                null,
                MultiFactorAuthenticationToken.AuthenticationProviderType.from(provider),
                resolvedFactors,
                userDetails.getAuthorities()
        );
        attachSecurityUserDetails(token, userDetails, username);
        return token;
    }

    private void attachSecurityUserDetails(MultiFactorAuthenticationToken token, UserDetails userDetails, String username) {
        if (userDetails instanceof com.tiny.platform.core.oauth.model.SecurityUser securityUser) {
            token.setDetails(securityUser);
            logger.debug("用户 {} 的 SecurityUser 已设置到 MultiFactorAuthenticationToken.details (userId: {})",
                username, securityUser.getUserId());
        }
    }


    /**
     * 记录认证方法验证成功的信息（最后验证时间和IP）
     */
    private void recordAuthenticationMethodVerification(UserAuthenticationMethod method) {
        if (method == null) {
            return;
        }
        try {
            // 尝试从 RequestContextHolder 获取当前请求
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String clientIp = IpUtils.getClientIp(request);
                
                method.setLastVerifiedAt(LocalDateTime.now());
                method.setLastVerifiedIp(clientIp);
                persistRuntimeMethod(method);
                
                logger.debug("认证方法 {} (id={}) 验证信息已记录: IP={}, Time={}", 
                        method.getAuthenticationProvider() + "+" + method.getAuthenticationType(),
                        method.getId(), clientIp, method.getLastVerifiedAt());
            } else {
                // 如果无法获取请求（例如非HTTP请求），只记录时间
                method.setLastVerifiedAt(LocalDateTime.now());
                persistRuntimeMethod(method);
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

    private void persistRuntimeMethod(UserAuthenticationMethod method) {
        if (authenticationBridgeWriter != null) {
            authenticationBridgeWriter.upsertRuntime(method);
        }
    }

    /**
     * 简单掩码函数（避免在日志中泄露敏感字符串）
     */
    private static String mask(String s) {
        if (s == null) return null;
        if (s.length() <= 2) return "**";
        return s.substring(0, 1) + "**" + s.substring(s.length() - 1);
    }

    /**
     * 验证参数格式（允许字母数字下划线短横）
     */
    private static boolean isValidParam(String p) {
        return p != null && p.matches("^[A-Za-z0-9_-]{1,50}$");
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return MultiFactorAuthenticationToken.class.isAssignableFrom(authentication) ||
               UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
