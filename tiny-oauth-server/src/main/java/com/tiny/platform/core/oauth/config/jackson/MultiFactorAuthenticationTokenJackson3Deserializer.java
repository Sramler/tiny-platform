package com.tiny.platform.core.oauth.config.jackson;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Jackson 3 deserializer for {@link MultiFactorAuthenticationToken}.
 */
public class MultiFactorAuthenticationTokenJackson3Deserializer
        extends StdDeserializer<MultiFactorAuthenticationToken> {

    private static final Logger log = LoggerFactory.getLogger(MultiFactorAuthenticationTokenJackson3Deserializer.class);

    public MultiFactorAuthenticationTokenJackson3Deserializer() {
        super(MultiFactorAuthenticationToken.class);
    }

    @Override
    public MultiFactorAuthenticationToken deserialize(
            JsonParser jsonParser,
            DeserializationContext deserializationContext) throws JacksonException {
        JsonNode jsonNode = deserializationContext.readTree(jsonParser);
        log.debug("[MultiFactorAuthenticationTokenJackson3Deserializer] raw payload: {}", jsonNode);

        String username = jsonNode.has("username") ? jsonNode.get("username").asText() : null;
        Object credentials = jsonNode.has("credentials") && !jsonNode.get("credentials").isNull()
                ? jsonNode.get("credentials").asText() : null;
        String providerStr = jsonNode.has("provider")
                ? jsonNode.get("provider").asText()
                : (jsonNode.has("authenticationProvider") ? jsonNode.get("authenticationProvider").asText() : null);

        Set<MultiFactorAuthenticationToken.AuthenticationFactorType> completedFactors = new HashSet<>();
        if (jsonNode.has("completedFactors")) {
            JsonNode factorsNode = jsonNode.get("completedFactors");
            if (factorsNode.isArray()) {
                if (factorsNode.size() == 2
                        && "java.util.Collections$UnmodifiableSet".equals(factorsNode.get(0).asText())
                        && factorsNode.get(1).isArray()) {
                    factorsNode = factorsNode.get(1);
                }
                for (JsonNode factor : factorsNode) {
                    if (factor.isTextual()) {
                        MultiFactorAuthenticationToken.AuthenticationFactorType resolved =
                                MultiFactorAuthenticationToken.AuthenticationFactorType.from(factor.asText());
                        if (resolved != MultiFactorAuthenticationToken.AuthenticationFactorType.UNKNOWN) {
                            completedFactors.add(resolved);
                        }
                    }
                }
            }
        }

        String initialFactorStr = null;
        if (completedFactors.isEmpty() && jsonNode.has("authenticationType")) {
            initialFactorStr = jsonNode.get("authenticationType").asText();
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        if (jsonNode.has("authorities") && jsonNode.get("authorities").isArray()) {
            JsonNode authoritiesNode = jsonNode.get("authorities");
            if (authoritiesNode.size() == 2
                    && authoritiesNode.get(0).isTextual()
                    && authoritiesNode.get(1).isArray()
                    && authoritiesNode.get(0).asText().startsWith("java.util.")) {
                authoritiesNode = authoritiesNode.get(1);
            }

            for (JsonNode authority : authoritiesNode) {
                if (authority.has("authority")) {
                    authorities.add(new SimpleGrantedAuthority(authority.get("authority").asText()));
                } else if (authority.isTextual()) {
                    authorities.add(new SimpleGrantedAuthority(authority.asText()));
                }
            }
        }

        boolean authenticated = jsonNode.has("authenticated") && jsonNode.get("authenticated").asBoolean(false);

        try {
            Set<MultiFactorAuthenticationToken.AuthenticationFactorType> factorsForToken =
                    completedFactors.isEmpty() ? new HashSet<>() : new HashSet<>(completedFactors);
            if (factorsForToken.isEmpty() && initialFactorStr != null) {
                MultiFactorAuthenticationToken.AuthenticationFactorType initialFactor =
                        MultiFactorAuthenticationToken.AuthenticationFactorType.from(initialFactorStr);
                if (initialFactor != null
                        && initialFactor != MultiFactorAuthenticationToken.AuthenticationFactorType.UNKNOWN) {
                    factorsForToken.add(initialFactor);
                }
            }

            boolean normalizedAuthenticated = authenticated || !factorsForToken.isEmpty();

            MultiFactorAuthenticationToken token = new MultiFactorAuthenticationToken(
                    username,
                    credentials,
                    MultiFactorAuthenticationToken.AuthenticationProviderType.from(providerStr),
                    factorsForToken.isEmpty() ? null : factorsForToken,
                    authorities
            );

            if (!normalizedAuthenticated) {
                token.setAuthenticated(false);
            }

            if (jsonNode.has("details") && !jsonNode.get("details").isNull()) {
                try {
                    JsonNode detailsNode = jsonNode.get("details");
                    if (detailsNode.has("@type") && "securityUser".equals(detailsNode.get("@type").asText())) {
                        token.setDetails(deserializeSecurityUser(detailsNode));
                    } else {
                        token.setDetails(deserializationContext.readTreeAsValue(detailsNode, Object.class));
                    }
                } catch (Exception ex) {
                    log.warn("[MultiFactorAuthenticationTokenJackson3Deserializer] failed to restore details: {}",
                            ex.getMessage());
                }
            }

            return token;
        } catch (Exception ex) {
            throw DatabindException.from(deserializationContext,
                    "Failed to deserialize MultiFactorAuthenticationToken", ex);
        }
    }

    private SecurityUser deserializeSecurityUser(JsonNode detailsNode) {
        return new SecurityUser(
                readLong(detailsNode.get("userId")),
                readLong(detailsNode.get("activeTenantId")),
                readString(detailsNode.get("username")),
                readString(detailsNode.get("password")),
                readAuthorities(detailsNode.get("authorities")),
                readStringSet(detailsNode.get("roleCodes")),
                readBoolean(detailsNode.get("accountNonExpired"), false),
                readBoolean(detailsNode.get("accountNonLocked"), false),
                readBoolean(detailsNode.get("credentialsNonExpired"), false),
                readBoolean(detailsNode.get("enabled"), false),
                readString(detailsNode.get("permissionsVersion"))
        );
    }

    private Collection<? extends GrantedAuthority> readAuthorities(JsonNode authoritiesNode) {
        JsonNode normalizedNode = unwrapJavaUtilTypeWrapper(authoritiesNode);
        if (normalizedNode == null || !normalizedNode.isArray()) {
            return List.of();
        }
        List<GrantedAuthority> values = new ArrayList<>();
        for (JsonNode authorityNode : normalizedNode) {
            if (authorityNode.has("authority")) {
                values.add(new SimpleGrantedAuthority(authorityNode.get("authority").asText()));
            } else if (authorityNode.isTextual()) {
                values.add(new SimpleGrantedAuthority(authorityNode.asText()));
            }
        }
        return values;
    }

    private Set<String> readStringSet(JsonNode valuesNode) {
        JsonNode normalizedNode = unwrapJavaUtilTypeWrapper(valuesNode);
        if (normalizedNode == null || !normalizedNode.isArray()) {
            return Set.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode valueNode : normalizedNode) {
            String value = readString(valueNode);
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private JsonNode unwrapJavaUtilTypeWrapper(JsonNode node) {
        if (node == null || !node.isArray()) {
            return node;
        }
        if (node.size() == 2
                && node.get(0).isTextual()
                && node.get(0).asText().startsWith("java.util.")
                && node.get(1).isArray()) {
            return node.get(1);
        }
        return node;
    }

    private Long readLong(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        if (node.isTextual()) {
            try {
                return Long.parseLong(node.asText().trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid long value: " + node.asText(), ex);
            }
        }
        return null;
    }

    private String readString(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value != null ? value : null;
    }

    private boolean readBoolean(JsonNode node, boolean defaultValue) {
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isTextual()) {
            return Boolean.parseBoolean(node.asText());
        }
        return defaultValue;
    }
}
