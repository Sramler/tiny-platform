package com.tiny.platform.core.oauth.repository;

import com.tiny.platform.core.oauth.model.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户头像 Repository
 */
@Repository
public interface UserAvatarRepository extends JpaRepository<UserAvatar, Long> {

    /**
     * 根据用户ID查找头像
     */
    Optional<UserAvatar> findByUserId(Long userId);

    /**
     * 根据内容哈希查找头像（用于去重）
     */
    Optional<UserAvatar> findByContentHash(String contentHash);

    /**
     * 检查用户是否有头像
     */
    boolean existsByUserId(Long userId);

    /**
     * 删除用户头像
     */
    void deleteByUserId(Long userId);
}
