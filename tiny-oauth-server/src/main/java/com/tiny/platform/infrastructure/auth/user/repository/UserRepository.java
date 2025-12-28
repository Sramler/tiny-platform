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

    @Query("select u.id from User u where u.username = :username")
    Optional<Long> findUserIdByUsername(@Param("username") String username);

    /**
     * 重写findById方法，使用@EntityGraph注解，在查询用户时，立即加载roles集合，解决懒加载异常
     * @param id 用户ID
     * @return 包含角色的用户
     */
    @Override
    @EntityGraph(attributePaths = "roles")
    Optional<User> findById(@NonNull Long id);

    /**
     * 根据角色ID查询所有拥有该角色的用户ID列表
     * @param roleId 角色ID
     * @return 用户ID列表
     */
    @Query(value = "SELECT user_id FROM user_role WHERE role_id = :roleId", nativeQuery = true)
    List<Long> findUserIdsByRoleId(@Param("roleId") Long roleId);

    /**
     * 删除用户与角色的关联关系
     * @param userId 用户ID
     * @param roleId 角色ID
     */
    @Modifying
    @Query(value = "DELETE FROM user_role WHERE user_id = :userId AND role_id = :roleId", nativeQuery = true)
    void deleteUserRoleRelation(@Param("userId") Long userId, @Param("roleId") Long roleId);

    /**
     * 添加用户与角色的关联关系
     * @param userId 用户ID
     * @param roleId 角色ID
     */
    @Modifying
    @Query(value = "INSERT INTO user_role (user_id, role_id) VALUES (:userId, :roleId)", nativeQuery = true)
    void addUserRoleRelation(@Param("userId") Long userId, @Param("roleId") Long roleId);
}