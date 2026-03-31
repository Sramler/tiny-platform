package com.tiny.platform.infrastructure.auth.datascope.framework;

import java.lang.annotation.*;

/**
 * 标注在 Service 方法上，声明该方法需要数据范围过滤。
 *
 * <p>使用示例：</p>
 * <pre>
 * {@code @DataScope(module = "user", accessType = "READ")}
 * public Page<User> listUsers(Pageable pageable) {
 *     // DataScopeAspect 会在方法执行前解析数据范围，
 *     // 方法内通过 DataScopeContext.get() 获取 ResolvedDataScope，
 *     // 然后使用 DataScopeSpecification.apply() 组合查询条件。
 * }
 * </pre>
 *
 * <p>如果用户没有配置数据范围规则，默认按最小权限原则返回 SELF。</p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    /**
     * 业务模块标识（如 "user", "scheduling", "dict"）。
     */
    String module();

    /**
     * 访问类型：READ（默认）或 WRITE。
     */
    String accessType() default "READ";
}
