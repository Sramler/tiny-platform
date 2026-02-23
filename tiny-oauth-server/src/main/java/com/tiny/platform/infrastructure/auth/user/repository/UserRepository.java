package com.tiny.platform.infrastructure.auth.user.repository;

import com.tiny.platform.infrastructure.auth.user.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {


    @EntityGraph(attributePaths = {"roles", "roles.resources"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<User> findUserByUsername(String username);

    @EntityGraph(attributePaths = {"roles", "roles.resources"}, type = EntityGraph.EntityGraphType.LOAD)
    Optional<User> findUserByUsernameAndTenantId(String username, Long tenantId);

    @Query("select u.id from User u where u.username = :username")
    Optional<Long> findUserIdByUsername(@Param("username") String username);

    @Query("select u.id from User u where u.username = :username and u.tenantId = :tenantId")
    Optional<Long> findUserIdByUsernameAndTenantId(@Param("username") String username, @Param("tenantId") Long tenantId);

    /**
     * 重写findById方法，使用@EntityGraph注解，在查询用户时，立即加载roles集合，解决懒加载异常
     * @param id 用户ID
     * @return 包含角色的用户
     */
    @Override
    @EntityGraph(attributePaths = "roles")
    Optional<User> findById(@NonNull Long id);

    @EntityGraph(attributePaths = "roles")
    Optional<User> findByIdAndTenantId(@NonNull Long id, @NonNull Long tenantId);

    /**
     * 根据角色ID查询所有拥有该角色的用户ID列表
     * @param roleId 角色ID
     * @return 用户ID列表
     */
    @Query(value = "SELECT user_id FROM user_role WHERE role_id = :roleId AND tenant_id = :tenantId", nativeQuery = true)
    List<Long> findUserIdsByRoleId(@Param("roleId") Long roleId, @Param("tenantId") Long tenantId);

    /**
     * 删除用户与角色的关联关系
     * @param userId 用户ID
     * @param roleId 角色ID
     */
    @Modifying
    @Query(value = "DELETE FROM user_role WHERE user_id = :userId AND role_id = :roleId AND tenant_id = :tenantId", nativeQuery = true)
    void deleteUserRoleRelation(@Param("userId") Long userId, @Param("roleId") Long roleId, @Param("tenantId") Long tenantId);

    @Modifying
    @Query(value = "DELETE FROM user_role WHERE user_id = :userId AND tenant_id = :tenantId", nativeQuery = true)
    void deleteUserRoleRelationsByUserId(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    /**
     * 添加用户与角色的关联关系
     * @param userId 用户ID
     * @param roleId 角色ID
     */
    @Modifying
    @Query(value = "INSERT INTO user_role (tenant_id, user_id, role_id) VALUES (:tenantId, :userId, :roleId)", nativeQuery = true)
    void addUserRoleRelation(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("roleId") Long roleId);
}
