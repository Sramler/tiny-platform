package com.tiny.web.sys.repository;

/**
 * Native-query projection: granted resource path/method for access checks (uri/url → path).
 */
public interface GrantedResourceAccessRow {

    String getPath();

    String getMethod();
}
