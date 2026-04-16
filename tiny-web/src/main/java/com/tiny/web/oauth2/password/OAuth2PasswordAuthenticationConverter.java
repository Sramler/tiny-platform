package com.tiny.web.oauth2.password;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.web.authentication.AuthenticationConverter;

import java.util.*;

/**
 * 自定义认证转换器：将请求参数转换为 OAuth2PasswordAuthenticationToken
 */
public class OAuth2PasswordAuthenticationConverter implements AuthenticationConverter {

    private static final AuthorizationGrantType PASSWORD_GRANT_TYPE = new AuthorizationGrantType("password");
    private static final String USERNAME_PARAMETER = "username";
    private static final String PASSWORD_PARAMETER = "password";

    @Override
    public OAuth2PasswordAuthenticationToken convert(HttpServletRequest request) {
        // 必须是 password 类型
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        if (!PASSWORD_GRANT_TYPE.getValue().equals(grantType)) {
            return null;
        }

        // 客户端认证信息
        Authentication clientPrincipal = OAuth2EndpointUtils.getAuthenticatedClientElseThrowInvalidClient(request);
        if (clientPrincipal == null) {
            throw new IllegalArgumentException("客户端未认证");
        }

        // 解析参数
        String username = request.getParameter(USERNAME_PARAMETER);
        String password = request.getParameter(PASSWORD_PARAMETER);
        String scopeParam = request.getParameter(OAuth2ParameterNames.SCOPE);
        Set<String> scopes = scopeParam != null ? new HashSet<>(Arrays.asList(scopeParam.split(" "))) : Collections.emptySet();

        return new OAuth2PasswordAuthenticationToken(clientPrincipal, username, password, scopes);
    }
}
