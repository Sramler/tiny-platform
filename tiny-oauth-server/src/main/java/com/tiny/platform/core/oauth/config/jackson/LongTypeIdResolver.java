package com.tiny.platform.core.oauth.config.jackson;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.TypeIdResolver;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.io.IOException;

/**
 * 兼容性 TypeIdResolver。
 *
 * <p>历史上这里包装过 Spring Security Jackson 2 的 allowlist resolver。迁移到
 * Jackson 3 后，运行时不再依赖该实现，但保留一个轻量 fallback 版本以兼容旧测试。</p>
 */
public class LongTypeIdResolver implements TypeIdResolver {

    private TypeIdResolver delegate;
    private final JavaType baseType;

    public LongTypeIdResolver(JavaType baseType) {
        this.baseType = baseType;
        this.delegate = null;
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
        if (Long.class.getName().equals(id)) {
            return TypeFactory.defaultInstance().constructType(Long.class);
        }
        if (delegate != null) {
            return delegate.typeFromId(context, id);
        }
        try {
            Class<?> clazz = Class.forName(id);
            return TypeFactory.defaultInstance().constructType(clazz);
        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException("Type id '" + id + "' not recognized", ex);
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
