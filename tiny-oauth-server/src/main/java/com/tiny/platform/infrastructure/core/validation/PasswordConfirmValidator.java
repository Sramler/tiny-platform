package com.tiny.platform.infrastructure.core.validation;

import com.tiny.platform.infrastructure.auth.user.dto.UserCreateUpdateDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * 密码确认验证器
 * 实现密码和确认密码的一致性验证
 */
public class PasswordConfirmValidator implements ConstraintValidator<PasswordConfirm, UserCreateUpdateDto> {

    @Override
    public void initialize(PasswordConfirm constraintAnnotation) {
        // 初始化方法，可以在这里进行一些初始化操作
    }

    @Override
    public boolean isValid(UserCreateUpdateDto userDto, ConstraintValidatorContext context) {
        // 判断是否为编辑模式
        boolean isEditMode = userDto.getId() != null;
        
        // 获取密码和确认密码
        String password = userDto.getPassword();
        String confirmPassword = userDto.getConfirmPassword();
        
        // 编辑模式下，如果密码为空，表示不修改密码，验证通过
        if (isEditMode && (password == null || password.trim().isEmpty())) {
            return true;
        }
        
        // 创建模式下，密码不能为空
        if (!isEditMode && (password == null || password.trim().isEmpty())) {
            addConstraintViolation(context, "创建用户时密码不能为空");
            return false;
        }
        
        // 如果密码不为空，检查确认密码
        if (password != null && !password.trim().isEmpty()) {
            // 检查密码长度
            if (password.length() < 6 || password.length() > 20) {
                addConstraintViolation(context, "密码长度必须在6-20个字符之间");
                return false;
            }
            
            // 检查确认密码是否为空
            if (confirmPassword == null || confirmPassword.trim().isEmpty()) {
                addConstraintViolation(context, "请输入确认密码");
                return false;
            }
            
            // 检查两次密码是否一致
            if (!password.equals(confirmPassword)) {
                addConstraintViolation(context, "两次输入的密码不一致");
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * 添加约束违反信息
     */
    private void addConstraintViolation(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
               .addConstraintViolation();
    }
} 