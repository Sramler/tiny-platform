package com.tiny.platform.core.oauth.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpUpgradeHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class CustomWebAuthenticationDetailsCoverageTest {

    @Test
    void shouldBuildCustomWebAuthenticationDetailsFromRequest() {
        CustomWebAuthenticationDetailsSource source = new CustomWebAuthenticationDetailsSource();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.setParameter("authenticationProvider", "LOCAL");
        request.setParameter("authenticationType", "PASSWORD");
        request.getSession(true);

        var details = (CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails) source.buildDetails(request);

        assertThat(details.getRemoteAddress()).isEqualTo("10.0.0.8");
        assertThat(details.getSessionId()).isNotNull();
        assertThat(details.getAuthenticationProvider()).isEqualTo("LOCAL");
        assertThat(details.getAuthenticationType()).isEqualTo("PASSWORD");
        assertThat(details.toString()).contains("authenticationProvider='LOCAL'").contains("authenticationType='PASSWORD'");
    }

    @Test
    void shouldSupportPrivateNoArgConstructorAndSetters() throws Exception {
        Constructor<CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails> constructor =
                CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        var details = constructor.newInstance();
        details.setAuthenticationProvider("LDAP");
        details.setAuthenticationType("PASSWORD");

        assertThat(details.getRemoteAddress()).isEqualTo("127.0.0.1");
        assertThat(details.getSessionId()).isNull();
        assertThat(details.getAuthenticationProvider()).isEqualTo("LDAP");
        assertThat(details.getAuthenticationType()).isEqualTo("PASSWORD");
        assertThat(details.toString()).contains("remoteAddress='127.0.0.1'");
    }

    @Test
    void shouldExposeMinimalRequestDefaultImplementations() throws Exception {
        Method method = CustomWebAuthenticationDetailsSource.CustomWebAuthenticationDetails.class
                .getDeclaredMethod("createMinimalRequest");
        method.setAccessible(true);
        HttpServletRequest request = (HttpServletRequest) method.invoke(null);

        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(request.getRemoteAddr()).isEqualTo("127.0.0.1");
        assertThat(request.getSession(false)).isNull();
        assertThat(request.getSession()).isNull();
        assertThat(request.getAttribute("x")).isNull();
        assertThat(request.getAttributeNames().hasMoreElements()).isFalse();
        assertThat(request.getCharacterEncoding()).isNull();
        request.setCharacterEncoding("UTF-8");
        assertThat(request.getContentLength()).isZero();
        assertThat(request.getContentLengthLong()).isZero();
        assertThat(request.getContentType()).isNull();
        assertThat(request.getInputStream()).isNull();
        assertThat(request.getParameter("x")).isNull();
        assertThat(request.getParameterNames().hasMoreElements()).isFalse();
        assertThat(request.getParameterValues("x")).isNull();
        assertThat(request.getParameterMap()).isEmpty();
        assertThat(request.getProtocol()).isNull();
        assertThat(request.getScheme()).isNull();
        assertThat(request.getServerName()).isNull();
        assertThat(request.getServerPort()).isZero();
        assertThat(request.getReader()).isNull();
        assertThat(request.getRemoteHost()).isNull();
        request.setAttribute("x", 1);
        request.removeAttribute("x");
        assertThat(request.getLocale()).isNull();
        assertThat(request.getLocales().hasMoreElements()).isFalse();
        assertThat(request.isSecure()).isFalse();
        assertThat(request.getRequestDispatcher("/x")).isNull();
        assertThat(request.getRemotePort()).isZero();
        assertThat(request.getLocalName()).isNull();
        assertThat(request.getLocalAddr()).isNull();
        assertThat(request.getLocalPort()).isZero();
        assertThat(request.getServletContext()).isNull();
        assertThat(request.startAsync()).isNull();
        assertThat(request.startAsync(null, null)).isNull();
        assertThat(request.isAsyncStarted()).isFalse();
        assertThat(request.isAsyncSupported()).isFalse();
        assertThat(request.getAsyncContext()).isNull();
        assertThat(request.getDispatcherType()).isNull();
        assertThat(request.getCookies()).isNull();
        assertThat(request.getDateHeader("x")).isZero();
        assertThat(request.getHeader("x")).isNull();
        assertThat(request.getHeaders("x").hasMoreElements()).isFalse();
        assertThat(request.getHeaderNames().hasMoreElements()).isFalse();
        assertThat(request.getIntHeader("x")).isZero();
        assertThat(request.getMethod()).isNull();
        assertThat(request.getPathInfo()).isNull();
        assertThat(request.getPathTranslated()).isNull();
        assertThat(request.getContextPath()).isNull();
        assertThat(request.getQueryString()).isNull();
        assertThat(request.getRemoteUser()).isNull();
        assertThat(request.isUserInRole("admin")).isFalse();
        assertThat(request.getUserPrincipal()).isNull();
        assertThat(request.getRequestedSessionId()).isNull();
        assertThat(request.getRequestURI()).isNull();
        assertThat(request.getRequestURL()).isNull();
        assertThat(request.getServletPath()).isNull();
        assertThat(request.isRequestedSessionIdValid()).isFalse();
        assertThat(request.isRequestedSessionIdFromCookie()).isFalse();
        assertThat(request.isRequestedSessionIdFromURL()).isFalse();
        assertThat(request.authenticate(response)).isFalse();
        request.login("u", "p");
        request.logout();
        assertThat(request.getParts()).isNull();
        assertThat(request.getPart("p")).isNull();
        assertThat(request.getRequestId()).isNull();
        assertThat(request.getProtocolRequestId()).isNull();
        assertThat(request.getServletConnection()).isNull();
        assertThat(request.getAuthType()).isNull();
        assertThat(request.changeSessionId()).isNull();
        assertThat(request.upgrade(HttpUpgradeHandler.class)).isNull();
    }
}
