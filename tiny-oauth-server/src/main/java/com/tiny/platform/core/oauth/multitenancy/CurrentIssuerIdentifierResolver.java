package com.tiny.platform.core.oauth.multitenancy;

import com.tiny.platform.core.oauth.tenant.IssuerTenantSupport;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.stereotype.Component;

/**
 * 从 AuthorizationServerContext 提取当前 issuer 对应的 tenantCode。
 */
@Component
public class CurrentIssuerIdentifierResolver {

    public String resolveCurrentTenantCode() {
        AuthorizationServerContext context = AuthorizationServerContextHolder.getContext();
        if (context == null) {
            return null;
        }
        return IssuerTenantSupport.extractTenantCodeFromIssuer(context.getIssuer());
    }
}
