package com.tiny.platform.infrastructure.idempotent.sdk.annotation;

import com.tiny.platform.infrastructure.idempotent.core.strategy.IdempotentStrategy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等性注解
 * 
 * <p>用于标记需要保证幂等性的接口方法。被标记的方法在指定时间内（timeout）内，
 * 相同的请求参数（通过 key 表达式生成）只会执行一次。</p>
 * 
 * <p>使用示例：</p>
 * <pre>
 * {@code
 * @PostMapping("/create")
 * @Idempotent(key = "#user.id + ':' + #user.name", timeout = 60)
 * public ResponseEntity<User> createUser(@RequestBody User user) {
 *     // 业务逻辑
 * }
 * }
 * </pre>
 * 
 * @author Auto Generated
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    
    /**
     * 幂等性 key 的 SpEL 表达式
     * 
     * <p>支持从方法参数中提取值，例如：</p>
     * <ul>
     *   <li>{@code "#userId"} - 使用 userId 参数</li>
     *   <li>{@code "#user.id"} - 使用 user 对象的 id 属性</li>
     *   <li>{@code "#request.getHeader('X-Idempotency-Key')"} - 从请求头获取</li>
     *   <li>{@code "#userId + ':' + #orderId"} - 组合多个值</li>
     * </ul>
     * 
     * <p>如果不指定，将使用默认策略：</p>
     * <ul>
     *   <li>如果请求头包含 {@code X-Idempotency-Key}，则使用该值</li>
     *   <li>否则使用：{@code 方法名 + 参数值的MD5}</li>
     * </ul>
     * 
     * @return SpEL 表达式字符串
     */
    String key() default "";
    
    /**
     * 幂等性 token 的过期时间（秒）
     * 
     * <p>默认 300 秒（5分钟），表示在 300 秒内相同的请求会被视为重复请求。</p>
     * 
     * @return 过期时间（秒）
     */
    long timeout() default 300;
    
    /**
     * 重复请求时的提示信息
     * 
     * @return 提示信息
     */
    String message() default "请勿重复提交";
    
    /**
     * 失败策略：true 表示 fail-open（失败时继续执行），false 表示 fail-close（失败时抛出异常）
     * 
     * <p>默认 true，表示存储服务不可用时，仍然允许业务继续执行，避免阻塞业务。</p>
     * 
     * @return 失败策略
     */
    boolean failOpen() default true;
}

