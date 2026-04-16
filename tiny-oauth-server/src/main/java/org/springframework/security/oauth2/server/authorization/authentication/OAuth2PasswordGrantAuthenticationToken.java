package org.springframework.security.oauth2.server.authorization.authentication;

import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**

 * @see OAuth2AuthorizationCodeAuthenticationToken
 * @see OAuth2RefreshTokenAuthenticationToken
 * @see OAuth2ClientCredentialsAuthenticationToken
 * @see OAuth2DeviceCodeAuthenticationToken
 */
public class OAuth2PasswordGrantAuthenticationToken extends OAuth2AuthorizationGrantAuthenticationToken {

	private static final AuthorizationGrantType PASSWORD_GRANT_TYPE = new AuthorizationGrantType("password");

	private final String username;

	private final String password;

	private final Set<String> scopes;

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Set<String> getScopes() {
        return scopes;
    }

    /**
	 * Sub-class constructor.
	 * @param clientPrincipal the authenticated client principal
	 * @param additionalParameters the additional parameters
	 */
	public OAuth2PasswordGrantAuthenticationToken(String username, String password, Authentication clientPrincipal,
			@Nullable Set<String> scopes, Map<String, Object> additionalParameters) {
		super(PASSWORD_GRANT_TYPE, clientPrincipal, additionalParameters);
		this.username = username;
		this.password = password;
		this.scopes = Collections.unmodifiableSet((scopes != null) ? new HashSet<>(scopes) : Collections.emptySet());
	}

}
