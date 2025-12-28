package com.tiny.platform.infrastructure.export.core;

import java.util.Map;

/**
 * 支持过滤条件的 DataProvider 扩展接口。
 *
 * <p>说明：
 * <ul>
 *   <li>setFilters: 在当前导出上下文中设置过滤条件，具体含义由各业务 DataProvider 自行解释</li>
 *   <li>clearFilters: 导出结束后清理过滤上下文，避免线程复用导致条件串线</li>
 * </ul>
 *
 * <p>注意：实现类需要自行保证线程安全，可以使用 ThreadLocal 或其他机制。</p>
 */
public interface FilterAwareDataProvider<T> extends DataProvider<T> {

    /**
     * 设置当前导出上下文的过滤条件。
     *
     * @param filters 过滤条件 map，可为 null
     */
    void setFilters(Map<String, Object> filters);

    /**
     * 清理当前导出上下文的过滤条件。
     */
    void clearFilters();
}


