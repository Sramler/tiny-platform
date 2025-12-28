package com.tiny.platform.infrastructure.core.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 通用分页响应 DTO
 *
 * 封装 Spring Data Page<T> 的结构为一个更稳定、适合 JSON 序列化的格式。
 * 推荐前后端分页交互统一使用此结构，避免直接暴露 PageImpl。
 *
 * @param <T> 业务返回的数据类型（如 UserResponseDto）
 */
public class PageResponse<T> {

    /**
     * 当前页数据列表
     */
    private List<T> content;

    /**
     * 数据总条数
     */
    private long totalElements;

    /**
     * 总页数
     */
    private int totalPages;

    /**
     * 当前页码（从 0 开始）
     */
    private int pageNumber;

    /**
     * 每页条数
     */
    private int pageSize;

    public PageResponse(Page<T> page) {
        this.content = page.getContent();
        this.totalElements = page.getTotalElements();
        this.totalPages = page.getTotalPages();
        this.pageNumber = page.getNumber();
        this.pageSize = page.getSize();
    }

    // 也可用 Lombok @Data 省略下面这些方法
    public List<T> getContent() {
        return content;
    }

    public void setContent(List<T> content) {
        this.content = content;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(int pageNumber) {
        this.pageNumber = pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}