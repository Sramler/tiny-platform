package com.tiny.platform.core.oauth.service;

import com.tiny.platform.core.oauth.model.HttpRequestLog;

/**
 * HTTP 请求日志服务
 */
public interface HttpRequestLogService {

    /**
     * 保存请求日志
     *
     * @param log 日志实体
     */
    void save(HttpRequestLog log);
}
