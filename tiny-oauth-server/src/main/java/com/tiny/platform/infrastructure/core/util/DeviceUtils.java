package com.tiny.platform.infrastructure.core.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 设备信息工具类
 */
public class DeviceUtils {

    /**
     * 从 User-Agent 请求头中提取设备信息
     * 返回简化的设备描述（操作系统 + 浏览器）
     */
    public static String getDeviceInfo(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }

        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null || userAgent.isEmpty()) {
            return "unknown";
        }

        // 转换为小写便于匹配
        String ua = userAgent.toLowerCase();

        // 检测操作系统
        String os = "Unknown OS";
        if (ua.contains("windows")) {
            if (ua.contains("windows nt 10.0")) {
                os = "Windows 10/11";
            } else if (ua.contains("windows nt 6.3")) {
                os = "Windows 8.1";
            } else if (ua.contains("windows nt 6.2")) {
                os = "Windows 8";
            } else if (ua.contains("windows nt 6.1")) {
                os = "Windows 7";
            } else {
                os = "Windows";
            }
        } else if (ua.contains("mac os x") || ua.contains("macintosh")) {
            os = "macOS";
        } else if (ua.contains("linux")) {
            os = "Linux";
        } else if (ua.contains("android")) {
            os = "Android";
        } else if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod")) {
            os = "iOS";
        }

        // 检测浏览器
        String browser = "Unknown Browser";
        if (ua.contains("edg")) {
            browser = "Edge";
        } else if (ua.contains("chrome") && !ua.contains("edg")) {
            browser = "Chrome";
        } else if (ua.contains("safari") && !ua.contains("chrome")) {
            browser = "Safari";
        } else if (ua.contains("firefox")) {
            browser = "Firefox";
        } else if (ua.contains("opera") || ua.contains("opr")) {
            browser = "Opera";
        } else if (ua.contains("msie") || ua.contains("trident")) {
            browser = "IE";
        }

        return os + " / " + browser;
    }
}
