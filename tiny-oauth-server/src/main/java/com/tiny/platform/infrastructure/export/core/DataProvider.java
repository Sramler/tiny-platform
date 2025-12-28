package com.tiny.platform.infrastructure.export.core;

import java.util.Iterator;

/**
 * 数据提供者接口
 * T: 每行的数据类型
 */
public interface DataProvider<T> {

    /**
     * 获取迭代器，流式返回数据
     */
    Iterator<T> fetchIterator(int batchSize);

    /**
     * 估算总行数，用于 progress 计算
     * Slice/Iterator 可重写
     */
    default long estimateTotal() { return -1L; }
}