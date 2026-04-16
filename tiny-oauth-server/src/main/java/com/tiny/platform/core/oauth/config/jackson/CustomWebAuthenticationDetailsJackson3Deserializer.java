package com.tiny.platform.core.oauth.config.jackson;

import com.tiny.platform.core.oauth.config.CustomWebAuthenticationDetailsSource;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Jackson 3 deserializer for {@link CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails}.
 */
public class CustomWebAuthenticationDetailsJackson3Deserializer
        extends StdDeserializer<CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails> {

    public CustomWebAuthenticationDetailsJackson3Deserializer() {
        super(CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails.class);
    }

    @Override
    public CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails deserialize(
            JsonParser jsonParser,
            DeserializationContext deserializationContext) throws JacksonException {
        JsonNode jsonNode = deserializationContext.readTree(jsonParser);

        String remoteAddress = jsonNode.has("remoteAddress") ? jsonNode.get("remoteAddress").asText() : null;
        String sessionId = jsonNode.has("sessionId") ? jsonNode.get("sessionId").asText() : null;
        String authenticationProvider = jsonNode.has("authenticationProvider")
                ? jsonNode.get("authenticationProvider").asText() : null;
        String authenticationType = jsonNode.has("authenticationType")
                ? jsonNode.get("authenticationType").asText() : null;

        try {
            Class<?> clazz = CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails.class;
            java.lang.reflect.Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails details =
                    (CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails) constructor.newInstance();

            java.lang.reflect.Field field = clazz.getDeclaredField("authenticationProvider");
            field.setAccessible(true);
            field.set(details, authenticationProvider);

            field = clazz.getDeclaredField("authenticationType");
            field.setAccessible(true);
            field.set(details, authenticationType);

            Class<?> superClass = clazz.getSuperclass();
            field = superClass.getDeclaredField("remoteAddress");
            field.setAccessible(true);
            field.set(details, remoteAddress != null ? remoteAddress : "unknown");

            field = superClass.getDeclaredField("sessionId");
            field.setAccessible(true);
            field.set(details, sessionId);

            return details;
        } catch (Exception ex) {
            throw DatabindException.from(deserializationContext,
                    "Failed to deserialize CustomWebAuthenticationDetails", ex);
        }
    }
}
