package com.tiny.platform.core.oauth.config.jackson;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.springframework.security.jackson2.SecurityJackson2Modules;

import java.io.IOException;
import java.lang.reflect.Constructor;

/**
 * 自定义 TypeIdResolver，扩展 Spring Security 的 AllowlistTypeIdResolver 以支持 Long 类型。
 * <p>
 * 此解析器包装了 Spring Security 的 AllowlistTypeIdResolver，并额外允许 {@link Long} 类型的反序列化。
 *
 * @since 1.0.0
 */
public class LongTypeIdResolver implements TypeIdResolver {

    private final TypeIdResolver delegate;
    private final JavaType baseType;

    public LongTypeIdResolver(JavaType baseType) {
        this.baseType = baseType;
        this.delegate = createDelegateResolver(baseType);
    }

    /**
     * 创建 Spring Security 的 AllowlistTypeIdResolver 作为委托。
     */
    private TypeIdResolver createDelegateResolver(JavaType baseType) {
        try {
            // 尝试创建 Spring Security 的 AllowlistTypeIdResolver
            Class<?> securityModulesClass = SecurityJackson2Modules.class;
            Class<?>[] innerClasses = securityModulesClass.getDeclaredClasses();
            
            for (Class<?> innerClass : innerClasses) {
                if ("AllowlistTypeIdResolver".equals(innerClass.getSimpleName())) {
                    Constructor<?> constructor = innerClass.getDeclaredConstructor(JavaType.class);
                    constructor.setAccessible(true);
                    return (TypeIdResolver) constructor.newInstance(baseType);
                }
            }
        } catch (Exception e) {
            // 如果无法创建委托，返回 null（会在 typeFromId 中处理）
            System.err.println("Warning: Could not create delegate TypeIdResolver: " + e.getMessage());
        }
        return null;
    }

    @Override
    public void init(JavaType baseType) {
        if (delegate != null) {
            delegate.init(baseType);
        }
    }

    @Override
    public String idFromValue(Object value) {
        return delegate != null ? delegate.idFromValue(value) : value.getClass().getName();
    }

    @Override
    public String idFromValueAndType(Object value, Class<?> suggestedType) {
        return delegate != null ? delegate.idFromValueAndType(value, suggestedType) : suggestedType.getName();
    }

    @Override
    public String idFromBaseType() {
        return delegate != null ? delegate.idFromBaseType() : baseType.getRawClass().getName();
    }

    @Override
    public JavaType typeFromId(DatabindContext context, String id) throws IOException {
        // 如果是 Long 类型，直接返回
        if ("java.lang.Long".equals(id) || Long.class.getName().equals(id)) {
            return TypeFactory.defaultInstance().constructType(Long.class);
        }
        
        // 否则使用委托解析器
        if (delegate != null) {
            try {
                return delegate.typeFromId(context, id);
            } catch (IllegalArgumentException e) {
                // 如果委托解析器拒绝该类型，但对于 Long 我们已经处理了
                // 对于其他类型，重新抛出异常
                if (!id.contains("Long")) {
                    throw e;
                }
            }
        }
        
        // 如果没有委托解析器，尝试直接构造类型
        try {
            Class<?> clazz = Class.forName(id);
            return TypeFactory.defaultInstance().constructType(clazz);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Type id '" + id + "' not recognized", e);
        }
    }

    @Override
    public String getDescForKnownTypeIds() {
        return delegate != null ? delegate.getDescForKnownTypeIds() : null;
    }

    @Override
    public JsonTypeInfo.Id getMechanism() {
        return delegate != null ? delegate.getMechanism() : JsonTypeInfo.Id.CLASS;
    }
}
