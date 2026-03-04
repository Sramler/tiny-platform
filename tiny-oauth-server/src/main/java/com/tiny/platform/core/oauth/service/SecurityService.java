package com.tiny.platform.core.oauth.service;

import com.tiny.platform.infrastructure.auth.user.domain.User;
import java.util.Map;

/**
 * 用户安全与TOTP相关服务接口，封装主要业务逻辑
 */
public interface SecurityService {
    /**
     * 查询用户安全状态（TOTP绑定/激活等）
     */
    Map<String, Object> getSecurityStatus(User user);

    /**
     * 绑定TOTP流程
     * @param user 用户实体
     * @param plainPassword 密码
     * @param totpCode 验证码（真实环境要用TOTP算法库校验）
     */
    Map<String, Object> bindTotp(User user, String plainPassword, String totpCode);

    /**
     * 解绑用户TOTP
     */
    Map<String, Object> unbindTotp(User user, String plainPassword, String totpCode);

    /**
     * 校验TOTP（敏感操作step-up）
     */
    Map<String, Object> checkTotp(User user, String totpCode);

    /**
     * 跳过/不再提醒 MFA。
     * 该能力只用于 OPTIONAL 模式下、未激活 TOTP 用户的“提醒抑制”；
     * 不是已激活 TOTP 用户的验证绕过通道。
     */
    Map<String, Object> skipMfaRemind(User user, boolean skip);

    /**
     * 预绑定TOTP，返回 secret、otpauthUri 等（用于二维码/扫码，未激活）
     */
    Map<String, Object> preBindTotp(User user);
}
