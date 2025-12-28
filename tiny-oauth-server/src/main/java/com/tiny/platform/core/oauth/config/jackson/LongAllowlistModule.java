package com.tiny.platform.core.oauth.config.jackson;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.jackson2.SecurityJackson2Modules;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 自定义 Jackson 模块，用于将 {@link Long} 添加到 Spring Security 的 allowlist 中。
 * <p>
 * <b>问题背景</b>：
 * Spring Security 使用 allowlist 机制限制可反序列化的类型，以防止反序列化攻击。
 * 默认情况下，{@code java.lang.Long} 不在 allowlist 中，这会导致在反序列化
 * OAuth2 授权数据时抛出 {@link IllegalArgumentException}。
 * <p>
 * <b>解决方案</b>：
 * 此模块通过反射访问 Spring Security 内部的静态 allowlist Set（存储在
 * {@code SecurityJackson2Modules$AllowlistTypeIdResolver.ALLOWLIST_CLASS_NAMES}），
 * 并使用 {@code Unsafe} 修改 final 字段，添加 {@code "java.lang.Long"} 到 allowlist。
 * <p>
 * <b>执行时机</b>：
 * 在模块注册到 ObjectMapper 时，通过 {@link #setupModule(Module.SetupContext)} 自动执行。
 * <p>
 * <b>注意事项</b>：
 * <ul>
 *   <li>使用 Unsafe 修改 final 字段，可能在未来的 Java 版本中失效</li>
 *   <li>如果 Spring Security 内部实现改变，可能需要调整代码</li>
 *   <li>Long 是安全的 Java 基础类型，添加到 allowlist 是安全的</li>
 * </ul>
 * <p>
 * <b>关于官方推荐</b>：
 * 此方案不符合 Spring Security 官方推荐的最佳实践（官方推荐使用注解或 Mixin），
 * 但由于以下原因，这是当前约束下的实用解决方案：
 * <ul>
 *   <li>Long 是 Java 基础类型，无法使用 {@code @JsonTypeInfo} 注解</li>
 *   <li>OAuth2 授权数据由 Spring Security 内部序列化，无法控制序列化格式</li>
 *   <li>Spring Security 未提供公开的 API 来扩展 allowlist</li>
 *   <li>官方在相关 Issue 中表示不会将 Long 加入默认 allowlist</li>
 * </ul>
 *
 * @see <a href="https://github.com/spring-projects/spring-security/issues/4370">Spring Security Issue #4370</a>
 * @see <a href="https://github.com/spring-projects/spring-security/issues/12294">Spring Security Issue #12294</a>
 * @since 1.0.0
 */
public class LongAllowlistModule extends SimpleModule {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(LongAllowlistModule.class);
    private static final String LONG_CLASS_NAME = "java.lang.Long";
    private static volatile boolean allowlistModified = false;

    /**
     * 构造函数。
     */
    public LongAllowlistModule() {
        super("LongAllowlistModule");
    }
    
    @Override
    public void setupModule(Module.SetupContext context) {
        super.setupModule(context);
        // 在模块设置时立即修改 allowlist
        modifyAllowlist();
    }

    /**
     * 修改 Spring Security 的 allowlist，添加 Long 类名。
     * 使用双重检查锁定确保只执行一次。
     */
    private static void modifyAllowlist() {
        if (allowlistModified) {
            return;
        }
        
        synchronized (LongAllowlistModule.class) {
            if (allowlistModified) {
                return;
            }
            
            log.info("开始修改 Spring Security allowlist，添加 java.lang.Long...");
            
            try {
                Class<?> securityModulesClass = SecurityJackson2Modules.class;
                Class<?>[] innerClasses = securityModulesClass.getDeclaredClasses();
                
                for (Class<?> innerClass : innerClasses) {
                    if ("AllowlistTypeIdResolver".equals(innerClass.getSimpleName())) {
                        Field[] fields = innerClass.getDeclaredFields();
                        
                        for (Field field : fields) {
                            if (Set.class.isAssignableFrom(field.getType()) && Modifier.isStatic(field.getModifiers())) {
                                if (tryModifySetField(field)) {
                                    allowlistModified = true;
                                    log.info("成功将 java.lang.Long 添加到 Spring Security allowlist");
                                    return;
                                }
                            }
                        }
                    }
                }
                
                log.warn("未能找到或修改 allowlist Set，Long 类型反序列化可能失败");
                
            } catch (Exception e) {
                log.error("修改 allowlist 时出错", e);
            }
        }
    }
    
    /**
     * 尝试修改给定的 Set 字段，添加 Long 类名。
     * <p>
     * 注意：allowlist 存储的是类名字符串（Set&lt;String&gt;），不是 Class 对象。
     *
     * @param field 要修改的静态 Set 字段
     * @return 是否成功修改
     */
    @SuppressWarnings("unchecked")
    private static boolean tryModifySetField(Field field) {
        try {
            field.setAccessible(true);
            Object currentValue = field.get(null);
            
            if (!(currentValue instanceof Set)) {
                log.debug("字段 {} 不是 Set 类型: {}", field.getName(), 
                    currentValue != null ? currentValue.getClass().getName() : "null");
                return false;
            }
            
            Set<?> currentSet = (Set<?>) currentValue;
            
            // 检查 Set 的元素类型
            if (currentSet.isEmpty()) {
                log.warn("allowlist Set 为空，无法确定元素类型");
                return false;
            }
            
            Object firstElement = currentSet.iterator().next();
            boolean isStringSet = firstElement instanceof String;
            
            // 检查是否已包含 Long
            if (isStringSet && currentSet.contains(LONG_CLASS_NAME)) {
                log.debug("java.lang.Long 已在 allowlist 中");
                return true;
            } else if (!isStringSet && currentSet.contains(Long.class)) {
                log.debug("Long.class 已在 allowlist 中");
                return true;
            }
            
            // 尝试直接添加
            try {
                if (isStringSet) {
                    ((Set<String>) currentSet).add(LONG_CLASS_NAME);
                    log.debug("直接添加 {} 成功", LONG_CLASS_NAME);
                } else {
                    ((Set<Class<?>>) currentSet).add(Long.class);
                    log.debug("直接添加 Long.class 成功");
                }
                return true;
            } catch (UnsupportedOperationException e) {
                log.debug("Set 不可变，尝试修改底层集合或使用 Unsafe...");
                
                // 先尝试修改底层集合（更安全的方法）
                if (tryAlternativeModification(currentSet, isStringSet)) {
                    return true;
                }
                
                // 如果修改底层集合失败，使用 Unsafe
                return modifyFinalFieldWithUnsafe(field, currentSet, isStringSet);
            }
        } catch (Exception e) {
            log.error("修改字段 {} 时出错", field.getName(), e);
        }
        return false;
    }
    
    /**
     * 使用 Unsafe 来修改 final 字段（仅在必要时使用）。
     * <p>
     * 注意：此方法使用 {@code sun.misc.Unsafe}，在 Java 18+ 中相关方法已弃用但仍可用。
     * 如果未来版本不再支持，可能需要寻找替代方案。
     *
     * @param field 要修改的静态 final 字段
     * @param currentSet 当前的 allowlist Set
     * @param isStringSet 是否为字符串 Set
     * @return 是否成功修改
     */
    @SuppressWarnings("unchecked")
    private static boolean modifyFinalFieldWithUnsafe(Field field, Set<?> currentSet, boolean isStringSet) {
        try {
            // 创建新的可变集合并添加 Long
            Set<?> newSet;
            if (isStringSet) {
                Set<String> stringSet = new HashSet<>((Set<String>) currentSet);
                stringSet.add(LONG_CLASS_NAME);
                newSet = Collections.unmodifiableSet(stringSet);
            } else {
                Set<Class<?>> classSet = new HashSet<>((Set<Class<?>>) currentSet);
                classSet.add(Long.class);
                newSet = Collections.unmodifiableSet(classSet);
            }
            
            // 使用反射获取 Unsafe 实例
            // 注意：Unsafe 是内部 API，在 Java 18+ 中相关方法已弃用，但这是修改 final 字段的唯一方法
            sun.misc.Unsafe unsafe = getUnsafeInstance();
            
            // 获取字段的偏移量（注意：这些方法在 Java 18+ 中已弃用，但仍可用）
            // 使用 @SuppressWarnings 抑制弃用警告，因为这是必要的
            @SuppressWarnings("removal")
            long offset = unsafe.staticFieldOffset(field);
            @SuppressWarnings("removal")
            Object base = unsafe.staticFieldBase(field);
            
            // 使用 Unsafe 设置 final 字段的值
            unsafe.putObject(base, offset, newSet);
            
            // 验证修改是否成功
            Object verifyValue = field.get(null);
            if (verifyValue instanceof Set) {
                Set<?> verifySet = (Set<?>) verifyValue;
                boolean contains = isStringSet ? verifySet.contains(LONG_CLASS_NAME) : verifySet.contains(Long.class);
                if (contains) {
                    log.debug("使用 Unsafe 成功替换 final 字段，新 Set 大小: {}", verifySet.size());
                    return true;
                }
            }
            log.warn("使用 Unsafe 修改后验证失败");
            return false;
        } catch (Exception e) {
            log.error("使用 Unsafe 修改 final 字段失败", e);
            return false;
        }
    }
    
    /**
     * 尝试通过修改不可变集合的底层集合来添加 Long。
     * <p>
     * Collections.unmodifiableSet 内部使用一个名为 "c" 的字段存储底层集合。
     * 如果底层集合是可变的，直接修改它比使用 Unsafe 更安全。
     *
     * @param currentSet 当前的不可变 Set
     * @param isStringSet 是否为字符串 Set
     * @return 是否成功修改
     */
    @SuppressWarnings("unchecked")
    private static boolean tryAlternativeModification(Set<?> currentSet, boolean isStringSet) {
        try {
            // Collections.unmodifiableSet 内部使用一个名为 "c" 的字段存储底层集合
            Field delegateField = currentSet.getClass().getDeclaredField("c");
            delegateField.setAccessible(true);
            Set<?> delegate = (Set<?>) delegateField.get(currentSet);
            
            // 检查底层集合是否可变
            try {
                if (isStringSet) {
                    ((Set<String>) delegate).add(LONG_CLASS_NAME);
                } else {
                    ((Set<Class<?>>) delegate).add(Long.class);
                }
                log.debug("通过修改底层集合成功添加");
                return true;
            } catch (UnsupportedOperationException e) {
                log.debug("底层集合也是不可变的，将使用 Unsafe 方法");
                // 如果底层集合也是不可变的，尝试找到真正的可变集合
                return tryFindMutableDelegate(delegate, isStringSet);
            }
        } catch (NoSuchFieldException e) {
            // 某些不可变集合的实现可能没有标准的 'c' 字段，这是正常的
            // 尝试其他可能的字段名
            String[] possibleFieldNames = {"m", "delegate", "backingSet", "set"};
            for (String fieldName : possibleFieldNames) {
                try {
                    Field altField = currentSet.getClass().getDeclaredField(fieldName);
                    altField.setAccessible(true);
                    Object delegate = altField.get(currentSet);
                    if (delegate instanceof Set) {
                        if (isStringSet) {
                            ((Set<String>) delegate).add(LONG_CLASS_NAME);
                        } else {
                            ((Set<Class<?>>) delegate).add(Long.class);
                        }
                        log.debug("通过字段 '{}' 成功添加", fieldName);
                        return true;
                    }
                } catch (Exception ignored) {
                    // 继续尝试下一个字段名
                }
            }
            return false;
        } catch (Exception e) {
            log.debug("替代方法失败: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * 尝试在嵌套的不可变集合中找到可变的底层集合。
     *
     * @param set 要检查的 Set
     * @param isStringSet 是否为字符串 Set
     * @return 是否成功修改
     */
    @SuppressWarnings("unchecked")
    private static boolean tryFindMutableDelegate(Set<?> set, boolean isStringSet) {
        try {
            // 如果是 HashSet 或其他可变集合，直接添加
            if (set instanceof HashSet) {
                if (isStringSet) {
                    ((Set<String>) set).add(LONG_CLASS_NAME);
                } else {
                    ((Set<Class<?>>) set).add(Long.class);
                }
                log.debug("在 HashSet 中成功添加");
                return true;
            }
        } catch (Exception e) {
            log.debug("无法在嵌套集合中找到可变集合: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * 获取 Unsafe 实例。
     * <p>
     * 注意：Unsafe 是内部 API，在生产环境中使用需要谨慎。
     *
     * @return Unsafe 实例
     * @throws Exception 如果无法获取 Unsafe 实例
     */
    @SuppressWarnings("removal")
    private static sun.misc.Unsafe getUnsafeInstance() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (sun.misc.Unsafe) unsafeField.get(null);
    }
}
