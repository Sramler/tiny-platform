package com.tiny.platform.core.oauth.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.CsrfToken;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CsrfControllerTest {

    @Test
    void csrf_shouldReadTokenFromRequestAttributeByClassName() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        CsrfToken token = mock(CsrfToken.class);
        when(token.getToken()).thenReturn("t");
        when(token.getParameterName()).thenReturn("_csrf");
        when(token.getHeaderName()).thenReturn("X-XSRF-TOKEN");
        when(request.getAttribute(CsrfToken.class.getName())).thenReturn(token);

        CsrfController controller = new CsrfController();
        Map<String, String> resp = controller.csrf(request);

        assertEquals("t", resp.get("token"));
        assertEquals("_csrf", resp.get("parameterName"));
        assertEquals("X-XSRF-TOKEN", resp.get("headerName"));
    }

    @Test
    void csrf_shouldFallbackTo_csrfAttribute() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        CsrfToken token = mock(CsrfToken.class);
        when(token.getToken()).thenReturn("t2");
        when(token.getParameterName()).thenReturn("_csrf");
        when(token.getHeaderName()).thenReturn("X-XSRF-TOKEN");
        when(request.getAttribute(CsrfToken.class.getName())).thenReturn(null);
        when(request.getAttribute("_csrf")).thenReturn(token);

        CsrfController controller = new CsrfController();
        Map<String, String> resp = controller.csrf(request);

        assertEquals("t2", resp.get("token"));
    }

    @Test
    void csrf_whenMissing_shouldThrow() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(CsrfToken.class.getName())).thenReturn(null);
        when(request.getAttribute("_csrf")).thenReturn(null);

        CsrfController controller = new CsrfController();
        assertThrows(IllegalStateException.class, () -> controller.csrf(request));
    }
}

