package com.tiny.platform.infrastructure.auth.user.repository;

import com.tiny.platform.infrastructure.auth.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    List<User> findAllByUsername(String username);

    Optional<User> findUserByUsername(String username);

    @Query("select u.id from User u where u.username = :username")
    Optional<Long> findUserIdByUsername(@Param("username") String username);

    @Query("select u.username from User u where u.id in :userIds")
    List<String> findUsernamesByIdIn(@Param("userIds") Collection<Long> userIds);

    @Override
    Optional<User> findById(@NonNull Long id);
}
