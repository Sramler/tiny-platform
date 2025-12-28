package com.tiny.platform.application.oauth.workflow;

import com.tiny.platform.infrastructure.auth.role.domain.Role;
import com.tiny.platform.infrastructure.auth.user.domain.User;
import com.tiny.platform.infrastructure.auth.role.service.RoleService;
import com.tiny.platform.infrastructure.auth.user.service.UserService;
import org.camunda.bpm.engine.identity.Group;
import org.camunda.bpm.engine.identity.GroupQuery;
import org.camunda.bpm.engine.identity.NativeUserQuery;
import org.camunda.bpm.engine.identity.Tenant;
import org.camunda.bpm.engine.identity.TenantQuery;
import org.camunda.bpm.engine.identity.UserQuery;
import org.camunda.bpm.engine.impl.identity.ReadOnlyIdentityProvider;
import org.camunda.bpm.engine.impl.persistence.entity.GroupEntity;
import org.camunda.bpm.engine.impl.persistence.entity.UserEntity;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CamundaIdentityProvider implements ReadOnlyIdentityProvider {

    private final UserService userService; // 你的用户服务
    private final RoleService roleService; // 你的角色/组服务

    public CamundaIdentityProvider(UserService userService, RoleService roleService) {
        this.userService = userService;
        this.roleService = roleService;
    }

    // 查询用户
    @Override
    public UserEntity findUserById(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return null;
        }

        Optional<User> userOpt = findUserByFlexibleId(userId);
        if (userOpt.isEmpty()) {
            return null;
        }

        User user = userOpt.get();
        UserEntity entity = new UserEntity();
        // Camunda 要求 ID 为字符串
        entity.setId(String.valueOf(user.getId()));
        // 使用昵称优先，没有则回退用户名
        entity.setFirstName(user.getNickname() != null && !user.getNickname().isEmpty()
                ? user.getNickname()
                : user.getUsername());
        // 你的 User 无明确 email 字段，这里保持为空即可
        return entity;
    }

    // 查询组（这里以角色为组）。目前仅支持通过数值型ID查询（与现有服务契合）。
    @Override
    public GroupEntity findGroupById(String groupId) {
        if (groupId == null || groupId.trim().isEmpty()) {
            return null;
        }

        Optional<Role> roleOpt = parseLong(groupId)
                .flatMap(roleService::findById);
        if (roleOpt.isEmpty()) {
            return null;
        }

        Role role = roleOpt.get();
        GroupEntity group = new GroupEntity();
        // 将组ID与数据库角色ID保持一致，字符串化
        group.setId(String.valueOf(role.getId()));
        group.setName(role.getName());
        group.setType("ROLE");
        return group;
    }

    // 查询用户所属组（即用户拥有的角色）
    public List<Group> findGroupsByUser(String userId) {
        Optional<User> userOpt = findUserByFlexibleId(userId);
        List<Group> result = new ArrayList<>();
        if (userOpt.isEmpty()) {
            return result;
        }

        User user = userOpt.get();
        if (user.getRoles() == null || user.getRoles().isEmpty()) {
            return result;
        }

        for (Role role : user.getRoles()) {
            GroupEntity g = new GroupEntity();
            g.setId(String.valueOf(role.getId()));
            g.setName(role.getName());
            g.setType("ROLE");
            result.add(g);
        }
        return result;
    }

    // 可选：密码策略（只读可返回 null）
    // 仅用于接口满足；认证通常由 Spring Security 处理
    @Override
    public boolean checkPassword(String userId, String password) {
        return false;
    }

    public void createUser(UserEntity user) { throw new UnsupportedOperationException("Read-only identity provider"); }
    public void deleteUser(String userId) { throw new UnsupportedOperationException("Read-only identity provider"); }
    public void createGroup(GroupEntity group) { throw new UnsupportedOperationException("Read-only identity provider"); }
    public void deleteGroup(String groupId) { throw new UnsupportedOperationException("Read-only identity provider"); }

    // -------------------- 私有帮助方法 --------------------

    private Optional<User> findUserByFlexibleId(String userId) {
        // 尝试按数值ID查询
        Optional<Long> idOpt = parseLong(userId);
        if (idOpt.isPresent()) {
            Optional<User> byId = userService.findById(idOpt.get());
            if (byId.isPresent()) {
                return byId;
            }
        }
        // 回退按用户名查询
        return userService.findByUsername(userId);
    }

    private Optional<Long> parseLong(String text) {
        try {
            return Optional.of(Long.parseLong(text));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    // -------------------- 其余接口（按需返回空/不支持） --------------------

    @Override
    public UserQuery createUserQuery() { return null; }

    @Override
    public UserQuery createUserQuery(CommandContext commandContext) { return null; }

    @Override
    public NativeUserQuery createNativeUserQuery() { return null; }

    @Override
    public GroupQuery createGroupQuery() { return null; }

    @Override
    public GroupQuery createGroupQuery(CommandContext commandContext) { return null; }

    @Override
    public TenantQuery createTenantQuery() { return null; }

    @Override
    public TenantQuery createTenantQuery(CommandContext commandContext) { return null; }

    @Override
    public Tenant findTenantById(String tenantId) { return null; }

    // 以下接口在某些 Camunda 版本中并不存在于 ReadOnlyIdentityProvider，可不实现

    @Override
    public void flush() { /* no-op */ }

    @Override
    public void close() { /* no-op */ }
}