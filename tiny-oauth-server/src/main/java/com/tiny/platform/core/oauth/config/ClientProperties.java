package com.tiny.platform.core.oauth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "authentication")
public class ClientProperties {

    private Jwt jwt;
    private List<Client> clients;

    public static class Jwt {
        private String publicKeyPath;
        private String privateKeyPath;

        public String getPrivateKeyPath() {
            return privateKeyPath;
        }

        public void setPrivateKeyPath(String privateKeyPath) {
            this.privateKeyPath = privateKeyPath;
        }

        public String getPublicKeyPath() {
            return publicKeyPath;
        }

        public void setPublicKeyPath(String publicKeyPath) {
            this.publicKeyPath = publicKeyPath;
        }
    }

    public static class Client {
        private String clientId;
        private String clientSecret;
        private List<String> authenticationMethods;
        private List<String> grantTypes;
        private List<String> redirectUris;
        private List<String> postLogoutRedirectUris;
        private List<String> scopes;

        private ClientSetting clientSetting = new ClientSetting();
        private TokenSetting tokenSetting = new TokenSetting();

        public static class ClientSetting {
            private boolean requireAuthorizationConsent = true;
            private boolean requireProofKey = false;

            public boolean isRequireAuthorizationConsent() {
                return requireAuthorizationConsent;
            }

            public void setRequireAuthorizationConsent(boolean requireAuthorizationConsent) {
                this.requireAuthorizationConsent = requireAuthorizationConsent;
            }

            public boolean isRequireProofKey() {
                return requireProofKey;
            }

            public void setRequireProofKey(boolean requireProofKey) {
                this.requireProofKey = requireProofKey;
            }
        }

        public static class TokenSetting {
            private Duration accessTokenTimeToLive = Duration.ofMinutes(3);
            private Duration refreshTokenTimeToLive = Duration.ofHours(8);
            private boolean reuseRefreshTokens = true;
            private String accessTokenFormat = "self-contained"; // or "reference"

            public Duration getAccessTokenTimeToLive() {
                return accessTokenTimeToLive;
            }

            public void setAccessTokenTimeToLive(Duration accessTokenTimeToLive) {
                this.accessTokenTimeToLive = accessTokenTimeToLive;
            }

            public Duration getRefreshTokenTimeToLive() {
                return refreshTokenTimeToLive;
            }

            public void setRefreshTokenTimeToLive(Duration refreshTokenTimeToLive) {
                this.refreshTokenTimeToLive = refreshTokenTimeToLive;
            }

            public boolean isReuseRefreshTokens() {
                return reuseRefreshTokens;
            }

            public void setReuseRefreshTokens(boolean reuseRefreshTokens) {
                this.reuseRefreshTokens = reuseRefreshTokens;
            }

            public String getAccessTokenFormat() {
                return accessTokenFormat;
            }

            public void setAccessTokenFormat(String accessTokenFormat) {
                this.accessTokenFormat = accessTokenFormat;
            }
        }


        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public List<String> getAuthenticationMethods() {
            return authenticationMethods;
        }

        public void setAuthenticationMethods(List<String> authenticationMethods) {
            this.authenticationMethods = authenticationMethods;
        }

        public List<String> getGrantTypes() {
            return grantTypes;
        }

        public void setGrantTypes(List<String> grantTypes) {
            this.grantTypes = grantTypes;
        }

        public List<String> getRedirectUris() {
            return redirectUris;
        }

        public void setRedirectUris(List<String> redirectUris) {
            this.redirectUris = redirectUris;
        }

        public List<String> getPostLogoutRedirectUris() {
            return postLogoutRedirectUris;
        }

        public void setPostLogoutRedirectUris(List<String> postLogoutRedirectUris) {
            this.postLogoutRedirectUris = postLogoutRedirectUris;
        }

        public List<String> getScopes() {
            return scopes;
        }

        public void setScopes(List<String> scopes) {
            this.scopes = scopes;
        }

        public ClientSetting getClientSetting() {
            return clientSetting;
        }

        public void setClientSetting(ClientSetting clientSetting) {
            this.clientSetting = clientSetting;
        }

        public TokenSetting getTokenSetting() {
            return tokenSetting;
        }

        public void setTokenSetting(TokenSetting tokenSetting) {
            this.tokenSetting = tokenSetting;
        }
    }

    public List<Client> getClients() {
        return clients;
    }

    public void setClients(List<Client> clients) {
        this.clients = clients;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }
}
