package com.tiny.platform.infrastructure.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/**
 * 密码确认验证注解
 * 用于验证密码和确认密码是否一致
 */
@Documented
@Constraint(validatedBy = PasswordConfirmValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordConfirm {
    String message() default "密码和确认密码不一致";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
} 