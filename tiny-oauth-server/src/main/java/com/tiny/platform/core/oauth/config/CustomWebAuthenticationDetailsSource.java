package com.tiny.platform.core.oauth.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import com.fasterxml.jackson.annotation.JsonTypeName;

/**
 * 自定义 WebAuthenticationDetailsSource
 * 用于从登录请求中提取额外参数：authenticationProvider 和 authenticationType
 */
public class CustomWebAuthenticationDetailsSource
        implements AuthenticationDetailsSource<HttpServletRequest, WebAuthenticationDetails> {

    @Override
    public WebAuthenticationDetails buildDetails(HttpServletRequest request) {
        return new CustomWebAuthenticationDetails(request);
    }

    /**
     * 自定义的 WebAuthenticationDetails
     * 包含 authenticationProvider (LOCAL) 和 authenticationType (PASSWORD)
     * 注意：此类被序列化到 OAuth2 授权表，需要兼容 Jackson 反序列化
     */
    @JsonTypeName("customWebAuthenticationDetails")
    public static class CustomWebAuthenticationDetails extends WebAuthenticationDetails {
        
        private String authenticationProvider;  // 认证提供者，如 LOCAL
        private String authenticationType;      // 认证类型，如 PASSWORD

        /**
         * 从请求构建（首次登录时使用）
         */
        public CustomWebAuthenticationDetails(HttpServletRequest request) {
            super(request);
            
            // 从请求参数中提取额外信息
            this.authenticationProvider = request.getParameter("authenticationProvider");
            this.authenticationType = request.getParameter("authenticationType");
        }

        /**
         * Jackson 反序列化构造器（由反序列化器通过反射访问）
         * 用于从 OAuth2 授权表中读取已存储的 Authentication 对象
         * 此构造器创建临时的 mock request 来满足父类要求
         */
        private CustomWebAuthenticationDetails() {
            // 创建一个最小的 mock request 来调用父类构造器
            super(createMinimalRequest());
        }
        
        /**
         * 创建一个最小的 mock request 对象
         */
        private static HttpServletRequest createMinimalRequest() {
            return new jakarta.servlet.http.HttpServletRequest() {
                @Override
                public String getRemoteAddr() {
                    return "127.0.0.1"; // 默认 IP
                }
                
                @Override
                public jakarta.servlet.http.HttpSession getSession(boolean create) {
                    return null;
                }
                
                // 实现其他必需的方法（全部返回 null 或默认值）
                @Override
                public Object getAttribute(String name) { return null; }
                @Override
                public java.util.Enumeration<String> getAttributeNames() { return java.util.Collections.emptyEnumeration(); }
                @Override
                public String getCharacterEncoding() { return null; }
                @Override
                public void setCharacterEncoding(String env) {}
                @Override
                public int getContentLength() { return 0; }
                @Override
                public long getContentLengthLong() { return 0; }
                @Override
                public String getContentType() { return null; }
                @Override
                public jakarta.servlet.ServletInputStream getInputStream() { return null; }
                @Override
                public String getParameter(String name) { return null; }
                @Override
                public java.util.Enumeration<String> getParameterNames() { return java.util.Collections.emptyEnumeration(); }
                @Override
                public String[] getParameterValues(String name) { return null; }
                @Override
                public java.util.Map<String, String[]> getParameterMap() { return java.util.Collections.emptyMap(); }
                @Override
                public String getProtocol() { return null; }
                @Override
                public String getScheme() { return null; }
                @Override
                public String getServerName() { return null; }
                @Override
                public int getServerPort() { return 0; }
                @Override
                public java.io.BufferedReader getReader() { return null; }
                @Override
                public String getRemoteHost() { return null; }
                @Override
                public void setAttribute(String name, Object o) {}
                @Override
                public void removeAttribute(String name) {}
                @Override
                public java.util.Locale getLocale() { return null; }
                @Override
                public java.util.Enumeration<java.util.Locale> getLocales() { return java.util.Collections.emptyEnumeration(); }
                @Override
                public boolean isSecure() { return false; }
                @Override
                public jakarta.servlet.RequestDispatcher getRequestDispatcher(String path) { return null; }
                @Override
                public int getRemotePort() { return 0; }
                @Override
                public String getLocalName() { return null; }
                @Override
                public String getLocalAddr() { return null; }
                @Override
                public int getLocalPort() { return 0; }
                @Override
                public jakarta.servlet.ServletContext getServletContext() { return null; }
                @Override
                public jakarta.servlet.AsyncContext startAsync() { return null; }
                @Override
                public jakarta.servlet.AsyncContext startAsync(jakarta.servlet.ServletRequest servletRequest, jakarta.servlet.ServletResponse servletResponse) { return null; }
                @Override
                public boolean isAsyncStarted() { return false; }
                @Override
                public boolean isAsyncSupported() { return false; }
                @Override
                public jakarta.servlet.AsyncContext getAsyncContext() { return null; }
                @Override
                public jakarta.servlet.DispatcherType getDispatcherType() { return null; }
                @Override
                public jakarta.servlet.http.Cookie[] getCookies() { return null; }
                @Override
                public long getDateHeader(String name) { return 0; }
                @Override
                public String getHeader(String name) { return null; }
                @Override
                public java.util.Enumeration<String> getHeaders(String name) { return java.util.Collections.emptyEnumeration(); }
                @Override
                public java.util.Enumeration<String> getHeaderNames() { return java.util.Collections.emptyEnumeration(); }
                @Override
                public int getIntHeader(String name) { return 0; }
                @Override
                public String getMethod() { return null; }
                @Override
                public String getPathInfo() { return null; }
                @Override
                public String getPathTranslated() { return null; }
                @Override
                public String getContextPath() { return null; }
                @Override
                public String getQueryString() { return null; }
                @Override
                public String getRemoteUser() { return null; }
                @Override
                public boolean isUserInRole(String role) { return false; }
                @Override
                public java.security.Principal getUserPrincipal() { return null; }
                @Override
                public String getRequestedSessionId() { return null; }
                @Override
                public String getRequestURI() { return null; }
                @Override
                public StringBuffer getRequestURL() { return null; }
                @Override
                public String getServletPath() { return null; }
                @Override
                public jakarta.servlet.http.HttpSession getSession() { return null; }
                @Override
                public boolean isRequestedSessionIdValid() { return false; }
                @Override
                public boolean isRequestedSessionIdFromCookie() { return false; }
                @Override
                public boolean isRequestedSessionIdFromURL() { return false; }
                @Override
                public boolean authenticate(jakarta.servlet.http.HttpServletResponse response) { return false; }
                @Override
                public void login(String username, String password) {}
                @Override
                public void logout() {}
                @Override
                public java.util.Collection<jakarta.servlet.http.Part> getParts() { return null; }
                @Override
                public jakarta.servlet.http.Part getPart(String name) { return null; }
                
                // 实现 ServletRequest 新方法
                @Override
                public String getRequestId() { return null; }
                @Override
                public String getProtocolRequestId() { return null; }
                @Override
                public jakarta.servlet.ServletConnection getServletConnection() { return null; }
                
                // 实现 HttpServletRequest 新方法
                @Override
                public String getAuthType() { return null; }
                @Override
                public String changeSessionId() { return null; }
                @Override
                public <T extends jakarta.servlet.http.HttpUpgradeHandler> T upgrade(Class<T> httpUpgradeHandlerClass) { return null; }
            };
        }
        
        /**
         * 设置认证提供者（用于反序列化）
         */
        public void setAuthenticationProvider(String authenticationProvider) {
            this.authenticationProvider = authenticationProvider;
        }
        
        /**
         * 设置认证类型（用于反序列化）
         */
        public void setAuthenticationType(String authenticationType) {
            this.authenticationType = authenticationType;
        }

        public String getAuthenticationProvider() {
            return authenticationProvider;
        }

        public String getAuthenticationType() {
            return authenticationType;
        }

        @Override
        public String toString() {
            return "CustomWebAuthenticationDetails{" +
                    "authenticationProvider='" + authenticationProvider + '\'' +
                    ", authenticationType='" + authenticationType + '\'' +
                    ", sessionId='" + getSessionId() + '\'' +
                    ", remoteAddress='" + getRemoteAddress() + '\'' +
                    '}';
        }
    }
}
