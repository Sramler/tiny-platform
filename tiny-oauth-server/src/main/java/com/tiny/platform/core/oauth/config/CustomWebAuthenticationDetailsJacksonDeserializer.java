package com.tiny.platform.core.oauth.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

/**
 * CustomWebAuthenticationDetails 的 Jackson 反序列化器
 * 使用反射直接设置字段，避免调用父类的有参构造器
 */
public class CustomWebAuthenticationDetailsJacksonDeserializer
        extends JsonDeserializer<CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails> {

    @Override
    public CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails deserialize(
            JsonParser jsonParser,
            DeserializationContext deserializationContext) throws IOException {
        
        JsonNode jsonNode = jsonParser.getCodec().readTree(jsonParser);
        
        String remoteAddress = jsonNode.has("remoteAddress") ? jsonNode.get("remoteAddress").asText() : null;
        String sessionId = jsonNode.has("sessionId") ? jsonNode.get("sessionId").asText() : null;
        String authenticationProvider = jsonNode.has("authenticationProvider") ? jsonNode.get("authenticationProvider").asText() : null;
        String authenticationType = jsonNode.has("authenticationType") ? jsonNode.get("authenticationType").asText() : null;
        
        try {
            // 使用反射创建实例并设置所有字段
            java.lang.Class<?> clazz = CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails.class;
            
            // 调用无参构造器
            java.lang.reflect.Constructor<?> constructor = clazz.getDeclaredConstructor();
            constructor.setAccessible(true);
            CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails details = 
                (CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails) constructor.newInstance();
            
            // 通过反射设置本类字段
            java.lang.reflect.Field field = clazz.getDeclaredField("authenticationProvider");
            field.setAccessible(true);
            field.set(details, authenticationProvider);
            
            field = clazz.getDeclaredField("authenticationType");
            field.setAccessible(true);
            field.set(details, authenticationType);
            
            // 通过反射设置父类字段
            Class<?> superClass = clazz.getSuperclass();
            field = superClass.getDeclaredField("remoteAddress");
            field.setAccessible(true);
            field.set(details, remoteAddress != null ? remoteAddress : "unknown");
            
            field = superClass.getDeclaredField("sessionId");
            field.setAccessible(true);
            field.set(details, sessionId);
            
            return details;
        } catch (Exception e) {
            throw new IOException("Failed to deserialize CustomWebAuthenticationDetails", e);
        }
    }
}
