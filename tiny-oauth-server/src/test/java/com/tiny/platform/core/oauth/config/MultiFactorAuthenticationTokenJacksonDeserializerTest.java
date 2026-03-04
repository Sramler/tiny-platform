package com.tiny.platform.core.oauth.config;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.AuthenticationFactorAuthorities;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultiFactorAuthenticationTokenJacksonDeserializerTest {

    private final MultiFactorAuthenticationTokenJacksonDeserializer deserializer =
            new MultiFactorAuthenticationTokenJacksonDeserializer();

    @Test
    void shouldDeserializeAuthenticatedTokenWithWrappedFactorsAuthoritiesAndSecurityUserDetails() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonParser parser = new JsonFactory().createParser("""
                {
                  "username":"alice",
                  "credentials":"secret",
                  "provider":"LOCAL",
                  "completedFactors":["java.util.Collections$UnmodifiableSet",["PASSWORD","TOTP","UNKNOWN"]],
                  "authorities":["java.util.Collections$UnmodifiableRandomAccessList",[{"authority":"SCOPE_profile"},"ROLE_USER"]],
                  "authenticated":true,
                  "details":{
                    "@type":"securityUser",
                    "userId":"123",
                    "tenantId":"9",
                    "username":"alice",
                    "password":"",
                    "authorities":[],
                    "accountNonExpired":true,
                    "accountNonLocked":true,
                    "credentialsNonExpired":true,
                    "enabled":true
                  }
                }
                """);
        parser.setCodec(mapper);

        MultiFactorAuthenticationToken token = deserializer.deserialize(parser, mapper.getDeserializationContext());

        assertThat(token.getName()).isEqualTo("alice");
        assertThat(token.getCredentials()).isEqualTo("secret");
        assertThat(token.isAuthenticated()).isTrue();
        assertThat(token.getProvider()).isEqualTo(MultiFactorAuthenticationToken.AuthenticationProviderType.LOCAL);
        assertThat(token.getCompletedFactors())
                .contains(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD,
                        MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP)
                .doesNotContain(MultiFactorAuthenticationToken.AuthenticationFactorType.UNKNOWN);
        assertThat(token.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        "SCOPE_profile",
                        "ROLE_USER",
                        AuthenticationFactorAuthorities.FACTOR_AUTHORITY_PREFIX + "PASSWORD",
                        AuthenticationFactorAuthorities.FACTOR_AUTHORITY_PREFIX + "TOTP"
                );
        assertThat(token.getDetails()).isInstanceOf(SecurityUser.class);
        SecurityUser details = (SecurityUser) token.getDetails();
        assertThat(details.getUserId()).isEqualTo(123L);
        assertThat(details.getTenantId()).isEqualTo(9L);
    }

    @Test
    void shouldDeserializeTokenWithoutCompletedFactorsUsingAuthenticationTypeAndGenericDetails() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonParser parser = new JsonFactory().createParser("""
                {
                  "username":"bob",
                  "credentials":"pwd",
                  "authenticationProvider":"LDAP",
                  "authenticationType":"PASSWORD",
                  "authorities":["SCOPE_profile"],
                  "authenticated":false,
                  "details":{"ip":"127.0.0.1","session":"abc"}
                }
                """);
        parser.setCodec(mapper);

        MultiFactorAuthenticationToken token = deserializer.deserialize(parser, mapper.getDeserializationContext());

        assertThat(token.getName()).isEqualTo("bob");
        assertThat(token.isAuthenticated()).isFalse();
        assertThat(token.getProvider()).isEqualTo(MultiFactorAuthenticationToken.AuthenticationProviderType.LDAP);
        assertThat(token.getCompletedFactors()).containsExactly(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD);
        assertThat(token.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        "SCOPE_profile",
                        AuthenticationFactorAuthorities.FACTOR_AUTHORITY_PREFIX + "PASSWORD"
                );
        assertThat(token.getDetails()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) token.getDetails();
        assertThat(details).containsEntry("ip", "127.0.0.1").containsEntry("session", "abc");
    }

    @Test
    void shouldAddMultipleCompletedFactorsInUnauthenticatedBranchAndIgnoreInvalidDetails() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonParser parser = new JsonFactory().createParser("""
                {
                  "username":"carol",
                  "provider":"LOCAL",
                  "completedFactors":["PASSWORD","TOTP"],
                  "authenticated":false,
                  "details":{"@type":"securityUser","userId":"bad-long"}
                }
                """);
        parser.setCodec(mapper);

        MultiFactorAuthenticationToken token = deserializer.deserialize(parser, mapper.getDeserializationContext());

        assertThat(token.isAuthenticated()).isFalse();
        assertThat(token.getCompletedFactors())
                .contains(MultiFactorAuthenticationToken.AuthenticationFactorType.PASSWORD,
                        MultiFactorAuthenticationToken.AuthenticationFactorType.TOTP);
        assertThat(token.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(
                        AuthenticationFactorAuthorities.FACTOR_AUTHORITY_PREFIX + "PASSWORD",
                        AuthenticationFactorAuthorities.FACTOR_AUTHORITY_PREFIX + "TOTP"
                );
        // details 反序列化失败会被吞掉，不影响 token 返回
        assertThat(token.getDetails()).isNull();
    }

    @Test
    void shouldForceAuthenticatedFlagTrueWhenNoAuthorities() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonParser parser = new JsonFactory().createParser("""
                {
                  "username":"dave",
                  "provider":"LOCAL",
                  "completedFactors":"not-an-array",
                  "authenticated":true,
                  "details":null
                }
                """);
        parser.setCodec(mapper);

        MultiFactorAuthenticationToken token = deserializer.deserialize(parser, mapper.getDeserializationContext());

        assertThat(token.isAuthenticated()).isTrue();
        assertThat(token.getAuthorities()).isEmpty();
        assertThat(token.getCompletedFactors()).isEmpty();
        assertThat(token.getDetails()).isNull();
    }

    @Test
    void shouldWrapConstructionExceptionAsIoException() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonParser parser = new JsonFactory().createParser("""
                {
                  "authenticated": false
                }
                """);
        parser.setCodec(mapper);

        assertThatThrownBy(() -> deserializer.deserialize(parser, mapper.getDeserializationContext()))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("Failed to deserialize MultiFactorAuthenticationToken");
    }
}
