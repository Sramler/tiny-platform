package com.tiny.platform.core.oauth.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * 扩展 {@link JdbcOAuth2AuthorizationService}，在保存授权信息时输出关键信息，便于排查 refresh_token 相关问题。
 */
public class TracingOAuth2AuthorizationService extends JdbcOAuth2AuthorizationService {

    private static final Logger logger = LoggerFactory.getLogger(TracingOAuth2AuthorizationService.class);

    public TracingOAuth2AuthorizationService(JdbcTemplate jdbcTemplate, RegisteredClientRepository registeredClientRepository) {
        super(jdbcTemplate, registeredClientRepository);
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        if (logger.isInfoEnabled()) {
            logger.info("[OAuth2][save] clientId={} principal={} grantType={} hasRefreshToken={} refreshIssuedAt={} refreshExpiresAt={}",
                    authorization.getRegisteredClientId(),
                    authorization.getPrincipalName(),
                    authorization.getAuthorizationGrantType().getValue(),
                    authorization.getRefreshToken() != null,
                    authorization.getRefreshToken() != null ? authorization.getRefreshToken().getToken().getIssuedAt() : null,
                    authorization.getRefreshToken() != null ? authorization.getRefreshToken().getToken().getExpiresAt() : null);
        }
        if (authorization.getRefreshToken() == null && logger.isWarnEnabled()) {
            logger.warn("[OAuth2][save] authorization {} issued without refresh token (authorizedScopes={})",
                    authorization.getId(),
                    authorization.getAuthorizedScopes());
        }
        super.save(authorization);
    }
}
