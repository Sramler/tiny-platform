package com.tiny.platform.infrastructure.auth.user.repository;

import com.tiny.platform.infrastructure.auth.user.domain.UserAuthCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 认证凭证仓库：只负责认证材料读写，不承载作用域策略。
 */
@Repository
public interface UserAuthCredentialRepository extends JpaRepository<UserAuthCredential, Long> {

  List<UserAuthCredential> findByUserId(Long userId);

  Optional<UserAuthCredential> findByUserIdAndAuthenticationProviderAndAuthenticationType(
      Long userId,
      String authenticationProvider,
      String authenticationType);

  boolean existsById(Long id);
}
