package com.tiny.platform.infrastructure.export.core;

import java.util.List;

/**
 * PageResult 简单分页封装（可扩展）
 */
public class PageResult<T> {
    private List<T> content;
    private boolean hasNext;

    public List<T> getContent() { return content; }
    public void setContent(List<T> content) { this.content = content; }
    public boolean isHasNext() { return hasNext; }
    public void setHasNext(boolean hasNext) { this.hasNext = hasNext; }
}