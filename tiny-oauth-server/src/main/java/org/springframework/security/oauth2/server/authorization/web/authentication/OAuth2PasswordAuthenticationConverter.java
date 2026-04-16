package org.springframework.security.oauth2.server.authorization.web.authentication;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2PasswordGrantAuthenticationToken;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

import java.util.*;

/**

 * @see OAuth2AuthorizationCodeAuthenticationConverter
 * @see OAuth2RefreshTokenAuthenticationConverter
 * @see OAuth2ClientCredentialsAuthenticationConverter
 */
public class OAuth2PasswordAuthenticationConverter implements AuthenticationConverter {

	static final String PASSWORD_REQUEST_ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc6749#section-4.3.2";
	private static final AuthorizationGrantType PASSWORD_GRANT_TYPE = new AuthorizationGrantType("password");
	private static final String USERNAME_PARAMETER = "username";
	private static final String PASSWORD_PARAMETER = "password";

	@Nullable
	@Override
	public Authentication convert(HttpServletRequest request) {
		MultiValueMap<String, String> parameters = OAuth2EndpointUtils.getFormParameters(request);

		// grant_type (REQUIRED)
		String grantType = parameters.getFirst(OAuth2ParameterNames.GRANT_TYPE);
		if (!PASSWORD_GRANT_TYPE.getValue().equals(grantType)) {
			return null;
		}

		Authentication clientPrincipal = SecurityContextHolder.getContext().getAuthentication();

		String username = parameters.getFirst(USERNAME_PARAMETER);
		if (!StringUtils.hasText(username) || parameters.get(USERNAME_PARAMETER).size() != 1) {
			OAuth2EndpointUtils.throwError(OAuth2ErrorCodes.INVALID_REQUEST, USERNAME_PARAMETER,
					PASSWORD_REQUEST_ERROR_URI);
		}

		String password = parameters.getFirst(PASSWORD_PARAMETER);
		if (!StringUtils.hasText(password) || parameters.get(PASSWORD_PARAMETER).size() != 1) {
			OAuth2EndpointUtils.throwError(OAuth2ErrorCodes.INVALID_REQUEST, PASSWORD_PARAMETER,
					PASSWORD_REQUEST_ERROR_URI);
		}

		// scope (OPTIONAL)
		String scope = parameters.getFirst(OAuth2ParameterNames.SCOPE);
		if (StringUtils.hasText(scope) && parameters.get(OAuth2ParameterNames.SCOPE).size() != 1) {
			OAuth2EndpointUtils.throwError(OAuth2ErrorCodes.INVALID_REQUEST, OAuth2ParameterNames.SCOPE,
					PASSWORD_REQUEST_ERROR_URI);
		}
		Set<String> requestedScopes = null;
		if (StringUtils.hasText(scope)) {
			requestedScopes = new HashSet<>(Arrays.asList(StringUtils.delimitedListToStringArray(scope, " ")));
		}

		Map<String, Object> additionalParameters = new HashMap<>();
		parameters.forEach((key, value) -> {
			if (!key.equals(OAuth2ParameterNames.GRANT_TYPE) && !key.equals(OAuth2ParameterNames.SCOPE)) {
				additionalParameters.put(key, (value.size() == 1) ? value.get(0) : value.toArray(new String[0]));
			}
		});

		return new OAuth2PasswordGrantAuthenticationToken(username, password, clientPrincipal, requestedScopes,
				additionalParameters);
	}

}
