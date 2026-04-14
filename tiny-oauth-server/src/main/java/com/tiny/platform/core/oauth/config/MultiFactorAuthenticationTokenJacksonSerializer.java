package com.tiny.platform.core.oauth.config;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.AuthenticationFactorAuthorities;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/**
 * MultiFactorAuthenticationToken 的 Jackson 序列化器。
 *
 * <p>OAuth2 授权码/静默换票会把 principal 存入 oauth2_authorization.attributes。
 * 默认序列化链不会可靠保留 details.SecurityUser，导致后续换票时 access token 丢失
 * userId / activeScopeType / authorities / permissions 等 claim。这里显式写出最小运行时上下文，
 * 与 {@link MultiFactorAuthenticationTokenJacksonDeserializer} 保持对称。</p>
 */
public class MultiFactorAuthenticationTokenJacksonSerializer
        extends JsonSerializer<MultiFactorAuthenticationToken> {

    @Override
    public void serialize(MultiFactorAuthenticationToken value,
                          JsonGenerator jsonGenerator,
                          SerializerProvider serializers) throws IOException {
        jsonGenerator.writeStartObject();
        writeFields(value, jsonGenerator, serializers);
        jsonGenerator.writeEndObject();
    }

    @Override
    public void serializeWithType(MultiFactorAuthenticationToken value,
                                  JsonGenerator jsonGenerator,
                                  SerializerProvider serializers,
                                  TypeSerializer typeSerializer) throws IOException {
        WritableTypeId typeId = typeSerializer.writeTypePrefix(
                jsonGenerator,
                typeSerializer.typeId(value, JsonToken.START_OBJECT)
        );
        writeFields(value, jsonGenerator, serializers);
        typeSerializer.writeTypeSuffix(jsonGenerator, typeId);
    }

    private void writeFields(MultiFactorAuthenticationToken value,
                             JsonGenerator jsonGenerator,
                             SerializerProvider serializers) throws IOException {
        jsonGenerator.writeStringField("username", value.getUsername());
        jsonGenerator.writeStringField("provider", value.getProvider().name());

        Object credentials = value.getCredentials();
        if (credentials instanceof CharSequence charSequence) {
            jsonGenerator.writeStringField("credentials", charSequence.toString());
        } else {
            jsonGenerator.writeNullField("credentials");
        }

        jsonGenerator.writeArrayFieldStart("completedFactors");
        for (MultiFactorAuthenticationToken.AuthenticationFactorType factor : value.getCompletedFactors()) {
            if (factor != null && factor != MultiFactorAuthenticationToken.AuthenticationFactorType.UNKNOWN) {
                jsonGenerator.writeString(factor.name());
            }
        }
        jsonGenerator.writeEndArray();

        jsonGenerator.writeBooleanField("authenticated", value.isAuthenticated());

        jsonGenerator.writeArrayFieldStart("authorities");
        writeAuthorities(jsonGenerator, value.getAuthorities());
        jsonGenerator.writeEndArray();

        writeDetails(jsonGenerator, serializers, value.getDetails());
    }

    private void writeAuthorities(JsonGenerator jsonGenerator,
                                  Collection<? extends GrantedAuthority> authorities) throws IOException {
        if (authorities == null) {
            return;
        }
        for (GrantedAuthority authority : authorities) {
            String authorityValue = authority == null ? null : authority.getAuthority();
            if (authorityValue == null || authorityValue.isBlank()) {
                continue;
            }
            if (AuthenticationFactorAuthorities.fromAuthority(authorityValue)
                    != MultiFactorAuthenticationToken.AuthenticationFactorType.UNKNOWN) {
                continue;
            }
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField("authority", authorityValue);
            jsonGenerator.writeEndObject();
        }
    }

    private void writeDetails(JsonGenerator jsonGenerator,
                              SerializerProvider serializers,
                              Object details) throws IOException {
        if (details == null) {
            jsonGenerator.writeNullField("details");
            return;
        }
        jsonGenerator.writeFieldName("details");
        if (details instanceof SecurityUser securityUser) {
            writeSecurityUser(jsonGenerator, securityUser);
            return;
        }
        serializers.defaultSerializeValue(details, jsonGenerator);
    }

    private void writeSecurityUser(JsonGenerator jsonGenerator, SecurityUser securityUser) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField("@type", "securityUser");

        if (securityUser.getUserId() != null) {
            jsonGenerator.writeStringField("userId", securityUser.getUserId().toString());
        } else {
            jsonGenerator.writeNullField("userId");
        }

        if (securityUser.getActiveTenantId() != null) {
            jsonGenerator.writeStringField("activeTenantId", securityUser.getActiveTenantId().toString());
        } else {
            jsonGenerator.writeNullField("activeTenantId");
        }

        jsonGenerator.writeStringField("username", securityUser.getUsername());
        jsonGenerator.writeStringField("password", securityUser.getPassword());

        jsonGenerator.writeArrayFieldStart("authorities");
        for (GrantedAuthority authority : securityUser.getAuthorities()) {
            String authorityValue = authority == null ? null : authority.getAuthority();
            if (authorityValue == null || authorityValue.isBlank()) {
                continue;
            }
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringField("authority", authorityValue);
            jsonGenerator.writeEndObject();
        }
        jsonGenerator.writeEndArray();

        Set<String> roleCodes = securityUser.getRoleCodes();
        jsonGenerator.writeArrayFieldStart("roleCodes");
        for (String roleCode : roleCodes) {
            if (roleCode != null && !roleCode.isBlank()) {
                jsonGenerator.writeString(roleCode);
            }
        }
        jsonGenerator.writeEndArray();

        jsonGenerator.writeBooleanField("accountNonExpired", securityUser.isAccountNonExpired());
        jsonGenerator.writeBooleanField("accountNonLocked", securityUser.isAccountNonLocked());
        jsonGenerator.writeBooleanField("credentialsNonExpired", securityUser.isCredentialsNonExpired());
        jsonGenerator.writeBooleanField("enabled", securityUser.isEnabled());

        if (securityUser.getPermissionsVersion() != null) {
            jsonGenerator.writeStringField("permissionsVersion", securityUser.getPermissionsVersion());
        } else {
            jsonGenerator.writeNullField("permissionsVersion");
        }

        jsonGenerator.writeEndObject();
    }
}
