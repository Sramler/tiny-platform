package com.tiny.platform.infrastructure.auth.datascope.framework;

/**
 * ThreadLocal 持有当前请求解析后的数据范围。
 *
 * <p>由 {@link DataScopeAspect} 在进入 {@code @DataScope} 方法前设置，
 * 在方法执行后清理。业务方法通过 {@link #get()} 获取当前数据范围，
 * 结合 {@link DataScopeSpecification} 组合查询条件。</p>
 */
public final class DataScopeContext {

    private static final ThreadLocal<ResolvedDataScope> SCOPE = new ThreadLocal<>();

    private DataScopeContext() {}

    public static void set(ResolvedDataScope scope) {
        SCOPE.set(scope);
    }

    /**
     * 获取当前线程的数据范围。
     *
     * @return 解析后的数据范围，如果不在 {@code @DataScope} 方法上下文内则返回 null
     */
    public static ResolvedDataScope get() {
        return SCOPE.get();
    }

    public static void clear() {
        SCOPE.remove();
    }
}
