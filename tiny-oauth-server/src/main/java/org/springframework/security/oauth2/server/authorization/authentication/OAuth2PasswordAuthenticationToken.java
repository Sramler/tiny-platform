package org.springframework.security.oauth2.server.authorization.authentication;

import org.springframework.lang.Nullable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.Map;

/**
 * @see OAuth2AuthorizationConsentAuthenticationToken
 * @see OAuth2AccessTokenAuthenticationToken
 * @see OAuth2ClientAuthenticationToken
 */
public class OAuth2PasswordAuthenticationToken extends AbstractAuthenticationToken {

	private static final long serialVersionUID = 1L;

	private final String username;

	private final Object credentials;

	private final Map<String, Object> additionalParameters;

    public Map<String, Object> getAdditionalParameters() {
        return additionalParameters;
    }

    public OAuth2PasswordAuthenticationToken(String username, @Nullable Object credentials,
                                             @Nullable Map<String, Object> additionalParameters) {
		super(Collections.emptyList());
		Assert.hasText(username, "username cannot be empty");
		this.username = username;
		this.credentials = credentials;
		this.additionalParameters = Collections
			.unmodifiableMap((additionalParameters != null) ? additionalParameters : Collections.emptyMap());
	}

	@Override
	public Object getCredentials() {
		return this.credentials;
	}

	@Override
	public Object getPrincipal() {
		return this.username;
	}

}
