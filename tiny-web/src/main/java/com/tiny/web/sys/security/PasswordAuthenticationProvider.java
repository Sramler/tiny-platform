package com.tiny.web.sys.security;

import com.tiny.web.sys.model.User;
import com.tiny.web.sys.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.Optional;

/**
 * 密码认证提供者（从 {@code user_auth_credential} + {@code user_auth_scope_policy} 读取密码材料）。
 */
public class PasswordAuthenticationProvider implements AuthenticationProvider {

    private static final Logger logger = LoggerFactory.getLogger(PasswordAuthenticationProvider.class);

    private final UserRepository userRepository;
    private final UserAuthPasswordLookupService userAuthPasswordLookupService;
    private final PasswordEncoder passwordEncoder;
    private final UserDetailsService userDetailsService;

    public PasswordAuthenticationProvider(UserRepository userRepository,
                                         UserAuthPasswordLookupService userAuthPasswordLookupService,
                                         PasswordEncoder passwordEncoder,
                                         UserDetailsService userDetailsService) {
        this.userRepository = userRepository;
        this.userAuthPasswordLookupService = userAuthPasswordLookupService;
        this.passwordEncoder = passwordEncoder;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();

        // 查找用户
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("用户不存在: " + username));

        Optional<Map<String, Object>> configOpt =
                userAuthPasswordLookupService.findLocalPasswordConfiguration(user.getId());
        if (configOpt.isEmpty()) {
            logger.warn("用户 {} 未配置 LOCAL + PASSWORD 认证方法", username);
            throw new BadCredentialsException("该用户未配置密码认证方式");
        }

        Map<String, Object> config = configOpt.get();
        if (config.isEmpty()) {
            logger.error("用户 {} 的密码认证配置为空", username);
            throw new BadCredentialsException("密码认证配置错误：配置为空");
        }
        
        // 添加详细的配置日志
        logger.debug("用户 {} 的认证配置键: {}", username, config.keySet());
        logger.debug("用户 {} 的认证配置内容: {}", username, config);

        // 获取编码后的密码
        // 在 authentication_configuration 上下文中，使用 "password" 键名
        // 编码后的密码可能包含编码器前缀（如 {bcrypt}...）或者只是纯哈希（如 $2a$10$...）
        if (!config.containsKey("password")) {
            logger.error("用户 {} 的认证配置中不包含 password 字段。可用的键: {}, 配置内容: {}", 
                    username, config.keySet(), config);
            throw new BadCredentialsException("密码认证配置错误：缺少 password 字段");
        }
        
        Object passwordValue = config.get("password");
        if (passwordValue == null) {
            logger.error("用户 {} 的 password 字段值为 null。配置内容: {}", username, config);
            throw new BadCredentialsException("密码认证配置错误：password 字段值为 null");
        }
        
        String encodedPassword;
        if (passwordValue instanceof String) {
            encodedPassword = (String) passwordValue;
        } else {
            logger.warn("用户 {} 的 password 字段不是字符串类型: {}，尝试转换为字符串", 
                    username, passwordValue.getClass().getName());
            encodedPassword = passwordValue.toString();
        }

        if (encodedPassword.isEmpty()) {
            logger.error("用户 {} 的 password 字段值为空字符串。配置内容: {}", username, config);
            throw new BadCredentialsException("密码认证配置错误：password 字段值为空");
        }
        
        logger.debug("用户 {} 从 password 键读取密码", username);
        
        logger.debug("用户 {} 成功读取密码，密码长度: {}, 密码前缀: {}", 
                username, 
                encodedPassword.length(),
                encodedPassword.length() > 10 ? encodedPassword.substring(0, Math.min(10, encodedPassword.length())) : encodedPassword);
        
        // 验证密码（数据库中的密码应该已经包含前缀，如 {bcrypt}...）
        logger.debug("用户 {} 密码验证开始", username);
        boolean passwordMatches = passwordEncoder.matches(password, encodedPassword);
        logger.debug("用户 {} 密码验证结果: {}", username, passwordMatches ? "成功" : "失败");
        
        if (!passwordMatches) {
            logger.warn("用户 {} 密码验证失败", username);
            throw new BadCredentialsException("密码错误");
        }

        // 认证成功，加载用户详情（UserDetailsService 会从同一新模型补齐 User.password）
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        logger.info("用户 {} 密码认证成功", username);
        // 清空密码，避免在 Authentication 对象中保留敏感信息
        return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
    }


    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}

