package com.tiny.platform.infrastructure.auth.user.service;

import com.tiny.platform.infrastructure.core.exception.code.ErrorCode;
import com.tiny.platform.infrastructure.core.exception.exception.BusinessException;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.user.dto.UserCreateUpdateDto;
import com.tiny.platform.infrastructure.auth.user.dto.UserRequestDto;
import com.tiny.platform.infrastructure.auth.user.dto.UserResponseDto;
import com.tiny.platform.infrastructure.auth.user.repository.UserRepository;
import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.role.repository.RoleRepository;
import com.tiny.platform.infrastructure.auth.user.repository.UserAuthenticationMethodRepository;
import com.tiny.platform.infrastructure.auth.user.domain.UserAuthenticationMethod;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.data.jpa.domain.Specification;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.HashSet;
import java.util.HashMap;
import java.time.LocalDateTime;
import com.tiny.platform.core.oauth.tenant.TenantContext;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final UserAuthenticationMethodRepository authenticationMethodRepository;

    @Autowired
    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, 
                          RoleRepository roleRepository, UserAuthenticationMethodRepository authenticationMethodRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
        this.authenticationMethodRepository = authenticationMethodRepository;
    }


    @Override
    public Page<UserResponseDto> users(UserRequestDto query, Pageable pageable) {
        Long tenantId = requireTenantId();
        Page<User> users = userRepository.findAll((Specification<User>) (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));

            if (StringUtils.hasText(query.getUsername())) {
                predicates.add(cb.like(root.get("username"), "%" + query.getUsername() + "%"));
            }
            if (StringUtils.hasText(query.getNickname())) {
                predicates.add(cb.like(root.get("nickname"), "%" + query.getNickname() + "%")); // ⚠️ 字段名错误？可能你原本写了 phone？
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);

        // ⚠️ 用 Page.map() 转换为 Page<UserResponseDto>
        return users.map(this::toDto);
    }

    private UserResponseDto toDto(User user) {
        return new UserResponseDto(user.getId(),user.getUsername(),user.getNickname(),user.isEnabled(),
                user.isAccountNonExpired(),user.isAccountNonLocked(),user.isCredentialsNonExpired(),user.getLastLoginAt(),
                user.getFailedLoginCount() != null ? user.getFailedLoginCount() : 0, user.getLastFailedLoginAt());
    }

    @Override
    public Optional<User> findById(Long id) {
        return userRepository.findByIdAndTenantId(id, requireTenantId());
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findUserByUsernameAndTenantId(username, requireTenantId());
    }

    @Override
    public User create(User user) {
        user.setTenantId(requireTenantId());
        return userRepository.save(user);
    }

    @Override
    public User update(Long id, User user) {
        return userRepository.findByIdAndTenantId(id, requireTenantId())
            .map(existing -> {
                existing.setUsername(user.getUsername());
                // 不再更新 user.password，密码已迁移到 user_authentication_method 表
                existing.setNickname(user.getNickname());
                existing.setEnabled(user.isEnabled());
                existing.setLastLoginAt(user.getLastLoginAt());
                return userRepository.save(existing);
            })
            .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Override
    public void delete(Long id) {
        userRepository.findByIdAndTenantId(id, requireTenantId())
            .ifPresent(userRepository::delete);
    }

    @Override
    @Transactional
    public User createFromDto(UserCreateUpdateDto userDto) {
        Long tenantId = requireTenantId();
        // 检查用户名是否已存在
        if (userRepository.findUserByUsernameAndTenantId(userDto.getUsername(), tenantId).isPresent()) {
            throw new BusinessException(
                ErrorCode.RESOURCE_ALREADY_EXISTS,
                "用户名已存在: " + userDto.getUsername()
            );
        }
        
        // 创建新用户（不设置密码）
        User user = new User();
        user.setTenantId(tenantId);
        user.setUsername(userDto.getUsername());
        user.setNickname(userDto.getNickname());
        user.setEnabled(userDto.getEnabled());
        user.setAccountNonExpired(userDto.getAccountNonExpired());
        user.setAccountNonLocked(userDto.getAccountNonLocked());
        user.setCredentialsNonExpired(userDto.getCredentialsNonExpired());
        
        // 处理角色
        if (userDto.getRoleIds() != null && !userDto.getRoleIds().isEmpty()) {
            var roles = roleRepository.findByIdInAndTenantId(userDto.getRoleIds(), tenantId);
            if (roles.size() != userDto.getRoleIds().size()) {
                throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "部分角色不存在"
                );
            }
        }
        
        // 保存用户，获取用户ID
        user = userRepository.save(user);

        if (userDto.getRoleIds() != null && !userDto.getRoleIds().isEmpty()) {
            for (Long roleId : userDto.getRoleIds()) {
                userRepository.addUserRoleRelation(tenantId, user.getId(), roleId);
            }
        }
        
        // 创建认证方法（将密码存储在 user_authentication_method 表中）
        createPasswordAuthenticationMethod(user.getId(), tenantId, userDto.getPassword());
        
        return user;
    }

    @Override
    @Transactional
    public User updateFromDto(UserCreateUpdateDto userDto) {
        Long tenantId = requireTenantId();
        User existingUser = userRepository.findByIdAndTenantId(userDto.getId(), tenantId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.NOT_FOUND,
                "用户不存在: " + userDto.getId()
            ));
        
        // 检查用户名是否已被其他用户使用
        Optional<User> userWithSameUsername = userRepository.findUserByUsernameAndTenantId(userDto.getUsername(), tenantId);
        if (userWithSameUsername.isPresent() && !userWithSameUsername.get().getId().equals(userDto.getId())) {
            throw new BusinessException(
                ErrorCode.RESOURCE_ALREADY_EXISTS,
                "用户名已被其他用户使用: " + userDto.getUsername()
            );
        }
        
        // 更新基本信息
        existingUser.setUsername(userDto.getUsername());
        existingUser.setNickname(userDto.getNickname());
        // 更新邮箱（允许设置为null来清空）
        existingUser.setEmail(userDto.getEmail() != null && !userDto.getEmail().trim().isEmpty() 
            ? userDto.getEmail().trim() 
            : null);
        // 更新手机号（允许设置为null来清空）
        existingUser.setPhone(userDto.getPhone() != null && !userDto.getPhone().trim().isEmpty() 
            ? userDto.getPhone().trim() 
            : null);
        existingUser.setEnabled(userDto.getEnabled());
        existingUser.setAccountNonExpired(userDto.getAccountNonExpired());
        boolean unlockingUser = !existingUser.isAccountNonLocked() && Boolean.TRUE.equals(userDto.getAccountNonLocked());
        existingUser.setAccountNonLocked(userDto.getAccountNonLocked());
        existingUser.setCredentialsNonExpired(userDto.getCredentialsNonExpired());
        if (unlockingUser) {
            existingUser.setFailedLoginCount(0);
            existingUser.setLastFailedLoginAt(null);
        }
        
        // 如果提供了新密码，则更新认证方法表中的密码
        if (userDto.needUpdatePassword()) {
            updatePasswordAuthenticationMethod(userDto.getId(), tenantId, userDto.getPassword());
        }
        
        // 处理角色
        if (userDto.getRoleIds() != null) {
            var roles = roleRepository.findByIdInAndTenantId(userDto.getRoleIds(), tenantId);
            if (roles.size() != userDto.getRoleIds().size()) {
                throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "部分角色不存在"
                );
            }
            userRepository.deleteUserRoleRelationsByUserId(existingUser.getId(), tenantId);
            for (Long roleId : userDto.getRoleIds()) {
                userRepository.addUserRoleRelation(tenantId, existingUser.getId(), roleId);
            }
        }
        
        return userRepository.save(existingUser);
    }
    
    /**
     * 创建密码认证方法
     * @param userId 用户ID
     * @param plainPassword 明文密码
     */
    private void createPasswordAuthenticationMethod(Long userId, Long tenantId, String plainPassword) {
        // 加密密码（DelegatingPasswordEncoder 会自动添加 {bcrypt} 前缀）
        String encodedPassword = passwordEncoder.encode(plainPassword);
        
        // 创建认证配置
        Map<String, Object> config = new HashMap<>();
        config.put("password", encodedPassword);
        
        // 创建认证方法
        UserAuthenticationMethod method = new UserAuthenticationMethod();
        method.setUserId(userId);
        method.setTenantId(tenantId);
        method.setAuthenticationProvider("LOCAL");
        method.setAuthenticationType("PASSWORD");
        method.setAuthenticationConfiguration(config);
        method.setIsPrimaryMethod(true);
        method.setIsMethodEnabled(true);
        method.setAuthenticationPriority(0);
        method.setCreatedAt(LocalDateTime.now());
        method.setUpdatedAt(LocalDateTime.now());
        
        // 保存认证方法
        authenticationMethodRepository.save(method);
    }
    
    /**
     * 更新密码认证方法
     * @param userId 用户ID
     * @param plainPassword 新明文密码
     */
    private void updatePasswordAuthenticationMethod(Long userId, Long tenantId, String plainPassword) {
        // 查找现有的认证方法
        Optional<UserAuthenticationMethod> existingMethod = authenticationMethodRepository
                .findByUserIdAndTenantIdAndAuthenticationProviderAndAuthenticationType(userId, tenantId, "LOCAL", "PASSWORD");
        
        if (existingMethod.isPresent()) {
            // 更新现有认证方法
            UserAuthenticationMethod method = existingMethod.get();
            Map<String, Object> config = method.getAuthenticationConfiguration();
            if (config == null) {
                config = new HashMap<>();
            }
            // 加密新密码（DelegatingPasswordEncoder 会自动添加 {bcrypt} 前缀）
            String encodedPassword = passwordEncoder.encode(plainPassword);
            config.put("password", encodedPassword);
            method.setAuthenticationConfiguration(config);
            method.setUpdatedAt(LocalDateTime.now());
            authenticationMethodRepository.save(method);
        } else {
            // 如果不存在，则创建新的认证方法
            createPasswordAuthenticationMethod(userId, tenantId, plainPassword);
        }
    }

    @Override
    @Transactional
    public void batchEnable(List<Long> ids) {
        // 批量启用用户，使用事务确保一致性
        Long tenantId = requireTenantId();
        List<User> users = userRepository.findAllById(ids).stream()
                .filter(u -> u.getTenantId().equals(tenantId))
                .toList();
        if (users.size() != ids.size()) {
                throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "部分用户不存在"
                );
        }
        
        for (User user : users) {
            user.setEnabled(true);
        }
        userRepository.saveAll(users);
    }
    
    @Override
    @Transactional
    public void batchDisable(List<Long> ids) {
        // 批量禁用用户，使用事务确保一致性
        Long tenantId = requireTenantId();
        List<User> users = userRepository.findAllById(ids).stream()
                .filter(u -> u.getTenantId().equals(tenantId))
                .toList();
        if (users.size() != ids.size()) {
                throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "部分用户不存在"
                );
        }
        
        for (User user : users) {
            user.setEnabled(false);
        }
        userRepository.saveAll(users);
    }
    
    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        // 批量删除用户，使用事务确保一致性
        Long tenantId = requireTenantId();
        List<User> users = userRepository.findAllById(ids).stream()
                .filter(u -> u.getTenantId().equals(tenantId))
                .toList();
        if (users.size() != ids.size()) {
                throw new BusinessException(
                    ErrorCode.NOT_FOUND,
                    "部分用户不存在"
                );
        }
        
        userRepository.deleteAll(users);
    }

    @Override
    @Transactional
    public void updateUserRoles(Long userId, List<Long> roleIds) {
        Long tenantId = requireTenantId();
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.NOT_FOUND,
                "用户不存在: " + userId
            ));
        // 查询所有角色
        List<Role> roles = roleRepository.findByIdInAndTenantId(roleIds, tenantId);
        if (roles.size() != roleIds.size()) {
            throw new BusinessException(
                ErrorCode.NOT_FOUND,
                "部分角色不存在"
            );
        }
        userRepository.deleteUserRoleRelationsByUserId(user.getId(), tenantId);
        for (Role role : roles) {
            userRepository.addUserRoleRelation(tenantId, user.getId(), role.getId());
        }
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(ErrorCode.MISSING_PARAMETER, "缺少租户信息");
        }
        return tenantId;
    }
}
