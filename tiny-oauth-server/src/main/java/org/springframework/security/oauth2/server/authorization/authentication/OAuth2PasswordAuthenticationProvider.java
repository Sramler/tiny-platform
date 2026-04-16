package org.springframework.security.oauth2.server.authorization.authentication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2Utils;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.util.Assert;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**

 * @see OAuth2AuthorizationCodeAuthenticationProvider
 * @see OAuth2RefreshTokenAuthenticationProvider
 * @see OAuth2ClientCredentialsAuthenticationProvider
 */
public final class OAuth2PasswordAuthenticationProvider implements AuthenticationProvider {

	private static final String ERROR_URI = "https://datatracker.ietf.org/doc/html/rfc6749#section-5.2";

	private static final AuthorizationGrantType PASSWORD_GRANT_TYPE = new AuthorizationGrantType("password");

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final HttpSecurity httpSecurity;

	private final OAuth2AuthorizationService authorizationService;

	private final UserDetailsService userDetailsService;

	private final PasswordEncoder passwordEncoder;

	private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

	public OAuth2PasswordAuthenticationProvider(HttpSecurity httpSecurity,
			OAuth2AuthorizationService authorizationService, UserDetailsService userDetailsService) {
		Assert.notNull(httpSecurity, "httpSecurity cannot be null");
		Assert.notNull(authorizationService, "authorizationService cannot be null");
		Assert.notNull(userDetailsService, "userDetailsService cannot be null");
		this.httpSecurity = httpSecurity;
		this.authorizationService = authorizationService;
		this.userDetailsService = userDetailsService;
		this.passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
		this.tokenGenerator = OAuth2Utils.getTokenGenerator(httpSecurity);
		Assert.notNull(this.tokenGenerator, "tokenGenerator cannot be null");
		httpSecurity.authenticationProvider(this);
	}

	public OAuth2PasswordAuthenticationProvider(HttpSecurity httpSecurity,
			OAuth2AuthorizationService authorizationService, UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		Assert.notNull(httpSecurity, "httpSecurity cannot be null");
		Assert.notNull(authorizationService, "authorizationService cannot be null");
		Assert.notNull(userDetailsService, "userDetailsService cannot be null");
		Assert.notNull(passwordEncoder, "passwordEncoder cannot be null");
		this.httpSecurity = httpSecurity;
		this.authorizationService = authorizationService;
		this.userDetailsService = userDetailsService;
		this.passwordEncoder = passwordEncoder;
		this.tokenGenerator = OAuth2Utils.getTokenGenerator(httpSecurity);
		Assert.notNull(this.tokenGenerator, "tokenGenerator cannot be null");
		httpSecurity.authenticationProvider(this);
	}

	public OAuth2PasswordAuthenticationProvider(HttpSecurity httpSecurity,
			OAuth2AuthorizationService authorizationService, UserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder, OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator) {
		Assert.notNull(httpSecurity, "httpSecurity cannot be null");
		Assert.notNull(authorizationService, "authorizationService cannot be null");
		Assert.notNull(userDetailsService, "userDetailsService cannot be null");
		Assert.notNull(passwordEncoder, "passwordEncoder cannot be null");
		Assert.notNull(tokenGenerator, "tokenGenerator cannot be null");
		this.httpSecurity = httpSecurity;
		this.authorizationService = authorizationService;
		this.userDetailsService = userDetailsService;
		this.passwordEncoder = passwordEncoder;
		this.tokenGenerator = tokenGenerator;
		httpSecurity.authenticationProvider(this);
	}

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {

		OAuth2PasswordGrantAuthenticationToken passwordGrantAuthenticationToken = (OAuth2PasswordGrantAuthenticationToken) authentication;

		OAuth2ClientAuthenticationToken clientAuthenticationToken = (OAuth2ClientAuthenticationToken) passwordGrantAuthenticationToken
			.getPrincipal();

		RegisteredClient registeredClient = clientAuthenticationToken.getRegisteredClient();

		if (this.logger.isTraceEnabled()) {
			this.logger.trace("Retrieved registered client");
		}

		if (!registeredClient.getAuthorizationGrantTypes().contains(PASSWORD_GRANT_TYPE)) {
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Invalid request: requested grant_type is not allowed for registered client '{}'",
						registeredClient.getId());
			}
			throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
		}

		Set<String> authorizedScopes = new LinkedHashSet<>(passwordGrantAuthenticationToken.getScopes());

		if (this.logger.isInfoEnabled()) {
			this.logger.info("Handling password grant token request: client_id={}, username={}, scopes={}",
					registeredClient.getClientId(),
					passwordGrantAuthenticationToken.getUsername(),
					authorizedScopes);
		}

		Object credentials = passwordGrantAuthenticationToken.getCredentials();
		String username = passwordGrantAuthenticationToken.getUsername();
		String password = passwordGrantAuthenticationToken.getPassword();
		Map<String, Object> additionalParameters = passwordGrantAuthenticationToken.getAdditionalParameters();

		UserDetails userDetails = userDetailsService.loadUserByUsername(username);

		boolean matches = passwordEncoder.matches(password, userDetails.getPassword());
		if (!matches) {
			throw new BadCredentialsException("Invalid request: password do not match");
		}

		OAuth2PasswordAuthenticationToken passwordAuthenticationToken = new OAuth2PasswordAuthenticationToken(username,
				registeredClient, additionalParameters);

		DefaultOAuth2TokenContext.Builder tokenContextBuilder = DefaultOAuth2TokenContext.builder()
			.registeredClient(registeredClient)
			.principal(passwordAuthenticationToken)
			.authorizationServerContext(AuthorizationServerContextHolder.getContext())
			.authorizedScopes(authorizedScopes)
			.tokenType(OAuth2TokenType.ACCESS_TOKEN)
			.authorizationGrantType(PASSWORD_GRANT_TYPE)
			.authorizationGrant(passwordGrantAuthenticationToken);

		// ----- Access token -----
		OAuth2TokenContext tokenContext = tokenContextBuilder.tokenType(OAuth2TokenType.ACCESS_TOKEN).build();

		OAuth2Token generatedAccessToken = this.tokenGenerator.generate(tokenContext);

		if (generatedAccessToken == null) {
			OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
					"The token generator failed to generate the access token.", ERROR_URI);
			throw new OAuth2AuthenticationException(error);
		}

		if (this.logger.isTraceEnabled()) {
			this.logger.trace("Generated access token");
		}
		if (this.logger.isInfoEnabled()) {
			this.logger.info("Issued access token (password grant): client_id={}, username={}, scopes={}",
					registeredClient.getClientId(), username, authorizedScopes);
		}

		OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.withRegisteredClient(registeredClient)
			.principalName(username)
			.authorizationGrantType(PASSWORD_GRANT_TYPE)
			.authorizedScopes(authorizedScopes);

		OAuth2AccessToken accessToken = OAuth2AuthenticationProviderUtils.accessToken(authorizationBuilder,
				generatedAccessToken, tokenContext);

		// ----- Refresh token -----
		OAuth2RefreshToken refreshToken = null;
		// Do not issue refresh token to public client
		if (registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
			tokenContext = tokenContextBuilder.tokenType(OAuth2TokenType.REFRESH_TOKEN).build();
			OAuth2Token generatedRefreshToken = this.tokenGenerator.generate(tokenContext);
			if (generatedRefreshToken != null) {
				if (!(generatedRefreshToken instanceof OAuth2RefreshToken)) {
					OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.SERVER_ERROR,
							"The token generator failed to generate a valid refresh token.", ERROR_URI);
					throw new OAuth2AuthenticationException(error);
				}

				if (this.logger.isTraceEnabled()) {
					this.logger.trace("Generated refresh token");
				}
				if (this.logger.isInfoEnabled()) {
					this.logger.info("Issued refresh token (password grant): client_id={}, username={}",
							registeredClient.getClientId(), username);
				}

				refreshToken = (OAuth2RefreshToken) generatedRefreshToken;
				authorizationBuilder.refreshToken(refreshToken);
			}
		}

		OAuth2Authorization authorization = authorizationBuilder.build();

		this.authorizationService.save(authorization);

		if (this.logger.isTraceEnabled()) {
			this.logger.trace("Saved authorization");
			// This log is kept separate for consistency with other providers
			this.logger.trace("Authenticated token request");
		}

		return new OAuth2AccessTokenAuthenticationToken(registeredClient, passwordAuthenticationToken, accessToken,
				refreshToken, additionalParameters);
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return OAuth2PasswordGrantAuthenticationToken.class.isAssignableFrom(authentication);
	}

}
