package com.tiny.platform.core.oauth.config.jackson;

import com.tiny.platform.core.oauth.model.SecurityUser;
import com.tiny.platform.core.oauth.security.AuthenticationFactorAuthorities;
import com.tiny.platform.core.oauth.security.MultiFactorAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonToken;
import tools.jackson.core.type.WritableTypeId;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;

/**
 * Jackson 3 serializer for {@link MultiFactorAuthenticationToken}.
 */
public class MultiFactorAuthenticationTokenJackson3Serializer
        extends StdSerializer<MultiFactorAuthenticationToken> {

    public MultiFactorAuthenticationTokenJackson3Serializer() {
        super(MultiFactorAuthenticationToken.class);
    }

    @Override
    public void serialize(MultiFactorAuthenticationToken value,
                          JsonGenerator jsonGenerator,
                          SerializationContext serializers) throws JacksonException {
        jsonGenerator.writeStartObject();
        writeFields(value, jsonGenerator, serializers);
        jsonGenerator.writeEndObject();
    }

    @Override
    public void serializeWithType(MultiFactorAuthenticationToken value,
                                  JsonGenerator jsonGenerator,
                                  SerializationContext serializers,
                                  TypeSerializer typeSerializer) throws JacksonException {
        WritableTypeId typeId = typeSerializer.writeTypePrefix(
                jsonGenerator,
                serializers,
                typeSerializer.typeId(value, JsonToken.START_OBJECT)
        );
        writeFields(value, jsonGenerator, serializers);
        typeSerializer.writeTypeSuffix(jsonGenerator, serializers, typeId);
    }

    private void writeFields(MultiFactorAuthenticationToken value,
                             JsonGenerator jsonGenerator,
                             SerializationContext serializers) throws JacksonException {
        jsonGenerator.writeStringProperty("username", value.getUsername());
        jsonGenerator.writeStringProperty("provider", value.getProvider().name());

        Object credentials = value.getCredentials();
        if (credentials instanceof CharSequence charSequence) {
            jsonGenerator.writeStringProperty("credentials", charSequence.toString());
        } else {
            jsonGenerator.writeNullProperty("credentials");
        }

        jsonGenerator.writeArrayPropertyStart("completedFactors");
        for (MultiFactorAuthenticationToken.AuthenticationFactorType factor : value.getCompletedFactors()) {
            if (factor != null && factor != MultiFactorAuthenticationToken.AuthenticationFactorType.UNKNOWN) {
                jsonGenerator.writeString(factor.name());
            }
        }
        jsonGenerator.writeEndArray();

        jsonGenerator.writeBooleanProperty("authenticated", value.isAuthenticated());

        jsonGenerator.writeArrayPropertyStart("authorities");
        writeAuthorities(jsonGenerator, value.getAuthorities());
        jsonGenerator.writeEndArray();

        writeDetails(jsonGenerator, serializers, value.getDetails());
    }

    private void writeAuthorities(JsonGenerator jsonGenerator,
                                  Collection<? extends GrantedAuthority> authorities) throws JacksonException {
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
            jsonGenerator.writeStringProperty("authority", authorityValue);
            jsonGenerator.writeEndObject();
        }
    }

    private void writeDetails(JsonGenerator jsonGenerator,
                              SerializationContext serializers,
                              Object details) throws JacksonException {
        if (details == null) {
            jsonGenerator.writeNullProperty("details");
            return;
        }
        if (details instanceof SecurityUser securityUser) {
            jsonGenerator.writeName("details");
            writeSecurityUser(jsonGenerator, securityUser);
            return;
        }
        serializers.defaultSerializeProperty("details", details, jsonGenerator);
    }

    private void writeSecurityUser(JsonGenerator jsonGenerator, SecurityUser securityUser) throws JacksonException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringProperty("@type", "securityUser");

        if (securityUser.getUserId() != null) {
            jsonGenerator.writeStringProperty("userId", securityUser.getUserId().toString());
        } else {
            jsonGenerator.writeNullProperty("userId");
        }

        if (securityUser.getActiveTenantId() != null) {
            jsonGenerator.writeStringProperty("activeTenantId", securityUser.getActiveTenantId().toString());
        } else {
            jsonGenerator.writeNullProperty("activeTenantId");
        }

        jsonGenerator.writeStringProperty("username", securityUser.getUsername());
        jsonGenerator.writeStringProperty("password", securityUser.getPassword());

        jsonGenerator.writeArrayPropertyStart("authorities");
        for (GrantedAuthority authority : securityUser.getAuthorities()) {
            String authorityValue = authority == null ? null : authority.getAuthority();
            if (authorityValue == null || authorityValue.isBlank()) {
                continue;
            }
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStringProperty("authority", authorityValue);
            jsonGenerator.writeEndObject();
        }
        jsonGenerator.writeEndArray();

        Set<String> roleCodes = securityUser.getRoleCodes();
        jsonGenerator.writeArrayPropertyStart("roleCodes");
        for (String roleCode : roleCodes) {
            if (roleCode != null && !roleCode.isBlank()) {
                jsonGenerator.writeString(roleCode);
            }
        }
        jsonGenerator.writeEndArray();

        jsonGenerator.writeBooleanProperty("accountNonExpired", securityUser.isAccountNonExpired());
        jsonGenerator.writeBooleanProperty("accountNonLocked", securityUser.isAccountNonLocked());
        jsonGenerator.writeBooleanProperty("credentialsNonExpired", securityUser.isCredentialsNonExpired());
        jsonGenerator.writeBooleanProperty("enabled", securityUser.isEnabled());

        if (securityUser.getPermissionsVersion() != null) {
            jsonGenerator.writeStringProperty("permissionsVersion", securityUser.getPermissionsVersion());
        } else {
            jsonGenerator.writeNullProperty("permissionsVersion");
        }

        jsonGenerator.writeEndObject();
    }
}
