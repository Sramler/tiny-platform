package com.tiny.web.sys.security;

import com.tiny.web.sys.model.User;
import com.tiny.web.sys.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserDetailsServiceImpl.class);
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private UserAuthPasswordLookupService userAuthPasswordLookupService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        Optional<Map<String, Object>> configOpt =
                userAuthPasswordLookupService.findLocalPasswordConfiguration(user.getId());
        if (configOpt.isPresent()) {
            Map<String, Object> config = configOpt.get();
            if (config.containsKey("password")) {
                Object passwordValue = config.get("password");
                if (passwordValue instanceof String encodedPassword && !encodedPassword.isEmpty()) {
                    user.setPassword(encodedPassword);
                    logger.debug("从新模型凭证表加载编码密码到 User 对象，用户: {}", username);
                } else {
                    logger.warn("用户 {} 的 password 字段不是非空字符串", username);
                }
            } else {
                logger.warn("用户 {} 的认证配置中不包含 password 字段。可用的键: {}", username, config.keySet());
            }
        } else if (user.getPassword() == null || user.getPassword().isEmpty()) {
            logger.warn("用户 {} 既没有新模型密码凭证，也没有 user.password 字段", username);
        }
        
        // Update last login time
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        return user;
    }
}