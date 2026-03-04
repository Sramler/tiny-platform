package com.tiny.platform.core.oauth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;

@RestController
public class CsrfController {

    @GetMapping("/csrf")
    @ResponseBody
    public Map<String, String> csrf(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken == null) {
            csrfToken = (CsrfToken) request.getAttribute("_csrf");
        }
        if (csrfToken == null) {
            throw new IllegalStateException("CSRF token not found in request");
        }
        return Map.of(
                "token", csrfToken.getToken(),
                "parameterName", csrfToken.getParameterName(),
                "headerName", csrfToken.getHeaderName()
        );
    }
}
