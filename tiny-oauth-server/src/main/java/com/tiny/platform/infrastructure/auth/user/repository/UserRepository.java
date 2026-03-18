package com.tiny.platform.infrastructure.auth.user.repository;

import com.tiny.platform.infrastructure.auth.user.domain.User;
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

    List<User> findAllByUsername(String username);

    Optional<User> findUserByUsername(String username);

    @Query("select u.id from User u where u.username = :username")
    Optional<Long> findUserIdByUsername(@Param("username") String username);

    @Override
    Optional<User> findById(@NonNull Long id);

    /**
     * 兼容：按租户+ID 查用户（租户内用户模型）。新逻辑优先用 tenant_user + findById。
     */
    Optional<User> findByIdAndTenantId(@NonNull Long id, @NonNull Long tenantId);
}
