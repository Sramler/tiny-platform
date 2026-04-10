package com.tiny.platform.core.oauth.multitenancy;

import com.tiny.platform.core.oauth.tenant.IssuerTenantSupport;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.stereotype.Component;

/**
 * 从 AuthorizationServerContext 提取当前 issuer 对应的 issuerKey。
 */
@Component
public class CurrentIssuerIdentifierResolver {

    public String resolveCurrentIssuerKey() {
        AuthorizationServerContext context = AuthorizationServerContextHolder.getContext();
        if (context == null) {
            return null;
        }
        return IssuerTenantSupport.extractIssuerKeyFromIssuer(context.getIssuer());
    }

    /**
     * 兼容旧调用方：仅在当前 issuer 代表真实租户时返回 tenantCode。
     */
    public String resolveCurrentTenantCode() {
        return IssuerTenantSupport.extractTenantCodeFromIssuer(
            AuthorizationServerContextHolder.getContext() != null
                ? AuthorizationServerContextHolder.getContext().getIssuer()
                : null
        );
    }
}
