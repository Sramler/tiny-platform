package com.tiny.web.sys.repository;

/**
 * Canonical carrier-backed access row used by tiny-web demo authorization checks.
 */
public class GrantedResourceAccessRow {

    private final String path;
    private final String method;

    public GrantedResourceAccessRow(String path, String method) {
        this.path = path;
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public String getMethod() {
        return method;
    }
}
