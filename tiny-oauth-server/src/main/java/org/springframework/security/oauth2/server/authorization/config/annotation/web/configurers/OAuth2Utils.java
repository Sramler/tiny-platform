package org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers;

import java.util.Map;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.util.StringUtils;

/**

 */
public class OAuth2Utils {

	public static OAuth2TokenGenerator<? extends OAuth2Token> getTokenGenerator(HttpSecurity httpSecurity) {
		OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator = httpSecurity
				.getSharedObject(OAuth2TokenGenerator.class);
		if (tokenGenerator == null) {
			tokenGenerator = getOptionalBean(httpSecurity, OAuth2TokenGenerator.class);
			if (tokenGenerator != null) {
				httpSecurity.setSharedObject(OAuth2TokenGenerator.class, tokenGenerator);
			}
		}
		return tokenGenerator;
	}

	private static <T> T getOptionalBean(HttpSecurity httpSecurity, Class<T> type) {
		ApplicationContext applicationContext = httpSecurity.getSharedObject(ApplicationContext.class);
		Map<String, T> beansMap = BeanFactoryUtils.beansOfTypeIncludingAncestors(applicationContext, type);
		if (beansMap.size() > 1) {
			throw new NoUniqueBeanDefinitionException(type, beansMap.size(),
					"Expected single matching bean of type '" + type.getName() + "' but found " + beansMap.size() + ": "
							+ StringUtils.collectionToCommaDelimitedString(beansMap.keySet()));
		}
		return (!beansMap.isEmpty() ? beansMap.values().iterator().next() : null);
	}

}
