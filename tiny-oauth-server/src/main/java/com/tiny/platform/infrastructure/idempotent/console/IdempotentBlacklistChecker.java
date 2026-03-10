package com.tiny.platform.infrastructure.idempotent.console;

import com.tiny.platform.infrastructure.idempotent.console.repository.IdempotentConsoleRepository;
import org.springframework.lang.Nullable;

/**
 * 幂等黑名单检查器
 *
 * <p>在幂等切面执行前检查 key 是否在黑名单中，若匹配则直接拒绝。</p>
 *
 * @author tiny-platform
 * @since 1.0.0
 */
public class IdempotentBlacklistChecker {

    @Nullable
    private final IdempotentConsoleRepository consoleRepository;

    public IdempotentBlacklistChecker(@Nullable IdempotentConsoleRepository consoleRepository) {
        this.consoleRepository = consoleRepository;
    }

    /**
     * 检查 fullKey 是否在黑名单中
     *
     * @param fullKey 完整幂等 key
     * @return true 表示在黑名单中，应拒绝请求
     */
    public boolean isBlacklisted(String fullKey) {
        if (consoleRepository == null) {
            return false;
        }
        try {
            return consoleRepository.isBlacklisted(fullKey);
        } catch (Exception e) {
            return false;
        }
    }
}
