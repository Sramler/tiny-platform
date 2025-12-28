package com.tiny.platform.infrastructure.core.exception.util;

/**
 * 异常工具类
 * 
 * <p>提供异常处理的常用工具方法，包括：</p>
 * <ul>
 *   <li>获取异常详情信息</li>
 *   <li>获取异常的根原因</li>
 *   <li>获取异常堆栈信息</li>
 *   <li>判断是否为业务异常</li>
 * </ul>
 * 
 * <p>使用示例：</p>
 * <pre>
 * // 获取异常详情
 * String detail = ExceptionUtils.getExceptionDetail(exception);
 * 
 * // 获取根原因
 * Throwable rootCause = ExceptionUtils.getRootCause(exception);
 * 
 * // 判断是否为业务异常
 * if (ExceptionUtils.isBusinessException(exception)) {
 *     // 可以安全地向客户端暴露详细信息
 * }
 * </pre>
 * 
 * @author Tiny Platform
 * @since 1.0.0
 */
public class ExceptionUtils {
    
    /**
     * 获取异常详情信息
     * 
     * <p>优先返回异常消息，如果没有消息则返回类名。</p>
     * 
     * @param ex 异常
     * @return 异常详情，如果异常为 null 则返回 "未知异常"
     */
    public static String getExceptionDetail(Throwable ex) {
        if (ex == null) {
            return "未知异常";
        }
        
        // 优先返回异常消息
        if (ex.getMessage() != null && !ex.getMessage().isEmpty()) {
            return ex.getMessage();
        }
        
        // 如果没有消息，返回类名
        return ex.getClass().getSimpleName();
    }
    
    /**
     * 获取异常的根原因
     * 
     * <p>递归查找异常链中的根原因（最底层的 cause）。</p>
     * 
     * @param ex 异常
     * @return 根原因，如果异常为 null 则返回 null
     */
    public static Throwable getRootCause(Throwable ex) {
        if (ex == null) {
            return null;
        }
        
        Throwable cause = ex.getCause();
        if (cause == null || cause == ex) {
            return ex;
        }
        
        return getRootCause(cause);
    }
    
    /**
     * 获取异常堆栈信息（用于日志）
     * 
     * <p>将异常的完整堆栈信息转换为字符串，用于日志记录。</p>
     * 
     * @param ex 异常
     * @return 堆栈信息字符串，如果异常为 null 则返回空字符串
     */
    public static String getStackTrace(Throwable ex) {
        if (ex == null) {
            return "";
        }
        
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }
    
    /**
     * 判断是否为业务异常（可向客户端暴露详细信息的异常）
     * 
     * <p>业务异常是指可以安全地向客户端暴露详细信息的异常，
     * 通常包括参数验证失败、业务规则违反等情况。</p>
     * 
     * @param ex 异常
     * @return true 表示是业务异常，可以安全地向客户端暴露详细信息
     */
    public static boolean isBusinessException(Throwable ex) {
        if (ex == null) {
            return false;
        }
        
        // 使用 instanceof 判断，更准确
        if (ex instanceof com.tiny.platform.infrastructure.core.exception.exception.BusinessException) {
            return true;
        }
        if (ex instanceof IllegalArgumentException) {
            return true;
        }
        if (ex instanceof IllegalStateException) {
            return true;
        }
        
        // 通过类名判断其他业务异常（向后兼容）
        String className = ex.getClass().getName();
        return className.contains("BusinessException") ||
               className.contains("ValidationException");
    }
}

